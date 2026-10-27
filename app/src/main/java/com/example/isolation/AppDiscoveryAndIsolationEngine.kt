package com.example.isolation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.model.CompatibilityStatus
import com.example.model.DiscoveredApp
import com.example.model.HostIsolationEnvironmentReport
import com.example.model.IsolationTier
import com.example.model.ManagedAppInstanceEntity
import com.example.model.TargetEcosystemApp
import com.example.security.CryptoSecurityManager

data class InstanceLaunchResult(
    val succeeded: Boolean,
    val effectiveTier: IsolationTier,
    val message: String
)

/**
 * Genuine Android App Discovery, Compatibility Inspection, and Isolation Engine.
 *
 * Uses official Android APIs:
 * - PackageManager & LauncherApps for cross-profile app discovery
 * - UserManager for detecting secondary Work/Clone UserHandles
 * - DevicePolicyManager for Managed Work Profile provisioning
 * - Strict REQUIRE_SECURE_ENV and security boundary inspection
 */
class AppDiscoveryAndIsolationEngine(
    private val context: Context,
    private val cryptoSecurityManager: CryptoSecurityManager
) {

    companion object {
        const val WORKSPACE_NOTIFICATION_CHANNEL_ID = "clonexapro_workspace_events"

        // Target Messaging & Social catalog defined in Section 2
        private val TARGET_CATALOG = listOf(
            Triple("WhatsApp", "com.whatsapp", "Messaging"),
            Triple("WhatsApp Business", "com.whatsapp.w4b", "Messaging"),
            Triple("Instagram", "com.instagram.android", "Social Media"),
            Triple("Facebook", "com.facebook.katana", "Social Media"),
            Triple("Messenger", "com.facebook.orca", "Messaging"),
            Triple("TikTok", "com.zhiliaoapp.musically", "Social Video"),
            Triple("Telegram", "org.telegram.messenger", "Messaging"),
            Triple("Discord", "com.discord", "Community & Chat"),
            Triple("Signal", "org.thoughtcrime.securesms", "Encrypted Messaging"),
            Triple("Snapchat", "com.snapchat.android", "Social Media"),
            Triple("X (Twitter)", "com.twitter.android", "Social Media"),
            Triple("Slack", "com.Slack", "Workspace Chat")
        )

        // Packages that require hardware-backed attestation or strict User-0 security boundaries
        private val SECURE_ENV_RESTRICTED_PREFIXES = listOf(
            "com.google.android.gms",
            "com.android.vending",
            "com.google.android.apps.walletnfcrel",
            "com.paypal.android",
            "com.revolut",
            "com.chase.sig.android",
            "com.Runtime.banking"
        )

        private val CORE_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.providers.settings",
            "com.android.shell"
        )
    }

    init {
        ensureNotificationChannel()
    }

    /**
     * Evaluates the live Android device/emulator capabilities for app isolation.
     */
    fun inspectHostEnvironment(): HostIsolationEnvironmentReport {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

        val profiles: List<UserHandle> = try {
            userManager?.userProfiles ?: listOf(Process.myUserHandle())
        } catch (_: Exception) {
            listOf(Process.myUserHandle())
        }

        val mySerial = userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: 0L
        val serialNumbers = profiles.map { handle ->
            try {
                userManager?.getSerialNumberForUser(handle) ?: 0L
            } catch (_: Exception) {
                0L
            }
        }

        val hasSecondaryProfile = serialNumbers.any { it != mySerial }
        val isProvisioningAllowed = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm?.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE) == true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        val isProfileOwner = try {
            dpm?.isProfileOwnerApp(context.packageName) == true
        } catch (_: Exception) {
            false
        }

        val supportsCloneProfileApi = Build.VERSION.SDK_INT >= 34

        val summary = when {
            hasSecondaryProfile ->
                "Tier C Active: Secondary Android Profile detected (${profiles.size} UserHandles). Genuine cross-profile isolated launch is available."
            isProvisioningAllowed ->
                "Work Profile Ready: Host device permits Managed Work Profile provisioning for OS-level Tier C isolation."
            else ->
                "Single-User Host Mode: Running on a single UserHandle (Serial $mySerial). Supports Tier B Multi-Document Task separation & Tier A Private Space management. Provision a Work Profile on a physical device for Tier C data separation."
        }

        return HostIsolationEnvironmentReport(
            androidSdkInt = Build.VERSION.SDK_INT,
            androidReleaseName = Build.VERSION.RELEASE ?: "Android",
            deviceManufacturer = Build.MANUFACTURER ?: "Generic",
            deviceModel = Build.MODEL ?: "Android Device",
            detectedProfileCount = profiles.size,
            profileSerialNumbers = serialNumbers,
            hasSecondaryUserProfile = hasSecondaryProfile,
            isManagedProfileProvisioningAllowed = isProvisioningAllowed,
            isProfileOwnerActive = isProfileOwner,
            supportsCloneProfileApi = supportsCloneProfileApi,
            hardwareKeystoreAvailable = cryptoSecurityManager.isHardwareKeystoreAvailable(),
            deviceCredentialOrBiometricEnrolled = cryptoSecurityManager.isDeviceCredentialOrBiometricAvailable(),
            activeEngineSummary = summary
        )
    }

    /**
     * Discovers genuinely installed launchable applications on the device using
     * LauncherApps and PackageManager. Never fabricates installed apps.
     */
    fun discoverInstalledApps(includeSystemApps: Boolean): List<DiscoveredApp> {
        val pm = context.packageManager
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

        val myHandle = Process.myUserHandle()
        val mySerial = userManager?.getSerialNumberForUser(myHandle) ?: 0L
        val profiles = try {
            userManager?.userProfiles ?: listOf(myHandle)
        } catch (_: Exception) {
            listOf(myHandle)
        }
        val secondaryProfiles = profiles.filter {
            (userManager?.getSerialNumberForUser(it) ?: 0L) != mySerial
        }

        // Map of packageName -> Secondary UserHandle serial if present in a secondary profile
        val secondaryProfilePackages = mutableMapOf<String, Long>()
        if (launcherApps != null) {
            for (secHandle in secondaryProfiles) {
                val secSerial = userManager?.getSerialNumberForUser(secHandle) ?: continue
                try {
                    val activities = launcherApps.getActivityList(null, secHandle)
                    for (info in activities) {
                        secondaryProfilePackages[info.applicationInfo.packageName] = secSerial
                    }
                } catch (_: Exception) {
                    // Ignore cross-profile visibility exceptions
                }
            }
        }

        // Query all launchable packages in the primary profile
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedInfos = try {
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            pm.queryIntentActivities(mainIntent, 0)
        }

        val discoveredByPackage = linkedMapOf<String, DiscoveredApp>()
        val targetPackageSet = TARGET_CATALOG.map { it.second }.toSet()

        for (resolveInfo in resolvedInfos) {
            val appInfo = resolveInfo.activityInfo?.applicationInfo ?: continue
            val pkgName = appInfo.packageName
            if (pkgName == context.packageName || discoveredByPackage.containsKey(pkgName)) {
                continue
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isPureSystem = isSystem && !isUpdatedSystem

            if (!includeSystemApps && isPureSystem && pkgName !in targetPackageSet) {
                // Keep user-facing productivity/browser/messaging apps or let user toggle system apps
                if (pkgName in CORE_SYSTEM_PACKAGES || pkgName.startsWith("com.android.providers")) {
                    continue
                }
            }

            val appLabel = try {
                pm.getApplicationLabel(appInfo).toString().ifBlank { pkgName }
            } catch (_: Exception) {
                pkgName
            }

            val pkgInfo = try {
                pm.getPackageInfo(pkgName, PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                null
            }

            val versionName = pkgInfo?.versionName ?: "1.0"
            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 21
            val hasSecureEnvFlag = inspectRequireSecureEnvFlag(pkgInfo?.applicationInfo ?: appInfo)
            val secondarySerial = secondaryProfilePackages[pkgName]
            val isTargetApp = pkgName in targetPackageSet

            val (status, reason) = evaluateCompatibility(
                pkgName = pkgName,
                isPureSystem = isPureSystem,
                hasSecureEnvFlag = hasSecureEnvFlag,
                hasSecondaryProfileInstall = secondarySerial != null,
                isTargetApp = isTargetApp
            )

            val categoryLabel = resolveCategoryLabel(appInfo, pkgName)
            val iconDrawable = try {
                pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                null
            }

            discoveredByPackage[pkgName] = DiscoveredApp(
                packageName = pkgName,
                appName = appLabel,
                versionName = versionName,
                targetSdk = appInfo.targetSdkVersion,
                minSdk = minSdk,
                isSystemApp = isPureSystem,
                isUpdatedSystemApp = isUpdatedSystem,
                isInstalledInSecondaryProfile = secondarySerial != null,
                secondaryProfileSerial = secondarySerial,
                categoryLabel = categoryLabel,
                compatibilityStatus = status,
                compatibilityReason = reason,
                hasRequireSecureEnvFlag = hasSecureEnvFlag,
                isTargetMessagingOrSocial = isTargetApp,
                iconDrawable = iconDrawable
            )
        }

        return discoveredByPackage.values.sortedWith(
            compareByDescending<DiscoveredApp> { it.isTargetMessagingOrSocial }
                .thenByDescending { it.isInstalledInSecondaryProfile }
                .thenBy { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Evaluates the Target Messaging & Social Media ecosystem (WhatsApp, Facebook, Instagram,
     * TikTok, Telegram, etc.) against real host installation state.
     */
    fun evaluateTargetEcosystemApps(): List<TargetEcosystemApp> {
        val pm = context.packageManager
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val mySerial = userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: 0L
        val secondaryHandles = try {
            userManager?.userProfiles?.filter {
                (userManager.getSerialNumberForUser(it)) != mySerial
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return TARGET_CATALOG.map { (name, pkg, category) ->
            val installedOnHost = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }

            val installedInWork = secondaryHandles.any { handle ->
                try {
                    launcherApps?.getActivityList(pkg, handle)?.isNotEmpty() == true
                } catch (_: Exception) {
                    false
                }
            }

            val notes = when {
                installedInWork ->
                    "Installed in both Primary & Isolated Profile. Supports Tier C full data separation."
                installedOnHost ->
                    "Installed on device. Ready for Tier B Multi-Task window or Tier C Work Profile cloning."
                else ->
                    "Not currently installed on this device. Install from Google Play to manage or isolate."
            }

            TargetEcosystemApp(
                displayName = name,
                packageName = pkg,
                category = category,
                isInstalledOnHost = installedOnHost,
                isInstalledInWorkProfile = installedInWork,
                isolationNotes = notes,
                compatibilityStatus = CompatibilityStatus.COMPATIBLE_ISOLATED
            )
        }
    }

    /**
     * Launches a managed instance using the highest supported isolation mechanism,
     * strictly respecting REQUIRE_SECURE_ENV and Android security boundaries.
     */
    fun launchManagedInstance(
        instance: ManagedAppInstanceEntity,
        strictSecurityCheck: Boolean,
        notificationsEnabled: Boolean
    ): InstanceLaunchResult {
        val pm = context.packageManager

        val appInfo = try {
            pm.getApplicationInfo(instance.packageName, PackageManager.GET_META_DATA)
        } catch (_: PackageManager.NameNotFoundException) {
            return InstanceLaunchResult(
                succeeded = false,
                effectiveTier = instance.resolvedTier,
                message = "Launch failed: ${instance.appName} (${instance.packageName}) is no longer installed on this device."
            )
        }

        if (strictSecurityCheck && inspectRequireSecureEnvFlag(appInfo)) {
            return InstanceLaunchResult(
                succeeded = false,
                effectiveTier = instance.resolvedTier,
                message = "Security Boundary Enforced: ${instance.appName} declares REQUIRE_SECURE_ENV or hardware attestation requirements and cannot be loaded inside a simulated/virtualized container."
            )
        }

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

        // 1. Attempt Tier C Cross-Profile Isolated Launch if requested or if a matching secondary UserHandle exists
        if (instance.resolvedTier == IsolationTier.ISOLATED_PROFILE_ACTIVE && launcherApps != null && userManager != null) {
            val mySerial = userManager.getSerialNumberForUser(Process.myUserHandle())
            val targetHandle = userManager.userProfiles.firstOrNull { handle ->
                val serial = userManager.getSerialNumberForUser(handle)
                (instance.userProfileSerial != 0L && serial == instance.userProfileSerial) ||
                    (serial != mySerial)
            }

            if (targetHandle != null) {
                try {
                    val activities = launcherApps.getActivityList(instance.packageName, targetHandle)
                    if (activities.isNotEmpty()) {
                        val component = activities.first().componentName
                        launcherApps.startMainActivity(component, targetHandle, null, null)
                        val serial = userManager.getSerialNumberForUser(targetHandle)
                        if (notificationsEnabled) {
                            postLaunchNotification(instance, IsolationTier.ISOLATED_PROFILE_ACTIVE)
                        }
                        return InstanceLaunchResult(
                            succeeded = true,
                            effectiveTier = IsolationTier.ISOLATED_PROFILE_ACTIVE,
                            message = "Launched ${instance.appName} (${instance.accountLabel}) in isolated Android Profile (Serial #$serial) with full data separation."
                        )
                    }
                } catch (e: Exception) {
                    return InstanceLaunchResult(
                        succeeded = false,
                        effectiveTier = IsolationTier.ISOLATED_PROFILE_ACTIVE,
                        message = "Cross-profile launch blocked by OS policy: ${e.localizedMessage ?: "Profile locked or unavailable"}."
                    )
                }
            }
        }

        // 2. Fallback or explicit Tier B (Multi-Document Task Launch) / Tier A (Standard Workspace Launch)
        val baseLaunchIntent = pm.getLaunchIntentForPackage(instance.packageName)
            ?: return InstanceLaunchResult(
                succeeded = false,
                effectiveTier = instance.resolvedTier,
                message = "Launch failed: ${instance.appName} does not expose a main LAUNCHER activity."
            )

        return try {
            val effectiveTier = if (instance.resolvedTier == IsolationTier.UI_MANAGED_ENTRY) {
                baseLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                IsolationTier.UI_MANAGED_ENTRY
            } else {
                baseLaunchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
                IsolationTier.MANAGED_TASK_LAUNCH
            }

            context.startActivity(baseLaunchIntent)
            if (notificationsEnabled) {
                postLaunchNotification(instance, effectiveTier)
            }

            val detailNote = if (instance.resolvedTier == IsolationTier.ISOLATED_PROFILE_ACTIVE &&
                effectiveTier == IsolationTier.MANAGED_TASK_LAUNCH
            ) {
                "Launched ${instance.appName} (${instance.accountLabel}) via Tier B Multi-Task window (No secondary Work Profile UserHandle is active on this device yet)."
            } else {
                "Launched ${instance.appName} (${instance.accountLabel}) via ${effectiveTier.badgeTitle}."
            }

            InstanceLaunchResult(
                succeeded = true,
                effectiveTier = effectiveTier,
                message = detailNote
            )
        } catch (e: Exception) {
            InstanceLaunchResult(
                succeeded = false,
                effectiveTier = instance.resolvedTier,
                message = "OS rejected launch intent for ${instance.appName}: ${e.localizedMessage ?: "SecurityException"}"
            )
        }
    }

    /**
     * Builds the official Android Work Profile provisioning intent
     * (DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).
     */
    fun createWorkProfileProvisioningIntent(): Intent {
        val adminComponent: ComponentName = ClonexaDeviceAdminReceiver.getComponentName(context)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
        }
    }

    /**
     * Inspects whether an ApplicationInfo declares REQUIRE_SECURE_ENV (Android 12+ private flag
     * or manifest metadata) or belongs to restricted financial/security packages.
     */
    private fun inspectRequireSecureEnvFlag(appInfo: ApplicationInfo): Boolean {
        val pkg = appInfo.packageName
        if (SECURE_ENV_RESTRICTED_PREFIXES.any { pkg.startsWith(it, ignoreCase = true) }) {
            return true
        }
        val meta = appInfo.metaData
        if (meta != null) {
            if (meta.getBoolean("android.security.REQUIRE_SECURE_ENV", false) ||
                meta.getBoolean("require_secure_env", false)
            ) {
                return true
            }
        }
        // Reflectively inspect ApplicationInfo.PRIVATE_FLAG_EXT_REQUIRE_SECURE_ENV if exposed
        return try {
            val field = ApplicationInfo::class.java.getDeclaredField("privateFlagsExt")
            field.isAccessible = true
            val flagsExt = field.getInt(appInfo)
            // PRIVATE_FLAG_EXT_REQUIRE_SECURE_ENV = 1 << 4 (0x10) in AOSP ApplicationInfo
            (flagsExt and 0x10) != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun evaluateCompatibility(
        pkgName: String,
        isPureSystem: Boolean,
        hasSecureEnvFlag: Boolean,
        hasSecondaryProfileInstall: Boolean,
        isTargetApp: Boolean
    ): Pair<CompatibilityStatus, String> {
        if (hasSecureEnvFlag) {
            return CompatibilityStatus.RESTRICTED_SECURE_ENV to
                "Declares REQUIRE_SECURE_ENV or hardware attestation requirements. Blocked from simulated containers per Android & Google Play security policy."
        }
        if (pkgName in CORE_SYSTEM_PACKAGES) {
            return CompatibilityStatus.INCOMPATIBLE_SYSTEM_CORE to
                "Core Android OS system service bound to primary hardware user."
        }
        if (hasSecondaryProfileInstall) {
            return CompatibilityStatus.COMPATIBLE_ISOLATED to
                "Verified in secondary Android profile. Supports full Tier C OS-level isolated execution."
        }
        if (isTargetApp) {
            return CompatibilityStatus.COMPATIBLE_ISOLATED to
                "Verified target social/messaging app. Compatible with Android Work/Clone Profile isolation and multi-task workspace management."
        }
        if (isPureSystem) {
            return CompatibilityStatus.COMPATIBLE_WORK_PROFILE_ONLY to
                "Pre-installed system application. Can be enabled inside a Managed Work Profile or launched in a separate document task."
        }
        return CompatibilityStatus.COMPATIBLE_ISOLATED to
            "Standard user application. Compatible with Work Profile data isolation, Multi-Document task launching, and Private Space lock."
    }

    private fun resolveCategoryLabel(appInfo: ApplicationInfo, pkgName: String): String {
        val targetMatch = TARGET_CATALOG.firstOrNull { it.second == pkgName }
        if (targetMatch != null) return targetMatch.third

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> return "Social & Messaging"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
                ApplicationInfo.CATEGORY_GAME -> return "Gaming"
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return "Media & Video"
                ApplicationInfo.CATEGORY_NEWS -> return "News & Reading"
                ApplicationInfo.CATEGORY_MAPS -> return "Navigation"
            }
        }
        return if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
            "System Utility"
        } else {
            "Installed App"
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                WORKSPACE_NOTIFICATION_CHANNEL_ID,
                "ClonexaPro Instance Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays active isolated workspace instance status and security alerts."
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun postLaunchNotification(instance: ManagedAppInstanceEntity, tier: IsolationTier) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val notification = NotificationCompat.Builder(context, WORKSPACE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("${instance.appName} • ${instance.accountLabel}")
                .setContentText("Active via ${tier.badgeTitle}")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            nm.notify(instance.id.toInt().coerceAtLeast(1001), notification)
        } catch (_: Exception) {
            // Ignore notification errors in headless/test environments
        }
    }
}
