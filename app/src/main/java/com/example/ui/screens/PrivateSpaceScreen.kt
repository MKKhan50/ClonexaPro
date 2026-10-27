package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ClonexaScreen
import com.example.model.ManagedAppInstanceEntity
import com.example.security.BiometricAuthLayerStatus
import com.example.ui.ClonexaUiState
import com.example.ui.components.ManagedInstanceCard
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed
import com.example.ui.theme.StatusWarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivateSpaceScreen(
    uiState: ClonexaUiState,
    onConfigurePin: (String) -> Unit,
    onUnlockWithPin: (String) -> Unit,
    onUnlockWithBiometrics: (Activity?) -> Unit,
    onLockImmediately: () -> Unit,
    onResetPin: (String) -> Unit,
    onSetSessionTimeout: (Int) -> Unit,
    onNavigateToDiscovery: (ClonexaScreen) -> Unit,
    onLaunchInstance: (ManagedAppInstanceEntity) -> Unit,
    onManageInstance: (ManagedAppInstanceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val iconByPackage = remember(uiState.discoveredApps) {
        uiState.discoveredApps.associate { it.packageName to it.iconDrawable }
    }

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var resetVerifyPinInput by remember { mutableStateOf("") }
    var showResetPinBox by remember { mutableStateOf(false) }

    val isPinConfigured = uiState.userSettings.isPinConfigured
    val isUnlocked = uiState.isPrivateSpaceUnlocked
    val bioState = uiState.biometricSpaceState

    val statusColor = when (bioState.status) {
        BiometricAuthLayerStatus.UNLOCKED -> StatusCompatibleGreen
        BiometricAuthLayerStatus.AUTHENTICATING -> StatusWarningAmber
        BiometricAuthLayerStatus.AUTH_ERROR,
        BiometricAuthLayerStatus.COOLDOWN_LOCKOUT -> StatusRestrictedRed
        BiometricAuthLayerStatus.LOCKED -> MaterialTheme.colorScheme.primary
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("private_space_screen"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Biometric Authentication Layer Gate & Cryptographic Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("biometric_auth_layer_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_private_vault_art),
                                contentDescription = "Private Space Biometric Shield",
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Private Space Biometric Gate",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = bioState.capabilityReport.postureSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.testTag("biometric_hardware_posture_text")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = statusColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = bioState.status.badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("biometric_auth_state_badge")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isUnlocked && bioState.sessionCryptoProofToken.isNotBlank()) {
                                "Session Proof: ${bioState.sessionCryptoProofToken}"
                            } else {
                                bioState.statusMessage
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (isUnlocked) FontFamily.Monospace else FontFamily.Default,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("biometric_session_proof_text")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (isUnlocked) {
                                    onLockImmediately()
                                } else {
                                    onUnlockWithBiometrics(activity)
                                }
                            },
                            modifier = Modifier.testTag("biometric_layer_action_button")
                        ) {
                            Icon(
                                imageVector = if (isUnlocked) Icons.Default.Lock else Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isUnlocked) "Lock Space" else "Biometric Unlock")
                        }
                    }
                }
            }
        }

        // 2. Credential Setup or Unlock Gate when Private Space is Locked
        if (!isUnlocked) {
            if (!isPinConfigured) {
                // State 1: PIN Not Yet Configured (compact layout so all inputs & buttons fit in viewport)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_pin_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Initialize Private Space PIN or Unlock with Biometrics",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                                    label = { Text("4–8 Digit PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_pin_input")
                                )

                                OutlinedTextField(
                                    value = confirmPinInput,
                                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPinInput = it },
                                    label = { Text("Confirm PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_pin_confirm_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (pinInput == confirmPinInput && pinInput.length >= 4) {
                                            onConfigurePin(pinInput)
                                            pinInput = ""
                                            confirmPinInput = ""
                                        }
                                    },
                                    enabled = pinInput.length >= 4 && pinInput == confirmPinInput,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("save_pin_button")
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Encrypt & Activate")
                                }

                                if (uiState.userSettings.biometricUnlockEnabled) {
                                    OutlinedButton(
                                        onClick = { onUnlockWithBiometrics(activity) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("unlock_with_biometric_button")
                                    ) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Biometrics")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // State 2: PIN Configured, Currently Locked
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("unlock_pin_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Unlock Private Space (${uiState.privateManagedInstances.size} Protected Slots)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                                label = { Text("Enter Private Space PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("unlock_pin_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onUnlockWithPin(pinInput)
                                        pinInput = ""
                                    },
                                    enabled = pinInput.length >= 4,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("unlock_with_pin_button")
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Unlock with PIN")
                                }

                                if (uiState.userSettings.biometricUnlockEnabled) {
                                    OutlinedButton(
                                        onClick = { onUnlockWithBiometrics(activity) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("unlock_with_biometric_button")
                                    ) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Biometrics")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Locked Instances Access Guard Notice (hides instance details until unlocked)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("locked_instances_shield_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.privateManagedInstances.size} Isolated App Instance(s) Protected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Access to view, launch, or manage isolated instances is locked. Complete Biometric or Keystore PIN authentication above to unlock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // State 3: Vault Unlocked via Biometric Layer or Verified Keystore PIN
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Private Space Session Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Verified via ${bioState.lastAuthMethodLabel} • Showing ${uiState.privateManagedInstances.size} protected instance(s).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onLockImmediately,
                            modifier = Modifier.testTag("lock_private_space_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Now")
                        }
                    }
                }
            }

            // Session Timeout Controls
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto-Lock Session Timeout",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Immediate", 1 to "1 Min", 5 to "5 Min", 15 to "15 Min").forEach { (mins, label) ->
                                FilterChip(
                                    selected = uiState.userSettings.sessionTimeoutMinutes == mins,
                                    onClick = { onSetSessionTimeout(mins) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("timeout_chip_$mins")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showResetPinBox = !showResetPinBox }
                            ) {
                                Text(if (showResetPinBox) "Cancel PIN Reset" else "Change / Remove PIN")
                            }
                            Button(
                                onClick = { onNavigateToDiscovery(ClonexaScreen.DISCOVERY) }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Private App")
                            }
                        }

                        if (showResetPinBox) {
                            OutlinedTextField(
                                value = resetVerifyPinInput,
                                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) resetVerifyPinInput = it },
                                label = { Text("Verify Current PIN to Clear") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    onResetPin(resetVerifyPinInput)
                                    resetVerifyPinInput = ""
                                    showResetPinBox = false
                                },
                                enabled = resetVerifyPinInput.length >= 4
                            ) {
                                Text("Confirm Clear PIN")
                            }
                        }
                    }
                }
            }

            if (uiState.privateManagedInstances.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Instances in Private Space Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "When creating an instance in App Discovery, toggle 'Lock in Private Space Vault', or open any existing instance on the Workspace screen and tap 'Move to Private Space Vault'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.privateManagedInstances,
                    key = { "private_${it.id}" }
                ) { instance ->
                    ManagedInstanceCard(
                        instance = instance,
                        iconDrawable = iconByPackage[instance.packageName],
                        onLaunch = { onLaunchInstance(instance) },
                        onManage = { onManageInstance(instance) }
                    )
                }
            }
        }
    }
}
