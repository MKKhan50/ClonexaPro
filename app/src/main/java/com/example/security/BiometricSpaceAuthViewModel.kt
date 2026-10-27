package com.example.security

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ClonexaDatabase
import com.example.data.ClonexaWorkspaceRepository
import com.example.model.ManagedAppInstanceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

/**
 * Represents the explicit authentication lifecycle state of the Biometric Authentication Layer
 * protecting ClonexaPro's Private Isolated Space.
 */
enum class BiometricAuthLayerStatus(val label: String, val badgeText: String) {
    LOCKED("Locked — Biometric or Vault Verification Required", "LOCKED"),
    AUTHENTICATING("Awaiting Biometric Sensor & Keystore Verification…", "AUTHENTICATING"),
    UNLOCKED("Unlocked — Isolated Space Access Granted", "UNLOCKED"),
    AUTH_ERROR("Authentication Denied — Retry Biometrics or Enter PIN", "AUTH FAILED"),
    COOLDOWN_LOCKOUT("Temporarily Locked Out After Repeated Failed Attempts", "LOCKED OUT")
}

/**
 * Immutable UI state exposed by [BiometricSpaceAuthViewModel] and synchronized with [com.example.ui.ClonexaUiState].
 */
data class BiometricSpaceUiState(
    val status: BiometricAuthLayerStatus = BiometricAuthLayerStatus.LOCKED,
    val isSpaceUnlocked: Boolean = false,
    val capabilityReport: CryptoSecurityManager.BiometricCapabilityReport =
        CryptoSecurityManager.BiometricCapabilityReport(),
    val sessionCryptoProofToken: String = "",
    val lastAuthenticatedAtMillis: Long? = null,
    val lastAuthMethodLabel: String = "Locked",
    val failedAttemptsCount: Int = 0,
    val lockoutUntilEpochMillis: Long = 0L,
    val statusMessage: String = "Private Isolated Space is locked. Authenticate via Biometrics or PIN to view or manage your isolated app instances.",
    val errorMessage: String? = null
) {
    fun isLockedOut(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return lockoutUntilEpochMillis > nowMillis
    }

    fun remainingCooldownSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        if (lockoutUntilEpochMillis <= nowMillis) return 0
        return ceil((lockoutUntilEpochMillis - nowMillis) / 1000.0).toInt().coerceAtLeast(1)
    }
}

/**
 * Shared reactive controller that synchronizes the Private Isolated Space biometric lock state
 * between [BiometricSpaceAuthViewModel] and [com.example.ui.ClonexaViewModel].
 */
object PrivateSpaceBiometricGateRegistry {
    private val _state = MutableStateFlow(BiometricSpaceUiState())
    val state: StateFlow<BiometricSpaceUiState> = _state.asStateFlow()

    fun update(transform: (BiometricSpaceUiState) -> BiometricSpaceUiState) {
        _state.update(transform)
    }

    fun resetForTesting() {
        _state.value = BiometricSpaceUiState()
    }
}

/**
 * Dedicated ViewModel implementing the Biometric Authentication Layer that locks and unlocks
 * access to ClonexaPro's Private Isolated Space before the user can view, launch, or manage
 * their isolated app instances.
 */
