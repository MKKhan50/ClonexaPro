package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.R
import com.example.model.AppLockCredentialType
import com.example.model.MonetizationTier
import com.example.model.ThemePreference
import com.example.ui.ClonexaUiState
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed
import com.example.ui.theme.StatusWarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LegalDocType(val title: String, val body: String) {
    PRIVACY_POLICY(
        title = "ClonexaPro Privacy Policy",
        body = """
            1. Zero Third-Party Account Credential Collection:
            ClonexaPro never intercepts, requests, stores, or transmits passwords, OAuth tokens, or private messages belonging to WhatsApp, Facebook, Instagram, TikTok, Telegram, or any other installed application.

            2. Public IP & Geolocation Check Requests:
            When you open or refresh the IP Checker & Privacy Consistency screen or test a proxy in Proxy Manager, ClonexaPro sends an HTTPS request to reputable external IP-lookup endpoints (ipwho.is, api4.ipify.org, api6.ipify.org, ipapi.co, or 1.1.1.1/cdn-cgi/trace). Those external services observe the source IP of the request (either your direct ISP address or your active proxy exit IP). Detected IP addresses and Privacy Consistency Score reports are held in transient RAM only and are never written to disk or sent to analytics servers.

            3. Proxy Configuration & Encrypted Storage:
            User-configured proxy servers (HTTP, HTTPS, SOCKS5) are stored locally in the on-device Room database. Optional proxy authentication passwords are encrypted prior to storage using a 256-bit AES-GCM key managed by the Android Keystore and are never logged.

            4. Internet Speed Test Endpoints:
            When you initiate an Internet Speed Test, ClonexaPro exchanges measurement payloads with Cloudflare Speed Test HTTPS endpoints (speed.cloudflare.com) to measure real download throughput, upload throughput, ping, and jitter. Summary speed test results are stored locally on your device and can be deleted at any time.

            5. Android Keystore App Lock & Private Space Security:
            When you configure an Application Entry Lock (PIN or Password) or Private Space Vault PIN, ClonexaPro computes a salted SHA-256 cryptographic digest and encrypts the token with an AndroidKeyStore AES-256-GCM master key. Plaintext PINs, passwords, and recovery codes are never stored on disk.

            6. Android Permissions Used:
            • INTERNET & ACCESS_NETWORK_STATE: Required for user-initiated IP checks, proxy verification, and speed tests.
            • USE_BIOMETRIC: Used only when you opt into BiometricPrompt unlock for App Lock or Private Space.
            • POST_NOTIFICATIONS & VIBRATE: Used for optional instance launch status notifications.
            • BIND_DEVICE_ADMIN: Used only if you explicitly trigger Android Managed Work Profile provisioning.
            • Package Visibility (<queries>): Used strictly on-device to discover launcher-capable apps and display compatibility status. Installed app lists never leave your device.

            7. Clearing Local Data & Disabling Features:
            You can disable App Lock, delete saved proxies, clear speed test history, delete managed workspace instances, or clear security audit logs directly within the app, or clear all app storage via Android System Settings.
        """.trimIndent()
    ),
    TERMS_OF_USE(
        title = "ClonexaPro Terms of Use (Draft for Legal Review)",
        body = """
            NOTICE: This Terms of Use document is provided as an operational draft template and should be reviewed by qualified legal counsel prior to commercial distribution.

            1. Authorized Platform Isolation Mechanisms:
            ClonexaPro operates exclusively using standard Android platform APIs (DevicePolicyManager Managed Work Profiles, UserManager, LauncherApps, and multi-document Activity launch intents). It does not root devices, patch third-party APKs, or bypass Android OS security controls.

            2. Respect for Application Security Boundaries:
            ClonexaPro strictly honors the Android REQUIRE_SECURE_ENV manifest flag and hardware attestation policies. Applications that prohibit virtualized or container execution are blocked from non-native loading.

            3. Proxy, IP Checker & Speed Test Usage:
            You are solely responsible for ensuring that any proxy servers you configure in Proxy Manager are authorized for your use. Privacy Consistency Scores (0–100%) are informational diagnostics calculated from ClonexaPro's documented consistency formula and do not guarantee anonymity.

            4. Third-Party Terms of Service:
            Users are responsible for complying with the Terms of Service of third-party applications (such as WhatsApp, Instagram, Facebook, TikTok, and Telegram) when operating multiple accounts.

            5. App Lock & Credential Recovery Limitation:
            Because App Lock PINs and Passwords are cryptographically hashed and protected via the Android Keystore with zero server-side backup, you must retain your generated Recovery Code (CLX-XXXX-XXXX) to reset App Lock if you forget your credential.
        """.trimIndent()
    ),
    OPEN_SOURCE_LICENSES(
        title = "Open-Source Notices & Third-Party Acknowledgments",
        body = """
            ClonexaPro is built with modern Android Jetpack and open-source components licensed under the Apache License, Version 2.0:

            • AndroidX Core KTX, Activity Compose & Lifecycle ViewModel
            Copyright © The Android Open Source Project — Licensed under Apache License 2.0.

            • Jetpack Compose UI & Material Design 3
            Copyright © The Android Open Source Project — Licensed under Apache License 2.0.

            • AndroidX Room Persistence Library & Jetpack DataStore Preferences
            Copyright © The Android Open Source Project — Licensed under Apache License 2.0.

            • Kotlin Standard Library & Kotlinx Coroutines
            Copyright © JetBrains s.r.o. and Kotlin Programming Language contributors — Licensed under Apache License 2.0.

            External Network Diagnostic Endpoints (Contacted Only on User Action):
            • Cloudflare Speed Test (speed.cloudflare.com) & Trace (1.1.1.1)
            • ipify Public IPv4/IPv6 API (ipify.org)
            • ipwho.is & ipapi.co IP Geolocation Lookup Services
        """.trimIndent()
    ),
    HELP_AND_SUPPORT(
        title = "Help, Troubleshooting & Device Guide",
        body = """
            • How do I get complete data separation for a second WhatsApp or Telegram account?
            Complete data separation on Android requires a secondary Android UserHandle (Tier C). Open the 'Engine Lab' tab and tap 'Trigger Android Work Profile Provisioning' on a supported physical device, or enable Android 14+ App Cloning in your device Settings. Once a secondary profile exists, ClonexaPro automatically detects it via UserManager and launches the isolated copy via LauncherApps.

            • How does the Privacy Consistency Score on the IP Checker screen work?
            The IP Checker screen evaluates 5 transparent consistency checks (100 points total): Direct vs. Proxy IP separation (30 pts), Device Time Zone vs. IP Time Zone alignment (25 pts), System Language/Locale vs. IP Country consistency (15 pts), DNS routing posture (15 pts), and WebRTC/Local RFC 1918 interface isolation (15 pts).

            • How do I recover access if I forget my App Lock PIN or Password?
            When you configure App Lock, ClonexaPro generates a one-time Recovery Code (CLX-XXXX-XXXX). Tap 'Forgot PIN/Password? Use Recovery Code' on the unlock screen and enter that code to reset App Lock safely.
        """.trimIndent()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndLegalScreen(
    uiState: ClonexaUiState,
    onSetTheme: (ThemePreference) -> Unit,
    onSetBiometricEnabled: (Boolean) -> Unit,
    onSetNotificationsEnabled: (Boolean) -> Unit,
    onSetStrictSecurityCheck: (Boolean) -> Unit,
    onSetMonetizationTier: (MonetizationTier) -> Unit,
    onConfigureAppLock: (
        credentialType: AppLockCredentialType,
        newSecret: String,
        confirmSecret: String,
        currentSecretIfChanging: String
    ) -> Unit = { _, _, _, _ -> },
    onToggleAppLockEnabled: (enabled: Boolean, verificationSecret: String) -> Unit = { _, _ -> },
    onLockAppNow: () -> Unit = {},
    onAcknowledgeRecoveryCode: () -> Unit = {},
    onSavePublicPrivacyUrl: (String) -> Unit = {},
    onClearAuditLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.userSettings
    var activeLegalModal by remember { mutableStateOf<LegalDocType?>(null) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }

    // App Lock form state
    var selectedLockType by remember(settings.appLockCredentialType) {
        mutableStateOf(settings.appLockCredentialType)
    }
    var currentLockSecretInput by remember { mutableStateOf("") }
    var newLockSecretInput by remember { mutableStateOf("") }
    var confirmLockSecretInput by remember { mutableStateOf("") }
    var disableVerificationSecretInput by remember { mutableStateOf("") }
    var showDisableLockDialog by remember { mutableStateOf(false) }

    // Public Privacy Policy URL state for Google Play Console compliance
    var privacyUrlInput by remember(settings.publicPrivacyPolicyUrl) {
        mutableStateOf(settings.publicPrivacyPolicyUrl)
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        onSetNotificationsEnabled(granted)
    }

    if (showDisableLockDialog) {
        AlertDialog(
            onDismissRequest = { showDisableLockDialog = false },
            modifier = Modifier.testTag("disable_app_lock_dialog"),
            title = { Text("Disable Application Lock") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your current App Lock ${settings.appLockCredentialType.label} to confirm disabling Application Entry Lock.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = disableVerificationSecretInput,
                        onValueChange = { disableVerificationSecretInput = it },
                        label = { Text("Current ${settings.appLockCredentialType.label}") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("disable_app_lock_verify_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleAppLockEnabled(false, disableVerificationSecretInput)
                        disableVerificationSecretInput = ""
                        showDisableLockDialog = false
                    },
                    enabled = disableVerificationSecretInput.isNotBlank(),
                    modifier = Modifier.testTag("confirm_disable_app_lock_button")
                ) {
                    Text("Verify & Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableLockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (activeLegalModal != null) {
        val doc = activeLegalModal!!
        AlertDialog(
            onDismissRequest = { activeLegalModal = null },
            modifier = Modifier.testTag("legal_document_modal"),
            title = { Text(doc.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = doc.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { activeLegalModal = null },
                    modifier = Modifier.testTag("close_legal_modal_button")
                ) {
                    Text("Close")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings, Security & About",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ClonexaPro v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Official About ClonexaPro Brand Card (Sections 1 & 4)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_clonexapro_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    )
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.clonexapro_official_logo),
                            contentDescription = "ClonexaPro Official Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .testTag("about_official_logo")
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ClonexaPro",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.testTag("about_app_name")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "v${BuildConfig.VERSION_NAME}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("about_version_text")
                                    )
                                }
                            }
                            Text(
                                text = "Application ID: ${BuildConfig.APPLICATION_ID} • Version Code ${BuildConfig.VERSION_CODE}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Multi-Account Workspace, Android Work/Clone Profile Isolation, Private Vault, Proxy Manager, Speed Test & IP Privacy Consistency Inspector.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Developer & Publisher Configuration",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Package Namespace: ${BuildConfig.APPLICATION_ID} • Signed Android Release/Debug Distribution Build. Publisher contact details and hosted Privacy Policy URL should be configured below to match your Google Play Console listing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Application Entry Lock — PIN or Password (Section 3)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_lock_settings_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (settings.isAppLockActive) {
                        StatusCompatibleGreen.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "App Lock — PIN or Password",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Protects entry to ClonexaPro with Android Keystore AES-256-GCM + salted SHA-256",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (settings.isAppLockActive) {
                                StatusCompatibleGreen.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (settings.isAppLockActive) {
                                    "Enabled (${settings.appLockCredentialType.label})"
                                } else {
                                    "Disabled"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (settings.isAppLockActive) {
                                    StatusCompatibleGreen
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("app_lock_status_badge")
                            )
                        }
                    }

                    // Active App Lock Quick Actions (Lock App Now / Disable / Re-Enable)
                    if (settings.isAppLockConfigured) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (settings.isAppLockActive) {
                                Button(
                                    onClick = onLockAppNow,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("lock_app_now_button")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lock App Now")
                                }
                                OutlinedButton(
                                    onClick = { showDisableLockDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("disable_app_lock_button")
                                ) {
                                    Text("Disable App Lock")
                                }
                            } else {
                                Button(
                                    onClick = { onToggleAppLockEnabled(true, "") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reenable_app_lock_button")
                                ) {
                                    Text("Re-Enable Configured App Lock")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2B. App Lock Credential Setup / Change Form (PIN or Password)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_lock_setup_form_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (settings.isAppLockConfigured) {
                            "Change App Lock Credential Mode & Secret"
                        } else {
                            "Choose App Lock Mode (PIN or Password)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = selectedLockType == AppLockCredentialType.PIN,
                            onClick = {
                                selectedLockType = AppLockCredentialType.PIN
                                newLockSecretInput = ""
                                confirmLockSecretInput = ""
                            },
                            label = { Text(AppLockCredentialType.PIN.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_lock_mode_pin_chip")
                        )
                        FilterChip(
                            selected = selectedLockType == AppLockCredentialType.PASSWORD,
                            onClick = {
                                selectedLockType = AppLockCredentialType.PASSWORD
                                newLockSecretInput = ""
                                confirmLockSecretInput = ""
                            },
                            label = { Text(AppLockCredentialType.PASSWORD.label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_lock_mode_password_chip")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (settings.isAppLockConfigured) {
                            OutlinedTextField(
                                value = currentLockSecretInput,
                                onValueChange = { currentLockSecretInput = it.take(64) },
                                label = { Text("Current") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("app_lock_current_secret_input")
                            )
                        }

                        OutlinedTextField(
                            value = newLockSecretInput,
                            onValueChange = {
                                newLockSecretInput = if (selectedLockType == AppLockCredentialType.PIN) {
                                    it.filter { ch -> ch.isDigit() }.take(8)
                                } else {
                                    it.take(64)
                                }
                            },
                            label = {
                                Text(
                                    if (settings.isAppLockConfigured) {
                                        "New ${selectedLockType.label}"
                                    } else {
                                        "Set ${selectedLockType.label}"
                                    }
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (selectedLockType == AppLockCredentialType.PIN) {
                                    KeyboardType.NumberPassword
                                } else {
                                    KeyboardType.Password
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_lock_new_secret_input")
                        )

                        OutlinedTextField(
                            value = confirmLockSecretInput,
                            onValueChange = {
                                confirmLockSecretInput = if (selectedLockType == AppLockCredentialType.PIN) {
                                    it.filter { ch -> ch.isDigit() }.take(8)
                                } else {
                                    it.take(64)
                                }
                            },
                            label = { Text("Confirm ${selectedLockType.label}") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (selectedLockType == AppLockCredentialType.PIN) {
                                    KeyboardType.NumberPassword
                                } else {
                                    KeyboardType.Password
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("app_lock_confirm_secret_input")
                        )
                    }

                    Button(
                        onClick = {
                            onConfigureAppLock(
                                selectedLockType,
                                newLockSecretInput,
                                confirmLockSecretInput,
                                currentLockSecretInput
                            )
                            currentLockSecretInput = ""
                            newLockSecretInput = ""
                            confirmLockSecretInput = ""
                        },
                        enabled = newLockSecretInput.isNotBlank() && confirmLockSecretInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_app_lock_button")
                    ) {
                        Text(
                            if (settings.isAppLockConfigured) {
                                "Update App Lock"
                            } else {
                                "Enable App Lock"
                            }
                        )
                    }
                }
            }
        }

        // 2C. Recovery Code Banner & Recovery Limitations Disclosure
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uiState.newlyGeneratedAppLockRecoveryCode != null) {
                    val recCode = uiState.newlyGeneratedAppLockRecoveryCode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("app_lock_recovery_code_banner"),
                        color = StatusCompatibleGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, StatusCompatibleGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = StatusCompatibleGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Your One-Time Recovery Code",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusCompatibleGreen
                                )
                            }
                            Text(
                                text = recCode,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("app_lock_generated_recovery_code_value")
                            )
                            Text(
                                text = "Store this code in a safe place outside ClonexaPro. Because your PIN/Password is never stored in plain text and has no universal backdoor, this Recovery Code is the only way to reset App Lock without clearing app data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("ClonexaPro Recovery Code", recCode))
                                    },
                                    modifier = Modifier.testTag("copy_recovery_code_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Code")
                                }
                                Button(
                                    onClick = onAcknowledgeRecoveryCode,
                                    modifier = Modifier.testTag("acknowledge_recovery_code_button")
                                ) {
                                    Text("I Saved This Code")
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = StatusWarningAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusWarningAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = StatusWarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recovery Limitation Notice: ClonexaPro never stores your PIN or Password in plain text and never includes a universal backdoor code. When you enable or change App Lock, a one-time Recovery Code (CLX-XXXX-XXXX) will be generated so you can safely reset App Lock if you forget your credential.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Appearance & Theme Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Theme & Visual Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = uiState.userSettings.themePreference == pref,
                                onClick = { onSetTheme(pref) },
                                label = { Text(pref.label) },
                                modifier = Modifier.testTag("theme_chip_${pref.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // 4. Security & Android Boundary Controls
        item {
            val biometricHardwareSupported = uiState.hostEnvironment?.deviceCredentialOrBiometricEnrolled ?: true
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Security & Policy Enforcement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SettingToggleRow(
                        title = "Enforce REQUIRE_SECURE_ENV Boundary",
                        subtitle = "Strictly block simulated/virtualized loading of apps that require hardware attestation or secure environments.",
                        checked = uiState.userSettings.strictSecurityBoundaryCheck,
                        onCheckedChange = onSetStrictSecurityCheck,
                        testTag = "strict_security_switch"
                    )

                    HorizontalDivider()

                    SettingToggleRow(
                        title = "Biometric / Device Credential Unlock",
                        subtitle = if (biometricHardwareSupported) {
                            "Allow Android BiometricPrompt authentication for App Lock and Private Space Vault."
                        } else {
                            "Biometric hardware or screen lock credential not detected on this device; PIN/Password will be required."
                        },
                        checked = uiState.userSettings.biometricUnlockEnabled,
                        onCheckedChange = onSetBiometricEnabled,
                        testTag = "biometric_unlock_switch"
                    )
                }
            }
        }

        // 5. Notifications & Runtime Permissions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notifications & Legitimate Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SettingToggleRow(
                        title = "Instance Launch Status Notifications",
                        subtitle = "Post a low-priority status notification showing the active Isolation Tier when launching a workspace slot.",
                        checked = uiState.userSettings.notificationsEnabled && hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSetNotificationsEnabled(enabled)
                            }
                        },
                        testTag = "notifications_switch"
                    )

                    Text(
                        text = "Permission Transparency: ClonexaPro only declares INTERNET, ACCESS_NETWORK_STATE, USE_BIOMETRIC, POST_NOTIFICATIONS, VIBRATE, BIND_DEVICE_ADMIN (for optional Work Profile setup), and <queries> for launcher discovery. It never requests invasive contacts, SMS, call log, or storage permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Optional Monetization Architecture Readiness
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Workspace Tier Architecture (Ad-Free)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Prepared for optional Google Play Billing integration without third-party ad SDK bloat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MonetizationTier.entries.forEach { tier ->
                        val selected = uiState.userSettings.monetizationTier == tier
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSetMonetizationTier(tier) }
                                .testTag("monetization_tier_${tier.name.lowercase()}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = tier.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tier.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Legal, Privacy Policy, Terms, Open-Source Notices & Google Play URL (Section 4)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Privacy Policy, Terms & Open-Source Notices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { activeLegalModal = LegalDocType.PRIVACY_POLICY },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_privacy_policy_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy Policy & Data Handling")
                    }
                    OutlinedButton(
                        onClick = { activeLegalModal = LegalDocType.TERMS_OF_USE },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_terms_button")
                    ) {
                        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Terms of Use (Draft for Legal Review)")
                    }
                    OutlinedButton(
                        onClick = { activeLegalModal = LegalDocType.OPEN_SOURCE_LICENSES },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_licenses_button")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open-Source Licenses & Acknowledgments")
                    }
                    OutlinedButton(
                        onClick = { activeLegalModal = LegalDocType.HELP_AND_SUPPORT },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_help_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Help, FAQ & Isolation Guide")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Public Privacy Policy URL (Google Play Console Compliance)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Configure your hosted HTTPS Privacy Policy URL for Google Play Store distribution (no fake placeholder domain is pre-filled).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = privacyUrlInput,
                            onValueChange = { privacyUrlInput = it },
                            label = { Text("Hosted HTTPS Privacy Policy URL") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("public_privacy_policy_url_input")
                        )
                        Button(
                            onClick = { onSavePublicPrivacyUrl(privacyUrlInput) },
                            modifier = Modifier.testTag("save_privacy_url_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        // 8. Local Security Audit Log
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
                        text = "Security & Operation Audit Log (${uiState.auditLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (uiState.auditLogs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAuditLogs,
                        modifier = Modifier.testTag("clear_audit_logs_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear")
                    }
                }
            }
        }

        if (uiState.auditLogs.isEmpty()) {
            item {
                Text(
                    text = "No workspace operations logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(
                items = uiState.auditLogs.take(15),
                key = { "audit_${it.id}" }
            ) { log ->
                val accent = if (log.isSuccess) StatusCompatibleGreen else StatusRestrictedRed
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = log.eventType,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = log.summary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

