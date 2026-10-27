package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ClonexaScreen
import com.example.model.ThemePreference
import com.example.security.BiometricSpaceAuthViewModel
import com.example.ui.ClonexaUiState
import com.example.ui.ClonexaViewModel
import com.example.ui.components.AppLockGateScreen
import com.example.ui.components.ClonexaBrandTopBar
import com.example.ui.components.CreateInstanceModalDialog
import com.example.ui.components.InstanceDetailsSheetDialog
import androidx.compose.material.icons.filled.Dns
import com.example.ui.screens.AppDiscoveryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EngineArchitectureScreen
import com.example.ui.screens.MyIpScreen
import com.example.ui.screens.PrivateSpaceScreen
import com.example.ui.screens.ProxyManagerScreen
import com.example.ui.screens.SettingsAndLegalScreen
import com.example.ui.screens.SpeedTestScreen
import com.example.ui.theme.ClonexaProTheme
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: ClonexaViewModel = viewModel(
                factory = ClonexaViewModel.provideFactory(context)
            )
            val biometricSpaceAuthViewModel: BiometricSpaceAuthViewModel = viewModel(
                factory = BiometricSpaceAuthViewModel.provideFactory(context)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            @Suppress("UNUSED_VARIABLE")
            val biometricSpaceState by biometricSpaceAuthViewModel.uiState.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (uiState.userSettings.themePreference) {
                ThemePreference.SYSTEM -> systemDark
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }

            ClonexaProTheme(darkTheme = useDarkTheme) {
                ClonexaProApp(
                    uiState = uiState,
                    isDarkTheme = useDarkTheme,
                    viewModel = viewModel
                )
            }
        }
    }
}

