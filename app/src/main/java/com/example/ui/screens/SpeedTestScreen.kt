package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SpeedTestPhase
import com.example.model.SpeedTestResultEntity
import com.example.ui.ClonexaUiState
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed
import com.example.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun SpeedTestScreen(
    uiState: ClonexaUiState,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val live = uiState.speedTestLiveState
    val netInfo = uiState.networkConnectionInfo
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("speed_test_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & Active Connection Type Identification
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Internet Speed & Latency Test",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time HTTPS throughput & round-trip diagnostics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshNetwork,
                    modifier = Modifier.testTag("refresh_network_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh network connection status",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 2. Connection Type & Endpoint Banner
        item {
            val connColor = if (netInfo.isConnected) StatusCompatibleGreen else StatusRestrictedRed
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection_type_banner"),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, connColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(connColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (netInfo.connectionTypeLabel.contains("Cellular", ignoreCase = true)) {
                                    Icons.Default.CellTower
                                } else {
                                    Icons.Default.Wifi
                                },
                                contentDescription = "Active Network Transport",
                                tint = connColor
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(connColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = netInfo.connectionTypeLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Endpoint: ${live.serverLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 3. Speedometer Gauge & Start / Cancel Button Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Phase Badge
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = live.phase.label.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Animated Speedometer Arc Gauge
                    SpeedometerArcGauge(
                        currentMbps = live.currentGaugeMbps,
                        phase = live.phase,
                        modifier = Modifier
                            .size(156.dp)
                            .testTag("speed_test_gauge")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (live.isRunning) {
                        LinearProgressIndicator(
                            progress = { live.overallProgress.coerceIn(0f, 1f) },
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("speed_test_progress_bar")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        text = live.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        textAlign = TextAlign.Center
                    )

                    if (live.errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = StatusRestrictedRed.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusRestrictedRed.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusRestrictedRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = live.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFCA5A5)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3B. Prominent Start / Stop Button Item
        item {
            if (!live.isRunning) {
                Button(
                    onClick = onStartTest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF00262C)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_speed_test_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (live.phase == SpeedTestPhase.COMPLETED) "Run Speed Test Again" else "Start Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onCancelTest,
                    border = BorderStroke(1.5.dp, StatusWarningAmber),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusWarningAmber
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("cancel_speed_test_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Stop / Cancel Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4A. Download & Upload Telemetry Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricBox(
                    title = "DOWNLOAD",
                    value = live.downloadMbps?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    unit = "Mbps",
                    icon = Icons.Default.CloudDownload,
                    accentColor = Color(0xFF00E5FF),
                    testTag = "speed_test_download_value",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricBox(
                    title = "UPLOAD",
                    value = live.uploadMbps?.let { String.format(Locale.US, "%.2f", it) } ?: "--",
                    unit = "Mbps",
                    icon = Icons.Default.CloudUpload,
                    accentColor = Color(0xFFB388FF),
                    testTag = "speed_test_upload_value",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 4B. Ping & Jitter Telemetry Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricBox(
                    title = "PING (LATENCY)",
                    value = live.pingMs?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    unit = "ms",
                    icon = Icons.Default.Speed,
                    accentColor = Color(0xFF00E676),
                    testTag = "speed_test_ping_value",
                    modifier = Modifier.weight(1f)
                )
                TelemetryMetricBox(
                    title = "JITTER",
                    value = live.jitterMs?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    unit = "ms",
                    icon = Icons.Default.GraphicEq,
                    accentColor = Color(0xFF38BDF8),
                    testTag = "speed_test_jitter_value",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Measurement Accuracy & Network Variability Disclosure (Requirement 11)
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "About Real Network Measurements: ClonexaPro measures live HTTPS throughput and latency against authorized Cloudflare edge servers. Results may vary depending on Wi-Fi or cellular signal strength, local network congestion, active VPN routing, server distance, and your carrier's data policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Local Test History Section (Requirement 8)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saved Test History (${uiState.speedTestHistory.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.speedTestHistory.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_speed_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All")
                    }
                }
            }
        }

        if (uiState.speedTestHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_speed_history_card"),
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
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Speed Test History Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Completed speed test measurements are automatically saved locally on your device for comparison.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.speedTestHistory,
                key = { "speed_hist_${it.id}" }
            ) { record ->
                SpeedTestHistoryItemCard(
                    record = record,
                    formattedDate = dateFormat.format(Date(record.timestamp)),
                    onDelete = { onDeleteHistoryItem(record.id) }
                )
            }
        }
    }
}

@Composable
private fun SpeedometerArcGauge(
    currentMbps: Double,
    phase: SpeedTestPhase,
    modifier: Modifier = Modifier
) {
    // Non-linear scale up to 500 Mbps for smooth visual feedback across slow and fast connections
    val normalizedTarget = remember(currentMbps) {
        val clamped = currentMbps.coerceIn(0.0, 500.0)
        when {
            clamped <= 10.0 -> (clamped / 10.0) * 0.25
            clamped <= 50.0 -> 0.25 + ((clamped - 10.0) / 40.0) * 0.25
            clamped <= 150.0 -> 0.50 + ((clamped - 50.0) / 100.0) * 0.25
            else -> 0.75 + ((clamped - 150.0) / 350.0) * 0.25
        }.toFloat().coerceIn(0f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = normalizedTarget,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "speedometer_Sweep"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val diameter = min(size.width, size.height) - strokeWidth * 1.4f
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            // Background Track Arc (140 deg to 400 deg -> 260 deg total sweep)
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Cyan-to-Purple Gradient Arc
            val activeSweep = (260f * animatedFraction).coerceAtLeast(2f)
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF00E5FF),
                        Color(0xFF38BDF8),
                        Color(0xFF7C4DFF),
                        Color(0xFF00E5FF)
                    )
                ),
                startAngle = 140f,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Indicator Tip Dot
            val angleRad = Math.toRadians((140.0 + activeSweep.toDouble()))
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val dotCenter = Offset(
                x = center.x + (radius * cos(angleRad)).toFloat(),
                y = center.y + (radius * sin(angleRad)).toFloat()
            )
            drawCircle(
                color = Color.White,
                radius = strokeWidth * 0.36f,
                center = dotCenter
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%.2f", currentMbps),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Mbps",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (phase) {
                    SpeedTestPhase.DOWNLOAD -> "LIVE DOWNLOAD"
                    SpeedTestPhase.UPLOAD -> "LIVE UPLOAD"
                    SpeedTestPhase.PING_AND_JITTER -> "PROBING LATENCY"
                    SpeedTestPhase.COMPLETED -> "FINAL DOWNLOAD"
                    else -> "READY"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun TelemetryMetricBox(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedTestHistoryItemCard(
    record: SpeedTestResultEntity,
    formattedDate: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speed_history_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${record.connectionType} • ${record.statusNote}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$formattedDate • ${record.serverName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete test result",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HistoryMetricColumn("Download", "${String.format(Locale.US, "%.2f", record.downloadMbps)} Mbps")
                HistoryMetricColumn("Upload", "${String.format(Locale.US, "%.2f", record.uploadMbps)} Mbps")
                HistoryMetricColumn("Ping", "${String.format(Locale.US, "%.0f", record.pingMs)} ms")
                HistoryMetricColumn("Jitter", "${String.format(Locale.US, "%.1f", record.jitterMs)} ms")
            }
        }
    }
}

@Composable
private fun HistoryMetricColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
