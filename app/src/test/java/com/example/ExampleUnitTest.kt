package com.example

import com.example.model.AppLockCredentialType
import com.example.model.CompatibilityStatus
import com.example.model.IpLookupMode
import com.example.model.IpVersionClassification
import com.example.model.IsolationTier
import com.example.model.ManagedAppInstanceEntity
import com.example.model.PrivacyDiagnosticStatus
import com.example.model.ProxyConfigEntity
import com.example.model.ProxyProtocol
import com.example.network.MyIpInspectorEngine
import com.example.network.NetworkSpeedTestEngine
import com.example.network.ProxyManagerEngine
import com.example.security.CryptoSecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun isolationTierDistinguishesFullDataSeparation() {
        assertTrue(IsolationTier.ISOLATED_PROFILE_ACTIVE.providesFullDataSeparation)
        assertFalse(IsolationTier.MANAGED_TASK_LAUNCH.providesFullDataSeparation)
        assertFalse(IsolationTier.UI_MANAGED_ENTRY.providesFullDataSeparation)
    }

    @Test
    fun compatibilityStatusEnforcesSecurityBoundaries() {
        assertTrue(CompatibilityStatus.COMPATIBLE_ISOLATED.canCreateInstance)
        assertTrue(CompatibilityStatus.COMPATIBLE_WORK_PROFILE_ONLY.canCreateInstance)
        assertFalse(CompatibilityStatus.RESTRICTED_SECURE_ENV.canCreateInstance)
        assertFalse(CompatibilityStatus.INCOMPATIBLE_SYSTEM_CORE.canCreateInstance)
    }

    @Test
    fun managedAppInstanceResolvesIsolationTierCorrectly() {
        val instance = ManagedAppInstanceEntity(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            accountLabel = "Work Line",
            accountCategory = "Messaging",
            badgeColorHex = "#00E5FF",
            isolationTier = IsolationTier.ISOLATED_PROFILE_ACTIVE.name
        )
        assertEquals(IsolationTier.ISOLATED_PROFILE_ACTIVE, instance.resolvedTier)
    }

    @Test
    fun networkSpeedTestEngineComputesRealMbpsCorrectly() {
        val mbps = NetworkSpeedTestEngine.computeMbps(
            bytesTransferred = 10_000_000L,
            elapsedNanos = 2_000_000_000L
        )
        assertEquals(40.0, mbps, 0.01)
        assertEquals(0.0, NetworkSpeedTestEngine.computeMbps(0L, 1_000_000L), 0.0)
    }

    @Test
    fun networkSpeedTestEngineComputesRealPingAndJitter() {
        val (ping, jitter) = NetworkSpeedTestEngine.computePingAndJitter(
            listOf(20.0, 24.0, 22.0, 26.0)
        )
        assertEquals(23.0, ping, 0.1)
        assertEquals(3.3, jitter, 0.1)
    }

    @Test
    fun proxyInputValidationRejectsMalformedHostAndPorts() {
        val valid = ProxyManagerEngine.validateProxyInput("Frankfurt Relay", "198.51.100.42", "8080")
        assertTrue(valid.isValid)
        assertEquals("198.51.100.42", valid.sanitizedHost)
        assertEquals(8080, valid.parsedPort)

        val validHostname = ProxyManagerEngine.validateProxyInput("Corp Proxy", "proxy.clonexapro.example.com", "3128")
        assertTrue(validHostname.isValid)

        val blankName = ProxyManagerEngine.validateProxyInput("   ", "198.51.100.42", "8080")
        assertFalse(blankName.isValid)

        val urlInHost = ProxyManagerEngine.validateProxyInput("Bad Host", "http://198.51.100.42/path", "8080")
        assertFalse(urlInHost.isValid)

        val invalidPortLow = ProxyManagerEngine.validateProxyInput("Bad Port", "198.51.100.42", "0")
        assertFalse(invalidPortLow.isValid)

        val invalidPortHigh = ProxyManagerEngine.validateProxyInput("Bad Port", "198.51.100.42", "70000")
        assertFalse(invalidPortHigh.isValid)
    }

    @Test
    fun geolocationJsonParserExtractsIpCountryRegionCoordinatesAndIsp() {
        val sampleJson = """
            {
              "ip": "203.0.113.88",
              "success": true,
              "country": "Japan",
              "region": "Tokyo",
              "city": "Chiyoda",
              "latitude": 35.6895,
              "longitude": 139.6917,
              "connection": {
                "isp": "NTT Communications",
                "org": "Tokyo Cloud Edge"
              }
            }
        """.trimIndent()

        val report = ProxyManagerEngine.parseGeolocationJson(
            rawJson = sampleJson,
            latencyMs = 45L,
            viaRouteLabel = "Tokyo Proxy (HTTP)",
            dnsBehaviorNote = "HTTP CONNECT Tunnel"
        )
        assertNotNull(report)
        assertEquals("203.0.113.88", report!!.publicIp)
        assertEquals("Japan", report.country)
        assertEquals("Tokyo", report.region)
        assertEquals("Chiyoda", report.city)
        assertTrue(report.coordinates.contains("35.690") || report.coordinates.contains("35.689"))
        assertTrue(report.ispOrg.contains("NTT Communications"))

        val failedJson = """{"ip":"203.0.113.88","success":false}"""
        assertNull(
            ProxyManagerEngine.parseGeolocationJson(
                rawJson = failedJson,
                latencyMs = 10L,
                viaRouteLabel = "Test",
                dnsBehaviorNote = "Test"
            )
        )
    }

    @Test
    fun myIpInspectorClassifiesIpv4Ipv6AndPrivateVsPublicScope() {
        assertTrue(MyIpInspectorEngine.isIpv4Address("203.0.113.45"))
        assertFalse(MyIpInspectorEngine.isIpv4Address("2001:4860:4860::8888"))
        assertTrue(MyIpInspectorEngine.isIpv6Address("2001:4860:4860::8888"))
        assertFalse(MyIpInspectorEngine.isIpv6Address("203.0.113.45"))

        // Private / Local vs Public Routable classification
        assertTrue(MyIpInspectorEngine.isPrivateOrReservedIp("192.168.1.25"))
        assertTrue(MyIpInspectorEngine.isPrivateOrReservedIp("10.0.0.12"))
        assertTrue(MyIpInspectorEngine.isPrivateOrReservedIp("172.20.4.1"))
        assertTrue(MyIpInspectorEngine.isPrivateOrReservedIp("127.0.0.1"))
        assertTrue(MyIpInspectorEngine.isPrivateOrReservedIp("fe80::1ff:fe23:4567:890a"))
        assertFalse(MyIpInspectorEngine.isPrivateOrReservedIp("198.51.100.77"))
        assertFalse(MyIpInspectorEngine.isPrivateOrReservedIp("2001:4860:4860::8888"))

        assertEquals("Public Routable IPv4", MyIpInspectorEngine.classifyIpScope("198.51.100.77"))
        assertEquals("Private / Local Network (IPv4)", MyIpInspectorEngine.classifyIpScope("192.168.1.25"))
        assertEquals("Public Routable IPv6", MyIpInspectorEngine.classifyIpScope("2001:4860:4860::8888"))

        assertEquals(
            IpVersionClassification.DUAL_STACK,
            MyIpInspectorEngine.resolveVersionClassification("198.51.100.77", "2001:4860:4860::8888")
        )
        assertEquals(
            IpVersionClassification.IPV4,
            MyIpInspectorEngine.resolveVersionClassification("198.51.100.77", "Unavailable")
        )
        assertEquals(
            IpVersionClassification.IPV6,
            MyIpInspectorEngine.resolveVersionClassification("Unavailable", "2001:4860:4860::8888")
        )
        assertEquals(
            IpVersionClassification.UNAVAILABLE,
            MyIpInspectorEngine.resolveVersionClassification("Unavailable", "Unavailable")
        )
    }

    @Test
    fun myIpInspectorParsesExternalJsonPlainTextAndTraceWithoutFabricatingData() {
        val ipifyV6 = """{"ip":"2606:4700:4700::1111"}"""
        val parsedV6 = MyIpInspectorEngine.parseExternalIpResponse(ipifyV6, "ipify IPv6")
        assertNotNull(parsedV6)
        assertEquals("2606:4700:4700::1111", parsedV6!!.ip)
        assertEquals("Unavailable", parsedV6.country)
        assertEquals("Unavailable", parsedV6.approximateCoordinates)

        val plainIpv4 = "198.51.100.210\n"
        val parsedPlain = MyIpInspectorEngine.parseExternalIpResponse(plainIpv4, "icanhazip")
        assertNotNull(parsedPlain)
        assertEquals("198.51.100.210", parsedPlain!!.ip)

        val invalidHtml = "<html><body>502 Bad Gateway</body></html>"
        assertNull(MyIpInspectorEngine.parseExternalIpResponse(invalidHtml, "error"))
    }

    @Test
    fun privacyConsistencyReportCalculatesTransparentScoreAndWarnings() {
        // 1. High-consistency proxy scenario: Proxy IP differs from Direct IP, Time Zone matches, Locale matches
        val alignedReport = MyIpInspectorEngine.calculatePrivacyConsistencyReport(
            mode = IpLookupMode.CURRENT_PROXY,
            primaryPublicIp = "203.0.113.199",
            directKnownIp = "198.51.100.44",
            proxyKnownIp = "203.0.113.199",
            isProxyActiveAndVerified = true,
            proxyProtocolName = "SOCKS5",
            ipCountry = "Switzerland",
            ipTimeZone = "Europe/Zurich",
            localInterfaceIp = "192.168.1.15 (Private RFC 1918)",
            deviceTimeZone = "Europe/Zurich",
            deviceLocale = java.util.Locale.forLanguageTag("de-CH")
        )
        assertTrue(alignedReport.scorePercent in 90..100)
        assertEquals(5, alignedReport.checks.size)
        assertTrue(alignedReport.checks.all { it.status == PrivacyDiagnosticStatus.PASS })

        // 2. Mismatched Time Zone & Identical Direct/Proxy IP warning scenario
        val warningReport = MyIpInspectorEngine.calculatePrivacyConsistencyReport(
            mode = IpLookupMode.CURRENT_PROXY,
            primaryPublicIp = "198.51.100.44",
            directKnownIp = "198.51.100.44",
            proxyKnownIp = "198.51.100.44",
            isProxyActiveAndVerified = true,
            proxyProtocolName = "HTTP",
            ipCountry = "Japan",
            ipTimeZone = "Asia/Tokyo",
            localInterfaceIp = "192.168.1.15 (Private RFC 1918)",
            deviceTimeZone = "America/New_York",
            deviceLocale = java.util.Locale.US
        )
        assertTrue(warningReport.scorePercent < alignedReport.scorePercent)
        assertTrue(warningReport.checks.any { it.status == PrivacyDiagnosticStatus.WARNING })
    }
}

