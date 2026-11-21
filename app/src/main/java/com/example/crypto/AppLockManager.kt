package com.example.crypto

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.appLockDataStore by preferencesDataStore(name = "forever_us_app_lock")

data class LocalSecurityPreferences(
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val themePalette: String = "rose_quartz",
    val darkMode: Boolean = false
)

/**
 * Manages salted PBKDF2-HMAC-SHA256 PIN verification, local theme preferences,
 * and Android platform BiometricPrompt authentication (API 28+).
 */
object AppLockManager {
    private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
    private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
    private val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    private val KEY_THEME_PALETTE = stringPreferencesKey("theme_palette")
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")

    private const val PBKDF2_ITERATIONS = 65536
    private const val PBKDF2_KEY_BITS = 256

    fun observePreferences(context: Context): Flow<LocalSecurityPreferences> =
        context.appLockDataStore.data.map { prefs ->
            LocalSecurityPreferences(
                pinEnabled = prefs[KEY_PIN_ENABLED] ?: false,
                biometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false,
                themePalette = prefs[KEY_THEME_PALETTE] ?: "rose_quartz",
                darkMode = prefs[KEY_DARK_MODE] ?: false
            )
        }

    suspend fun setThemePreferences(context: Context, paletteKey: String, darkMode: Boolean) {
        context.appLockDataStore.edit { prefs ->
            prefs[KEY_THEME_PALETTE] = paletteKey
            prefs[KEY_DARK_MODE] = darkMode
        }
    }

    suspend fun setPin(context: Context, pin: String) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must be 4 to 8 digits." }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = hashPin(pin, salt)
            context.appLockDataStore.edit { prefs ->
                prefs[KEY_PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
                prefs[KEY_PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
                prefs[KEY_PIN_ENABLED] = true
            }
            Unit
        }

    suspend fun disablePin(context: Context) {
        context.appLockDataStore.edit { prefs ->
            prefs.remove(KEY_PIN_HASH)
            prefs.remove(KEY_PIN_SALT)
            prefs[KEY_PIN_ENABLED] = false
            prefs[KEY_BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(context: Context, enabled: Boolean) {
        context.appLockDataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun verifyPin(context: Context, candidatePin: String): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val prefs = context.appLockDataStore.data.first()
            val saltBase64 = prefs[KEY_PIN_SALT] ?: return@withContext false
            val expectedHashBase64 = prefs[KEY_PIN_HASH] ?: return@withContext false
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expectedHash = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val candidateHash = hashPin(candidatePin, salt)
            constantTimeEquals(expectedHash, candidateHash)
        }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val manager = context.getSystemService(BiometricManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun promptBiometricUnlock(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometric unlock requires Android 9 or higher. Please enter your PIN.")
            return
        }
        val executor = context.mainExecutor
        val cancellationSignal = CancellationSignal()
        val prompt = BiometricPrompt.Builder(context)
            .setTitle("Unlock SoulSync")
            .setSubtitle("Verify your identity to open your private sanctuary")
            .setNegativeButton("Use PIN", executor) { _, _ -> }
            .build()

        prompt.authenticate(
            cancellationSignal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onError(errString?.toString() ?: "Biometric authentication failed")
                }

                override fun onAuthenticationFailed() {
                    onError("Biometric not recognized. Try again or use PIN.")
                }
            }
        )
    }

    internal fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
