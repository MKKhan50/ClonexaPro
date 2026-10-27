package com.example.network

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ProxyInfo
import android.os.Build
import android.util.Base64
import com.example.isolation.ClonexaDeviceAdminReceiver
import com.example.model.IpGeolocationReport
import com.example.model.ProxyConfigEntity
import com.example.model.ProxyProtocol
import com.example.model.ProxyTestStatus
import com.example.security.CryptoSecurityManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.Authenticator
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ProxyValidationResult(
    val isValid: Boolean,
    val sanitizedHost: String = "",
    val parsedPort: Int = 0,
    val errorMessage: String? = null
)

data class ProxyVerificationResult(
    val status: ProxyTestStatus,
    val latencyMs: Long?,
    val geoReport: IpGeolocationReport,
    val dnsBehavior: String,
    val diagnosticMessage: String
)

/**
 * Genuine Proxy Manager Engine supporting:
 * - Strict input validation for hostnames, IPv4/IPv6 addresses, and ports (1..65535)
 * - Real HTTP, HTTPS (TLS), and SOCKS5 (RFC 1928 / RFC 1929) proxy connection testing
 * - Externally observed Public IP & Approximate Geolocation lookup routed through the proxy
 * - Application-scoped ProxySelector routing (with optional Work Profile recommended proxy when Profile Owner)
 * - Android Keystore-backed credential decryption in memory without ever logging passwords
 */
