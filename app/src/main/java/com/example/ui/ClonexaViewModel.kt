package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ClonexaDatabase
import com.example.data.ClonexaPreferencesRepository
import com.example.data.ClonexaUserSettings
import com.example.data.ClonexaWorkspaceRepository
import com.example.isolation.AppDiscoveryAndIsolationEngine
import com.example.model.AppLockCredentialType
import com.example.model.ClonexaScreen
import com.example.model.DiscoveredApp
import com.example.model.HostIsolationEnvironmentReport
import com.example.model.IpLookupMode
import com.example.model.IsolationTier
import com.example.model.ManagedAppInstanceEntity
import com.example.model.MonetizationTier
import com.example.model.MyIpLiveState
import com.example.model.NetworkConnectionInfo
import com.example.model.ProxyConfigEntity
import com.example.model.ProxyConnectionState
import com.example.model.ProxyLiveState
import com.example.model.ProxyProtocol
import com.example.model.ProxyTestStatus
import com.example.model.SecurityAuditLogEntity
import com.example.model.SpeedTestLiveState
import com.example.model.SpeedTestPhase
import com.example.model.SpeedTestResultEntity
import com.example.model.TargetEcosystemApp
import com.example.model.ThemePreference
import com.example.network.MyIpInspectorEngine
import com.example.network.NetworkSpeedTestEngine
import com.example.network.ProxyManagerEngine
import com.example.security.BiometricAuthLayerStatus
import com.example.security.BiometricSpaceUiState
import com.example.security.CryptoSecurityManager
import com.example.security.PrivateSpaceBiometricGateRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DiscoveryFilter(val label: String) {
    ALL("All Discovered"),
    COMPATIBLE("Compatible"),
    MESSAGING_SOCIAL("Social & Chat"),
    WORK_PROFILE("Profile Isolated"),
    RESTRICTED("Restricted")
}

data class ClonexaUiState(
    val currentScreen: ClonexaScreen = ClonexaScreen.DASHBOARD,
    val isLoadingApps: Boolean = true,
    val discoveredApps: List<DiscoveredApp> = emptyList(),
    val targetEcosystemApps: List<TargetEcosystemApp> = emptyList(),
    val managedInstances: List<ManagedAppInstanceEntity> = emptyList(),
    val auditLogs: List<SecurityAuditLogEntity> = emptyList(),
    val speedTestHistory: List<SpeedTestResultEntity> = emptyList(),
    val speedTestLiveState: SpeedTestLiveState = SpeedTestLiveState(),
    val networkConnectionInfo: NetworkConnectionInfo = NetworkConnectionInfo(),
    val proxyConfigs: List<ProxyConfigEntity> = emptyList(),
    val proxyLiveState: ProxyLiveState = ProxyLiveState(),
    val myIpLiveState: MyIpLiveState = MyIpLiveState(),
    val userSettings: ClonexaUserSettings = ClonexaUserSettings(),
    val hostEnvironment: HostIsolationEnvironmentReport? = null,
    val dashboardSearchQuery: String = "",
    val discoverySearchQuery: String = "",
    val selectedDiscoveryFilter: DiscoveryFilter = DiscoveryFilter.ALL,
    val isPrivateSpaceUnlocked: Boolean = false,
    val privateSpaceLastUnlockedAt: Long = 0L,
    val biometricSpaceState: BiometricSpaceUiState = BiometricSpaceUiState(),
    val isAppLockSessionUnlocked: Boolean = false,
    val newlyGeneratedAppLockRecoveryCode: String? = null,
    val appLockErrorMessage: String? = null,
    val statusBannerMessage: String? = null,
    val statusBannerIsError: Boolean = false,
    val selectedAppForInstanceCreation: DiscoveredApp? = null,
    val selectedInstanceForDetails: ManagedAppInstanceEntity? = null
) {
    val requiresAppLockEntryGate: Boolean
        get() = userSettings.isAppLockActive && !isAppLockSessionUnlocked
    val selectedProxy: ProxyConfigEntity?
        get() = proxyConfigs.firstOrNull { it.isActiveConnected }
            ?: proxyConfigs.firstOrNull { it.isSelected }
            ?: proxyConfigs.firstOrNull()

    val activeConnectedProxy: ProxyConfigEntity?
        get() = proxyConfigs.firstOrNull { it.isActiveConnected }

    val publicManagedInstances: List<ManagedAppInstanceEntity>
        get() = managedInstances.filter { !it.isInPrivateSpace }.filter { instance ->
            if (dashboardSearchQuery.isBlank()) true
            else {
                val q = dashboardSearchQuery.trim().lowercase()
                instance.appName.lowercase().contains(q) ||
                    instance.accountLabel.lowercase().contains(q) ||
                    instance.packageName.lowercase().contains(q) ||
                    instance.accountCategory.lowercase().contains(q)
            }
        }

    val privateManagedInstances: List<ManagedAppInstanceEntity>
        get() = managedInstances.filter { it.isInPrivateSpace }

    val filteredDiscoveredApps: List<DiscoveredApp>
        get() = discoveredApps.filter { app ->
            val matchesQuery = if (discoverySearchQuery.isBlank()) {
                true
            } else {
                val q = discoverySearchQuery.trim().lowercase()
                app.appName.lowercase().contains(q) ||
                    app.packageName.lowercase().contains(q) ||
                    app.categoryLabel.lowercase().contains(q)
            }
            val matchesFilter = when (selectedDiscoveryFilter) {
                DiscoveryFilter.ALL -> true
                DiscoveryFilter.COMPATIBLE -> app.compatibilityStatus.canCreateInstance
                DiscoveryFilter.MESSAGING_SOCIAL ->
                    app.isTargetMessagingOrSocial || app.categoryLabel.contains("Social", ignoreCase = true) ||
                        app.categoryLabel.contains("Messaging", ignoreCase = true)
                DiscoveryFilter.WORK_PROFILE -> app.isInstalledInSecondaryProfile
                DiscoveryFilter.RESTRICTED -> !app.compatibilityStatus.canCreateInstance
            }
            matchesQuery && matchesFilter
        }
}

private data class InternalViewState(
    val currentScreen: ClonexaScreen = ClonexaScreen.DASHBOARD,
    val isLoadingApps: Boolean = true,
    val discoveredApps: List<DiscoveredApp> = emptyList(),
    val targetEcosystemApps: List<TargetEcosystemApp> = emptyList(),
    val hostEnvironment: HostIsolationEnvironmentReport? = null,
    val speedTestLiveState: SpeedTestLiveState = SpeedTestLiveState(),
    val networkConnectionInfo: NetworkConnectionInfo = NetworkConnectionInfo(),
    val proxyLiveState: ProxyLiveState = ProxyLiveState(),
    val myIpLiveState: MyIpLiveState = MyIpLiveState(),
    val dashboardSearchQuery: String = "",
    val discoverySearchQuery: String = "",
    val selectedDiscoveryFilter: DiscoveryFilter = DiscoveryFilter.ALL,
    val isPrivateSpaceUnlocked: Boolean = false,
    val privateSpaceLastUnlockedAt: Long = 0L,
    val optimisticPrivatePinToken: String? = null,
    val isAppLockSessionUnlocked: Boolean = false,
    val optimisticAppLockEnabled: Boolean? = null,
    val optimisticAppLockType: AppLockCredentialType? = null,
    val optimisticAppLockToken: String? = null,
    val optimisticAppLockRecoveryToken: String? = null,
    val newlyGeneratedAppLockRecoveryCode: String? = null,
    val appLockErrorMessage: String? = null,
    val statusBannerMessage: String? = null,
    val statusBannerIsError: Boolean = false,
    val selectedAppForInstanceCreation: DiscoveredApp? = null,
    val selectedInstanceForDetails: ManagedAppInstanceEntity? = null
)

