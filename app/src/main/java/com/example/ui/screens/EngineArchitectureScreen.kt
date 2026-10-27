package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.ClonexaUiState
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWorkProfileCyan

@Composable
fun EngineArchitectureScreen(
    uiState: ClonexaUiState,
    onBuildWorkProfileIntent: () -> Intent,
    onWorkProfileProvisioningResult: (Boolean, String) -> Unit,
    onRefreshEnvironment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val env = uiState.hostEnvironment

    val workProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onWorkProfileProvisioningResult(
                true,
                "Android Managed Work Profile provisioning accepted by OS! Refreshing UserHandles…"
            )
        } else {
            onWorkProfileProvisioningResult(
                false,
                "Managed Work Profile setup did not complete (Result code: ${result.resultCode}). Note: Streaming emulators or devices with existing Device Owner policies may restrict secondary profile creation."
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("architecture_lab_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Isolation Engine & Feasibility Lab",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Live Android OS profile telemetry & technical architecture evaluation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onRefreshEnvironment,
                    modifier = Modifier.testTag("refresh_env_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Re-scan host isolation environment",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 1. Live Host Telemetry Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Host Runtime Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    TelemetryRow("Device Model", "${env?.deviceManufacturer ?: "Android"} ${env?.deviceModel ?: ""}")
                    TelemetryRow("Android SDK Version", "API ${env?.androidSdkInt ?: "-"} (Android ${env?.androidReleaseName ?: "-"})")
                    TelemetryRow(
                        "Detected UserHandles",
                        "${env?.detectedProfileCount ?: 1} profile(s) • Serials: ${env?.profileSerialNumbers?.joinToString(", ") ?: "0"}"
                    )
                    TelemetryRow(
                        "Secondary Work/Clone Profile",
                        if (env?.hasSecondaryUserProfile == true) "ACTIVE (Tier C Ready)" else "Not Provisioned Yet"
                    )
                    TelemetryRow(
                        "Managed Profile Provisioning Allowed",
                        if (env?.isManagedProfileProvisioningAllowed == true) "Supported by OS Policy" else "Restricted on Current Host/Emulator"
                    )
                    TelemetryRow(
                        "Android 14+ Clone Profile API",
                        if (env?.supportsCloneProfileApi == true) "Supported (API 34+)" else "Requires Android 14+"
                    )
                    TelemetryRow(
                        "Hardware AndroidKeyStore",
                        if (env?.hardwareKeystoreAvailable == true) "Operational (AES-256-GCM)" else "Fallback Mode"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            try {
                                val intent = onBuildWorkProfileIntent()
                                workProfileLauncher.launch(intent)
                            } catch (e: Exception) {
                                onWorkProfileProvisioningResult(
                                    false,
                                    "OS could not launch Managed Profile provisioning activity: ${e.localizedMessage ?: "Unsupported on this device"}"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("provision_work_profile_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Android Work Profile Provisioning")
                    }
                }
            }
        }

        // 2. Evaluation of the 4 Isolation Architectures (Sections 3 & 6)
        item {
            Text(
                text = "Technical Feasibility & Architecture Matrix",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ArchitectureEvaluationCard(
                title = "1. Android Work Profile (DevicePolicyManager + LauncherApps)",
                statusBadge = "Implemented • Tier C Official Isolation",
                badgeColor = StatusCompatibleGreen,
                summary = "Uses Android's enterprise-grade Managed Profile architecture (UserHandle 10+) and ClonexaDeviceAdminReceiver. Every app installed inside the Work Profile receives a dedicated Linux UID and isolated /data/user/<profileId>/<pkg> directory.",
                playCompliance = "100% Google Play Policy Compliant • Uses public Android Enterprise APIs."
            )
        }

        item {
            ArchitectureEvaluationCard(
                title = "2. Android 14+ Clone Profile & OEM Dual Apps",
                statusBadge = "Supported via UserManager / LauncherApps",
                badgeColor = StatusWorkProfileCyan,
                summary = "Android 14 (API 34+) introduced USER_TYPE_PROFILE_CLONE for running a second instance of messaging apps. While creating a Clone Profile programmatically requires the signature-level MANAGE_USERS permission (reserved for OEM system settings), ClonexaPro automatically discovers and launches existing Clone Profile instances via UserManager.getUserProfiles() and LauncherApps.",
                playCompliance = "100% Compliant • Respects OS signature permission boundaries."
            )
        }

        item {
            ArchitectureEvaluationCard(
                title = "3. Multi-Document Task Window (FLAG_ACTIVITY_NEW_DOCUMENT)",
                statusBadge = "Implemented • Tier B Host Fallback",
                badgeColor = StatusWorkProfileCyan,
                summary = "When a physical device has not yet provisioned a Work Profile (or inside a single-user cloud emulator), ClonexaPro launches target apps with FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK. Honest Disclosure: This creates a separate recent-tasks window but shares the host app's data directory unless the app natively supports multi-account switching.",
                playCompliance = "100% Compliant • Standard Android Activity Task API."
            )
        }

        item {
            ArchitectureEvaluationCard(
                title = "4. User-Space Virtualization Engine (JNI/C++ Syscall & Binder Hooking)",
                statusBadge = "Platform & Security Boundary Analysis",
                badgeColor = StatusWarningAmber,
                summary = "Legacy apps like Parallel Space hook libc.so openat()/stat() syscalls via NDK C/C++ (plt-hook/seccomp) and proxy ActivityManager/PackageManager Binder IPC to load APKs inside stub processes (:p0..:p99). Why it is NOT falsified here:\n" +
                    "• Build Environment Constraint: Google AI Studio uses a single-module Kotlin/Compose structure without NDK/CMake native C++ compilation or multi-process stub generation.\n" +
                    "• Android 12–16 Security Restrictions: Modern Android enforces non-SDK hidden API blocks, hardware Keystore UID binding, and the REQUIRE_SECURE_ENV manifest flag (which explicitly forbids running inside user-space virtual containers).\n" +
                    "• Google Play Policy: Play Developer Program Policy strictly prohibits Dynamic Code Loading (DCL) of unverified external DEX/SO binaries and bypassing security controls.",
                playCompliance = "Strictly Enforced • ClonexaPro refuses to use undocumented native hooks or bypass REQUIRE_SECURE_ENV."
            )
        }

        // 3. Roadmap for Native Android Engineering (Section 14)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Native Engineering Roadmap (Exported Android Studio Project)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "When exporting this project to local Android Studio for physical-device enterprise deployment:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RoadmapBullet(
                        step = "Phase 1 • Zero-Touch Work Profile Provisioning",
                        detail = "Sign the APK with your production release key and use DevicePolicyManager.enableSystemApp() + CrossProfileApps inside ClonexaDeviceAdminReceiver to automatically mirror selected messaging apps into the managed profile."
                    )
                    RoadmapBullet(
                        step = "Phase 2 • CrossProfileApps & Notification Bridging",
                        detail = "Integrate CrossProfileApps.startMainActivity() and a companion NotificationListenerService (with explicit user consent) to badge active Work Profile instance counts on the ClonexaPro dashboard."
                    )
                    RoadmapBullet(
                        step = "Phase 3 • OEM Clone Profile Deep-Linking",
                        detail = "Add manufacturer-specific settings deep links (Samsung Dual Messenger, Xiaomi Dual Apps, OnePlus Parallel Apps, Pixel Android 14+ App Cloning Settings) so users on physical OEM devices can enable OS-native Clone Profiles in one tap."
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun ArchitectureEvaluationCard(
    title: String,
    statusBadge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    summary: String,
    playCompliance: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = playCompliance,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun RoadmapBullet(step: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = step,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
