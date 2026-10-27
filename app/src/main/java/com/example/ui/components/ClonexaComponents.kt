package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppLockCredentialType
import com.example.model.CompatibilityStatus
import com.example.model.DiscoveredApp
import com.example.model.IsolationTier
import com.example.model.ManagedAppInstanceEntity
import com.example.ui.theme.StatusCompatibleGreen
import com.example.ui.theme.StatusRestrictedRed
import com.example.ui.theme.StatusWarningAmber
import com.example.ui.theme.StatusWorkProfileCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Drawable.toComposeImageBitmap(sizePx: Int = 128): ImageBitmap? {
    return try {
        if (this is BitmapDrawable && this.bitmap != null) {
            return this.bitmap.asImageBitmap()
        }
        val width = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(sizePx) else sizePx
        val height = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(sizePx) else sizePx
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF00E5FF)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun AppDrawableIcon(
    drawable: Drawable?,
    appName: String,
    accentHex: String = "#00E5FF",
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(drawable) { drawable?.toComposeImageBitmap() }
    val accentColor = remember(accentHex) { parseHexColor(accentHex) }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "$appName icon",
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
        )
    } else {
        val initials = remember(appName) {
            appName.trim().split(" ")
                .filter { it.isNotEmpty() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifEmpty { "AP" }
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
                        )
                    )
                )
                .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

