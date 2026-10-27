package com.example.network

import android.content.Context
import com.example.model.IpLookupMode
import com.example.model.IpVersionClassification
import com.example.model.MyIpSnapshot
import com.example.model.PrivacyConsistencyReport
import com.example.model.PrivacyDiagnosticCheckItem
import com.example.model.PrivacyDiagnosticStatus
import com.example.model.ProxyConfigEntity
import com.example.model.ProxyTestStatus
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Real Public & Local IP Address Inspector + Privacy Consistency Score Engine for ClonexaPro.
 *
 * Capabilities:
 * - Detects externally observed Public IPv4 and Public IPv6 addresses independently.
 * - Distinguishes Public Routable IP addresses from Private/Local LAN (RFC 1918, Link-Local, Loopback) addresses.
 * - Supports two strict, never-conflated modes:
 *   1. Direct Connection IP (always routed via Proxy.NO_PROXY)
 *   2. Current Proxy IP (strictly routed through the active proxy in ProxyManagerEngine; refuses to report
 *      direct IP if no proxy is active).
 * - Computes a transparent, documented 0–100% Privacy Consistency Score comparing Direct vs. Proxy IP,
 *   Device Time Zone vs. IP Time Zone, System Language/Locale vs. IP Country, DNS routing posture,
 *   and WebRTC / local interface exposure posture.
 * - Never claims to reproduce Whoer.net's proprietary algorithm and never fabricates values.
 */