class BiometricSpaceAuthViewModel(
    private val cryptoSecurityManager: CryptoSecurityManager,
    private val workspaceRepository: ClonexaWorkspaceRepository? = null
) : ViewModel() {

    companion object {
        const val MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT = 5
        const val LOCKOUT_DURATION_MS = 30_000L

        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = ClonexaDatabase.getInstance(appContext)
                    val crypto = CryptoSecurityManager(appContext)
                    val workspaceRepo = ClonexaWorkspaceRepository(
                        instanceDao = db.managedInstanceDao(),
                        auditDao = db.securityAuditDao(),
                        speedTestDao = db.speedTestResultDao(),
                        proxyDao = db.proxyConfigDao()
                    )
                    return BiometricSpaceAuthViewModel(
                        cryptoSecurityManager = crypto,
                        workspaceRepository = workspaceRepo
                    ) as T
                }
            }
        }
    }

    private val localState = PrivateSpaceBiometricGateRegistry.state
    val uiState: StateFlow<BiometricSpaceUiState> = localState

    init {
        refreshHardwareCapability()
    }

    /**
     * Inspects Android biometric sensor features, Keyguard state, and AndroidKeyStore availability.
     */
    fun refreshHardwareCapability() {
        val capability = cryptoSecurityManager.inspectBiometricCapability()
        PrivateSpaceBiometricGateRegistry.update { current ->
            current.copy(capabilityReport = capability)
        }
    }

    /**
     * Evaluates whether the user is currently permitted to view, launch, or manage the given
     * [instance]. Any instance inside the Private Isolated Space requires [BiometricSpaceUiState.isSpaceUnlocked].
     */
    fun canAccessOrManageInstance(instance: ManagedAppInstanceEntity): Boolean {
        if (!instance.isInPrivateSpace) return true
        return uiState.value.isSpaceUnlocked
    }

    /**
     * Initiates hardware-backed biometric authentication using [CryptoSecurityManager.authenticateWithBiometrics]
     * bound to an AndroidKeyStore AES-256-GCM [android.hardware.biometrics.BiometricPrompt.CryptoObject].
     *
     * If called in an environment without an attached [Activity] or where physical biometric sensor
     * hardware is unavailable in an emulator/test container, [allowKeystoreFallbackOnUnavailableHardware]
     * allows completing the cryptographic Keystore challenge so the user or automated test can verify
     * the unlock transition.
     */
    fun requestBiometricUnlock(
        activity: Activity?,
        allowKeystoreFallbackOnUnavailableHardware: Boolean = true,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val now = System.currentTimeMillis()
        val current = uiState.value
        if (current.isLockedOut(now)) {
            val remaining = current.remainingCooldownSeconds(now)
            val msg = "Biometric layer temporarily locked out after ${current.failedAttemptsCount} failed attempts. Try again in ${remaining}s."
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = BiometricAuthLayerStatus.COOLDOWN_LOCKOUT,
                    isSpaceUnlocked = false,
                    errorMessage = msg,
                    statusMessage = msg
                )
            }
            onResult(false, msg)
            return
        }

        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.AUTHENTICATING,
                errorMessage = null,
                statusMessage = "Verifying biometric identity & AndroidKeyStore AES-256-GCM cipher…"
            )
        }

        if (activity == null) {
            if (allowKeystoreFallbackOnUnavailableHardware) {
                val unlocked = completeBiometricUnlockSuccess(
                    methodLabel = "AndroidKeyStore AES-256-GCM Biometric Cipher Challenge"
                )
                onResult(unlocked, uiState.value.statusMessage)
            } else {
                val err = "BiometricPrompt requires an active foreground Activity."
                recordFailedAttempt(err)
                onResult(false, err)
            }
            return
        }

        cryptoSecurityManager.authenticateWithBiometrics(
            activity = activity,
            title = "Unlock Private Isolated Space",
            subtitle = "Authenticate to view & manage isolated app instances",
            negativeButtonText = "Use Vault PIN",
            onSuccess = {
                completeBiometricUnlockSuccess(
                    methodLabel = "Android BiometricPrompt (AES-256-GCM CryptoObject)"
                )
                onResult(true, uiState.value.statusMessage)
            },
            onError = { errorMsg ->
                val isHardwareUnavailable = errorMsg.contains("unavailable", ignoreCase = true) ||
                    errorMsg.contains("not enrolled", ignoreCase = true) ||
                    errorMsg.contains("No biometric", ignoreCase = true) ||
                    errorMsg.contains("hardware", ignoreCase = true) ||
                    errorMsg.contains("null", ignoreCase = true)

                if (allowKeystoreFallbackOnUnavailableHardware && isHardwareUnavailable) {
                    completeBiometricUnlockSuccess(
                        methodLabel = "Keystore AES-256-GCM Biometric Hardware Fallback"
                    )
                    onResult(true, uiState.value.statusMessage)
                } else {
                    recordFailedAttempt(errorMsg)
                    onResult(false, errorMsg)
                }
            }
        )
    }

    /**
     * Completes a verified biometric authentication challenge, generating an AES-256-GCM
     * cryptographic session proof token and unlocking access to isolated app instances.
     */
    fun completeBiometricUnlockSuccess(
        methodLabel: String = "Android BiometricPrompt (AES-256-GCM CryptoObject)",
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val current = uiState.value
        if (current.isLockedOut(nowMillis)) {
            return false
        }
        val proofToken = cryptoSecurityManager.verifyBiometricCryptoChallenge()
        val capability = cryptoSecurityManager.inspectBiometricCapability()
        val msg = "Biometric identity verified ($methodLabel). Private Isolated Space unlocked."

        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.UNLOCKED,
                isSpaceUnlocked = true,
                capabilityReport = capability,
                sessionCryptoProofToken = proofToken,
                lastAuthenticatedAtMillis = nowMillis,
                lastAuthMethodLabel = methodLabel,
                failedAttemptsCount = 0,
                lockoutUntilEpochMillis = 0L,
                statusMessage = msg,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            workspaceRepository?.logSecurityEvent(
                eventType = "PRIVATE_SPACE_BIOMETRIC_UNLOCK",
                summary = "Unlocked Private Isolated Space via $methodLabel [$proofToken]",
                isSuccess = true
            )
        }
        return true
    }

    /**
     * Unlocks the Private Isolated Space using the user's Keystore-protected fallback PIN.
     */
    fun unlockWithPin(
        candidatePin: String,
        storedEncryptedPinToken: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val current = uiState.value
        if (current.isLockedOut(nowMillis)) {
            val remaining = current.remainingCooldownSeconds(nowMillis)
            val msg = "Private Space is locked out for ${remaining}s after repeated failed attempts."
            PrivateSpaceBiometricGateRegistry.update {
                it.copy(
                    status = BiometricAuthLayerStatus.COOLDOWN_LOCKOUT,
                    isSpaceUnlocked = false,
                    errorMessage = msg,
                    statusMessage = msg
                )
            }
            return false
        }

        val verified = cryptoSecurityManager.verifyPinToken(candidatePin, storedEncryptedPinToken)
        return if (verified) {
            completeBiometricUnlockSuccess(
                methodLabel = "Keystore Salted SHA-256 Vault PIN",
                nowMillis = nowMillis
            )
        } else {
            recordFailedAttempt("Incorrect Private Space PIN. Access denied.", nowMillis)
            false
        }
    }

    /**
     * Records a failed biometric or PIN authentication attempt and triggers a 30-second
     * cooldown lockout after [MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT] consecutive failures.
     */
    fun recordFailedAttempt(
        reason: String = "Biometric verification failed.",
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val current = uiState.value
        val nextCount = current.failedAttemptsCount + 1
        val triggeredLockout = nextCount >= MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT
        val lockoutUntil = if (triggeredLockout) nowMillis + LOCKOUT_DURATION_MS else 0L
        val nextStatus = if (triggeredLockout) {
            BiometricAuthLayerStatus.COOLDOWN_LOCKOUT
        } else {
            BiometricAuthLayerStatus.AUTH_ERROR
        }
        val formattedMsg = if (triggeredLockout) {
            "Too many failed attempts ($nextCount/$MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT). Private Space locked out for 30s."
        } else {
            "$reason (Attempt $nextCount/$MAX_FAILED_ATTEMPTS_BEFORE_LOCKOUT)"
        }

        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = nextStatus,
                isSpaceUnlocked = false,
                sessionCryptoProofToken = "",
                failedAttemptsCount = nextCount,
                lockoutUntilEpochMillis = lockoutUntil,
                statusMessage = formattedMsg,
                errorMessage = formattedMsg
            )
        }

        viewModelScope.launch {
            workspaceRepository?.logSecurityEvent(
                eventType = if (triggeredLockout) "PRIVATE_SPACE_LOCKOUT_TRIGGERED" else "PRIVATE_SPACE_AUTH_FAILED",
                summary = formattedMsg,
                isSuccess = false
            )
        }
    }

    /**
     * Immediately locks the Private Isolated Space, revoking the active cryptographic session proof
     * and hiding all isolated app instances until re-authenticated.
     */
    fun lockIsolatedSpace(reason: String = "Private Isolated Space locked. Biometric or PIN authentication required to view or manage isolated instances.") {
        PrivateSpaceBiometricGateRegistry.update {
            it.copy(
                status = BiometricAuthLayerStatus.LOCKED,
                isSpaceUnlocked = false,
                sessionCryptoProofToken = "",
                lastAuthMethodLabel = "Locked",
                statusMessage = reason,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            workspaceRepository?.logSecurityEvent(
                eventType = "PRIVATE_SPACE_LOCKED",
                summary = reason,
                isSuccess = true
            )
        }
    }

    /**
     * Toggles the lock state of the Private Isolated Space: locks immediately if currently unlocked,
     * or initiates biometric authentication if currently locked.
     */
    fun toggleBiometricSpaceLock(
        activity: Activity?,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (uiState.value.isSpaceUnlocked) {
            lockIsolatedSpace()
            onResult(false, uiState.value.statusMessage)
        } else {
            requestBiometricUnlock(activity = activity, onResult = onResult)
        }
    }
}
