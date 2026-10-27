package com.example.model

import android.graphics.drawable.Drawable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Distinguishes the three architectural realities required by Section 6:
 * A. UI_MANAGED_ENTRY: Cataloged inside the ClonexaPro workspace (host app shared data).
 * B. MANAGED_TASK_LAUNCH: Launched via Android multi-document/multi-task intent separation.
 * C. ISOLATED_PROFILE_ACTIVE: Genuinely running in a separate Android Managed Work Profile or Clone Profile UserHandle.
 */
enum class IsolationTier(
    val code: String,
    val badgeTitle: String,
    val shortSummary: String,
    val technicalGuarantee: String,
    val providesFullDataSeparation: Boolean
) {
    ISOLATED_PROFILE_ACTIVE(
        code = "TIER_C",
        badgeTitle = "Tier C • Isolated Profile",
        shortSummary = "Hardware/OS Work or Clone Profile (Separate UID & Data Directory)",
        technicalGuarantee = "Guaranteed by Android UserManager & LauncherApps cross-profile sandbox (/data/user/<profileId>/). Complete filesystem, keychain, and account separation.",
        providesFullDataSeparation = true
    ),
    MANAGED_TASK_LAUNCH(
        code = "TIER_B",
        badgeTitle = "Tier B • Multi-Task Window",
        shortSummary = "Android Multi-Document Task Slot (Host Profile Storage)",
        technicalGuarantee = "Uses FLAG_ACTIVITY_NEW_DOCUMENT and FLAG_ACTIVITY_MULTIPLE_TASK to create an independent recent-tasks window. Shares host app storage unless a Work Profile is provisioned.",
        providesFullDataSeparation = false
    ),
    UI_MANAGED_ENTRY(
        code = "TIER_A",
        badgeTitle = "Tier A • Workspace Entry",
        shortSummary = "Cataloged in ClonexaPro with Private Space Lock & Account Tagging",
        technicalGuarantee = "Organizes account labels, audit logs, and Private Space PIN/Biometric access control before launching the host application.",
        providesFullDataSeparation = false
    )
}

/**
 * Classifies installed applications based on Android security flags, system status,
 * and compatibility with multi-instance / Work Profile execution.
 */
enum class CompatibilityStatus(
    val label: String,
    val summary: String,
    val canCreateInstance: Boolean
) {
    COMPATIBLE_ISOLATED(
        label = "Compatible • Multi-Instance Ready",
        summary = "Eligible for Android Work/Clone Profile isolation and multi-task workspace launching.",
        canCreateInstance = true
    ),
    COMPATIBLE_WORK_PROFILE_ONLY(
        label = "Work Profile Required for 2nd Account",
        summary = "Uses singleton account storage per Android user profile. Provision an Android Work Profile for a separate data sandbox.",
        canCreateInstance = true
    ),
    RESTRICTED_SECURE_ENV(
        label = "Restricted • Security Boundary Enforced",
        summary = "Declares REQUIRE_SECURE_ENV or hardware attestation policy. ClonexaPro strictly honors Android security boundaries and blocks simulated/virtualized loading.",
        canCreateInstance = false
    ),
    INCOMPATIBLE_SYSTEM_CORE(
        label = "System Core • Non-Clonable",
        summary = "Privileged Android OS or carrier infrastructure package tied to User 0 hardware services.",
        canCreateInstance = false
    )
}

/**
 * Represents a real installed application discovered via Android PackageManager and LauncherApps.
 */
data class DiscoveredApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val targetSdk: Int,
    val minSdk: Int,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isInstalledInSecondaryProfile: Boolean,
    val secondaryProfileSerial: Long?,
    val categoryLabel: String,
    val compatibilityStatus: CompatibilityStatus,
    val compatibilityReason: String,
    val hasRequireSecureEnvFlag: Boolean,
    val isTargetMessagingOrSocial: Boolean,
    val iconDrawable: Drawable? = null
)

/**
 * Represents well-known target messaging and social applications and whether they are
 * currently installed on the host device (never fabricating fake installations).
 */
data class TargetEcosystemApp(
    val displayName: String,
    val packageName: String,
    val category: String,
    val isInstalledOnHost: Boolean,
    val isInstalledInWorkProfile: Boolean,
    val isolationNotes: String,
    val compatibilityStatus: CompatibilityStatus
)

/**
 * Room Entity storing user-configured application instances in the ClonexaPro workspace.
 * Keeps management metadata strictly separate from third-party account credentials.
 */