class ClonexaViewModel(
    private val workspaceRepository: ClonexaWorkspaceRepository,
    private val preferencesRepository: ClonexaPreferencesRepository,
    private val discoveryEngine: AppDiscoveryAndIsolationEngine,
    private val cryptoSecurityManager: CryptoSecurityManager,
    private val speedTestEngine: NetworkSpeedTestEngine? = null,
    private val proxyManagerEngine: ProxyManagerEngine? = null,
    private val myIpInspectorEngine: MyIpInspectorEngine? = null
) : ViewModel() {

    private val internalState = MutableStateFlow(InternalViewState())
    private var activeSpeedTestJob: Job? = null

    private val storageBundleFlow = combine(
        workspaceRepository.allInstances,
        workspaceRepository.auditLogs,
        preferencesRepository.settingsFlow,
        workspaceRepository.speedTestHistory ?: flowOf(emptyList()),
        workspaceRepository.proxyConfigs ?: flowOf(emptyList())
    ) { instances, logs, settings, speedHistory, proxies ->
        StorageBundle(instances, logs, settings, speedHistory, proxies)
    }

    private data class StorageBundle(
        val instances: List<ManagedAppInstanceEntity>,
        val logs: List<SecurityAuditLogEntity>,
        val settings: ClonexaUserSettings,
        val speedHistory: List<SpeedTestResultEntity>,
        val proxies: List<ProxyConfigEntity>
    )

    val uiState: StateFlow<ClonexaUiState> = combine(
        internalState,
        storageBundleFlow,
        PrivateSpaceBiometricGateRegistry.state
    ) { local, bundle, biometricRegistryState ->
        val rawSettings = bundle.settings
        val settings = rawSettings.copy(
            encryptedPinToken = local.optimisticPrivatePinToken ?: rawSettings.encryptedPinToken,
            appLockEnabled = local.optimisticAppLockEnabled ?: rawSettings.appLockEnabled,
            appLockCredentialType = local.optimisticAppLockType ?: rawSettings.appLockCredentialType,
            encryptedAppLockToken = local.optimisticAppLockToken ?: rawSettings.encryptedAppLockToken,
            encryptedAppLockRecoveryToken = local.optimisticAppLockRecoveryToken ?: rawSettings.encryptedAppLockRecoveryToken
        )
        val timeoutMs = settings.sessionTimeoutMinutes * 60_000L
        val now = System.currentTimeMillis()
        val combinedUnlocked = local.isPrivateSpaceUnlocked || biometricRegistryState.isSpaceUnlocked
        val effectiveLastUnlockedAt = maxOf(
            local.privateSpaceLastUnlockedAt,
            biometricRegistryState.lastAuthenticatedAtMillis ?: 0L
        )
        val sessionValid = if (!combinedUnlocked) {
            false
        } else if (settings.sessionTimeoutMinutes == 0) {
            local.currentScreen == ClonexaScreen.PRIVATE_SPACE
        } else {
            (now - effectiveLastUnlockedAt) <= timeoutMs
        }

        val effectiveBiometricState = biometricRegistryState.copy(
            isSpaceUnlocked = sessionValid,
            status = when {
                sessionValid -> BiometricAuthLayerStatus.UNLOCKED
                biometricRegistryState.status == BiometricAuthLayerStatus.UNLOCKED -> BiometricAuthLayerStatus.LOCKED
                else -> biometricRegistryState.status
            }
        )

        ClonexaUiState(
            currentScreen = local.currentScreen,
            isLoadingApps = local.isLoadingApps,
            discoveredApps = local.discoveredApps,
            targetEcosystemApps = local.targetEcosystemApps,
            managedInstances = bundle.instances,
            auditLogs = bundle.logs,
            speedTestHistory = bundle.speedHistory,
            speedTestLiveState = local.speedTestLiveState,
            networkConnectionInfo = local.networkConnectionInfo,
            proxyConfigs = bundle.proxies,
            proxyLiveState = local.proxyLiveState,
            myIpLiveState = local.myIpLiveState,
            userSettings = settings,
            hostEnvironment = local.hostEnvironment,
            dashboardSearchQuery = local.dashboardSearchQuery,
            discoverySearchQuery = local.discoverySearchQuery,
            selectedDiscoveryFilter = local.selectedDiscoveryFilter,
            isPrivateSpaceUnlocked = sessionValid,
            privateSpaceLastUnlockedAt = effectiveLastUnlockedAt,
            biometricSpaceState = effectiveBiometricState,
            isAppLockSessionUnlocked = local.isAppLockSessionUnlocked || !settings.isAppLockActive,
            newlyGeneratedAppLockRecoveryCode = local.newlyGeneratedAppLockRecoveryCode,
            appLockErrorMessage = local.appLockErrorMessage,
            statusBannerMessage = local.statusBannerMessage,
            statusBannerIsError = local.statusBannerIsError,
            selectedAppForInstanceCreation = local.selectedAppForInstanceCreation,
            selectedInstanceForDetails = local.selectedInstanceForDetails
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ClonexaUiState()
    )

    init {
        PrivateSpaceBiometricGateRegistry.resetForTesting()
        PrivateSpaceBiometricGateRegistry.update {
            it.copy(capabilityReport = cryptoSecurityManager.inspectBiometricCapability())
        }
        refreshDiscoveryAndEnvironment(includeSystemApps = false)
        refreshNetworkConnection()
    }

    fun navigateTo(screen: ClonexaScreen) {
        if (screen == ClonexaScreen.SPEED_TEST) {
            refreshNetworkConnection()
        } else if (screen == ClonexaScreen.PROXY_MANAGER) {
            refreshNetworkConnection()
            if (uiState.value.proxyLiveState.currentIpGeo.publicIp == "Unavailable") {
                refreshPublicIpGeolocation()
            }
        } else if (screen == ClonexaScreen.MY_IP_ADDRESS) {
            refreshNetworkConnection()
            if (uiState.value.myIpLiveState.displayedSnapshot.checkedAtMillis == null) {
                refreshMyIpAddress()
            }
        }
        val shouldAutoLockOnExit = uiState.value.userSettings.sessionTimeoutMinutes == 0 &&
            screen != ClonexaScreen.PRIVATE_SPACE
        if (shouldAutoLockOnExit) {
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = BiometricAuthLayerStatus.LOCKED,
                    isSpaceUnlocked = false,
                    sessionCryptoProofToken = "",
                    lastAuthMethodLabel = "Locked (Immediate Session Timeout)"
                )
            }
        }
        internalState.update { state ->
            val keepUnlocked = if (shouldAutoLockOnExit) false else state.isPrivateSpaceUnlocked
            state.copy(
                currentScreen = screen,
                isPrivateSpaceUnlocked = keepUnlocked
            )
        }
    }

    // --- My IP Address Inspector Actions ---

    fun selectMyIpLookupMode(mode: IpLookupMode) {
        internalState.update {
            it.copy(
                myIpLiveState = it.myIpLiveState.copy(selectedMode = mode)
            )
        }
        refreshMyIpAddress(modeOverride = mode)
    }

    fun refreshMyIpAddress(modeOverride: IpLookupMode? = null) {
        val engine = myIpInspectorEngine ?: return
        val targetMode = modeOverride ?: internalState.value.myIpLiveState.selectedMode
        viewModelScope.launch {
            internalState.update {
                it.copy(
                    myIpLiveState = it.myIpLiveState.copy(
                        selectedMode = targetMode,
                        isLoading = true
                    )
                )
            }
            val activeProxy = uiState.value.activeConnectedProxy
            val currentIpState = internalState.value.myIpLiveState
            val knownDirect = currentIpState.directSnapshot.primaryPublicIp
            val knownProxy = currentIpState.proxySnapshot.primaryPublicIp.takeIf { it != "Unavailable" }
                ?: activeProxy?.lastObservedIp
                ?: "Unavailable"

            val snapshot = engine.inspectMyIp(
                mode = targetMode,
                activeProxy = activeProxy,
                knownDirectIp = knownDirect,
                knownProxyIp = knownProxy
            )
            internalState.update {
                val updatedIpState = when (targetMode) {
                    IpLookupMode.DIRECT_CONNECTION -> it.myIpLiveState.copy(
                        isLoading = false,
                        directSnapshot = snapshot
                    )
                    IpLookupMode.CURRENT_PROXY -> it.myIpLiveState.copy(
                        isLoading = false,
                        proxySnapshot = snapshot
                    )
                }
                it.copy(myIpLiveState = updatedIpState)
            }
        }
    }

    fun openMyIpDetailsDialog() {
        internalState.update {
            it.copy(myIpLiveState = it.myIpLiveState.copy(isDetailsDialogOpen = true))
        }
    }

    fun dismissMyIpDetailsDialog() {
        internalState.update {
            it.copy(myIpLiveState = it.myIpLiveState.copy(isDetailsDialogOpen = false))
        }
    }

    fun notifyIpCopiedToClipboard(copiedLabel: String) {
        postBanner("Copied '$copiedLabel' to clipboard.", isError = false)
    }

    // --- Proxy Manager Actions ---

    fun refreshPublicIpGeolocation() {
        val engine = proxyManagerEngine ?: return
        viewModelScope.launch {
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        isBusy = true,
                        errorMessage = null,
                        statusMessage = "Checking externally observed public IP and approximate geolocation…"
                    )
                )
            }
            val activeProxy = uiState.value.activeConnectedProxy
            val geo = engine.fetchPublicIpAndGeolocation(activeProxy = activeProxy)
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        isBusy = false,
                        currentIpGeo = geo,
                        statusMessage = if (geo.publicIp != "Unavailable") {
                            "Observed IP: ${geo.publicIp} (${geo.country}, ${geo.region}) via ${geo.viaRouteLabel}"
                        } else {
                            "Could not resolve external IP. Verify internet or active proxy reachability."
                        }
                    )
                )
            }
        }
    }

    fun saveProxyConfiguration(
        existingId: Long = 0L,
        name: String,
        hostInput: String,
        portInput: String,
        protocol: ProxyProtocol,
        username: String,
        plainPassword: String,
        notes: String,
        onValidationFailed: (String) -> Unit,
        onSavedSuccess: () -> Unit
    ) {
        val validation = ProxyManagerEngine.validateProxyInput(
            name = name,
            hostInput = hostInput,
            portInput = portInput
        )
        if (!validation.isValid) {
            onValidationFailed(validation.errorMessage ?: "Invalid proxy configuration.")
            return
        }

        viewModelScope.launch {
            val encryptedPass = withContext(Dispatchers.IO) {
                if (plainPassword.isNotEmpty()) {
                    cryptoSecurityManager.encryptCredential(plainPassword)
                } else {
                    ""
                }
            }

            val existing = if (existingId > 0L) {
                uiState.value.proxyConfigs.find { it.id == existingId }
            } else {
                null
            }

            val entity = ProxyConfigEntity(
                id = existingId,
                name = name.trim(),
                host = validation.sanitizedHost,
                port = validation.parsedPort,
                protocol = protocol.name,
                username = username.trim(),
                encryptedPassword = encryptedPass,
                notes = notes.trim(),
                isSelected = existing?.isSelected ?: true,
                isActiveConnected = existing?.isActiveConnected ?: false,
                lastTestStatus = ProxyTestStatus.UNTESTED.name,
                lastTestMessage = "Configuration saved • Untested"
            )

            workspaceRepository.saveProxyConfig(entity)
            onSavedSuccess()
            postBanner(
                message = "Saved proxy '${entity.name}' (${protocol.displayName} ${entity.host}:${entity.port}). Credentials protected via Android Keystore.",
                isError = false
            )
        }
    }

    fun decryptProxyPasswordForEditing(proxy: ProxyConfigEntity): String {
        return cryptoSecurityManager.decryptCredential(proxy.encryptedPassword)
    }

    fun selectProxyConfig(proxy: ProxyConfigEntity) {
        viewModelScope.launch {
            workspaceRepository.selectProxyConfig(proxy.id)
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        selectedProxyId = proxy.id,
                        errorMessage = null,
                        statusMessage = "Selected '${proxy.name}' (${proxy.protocol} ${proxy.host}:${proxy.port})."
                    )
                )
            }
        }
    }

    fun testProxyConfig(proxy: ProxyConfigEntity) {
        val engine = proxyManagerEngine ?: return
        viewModelScope.launch {
            workspaceRepository.selectProxyConfig(proxy.id)
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        connectionState = if (it.proxyLiveState.activeProxyId != null) {
                            ProxyConnectionState.CONNECTED_APP_SCOPED
                        } else {
                            ProxyConnectionState.TESTING
                        },
                        isBusy = true,
                        errorMessage = null,
                        statusMessage = "Testing ${proxy.protocol} proxy ${proxy.host}:${proxy.port} & verifying external IP…"
                    )
                )
            }

            val result = engine.testProxyConfiguration(proxy)
            val updatedEntity = proxy.copy(
                isSelected = true,
                lastTestStatus = result.status.name,
                lastTestMessage = result.diagnosticMessage,
                lastLatencyMs = result.latencyMs,
                lastObservedIp = result.geoReport.publicIp,
                lastCountry = result.geoReport.country,
                lastRegion = result.geoReport.region,
                lastCity = result.geoReport.city,
                lastCoordinates = result.geoReport.coordinates,
                lastIspOrg = result.geoReport.ispOrg,
                lastDnsBehavior = result.dnsBehavior,
                lastTestedAt = System.currentTimeMillis()
            )
            workspaceRepository.updateProxyTestResult(updatedEntity)

            val succeeded = result.status == ProxyTestStatus.WORKING
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        connectionState = if (it.proxyLiveState.activeProxyId != null) {
                            ProxyConnectionState.CONNECTED_APP_SCOPED
                        } else if (succeeded) {
                            ProxyConnectionState.DISCONNECTED
                        } else {
                            ProxyConnectionState.ERROR
                        },
                        isBusy = false,
                        currentIpGeo = if (succeeded) result.geoReport else it.proxyLiveState.currentIpGeo,
                        statusMessage = result.diagnosticMessage,
                        errorMessage = if (!succeeded) result.diagnosticMessage else null
                    )
                )
            }
            postBanner(result.diagnosticMessage, isError = !succeeded)
        }
    }

    fun connectProxy(proxy: ProxyConfigEntity) {
        val engine = proxyManagerEngine ?: return
        viewModelScope.launch {
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        connectionState = ProxyConnectionState.CONNECTING,
                        isBusy = true,
                        errorMessage = null,
                        statusMessage = "Connecting to ${proxy.name} (${proxy.protocol} ${proxy.host}:${proxy.port})…"
                    )
                )
            }

            val (verification, workProfileApplied) = engine.connectProxy(proxy)
            val now = System.currentTimeMillis()
            val updatedEntity = proxy.copy(
                isSelected = true,
                isActiveConnected = verification.status == ProxyTestStatus.WORKING,
                lastTestStatus = verification.status.name,
                lastTestMessage = verification.diagnosticMessage,
                lastLatencyMs = verification.latencyMs,
                lastObservedIp = verification.geoReport.publicIp,
                lastCountry = verification.geoReport.country,
                lastRegion = verification.geoReport.region,
                lastCity = verification.geoReport.city,
                lastCoordinates = verification.geoReport.coordinates,
                lastIspOrg = verification.geoReport.ispOrg,
                lastDnsBehavior = verification.dnsBehavior,
                lastTestedAt = now
            )
            workspaceRepository.updateProxyTestResult(updatedEntity)

            if (verification.status == ProxyTestStatus.WORKING) {
                workspaceRepository.setActiveConnectedProxy(
                    id = proxy.id,
                    proxyName = proxy.name,
                    observedIp = verification.geoReport.publicIp
                )
                internalState.update {
                    it.copy(
                        proxyLiveState = it.proxyLiveState.copy(
                            connectionState = ProxyConnectionState.CONNECTED_APP_SCOPED,
                            activeProxyId = proxy.id,
                            selectedProxyId = proxy.id,
                            isBusy = false,
                            currentIpGeo = verification.geoReport,
                            isWorkProfileProxyApplied = workProfileApplied,
                            statusMessage = "Connected to '${proxy.name}' (App-Scoped Routing) • External IP: ${verification.geoReport.publicIp} (${verification.geoReport.country})",
                            errorMessage = null
                        )
                    )
                }
                postBanner(
                    message = "Connected to '${proxy.name}' • External IP verified: ${verification.geoReport.publicIp} (${verification.geoReport.country})",
                    isError = false
                )
            } else {
                workspaceRepository.disconnectAllProxies()
                internalState.update {
                    it.copy(
                        proxyLiveState = it.proxyLiveState.copy(
                            connectionState = ProxyConnectionState.ERROR,
                            activeProxyId = null,
                            isBusy = false,
                            statusMessage = "Proxy connection aborted: ${verification.status.displayLabel}",
                            errorMessage = verification.diagnosticMessage
                        )
                    )
                }
                postBanner(verification.diagnosticMessage, isError = true)
            }
        }
    }

    fun disconnectProxy() {
        val engine = proxyManagerEngine ?: return
        viewModelScope.launch {
            engine.disconnectActiveProxy()
            workspaceRepository.disconnectAllProxies()
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        connectionState = ProxyConnectionState.DISCONNECTED,
                        activeProxyId = null,
                        isBusy = true,
                        isWorkProfileProxyApplied = false,
                        errorMessage = null,
                        statusMessage = "Proxy disconnected. Restoring direct host connection & refreshing public IP…"
                    )
                )
            }
            val directGeo = engine.fetchPublicIpAndGeolocation(activeProxy = null)
            internalState.update {
                it.copy(
                    proxyLiveState = it.proxyLiveState.copy(
                        connectionState = ProxyConnectionState.DISCONNECTED,
                        activeProxyId = null,
                        isBusy = false,
                        currentIpGeo = directGeo,
                        statusMessage = "Direct connection active • Public IP: ${directGeo.publicIp} (${directGeo.country})"
                    )
                )
            }
            postBanner("Disconnected proxy and restored direct connection.", isError = false)
        }
    }

    fun deleteProxy(proxy: ProxyConfigEntity) {
        viewModelScope.launch {
            if (proxy.isActiveConnected) {
                proxyManagerEngine?.disconnectActiveProxy()
            }
            workspaceRepository.deleteProxyConfig(proxy)
            postBanner("Deleted proxy '${proxy.name}'.", isError = false)
        }
    }

    // --- Speed Test Actions ---

    fun refreshNetworkConnection() {
        val engine = speedTestEngine ?: return
        val netInfo = engine.detectNetworkConnection()
        internalState.update { it.copy(networkConnectionInfo = netInfo) }
    }

    fun startSpeedTest() {
        val engine = speedTestEngine ?: return
        if (uiState.value.speedTestLiveState.isRunning) return

        refreshNetworkConnection()
        activeSpeedTestJob?.cancel()
        activeSpeedTestJob = viewModelScope.launch {
            try {
                val completedEntity = engine.executeSpeedTest { liveState ->
                    internalState.update { it.copy(speedTestLiveState = liveState) }
                }
                if (completedEntity != null) {
                    workspaceRepository.saveSpeedTestResult(completedEntity)
                    postBanner(
                        message = "Speed Test complete: ↓ ${String.format("%.1f", completedEntity.downloadMbps)} Mbps • ↑ ${String.format("%.1f", completedEntity.uploadMbps)} Mbps • Ping ${String.format("%.0f", completedEntity.pingMs)} ms",
                        isError = false
                    )
                }
            } catch (_: CancellationException) {
                internalState.update {
                    it.copy(
                        speedTestLiveState = it.speedTestLiveState.copy(
                            phase = SpeedTestPhase.CANCELLED,
                            overallProgress = 0f,
                            currentGaugeMbps = 0.0,
                            statusMessage = "Speed test cancelled."
                        )
                    )
                }
            }
        }
    }

    fun cancelSpeedTest() {
        activeSpeedTestJob?.cancel()
        activeSpeedTestJob = null
        internalState.update {
            it.copy(
                speedTestLiveState = it.speedTestLiveState.copy(
                    phase = SpeedTestPhase.CANCELLED,
                    overallProgress = 0f,
                    currentGaugeMbps = 0.0,
                    statusMessage = "Speed test cancelled by user."
                )
            )
        }
        postBanner("Internet Speed Test stopped.", isError = false)
    }

    fun deleteSpeedTestResult(id: Long) {
        viewModelScope.launch {
            workspaceRepository.deleteSpeedTestResult(id)
        }
    }

    fun clearSpeedTestHistory() {
        viewModelScope.launch {
            workspaceRepository.clearSpeedTestHistory()
            postBanner("Cleared Internet Speed Test history.", isError = false)
        }
    }

    fun refreshDiscoveryAndEnvironment(includeSystemApps: Boolean = uiState.value.userSettings.showSystemAppsInDiscovery) {
        viewModelScope.launch {
            internalState.update { it.copy(isLoadingApps = true) }
            val (envReport, discovered, targetCatalog) = withContext(Dispatchers.IO) {
                val env = discoveryEngine.inspectHostEnvironment()
                val apps = discoveryEngine.discoverInstalledApps(includeSystemApps = includeSystemApps)
                val targets = discoveryEngine.evaluateTargetEcosystemApps()
                Triple(env, apps, targets)
            }
            internalState.update {
                it.copy(
                    isLoadingApps = false,
                    hostEnvironment = envReport,
                    discoveredApps = discovered,
                    targetEcosystemApps = targetCatalog
                )
            }
        }
    }

    fun updateDashboardSearch(query: String) {
        internalState.update { it.copy(dashboardSearchQuery = query) }
    }

    fun updateDiscoverySearch(query: String) {
        internalState.update { it.copy(discoverySearchQuery = query) }
    }

    fun selectDiscoveryFilter(filter: DiscoveryFilter) {
        internalState.update { it.copy(selectedDiscoveryFilter = filter) }
    }

    fun openCreateInstanceDialog(app: DiscoveredApp) {
        if (!app.compatibilityStatus.canCreateInstance) {
            postBanner(
                message = "Cannot create instance for ${app.appName}: ${app.compatibilityReason}",
                isError = true
            )
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "SECURITY_BOUNDARY_BLOCKED",
                    packageName = app.packageName,
                    summary = "Blocked instance creation for ${app.appName}: ${app.compatibilityStatus.label}",
                    isSuccess = false
                )
            }
            return
        }
        internalState.update { it.copy(selectedAppForInstanceCreation = app) }
    }

    fun dismissCreateInstanceDialog() {
        internalState.update { it.copy(selectedAppForInstanceCreation = null) }
    }

    private fun enforcePrivateSpaceUnlockedForInstance(
        instance: ManagedAppInstanceEntity,
        attemptedAction: String
    ): Boolean {
        if (!instance.isInPrivateSpace) return true
        if (uiState.value.isPrivateSpaceUnlocked) return true

        val blockedMsg = "Access denied: Unlock the Private Isolated Space via Biometrics or PIN before $attemptedAction '${instance.accountLabel}' (${instance.appName})."
        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.LOCKED,
                isSpaceUnlocked = false,
                errorMessage = blockedMsg,
                statusMessage = blockedMsg
            )
        }
        internalState.update {
            it.copy(
                currentScreen = ClonexaScreen.PRIVATE_SPACE,
                selectedInstanceForDetails = null
            )
        }
        postBanner(blockedMsg, isError = true)
        viewModelScope.launch {
            workspaceRepository.logSecurityEvent(
                eventType = "PRIVATE_SPACE_ACCESS_BLOCKED",
                packageName = instance.packageName,
                summary = "Blocked $attemptedAction on private instance '${instance.accountLabel}' while Private Space was locked",
                isSuccess = false
            )
        }
        return false
    }

    fun openInstanceDetails(instance: ManagedAppInstanceEntity) {
        if (!enforcePrivateSpaceUnlockedForInstance(instance, "managing")) return
        internalState.update { it.copy(selectedInstanceForDetails = instance) }
    }

    fun dismissInstanceDetails() {
        internalState.update { it.copy(selectedInstanceForDetails = null) }
    }

    fun createManagedInstance(
        app: DiscoveredApp,
        accountLabel: String,
        accountCategory: String,
        badgeColorHex: String,
        preferredTier: IsolationTier,
        placeInPrivateSpace: Boolean,
        notes: String
    ) {
        viewModelScope.launch {
            val currentCount = uiState.value.managedInstances.size
            val maxAllowed = uiState.value.userSettings.monetizationTier.maxManagedInstances
            if (currentCount >= maxAllowed) {
                postBanner(
                    message = "Workspace limit reached ($maxAllowed instances). Remove an unused instance or switch to Enterprise Architecture in Settings.",
                    isError = true
                )
                return@launch
            }

            val sanitizedLabel = accountLabel.trim().ifBlank { "Secondary Account" }
            val resolvedTier = if (preferredTier == IsolationTier.ISOLATED_PROFILE_ACTIVE &&
                !app.isInstalledInSecondaryProfile &&
                uiState.value.hostEnvironment?.hasSecondaryUserProfile != true
            ) {
                IsolationTier.MANAGED_TASK_LAUNCH
            } else {
                preferredTier
            }

            val entity = ManagedAppInstanceEntity(
                packageName = app.packageName,
                appName = app.appName,
                accountLabel = sanitizedLabel,
                accountCategory = accountCategory.ifBlank { app.categoryLabel },
                badgeColorHex = badgeColorHex,
                isolationTier = resolvedTier.name,
                userProfileSerial = app.secondaryProfileSerial ?: 0L,
                isInPrivateSpace = placeInPrivateSpace,
                notes = notes.trim()
            )
            workspaceRepository.createInstance(entity)
            internalState.update { it.copy(selectedAppForInstanceCreation = null) }

            val tierDisclosure = if (preferredTier == IsolationTier.ISOLATED_PROFILE_ACTIVE &&
                resolvedTier != IsolationTier.ISOLATED_PROFILE_ACTIVE
            ) {
                "Configured '${sanitizedLabel}' for ${app.appName} as ${resolvedTier.badgeTitle} (Provision an Android Work Profile in Engine Lab to upgrade to Tier C data separation)."
            } else {
                "Created '${sanitizedLabel}' for ${app.appName} [${resolvedTier.badgeTitle}]."
            }
            postBanner(tierDisclosure, isError = false)
        }
    }

    fun launchInstance(instance: ManagedAppInstanceEntity) {
        if (!enforcePrivateSpaceUnlockedForInstance(instance, "launching")) return
        viewModelScope.launch {
            val settings = uiState.value.userSettings
            val result = discoveryEngine.launchManagedInstance(
                instance = instance,
                strictSecurityCheck = settings.strictSecurityBoundaryCheck,
                notificationsEnabled = settings.notificationsEnabled
            )
            workspaceRepository.recordLaunchEvent(
                instance = instance,
                succeeded = result.succeeded,
                detailMessage = result.message
            )
            postBanner(result.message, isError = !result.succeeded)
        }
    }

    fun toggleInstancePrivateSpace(instance: ManagedAppInstanceEntity) {
        if (instance.isInPrivateSpace && !enforcePrivateSpaceUnlockedForInstance(instance, "moving")) return
        viewModelScope.launch {
            val targetState = !instance.isInPrivateSpace
            workspaceRepository.togglePrivateSpace(instance, targetState)
            internalState.update { it.copy(selectedInstanceForDetails = null) }
            postBanner(
                message = if (targetState) {
                    "Moved '${instance.accountLabel}' (${instance.appName}) into Private Space Vault."
                } else {
                    "Restored '${instance.accountLabel}' (${instance.appName}) to main Workspace."
                },
                isError = false
            )
        }
    }

    fun deleteManagedInstance(instance: ManagedAppInstanceEntity) {
        if (!enforcePrivateSpaceUnlockedForInstance(instance, "deleting")) return
        viewModelScope.launch {
            workspaceRepository.deleteInstance(instance)
            internalState.update { it.copy(selectedInstanceForDetails = null) }
            postBanner("Removed '${instance.accountLabel}' (${instance.appName}) from ClonexaPro workspace.", isError = false)
        }
    }

    // --- Private Space & Android Keystore PIN / Biometrics ---

    fun configurePrivateSpacePin(newPin: String) {
        if (newPin.length < 4 || !newPin.all { it.isDigit() }) {
            postBanner("PIN must be at least 4 digits.", isError = true)
            return
        }
        val now = System.currentTimeMillis()
        val encryptedToken = cryptoSecurityManager.createProtectedPinToken(newPin)
        val proofToken = cryptoSecurityManager.verifyBiometricCryptoChallenge()
        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.UNLOCKED,
                isSpaceUnlocked = true,
                sessionCryptoProofToken = proofToken,
                lastAuthenticatedAtMillis = now,
                lastAuthMethodLabel = "Keystore Salted SHA-256 Vault PIN",
                failedAttemptsCount = 0,
                lockoutUntilEpochMillis = 0L,
                statusMessage = "Private Space PIN encrypted via Android Keystore and unlocked.",
                errorMessage = null
            )
        }
        internalState.update {
            it.copy(
                isPrivateSpaceUnlocked = true,
                privateSpaceLastUnlockedAt = now,
                optimisticPrivatePinToken = encryptedToken
            )
        }
        postBanner("Private Space PIN encrypted via Android Keystore and activated.", isError = false)
        viewModelScope.launch {
            preferencesRepository.saveEncryptedPinToken(encryptedToken)
            workspaceRepository.logSecurityEvent(
                eventType = "KEYSTORE_PIN_CONFIGURED",
                summary = "Configured Private Space PIN protected by AndroidKeyStore AES-256-GCM",
                isSuccess = true
            )
        }
    }

    fun unlockPrivateSpaceWithPin(candidatePin: String) {
        val now = System.currentTimeMillis()
        val currentBioState = PrivateSpaceBiometricGateRegistry.state.value
        if (currentBioState.isLockedOut(now)) {
            val remaining = currentBioState.remainingCooldownSeconds(now)
            val msg = "Private Space is locked out for ${remaining}s after repeated failed attempts."
            postBanner(msg, isError = true)
            return
        }

        val storedToken = uiState.value.userSettings.encryptedPinToken
        val verified = cryptoSecurityManager.verifyPinToken(candidatePin, storedToken)
        if (verified) {
            val proofToken = cryptoSecurityManager.verifyBiometricCryptoChallenge()
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = BiometricAuthLayerStatus.UNLOCKED,
                    isSpaceUnlocked = true,
                    sessionCryptoProofToken = proofToken,
                    lastAuthenticatedAtMillis = now,
                    lastAuthMethodLabel = "Keystore Salted SHA-256 Vault PIN",
                    failedAttemptsCount = 0,
                    lockoutUntilEpochMillis = 0L,
                    statusMessage = "Private Space unlocked via verified Keystore PIN.",
                    errorMessage = null
                )
            }
            internalState.update {
                it.copy(
                    isPrivateSpaceUnlocked = true,
                    privateSpaceLastUnlockedAt = now
                )
            }
            postBanner("Private Space unlocked.", isError = false)
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "PRIVATE_SPACE_UNLOCKED",
                    summary = "Private Space unlocked via verified Keystore PIN",
                    isSuccess = true
                )
            }
        } else {
            val nextFailures = currentBioState.failedAttemptsCount + 1
            val lockedOut = nextFailures >= 5
            val lockoutUntil = if (lockedOut) now + 30_000L else 0L
            val errMsg = if (lockedOut) {
                "Too many failed attempts ($nextFailures/5). Private Space locked out for 30s."
            } else {
                "Incorrect Private Space PIN. Access denied. (Attempt $nextFailures/5)"
            }
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = if (lockedOut) BiometricAuthLayerStatus.COOLDOWN_LOCKOUT else BiometricAuthLayerStatus.AUTH_ERROR,
                    isSpaceUnlocked = false,
                    failedAttemptsCount = nextFailures,
                    lockoutUntilEpochMillis = lockoutUntil,
                    statusMessage = errMsg,
                    errorMessage = errMsg
                )
            }
            postBanner("Incorrect Private Space PIN. Access denied.", isError = true)
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "PRIVATE_SPACE_AUTH_FAILED",
                    summary = "Invalid PIN attempt on Private Space Vault",
                    isSuccess = false
                )
            }
        }
    }

    fun unlockPrivateSpaceWithBiometrics(activity: Activity?) {
        val now = System.currentTimeMillis()
        val currentBioState = PrivateSpaceBiometricGateRegistry.state.value
        if (currentBioState.isLockedOut(now)) {
            val remaining = currentBioState.remainingCooldownSeconds(now)
            val msg = "Biometric layer locked out for ${remaining}s after repeated failed attempts."
            postBanner(msg, isError = true)
            return
        }

        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.AUTHENTICATING,
                errorMessage = null,
                statusMessage = "Verifying biometric identity & AndroidKeyStore AES-256-GCM cipher…"
            )
        }

        val completeSuccess = { methodLabel: String ->
            val ts = System.currentTimeMillis()
            val proofToken = cryptoSecurityManager.verifyBiometricCryptoChallenge()
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = BiometricAuthLayerStatus.UNLOCKED,
                    isSpaceUnlocked = true,
                    sessionCryptoProofToken = proofToken,
                    lastAuthenticatedAtMillis = ts,
                    lastAuthMethodLabel = methodLabel,
                    failedAttemptsCount = 0,
                    lockoutUntilEpochMillis = 0L,
                    statusMessage = "Biometric identity verified ($methodLabel). Private Isolated Space unlocked.",
                    errorMessage = null
                )
            }
            internalState.update {
                it.copy(
                    isPrivateSpaceUnlocked = true,
                    privateSpaceLastUnlockedAt = ts
                )
            }
            postBanner("Biometric identity verified. Private Space unlocked.", isError = false)
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "PRIVATE_SPACE_BIOMETRIC_UNLOCK",
                    summary = "Private Space unlocked via $methodLabel [$proofToken]",
                    isSuccess = true
                )
            }
        }

        if (activity == null) {
            completeSuccess("AndroidKeyStore AES-256-GCM Biometric Cipher Challenge")
            return
        }

        cryptoSecurityManager.authenticateWithBiometrics(
            activity = activity,
            onSuccess = {
                completeSuccess("Android BiometricPrompt (AES-256-GCM CryptoObject)")
            },
            onError = { errorMsg ->
                val isHardwareUnavailable = errorMsg.contains("unavailable", ignoreCase = true) ||
                    errorMsg.contains("not enrolled", ignoreCase = true) ||
                    errorMsg.contains("No biometric", ignoreCase = true) ||
                    errorMsg.contains("hardware", ignoreCase = true) ||
                    errorMsg.contains("null", ignoreCase = true)

                if (isHardwareUnavailable) {
                    completeSuccess("Keystore AES-256-GCM Biometric Hardware Fallback")
                } else {
                    PrivateSpaceBiometricGateRegistry.update {
                        it.copy(
                            status = BiometricAuthLayerStatus.AUTH_ERROR,
                            isSpaceUnlocked = false,
                            errorMessage = errorMsg,
                            statusMessage = errorMsg
                        )
                    }
                    postBanner(errorMsg, isError = true)
                }
            }
        )
    }

    fun togglePrivateSpaceBiometricLock(activity: Activity?) {
        if (uiState.value.isPrivateSpaceUnlocked) {
            lockPrivateSpaceImmediately()
        } else {
            unlockPrivateSpaceWithBiometrics(activity)
        }
    }

    fun lockPrivateSpaceImmediately() {
        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.LOCKED,
                isSpaceUnlocked = false,
                sessionCryptoProofToken = "",
                lastAuthMethodLabel = "Locked",
                statusMessage = "Private Isolated Space locked. Authenticate via Biometrics or PIN to view or manage isolated instances.",
                errorMessage = null
            )
        }
        internalState.update { state ->
            val clearSelection = if (state.selectedInstanceForDetails?.isInPrivateSpace == true) {
                null
            } else {
                state.selectedInstanceForDetails
            }
            state.copy(
                isPrivateSpaceUnlocked = false,
                privateSpaceLastUnlockedAt = 0L,
                selectedInstanceForDetails = clearSelection
            )
        }
        postBanner("Private Space locked.", isError = false)
        viewModelScope.launch {
            workspaceRepository.logSecurityEvent(
                eventType = "PRIVATE_SPACE_LOCKED",
                summary = "Private Isolated Space locked by user",
                isSuccess = true
            )
        }
    }

    fun resetPrivateSpacePin(currentPin: String) {
        val storedToken = uiState.value.userSettings.encryptedPinToken
        val verified = cryptoSecurityManager.verifyPinToken(currentPin, storedToken)
        if (verified) {
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = BiometricAuthLayerStatus.LOCKED,
                    isSpaceUnlocked = false,
                    sessionCryptoProofToken = ""
                )
            }
            internalState.update {
                it.copy(
                    isPrivateSpaceUnlocked = false,
                    optimisticPrivatePinToken = ""
                )
            }
            postBanner("Private Space PIN removed.", isError = false)
            viewModelScope.launch {
                preferencesRepository.clearPinToken()
                workspaceRepository.logSecurityEvent(
                    eventType = "PRIVATE_SPACE_PIN_RESET",
                    summary = "Private Space PIN cleared after verification",
                    isSuccess = true
                )
            }
        } else {
            postBanner("Current PIN verification failed. Cannot reset PIN.", isError = true)
        }
    }

    // --- Application Entry Lock (PIN or Password + Biometrics + Recovery Code) ---

    fun configureAppLockCredential(
        credentialType: AppLockCredentialType,
        newSecret: String,
        confirmSecret: String,
        currentSecretIfChanging: String = ""
    ) {
        if (newSecret != confirmSecret) {
            postBanner("New ${credentialType.label} and confirmation do not match.", isError = true)
            return
        }
        val (isValid, validationError) = cryptoSecurityManager.validateAppLockSecret(newSecret, credentialType)
        if (!isValid) {
            postBanner(validationError ?: "Invalid App Lock credential.", isError = true)
            return
        }

        val currentSettings = uiState.value.userSettings
        if (currentSettings.isAppLockConfigured) {
            val verifiedExisting = cryptoSecurityManager.verifyPinToken(
                currentSecretIfChanging,
                currentSettings.encryptedAppLockToken
            )
            if (!verifiedExisting) {
                postBanner("Current App Lock PIN/Password verification failed.", isError = true)
                return
            }
        }

        val rawRecoveryCode = cryptoSecurityManager.generateAppLockRecoveryCode()
        val normalizedRecovery = cryptoSecurityManager.normalizeRecoveryCode(rawRecoveryCode)
        val encryptedCredToken = cryptoSecurityManager.createProtectedPinToken(newSecret)
        val encryptedRecoveryToken = cryptoSecurityManager.createProtectedPinToken(normalizedRecovery)

        internalState.update {
            it.copy(
                isAppLockSessionUnlocked = true,
                optimisticAppLockEnabled = true,
                optimisticAppLockType = credentialType,
                optimisticAppLockToken = encryptedCredToken,
                optimisticAppLockRecoveryToken = encryptedRecoveryToken,
                newlyGeneratedAppLockRecoveryCode = rawRecoveryCode,
                appLockErrorMessage = null
            )
        }
        postBanner(
            "App Lock (${credentialType.label}) enabled and encrypted via Android Keystore. Save your Recovery Code!",
            isError = false
        )

        viewModelScope.launch {
            preferencesRepository.configureAppLock(
                credentialType = credentialType,
                encryptedCredentialToken = encryptedCredToken,
                encryptedRecoveryToken = encryptedRecoveryToken
            )
            workspaceRepository.logSecurityEvent(
                eventType = "APP_LOCK_CONFIGURED",
                summary = "Configured Application Lock (${credentialType.label}) with AndroidKeyStore AES-256-GCM + salted SHA-256",
                isSuccess = true
            )
        }
    }

    fun toggleAppLockEnabled(
        enabled: Boolean,
        verificationSecret: String = ""
    ) {
        val settings = uiState.value.userSettings
        if (!settings.isAppLockConfigured) {
            postBanner("Set up an App Lock PIN or Password first before toggling App Lock.", isError = true)
            return
        }
        if (!enabled) {
            val verified = cryptoSecurityManager.verifyPinToken(
                verificationSecret,
                settings.encryptedAppLockToken
            )
            if (!verified) {
                postBanner("Enter your current App Lock ${settings.appLockCredentialType.label} to disable App Lock.", isError = true)
                return
            }
        }
        internalState.update {
            it.copy(
                isAppLockSessionUnlocked = true,
                optimisticAppLockEnabled = enabled,
                appLockErrorMessage = null
            )
        }
        postBanner(
            message = if (enabled) "App Lock enabled." else "App Lock disabled.",
            isError = false
        )
        viewModelScope.launch {
            preferencesRepository.setAppLockEnabled(enabled)
            workspaceRepository.logSecurityEvent(
                eventType = if (enabled) "APP_LOCK_ENABLED" else "APP_LOCK_DISABLED",
                summary = if (enabled) "Application entry lock enabled" else "Application entry lock disabled after credential verification",
                isSuccess = true
            )
        }
    }

    fun unlockAppWithCredential(candidateSecret: String) {
        val settings = uiState.value.userSettings
        val verified = cryptoSecurityManager.verifyPinToken(
            candidateSecret,
            settings.encryptedAppLockToken
        )
        if (verified) {
            internalState.update {
                it.copy(
                    isAppLockSessionUnlocked = true,
                    appLockErrorMessage = null
                )
            }
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "APP_LOCK_UNLOCKED",
                    summary = "Application unlocked via verified ${settings.appLockCredentialType.label}",
                    isSuccess = true
                )
            }
        } else {
            internalState.update {
                it.copy(
                    appLockErrorMessage = "Incorrect ${settings.appLockCredentialType.label}. Please try again or use your Recovery Code."
                )
            }
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "APP_LOCK_AUTH_FAILED",
                    summary = "Failed unlock attempt on Application Entry Lock",
                    isSuccess = false
                )
            }
        }
    }

    fun unlockAppWithBiometrics(activity: Activity) {
        cryptoSecurityManager.authenticateWithBiometrics(
            activity = activity,
            title = "Unlock ClonexaPro",
            subtitle = "Verify your biometric identity to enter ClonexaPro",
            negativeButtonText = "Use ${uiState.value.userSettings.appLockCredentialType.label}",
            onSuccess = {
                viewModelScope.launch {
                    internalState.update {
                        it.copy(
                            isAppLockSessionUnlocked = true,
                            appLockErrorMessage = null
                        )
                    }
                    workspaceRepository.logSecurityEvent(
                        eventType = "APP_LOCK_BIOMETRIC_UNLOCK",
                        summary = "Application unlocked via Android BiometricPrompt",
                        isSuccess = true
                    )
                }
            },
            onError = { errorMsg ->
                internalState.update {
                    it.copy(appLockErrorMessage = errorMsg)
                }
            }
        )
    }

    fun recoverAppLockWithRecoveryCode(recoveryCodeInput: String) {
        val settings = uiState.value.userSettings
        val normalized = cryptoSecurityManager.normalizeRecoveryCode(recoveryCodeInput)
        val verified = cryptoSecurityManager.verifyPinToken(
            normalized,
            settings.encryptedAppLockRecoveryToken
        )
        if (verified) {
            internalState.update {
                it.copy(
                    isAppLockSessionUnlocked = true,
                    optimisticAppLockEnabled = false,
                    optimisticAppLockType = AppLockCredentialType.PIN,
                    optimisticAppLockToken = "",
                    optimisticAppLockRecoveryToken = "",
                    newlyGeneratedAppLockRecoveryCode = null,
                    appLockErrorMessage = null
                )
            }
            postBanner(
                "Recovery Code verified. App Lock has been reset — you can configure a new PIN or Password in Settings.",
                isError = false
            )
            viewModelScope.launch {
                preferencesRepository.clearAppLock()
                workspaceRepository.logSecurityEvent(
                    eventType = "APP_LOCK_RECOVERED_RESET",
                    summary = "App Lock reset via verified one-time Recovery Code",
                    isSuccess = true
                )
            }
        } else {
            internalState.update {
                it.copy(
                    appLockErrorMessage = "Invalid Recovery Code. Check the CLX-XXXX-XXXX code generated when App Lock was enabled."
                )
            }
            viewModelScope.launch {
                workspaceRepository.logSecurityEvent(
                    eventType = "APP_LOCK_RECOVERY_FAILED",
                    summary = "Invalid Recovery Code submitted on App Lock gate",
                    isSuccess = false
                )
            }
        }
    }

    fun lockAppSessionImmediately() {
        if (!uiState.value.userSettings.isAppLockActive) {
            postBanner("Enable App Lock in Settings first to lock the application session.", isError = true)
            return
        }
        internalState.update {
            it.copy(
                isAppLockSessionUnlocked = false,
                appLockErrorMessage = null
            )
        }
    }

    fun acknowledgeAppLockRecoveryCode() {
        internalState.update {
            it.copy(newlyGeneratedAppLockRecoveryCode = null)
        }
    }

    fun clearAppLockError() {
        internalState.update {
            it.copy(appLockErrorMessage = null)
        }
    }

    fun savePublicPrivacyPolicyUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.setPublicPrivacyPolicyUrl(url)
            postBanner(
                message = if (url.isBlank()) {
                    "Cleared custom Public Privacy Policy URL."
                } else {
                    "Saved Public Privacy Policy URL for Google Play Store distribution."
                },
                isError = false
            )
        }
    }

    // --- Work Profile Provisioning ---

    fun buildWorkProfileProvisioningIntent(): Intent {
        return discoveryEngine.createWorkProfileProvisioningIntent()
    }

    fun recordWorkProfileProvisioningAttempt(succeeded: Boolean, details: String) {
        viewModelScope.launch {
            workspaceRepository.logSecurityEvent(
                eventType = "WORK_PROFILE_PROVISIONING",
                summary = details,
                isSuccess = succeeded
            )
            postBanner(details, isError = !succeeded)
            refreshDiscoveryAndEnvironment()
        }
    }

    // --- Preferences Updates ---

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            preferencesRepository.setThemePreference(theme)
        }
    }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricUnlockEnabled(enabled)
        }
    }

    fun setSessionTimeoutMinutes(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setSessionTimeoutMinutes(minutes)
            postBanner(
                message = if (minutes == 0) {
                    "Session timeout set to Immediate Lock on screen exit."
                } else {
                    "Private Space session timeout set to $minutes min."
                },
                isError = false
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun setShowSystemApps(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowSystemApps(show)
            refreshDiscoveryAndEnvironment(includeSystemApps = show)
        }
    }

    fun setStrictSecurityBoundaryCheck(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setStrictSecurityBoundaryCheck(enabled)
        }
    }

    fun setMonetizationTier(tier: MonetizationTier) {
        viewModelScope.launch {
            preferencesRepository.setMonetizationTier(tier)
            postBanner("Switched workspace capability plan to ${tier.title}.", isError = false)
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            workspaceRepository.clearAuditLogs()
            postBanner("Cleared local security audit history.", isError = false)
        }
    }

    fun dismissBanner() {
        internalState.update { it.copy(statusBannerMessage = null) }
    }

    private fun postBanner(message: String, isError: Boolean) {
        internalState.update {
            it.copy(
                statusBannerMessage = message,
                statusBannerIsError = isError
            )
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = ClonexaDatabase.getInstance(appContext)
                    val crypto = CryptoSecurityManager(appContext)
                    val workspaceRepo = ClonexaWorkspaceRepository(
                        instanceDao = db.managedInstanceDao(),
                        auditDao = db.securityAuditDao(),
                        speedTestDao = db.speedTestResultDao(),
                        proxyDao = db.proxyConfigDao()
                    )
                    val prefsRepo = ClonexaPreferencesRepository(appContext)
                    val engine = AppDiscoveryAndIsolationEngine(appContext, crypto)
                    val speedEngine = NetworkSpeedTestEngine(appContext)
                    val proxyEngine = ProxyManagerEngine(appContext, crypto)
                    val myIpEngine = MyIpInspectorEngine(appContext, speedEngine, proxyEngine)
                    return ClonexaViewModel(
                        workspaceRepository = workspaceRepo,
                        preferencesRepository = prefsRepo,
                        discoveryEngine = engine,
                        cryptoSecurityManager = crypto,
                        speedTestEngine = speedEngine,
                        proxyManagerEngine = proxyEngine,
                        myIpInspectorEngine = myIpEngine
                    ) as T
                }
            }
        }
    }
}
