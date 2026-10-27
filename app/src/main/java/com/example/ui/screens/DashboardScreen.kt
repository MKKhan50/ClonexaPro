package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ClonexaScreen
import com.example.model.DiscoveredApp
import com.example.model.ManagedAppInstanceEntity
import com.example.model.TargetEcosystemApp
import com.example.ui.ClonexaUiState
import com.example.ui.components.AppDrawableIcon
import com.example.ui.components.CompatibilityStatusChip
import com.example.ui.components.ManagedInstanceCard
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusWarningAmber

@Composable
fun DashboardScreen(
    uiState: ClonexaUiState,
    onSearchQueryChange: (String) -> Unit,
    onNavigate: (ClonexaScreen) -> Unit,
    onSelectAppForInstance: (DiscoveredApp) -> Unit,
    onLaunchInstance: (ManagedAppInstanceEntity) -> Unit,
    onManageInstance: (ManagedAppInstanceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconByPackage = remember(uiState.discoveredApps) {
        uiState.discoveredApps.associate { it.packageName to it.iconDrawable }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Hero Isolation Banner with Official ClonexaPro Branding
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_hero_isolation_banner),
                        contentDescription = "ClonexaPro Isolation Architecture Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.hostEnvironment?.hasSecondaryUserProfile == true) {
                                            "OS WORK / CLONE PROFILE DETECTED"
                                        } else {
                                            "HOST MULTI-INSTANCE WORKSPACE"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Image(
                                painter = painterResource(id = R.drawable.clonexapro_official_logo),
                                contentDescription = "ClonexaPro Official Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .testTag("dashboard_hero_official_logo")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "ClonexaPro • Parallel Accounts & Isolated Profiles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.hostEnvironment?.activeEngineSummary
                                ?: "Evaluating Android UserManager, LauncherApps & DevicePolicyManager isolation capabilities…",
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color(0xFFCBD5E1),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onNavigate(ClonexaScreen.DISCOVERY) },
                                modifier = Modifier.testTag("add_apps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Apps")
                            }
                            OutlinedButton(
                                onClick = { onNavigate(ClonexaScreen.ARCHITECTURE_LAB) },
                                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF94A3B8)),
                                modifier = Modifier.testTag("open_engine_lab_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeveloperBoard,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Engine Lab",
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Telemetry & Quick Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "Active Slots",
                    value = uiState.publicManagedInstances.size.toString(),
                    subtitle = "Workspace",
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Discovered",
                    value = uiState.discoveredApps.size.toString(),
                    subtitle = "Device Apps",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(ClonexaScreen.DISCOVERY) }
                )
                MetricStatCard(
                    title = "Private Vault",
                    value = uiState.privateManagedInstances.size.toString(),
                    subtitle = if (uiState.isPrivateSpaceUnlocked) "Unlocked" else "Locked",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(ClonexaScreen.PRIVATE_SPACE) }
                )
            }
        }

        // 2B. Internet Speed Test Feature Card (Requirement 1)
        item {
            val latestSpeed = uiState.speedTestHistory.firstOrNull()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ClonexaScreen.SPEED_TEST) }
                    .testTag("dashboard_speed_test_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                        )
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = uiState.networkConnectionInfo.connectionTypeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Internet Speed Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (latestSpeed != null) {
                                "Last: ↓ ${String.format("%.1f", latestSpeed.downloadMbps)} Mbps • ↑ ${String.format("%.1f", latestSpeed.uploadMbps)} Mbps • Ping ${String.format("%.0f", latestSpeed.pingMs)} ms"
                            } else {
                                "Measure real Download, Upload, Ping & Jitter for your isolated accounts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onNavigate(ClonexaScreen.SPEED_TEST) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_speed_test_button")
                    ) {
                        Text("Test Speed")
                    }
                }
            }
        }

        // 2C. Proxy Manager & IP Geolocation Feature Card
        item {
            val activeProxy = uiState.activeConnectedProxy
            val geo = uiState.proxyLiveState.currentIpGeo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ClonexaScreen.PROXY_MANAGER) }
                    .testTag("dashboard_proxy_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (activeProxy != null) StatusCompatibleGreen else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = if (activeProxy != null) {
                                StatusCompatibleGreen.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (activeProxy != null) {
                                    "Connected • ${activeProxy.name} (${activeProxy.protocol})"
                                } else {
                                    "Direct Routing • ${uiState.proxyConfigs.size} Saved Proxies"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeProxy != null) StatusCompatibleGreen else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Proxy Manager & IP Inspector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (geo.publicIp != "Unavailable") {
                                "Public IP: ${geo.publicIp} (${geo.country}, ${geo.region})"
                            } else {
                                "Configure HTTP, HTTPS & SOCKS5 proxies and verify external IP geolocation"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onNavigate(ClonexaScreen.PROXY_MANAGER) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_proxy_manager_button")
                    ) {
                        Text("Proxies")
                    }
                }
            }
        }

        // 2D. Combined IP Checker & Privacy Consistency Score Feature Card
        item {
            val ipSnapshot = uiState.myIpLiveState.displayedSnapshot
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ClonexaScreen.MY_IP_ADDRESS) }
                    .testTag("dashboard_my_ip_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${ipSnapshot.mode.title} • Privacy Score: ${ipSnapshot.privacyReport.scorePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "IP Checker & Privacy Consistency Score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ipSnapshot.hasValidPublicIp) {
                                "${ipSnapshot.primaryPublicIp} • ${ipSnapshot.country}, ${ipSnapshot.region} • Score ${ipSnapshot.privacyReport.scorePercent}% (${ipSnapshot.privacyReport.ratingLabel})"
                            } else {
                                "Inspect Public IPv4/IPv6, Direct vs. Proxy IP, Time Zone & Privacy Score (0–100%)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { onNavigate(ClonexaScreen.MY_IP_ADDRESS) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("open_my_ip_button")
                    ) {
                        Text("IP & Score")
                    }
                }
            }
        }

        // 3. Search Bar
        item {
            OutlinedTextField(
                value = uiState.dashboardSearchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search managed instances or discovered apps…") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (uiState.dashboardSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_search_input")
            )
        }

        // 4. Managed Workspace Instances Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Managed Application Instances",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Organized by account label and Android isolation tier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onNavigate(ClonexaScreen.DISCOVERY) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Instance")
                }
            }
        }

        if (uiState.publicManagedInstances.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_instances_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Active Workspace Instances Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select any compatible installed app below or open App Discovery to configure a secondary account slot, multi-window task, or Work Profile instance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { onNavigate(ClonexaScreen.DISCOVERY) },
                            modifier = Modifier.testTag("empty_state_discover_button")
                        ) {
                            Text("Discover Installed Apps (${uiState.discoveredApps.size})")
                        }
                    }
                }
            }
        } else {
            items(
                items = uiState.publicManagedInstances,
                key = { it.id }
            ) { instance ->
                ManagedInstanceCard(
                    instance = instance,
                    iconDrawable = iconByPackage[instance.packageName],
                    onLaunch = { onLaunchInstance(instance) },
                    onManage = { onManageInstance(instance) }
                )
            }
        }

        // 5. Target Messaging & Social Compatibility Ecosystem (Section 2)
        item {
            Column {
                Text(
                    text = "Target Messaging & Social Compatibility",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time host package verification — never fabricates un-installed apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(
                    items = uiState.targetEcosystemApps,
                    key = { it.packageName }
                ) { target ->
                    val matchingDiscovered = uiState.discoveredApps.find {
                        it.packageName == target.packageName
                    }
                    TargetEcosystemCard(
                        target = target,
                        discoveredApp = matchingDiscovered,
                        onConfigure = {
                            if (matchingDiscovered != null) {
                                onSelectAppForInstance(matchingDiscovered)
                            } else {
                                onNavigate(ClonexaScreen.DISCOVERY)
                            }
                        }
                    )
                }
            }
        }

        // 6. Discovered Installed Applications Quick Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Discovered Installed Applications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Queried live via Android PackageManager & LauncherApps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { onNavigate(ClonexaScreen.DISCOVERY) },
                    modifier = Modifier.testTag("view_all_discovered_button")
                ) {
                    Text("See All (${uiState.discoveredApps.size})")
                }
            }
        }

        items(
            items = uiState.discoveredApps.take(6),
            key = { "dash_disc_${it.packageName}" }
        ) { app ->
            DiscoveredAppCompactRow(
                app = app,
                onSelect = { onSelectAppForInstance(app) }
            )
        }

        // 7. Quick Private Space & Security Shortcut Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(ClonexaScreen.PRIVATE_SPACE) }
                    .testTag("dashboard_private_space_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Private Space & Keystore Security",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.privateManagedInstances.size} hidden instance(s) • Android Keystore AES-256-GCM + Biometric Gate",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Open Private Space",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TargetEcosystemCard(
    target: TargetEcosystemApp,
    discoveredApp: DiscoveredApp?,
    onConfigure: () -> Unit
) {
    val statusColor = if (target.isInstalledOnHost) StatusCompatibleGreen else StatusWarningAmber

    Card(
        modifier = Modifier
            .width(235.dp)
            .clickable(enabled = target.isInstalledOnHost) { onConfigure() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppDrawableIcon(
                    drawable = discoveredApp?.iconDrawable,
                    appName = target.displayName,
                    accentHex = if (target.isInstalledOnHost) "#00E676" else "#94A3B8",
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = target.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = target.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = statusColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = if (target.isInstalledOnHost) {
                        "Installed on Device • Ready"
                    } else {
                        "Not Installed on Host Device"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = target.isolationNotes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (target.isInstalledOnHost) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onConfigure,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clone / Add Slot")
                }
            }
        }
    }
}

@Composable
private fun DiscoveredAppCompactRow(
    app: DiscoveredApp,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("discovered_compact_${app.packageName}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppDrawableIcon(
                    drawable = app.iconDrawable,
                    appName = app.appName,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${app.packageName} • v${app.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CompatibilityStatusChip(status = app.compatibilityStatus)
                }
            }

            if (app.compatibilityStatus.canCreateInstance) {
                IconButton(
                    onClick = onSelect,
                    modifier = Modifier.testTag("quick_add_${app.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ${app.appName} instance",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