@Entity(tableName = "managed_app_instances")
data class ManagedAppInstanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val accountLabel: String,
    val accountCategory: String,
    val badgeColorHex: String,
    val isolationTier: String, // IsolationTier.name
    val userProfileSerial: Long = 0L,
    val isInPrivateSpace: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunchedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = ""
) {
    val resolvedTier: IsolationTier
        get() = IsolationTier.entries.find { it.name == isolationTier } ?: IsolationTier.UI_MANAGED_ENTRY
}

/**
 * Room Entity storing genuine operational and security audit events locally on the device.
 */
@Entity(tableName = "security_audit_logs")
data class SecurityAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventType: String,
    val packageName: String?,
    val summary: String,
    val isSuccess: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Room Entity storing historical Internet Speed Test measurements locally on the device.
 */
@Entity(tableName = "speed_test_results")
data class SpeedTestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
    val connectionType: String,
    val serverName: String,
    val bytesTransferred: Long,
    val statusNote: String = "Completed",
    val timestamp: Long = System.currentTimeMillis()
)

enum class SpeedTestPhase(val label: String) {
    IDLE("Ready to Test"),
    PING_AND_JITTER("Measuring Ping & Jitter"),
    DOWNLOAD("Testing Download Speed"),
    UPLOAD("Testing Upload Speed"),
    COMPLETED("Test Complete"),
    CANCELLED("Test Cancelled"),
    ERROR("Test Interrupted")
}

data class NetworkConnectionInfo(
    val connectionTypeLabel: String = "Checking Network…",
    val isConnected: Boolean = false,
    val isValidated: Boolean = false,
    val isVpnActive: Boolean = false,
    val linkDownstreamKbps: Int = 0,
    val linkUpstreamKbps: Int = 0
)

data class SpeedTestLiveState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val overallProgress: Float = 0f,
    val currentGaugeMbps: Double = 0.0,
    val pingMs: Double? = null,
    val jitterMs: Double? = null,
    val downloadMbps: Double? = null,
    val uploadMbps: Double? = null,
    val bytesTransferred: Long = 0L,
    val serverLabel: String = "Cloudflare Edge Speed Test (HTTPS)",
    val statusMessage: String = "Tap Start Test to measure real network throughput.",
    val errorMessage: String? = null
) {
    val isRunning: Boolean
        get() = phase == SpeedTestPhase.PING_AND_JITTER ||
            phase == SpeedTestPhase.DOWNLOAD ||
            phase == SpeedTestPhase.UPLOAD
}

/**
 * Live diagnostic report of the current Android device/emulator isolation capabilities.
 */
data class HostIsolationEnvironmentReport(
    val androidSdkInt: Int,
    val androidReleaseName: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val detectedProfileCount: Int,
    val profileSerialNumbers: List<Long>,
    val hasSecondaryUserProfile: Boolean,
    val isManagedProfileProvisioningAllowed: Boolean,
    val isProfileOwnerActive: Boolean,
    val supportsCloneProfileApi: Boolean,
    val hardwareKeystoreAvailable: Boolean,
    val deviceCredentialOrBiometricEnrolled: Boolean,
    val activeEngineSummary: String
)

enum class ThemePreference(val label: String) {
    SYSTEM("System Default"),
    DARK("Cyber Obsidian (Dark)"),
    LIGHT("Technical Daylight (Light)")
}

enum class MonetizationTier(
    val title: String,
    val subtitle: String,
    val maxManagedInstances: Int
) {
    FREE_STANDARD(
        title = "ClonexaPro Standard (Free)",
        subtitle = "Full Work Profile isolation, Private Space Keystore vault, and up to 12 managed slots with zero intrusive ads.",
        maxManagedInstances = 12
    ),
    PRO_ENTERPRISE(
        title = "ClonexaPro Enterprise Architecture",
        subtitle = "Unlimited workspace profiles, priority security audit export, and multi-profile policy templates.",
        maxManagedInstances = 999
    )
}

enum class ProxyProtocol(
    val displayName: String,
    val technicalDescription: String
) {
    HTTP(
        displayName = "HTTP",
        technicalDescription = "HTTP Proxy with CONNECT tunneling for HTTPS requests and Basic Proxy-Authorization."
    ),
    HTTPS(
        displayName = "HTTPS",
        technicalDescription = "TLS-wrapped HTTPS Proxy tunnel with certificate handshake verification."
    ),
    SOCKS5(
        displayName = "SOCKS5",
        technicalDescription = "RFC 1928 SOCKS5 TCP proxy with RFC 1929 Username/Password authentication negotiation."
    )
}