class ProxyManagerEngine(
    private val context: Context,
    private val cryptoSecurityManager: CryptoSecurityManager
) {

    companion object {
        private const val GEO_URL_PRIMARY = "https://ipwho.is/"
        private const val GEO_URL_SECONDARY = "https://ipapi.co/json/"
        private const val TRACE_URL_FALLBACK = "https://1.1.1.1/cdn-cgi/trace"

        private const val CONNECT_TIMEOUT_MS = 6500
        private const val READ_TIMEOUT_MS = 7000

        private val HOSTNAME_REGEX = Regex(
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$"
        )
        private val IPV4_REGEX = Regex(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
        )
        private val IPV6_SIMPLE_REGEX = Regex(
            "^\\[?[0-9a-fA-F:]+\\]?$"
        )

        /**
         * Validates user-entered proxy fields and rejects malformed addresses or invalid ports.
         */
        fun validateProxyInput(
            name: String,
            hostInput: String,
            portInput: String
        ): ProxyValidationResult {
            if (name.trim().isEmpty()) {
                return ProxyValidationResult(
                    isValid = false,
                    errorMessage = "Proxy name is required."
                )
            }

            val trimmedHost = hostInput.trim()
            if (trimmedHost.isEmpty()) {
                return ProxyValidationResult(
                    isValid = false,
                    errorMessage = "Server hostname or IP address is required."
                )
            }

            if (trimmedHost.contains("://") || trimmedHost.contains("/") || trimmedHost.contains(" ")) {
                return ProxyValidationResult(
                    isValid = false,
                    errorMessage = "Enter a valid hostname or IP address without 'http://' or path slashes."
                )
            }

            val isValidHost = IPV4_REGEX.matches(trimmedHost) ||
                HOSTNAME_REGEX.matches(trimmedHost) ||
                (trimmedHost.contains(":") && IPV6_SIMPLE_REGEX.matches(trimmedHost))

            if (!isValidHost) {
                return ProxyValidationResult(
                    isValid = false,
                    errorMessage = "Malformed server hostname or IP address: '$trimmedHost'."
                )
            }

            val port = portInput.trim().toIntOrNull()
            if (port == null || port !in 1..65535) {
                return ProxyValidationResult(
                    isValid = false,
                    errorMessage = "Port must be a valid integer between 1 and 65535."
                )
            }

            return ProxyValidationResult(
                isValid = true,
                sanitizedHost = trimmedHost.removePrefix("[").removeSuffix("]"),
                parsedPort = port
            )
        }

        fun encodeBase64Basic(raw: String): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val bytes = raw.toByteArray(StandardCharsets.UTF_8)
            val out = StringBuilder(((bytes.size + 2) / 3) * 4)
            var i = 0
            while (i < bytes.size) {
                val b0 = bytes[i].toInt() and 0xFF
                val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
                val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
                val triple = (b0 shl 16) or (b1 shl 8) or b2
                out.append(alphabet[(triple shr 18) and 0x3F])
                out.append(alphabet[(triple shr 12) and 0x3F])
                out.append(if (i + 1 < bytes.size) alphabet[(triple shr 6) and 0x3F] else '=')
                out.append(if (i + 2 < bytes.size) alphabet[triple and 0x3F] else '=')
                i += 3
            }
            return out.toString()
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

        /**
         * Parses JSON geolocation responses from ipwho.is or ipapi.co into [IpGeolocationReport].
         * Returns "Unknown" or "Unavailable" for missing fields; never fabricates data.
         */
        fun parseGeolocationJson(
            rawJson: String,
            latencyMs: Long,
            viaRouteLabel: String,
            dnsBehaviorNote: String
        ): IpGeolocationReport? {
            val trimmed = rawJson.trim()
            if (!trimmed.startsWith("{")) return null

            val successFlag = extractJsonBoolean(trimmed, "success")
            if (successFlag == false) return null

            val ip = extractJsonString(trimmed, "ip")?.trim().orEmpty()
            if (ip.isEmpty()) return null

            val country = (extractJsonString(trimmed, "country_name")
                ?: extractJsonString(trimmed, "country")
                ?: "Unknown").ifBlank { "Unknown" }

            val region = (extractJsonString(trimmed, "region")
                ?: extractJsonString(trimmed, "region_name")
                ?: "Unknown").ifBlank { "Unknown" }

            val city = (extractJsonString(trimmed, "city") ?: "Unknown").ifBlank { "Unknown" }

            val lat = extractJsonDouble(trimmed, "latitude")
            val lon = extractJsonDouble(trimmed, "longitude")
            val coordinates = if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
                String.format(java.util.Locale.US, "Approx. %.3f, %.3f", lat, lon)
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

            return IpGeolocationReport(
                publicIp = ip,
                country = country,
                region = region,
                city = city,
                coordinates = coordinates,
                ispOrg = ispOrg,
                latencyMs = latencyMs,
                viaRouteLabel = viaRouteLabel,
                dnsBehaviorNote = dnsBehaviorNote
            )
        }
    }

    private val defaultProxySelector: ProxySelector? = ProxySelector.getDefault()

    @Volatile
    private var activeConnectedConfig: ProxyConfigEntity? = null

    /**
     * Performs a genuine network test through the specified [config] and measures
     * reachability, authentication, latency, DNS resolution, and externally observed IP geolocation.
     */
    suspend fun testProxyConfiguration(
        config: ProxyConfigEntity,
        targetEndpointOverride: String? = null
    ): ProxyVerificationResult = withContext(Dispatchers.IO) {
        val decryptedPassword = cryptoSecurityManager.decryptCredential(config.encryptedPassword)
        val startNanos = System.nanoTime()

        try {
            // 1. Protocol-specific low-level handshake & authentication verification
            when (config.resolvedProtocol) {
                ProxyProtocol.SOCKS5 -> {
                    val socksCheck = verifySocks5HandshakeAndAuth(
                        host = config.host,
                        port = config.port,
                        username = config.username,
                        password = decryptedPassword
                    )
                    if (socksCheck != ProxyTestStatus.WORKING) {
                        val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                        return@withContext ProxyVerificationResult(
                            status = socksCheck,
                            latencyMs = if (socksCheck == ProxyTestStatus.AUTH_FAILED) elapsedMs else null,
                            geoReport = IpGeolocationReport(
                                viaRouteLabel = "${config.name} (SOCKS5)",
                                dnsBehaviorNote = "SOCKS5 Handshake Rejected"
                            ),
                            dnsBehavior = "Handshake failed before DNS lookup",
                            diagnosticMessage = when (socksCheck) {
                                ProxyTestStatus.AUTH_FAILED ->
                                    "Authentication Failed: SOCKS5 server at ${config.host}:${config.port} rejected credentials."
                                ProxyTestStatus.TIMEOUT ->
                                    "Timeout: SOCKS5 server at ${config.host}:${config.port} did not respond within ${CONNECT_TIMEOUT_MS}ms."
                                else ->
                                    "Connection Failed: Unable to complete SOCKS5 handshake with ${config.host}:${config.port}."
                            }
                        )
                    }
                }
                ProxyProtocol.HTTPS -> {
                    val httpsCheck = verifyHttpsTlsProxyHandshake(
                        host = config.host,
                        port = config.port,
                        username = config.username,
                        password = decryptedPassword
                    )
                    if (httpsCheck != ProxyTestStatus.WORKING) {
                        val elapsedMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                        return@withContext ProxyVerificationResult(
                            status = httpsCheck,
                            latencyMs = if (httpsCheck == ProxyTestStatus.AUTH_FAILED) elapsedMs else null,
                            geoReport = IpGeolocationReport(
                                viaRouteLabel = "${config.name} (HTTPS)",
                                dnsBehaviorNote = "TLS Proxy Tunnel Failed"
                            ),
                            dnsBehavior = "TLS proxy handshake failed",
                            diagnosticMessage = when (httpsCheck) {
                                ProxyTestStatus.AUTH_FAILED ->
                                    "Authentication Failed: HTTPS proxy returned 407 Proxy Authentication Required."
                                ProxyTestStatus.TIMEOUT ->
                                    "Timeout: HTTPS TLS proxy at ${config.host}:${config.port} timed out."
                                else ->
                                    "Connection Failed: Could not establish TLS proxy session with ${config.host}:${config.port}."
                            }
                        )
                    }
                }
                ProxyProtocol.HTTP -> {
                    // HTTP proxy is verified directly via proxied HTTP/HTTPS request below
                }
            }

            // 2. Perform real proxied HTTP/HTTPS request to external IP & Geolocation check endpoint
            val dnsBehavior = when (config.resolvedProtocol) {
                ProxyProtocol.HTTP -> "HTTP CONNECT Tunnel (Remote Proxy DNS)"
                ProxyProtocol.HTTPS -> "TLS Proxy Tunnel (Remote Proxy DNS)"
                ProxyProtocol.SOCKS5 -> "SOCKS5 Proxy Socket"
            }

            val endpoints = if (targetEndpointOverride != null) {
                listOf(targetEndpointOverride)
            } else {
                listOf(GEO_URL_PRIMARY, GEO_URL_SECONDARY, TRACE_URL_FALLBACK)
            }

            var lastStatus = ProxyTestStatus.CONNECTION_FAILED
            var lastDiag = "Could not reach external verification endpoint through proxy."

            for (endpoint in endpoints) {
                val reqStart = System.nanoTime()
                val outcome = executeSingleProxiedGet(
                    urlStr = endpoint,
                    config = config,
                    decryptedPassword = decryptedPassword
                )
                val rttMs = ((System.nanoTime() - reqStart) / 1_000_000L).coerceAtLeast(1L)

                if (outcome.status == ProxyTestStatus.AUTH_FAILED) {
                    return@withContext ProxyVerificationResult(
                        status = ProxyTestStatus.AUTH_FAILED,
                        latencyMs = rttMs,
                        geoReport = IpGeolocationReport(
                            latencyMs = rttMs,
                            viaRouteLabel = "${config.name} (${config.protocol})",
                            dnsBehaviorNote = dnsBehavior
                        ),
                        dnsBehavior = dnsBehavior,
                        diagnosticMessage = "Authentication Failed (HTTP 407): Proxy server rejected username/password."
                    )
                }

                if (outcome.status == ProxyTestStatus.WORKING && outcome.body.isNotBlank()) {
                    val geo = parseGeolocationOrTrace(
                        rawBody = outcome.body,
                        latencyMs = rttMs,
                        viaRouteLabel = "${config.name} (${config.protocol})",
                        dnsBehaviorNote = dnsBehavior
                    )
                    if (geo != null) {
                        return@withContext ProxyVerificationResult(
                            status = ProxyTestStatus.WORKING,
                            latencyMs = rttMs,
                            geoReport = geo,
                            dnsBehavior = dnsBehavior,
                            diagnosticMessage = "Working: Verified via ${config.protocol} proxy (${rttMs} ms) • Observed IP: ${geo.publicIp} (${geo.country})."
                        )
                    }
                }

                lastStatus = outcome.status
                lastDiag = outcome.diagnostic
                if (outcome.status == ProxyTestStatus.TIMEOUT) break
            }

            ProxyVerificationResult(
                status = lastStatus,
                latencyMs = null,
                geoReport = IpGeolocationReport(
                    viaRouteLabel = "${config.name} (${config.protocol})",
                    dnsBehaviorNote = dnsBehavior
                ),
                dnsBehavior = dnsBehavior,
                diagnosticMessage = lastDiag
            )
        } catch (_: SocketTimeoutException) {
            ProxyVerificationResult(
                status = ProxyTestStatus.TIMEOUT,
                latencyMs = null,
                geoReport = IpGeolocationReport(),
                dnsBehavior = "Timed out",
                diagnosticMessage = "Timeout: Proxy ${config.host}:${config.port} did not respond in time."
            )
        } catch (e: Exception) {
            ProxyVerificationResult(
                status = ProxyTestStatus.CONNECTION_FAILED,
                latencyMs = null,
                geoReport = IpGeolocationReport(),
                dnsBehavior = "Connection failed",
                diagnosticMessage = "Connection Failed: ${e.localizedMessage ?: "Unreachable proxy host"}"
            )
        }
    }

    /**
     * Fetches the current externally observed Public IP & Approximate Geolocation.
     * If [activeProxy] is non-null, routes strictly through that proxy; otherwise uses
     * the direct connection (`Proxy.NO_PROXY`).
     */
    suspend fun fetchPublicIpAndGeolocation(
        activeProxy: ProxyConfigEntity? = activeConnectedConfig,
        endpointOverride: String? = null
    ): IpGeolocationReport = withContext(Dispatchers.IO) {
        if (activeProxy != null) {
            val verified = testProxyConfiguration(activeProxy, endpointOverride)
            if (verified.status == ProxyTestStatus.WORKING) {
                return@withContext verified.geoReport
            }
            return@withContext IpGeolocationReport(
                publicIp = "Unavailable (Proxy Error)",
                country = "Unknown",
                region = "Unknown",
                city = "Unknown",
                coordinates = "Unavailable",
                ispOrg = "Unavailable",
                latencyMs = verified.latencyMs ?: 0L,
                viaRouteLabel = "${activeProxy.name} (${verified.status.displayLabel})",
                dnsBehaviorNote = verified.dnsBehavior
            )
        }

        val endpoints = if (endpointOverride != null) {
            listOf(endpointOverride)
        } else {
            listOf(GEO_URL_PRIMARY, GEO_URL_SECONDARY, TRACE_URL_FALLBACK)
        }

        for (urlStr in endpoints) {
            var conn: HttpURLConnection? = null
            try {
                val startNanos = System.nanoTime()
                conn = (URL(urlStr).openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    useCaches = false
                    setRequestProperty("Accept", "application/json, text/plain")
                    setRequestProperty("User-Agent", "ClonexaPro-IPInspector/1.0")
                }
                conn.connect()
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val rttMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
                    val parsed = parseGeolocationOrTrace(
                        rawBody = body,
                        latencyMs = rttMs,
                        viaRouteLabel = "Direct Host Connection",
                        dnsBehaviorNote = "Standard Android System DNS"
                    )
                    if (parsed != null) return@withContext parsed
                }
            } catch (_: Exception) {
                // Try next geolocation endpoint
            } finally {
                conn?.disconnect()
            }
        }

        IpGeolocationReport(
            publicIp = "Unavailable",
            country = "Unknown",
            region = "Unknown",
            city = "Unknown",
            coordinates = "Unavailable",
            ispOrg = "Unavailable",
            latencyMs = 0L,
            viaRouteLabel = "Offline / Direct Lookup Unavailable",
            dnsBehaviorNote = "Unavailable"
        )
    }

    /**
     * Activates application-scoped proxy routing (and optional Work Profile recommended global proxy
     * if ClonexaPro is an active Profile Owner) after verifying that the proxy genuinely works.
     */
    suspend fun connectProxy(
        config: ProxyConfigEntity,
        endpointOverride: String? = null
    ): Pair<ProxyVerificationResult, Boolean> = withContext(Dispatchers.IO) {
        val verification = testProxyConfiguration(config, endpointOverride)
        if (verification.status != ProxyTestStatus.WORKING) {
            return@withContext verification to false
        }

        val decryptedPassword = cryptoSecurityManager.decryptCredential(config.encryptedPassword)
        activeConnectedConfig = config

        // Install process-scoped ProxySelector for HTTP/SOCKS routing
        val javaProxyType = if (config.resolvedProtocol == ProxyProtocol.SOCKS5) {
            Proxy.Type.SOCKS
        } else {
            Proxy.Type.HTTP
        }
        val proxyAddress = InetSocketAddress.createUnresolved(config.host, config.port)
        val activeJavaProxy = Proxy(javaProxyType, proxyAddress)

        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                if (uri == null) return listOf(Proxy.NO_PROXY)
                val host = uri.host ?: ""
                if (host == "localhost" || host == "127.0.0.1") {
                    return listOf(Proxy.NO_PROXY)
                }
                return listOf(activeJavaProxy)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                // Reported via test status & UI telemetry
            }
        })

        if (config.username.isNotBlank() || decryptedPassword.isNotBlank()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(
                        config.username,
                        decryptedPassword.toCharArray()
                    )
                }
            })
        } else {
            Authenticator.setDefault(null)
        }

        // If ClonexaPro is an active Work Profile Owner, apply recommended global proxy inside the Work Profile
        val workProfileApplied = applyWorkProfileRecommendedProxyIfPermitted(config)
        verification to workProfileApplied
    }

    /**
     * Disconnects the active proxy, restores the default ProxySelector, clears in-memory
     * Authenticator credentials, and clears any Work Profile proxy policy.
     */
    fun disconnectActiveProxy() {
        activeConnectedConfig = null
        try {
            ProxySelector.setDefault(defaultProxySelector)
            Authenticator.setDefault(null)
        } catch (_: Exception) {
            // Ignored
        }
        clearWorkProfileRecommendedProxyIfPermitted()
    }

    private data class HttpOutcome(
        val status: ProxyTestStatus,
        val body: String = "",
        val diagnostic: String = ""
    )

    private fun executeSingleProxiedGet(
        urlStr: String,
        config: ProxyConfigEntity,
        decryptedPassword: String
    ): HttpOutcome {
        if (config.resolvedProtocol == ProxyProtocol.HTTPS) {
            return executeHttpsTlsTunnelGet(urlStr, config, decryptedPassword)
        }
        if (config.resolvedProtocol == ProxyProtocol.HTTP) {
            return executeHttpPlainProxyGet(urlStr, config, decryptedPassword)
        }

        val javaProxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port))
        var conn: HttpURLConnection? = null

        val prevAuth = if (config.username.isNotBlank() || decryptedPassword.isNotBlank()) {
            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(config.username, decryptedPassword.toCharArray())
                }
            })
            true
        } else {
            false
        }

        return try {
            conn = (URL(urlStr).openConnection(javaProxy) as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json, text/plain")
                setRequestProperty("User-Agent", "ClonexaPro-ProxyVerifier/1.0")
            }
            conn.connect()
            val code = conn.responseCode
            when (code) {
                HttpURLConnection.HTTP_PROXY_AUTH, HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    HttpOutcome(
                        status = ProxyTestStatus.AUTH_FAILED,
                        diagnostic = "Authentication Failed (HTTP $code): Proxy rejected credentials."
                    )
                }
                in 200..299 -> {
                    val text = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    HttpOutcome(
                        status = ProxyTestStatus.WORKING,
                        body = text,
                        diagnostic = "Working"
                    )
                }
                else -> {
                    HttpOutcome(
                        status = ProxyTestStatus.CONNECTION_FAILED,
                        diagnostic = "Proxy returned unexpected HTTP status $code."
                    )
                }
            }
        } catch (_: SocketTimeoutException) {
            HttpOutcome(
                status = ProxyTestStatus.TIMEOUT,
                diagnostic = "Timeout while waiting for response through ${config.host}:${config.port}."
            )
        } catch (e: UnknownHostException) {
            HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "DNS / Host Unreachable: ${e.localizedMessage ?: config.host}"
            )
        } catch (e: ConnectException) {
            HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "Connection Refused by ${config.host}:${config.port}."
            )
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Proxy error"
            if (msg.contains("407") || msg.contains("Authentication", ignoreCase = true)) {
                HttpOutcome(
                    status = ProxyTestStatus.AUTH_FAILED,
                    diagnostic = "Authentication Failed: $msg"
                )
            } else {
                HttpOutcome(
                    status = ProxyTestStatus.CONNECTION_FAILED,
                    diagnostic = "Connection Failed: $msg"
                )
            }
        } finally {
            conn?.disconnect()
            if (prevAuth && activeConnectedConfig == null) {
                Authenticator.setDefault(null)
            }
        }
    }

    /**
     * Executes an HTTP or HTTPS request through an HTTP proxy over a real TCP socket,
     * preserving Basic Proxy-Authorization headers across both HTTP absolute-URI forwarding
     * and HTTPS CONNECT tunneling.
     */
    private fun executeHttpPlainProxyGet(
        urlStr: String,
        config: ProxyConfigEntity,
        decryptedPassword: String
    ): HttpOutcome {
        val parsedUrl = URL(urlStr)
        val isHttpsTarget = parsedUrl.protocol.equals("https", ignoreCase = true)
        val targetHost = parsedUrl.host
        val targetPort = if (parsedUrl.port != -1) parsedUrl.port else if (isHttpsTarget) 443 else 80
        val path = parsedUrl.file.ifEmpty { "/" }

        val authHeader = if (config.username.isNotBlank() || decryptedPassword.isNotBlank()) {
            val token = encodeBase64Basic("${config.username}:$decryptedPassword")
            "Proxy-Authorization: Basic $token\r\n"
        } else {
            ""
        }

        val rawSocket = Socket()
        return try {
            rawSocket.soTimeout = READ_TIMEOUT_MS
            rawSocket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)

            if (!isHttpsTarget) {
                val req = "GET $urlStr HTTP/1.1\r\n" +
                    "Host: $targetHost\r\n" +
                    authHeader +
                    "User-Agent: ClonexaPro-ProxyVerifier/1.0\r\n" +
                    "Accept: application/json, text/plain\r\n" +
                    "Connection: close\r\n\r\n"
                rawSocket.getOutputStream().write(req.toByteArray(StandardCharsets.UTF_8))
                rawSocket.getOutputStream().flush()

                val reader = BufferedReader(InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8))
                val statusLine = reader.readLine() ?: return HttpOutcome(
                    status = ProxyTestStatus.CONNECTION_FAILED,
                    diagnostic = "HTTP proxy closed connection without a response."
                )
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                return when {
                    statusLine.contains(" 407 ") || statusLine.contains(" 401 ") -> HttpOutcome(
                        status = ProxyTestStatus.AUTH_FAILED,
                        diagnostic = "Authentication Failed (HTTP 407): Proxy rejected credentials."
                    )
                    statusLine.contains(" 200 ") -> {
                        val body = reader.readText()
                        HttpOutcome(status = ProxyTestStatus.WORKING, body = body, diagnostic = "Working")
                    }
                    else -> HttpOutcome(
                        status = ProxyTestStatus.CONNECTION_FAILED,
                        diagnostic = "HTTP proxy returned status: $statusLine"
                    )
                }
            } else {
                val connectCmd = "CONNECT $targetHost:$targetPort HTTP/1.1\r\n" +
                    "Host: $targetHost:$targetPort\r\n" +
                    authHeader +
                    "Proxy-Connection: keep-alive\r\n\r\n"
                rawSocket.getOutputStream().write(connectCmd.toByteArray(StandardCharsets.UTF_8))
                rawSocket.getOutputStream().flush()

                val reader = BufferedReader(InputStreamReader(rawSocket.getInputStream(), StandardCharsets.UTF_8))
                val statusLine = reader.readLine() ?: return HttpOutcome(
                    status = ProxyTestStatus.CONNECTION_FAILED,
                    diagnostic = "HTTP proxy closed connection during CONNECT."
                )
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                if (statusLine.contains(" 407 ") || statusLine.contains(" 401 ")) {
                    return HttpOutcome(
                        status = ProxyTestStatus.AUTH_FAILED,
                        diagnostic = "Authentication Failed (HTTP 407): Proxy rejected credentials."
                    )
                }
                if (!statusLine.contains(" 200 ")) {
                    return HttpOutcome(
                        status = ProxyTestStatus.CONNECTION_FAILED,
                        diagnostic = "HTTP Proxy CONNECT rejected: $statusLine"
                    )
                }

                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val targetSsl = sslFactory.createSocket(rawSocket, targetHost, targetPort, true) as SSLSocket
                targetSsl.soTimeout = READ_TIMEOUT_MS
                targetSsl.startHandshake()

                val getReq = "GET $path HTTP/1.1\r\n" +
                    "Host: $targetHost\r\n" +
                    "User-Agent: ClonexaPro-ProxyVerifier/1.0\r\n" +
                    "Accept: application/json, text/plain\r\n" +
                    "Connection: close\r\n\r\n"
                targetSsl.outputStream.write(getReq.toByteArray(StandardCharsets.UTF_8))
                targetSsl.outputStream.flush()

                val targetReader = BufferedReader(InputStreamReader(targetSsl.inputStream, StandardCharsets.UTF_8))
                val respStatus = targetReader.readLine() ?: ""
                while (true) {
                    val header = targetReader.readLine() ?: break
                    if (header.isEmpty()) break
                }
                val body = targetReader.readText()
                targetSsl.close()

                if (respStatus.contains(" 200 ")) {
                    HttpOutcome(status = ProxyTestStatus.WORKING, body = body, diagnostic = "Working")
                } else {
                    HttpOutcome(
                        status = ProxyTestStatus.CONNECTION_FAILED,
                        diagnostic = "Target endpoint returned: $respStatus"
                    )
                }
            }
        } catch (_: SocketTimeoutException) {
            HttpOutcome(
                status = ProxyTestStatus.TIMEOUT,
                diagnostic = "Timeout while waiting for response through ${config.host}:${config.port}."
            )
        } catch (e: UnknownHostException) {
            HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "DNS / Host Unreachable: ${e.localizedMessage ?: config.host}"
            )
        } catch (_: ConnectException) {
            HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "Connection Refused by ${config.host}:${config.port}."
            )
        } catch (e: Exception) {
            HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "Connection Failed: ${e.localizedMessage ?: "Proxy error"}"
            )
        } finally {
            try {
                rawSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Performs a real RFC 1928 SOCKS5 greeting and RFC 1929 Username/Password authentication check.
     */
    private fun verifySocks5HandshakeAndAuth(
        host: String,
        port: Int,
        username: String,
        password: String
    ): ProxyTestStatus {
        val socket = Socket()
        return try {
            socket.soTimeout = READ_TIMEOUT_MS
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            val hasCreds = username.isNotBlank() || password.isNotBlank()
            if (hasCreds) {
                // Offer No-Auth (0x00) and Username/Password (0x02)
                out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                // Offer No-Auth (0x00) only
                out.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            out.flush()

            val version = input.read()
            val method = input.read()
            if (version != 0x05) {
                return ProxyTestStatus.CONNECTION_FAILED
            }
            if (method == 0xFF || (method == 0x02 && !hasCreds)) {
                return ProxyTestStatus.AUTH_FAILED
            }

            if (method == 0x02) {
                val uBytes = username.toByteArray(StandardCharsets.UTF_8)
                val pBytes = password.toByteArray(StandardCharsets.UTF_8)
                if (uBytes.size > 255 || pBytes.size > 255) {
                    return ProxyTestStatus.AUTH_FAILED
                }
                val authPacket = ByteArray(3 + uBytes.size + pBytes.size)
                authPacket[0] = 0x01 // Subnegotiation version 1
                authPacket[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authPacket, 2, uBytes.size)
                authPacket[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authPacket, 3 + uBytes.size, pBytes.size)

                out.write(authPacket)
                out.flush()

                val authVer = input.read()
                val authStatus = input.read()
                if (authVer < 0 || authStatus != 0x00) {
                    return ProxyTestStatus.AUTH_FAILED
                }
            }
            ProxyTestStatus.WORKING
        } catch (_: SocketTimeoutException) {
            ProxyTestStatus.TIMEOUT
        } catch (_: Exception) {
            ProxyTestStatus.CONNECTION_FAILED
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Verifies TLS handshake with an HTTPS proxy server and checks credentials.
     */
    private fun verifyHttpsTlsProxyHandshake(
        host: String,
        port: Int,
        username: String,
        password: String
    ): ProxyTestStatus {
        val rawSocket = Socket()
        return try {
            rawSocket.soTimeout = READ_TIMEOUT_MS
            rawSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.soTimeout = READ_TIMEOUT_MS
            sslSocket.startHandshake()

            val out = sslSocket.outputStream
            val authHeader = if (username.isNotBlank() || password.isNotBlank()) {
                val token = encodeBase64Basic("$username:$password")
                "Proxy-Authorization: Basic $token\r\n"
            } else {
                ""
            }
            val probe = "CONNECT 1.1.1.1:443 HTTP/1.1\r\nHost: 1.1.1.1:443\r\n$authHeader\r\n"
            out.write(probe.toByteArray(StandardCharsets.UTF_8))
            out.flush()

            val reader = BufferedReader(InputStreamReader(sslSocket.inputStream, StandardCharsets.UTF_8))
            val statusLine = reader.readLine() ?: return ProxyTestStatus.CONNECTION_FAILED
            sslSocket.close()

            when {
                statusLine.contains(" 407 ") || statusLine.contains(" 401 ") -> ProxyTestStatus.AUTH_FAILED
                statusLine.contains(" 200 ") -> ProxyTestStatus.WORKING
                else -> ProxyTestStatus.CONNECTION_FAILED
            }
        } catch (_: SocketTimeoutException) {
            ProxyTestStatus.TIMEOUT
        } catch (_: Exception) {
            ProxyTestStatus.CONNECTION_FAILED
        } finally {
            try {
                rawSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun executeHttpsTlsTunnelGet(
        urlStr: String,
        config: ProxyConfigEntity,
        decryptedPassword: String
    ): HttpOutcome {
        val parsedUrl = URL(urlStr)
        val targetHost = parsedUrl.host
        val targetPort = if (parsedUrl.port != -1) parsedUrl.port else 443
        val path = parsedUrl.file.ifEmpty { "/" }

        val rawSocket = Socket()
        return try {
            rawSocket.soTimeout = READ_TIMEOUT_MS
            rawSocket.connect(InetSocketAddress(config.host, config.port), CONNECT_TIMEOUT_MS)
            val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val proxySsl = sslFactory.createSocket(rawSocket, config.host, config.port, true) as SSLSocket
            proxySsl.soTimeout = READ_TIMEOUT_MS
            proxySsl.startHandshake()

            val authHeader = if (config.username.isNotBlank() || decryptedPassword.isNotBlank()) {
                val token = encodeBase64Basic("${config.username}:$decryptedPassword")
                "Proxy-Authorization: Basic $token\r\n"
            } else {
                ""
            }

            val connectCmd = "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\n$authHeader\r\n"
            proxySsl.outputStream.write(connectCmd.toByteArray(StandardCharsets.UTF_8))
            proxySsl.outputStream.flush()

            val reader = BufferedReader(InputStreamReader(proxySsl.inputStream, StandardCharsets.UTF_8))
            val statusLine = reader.readLine() ?: return HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "HTTPS proxy closed connection during CONNECT."
            )
            if (statusLine.contains(" 407 ") || statusLine.contains(" 401 ")) {
                return HttpOutcome(
                    status = ProxyTestStatus.AUTH_FAILED,
                    diagnostic = "Authentication Failed: $statusLine"
                )
            }
            if (!statusLine.contains(" 200 ")) {
                return HttpOutcome(
                    status = ProxyTestStatus.CONNECTION_FAILED,
                    diagnostic = "HTTPS Proxy CONNECT rejected: $statusLine"
                )
            }
            // Drain CONNECT response headers
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }

            // Wrap tunneled socket in TLS for target HTTPS endpoint
            val targetSsl = sslFactory.createSocket(proxySsl, targetHost, targetPort, true) as SSLSocket
            targetSsl.startHandshake()
            val getReq = "GET $path HTTP/1.1\r\nHost: $targetHost\r\nUser-Agent: ClonexaPro/1.0\r\nConnection: close\r\n\r\n"
            targetSsl.outputStream.write(getReq.toByteArray(StandardCharsets.UTF_8))
            targetSsl.outputStream.flush()

            val targetReader = BufferedReader(InputStreamReader(targetSsl.inputStream, StandardCharsets.UTF_8))
            val respStatus = targetReader.readLine() ?: ""
            while (true) {
                val header = targetReader.readLine() ?: break
                if (header.isEmpty()) break
            }
            val body = targetReader.readText()
            targetSsl.close()

            if (respStatus.contains(" 200 ")) {
                HttpOutcome(status = ProxyTestStatus.WORKING, body = body, diagnostic = "Working")
            } else {
                HttpOutcome(
                    status = ProxyTestStatus.CONNECTION_FAILED,
                    diagnostic = "Target endpoint returned: $respStatus"
                )
            }
        } catch (_: SocketTimeoutException) {
            HttpOutcome(status = ProxyTestStatus.TIMEOUT, diagnostic = "HTTPS proxy tunnel timed out.")
        } catch (e: Exception) {
            HttpOutcome(
                status = ProxyTestStatus.CONNECTION_FAILED,
                diagnostic = "HTTPS proxy error: ${e.localizedMessage ?: "TLS failure"}"
            )
        } finally {
            try {
                rawSocket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseGeolocationOrTrace(
        rawBody: String,
        latencyMs: Long,
        viaRouteLabel: String,
        dnsBehaviorNote: String
    ): IpGeolocationReport? {
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{")) {
            return parseGeolocationJson(
                rawJson = trimmed,
                latencyMs = latencyMs,
                viaRouteLabel = viaRouteLabel,
                dnsBehaviorNote = dnsBehaviorNote
            )
        }
        // Fallback parser for Cloudflare /cdn-cgi/trace key=value format
        if (trimmed.contains("ip=")) {
            val map = trimmed.lines()
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
                }
                .toMap()
            val ip = map["ip"] ?: return null
            val loc = map["loc"] ?: "Unknown"
            val colo = map["colo"] ?: "Unavailable"
            return IpGeolocationReport(
                publicIp = ip,
                country = loc,
                region = "Cloudflare Edge ($colo)",
                city = "Unknown",
                coordinates = "Unavailable",
                ispOrg = "Cloudflare Trace Verification",
                latencyMs = latencyMs,
                viaRouteLabel = viaRouteLabel,
                dnsBehaviorNote = dnsBehaviorNote
            )
        }
        return null
    }

    private fun applyWorkProfileRecommendedProxyIfPermitted(config: ProxyConfigEntity): Boolean {
        if (config.resolvedProtocol != ProxyProtocol.HTTP) return false
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return false
            if (dpm.isProfileOwnerApp(context.packageName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val admin = ClonexaDeviceAdminReceiver.getComponentName(context)
                val proxyInfo = ProxyInfo.buildDirectProxy(config.host, config.port)
                dpm.setRecommendedGlobalProxy(admin, proxyInfo)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun clearWorkProfileRecommendedProxyIfPermitted() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
            if (dpm.isProfileOwnerApp(context.packageName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val admin = ClonexaDeviceAdminReceiver.getComponentName(context)
                dpm.setRecommendedGlobalProxy(admin, null)
            }
        } catch (_: Exception) {
            // Ignored
        }
    }
}