class MyIpInspectorEngine(
    private val context: Context,
    private val speedTestEngine: NetworkSpeedTestEngine,
    private val proxyManagerEngine: ProxyManagerEngine
) {

    data class ParsedExternalIpPayload(
        val ip: String,
        val country: String = "Unavailable",
        val region: String = "Unavailable",
        val city: String = "Unavailable",
        val approximateCoordinates: String = "Unavailable",
        val ispOrg: String = "Unavailable",
        val asnNumber: String = "Unavailable",
        val timezoneId: String = "Unavailable",
        val sourceLabel: String = "External IP Check"
    )

    companion object {
        private const val IPV4_PRIMARY_URL = "https://api4.ipify.org?format=json"
        private const val IPV4_FALLBACK_URL = "https://ipv4.icanhazip.com/"

        private const val IPV6_PRIMARY_URL = "https://api6.ipify.org?format=json"
        private const val IPV6_FALLBACK_URL = "https://ipv6.icanhazip.com/"

        private const val GEO_PRIMARY_URL = "https://ipwho.is/"
        private const val GEO_SECONDARY_URL = "https://ipapi.co/json/"
        private const val GEO_TRACE_FALLBACK_URL = "https://1.1.1.1/cdn-cgi/trace"

        private const val CONNECT_TIMEOUT_MS = 6000
        private const val READ_TIMEOUT_MS = 6500

        private val IPV4_PATTERN = Regex(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
        )

        /**
         * Returns true if [candidate] is a syntactically valid IPv4 address.
         */
        fun isIpv4Address(candidate: String): Boolean {
            val trimmed = candidate.trim()
            if (trimmed.isEmpty() || trimmed.contains(':')) return false
            return IPV4_PATTERN.matches(trimmed)
        }

        /**
         * Returns true if [candidate] is a syntactically valid IPv6 address.
         */
        fun isIpv6Address(candidate: String): Boolean {
            val trimmed = candidate.trim().removePrefix("[").removeSuffix("]")
            if (!trimmed.contains(':')) return false
            val withoutZone = trimmed.substringBefore('%')
            val parts = withoutZone.split(":")
            if (parts.size < 3 || parts.size > 8) return false
            return parts.all { part ->
                part.isEmpty() || (part.length <= 4 && part.all { ch ->
                    ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
                })
            }
        }

        /**
         * Returns true if [ip] is a Private LAN (RFC 1918), Loopback, Link-Local, CGNAT, or ULA address.
         */
        fun isPrivateOrReservedIp(ip: String): Boolean {
            val trimmed = ip.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
            if (isIpv4Address(trimmed)) {
                val octets = trimmed.split('.').mapNotNull { it.toIntOrNull() }
                if (octets.size != 4) return true
                val o0 = octets[0]
                val o1 = octets[1]
                return when {
                    o0 == 10 -> true // 10.0.0.0/8 RFC 1918
                    o0 == 127 -> true // 127.0.0.0/8 Loopback
                    o0 == 169 && o1 == 254 -> true // 169.254.0.0/16 Link-local
                    o0 == 172 && o1 in 16..31 -> true // 172.16.0.0/12 RFC 1918
                    o0 == 192 && o1 == 168 -> true // 192.168.0.0/16 RFC 1918
                    o0 == 100 && o1 in 64..127 -> true // 100.64.0.0/10 Carrier-Grade NAT
                    o0 == 0 -> true
                    else -> false
                }
            }
            if (isIpv6Address(trimmed)) {
                val lower = trimmed.lowercase()
                return lower == "::1" ||
                    lower == "0:0:0:0:0:0:0:1" ||
                    lower.startsWith("fe80:") ||
                    lower.startsWith("fc") ||
                    lower.startsWith("fd")
            }
            return false
        }

        /**
         * Describes whether an IP address is Public Routable vs. Private/Local Network.
         */
        fun classifyIpScope(ip: String): String {
            val trimmed = ip.trim()
            if (trimmed.isEmpty() || trimmed == "Unavailable") return "Unavailable"
            return when {
                isIpv4Address(trimmed) -> if (isPrivateOrReservedIp(trimmed)) {
                    "Private / Local Network (IPv4)"
                } else {
                    "Public Routable IPv4"
                }
                isIpv6Address(trimmed) -> if (isPrivateOrReservedIp(trimmed)) {
                    "Private / Link-Local (IPv6)"
                } else {
                    "Public Routable IPv6"
                }
                else -> "Unavailable"
            }
        }

        /**
         * Determines the [IpVersionClassification] from detected IPv4 and IPv6 strings.
         */
        fun resolveVersionClassification(ipv4: String, ipv6: String): IpVersionClassification {
            val hasV4 = isIpv4Address(ipv4)
            val hasV6 = isIpv6Address(ipv6)
            return when {
                hasV4 && hasV6 -> IpVersionClassification.DUAL_STACK
                hasV4 -> IpVersionClassification.IPV4
                hasV6 -> IpVersionClassification.IPV6
                else -> IpVersionClassification.UNAVAILABLE
            }
        }

        private fun extractJsonString(json: String, key: String): String? {
            val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")
            return pattern.find(json)?.groupValues?.getOrNull(1)?.trim()
        }

        private fun extractJsonBoolean(json: String, key: String): Boolean? {
            val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
            return pattern.find(json)?.groupValues?.getOrNull(1)?.lowercase()?.toBooleanStrictOrNull()
        }

        private fun extractJsonDouble(json: String, key: String): Double? {
            val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+(?:\.\d+)?)""")
            return pattern.find(json)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }

        private fun extractJsonInt(json: String, key: String): Int? {
            val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
            return pattern.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        /**
         * Parses JSON, key-value trace, or plain-text IP payloads into [ParsedExternalIpPayload].
         * Never fabricates missing geolocation or ISP values.
         */
        fun parseExternalIpResponse(
            rawBody: String,
            sourceLabel: String = "External IP Check"
        ): ParsedExternalIpPayload? {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty()) return null

            // 1. JSON payload (ipwho.is, ipapi.co, ipify.org)
            if (trimmed.startsWith("{")) {
                val successFlag = extractJsonBoolean(trimmed, "success")
                if (successFlag == false) return null

                val ip = extractJsonString(trimmed, "ip")?.trim().orEmpty()
                if (!isIpv4Address(ip) && !isIpv6Address(ip)) return null

                val country = (extractJsonString(trimmed, "country_name")
                    ?: extractJsonString(trimmed, "country")
                    ?: "Unavailable").ifBlank { "Unavailable" }

                val region = (extractJsonString(trimmed, "region")
                    ?: extractJsonString(trimmed, "region_name")
                    ?: "Unavailable").ifBlank { "Unavailable" }

                val city = (extractJsonString(trimmed, "city") ?: "Unavailable").ifBlank { "Unavailable" }

                val lat = extractJsonDouble(trimmed, "latitude")
                val lon = extractJsonDouble(trimmed, "longitude")
                val coordinates = if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
                    String.format(Locale.US, "Approx. %.3f, %.3f", lat, lon)
                } else {
                    "Unavailable"
                }

                val isp = extractJsonString(trimmed, "isp").orEmpty()
                val org = extractJsonString(trimmed, "org").orEmpty()
                val ispOrg = listOf(isp, org)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" / ")
                    .ifBlank { "Unavailable" }

                val asnString = extractJsonString(trimmed, "asn")
                    ?: extractJsonInt(trimmed, "asn")?.let { "AS$it" }
                    ?: "Unavailable"

                val tzCandidate = extractJsonString(trimmed, "timezone")
                    ?: extractJsonString(trimmed, "id")
                    ?: "Unavailable"
                val tzId = if (tzCandidate.contains("/") || tzCandidate.startsWith("UTC", ignoreCase = true) || tzCandidate.startsWith("GMT", ignoreCase = true)) {
                    tzCandidate
                } else {
                    "Unavailable"
                }

                return ParsedExternalIpPayload(
                    ip = ip,
                    country = country,
                    region = region,
                    city = city,
                    approximateCoordinates = coordinates,
                    ispOrg = ispOrg,
                    asnNumber = asnString.ifBlank { "Unavailable" },
                    timezoneId = tzId.ifBlank { "Unavailable" },
                    sourceLabel = sourceLabel
                )
            }

            // 2. Cloudflare /cdn-cgi/trace key=value payload
            if (trimmed.contains("ip=")) {
                val map = trimmed.lines()
                    .mapNotNull { line ->
                        val idx = line.indexOf('=')
                        if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                    }
                    .toMap()
                val ip = map["ip"]?.trim().orEmpty()
                if (!isIpv4Address(ip) && !isIpv6Address(ip)) return null
                val loc = map["loc"]?.ifBlank { "Unavailable" } ?: "Unavailable"
                val colo = map["colo"]?.ifBlank { null }
                return ParsedExternalIpPayload(
                    ip = ip,
                    country = loc,
                    region = if (colo != null) "Cloudflare PoP ($colo)" else "Unavailable",
                    city = "Unavailable",
                    approximateCoordinates = "Unavailable",
                    ispOrg = "Unavailable",
                    asnNumber = "Unavailable",
                    timezoneId = "Unavailable",
                    sourceLabel = sourceLabel
                )
            }

            // 3. Plain-text single-line IP response (e.g. icanhazip.com)
            val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty()
            if (isIpv4Address(firstLine) || isIpv6Address(firstLine)) {
                return ParsedExternalIpPayload(
                    ip = firstLine,
                    sourceLabel = sourceLabel
                )
            }

            return null
        }

        /**
         * Compares two IANA time zone IDs by exact match or UTC offset equivalence at the current time.
         */
        fun areTimeZonesConsistent(deviceTzId: String, ipTzId: String): Boolean {
            if (deviceTzId.isBlank() || ipTzId.isBlank() ||
                deviceTzId == "Unavailable" || ipTzId == "Unavailable"
            ) {
                return false
            }
            if (deviceTzId.equals(ipTzId, ignoreCase = true)) return true
            return try {
                val devTz = TimeZone.getTimeZone(deviceTzId)
                val ipTz = TimeZone.getTimeZone(ipTzId)
                if ((ipTz.id == "GMT" && !ipTzId.equals("GMT", ignoreCase = true) && !ipTzId.equals("UTC", ignoreCase = true))) {
                    false
                } else {
                    val now = System.currentTimeMillis()
                    devTz.getOffset(now) == ipTz.getOffset(now)
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Checks whether the device's configured Locale country or language aligns with the IP geolocation country.
         */
        fun isLocaleConsistentWithCountry(
            deviceLocaleTag: String,
            deviceCountryCode: String,
            deviceCountryDisplay: String,
            ipCountry: String
        ): Boolean {
            val cleanIpCountry = ipCountry.trim()
            if (cleanIpCountry.isEmpty() || cleanIpCountry == "Unavailable" || cleanIpCountry == "Unknown") {
                return false
            }
            if (deviceCountryCode.isNotBlank() && cleanIpCountry.equals(deviceCountryCode, ignoreCase = true)) {
                return true
            }
            if (deviceCountryDisplay.isNotBlank() &&
                (cleanIpCountry.contains(deviceCountryDisplay, ignoreCase = true) ||
                    deviceCountryDisplay.contains(cleanIpCountry, ignoreCase = true))
            ) {
                return true
            }
            val normalizedCountry = cleanIpCountry.lowercase()
            val langPrefix = deviceLocaleTag.substringBefore('-').lowercase()
            return when {
                normalizedCountry in listOf("united states", "us", "usa", "united kingdom", "uk", "gb", "canada", "australia", "new zealand", "ireland", "singapore") && langPrefix == "en" -> true
                normalizedCountry in listOf("germany", "de", "austria", "switzerland") && langPrefix == "de" -> true
                normalizedCountry in listOf("france", "fr", "belgium", "switzerland", "canada") && langPrefix == "fr" -> true
                normalizedCountry in listOf("spain", "es", "mexico", "argentina", "colombia", "chile") && langPrefix == "es" -> true
                normalizedCountry in listOf("japan", "jp") && langPrefix == "ja" -> true
                normalizedCountry in listOf("brazil", "br", "portugal", "pt") && langPrefix == "pt" -> true
                else -> false
            }
        }

        /**
         * Calculates a transparent, documented 0–100% Privacy Consistency Score based on real detected parameters.
         * Never claims to reproduce Whoer.net's proprietary scoring algorithm.
         */
        fun calculatePrivacyConsistencyReport(
            mode: IpLookupMode,
            primaryPublicIp: String,
            directKnownIp: String,
            proxyKnownIp: String,
            isProxyActiveAndVerified: Boolean,
            proxyProtocolName: String?,
            ipCountry: String,
            ipTimeZone: String,
            localInterfaceIp: String,
            deviceTimeZone: String = TimeZone.getDefault().id ?: "UTC",
            deviceLocale: Locale = Locale.getDefault()
        ): PrivacyConsistencyReport {
            val localeTag = deviceLocale.toLanguageTag().ifBlank { "en-US" }
            val localeCountryCode = deviceLocale.country.orEmpty()
            val localeCountryName = deviceLocale.displayCountry.orEmpty()

            if (primaryPublicIp == "Unavailable" || primaryPublicIp.isBlank()) {
                val unavailableCheck = PrivacyDiagnosticCheckItem(
                    id = "ip_reachability",
                    title = "Public IP & Route Verification",
                    observedValue = "Unavailable",
                    expectedOrBaseline = "Valid Public IPv4 or IPv6",
                    status = PrivacyDiagnosticStatus.UNAVAILABLE,
                    weightPoints = 100,
                    earnedPoints = 0,
                    explanation = if (mode == IpLookupMode.CURRENT_PROXY) {
                        "Proxy IP could not be verified because no active proxy is connected or the proxy tunnel failed."
                    } else {
                        "Public IP could not be detected. Verify your network connection and tap Refresh / Run Full Check."
                    }
                )
                return PrivacyConsistencyReport(
                    scorePercent = 0,
                    ratingLabel = "Unavailable (No Public IP Detected)",
                    deviceTimeZone = deviceTimeZone,
                    ipTimeZone = ipTimeZone,
                    isTimeZoneMatched = null,
                    deviceLocaleTag = localeTag,
                    ipCountryCodeOrName = ipCountry,
                    isLocaleConsistent = null,
                    directPublicIp = directKnownIp,
                    proxyPublicIp = proxyKnownIp,
                    dnsRoutingStatus = "Unavailable",
                    webrtcExposureStatus = "Native Android Socket (No Browser WebRTC Stack)",
                    checks = listOf(unavailableCheck)
                )
            }

            val checks = mutableListOf<PrivacyDiagnosticCheckItem>()

            // 1. Public IP & Direct vs. Proxy Comparison (30 points)
            val resolvedDirectIp = if (mode == IpLookupMode.DIRECT_CONNECTION) primaryPublicIp else directKnownIp
            val resolvedProxyIp = if (mode == IpLookupMode.CURRENT_PROXY && isProxyActiveAndVerified) primaryPublicIp else proxyKnownIp

            val ipCheck: PrivacyDiagnosticCheckItem = when {
                mode == IpLookupMode.CURRENT_PROXY && isProxyActiveAndVerified -> {
                    if (resolvedDirectIp != "Unavailable" && resolvedDirectIp == primaryPublicIp) {
                        PrivacyDiagnosticCheckItem(
                            id = "ip_proxy_comparison",
                            title = "Direct IP vs. Verified Proxy IP",
                            observedValue = "Proxy: $primaryPublicIp / Direct: $resolvedDirectIp",
                            expectedOrBaseline = "Proxy IP distinct from Direct ISP IP",
                            status = PrivacyDiagnosticStatus.WARNING,
                            weightPoints = 30,
                            earnedPoints = 10,
                            explanation = "Warning: The IP observed through your proxy matches your Direct IP ($resolvedDirectIp). Your host ISP address is not masked."
                        )
                    } else {
                        PrivacyDiagnosticCheckItem(
                            id = "ip_proxy_comparison",
                            title = "Direct IP vs. Verified Proxy IP",
                            observedValue = if (resolvedDirectIp != "Unavailable") {
                                "Proxy: $primaryPublicIp (Direct: $resolvedDirectIp)"
                            } else {
                                "Verified Proxy IP: $primaryPublicIp"
                            },
                            expectedOrBaseline = "Verified Proxy Tunnel Active",
                            status = PrivacyDiagnosticStatus.PASS,
                            weightPoints = 30,
                            earnedPoints = 30,
                            explanation = "Externally observed IP ($primaryPublicIp) is verified through your active ${proxyProtocolName ?: "Proxy"} tunnel."
                        )
                    }
                }
                else -> {
                    if (resolvedProxyIp != "Unavailable" && resolvedProxyIp != primaryPublicIp) {
                        PrivacyDiagnosticCheckItem(
                            id = "ip_proxy_comparison",
                            title = "Direct IP vs. Verified Proxy IP",
                            observedValue = "Direct: $primaryPublicIp (Proxy: $resolvedProxyIp)",
                            expectedOrBaseline = "Direct vs. Proxy Separation Verified",
                            status = PrivacyDiagnosticStatus.PASS,
                            weightPoints = 30,
                            earnedPoints = 26,
                            explanation = "Inspecting Direct ISP IP ($primaryPublicIp). Your active proxy exit IP ($resolvedProxyIp) is distinct."
                        )
                    } else {
                        PrivacyDiagnosticCheckItem(
                            id = "ip_proxy_comparison",
                            title = "Direct IP vs. Verified Proxy IP",
                            observedValue = "Direct IP: $primaryPublicIp (No Active Proxy)",
                            expectedOrBaseline = "Active Proxy Tunnel Recommended for Masking",
                            status = PrivacyDiagnosticStatus.INFO,
                            weightPoints = 30,
                            earnedPoints = 20,
                            explanation = "Your direct ISP public IP ($primaryPublicIp) is visible to external services. Connect a proxy in Proxy Manager if you want to mask your host IP."
                        )
                    }
                }
            }
            checks.add(ipCheck)

            // 2. Time Zone Alignment (Device vs. IP Geolocation) (25 points)
            val tzMatched: Boolean? = if (ipTimeZone == "Unavailable" || ipTimeZone.isBlank()) {
                null
            } else {
                areTimeZonesConsistent(deviceTimeZone, ipTimeZone)
            }

            val tzCheck = when (tzMatched) {
                true -> PrivacyDiagnosticCheckItem(
                    id = "timezone_consistency",
                    title = "Device Time Zone vs. IP Time Zone",
                    observedValue = "Device: $deviceTimeZone • IP: $ipTimeZone",
                    expectedOrBaseline = "Matching Time Zone / UTC Offset",
                    status = PrivacyDiagnosticStatus.PASS,
                    weightPoints = 25,
                    earnedPoints = 25,
                    explanation = "Device system time zone ($deviceTimeZone) aligns with the IP geolocation time zone ($ipTimeZone)."
                )
                false -> PrivacyDiagnosticCheckItem(
                    id = "timezone_consistency",
                    title = "Device Time Zone vs. IP Time Zone",
                    observedValue = "Device: $deviceTimeZone • IP: $ipTimeZone",
                    expectedOrBaseline = "Device Time Zone matching $ipTimeZone",
                    status = PrivacyDiagnosticStatus.WARNING,
                    weightPoints = 25,
                    earnedPoints = 5,
                    explanation = "Warning: Device time zone ($deviceTimeZone) does not match IP geolocation time zone ($ipTimeZone). Websites and apps often detect proxies via time zone mismatches."
                )
                null -> PrivacyDiagnosticCheckItem(
                    id = "timezone_consistency",
                    title = "Device Time Zone vs. IP Time Zone",
                    observedValue = "Device: $deviceTimeZone • IP TZ: Unavailable",
                    expectedOrBaseline = "IANA Time Zone from Geolocation Endpoint",
                    status = PrivacyDiagnosticStatus.INFO,
                    weightPoints = 25,
                    earnedPoints = 18,
                    explanation = "Device time zone is '$deviceTimeZone'. The external IP service did not return an IANA time zone identifier for this IP."
                )
            }
            checks.add(tzCheck)

            // 3. System Language & Locale Consistency (15 points)
            val localeMatched: Boolean? = if (ipCountry == "Unavailable" || ipCountry == "Unknown" || ipCountry.isBlank()) {
                null
            } else {
                isLocaleConsistentWithCountry(localeTag, localeCountryCode, localeCountryName, ipCountry)
            }

            val localeCheck = when (localeMatched) {
                true -> PrivacyDiagnosticCheckItem(
                    id = "locale_consistency",
                    title = "System Language / Locale vs. IP Country",
                    observedValue = "Locale: $localeTag • IP Country: $ipCountry",
                    expectedOrBaseline = "Consistent Region / Language",
                    status = PrivacyDiagnosticStatus.PASS,
                    weightPoints = 15,
                    earnedPoints = 15,
                    explanation = "System language/locale ($localeTag) is consistent with the detected IP country ($ipCountry)."
                )
                false -> PrivacyDiagnosticCheckItem(
                    id = "locale_consistency",
                    title = "System Language / Locale vs. IP Country",
                    observedValue = "Locale: $localeTag • IP Country: $ipCountry",
                    expectedOrBaseline = "Locale matching $ipCountry",
                    status = PrivacyDiagnosticStatus.WARNING,
                    weightPoints = 15,
                    earnedPoints = 6,
                    explanation = "Warning: System locale ($localeTag) differs from the IP geolocation region ($ipCountry). Accept-Language headers may reveal your device's home region."
                )
                null -> PrivacyDiagnosticCheckItem(
                    id = "locale_consistency",
                    title = "System Language / Locale vs. IP Country",
                    observedValue = "Locale: $localeTag • IP Country: Unavailable",
                    expectedOrBaseline = "IP Country Geolocation",
                    status = PrivacyDiagnosticStatus.INFO,
                    weightPoints = 15,
                    earnedPoints = 11,
                    explanation = "System locale is '$localeTag'. IP country metadata was not returned by the lookup endpoint."
                )
            }
            checks.add(localeCheck)

            // 4. DNS Routing & Leak Posture (15 points)
            val dnsStatusText: String
            val dnsCheck = if (mode == IpLookupMode.CURRENT_PROXY && isProxyActiveAndVerified) {
                dnsStatusText = "Remote Proxy Tunnel DNS (${proxyProtocolName ?: "Proxy"})"
                PrivacyDiagnosticCheckItem(
                    id = "dns_posture",
                    title = "DNS Routing & Leak Indicator",
                    observedValue = dnsStatusText,
                    expectedOrBaseline = "Remote Hostname Resolution via Proxy",
                    status = PrivacyDiagnosticStatus.PASS,
                    weightPoints = 15,
                    earnedPoints = 15,
                    explanation = "Target hostnames are passed through the active ${proxyProtocolName ?: "Proxy"} tunnel so DNS resolution occurs on the remote proxy server."
                )
            } else {
                dnsStatusText = "Direct System DNS Resolver (ISP / Android Network)"
                PrivacyDiagnosticCheckItem(
                    id = "dns_posture",
                    title = "DNS Routing & Leak Indicator",
                    observedValue = dnsStatusText,
                    expectedOrBaseline = "Encrypted or Proxied DNS",
                    status = PrivacyDiagnosticStatus.INFO,
                    weightPoints = 15,
                    earnedPoints = 11,
                    explanation = "Direct connection uses your device's default Android/ISP DNS resolver. Note: Android native socket inspection cannot query browser-internal DoH state."
                )
            }
            checks.add(dnsCheck)

            // 5. WebRTC & Local Interface Exposure Posture (15 points)
            val webrtcStatusText = "Native Android Socket (No Browser WebRTC STUN/ICE)"
            val webrtcCheck = PrivacyDiagnosticCheckItem(
                id = "webrtc_local_posture",
                title = "WebRTC & Local Interface Exposure",
                observedValue = "Local RFC 1918: $localInterfaceIp (Isolated)",
                expectedOrBaseline = "No Local IP Leak in External Payload",
                status = PrivacyDiagnosticStatus.PASS,
                weightPoints = 15,
                earnedPoints = 15,
                explanation = "ClonexaPro uses native Android JVM/OS sockets rather than a browser WebRTC engine, so STUN/ICE UDP leaks are not active. Local interface ($localInterfaceIp) is not transmitted in HTTP headers."
            )
            checks.add(webrtcCheck)

            val totalScore = checks.sumOf { it.earnedPoints }.coerceIn(0, 100)
            val rating = when {
                totalScore >= 85 -> "High Consistency (Optimal)"
                totalScore >= 70 -> "Good Consistency (Minor Advisories)"
                totalScore >= 50 -> "Moderate Consistency (Review Warnings)"
                else -> "Low Consistency (Mismatches Detected)"
            }

            return PrivacyConsistencyReport(
                scorePercent = totalScore,
                ratingLabel = rating,
                deviceTimeZone = deviceTimeZone,
                ipTimeZone = ipTimeZone,
                isTimeZoneMatched = tzMatched,
                deviceLocaleTag = localeTag,
                ipCountryCodeOrName = ipCountry,
                isLocaleConsistent = localeMatched,
                directPublicIp = resolvedDirectIp,
                proxyPublicIp = resolvedProxyIp,
                dnsRoutingStatus = dnsStatusText,
                webrtcExposureStatus = webrtcStatusText,
                checks = checks
            )
        }
    }

    /**
     * Inspects local non-loopback network interfaces on the device to distinguish
     * private/LAN interface addresses from externally observed public IP addresses.
     */
    fun detectLocalInterfaceAddress(): Pair<String, String> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "Unavailable" to "No Active Network Interface"
            var bestIpv4: String? = null
            var bestIpv4Iface: String? = null
            var bestIpv6: String? = null
            var bestIpv6Iface: String? = null

            for (nif in Collections.list(interfaces)) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr.isLoopbackAddress) continue
                    val rawHost = (addr.hostAddress ?: continue).substringBefore('%')
                    if (addr is Inet4Address && bestIpv4 == null) {
                        bestIpv4 = rawHost
                        bestIpv4Iface = nif.displayName ?: nif.name
                    } else if (addr is Inet6Address && bestIpv6 == null) {
                        bestIpv6 = rawHost
                        bestIpv6Iface = nif.displayName ?: nif.name
                    }
                }
            }

            when {
                bestIpv4 != null -> {
                    val scope = classifyIpScope(bestIpv4)
                    "$bestIpv4 (${bestIpv4Iface ?: "if0"})" to scope
                }
                bestIpv6 != null -> {
                    val scope = classifyIpScope(bestIpv6)
                    "$bestIpv6 (${bestIpv6Iface ?: "if0"})" to scope
                }
                else -> "Unavailable" to "Private / Local Interface Unavailable"
            }
        } catch (_: Exception) {
            "Unavailable" to "Private / Local Interface Unavailable"
        }
    }

    /**
     * Performs a real IP & Geolocation lookup in the requested [mode] and computes the
     * integrated [PrivacyConsistencyReport].
     *
     * - In [IpLookupMode.DIRECT_CONNECTION], always uses `Proxy.NO_PROXY` and queries IPv4, IPv6,
     *   and geolocation endpoints independently.
     * - In [IpLookupMode.CURRENT_PROXY], requires an active connected [activeProxy] and routes the
     *   verification request strictly through that proxy. If [activeProxy] is null, never falls back
     *   to the direct connection IP.
     */
    suspend fun inspectMyIp(
        mode: IpLookupMode,
        activeProxy: ProxyConfigEntity?,
        knownDirectIp: String = "Unavailable",
        knownProxyIp: String = "Unavailable",
        ipv4EndpointOverride: String? = null,
        ipv6EndpointOverride: String? = null,
        geoEndpointOverride: String? = null
    ): MyIpSnapshot = withContext(Dispatchers.IO) {
        val netInfo = speedTestEngine.detectNetworkConnection()
        val (localIp, localScope) = detectLocalInterfaceAddress()
        val now = System.currentTimeMillis()

        if (mode == IpLookupMode.CURRENT_PROXY) {
            if (activeProxy == null) {
                val emptyPrivacy = calculatePrivacyConsistencyReport(
                    mode = IpLookupMode.CURRENT_PROXY,
                    primaryPublicIp = "Unavailable",
                    directKnownIp = knownDirectIp,
                    proxyKnownIp = "Unavailable",
                    isProxyActiveAndVerified = false,
                    proxyProtocolName = null,
                    ipCountry = "Unavailable",
                    ipTimeZone = "Unavailable",
                    localInterfaceIp = localIp
                )
                return@withContext MyIpSnapshot(
                    mode = IpLookupMode.CURRENT_PROXY,
                    primaryPublicIp = "Unavailable",
                    publicIpv4 = "Unavailable",
                    publicIpv6 = "Unavailable",
                    ipVersion = IpVersionClassification.UNAVAILABLE,
                    localInterfaceIp = localIp,
                    localInterfaceScope = localScope,
                    connectionTypeLabel = netInfo.connectionTypeLabel,
                    viaRouteDescription = "Proxy Not Active (Verification Refused)",
                    lookupServiceName = "Proxy Manager Tunnel Verifier",
                    checkedAtMillis = now,
                    isProxyVerified = false,
                    errorMessage = "Proxy is not currently active. Connect a proxy in Proxy Manager to verify your Current Proxy IP. ClonexaPro never labels your Direct IP as a Proxy IP.",
                    privacyReport = emptyPrivacy
                )
            }

            // Route strictly through the configured proxy
            val verification = proxyManagerEngine.testProxyConfiguration(
                config = activeProxy,
                targetEndpointOverride = geoEndpointOverride
            )

            if (verification.status != ProxyTestStatus.WORKING ||
                verification.geoReport.publicIp == "Unavailable"
            ) {
                val failedPrivacy = calculatePrivacyConsistencyReport(
                    mode = IpLookupMode.CURRENT_PROXY,
                    primaryPublicIp = "Unavailable",
                    directKnownIp = knownDirectIp,
                    proxyKnownIp = "Unavailable",
                    isProxyActiveAndVerified = false,
                    proxyProtocolName = activeProxy.protocol,
                    ipCountry = "Unavailable",
                    ipTimeZone = "Unavailable",
                    localInterfaceIp = localIp
                )
                return@withContext MyIpSnapshot(
                    mode = IpLookupMode.CURRENT_PROXY,
                    primaryPublicIp = "Unavailable",
                    publicIpv4 = "Unavailable",
                    publicIpv6 = "Unavailable",
                    ipVersion = IpVersionClassification.UNAVAILABLE,
                    localInterfaceIp = localIp,
                    localInterfaceScope = localScope,
                    connectionTypeLabel = "${netInfo.connectionTypeLabel} • Proxy (${activeProxy.protocol})",
                    viaRouteDescription = "${activeProxy.name} (${activeProxy.host}:${activeProxy.port}) • ${verification.status.displayLabel}",
                    lookupServiceName = "Proxied External IP Check",
                    latencyMs = verification.latencyMs,
                    checkedAtMillis = now,
                    isProxyVerified = false,
                    errorMessage = verification.diagnosticMessage,
                    privacyReport = failedPrivacy
                )
            }

            val observedProxyIp = verification.geoReport.publicIp.trim()
            val v4 = if (isIpv4Address(observedProxyIp)) observedProxyIp else "Unavailable"
            val v6 = if (isIpv6Address(observedProxyIp)) observedProxyIp else "Unavailable"
            val version = resolveVersionClassification(v4, v6)
            val resolvedCountry = verification.geoReport.country.let { if (it == "Unknown" || it.isBlank()) "Unavailable" else it }

            val privacyReport = calculatePrivacyConsistencyReport(
                mode = IpLookupMode.CURRENT_PROXY,
                primaryPublicIp = observedProxyIp,
                directKnownIp = knownDirectIp,
                proxyKnownIp = observedProxyIp,
                isProxyActiveAndVerified = true,
                proxyProtocolName = activeProxy.protocol,
                ipCountry = resolvedCountry,
                ipTimeZone = "Unavailable",
                localInterfaceIp = localIp
            )

            return@withContext MyIpSnapshot(
                mode = IpLookupMode.CURRENT_PROXY,
                primaryPublicIp = observedProxyIp,
                publicIpv4 = v4,
                publicIpv6 = v6,
                ipVersion = version,
                localInterfaceIp = localIp,
                localInterfaceScope = localScope,
                connectionTypeLabel = "${netInfo.connectionTypeLabel} • ${activeProxy.protocol} Proxy",
                ispOrg = verification.geoReport.ispOrg.ifBlank { "Unavailable" },
                asnNumber = extractAsnFromIsp(verification.geoReport.ispOrg),
                country = resolvedCountry,
                region = verification.geoReport.region.let { if (it == "Unknown" || it.isBlank()) "Unavailable" else it },
                city = verification.geoReport.city.let { if (it == "Unknown" || it.isBlank()) "Unavailable" else it },
                approximateCoordinates = verification.geoReport.coordinates.ifBlank { "Unavailable" },
                timezoneId = "Unavailable",
                viaRouteDescription = "Verified via Active Proxy: ${activeProxy.name} (${activeProxy.protocol} ${activeProxy.host}:${activeProxy.port})",
                lookupServiceName = "ipwho.is / ipapi.co via ${activeProxy.protocol} Proxy",
                latencyMs = verification.latencyMs,
                checkedAtMillis = now,
                isProxyVerified = true,
                errorMessage = null,
                privacyReport = privacyReport
            )
        }

        // Mode == IpLookupMode.DIRECT_CONNECTION (Strictly Proxy.NO_PROXY)
        val startNanos = System.nanoTime()
        coroutineScope {
            val v4Urls = if (ipv4EndpointOverride != null) {
                listOf(ipv4EndpointOverride)
            } else {
                listOf(IPV4_PRIMARY_URL, IPV4_FALLBACK_URL)
            }
            val v6Urls = if (ipv6EndpointOverride != null) {
                listOf(ipv6EndpointOverride)
            } else {
                listOf(IPV6_PRIMARY_URL, IPV6_FALLBACK_URL)
            }
            val geoUrls = if (geoEndpointOverride != null) {
                listOf(geoEndpointOverride)
            } else {
                listOf(GEO_PRIMARY_URL, GEO_SECONDARY_URL, GEO_TRACE_FALLBACK_URL)
            }

            val v4Deferred = async { fetchDirectPayloadFromCandidates(v4Urls, "ipify IPv4") }
            val v6Deferred = async { fetchDirectPayloadFromCandidates(v6Urls, "ipify IPv6") }
            val geoDeferred = async { fetchDirectPayloadFromCandidates(geoUrls, "ipwho.is / ipapi.co") }

            val v4Result = v4Deferred.await()
            val v6Result = v6Deferred.await()
            val geoResult = geoDeferred.await()

            val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)

            val detectedIpv4 = when {
                v4Result != null && isIpv4Address(v4Result.ip) -> v4Result.ip
                geoResult != null && isIpv4Address(geoResult.ip) -> geoResult.ip
                else -> "Unavailable"
            }

            val detectedIpv6 = when {
                v6Result != null && isIpv6Address(v6Result.ip) -> v6Result.ip
                geoResult != null && isIpv6Address(geoResult.ip) -> geoResult.ip
                else -> "Unavailable"
            }

            val primaryIp = when {
                geoResult != null && (isIpv4Address(geoResult.ip) || isIpv6Address(geoResult.ip)) -> geoResult.ip
                detectedIpv4 != "Unavailable" -> detectedIpv4
                detectedIpv6 != "Unavailable" -> detectedIpv6
                else -> "Unavailable"
            }

            val version = resolveVersionClassification(detectedIpv4, detectedIpv6)

            if (primaryIp == "Unavailable") {
                val emptyPrivacy = calculatePrivacyConsistencyReport(
                    mode = IpLookupMode.DIRECT_CONNECTION,
                    primaryPublicIp = "Unavailable",
                    directKnownIp = "Unavailable",
                    proxyKnownIp = knownProxyIp,
                    isProxyActiveAndVerified = activeProxy != null,
                    proxyProtocolName = activeProxy?.protocol,
                    ipCountry = "Unavailable",
                    ipTimeZone = "Unavailable",
                    localInterfaceIp = localIp
                )
                return@coroutineScope MyIpSnapshot(
                    mode = IpLookupMode.DIRECT_CONNECTION,
                    primaryPublicIp = "Unavailable",
                    publicIpv4 = "Unavailable",
                    publicIpv6 = "Unavailable",
                    ipVersion = IpVersionClassification.UNAVAILABLE,
                    localInterfaceIp = localIp,
                    localInterfaceScope = localScope,
                    connectionTypeLabel = netInfo.connectionTypeLabel,
                    viaRouteDescription = "Direct Host Connection (Proxy.NO_PROXY)",
                    lookupServiceName = "ipwho.is / ipify.org",
                    latencyMs = null,
                    checkedAtMillis = now,
                    isProxyVerified = false,
                    errorMessage = "Unable to detect public IP address. Check your internet connection or firewall settings and tap Refresh.",
                    privacyReport = emptyPrivacy
                )
            }

            val resolvedCountry = geoResult?.country ?: "Unavailable"
            val resolvedTz = geoResult?.timezoneId ?: "Unavailable"
            val resolvedProxyIp = activeProxy?.lastObservedIp?.takeIf { it != "Unavailable" && it.isNotBlank() } ?: knownProxyIp

            val privacyReport = calculatePrivacyConsistencyReport(
                mode = IpLookupMode.DIRECT_CONNECTION,
                primaryPublicIp = primaryIp,
                directKnownIp = primaryIp,
                proxyKnownIp = resolvedProxyIp,
                isProxyActiveAndVerified = activeProxy != null,
                proxyProtocolName = activeProxy?.protocol,
                ipCountry = resolvedCountry,
                ipTimeZone = resolvedTz,
                localInterfaceIp = localIp
            )

            MyIpSnapshot(
                mode = IpLookupMode.DIRECT_CONNECTION,
                primaryPublicIp = primaryIp,
                publicIpv4 = detectedIpv4,
                publicIpv6 = detectedIpv6,
                ipVersion = version,
                localInterfaceIp = localIp,
                localInterfaceScope = localScope,
                connectionTypeLabel = netInfo.connectionTypeLabel,
                ispOrg = geoResult?.ispOrg ?: "Unavailable",
                asnNumber = geoResult?.asnNumber?.let {
                    if (it != "Unavailable") it else extractAsnFromIsp(geoResult.ispOrg)
                } ?: "Unavailable",
                country = resolvedCountry,
                region = geoResult?.region ?: "Unavailable",
                city = geoResult?.city ?: "Unavailable",
                approximateCoordinates = geoResult?.approximateCoordinates ?: "Unavailable",
                timezoneId = resolvedTz,
                viaRouteDescription = "Direct Host Connection (Verified via Proxy.NO_PROXY)",
                lookupServiceName = geoResult?.sourceLabel ?: v4Result?.sourceLabel ?: "ipify.org",
                latencyMs = elapsedMs,
                checkedAtMillis = now,
                isProxyVerified = false,
                errorMessage = null,
                privacyReport = privacyReport
            )
        }
    }

    private fun fetchDirectPayloadFromCandidates(
        urls: List<String>,
        sourceLabel: String
    ): ParsedExternalIpPayload? {
        for (urlStr in urls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                    setRequestProperty("Accept", "application/json, text/plain")
                    setRequestProperty("User-Agent", "ClonexaPro-MyIpInspector/1.0")
                }
                conn.connect()
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val parsed = parseExternalIpResponse(body, sourceLabel = sourceLabel)
                    if (parsed != null) {
                        return parsed
                    }
                }
            } catch (_: SocketTimeoutException) {
                // Try next fallback candidate
            } catch (_: Exception) {
                // Try next fallback candidate
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun extractAsnFromIsp(ispOrg: String): String {
        val match = Regex("\\b(AS\\d+)\\b", RegexOption.IGNORE_CASE).find(ispOrg)
        return match?.groupValues?.getOrNull(1)?.uppercase() ?: "Unavailable"
    }
}