enum class ProxyTestStatus(val displayLabel: String) {
    WORKING("Working"),
    AUTH_FAILED("Authentication Failed"),
    CONNECTION_FAILED("Connection Failed"),
    TIMEOUT("Timeout"),
    UNSUPPORTED("Unsupported"),
    UNTESTED("Untested")
}

enum class ProxyConnectionState(val label: String) {
    DISCONNECTED("Direct Connection (No Proxy Active)"),
    CONNECTING("Establishing Proxy Tunnel…"),
    TESTING("Testing Proxy & IP Geolocation…"),
    CONNECTED_APP_SCOPED("Connected • Application-Scoped Routing"),
    ERROR("Proxy Connection Error")
}

/**
 * Room Entity storing user-configured proxies in the local Proxy Library.
 * Passwords are encrypted via AndroidKeyStore AES-256-GCM before storage in [encryptedPassword].
 */
@Entity(tableName = "proxy_configs")
data class ProxyConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: String, // ProxyProtocol.name
    val username: String = "",
    val encryptedPassword: String = "",
    val notes: String = "",
    val isSelected: Boolean = false,
    val isActiveConnected: Boolean = false,
    val lastTestStatus: String = ProxyTestStatus.UNTESTED.name,
    val lastTestMessage: String = "Not tested yet",
    val lastLatencyMs: Long? = null,
    val lastObservedIp: String = "Unavailable",
    val lastCountry: String = "Unknown",
    val lastRegion: String = "Unknown",
    val lastCity: String = "Unknown",
    val lastCoordinates: String = "Unavailable",
    val lastIspOrg: String = "Unavailable",
    val lastDnsBehavior: String = "Untested",
    val lastTestedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val resolvedProtocol: ProxyProtocol
        get() = ProxyProtocol.entries.find { it.name == protocol } ?: ProxyProtocol.HTTP

    val resolvedTestStatus: ProxyTestStatus
        get() = ProxyTestStatus.entries.find { it.name == lastTestStatus } ?: ProxyTestStatus.UNTESTED

    val hasAuthentication: Boolean
        get() = username.isNotBlank() || encryptedPassword.isNotBlank()
}

data class IpGeolocationReport(
    val publicIp: String = "Unavailable",
    val country: String = "Unknown",
    val region: String = "Unknown",
    val city: String = "Unknown",
    val coordinates: String = "Unavailable",
    val ispOrg: String = "Unavailable",
    val latencyMs: Long = 0L,
    val viaRouteLabel: String = "Direct Host Connection",
    val dnsBehaviorNote: String = "Standard System Resolver",
    val timestamp: Long = System.currentTimeMillis()
)

data class ProxyLiveState(
    val connectionState: ProxyConnectionState = ProxyConnectionState.DISCONNECTED,
    val activeProxyId: Long? = null,
    val selectedProxyId: Long? = null,
    val isBusy: Boolean = false,
    val statusMessage: String = "Direct connection active. Select or add a proxy to test and route application traffic.",
    val errorMessage: String? = null,
    val currentIpGeo: IpGeolocationReport = IpGeolocationReport(),
    val isWorkProfileProxyApplied: Boolean = false
)

/**
 * Explicitly distinguishes Direct Connection IP lookups from Active Proxy IP lookups.
 */
enum class IpLookupMode(
    val title: String,
    val subtitle: String
) {
    DIRECT_CONNECTION(
        title = "Direct Connection IP",
        subtitle = "Bypasses application proxies (Proxy.NO_PROXY) to inspect your host network's direct public IP."
    ),
    CURRENT_PROXY(
        title = "Current Proxy IP",
        subtitle = "Routes strictly through the active proxy in Proxy Manager to verify the externally observed proxy exit IP."
    )
}

enum class IpVersionClassification(val label: String) {
    IPV4("IPv4"),
    IPV6("IPv6"),
    DUAL_STACK("Dual-Stack (IPv4 + IPv6)"),
    UNAVAILABLE("Unavailable")
}

enum class PrivacyDiagnosticStatus(val label: String) {
    PASS("Consistent"),
    WARNING("Mismatch / Warning"),
    INFO("Informational"),
    UNAVAILABLE("Unavailable")
}

data class PrivacyDiagnosticCheckItem(
    val id: String,
    val title: String,
    val observedValue: String,
    val expectedOrBaseline: String,
    val status: PrivacyDiagnosticStatus,
    val weightPoints: Int,
    val earnedPoints: Int,
    val explanation: String
)

