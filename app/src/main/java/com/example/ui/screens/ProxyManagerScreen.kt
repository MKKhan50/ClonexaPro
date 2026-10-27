package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ProxyConfigEntity
import com.example.model.ProxyConnectionState
import com.example.model.ProxyProtocol
import com.example.model.ProxyTestStatus
import com.example.ui.ClonexaUiState
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWorkProfileCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProxyManagerScreen(
    uiState: ClonexaUiState,
    onRefreshIpGeo: () -> Unit,
    onSaveProxy: (
        existingId: Long,
        name: String,
        host: String,
        portStr: String,
        protocol: ProxyProtocol,
        username: String,
        password: String,
        notes: String,
        onValidationFailed: (String) -> Unit,
        onSavedSuccess: () -> Unit
    ) -> Unit,
    onDecryptPasswordForEdit: (ProxyConfigEntity) -> String,
    onSelectProxy: (ProxyConfigEntity) -> Unit,
    onTestProxy: (ProxyConfigEntity) -> Unit,
    onConnectProxy: (ProxyConfigEntity) -> Unit,
    onDisconnectProxy: () -> Unit,
    onDeleteProxy: (ProxyConfigEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val live = uiState.proxyLiveState
    val geo = live.currentIpGeo
    val selectedProxy = uiState.selectedProxy
    val activeProxy = uiState.activeConnectedProxy
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingProxy by remember { mutableStateOf<ProxyConfigEntity?>(null) }

    if (showAddOrEditDialog) {
        val initialPassword = remember(editingProxy) {
            editingProxy?.let { onDecryptPasswordForEdit(it) } ?: ""
        }
        AddOrEditProxyModalDialog(
            existingProxy = editingProxy,
            initialPlainPassword = initialPassword,
            onDismiss = {
                showAddOrEditDialog = false
                editingProxy = null
            },
            onSave = { existingId, name, host, portStr, protocol, user, pass, notes, onError ->
                onSaveProxy(
                    existingId,
                    name,
                    host,
                    portStr,
                    protocol,
                    user,
                    pass,
                    notes,
                    onError
                ) {
                    showAddOrEditDialog = false
                    editingProxy = null
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("proxy_manager_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header & Refresh IP Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Proxy Manager & IP Inspector",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Application-scoped HTTP, HTTPS & SOCKS5 routing + IP geolocation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshIpGeo,
                    modifier = Modifier.testTag("proxy_refresh_ip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh public IP and geolocation",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. Proxy Status & Externally Observed Public IP Dashboard Card
        item {
            val isConnected = activeProxy != null ||
                live.connectionState == ProxyConnectionState.CONNECTED_APP_SCOPED
            val statusColor = when {
                isConnected -> StatusCompatibleGreen
                live.connectionState == ProxyConnectionState.ERROR -> StatusRestrictedRed
                live.isBusy -> StatusWorkProfileCyan
                else -> StatusWarningAmber
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proxy_status_dashboard_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0D1326)
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = statusColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (activeProxy != null) {
                                        "CONNECTED • ${activeProxy.name.uppercase(Locale.getDefault())}"
                                    } else {
                                        live.connectionState.label.uppercase(Locale.getDefault())
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = selectedProxy?.let { "${it.protocol} • :${it.port}" } ?: "NO PROXY SELECTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (live.isBusy) {
                        LinearProgressIndicator(
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    // Public IP & Latency Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OBSERVED PUBLIC IP ADDRESS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = geo.publicIp,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.testTag("proxy_public_ip_value")
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "LATENCY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = if (geo.latencyMs > 0L) "${geo.latencyMs} ms" else "-- ms",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676),
                                modifier = Modifier.testTag("proxy_latency_value")
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Geolocation Telemetry Rows
                    ProxyTelemetryDetailRow(
                        icon = Icons.Default.Public,
                        label = "Country & Region",
                        value = "${geo.country} • ${geo.region} (${geo.city})",
                        testTag = "proxy_country_region_value"
                    )
                    ProxyTelemetryDetailRow(
                        icon = Icons.Default.LocationOn,
                        label = "Approx. Coordinates",
                        value = geo.coordinates,
                        testTag = "proxy_coordinates_value"
                    )
                    ProxyTelemetryDetailRow(
                        icon = Icons.Default.Language,
                        label = "ISP / Network Org",
                        value = geo.ispOrg,
                        testTag = "proxy_isp_value"
                    )
                    ProxyTelemetryDetailRow(
                        icon = Icons.Default.Router,
                        label = "Active Route & DNS",
                        value = "${geo.viaRouteLabel} • ${geo.dnsBehaviorNote}",
                        testTag = "proxy_route_dns_value"
                    )

                    Text(
                        text = live.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }

        // 3. Primary Proxy Action Controls (Connect, Disconnect, Test Proxy, Add Proxy)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeProxy == null) {
                        Button(
                            onClick = { selectedProxy?.let(onConnectProxy) },
                            enabled = selectedProxy != null && !live.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF00262C)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("proxy_connect_button")
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onDisconnectProxy,
                            enabled = !live.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusRestrictedRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("proxy_disconnect_button")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { selectedProxy?.let(onTestProxy) },
                        enabled = selectedProxy != null && !live.isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("proxy_test_button")
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Proxy")
                    }
                }

                Button(
                    onClick = {
                        editingProxy = null
                        showAddOrEditDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("proxy_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Proxy Configuration", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Connection Safety & Geolocation Accuracy Disclosure (Sections 3, 5 & 7)
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Routing Scope & Connection Safety",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Application-Scoped Routing: Connecting a proxy routes ClonexaPro's network traffic (including IP checks and speed tests) through the selected HTTP, HTTPS, or SOCKS5 tunnel. Due to Android UID sandboxing, it does not silently route unrelated third-party device apps unless a Work Profile global proxy policy is active.\n" +
                                "• Privacy & Geolocation Notice: A proxy does not automatically encrypt all device traffic or guarantee complete anonymity. IP geolocation lookups are routed through the active proxy and reflect approximate network-registry location rather than exact street-level coordinates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 5. Saved Proxy Library Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Saved Proxy Library (${uiState.proxyConfigs.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Credentials encrypted via AndroidKeyStore AES-256-GCM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.proxyConfigs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_proxy_library_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Proxies Saved Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap 'Add Proxy Configuration' above to add an HTTP, HTTPS, or SOCKS5 proxy server and verify its external IP and latency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.proxyConfigs,
                key = { "proxy_item_${it.id}" }
            ) { proxy ->
                ProxyLibraryCard(
                    proxy = proxy,
                    isSelected = selectedProxy?.id == proxy.id,
                    isBusy = live.isBusy,
                    formattedTestedDate = proxy.lastTestedAt?.let { dateFormat.format(Date(it)) },
                    onSelect = { onSelectProxy(proxy) },
                    onTest = { onTestProxy(proxy) },
                    onConnectOrDisconnect = {
                        if (proxy.isActiveConnected) onDisconnectProxy() else onConnectProxy(proxy)
                    },
                    onEdit = {
                        editingProxy = proxy
                        showAddOrEditDialog = true
                    },
                    onDelete = { onDeleteProxy(proxy) }
                )
            }
        }
    }
}

@Composable
private fun ProxyTelemetryDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(0.42f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun ProxyLibraryCard(
    proxy: ProxyConfigEntity,
    isSelected: Boolean,
    isBusy: Boolean,
    formattedTestedDate: String?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onConnectOrDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val status = proxy.resolvedTestStatus
    val statusColor = when (status) {
        ProxyTestStatus.WORKING -> StatusCompatibleGreen
        ProxyTestStatus.AUTH_FAILED, ProxyTestStatus.CONNECTION_FAILED -> StatusRestrictedRed
        ProxyTestStatus.TIMEOUT, ProxyTestStatus.UNSUPPORTED -> StatusWarningAmber
        ProxyTestStatus.UNTESTED -> StatusWorkProfileCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("proxy_card_${proxy.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (proxy.isActiveConnected || isSelected) 1.5.dp else 1.dp,
            color = when {
                proxy.isActiveConnected -> StatusCompatibleGreen
                isSelected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = proxy.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (proxy.isActiveConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = StatusCompatibleGreen.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusCompatibleGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${proxy.protocol} • ${proxy.host}:${proxy.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_proxy_${proxy.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit proxy",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_proxy_${proxy.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete proxy",
                            tint = StatusRestrictedRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Status Chip + Auth Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Status: ${status.displayLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (proxy.hasAuthentication) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auth (${proxy.username.ifBlank { "Keystore" }})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Last Test Telemetry
            Text(
                text = buildString {
                    append("Observed IP: ${proxy.lastObservedIp}")
                    if (proxy.lastCountry != "Unknown") {
                        append(" (${proxy.lastCountry}, ${proxy.lastRegion})")
                    }
                    if (proxy.lastLatencyMs != null) {
                        append(" • ${proxy.lastLatencyMs} ms")
                    }
                    if (formattedTestedDate != null) {
                        append(" • Tested $formattedTestedDate")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (proxy.notes.isNotBlank()) {
                Text(
                    text = proxy.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_test_proxy_${proxy.id}")
                ) {
                    Text("Test Proxy")
                }

                Button(
                    onClick = onConnectOrDisconnect,
                    enabled = !isBusy,
                    colors = if (proxy.isActiveConnected) {
                        ButtonDefaults.buttonColors(containerColor = StatusRestrictedRed)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("card_connect_proxy_${proxy.id}")
                ) {
                    Text(if (proxy.isActiveConnected) "Disconnect" else "Connect")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddOrEditProxyModalDialog(
    existingProxy: ProxyConfigEntity?,
    initialPlainPassword: String,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long,
        name: String,
        host: String,
        portStr: String,
        protocol: ProxyProtocol,
        username: String,
        password: String,
        notes: String,
        onError: (String) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf(existingProxy?.name ?: "") }
    var host by remember { mutableStateOf(existingProxy?.host ?: "") }
    var portStr by remember { mutableStateOf(existingProxy?.port?.toString() ?: "8080") }
    var selectedProtocol by remember {
        mutableStateOf(existingProxy?.resolvedProtocol ?: ProxyProtocol.HTTP)
    }
    var username by remember { mutableStateOf(existingProxy?.username ?: "") }
    var password by remember { mutableStateOf(initialPlainPassword) }
    var notes by remember { mutableStateOf(existingProxy?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingProxy == null) "Add Proxy Server" else "Edit Proxy Server",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = StatusRestrictedRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StatusRestrictedRed.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("proxy_validation_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = StatusRestrictedRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = validationError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusRestrictedRed
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("Proxy Name *") },
                    placeholder = { Text("e.g., Frankfurt SOCKS5 Relay") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_name_input")
                )

                Text(
                    text = "Protocol *",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProxyProtocol.entries.forEach { proto ->
                        FilterChip(
                            selected = selectedProtocol == proto,
                            onClick = { selectedProtocol = proto },
                            label = { Text(proto.displayName) },
                            modifier = Modifier.testTag("protocol_chip_${proto.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = selectedProtocol.technicalDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        validationError = null
                    },
                    label = { Text("Server Hostname or IP *") },
                    placeholder = { Text("e.g., proxy.example.com or 198.51.100.24") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_host_input")
                )

                OutlinedTextField(
                    value = portStr,
                    onValueChange = {
                        portStr = it
                        validationError = null
                    },
                    label = { Text("Port (1–65535) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_port_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (Optional • Keystore Encrypted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_password_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existingProxy?.id ?: 0L,
                        name,
                        host,
                        portStr,
                        selectedProtocol,
                        username,
                        password,
                        notes
                    ) { err ->
                        validationError = err
                    }
                },
                modifier = Modifier.testTag("confirm_save_proxy_button")
            ) {
                Text("Save Proxy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