private data class NavDestinationItem(
    val screen: ClonexaScreen,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val NAV_ITEMS = listOf(
    NavDestinationItem(ClonexaScreen.DASHBOARD, "Home", Icons.Default.Dashboard, "nav_dashboard"),
    NavDestinationItem(ClonexaScreen.DISCOVERY, "Apps", Icons.Default.Apps, "nav_discovery"),
    NavDestinationItem(ClonexaScreen.SPEED_TEST, "Speed", Icons.Default.Speed, "nav_speed_test"),
    NavDestinationItem(ClonexaScreen.PROXY_MANAGER, "Proxy", Icons.Default.Public, "nav_proxy"),
    NavDestinationItem(ClonexaScreen.PRIVATE_SPACE, "Vault", Icons.Default.Shield, "nav_private_space"),
    NavDestinationItem(ClonexaScreen.SETTINGS, "Settings", Icons.Default.Settings, "nav_settings")
)

@Composable
fun ClonexaProApp(
    uiState: ClonexaUiState,
    isDarkTheme: Boolean,
    viewModel: ClonexaViewModel
) {
    val context = LocalContext.current

    // Application-entry App Lock Gate (PIN or Password + Biometrics + Recovery Code)
    if (uiState.requiresAppLockEntryGate) {
        AppLockGateScreen(
            credentialType = uiState.userSettings.appLockCredentialType,
            biometricSupportedAndEnabled = uiState.userSettings.biometricUnlockEnabled &&
                uiState.hostEnvironment?.deviceCredentialOrBiometricEnrolled == true,
            errorMessage = uiState.appLockErrorMessage,
            onUnlockWithCredential = viewModel::unlockAppWithCredential,
            onUnlockWithBiometrics = {
                (context as? android.app.Activity)?.let { activity ->
                    viewModel.unlockAppWithBiometrics(activity)
                }
            },
            onRecoverWithCode = viewModel::recoverAppLockWithRecoveryCode,
            onClearError = viewModel::clearAppLockError
        )
        return
    }

    // Handle system back navigation on secondary tabs
    BackHandler(enabled = uiState.currentScreen != ClonexaScreen.DASHBOARD) {
        viewModel.navigateTo(ClonexaScreen.DASHBOARD)
    }

    // Instance Creation Dialog
    uiState.selectedAppForInstanceCreation?.let { targetApp ->
        CreateInstanceModalDialog(
            app = targetApp,
            hasSecondaryProfile = uiState.hostEnvironment?.hasSecondaryUserProfile == true,
            onDismiss = viewModel::dismissCreateInstanceDialog,
            onCreate = { label, category, colorHex, tier, inPrivate, notes ->
                viewModel.createManagedInstance(
                    app = targetApp,
                    accountLabel = label,
                    accountCategory = category,
                    badgeColorHex = colorHex,
                    preferredTier = tier,
                    placeInPrivateSpace = inPrivate,
                    notes = notes
                )
            }
        )
    }

    // Instance Options / Details Modal
    uiState.selectedInstanceForDetails?.let { instance ->
        InstanceDetailsSheetDialog(
            instance = instance,
            onDismiss = viewModel::dismissInstanceDetails,
            onLaunch = { viewModel.launchInstance(instance) },
            onTogglePrivateSpace = { viewModel.toggleInstancePrivateSpace(instance) },
            onDelete = { viewModel.deleteManagedInstance(instance) }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                ClonexaBrandTopBar(
                    hasSecondaryProfile = uiState.hostEnvironment?.hasSecondaryUserProfile == true,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        val nextTheme = if (isDarkTheme) ThemePreference.LIGHT else ThemePreference.DARK
                        viewModel.setThemePreference(nextTheme)
                    },
                    onOpenPrivateSpace = {
                        viewModel.navigateTo(ClonexaScreen.PRIVATE_SPACE)
                    }
                )
            },
            floatingActionButton = {
                if (uiState.currentScreen == ClonexaScreen.DASHBOARD) {
                    FloatingActionButton(
                        onClick = { viewModel.navigateTo(ClonexaScreen.DISCOVERY) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_instance")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add new app instance"
                        )
                    }
                }
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        NAV_ITEMS.forEach { item ->
                            NavigationBarItem(
                                selected = uiState.currentScreen == item.screen,
                                onClick = { viewModel.navigateTo(item.screen) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        NAV_ITEMS.forEach { item ->
                            NavigationRailItem(
                                selected = uiState.currentScreen == item.screen,
                                onClick = { viewModel.navigateTo(item.screen) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                        NavigationRailItem(
                            selected = uiState.currentScreen == ClonexaScreen.MY_IP_ADDRESS,
                            onClick = { viewModel.navigateTo(ClonexaScreen.MY_IP_ADDRESS) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = "My IP Address"
                                )
                            },
                            label = { Text("My IP") },
                            modifier = Modifier.testTag("nav_my_ip")
                        )
                        NavigationRailItem(
                            selected = uiState.currentScreen == ClonexaScreen.ARCHITECTURE_LAB,
                            onClick = { viewModel.navigateTo(ClonexaScreen.ARCHITECTURE_LAB) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.DeveloperBoard,
                                    contentDescription = "Engine Lab"
                                )
                            },
                            label = { Text("Lab") },
                            modifier = Modifier.testTag("nav_architecture")
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Status / Operation Feedback Banner
                    AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                        val msg = uiState.statusBannerMessage ?: ""
                        val bannerColor = if (uiState.statusBannerIsError) {
                            StatusRestrictedRed
                        } else {
                            StatusCompatibleGreen
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("status_feedback_banner"),
                            color = bannerColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.statusBannerIsError) {
                                            Icons.Default.ErrorOutline
                                        } else {
                                            Icons.Default.Verified
                                        },
                                        contentDescription = null,
                                        tint = bannerColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::dismissBanner,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss notification banner",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (uiState.currentScreen) {
                            ClonexaScreen.DASHBOARD -> DashboardScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::updateDashboardSearch,
                                onNavigate = viewModel::navigateTo,
                                onSelectAppForInstance = viewModel::openCreateInstanceDialog,
                                onLaunchInstance = viewModel::launchInstance,
                                onManageInstance = viewModel::openInstanceDetails
                            )
                            ClonexaScreen.DISCOVERY -> AppDiscoveryScreen(
                                uiState = uiState,
                                onSearchQueryChange = viewModel::updateDiscoverySearch,
                                onFilterChange = viewModel::selectDiscoveryFilter,
                                onToggleShowSystemApps = viewModel::setShowSystemApps,
                                onRefreshApps = { viewModel.refreshDiscoveryAndEnvironment() },
                                onSelectApp = viewModel::openCreateInstanceDialog
                            )
                            ClonexaScreen.SPEED_TEST -> SpeedTestScreen(
                                uiState = uiState,
                                onStartTest = viewModel::startSpeedTest,
                                onCancelTest = viewModel::cancelSpeedTest,
                                onRefreshNetwork = viewModel::refreshNetworkConnection,
                                onDeleteHistoryItem = viewModel::deleteSpeedTestResult,
                                onClearHistory = viewModel::clearSpeedTestHistory
                            )
                            ClonexaScreen.PROXY_MANAGER -> ProxyManagerScreen(
                                uiState = uiState,
                                onRefreshIpGeo = viewModel::refreshPublicIpGeolocation,
                                onSaveProxy = viewModel::saveProxyConfiguration,
                                onDecryptPasswordForEdit = viewModel::decryptProxyPasswordForEditing,
                                onSelectProxy = viewModel::selectProxyConfig,
                                onTestProxy = viewModel::testProxyConfig,
                                onConnectProxy = viewModel::connectProxy,
                                onDisconnectProxy = viewModel::disconnectProxy,
                                onDeleteProxy = viewModel::deleteProxy
                            )
                            ClonexaScreen.MY_IP_ADDRESS -> MyIpScreen(
                                uiState = uiState,
                                onSelectMode = viewModel::selectMyIpLookupMode,
                                onRefreshIp = { viewModel.refreshMyIpAddress() },
                                onOpenDetailsDialog = viewModel::openMyIpDetailsDialog,
                                onDismissDetailsDialog = viewModel::dismissMyIpDetailsDialog,
                                onIpCopied = viewModel::notifyIpCopiedToClipboard,
                                onNavigateToProxyManager = {
                                    viewModel.navigateTo(ClonexaScreen.PROXY_MANAGER)
                                }
                            )
                            ClonexaScreen.PRIVATE_SPACE -> PrivateSpaceScreen(
                                uiState = uiState,
                                onConfigurePin = viewModel::configurePrivateSpacePin,
                                onUnlockWithPin = viewModel::unlockPrivateSpaceWithPin,
                                onUnlockWithBiometrics = viewModel::unlockPrivateSpaceWithBiometrics,
                                onLockImmediately = viewModel::lockPrivateSpaceImmediately,
                                onResetPin = viewModel::resetPrivateSpacePin,
                                onSetSessionTimeout = viewModel::setSessionTimeoutMinutes,
                                onNavigateToDiscovery = viewModel::navigateTo,
                                onLaunchInstance = viewModel::launchInstance,
                                onManageInstance = viewModel::openInstanceDetails
                            )
                            ClonexaScreen.ARCHITECTURE_LAB -> EngineArchitectureScreen(
                                uiState = uiState,
                                onBuildWorkProfileIntent = viewModel::buildWorkProfileProvisioningIntent,
                                onWorkProfileProvisioningResult = viewModel::recordWorkProfileProvisioningAttempt,
                                onRefreshEnvironment = { viewModel.refreshDiscoveryAndEnvironment() }
                            )
                            ClonexaScreen.SETTINGS -> SettingsAndLegalScreen(
                                uiState = uiState,
                                onSetTheme = viewModel::setThemePreference,
                                onSetBiometricEnabled = viewModel::setBiometricUnlockEnabled,
                                onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                                onSetStrictSecurityCheck = viewModel::setStrictSecurityBoundaryCheck,
                                onSetMonetizationTier = viewModel::setMonetizationTier,
                                onClearAuditLogs = viewModel::clearAuditLogs,
                                onConfigureAppLock = viewModel::configureAppLockCredential,
                                onToggleAppLockEnabled = viewModel::toggleAppLockEnabled,
                                onLockAppNow = viewModel::lockAppSessionImmediately,
                                onAcknowledgeRecoveryCode = viewModel::acknowledgeAppLockRecoveryCode,
                                onSavePublicPrivacyUrl = viewModel::savePublicPrivacyPolicyUrl
                            )
                        }
                    }
                }
            }
        }
    }
}