/**
 * Transparent, documented Privacy Consistency Score (0–100%) calculated from real network,
 * proxy, timezone, locale, DNS, and local-interface consistency indicators.
 * Never claims to reproduce Whoer.net's proprietary scoring algorithm.
 */
data class PrivacyConsistencyReport(
    val scorePercent: Int = 0,
    val ratingLabel: String = "Not Evaluated Yet",
    val formulaSummary: String = "ClonexaPro Open Privacy Formula (100 pts max): IP & Proxy Tunnel Verification (30 pts) + Time Zone Alignment (25 pts) + System Language/Locale Consistency (15 pts) + DNS Tunnel Posture (15 pts) + Local Interface / WebRTC Exposure Posture (15 pts).",
    val deviceTimeZone: String = "Unavailable",
    val ipTimeZone: String = "Unavailable",
    val isTimeZoneMatched: Boolean? = null,
    val deviceLocaleTag: String = "Unavailable",
    val ipCountryCodeOrName: String = "Unavailable",
    val isLocaleConsistent: Boolean? = null,
    val directPublicIp: String = "Unavailable",
    val proxyPublicIp: String = "Unavailable",
    val dnsRoutingStatus: String = "Standard System Resolver",
    val webrtcExposureStatus: String = "Native Android Socket (No Browser WebRTC Stack)",
    val checks: List<PrivacyDiagnosticCheckItem> = emptyList()
)

enum class AppLockCredentialType(val label: String, val helperText: String) {
    PIN("Numeric PIN", "4 to 8 numeric digits (0–9)"),
    PASSWORD("Alphanumeric Password", "6+ characters with letters, numbers, or symbols")
}

/**
 * Immutable snapshot of a real IP & Geolocation inspection for either Direct Connection or Active Proxy mode,
 * combined with the integrated Privacy Consistency Score & Diagnostic Report.
 * Kept in memory only (not persisted to disk or analytics) for user privacy.
 */
data class MyIpSnapshot(
    val mode: IpLookupMode = IpLookupMode.DIRECT_CONNECTION,
    val primaryPublicIp: String = "Unavailable",
    val publicIpv4: String = "Unavailable",
    val publicIpv6: String = "Unavailable",
    val ipVersion: IpVersionClassification = IpVersionClassification.UNAVAILABLE,
    val localInterfaceIp: String = "Unavailable",
    val localInterfaceScope: String = "Private / Local Network Interface",
    val connectionTypeLabel: String = "Unavailable",
    val ispOrg: String = "Unavailable",
    val asnNumber: String = "Unavailable",
    val country: String = "Unavailable",
    val region: String = "Unavailable",
    val city: String = "Unavailable",
    val approximateCoordinates: String = "Unavailable",
    val timezoneId: String = "Unavailable",
    val viaRouteDescription: String = "Not Checked Yet",
    val lookupServiceName: String = "ipwho.is / ipify.org",
    val latencyMs: Long? = null,
    val checkedAtMillis: Long? = null,
    val isProxyVerified: Boolean = false,
    val errorMessage: String? = null,
    val privacyReport: PrivacyConsistencyReport = PrivacyConsistencyReport()
) {
    val hasValidPublicIp: Boolean
        get() = primaryPublicIp != "Unavailable" && primaryPublicIp.isNotBlank()
}

data class MyIpLiveState(
    val selectedMode: IpLookupMode = IpLookupMode.DIRECT_CONNECTION,
    val isLoading: Boolean = false,
    val directSnapshot: MyIpSnapshot = MyIpSnapshot(mode = IpLookupMode.DIRECT_CONNECTION),
    val proxySnapshot: MyIpSnapshot = MyIpSnapshot(
        mode = IpLookupMode.CURRENT_PROXY,
        viaRouteDescription = "No Active Proxy Connected",
        errorMessage = "Proxy is not currently active. Connect a proxy in Proxy Manager to verify your Current Proxy IP."
    ),
    val isDetailsDialogOpen: Boolean = false
) {
    val displayedSnapshot: MyIpSnapshot
        get() = when (selectedMode) {
            IpLookupMode.DIRECT_CONNECTION -> directSnapshot
            IpLookupMode.CURRENT_PROXY -> proxySnapshot
        }
}

enum class ClonexaScreen(val route: String) {
    DASHBOARD("dashboard"),
    DISCOVERY("discovery"),
    SPEED_TEST("speed_test"),
    PROXY_MANAGER("proxy_manager"),
    MY_IP_ADDRESS("my_ip_address"),
    PRIVATE_SPACE("private_space"),
    ARCHITECTURE_LAB("architecture_lab"),
    SETTINGS("settings")
}