@Composable
fun ClonexaBrandTopBar(
    hasSecondaryProfile: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenPrivateSpace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Official ClonexaPro Brand Logo
                Image(
                    painter = painterResource(id = R.drawable.clonexapro_official_logo),
                    contentDescription = "ClonexaPro Official Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                            ),
                            shape = RoundedCornerShape(13.dp)
                        )
                        .testTag("clonexapro_official_brand_logo")
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Clonexa",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = if (hasSecondaryProfile) {
                            "Tier C Profile Sandbox Active"
                        } else {
                            "Multi-Account & Isolation Engine"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenPrivateSpace,
                    modifier = Modifier.testTag("top_private_space_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Open Private Space Vault",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Light/Dark Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IsolationTierBadge(
    tier: IsolationTier,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (tier) {
        IsolationTier.ISOLATED_PROFILE_ACTIVE ->
            StatusCompatibleGreen.copy(alpha = 0.16f) to StatusCompatibleGreen
        IsolationTier.MANAGED_TASK_LAUNCH ->
            StatusWorkProfileCyan.copy(alpha = 0.16f) to StatusWorkProfileCyan
        IsolationTier.UI_MANAGED_ENTRY ->
            StatusWarningAmber.copy(alpha = 0.16f) to StatusWarningAmber
    }

    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = tier.badgeTitle,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CompatibilityStatusChip(
    status: CompatibilityStatus,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (status) {
        CompatibilityStatus.COMPATIBLE_ISOLATED ->
            StatusCompatibleGreen to Icons.Default.CheckCircle
        CompatibilityStatus.COMPATIBLE_WORK_PROFILE_ONLY ->
            StatusWorkProfileCyan to Icons.Default.Info
        CompatibilityStatus.RESTRICTED_SECURE_ENV ->
            StatusRestrictedRed to Icons.Default.Security
        CompatibilityStatus.INCOMPATIBLE_SYSTEM_CORE ->
            StatusWarningAmber to Icons.Default.Warning
    }

    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.label,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun ManagedInstanceCard(
    instance: ManagedAppInstanceEntity,
    iconDrawable: Drawable?,
    onLaunch: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = remember(instance.badgeColorHex) { parseHexColor(instance.badgeColorHex) }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onManage() }
            .testTag("managed_instance_card_${instance.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    AppDrawableIcon(
                        drawable = iconDrawable,
                        appName = instance.appName,
                        accentHex = instance.badgeColorHex,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = instance.accountLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${instance.appName} • ${instance.packageName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onManage,
                    modifier = Modifier.testTag("instance_options_${instance.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Manage instance options"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IsolationTierBadge(tier = instance.resolvedTier)
                if (instance.isInPrivateSpace) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Private Space Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Private Vault",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (instance.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = instance.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val launchMeta = if (instance.lastLaunchedAt != null) {
                    "Launches: ${instance.launchCount} • Last: ${dateFormat.format(Date(instance.lastLaunchedAt))}"
                } else {
                    "Ready to launch • 0 launches"
                }
                Text(
                    text = launchMeta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onLaunch,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("launch_instance_button_${instance.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Launch ${instance.accountLabel}",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Launch")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateInstanceModalDialog(
    app: DiscoveredApp,
    hasSecondaryProfile: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        accountLabel: String,
        accountCategory: String,
        badgeColorHex: String,
        preferredTier: IsolationTier,
        placeInPrivateSpace: Boolean,
        notes: String
    ) -> Unit
) {
    var accountLabel by remember { mutableStateOf("Work Account #2") }
    var notes by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#00E5FF") }
    var selectedTier by remember {
        mutableStateOf(
            if (app.isInstalledInSecondaryProfile || hasSecondaryProfile) {
                IsolationTier.ISOLATED_PROFILE_ACTIVE
            } else {
                IsolationTier.MANAGED_TASK_LAUNCH
            }
        )
    }
    var lockInPrivateSpace by remember { mutableStateOf(false) }

    val presetLabels = listOf("Work Account #2", "Business Line", "Personal Secondary", "Creator Profile")
    val colorOptions = listOf("#00E5FF", "#7C4DFF", "#00E676", "#FFB300", "#FF4081")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppDrawableIcon(drawable = app.iconDrawable, appName = app.appName, size = 40.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Configure App Instance",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${app.appName} (${app.packageName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompatibilityStatusChip(status = app.compatibilityStatus)

                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = { accountLabel = it },
                    label = { Text("Account / Instance Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instance_label_input")
                )

                Text(
                    text = "Quick Tag Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetLabels.forEach { preset ->
                        FilterChip(
                            selected = accountLabel == preset,
                            onClick = { accountLabel = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(
                    text = "Badge Color Accent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    colorOptions.forEach { hex ->
                        val c = parseHexColor(hex)
                        val isSelected = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }

                Text(
                    text = "Isolation Execution Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IsolationTier.entries.forEach { tier ->
                    val selected = selectedTier == tier
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTier = tier },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = tier.badgeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tier.shortSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lock in Private Space Vault",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Hide from main workspace until unlocked with PIN or Biometrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = lockInPrivateSpace,
                        onCheckedChange = { lockInPrivateSpace = it },
                        modifier = Modifier.testTag("private_space_toggle_switch")
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Workspace Notes (No Passwords)") },
                    placeholder = { Text("e.g., Region SIM #2, Support Account") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("instance_notes_input")
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Honest Architecture Disclosure: Creating a local workspace record does not automatically register a third-party account inside ${app.appName} and never collects third-party credentials. Full OS data separation requires Tier C Work/Clone Profile mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        accountLabel,
                        app.categoryLabel,
                        selectedColorHex,
                        selectedTier,
                        lockInPrivateSpace,
                        notes
                    )
                },
                modifier = Modifier.testTag("confirm_create_instance_button")
            ) {
                Text("Add to Workspace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InstanceDetailsSheetDialog(
    instance: ManagedAppInstanceEntity,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onTogglePrivateSpace: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.accountLabel,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${instance.appName} (${instance.packageName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close details")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IsolationTierBadge(tier = instance.resolvedTier)
                Text(
                    text = instance.resolvedTier.technicalGuarantee,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Profile Serial: #${instance.userProfileSerial} • Category: ${instance.accountCategory}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Total Launches: ${instance.launchCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onTogglePrivateSpace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toggle_private_space_button")
                ) {
                    Icon(
                        imageVector = if (instance.isInPrivateSpace) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (instance.isInPrivateSpace) {
                            "Move Out of Private Space"
                        } else {
                            "Move to Private Space Vault"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusRestrictedRed
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_instance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove Instance Record")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onLaunch()
                    onDismiss()
                },
                modifier = Modifier.testTag("modal_launch_instance_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Launch Now")
            }
        }
    )
}

/**
 * Full-screen Application Entry Lock Gate protecting access to ClonexaPro when App Lock is enabled.
 * Features the official ClonexaPro logo, PIN or Password verification, optional Biometric unlock,
 * and a Recovery Code reset flow.
 */
@Composable
fun AppLockGateScreen(
    credentialType: AppLockCredentialType,
    biometricSupportedAndEnabled: Boolean,
    errorMessage: String?,
    onUnlockWithCredential: (String) -> Unit,
    onUnlockWithBiometrics: () -> Unit,
    onRecoverWithCode: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var secretInput by remember { mutableStateOf("") }
    var recoveryMode by remember { mutableStateOf(false) }
    var recoveryCodeInput by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_lock_gate_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Image(
                painter = painterResource(id = R.drawable.clonexapro_official_logo),
                contentDescription = "ClonexaPro Official Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .testTag("app_lock_logo")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Clonexa",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Application Entry Protected • Android Keystore AES-256-GCM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!recoveryMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enter App Lock ${credentialType.label}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Enter your configured ${credentialType.label} to unlock your ClonexaPro workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = secretInput,
                            onValueChange = {
                                onClearError()
                                secretInput = if (credentialType == AppLockCredentialType.PIN) {
                                    it.filter { ch -> ch.isDigit() }.take(8)
                                } else {
                                    it.take(64)
                                }
                            },
                            label = { Text(credentialType.label) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (credentialType == AppLockCredentialType.PIN) {
                                    KeyboardType.NumberPassword
                                } else {
                                    KeyboardType.Password
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_gate_input")
                        )

                        if (errorMessage != null) {
                            Surface(
                                color = StatusRestrictedRed.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StatusRestrictedRed.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app_lock_gate_error")
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusRestrictedRed,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { onUnlockWithCredential(secretInput) },
                            enabled = secretInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_gate_unlock_button")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock ClonexaPro")
                        }

                        if (biometricSupportedAndEnabled) {
                            OutlinedButton(
                                onClick = onUnlockWithBiometrics,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app_lock_gate_biometric_button")
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock with Biometrics")
                            }
                        }

                        TextButton(
                            onClick = {
                                onClearError()
                                recoveryMode = true
                            },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("app_lock_gate_recovery_toggle_button")
                        ) {
                            Text("Forgot ${credentialType.label}? Use Recovery Code")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reset App Lock via Recovery Code",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Enter the one-time Recovery Code (CLX-XXXX-XXXX) generated when you enabled App Lock. Verifying your Recovery Code safely resets App Lock so you can set a new credential.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = recoveryCodeInput,
                            onValueChange = {
                                onClearError()
                                recoveryCodeInput = it.uppercase().take(20)
                            },
                            label = { Text("Recovery Code (CLX-XXXX-XXXX)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_gate_recovery_input")
                        )

                        if (errorMessage != null) {
                            Surface(
                                color = StatusRestrictedRed.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StatusRestrictedRed.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("app_lock_gate_error")
                            ) {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusRestrictedRed,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Button(
                            onClick = { onRecoverWithCode(recoveryCodeInput) },
                            enabled = recoveryCodeInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_gate_recovery_submit_button")
                        ) {
                            Text("Verify Recovery Code & Reset Lock")
                        }

                        TextButton(
                            onClick = {
                                onClearError()
                                recoveryMode = false
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Back to ${credentialType.label} Unlock")
                        }
                    }
                }
            }
        }
    }
}

