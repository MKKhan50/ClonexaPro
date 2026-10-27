package com.example

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ClonexaDatabase
import com.example.data.ClonexaWorkspaceRepository
import com.example.isolation.AppDiscoveryAndIsolationEngine
import com.example.model.IpLookupMode
import com.example.model.IpVersionClassification
import com.example.model.IsolationTier
import com.example.model.ManagedAppInstanceEntity
import com.example.model.ProxyConfigEntity
import com.example.model.ProxyProtocol
import com.example.model.ProxyTestStatus
import com.example.model.SpeedTestResultEntity
import com.example.network.MyIpInspectorEngine
import com.example.network.NetworkSpeedTestEngine
import com.example.network.ProxyManagerEngine
import com.example.security.BiometricAuthLayerStatus
import com.example.security.BiometricSpaceAuthViewModel
import com.example.security.CryptoSecurityManager
import com.example.security.PrivateSpaceBiometricGateRegistry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyAppIdentityAndResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ClonexaPro", appName)
        assertEquals("com.clonexapro.app", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun verifyCryptoSecurityManagerPinAndProxyCredentialEncryption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context)

        val token = crypto.createProtectedPinToken("4829")
        assertTrue(token.isNotBlank())
        assertTrue(crypto.verifyPinToken("4829", token))
        assertFalse(crypto.verifyPinToken("9999", token))

        // Verify App Lock PIN & Password validation and Recovery Code hashing
        assertTrue(crypto.validateAppLockSecret("482910", com.example.model.AppLockCredentialType.PIN).first)
        assertFalse(crypto.validateAppLockSecret("12", com.example.model.AppLockCredentialType.PIN).first)
        assertTrue(crypto.validateAppLockSecret("Clonexa#2026", com.example.model.AppLockCredentialType.PASSWORD).first)
        assertFalse(crypto.validateAppLockSecret("short", com.example.model.AppLockCredentialType.PASSWORD).first)

        val recoveryCode = crypto.generateAppLockRecoveryCode()
        assertTrue(recoveryCode.startsWith("CLX-"))
        val recoveryToken = crypto.createProtectedPinToken(crypto.normalizeRecoveryCode(recoveryCode))
        assertTrue(crypto.verifyPinToken(crypto.normalizeRecoveryCode(recoveryCode.lowercase()), recoveryToken))

        // Verify Proxy Credential Encryption & Decryption
        val rawSecret = "Sup3rS3cretProxyPass!2026"
        val encrypted = crypto.encryptCredential(rawSecret)
        assertTrue(encrypted.isNotBlank())
        assertFalse("Ciphertext must not expose raw password", encrypted.contains(rawSecret))
        assertEquals(rawSecret, crypto.decryptCredential(encrypted))
    }

    @Test
    fun verifyIsolationEngineAndWorkProfileProvisioningIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context)
        val engine = AppDiscoveryAndIsolationEngine(context, crypto)

        val envReport = engine.inspectHostEnvironment()
        assertTrue(envReport.detectedProfileCount >= 1)
        assertTrue(envReport.activeEngineSummary.isNotBlank())

        val targetApps = engine.evaluateTargetEcosystemApps()
        assertTrue(targetApps.any { it.packageName == "com.whatsapp" })
        assertTrue(targetApps.any { it.packageName == "org.telegram.messenger" })

        val workIntent = engine.createWorkProfileProvisioningIntent()
        assertEquals(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE, workIntent.action)
        assertNotNull(
            workIntent.getParcelableExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                android.content.ComponentName::class.java
            )
        )
    }

    @Test
    fun verifyRoomDatabaseInstanceLifecycleAndAuditLogs() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ClonexaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ClonexaWorkspaceRepository(
            db.managedInstanceDao(),
            db.securityAuditDao(),
            db.speedTestResultDao(),
            db.proxyConfigDao()
        )

        val id = repo.createInstance(
            ManagedAppInstanceEntity(
                packageName = "org.telegram.messenger",
                appName = "Telegram",
                accountLabel = "Work Profile #2",
                accountCategory = "Messaging",
                badgeColorHex = "#00E5FF",
                isolationTier = IsolationTier.MANAGED_TASK_LAUNCH.name,
                isInPrivateSpace = false,
                notes = "Dedicated business line"
            )
        )
        assertTrue(id > 0L)

        val all = repo.allInstances.first()
        assertEquals(1, all.size)
        assertEquals("Work Profile #2", all.first().accountLabel)

        repo.togglePrivateSpace(all.first(), moveToPrivate = true)
        val updated = repo.allInstances.first().first()
        assertTrue(updated.isInPrivateSpace)

        repo.recordLaunchEvent(updated, succeeded = true, detailMessage = "Launched Telegram")
        val afterLaunch = repo.allInstances.first().first()
        assertEquals(1, afterLaunch.launchCount)

        val logs = repo.auditLogs.first()
        assertTrue(logs.size >= 3)

        repo.deleteInstance(afterLaunch)
        assertTrue(repo.allInstances.first().isEmpty())
        db.close()
    }

    @Test
    fun verifySpeedTestPersistenceAndConnectionDetection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val speedEngine = NetworkSpeedTestEngine(context)
        val netInfo = speedEngine.detectNetworkConnection()
        assertTrue(netInfo.connectionTypeLabel.isNotBlank())

        val db = Room.inMemoryDatabaseBuilder(context, ClonexaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ClonexaWorkspaceRepository(
            db.managedInstanceDao(),
            db.securityAuditDao(),
            db.speedTestResultDao(),
            db.proxyConfigDao()
        )

        val savedId = repo.saveSpeedTestResult(
            SpeedTestResultEntity(
                downloadMbps = 128.45,
                uploadMbps = 42.10,
                pingMs = 14.2,
                jitterMs = 2.1,
                connectionType = "Wi-Fi",
                serverName = "Cloudflare Edge CDN (speed.cloudflare.com)",
                bytesTransferred = 12_500_000L
            )
        )
        assertTrue(savedId > 0L)

        val history = repo.speedTestHistory!!.first()
        assertEquals(1, history.size)
        assertEquals(128.45, history.first().downloadMbps, 0.01)

        repo.clearSpeedTestHistory()
        assertTrue(repo.speedTestHistory.first().isEmpty())
        db.close()
    }

    @Test
    fun verifyRealHttpProxyConnectionAuthAndExternalIpVerification() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context)
        val proxyEngine = ProxyManagerEngine(context, crypto)

        // Start a real loopback HTTP Proxy ServerSocket that enforces Basic Proxy-Authorization
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { proxyServer ->
            val proxyPort = proxyServer.localPort
            val serverThread = thread(start = true) {
                while (!proxyServer.isClosed) {
                    try {
                        proxyServer.accept().use { client ->
                            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                            val headers = mutableListOf<String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                headers.add(line)
                            }
                            val hasValidAuth = headers.any {
                                it.startsWith("Proxy-Authorization: Basic ", ignoreCase = true) &&
                                    it.contains("dXNlcjpzZWNyZXQ=") // Base64("user:secret")
                            }
                            val out = client.getOutputStream()
                            if (!hasValidAuth) {
                                val resp407 = "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                    "Proxy-Authenticate: Basic realm=\"ClonexaTestProxy\"\r\n" +
                                    "Content-Length: 0\r\nConnection: close\r\n\r\n"
                                out.write(resp407.toByteArray(StandardCharsets.UTF_8))
                            } else {
                                val geoPayload = """{"ip":"198.51.100.77","success":true,"country":"Germany","region":"Hesse","city":"Frankfurt","latitude":50.1109,"longitude":8.6821,"org":"AS24940 Hetzner Proxy Relay"}"""
                                val bytes = geoPayload.toByteArray(StandardCharsets.UTF_8)
                                val resp200 = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                out.write(resp200.toByteArray(StandardCharsets.UTF_8))
                                out.write(bytes)
                            }
                            out.flush()
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }

            // 1. Test with wrong password -> must return AUTH_FAILED
            val badAuthConfig = ProxyConfigEntity(
                id = 1L,
                name = "Frankfurt HTTP Proxy",
                host = "127.0.0.1",
                port = proxyPort,
                protocol = ProxyProtocol.HTTP.name,
                username = "user",
                encryptedPassword = crypto.encryptCredential("wrongpass")
            )
            val failedResult = proxyEngine.testProxyConfiguration(
                config = badAuthConfig,
                targetEndpointOverride = "http://ipwho.is/"
            )
            assertEquals(ProxyTestStatus.AUTH_FAILED, failedResult.status)

            // 2. Test & Connect with valid password -> must return WORKING & verify external IP + geolocation
            val validAuthConfig = badAuthConfig.copy(
                encryptedPassword = crypto.encryptCredential("secret")
            )
            val (connectResult, _) = proxyEngine.connectProxy(
                config = validAuthConfig,
                endpointOverride = "http://ipwho.is/"
            )
            assertEquals(ProxyTestStatus.WORKING, connectResult.status)
            assertEquals("198.51.100.77", connectResult.geoReport.publicIp)
            assertEquals("Germany", connectResult.geoReport.country)
            assertEquals("Hesse", connectResult.geoReport.region)
            assertEquals("Frankfurt", connectResult.geoReport.city)

            proxyEngine.disconnectActiveProxy()
            serverThread.join(2000)
        }
    }

    @Test
    fun verifyRealSocks5AuthenticationFailureDetection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context)
        val proxyEngine = ProxyManagerEngine(context, crypto)

        // Spin up a real loopback SOCKS5 server that requires RFC 1929 auth and rejects bad credentials
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { socksServer ->
            val socksPort = socksServer.localPort
            val serverThread = thread(start = true) {
                try {
                    socksServer.accept().use { client ->
                        val input = client.getInputStream()
                        val out = client.getOutputStream()
                        val ver = input.read()
                        val nMethods = input.read()
                        val methods = ByteArray(nMethods)
                        input.read(methods)
                        if (ver == 0x05) {
                            // Select method 0x02 (Username/Password)
                            out.write(byteArrayOf(0x05, 0x02))
                            out.flush()
                            // Read RFC 1929 subnegotiation and return 0x01, 0x01 (Auth failure)
                            input.read() // subver
                            val uLen = input.read()
                            input.read(ByteArray(uLen))
                            val pLen = input.read()
                            input.read(ByteArray(pLen))
                            out.write(byteArrayOf(0x01, 0x01))
                            out.flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val socksConfig = ProxyConfigEntity(
                id = 2L,
                name = "SOCKS5 Auth Test",
                host = "127.0.0.1",
                port = socksPort,
                protocol = ProxyProtocol.SOCKS5.name,
                username = "socksuser",
                encryptedPassword = crypto.encryptCredential("badpass")
            )
            val result = proxyEngine.testProxyConfiguration(socksConfig)
            assertEquals(ProxyTestStatus.AUTH_FAILED, result.status)
            serverThread.join(2000)
        }
    }

    @Test
    fun verifyMyIpDirectDualStackProxyAwareAndUnreachableHandling() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context)
        val speedEngine = NetworkSpeedTestEngine(context)
        val proxyEngine = ProxyManagerEngine(context, crypto)
        val myIpEngine = MyIpInspectorEngine(context, speedEngine, proxyEngine)

        // 1. Spin up loopback HTTP endpoints for Direct IPv4, Direct IPv6, and Direct Geolocation
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { directServer ->
            val directPort = directServer.localPort
            val directThread = thread(start = true) {
                while (!directServer.isClosed) {
                    try {
                        directServer.accept().use { client ->
                            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                            val reqLine = reader.readLine() ?: ""
                            while (true) {
                                val h = reader.readLine() ?: break
                                if (h.isEmpty()) break
                            }
                            val body = when {
                                reqLine.contains("/v4") -> """{"ip":"198.51.100.44"}"""
                                reqLine.contains("/v6") -> """{"ip":"2001:db8:85a3::8a2e:370:7334"}"""
                                else -> """{"ip":"198.51.100.44","success":true,"country":"Singapore","region":"Central Singapore","city":"Singapore","latitude":1.3521,"longitude":103.8198,"isp":"Singtel Fibre","asn":"AS3758"}"""
                            }
                            val bytes = body.toByteArray(StandardCharsets.UTF_8)
                            val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            client.getOutputStream().write(resp.toByteArray(StandardCharsets.UTF_8))
                            client.getOutputStream().write(bytes)
                            client.getOutputStream().flush()
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }

            val directSnapshot = myIpEngine.inspectMyIp(
                mode = IpLookupMode.DIRECT_CONNECTION,
                activeProxy = null,
                ipv4EndpointOverride = "http://127.0.0.1:$directPort/v4",
                ipv6EndpointOverride = "http://127.0.0.1:$directPort/v6",
                geoEndpointOverride = "http://127.0.0.1:$directPort/geo"
            )
            assertEquals("198.51.100.44", directSnapshot.primaryPublicIp)
            assertEquals("198.51.100.44", directSnapshot.publicIpv4)
            assertEquals("2001:db8:85a3::8a2e:370:7334", directSnapshot.publicIpv6)
            assertEquals(IpVersionClassification.DUAL_STACK, directSnapshot.ipVersion)
            assertEquals("Singapore", directSnapshot.country)
            assertEquals("Central Singapore", directSnapshot.region)
            assertTrue(directSnapshot.ispOrg.contains("Singtel Fibre"))
            assertFalse(directSnapshot.isProxyVerified)

            // 2. Verify Current Proxy IP mode when NO proxy is active refuses to mislabel Direct IP
            val inactiveProxySnapshot = myIpEngine.inspectMyIp(
                mode = IpLookupMode.CURRENT_PROXY,
                activeProxy = null
            )
            assertEquals("Unavailable", inactiveProxySnapshot.primaryPublicIp)
            assertEquals(IpVersionClassification.UNAVAILABLE, inactiveProxySnapshot.ipVersion)
            assertFalse(inactiveProxySnapshot.isProxyVerified)
            assertNotNull(inactiveProxySnapshot.errorMessage)

            // 3. Spin up a real loopback HTTP proxy returning a different Proxy Exit IP (203.0.113.199, Switzerland)
            ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { proxyServer ->
                val proxyPort = proxyServer.localPort
                val proxyThread = thread(start = true) {
                    while (!proxyServer.isClosed) {
                        try {
                            proxyServer.accept().use { client ->
                                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
                                while (true) {
                                    val h = reader.readLine() ?: break
                                    if (h.isEmpty()) break
                                }
                                val payload = """{"ip":"203.0.113.199","success":true,"country":"Switzerland","region":"Zurich","city":"Zurich","latitude":47.3769,"longitude":8.5417,"org":"AS3303 Swisscom Proxy Exit"}"""
                                val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                                val resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                client.getOutputStream().write(resp.toByteArray(StandardCharsets.UTF_8))
                                client.getOutputStream().write(bytes)
                                client.getOutputStream().flush()
                            }
                        } catch (_: Exception) {
                            break
                        }
                    }
                }

                val activeProxy = ProxyConfigEntity(
                    id = 10L,
                    name = "Zurich Swiss Proxy",
                    host = "127.0.0.1",
                    port = proxyPort,
                    protocol = ProxyProtocol.HTTP.name,
                    isActiveConnected = true
                )
                val proxySnapshot = myIpEngine.inspectMyIp(
                    mode = IpLookupMode.CURRENT_PROXY,
                    activeProxy = activeProxy,
                    geoEndpointOverride = "http://ipwho.is/"
                )
                assertEquals("203.0.113.199", proxySnapshot.primaryPublicIp)
                assertEquals("203.0.113.199", proxySnapshot.publicIpv4)
                assertEquals(IpVersionClassification.IPV4, proxySnapshot.ipVersion)
                assertEquals("Switzerland", proxySnapshot.country)
                assertEquals("Zurich", proxySnapshot.city)
                assertTrue(proxySnapshot.isProxyVerified)
                proxyThread.join(1000)
            }

            // 4. Verify unreachable endpoint never produces fabricated results
            val unreachableSnapshot = myIpEngine.inspectMyIp(
                mode = IpLookupMode.DIRECT_CONNECTION,
                activeProxy = null,
                ipv4EndpointOverride = "http://127.0.0.1:1/unreachable",
                ipv6EndpointOverride = "http://127.0.0.1:1/unreachable",
                geoEndpointOverride = "http://127.0.0.1:1/unreachable"
            )
            assertEquals("Unavailable", unreachableSnapshot.primaryPublicIp)
            assertEquals("Unavailable", unreachableSnapshot.publicIpv4)
            assertEquals("Unavailable", unreachableSnapshot.publicIpv6)
            assertEquals("Unavailable", unreachableSnapshot.country)
            assertNotNull(unreachableSnapshot.errorMessage)

            directThread.join(1000)
        }
    }

    @Test
    fun verifyDashboardNavigationSpeedTestProxyManagerAndPrivateSpaceFlow() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("clonexapro_official_brand_logo").assertIsDisplayed()

        // Scroll to My IP Address & Privacy Score card & open combined IP Checker + Privacy Score screen
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_my_ip_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_my_ip_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_my_ip_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("my_ip_screen").assertIsDisplayed()

        // Verify integrated Privacy Consistency Score (0-100%) card on the same IP Checker screen
        composeTestRule.onNodeWithTag("my_ip_screen")
            .performScrollToNode(hasTestTag("privacy_score_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("privacy_score_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("privacy_score_value").assertIsDisplayed()

        // Switch to Current Proxy IP mode and back to Direct Connection IP mode
        composeTestRule.onNodeWithTag("my_ip_screen")
            .performScrollToNode(hasTestTag("mode_proxy_ip_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mode_proxy_ip_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mode_direct_ip_button").performClick()
        composeTestRule.waitForIdle()

        // Scroll to Hero IP Card & open IP Technical Details dialog, copy report, and close dialog
        composeTestRule.onNodeWithTag("my_ip_screen")
            .performScrollToNode(hasTestTag("view_ip_details_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("my_ip_primary_value").assertIsDisplayed()
        composeTestRule.onNodeWithTag("view_ip_details_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ip_details_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_ip_full_report_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("close_ip_details_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate back to Home Dashboard
        composeTestRule.onNodeWithTag("nav_dashboard").performClick()
        composeTestRule.waitForIdle()

        // Scroll to Speed Test card & open Speed Test screen
        composeTestRule.onNodeWithTag("dashboard_screen")
            .performScrollToNode(hasTestTag("dashboard_speed_test_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dashboard_speed_test_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_speed_test_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speed_test_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_test_screen")
            .performScrollToNode(hasTestTag("speed_test_gauge"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speed_test_gauge").assertIsDisplayed()

        // Navigate to Proxy Manager via bottom navigation
        composeTestRule.onNodeWithTag("nav_proxy").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("proxy_manager_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("proxy_status_dashboard_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("proxy_public_ip_value").assertIsDisplayed()

        // Scroll to Add Proxy button, open dialog, and save a valid proxy configuration
        composeTestRule.onNodeWithTag("proxy_manager_screen")
            .performScrollToNode(hasTestTag("proxy_add_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("proxy_add_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("proxy_name_input").performTextInput("Frankfurt Relay")
        composeTestRule.onNodeWithTag("proxy_host_input").performTextInput("198.51.100.24")
        composeTestRule.onNodeWithTag("confirm_save_proxy_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to App Discovery
        composeTestRule.onNodeWithTag("nav_discovery").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discovery_screen").assertIsDisplayed()

        // Navigate to Private Space Vault & verify Biometric Authentication Layer Gate
        composeTestRule.onNodeWithTag("nav_private_space").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("private_space_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("biometric_auth_layer_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("biometric_auth_state_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("setup_pin_input").performTextInput("2580")
        composeTestRule.onNodeWithTag("setup_pin_confirm_input").performTextInput("2580")
        composeTestRule.onNodeWithTag("save_pin_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Settings & verify About ClonexaPro, Legal Documents, and App Lock flow
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_clonexapro_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_app_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_version_text").assertIsDisplayed()

        // Configure App Lock PIN, lock app session immediately, and unlock via entry gate
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("save_app_lock_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("app_lock_new_secret_input").performTextInput("7391")
        composeTestRule.onNodeWithTag("app_lock_confirm_secret_input").performTextInput("7391")
        composeTestRule.onNodeWithTag("save_app_lock_button").performClick()
        composeTestRule.waitForIdle()

        // Lock app now -> must show App Lock entry gate screen
        composeTestRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("lock_app_now_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("lock_app_now_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("app_lock_gate_screen").assertIsDisplayed()

        // Unlock App Lock entry gate with configured PIN
        composeTestRule.onNodeWithTag("app_lock_gate_input").performTextInput("7391")
        composeTestRule.onNodeWithTag("app_lock_gate_unlock_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun verifyBiometricSpaceAuthViewModelLocksAndUnlocksIsolatedInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PrivateSpaceBiometricGateRegistry.resetForTesting()
        val crypto = CryptoSecurityManager(context)
        val biometricViewModel = BiometricSpaceAuthViewModel(cryptoSecurityManager = crypto)

        val publicInstance = ManagedAppInstanceEntity(
            id = 1L,
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            accountLabel = "Public Work Line",
            accountCategory = "Messaging",
            badgeColorHex = "#00E5FF",
            isolationTier = IsolationTier.MANAGED_TASK_LAUNCH.name,
            isInPrivateSpace = false
        )
        val isolatedPrivateInstance = ManagedAppInstanceEntity(
            id = 2L,
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            accountLabel = "Confidential Vault Line",
            accountCategory = "Messaging",
            badgeColorHex = "#7C4DFF",
            isolationTier = IsolationTier.ISOLATED_PROFILE_ACTIVE.name,
            isInPrivateSpace = true
        )

        // 1. By default, Private Isolated Space is LOCKED and blocks access to isolated instances
        assertFalse(biometricViewModel.uiState.value.isSpaceUnlocked)
        assertEquals(BiometricAuthLayerStatus.LOCKED, biometricViewModel.uiState.value.status)
        assertTrue(biometricViewModel.canAccessOrManageInstance(publicInstance))
        assertFalse(biometricViewModel.canAccessOrManageInstance(isolatedPrivateInstance))

        // 2. Unlock via Biometric Authentication Layer -> grants access & issues AES-256-GCM proof token
        biometricViewModel.requestBiometricUnlock(activity = null)
        assertTrue(biometricViewModel.uiState.value.isSpaceUnlocked)
        assertEquals(BiometricAuthLayerStatus.UNLOCKED, biometricViewModel.uiState.value.status)
        assertTrue(biometricViewModel.uiState.value.sessionCryptoProofToken.startsWith("BIO_GCM_PROOF:"))
        assertTrue(biometricViewModel.canAccessOrManageInstance(isolatedPrivateInstance))

        // 3. Lock Isolated Space -> immediately revokes session proof and blocks isolated instance access
        biometricViewModel.lockIsolatedSpace()
        assertFalse(biometricViewModel.uiState.value.isSpaceUnlocked)
        assertEquals(BiometricAuthLayerStatus.LOCKED, biometricViewModel.uiState.value.status)
        assertEquals("", biometricViewModel.uiState.value.sessionCryptoProofToken)
        assertFalse(biometricViewModel.canAccessOrManageInstance(isolatedPrivateInstance))

        // 4. Verify 5 failed attempts trigger 30-second COOLDOWN_LOCKOUT
        val baseTime = 1_700_000_000_000L
        repeat(5) { idx ->
            biometricViewModel.recordFailedAttempt("Failed biometric scan #${idx + 1}", nowMillis = baseTime)
        }
        assertEquals(BiometricAuthLayerStatus.COOLDOWN_LOCKOUT, biometricViewModel.uiState.value.status)
        assertTrue(biometricViewModel.uiState.value.isLockedOut(baseTime + 5_000L))
        assertFalse(biometricViewModel.completeBiometricUnlockSuccess(nowMillis = baseTime + 5_000L))

        // After 30s cooldown expires, biometric authentication succeeds again
        assertTrue(biometricViewModel.completeBiometricUnlockSuccess(nowMillis = baseTime + 31_000L))
        assertTrue(biometricViewModel.uiState.value.isSpaceUnlocked)
    }
}

