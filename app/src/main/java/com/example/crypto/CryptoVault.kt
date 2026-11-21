package com.example.crypto

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Application-scoped facade for hardware-backed E2EE identity keys and local PIN/theme storage.
 * Resolved at the ViewModel factory boundary using applicationContext to prevent Activity leaks.
 */
class CryptoVault(private val appContext: Context) {

    suspend fun getOrCreateIdentityKeys(userId: String): IdentityKeyMaterial =
        E2EECryptoEngine.getOrCreateIdentityKeys(appContext, userId)

    suspend fun clearIdentityKeys(userId: String) =
        E2EECryptoEngine.clearIdentityKeys(appContext, userId)

    fun observeLocalSecurityPreferences(): Flow<LocalSecurityPreferences> =
        AppLockManager.observePreferences(appContext)

    suspend fun saveTheme(paletteKey: String, darkMode: Boolean) =
        AppLockManager.setThemePreferences(appContext, paletteKey, darkMode)

    suspend fun setPin(pin: String) = AppLockManager.setPin(appContext, pin)

    suspend fun disablePin() = AppLockManager.disablePin(appContext)

    suspend fun setBiometricEnabled(enabled: Boolean) = AppLockManager.setBiometricEnabled(appContext, enabled)

    suspend fun verifyPin(pin: String): Boolean = AppLockManager.verifyPin(appContext, pin)

    fun isBiometricHardwareAvailable(): Boolean = AppLockManager.isBiometricHardwareAvailable(appContext)
}
