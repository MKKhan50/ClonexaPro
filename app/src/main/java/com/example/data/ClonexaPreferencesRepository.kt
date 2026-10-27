package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLockCredentialType
import com.example.model.MonetizationTier
import com.example.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clonexaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clonexapro_preferences"
)

data class ClonexaUserSettings(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val encryptedPinToken: String = "",
    val biometricUnlockEnabled: Boolean = true,
    val sessionTimeoutMinutes: Int = 5,
    val notificationsEnabled: Boolean = true,
    val showSystemAppsInDiscovery: Boolean = false,
    val strictSecurityBoundaryCheck: Boolean = true,
    val monetizationTier: MonetizationTier = MonetizationTier.FREE_STANDARD,
    val appLockEnabled: Boolean = false,
    val appLockCredentialType: AppLockCredentialType = AppLockCredentialType.PIN,
    val encryptedAppLockToken: String = "",
    val encryptedAppLockRecoveryToken: String = "",
    val publicPrivacyPolicyUrl: String = ""
) {
    val isPinConfigured: Boolean
        get() = encryptedPinToken.isNotBlank()

    val isAppLockConfigured: Boolean
        get() = encryptedAppLockToken.isNotBlank()

    val isAppLockActive: Boolean
        get() = appLockEnabled && encryptedAppLockToken.isNotBlank()
}

class ClonexaPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_PREF = stringPreferencesKey("theme_preference")
        val ENCRYPTED_PIN_TOKEN = stringPreferencesKey("encrypted_pin_token")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_unlock_enabled")
        val SESSION_TIMEOUT_MIN = intPreferencesKey("session_timeout_minutes")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps_in_discovery")
        val STRICT_SECURITY_CHECK = booleanPreferencesKey("strict_security_boundary_check")
        val MONETIZATION_TIER = stringPreferencesKey("monetization_tier")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val APP_LOCK_CREDENTIAL_TYPE = stringPreferencesKey("app_lock_credential_type")
        val ENCRYPTED_APP_LOCK_TOKEN = stringPreferencesKey("encrypted_app_lock_token")
        val ENCRYPTED_APP_LOCK_RECOVERY_TOKEN = stringPreferencesKey("encrypted_app_lock_recovery_token")
        val PUBLIC_PRIVACY_POLICY_URL = stringPreferencesKey("public_privacy_policy_url")
    }

    val settingsFlow: Flow<ClonexaUserSettings> = context.clonexaDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_PREF] ?: ThemePreference.DARK.name
        val tierName = prefs[Keys.MONETIZATION_TIER] ?: MonetizationTier.FREE_STANDARD.name
        val lockTypeName = prefs[Keys.APP_LOCK_CREDENTIAL_TYPE] ?: AppLockCredentialType.PIN.name
        ClonexaUserSettings(
            themePreference = ThemePreference.entries.find { it.name == themeName } ?: ThemePreference.DARK,
            encryptedPinToken = prefs[Keys.ENCRYPTED_PIN_TOKEN] ?: "",
            biometricUnlockEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: true,
            sessionTimeoutMinutes = prefs[Keys.SESSION_TIMEOUT_MIN] ?: 5,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            showSystemAppsInDiscovery = prefs[Keys.SHOW_SYSTEM_APPS] ?: false,
            strictSecurityBoundaryCheck = prefs[Keys.STRICT_SECURITY_CHECK] ?: true,
            monetizationTier = MonetizationTier.entries.find { it.name == tierName } ?: MonetizationTier.FREE_STANDARD,
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            appLockCredentialType = AppLockCredentialType.entries.find { it.name == lockTypeName } ?: AppLockCredentialType.PIN,
            encryptedAppLockToken = prefs[Keys.ENCRYPTED_APP_LOCK_TOKEN] ?: "",
            encryptedAppLockRecoveryToken = prefs[Keys.ENCRYPTED_APP_LOCK_RECOVERY_TOKEN] ?: "",
            publicPrivacyPolicyUrl = prefs[Keys.PUBLIC_PRIVACY_POLICY_URL] ?: ""
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.clonexaDataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }

    suspend fun saveEncryptedPinToken(token: String) {
        context.clonexaDataStore.edit { it[Keys.ENCRYPTED_PIN_TOKEN] = token }
    }

    suspend fun clearPinToken() {
        context.clonexaDataStore.edit { it.remove(Keys.ENCRYPTED_PIN_TOKEN) }
    }

    suspend fun configureAppLock(
        credentialType: AppLockCredentialType,
        encryptedCredentialToken: String,
        encryptedRecoveryToken: String
    ) {
        context.clonexaDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = true
            it[Keys.APP_LOCK_CREDENTIAL_TYPE] = credentialType.name
            it[Keys.ENCRYPTED_APP_LOCK_TOKEN] = encryptedCredentialToken
            it[Keys.ENCRYPTED_APP_LOCK_RECOVERY_TOKEN] = encryptedRecoveryToken
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.clonexaDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun clearAppLock() {
        context.clonexaDataStore.edit {
            it[Keys.APP_LOCK_ENABLED] = false
            it.remove(Keys.ENCRYPTED_APP_LOCK_TOKEN)
            it.remove(Keys.ENCRYPTED_APP_LOCK_RECOVERY_TOKEN)
        }
    }

    suspend fun setPublicPrivacyPolicyUrl(url: String) {
        context.clonexaDataStore.edit {
            it[Keys.PUBLIC_PRIVACY_POLICY_URL] = url.trim()
        }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        context.clonexaDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setSessionTimeoutMinutes(minutes: Int) {
        context.clonexaDataStore.edit { it[Keys.SESSION_TIMEOUT_MIN] = minutes }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.clonexaDataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.clonexaDataStore.edit { it[Keys.SHOW_SYSTEM_APPS] = show }
    }

    suspend fun setStrictSecurityBoundaryCheck(enabled: Boolean) {
        context.clonexaDataStore.edit { it[Keys.STRICT_SECURITY_CHECK] = enabled }
    }

    suspend fun setMonetizationTier(tier: MonetizationTier) {
        context.clonexaDataStore.edit { it[Keys.MONETIZATION_TIER] = tier.name }
    }
}

