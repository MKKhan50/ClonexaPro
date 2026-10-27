package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.IpLookupMode
import com.example.model.MyIpSnapshot
import com.example.model.PrivacyDiagnosticCheckItem
import com.example.model.PrivacyDiagnosticStatus
import com.example.network.MyIpInspectorEngine
import com.example.ui.ClonexaUiState
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWorkProfileCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyIpScreen(
    uiState: ClonexaUiState,
    onSelectMode: (IpLookupMode) -> Unit,
    onRefreshIp: () -> Unit,
    onOpenDetailsDialog: () -> Unit,
    onDismissDetailsDialog: () -> Unit,
    onIpCopied: (String) -> Unit,
    onNavigateToProxyManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val myIpState = uiState.myIpLiveState
    val snapshot = myIpState.displayedSnapshot
    val privacyReport = snapshot.privacyReport
    val activeProxy = uiState.activeConnectedProxy

    val dateFormatter = remember {
        SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
    }

    if (myIpState.isDetailsDialogOpen) {
        IpTechnicalDetailsDialog(
            snapshot = snapshot,
            formattedTime = snapshot.checkedAtMillis?.let { dateFormatter.format(Date(it)) } ?: "Not checked yet",
            onCopyFullReport = { reportText ->
                copyTextToClipboard(context, "ClonexaPro IP & Privacy Report", reportText)
                onIpCopied("Full IP & Privacy Consistency report")
            },
            onDismiss = onDismissDetailsDialog
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("my_ip_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & Mode Switcher (Direct Connection IP vs Current Proxy IP)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_ip_mode_selector_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "IP Checker & Privacy Consistency",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Real IPv4/IPv6 inspection, Direct vs. Proxy verification & 0–100% Privacy Consistency Score",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = if (activeProxy != null) {
                                StatusCompatibleGreen.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (activeProxy != null) {
                                    "Proxy Active (${activeProxy.protocol})"
                                } else {
                                    "No Proxy Active"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeProxy != null) {
                                    StatusCompatibleGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = myIpState.selectedMode == IpLookupMode.DIRECT_CONNECTION,
                            onClick = { onSelectMode(IpLookupMode.DIRECT_CONNECTION) },
                            label = {
                                Text(
                                    text = IpLookupMode.DIRECT_CONNECTION.title,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Router,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_direct_ip_button")
                        )

                        FilterChip(
                            selected = myIpState.selectedMode == IpLookupMode.CURRENT_PROXY,
                            onClick = { onSelectMode(IpLookupMode.CURRENT_PROXY) },
                            label = {
                                Text(
                                    text = IpLookupMode.CURRENT_PROXY.title,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_proxy_ip_button")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = myIpState.selectedMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Large, Readable Primary IP Hero Card
        item {
            val isProxyMode = myIpState.selectedMode == IpLookupMode.CURRENT_PROXY
            val accentColor = when {
                snapshot.errorMessage != null -> StatusWarningAmber
                isProxyMode && snapshot.isProxyVerified -> StatusCompatibleGreen
                else -> MaterialTheme.colorScheme.primary
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_ip_hero_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
                        )
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isProxyMode) Icons.Default.Shield else Icons.Default.Public,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isProxyMode) {
                                        "OBSERVED CURRENT PROXY IP"
                                    } else {
                                        "OBSERVED DIRECT CONNECTION IP"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.testTag("my_ip_mode_badge")
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = snapshot.ipVersion.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("my_ip_version_badge")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Visible Loading Indicator (Determinate for Robolectric compatibility)
                    AnimatedVisibility(visible = myIpState.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("my_ip_loading_indicator")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isProxyMode) {
                                        "Querying external IP endpoint through active proxy tunnel & scoring privacy consistency…"
                                    } else {
                                        "Detecting Direct Public IPv4, IPv6, geolocation & computing Privacy Consistency Score…"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { 0.65f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Large, Readable Public IP Display
                    Text(
                        text = snapshot.primaryPublicIp,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (snapshot.hasValidPublicIp) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag("my_ip_primary_value")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = snapshot.viaRouteDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("my_ip_route_value")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val lastCheckedFormatted = snapshot.checkedAtMillis?.let {
                        "Last test time: ${dateFormatter.format(Date(it))}" +
                            (snapshot.latencyMs?.let { ms -> " • ${ms} ms" } ?: "")
                    } ?: "Last test time: Not checked yet"

                    Text(
                        text = lastCheckedFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("my_ip_last_checked_text")
                    )

                    // Error or Inactive Proxy Explanation Banner
                    AnimatedVisibility(visible = snapshot.errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .testTag("my_ip_error_banner"),
                            color = StatusRestrictedRed.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusRestrictedRed.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = StatusRestrictedRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = snapshot.errorMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isProxyMode && activeProxy == null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onNavigateToProxyManager,
                                        modifier = Modifier.testTag("my_ip_open_proxy_manager_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Proxy Manager")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row: Copy IP, Refresh / Run Full Check, View Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (snapshot.hasValidPublicIp) {
                                    copyTextToClipboard(
                                        context = context,
                                        label = "Public IP Address",
                                        text = snapshot.primaryPublicIp
                                    )
                                    onIpCopied(snapshot.primaryPublicIp)
                                }
                            },
                            enabled = snapshot.hasValidPublicIp,
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("copy_ip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy IP Address",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy IP")
                        }

                        OutlinedButton(
                            onClick = onRefreshIp,
                            enabled = !myIpState.isLoading,
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("refresh_ip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh / Run Full Check",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Full Check")
                        }

                        OutlinedButton(
                            onClick = onOpenDetailsDialog,
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("view_ip_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "View IP Details",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Details")
                        }
                    }
                }
            }
        }

        // 3. Prominent Privacy Consistency Score (0–100%) & Diagnostic Breakdown Card
        item {
            val score = privacyReport.scorePercent
            val scoreColor = when {
                !snapshot.hasValidPublicIp -> MaterialTheme.colorScheme.onSurfaceVariant
                score >= 85 -> StatusCompatibleGreen
                score >= 65 -> StatusWorkProfileCyan
                score >= 45 -> StatusWarningAmber
                else -> StatusRestrictedRed
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_score_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, scoreColor.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = scoreColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Privacy Consistency Score",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = privacyReport.ratingLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = scoreColor,
                                modifier = Modifier.testTag("privacy_score_rating")
                            )
                        }

                        Surface(
                            color = scoreColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "$score%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = scoreColor,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("privacy_score_value")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { (score / 100f).coerceIn(0f, 1f) },
                        color = scoreColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Direct IP vs Verified Proxy IP & Time Zone / Locale Quick Comparison Grid
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ComparisonPairRow(
                                leftLabel = "Direct Connection IP",
                                leftValue = myIpState.directSnapshot.primaryPublicIp.let {
                                    if (it != "Unavailable") it else privacyReport.directPublicIp
                                },
                                rightLabel = "Verified Proxy IP",
                                rightValue = myIpState.proxySnapshot.primaryPublicIp.let {
                                    if (it != "Unavailable") it else privacyReport.proxyPublicIp
                                },
                                leftTag = "privacy_direct_ip_value",
                                rightTag = "privacy_proxy_ip_value",
                                monospace = true
                            )
                            HorizontalDivider()
                            ComparisonPairRow(
                                leftLabel = "Device Time Zone",
                                leftValue = privacyReport.deviceTimeZone,
                                rightLabel = "IP-Based Time Zone",
                                rightValue = snapshot.timezoneId.takeIf { it != "Unavailable" } ?: privacyReport.ipTimeZone,
                                leftTag = "privacy_device_tz_value",
                                rightTag = "privacy_ip_tz_value",
                                monospace = true
                            )
                            HorizontalDivider()
                            ComparisonPairRow(
                                leftLabel = "Device Language / Locale",
                                leftValue = privacyReport.deviceLocaleTag,
                                rightLabel = "IP Country / Region",
                                rightValue = snapshot.country,
                                leftTag = "privacy_device_locale_value",
                                rightTag = "privacy_ip_country_compare_value",
                                monospace = false
                            )
                            HorizontalDivider()
                            ComparisonPairRow(
                                leftLabel = "DNS Routing Posture",
                                leftValue = privacyReport.dnsRoutingStatus,
                                rightLabel = "WebRTC Exposure",
                                rightValue = privacyReport.webrtcExposureStatus,
                                leftTag = "privacy_dns_status_value",
                                rightTag = "privacy_webrtc_status_value",
                                monospace = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Individual Consistency Checks & Warning Diagnostics",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        privacyReport.checks.forEach { checkItem ->
                            PrivacyCheckItemRow(check = checkItem)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Transparent Scoring Formula Disclosure",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${privacyReport.formulaSummary} ClonexaPro uses this open, deterministic formula based on real detected network parameters and never claims to reproduce Whoer.net's proprietary scoring algorithm.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Dual-Stack IPv4 / IPv6 & Public vs. Local Network Breakdown Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_ip_addresses_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IPv4, IPv6 & Network Interface Scope",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Distinguishes externally observed Public IPs from your device's Private/Local LAN address",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    IpMetricRow(
                        label = "Public IPv4 Address",
                        value = snapshot.publicIpv4,
                        sublabel = MyIpInspectorEngine.classifyIpScope(snapshot.publicIpv4),
                        valueTestTag = "my_ip_ipv4_value",
                        monospace = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    IpMetricRow(
                        label = "Public IPv6 Address",
                        value = snapshot.publicIpv6,
                        sublabel = if (snapshot.publicIpv6 == "Unavailable") {
                            "Not detected on current network path"
                        } else {
                            MyIpInspectorEngine.classifyIpScope(snapshot.publicIpv6)
                        },
                        valueTestTag = "my_ip_ipv6_value",
                        monospace = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    IpMetricRow(
                        label = "Local / Private Interface IP",
                        value = snapshot.localInterfaceIp,
                        sublabel = "${snapshot.localInterfaceScope} (Device LAN/Carrier Interface)",
                        valueTestTag = "my_ip_local_interface_value",
                        monospace = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    IpMetricRow(
                        label = "Detected Connection Type",
                        value = snapshot.connectionTypeLabel,
                        sublabel = "Reported via Android ConnectivityManager",
                        valueTestTag = "my_ip_connection_type_value",
                        monospace = false
                    )
                }
            }
        }

        // 5. Location, Time Zone & ISP Geolocation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_ip_geolocation_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IP Geolocation, Time Zone & ISP Organization",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    IpMetricRow(
                        label = "ISP / Network Organization",
                        value = snapshot.ispOrg,
                        sublabel = if (snapshot.asnNumber != "Unavailable") "ASN: ${snapshot.asnNumber}" else "Autonomous System",
                        valueTestTag = "my_ip_isp_value"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Country",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = snapshot.country,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("my_ip_country_value")
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Region / State",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = snapshot.region,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("my_ip_region_value")
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "City",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = snapshot.city,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("my_ip_city_value")
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    IpMetricRow(
                        label = "IP-Based Time Zone",
                        value = snapshot.timezoneId,
                        sublabel = "Device Time Zone: ${privacyReport.deviceTimeZone}",
                        valueTestTag = "my_ip_timezone_value",
                        monospace = true
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    IpMetricRow(
                        label = "Approximate Coordinates",
                        value = snapshot.approximateCoordinates,
                        sublabel = "Derived from regional ISP registry allocation — not GPS",
                        valueTestTag = "my_ip_coordinates_value",
                        monospace = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Approximate Location Notice: IP-based geolocation reflects the ISP routing hub or registry location associated with the public IP address and may not match your exact physical location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 6. Privacy & Security Disclosure Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("my_ip_privacy_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Privacy & External IP Lookup Disclosure",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• External Service Disclosure: Determining your public IP requires making an HTTPS request to an external IP echo service (ipify.org, ipwho.is, ipapi.co, or 1.1.1.1), which observes the source IP of that request (either your direct ISP address or your active proxy server's exit IP).\n" +
                            "• Zero Persistent Storage or Analytics: ClonexaPro keeps detected IP addresses and privacy consistency reports in transient memory only for display on this screen. IP addresses are never written to local databases, never logged to Android Logcat, and never transmitted to analytics services.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonPairRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftTag: String,
    rightTag: String,
    monospace: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = leftValue,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(leftTag)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = rightValue,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = if (rightValue == "Unavailable") {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.testTag(rightTag)
            )
        }
    }
}

@Composable
private fun PrivacyCheckItemRow(check: PrivacyDiagnosticCheckItem) {
    val (accentColor, statusIcon) = when (check.status) {
        PrivacyDiagnosticStatus.PASS -> StatusCompatibleGreen to Icons.Default.CheckCircle
        PrivacyDiagnosticStatus.WARNING -> StatusWarningAmber to Icons.Default.Warning
        PrivacyDiagnosticStatus.INFO -> StatusWorkProfileCyan to Icons.Default.Info
        PrivacyDiagnosticStatus.UNAVAILABLE -> StatusRestrictedRed to Icons.Default.ErrorOutline
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("privacy_check_item_${check.id}"),
        color = accentColor.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = check.status.label,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = check.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${check.status.label} (${check.earnedPoints}/${check.weightPoints} pts)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = check.observedValue,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = check.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IpMetricRow(
    label: String,
    value: String,
    sublabel: String,
    valueTestTag: String,
    monospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            color = if (value == "Unavailable") {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.testTag(valueTestTag)
        )
    }
}

@Composable
private fun IpTechnicalDetailsDialog(
    snapshot: MyIpSnapshot,
    formattedTime: String,
    onCopyFullReport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reportSummary = remember(snapshot, formattedTime) {
        buildString {
            appendLine("ClonexaPro IP & Privacy Consistency Inspection")
            appendLine("Mode: ${snapshot.mode.title}")
            appendLine("Privacy Consistency Score: ${snapshot.privacyReport.scorePercent}% (${snapshot.privacyReport.ratingLabel})")
            appendLine("Primary Public IP: ${snapshot.primaryPublicIp}")
            appendLine("Public IPv4: ${snapshot.publicIpv4}")
            appendLine("Public IPv6: ${snapshot.publicIpv6}")
            appendLine("IP Version: ${snapshot.ipVersion.label}")
            appendLine("Local Interface IP: ${snapshot.localInterfaceIp} (${snapshot.localInterfaceScope})")
            appendLine("Connection Type: ${snapshot.connectionTypeLabel}")
            appendLine("ISP / Org: ${snapshot.ispOrg}")
            appendLine("ASN: ${snapshot.asnNumber}")
            appendLine("Location (Approx.): ${snapshot.city}, ${snapshot.region}, ${snapshot.country}")
            appendLine("IP Time Zone: ${snapshot.timezoneId} (Device: ${snapshot.privacyReport.deviceTimeZone})")
            appendLine("System Locale: ${snapshot.privacyReport.deviceLocaleTag}")
            appendLine("Coordinates (Approx.): ${snapshot.approximateCoordinates}")
            appendLine("Route: ${snapshot.viaRouteDescription}")
            appendLine("Lookup Service: ${snapshot.lookupServiceName}")
            appendLine("Checked At: $formattedTime")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ip_details_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IP & Privacy Technical Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailKeyValueLine("Inspection Mode", snapshot.mode.title)
                DetailKeyValueLine("Privacy Score", "${snapshot.privacyReport.scorePercent}% • ${snapshot.privacyReport.ratingLabel}")
                DetailKeyValueLine("Primary Public IP", snapshot.primaryPublicIp)
                DetailKeyValueLine("Public IPv4", snapshot.publicIpv4)
                DetailKeyValueLine("Public IPv6", snapshot.publicIpv6)
                DetailKeyValueLine("IP Version", snapshot.ipVersion.label)
                DetailKeyValueLine("Local Interface IP", snapshot.localInterfaceIp)
                DetailKeyValueLine("Local Scope", snapshot.localInterfaceScope)
                DetailKeyValueLine("Connection Type", snapshot.connectionTypeLabel)
                DetailKeyValueLine("ISP / Organization", snapshot.ispOrg)
                DetailKeyValueLine("ASN", snapshot.asnNumber)
                DetailKeyValueLine("Country / Region", "${snapshot.country} • ${snapshot.region}")
                DetailKeyValueLine("City", snapshot.city)
                DetailKeyValueLine("IP Time Zone", snapshot.timezoneId)
                DetailKeyValueLine("Device Time Zone", snapshot.privacyReport.deviceTimeZone)
                DetailKeyValueLine("Device Locale", snapshot.privacyReport.deviceLocaleTag)
                DetailKeyValueLine("Approx. Coordinates", snapshot.approximateCoordinates)
                DetailKeyValueLine("Routing Path", snapshot.viaRouteDescription)
                DetailKeyValueLine("Lookup Endpoint", snapshot.lookupServiceName)
                DetailKeyValueLine("Lookup Latency", snapshot.latencyMs?.let { "$it ms" } ?: "Unavailable")
                DetailKeyValueLine("Timestamp", formattedTime)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCopyFullReport(reportSummary) },
                modifier = Modifier.testTag("copy_ip_full_report_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Report")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_ip_details_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailKeyValueLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

