package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.cryptoDataStore by preferencesDataStore(name = "forever_us_e2ee_vault")

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

data class IdentityKeyMaterial(
    val publicKeyBase64: String,
    val privateKey: PrivateKey
)

/**
 * Production End-to-End Encryption Engine using:
 * - Elliptic Curve Diffie-Hellman (ECDH secp256r1 / P-256) identity key pairs
 * - Hardware-backed Android Keystore AES-256-GCM wrapping for private key persistence
 * - RFC 5869 HKDF-SHA256 shared session key derivation bound to coupleId
 * - Authenticated AES-256-GCM encryption with random 96-bit IVs and Additional Authenticated Data
 * - Deterministic 60-digit Safety Number fingerprint for partner MITM verification
 */
object E2EECryptoEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "forever_us_master_wrap_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_BYTES = 12
    private val secureRandom = SecureRandom()

    private fun pubKeyPref(uid: String) = stringPreferencesKey("e2ee_pub_$uid")
    private fun wrappedPrivKeyPref(uid: String) = stringPreferencesKey("e2ee_priv_wrap_$uid")
    private fun wrapIvPref(uid: String) = stringPreferencesKey("e2ee_priv_iv_$uid")

    fun generateEcdhKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return generator.generateKeyPair()
    }

    suspend fun getOrCreateIdentityKeys(context: Context, userId: String): IdentityKeyMaterial =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs = context.cryptoDataStore.data.first()
            val storedPub = prefs[pubKeyPref(userId)]
            val storedWrappedPriv = prefs[wrappedPrivKeyPref(userId)]
            val storedWrapIv = prefs[wrapIvPref(userId)]

            if (!storedPub.isNullOrBlank() && !storedWrappedPriv.isNullOrBlank() && !storedWrapIv.isNullOrBlank()) {
                runCatching {
                    val privateKeyBytes = unwrapWithKeystore(
                        Base64.decode(storedWrappedPriv, Base64.NO_WRAP),
                        Base64.decode(storedWrapIv, Base64.NO_WRAP)
                    )
                    val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
                    return@withContext IdentityKeyMaterial(publicKeyBase64 = storedPub, privateKey = privateKey)
                }
            }

            val keyPair = generateEcdhKeyPair()
            val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
            val (wrappedPriv, wrapIv) = wrapWithKeystore(keyPair.private.encoded)

            context.cryptoDataStore.edit { store ->
                store[pubKeyPref(userId)] = pubBase64
                store[wrappedPrivKeyPref(userId)] = Base64.encodeToString(wrappedPriv, Base64.NO_WRAP)
                store[wrapIvPref(userId)] = Base64.encodeToString(wrapIv, Base64.NO_WRAP)
            }
            IdentityKeyMaterial(publicKeyBase64 = pubBase64, privateKey = keyPair.private)
        }

    suspend fun clearIdentityKeys(context: Context, userId: String) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.cryptoDataStore.edit { store ->
                store.remove(pubKeyPref(userId))
                store.remove(wrappedPrivKeyPref(userId))
                store.remove(wrapIvPref(userId))
            }
            Unit
        }

    fun decodePublicKey(publicKeyBase64: String): PublicKey {
        val bytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    /**
     * Derives a 256-bit AES shared session key via ECDH + HKDF-SHA256 bound to coupleId.
     */
    fun deriveSharedSessionKey(
        myPrivateKey: PrivateKey,
        partnerPublicKeyBase64: String,
        coupleId: String
    ): SecretKey {
        val partnerPub = decodePublicKey(partnerPublicKeyBase64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(myPrivateKey)
        agreement.doPhase(partnerPub, true)
        val rawSharedSecret = agreement.generateSecret()

        val salt = "ForeverUs-E2EE-v1|$coupleId".toByteArray(StandardCharsets.UTF_8)
        val info = "AES-256-GCM-SessionKey|$coupleId".toByteArray(StandardCharsets.UTF_8)
        val derivedKeyBytes = hkdfSha256(ikm = rawSharedSecret, salt = salt, info = info, lengthBytes = 32)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    /**
     * Encrypts UTF-8 text using AES-256-GCM with fresh 96-bit IV and AAD binding.
     */
    fun encryptText(plaintext: String, sessionKey: SecretKey, aad: String): EncryptedPayload {
        return encryptBytes(plaintext.toByteArray(StandardCharsets.UTF_8), sessionKey, aad)
    }

    /**
     * Decrypts UTF-8 text using AES-256-GCM and verifies AAD + authentication tag.
     */
    fun decryptText(ciphertextBase64: String, ivBase64: String, sessionKey: SecretKey, aad: String): String {
        val plainBytes = decryptBytes(ciphertextBase64, ivBase64, sessionKey, aad)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    fun encryptBytes(plainBytes: ByteArray, sessionKey: SecretKey, aad: String): EncryptedPayload {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(plainBytes)
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptBytes(ciphertextBase64: String, ivBase64: String, sessionKey: SecretKey, aad: String): ByteArray {
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Computes a deterministic 60-digit Safety Number (12 groups of 5 digits) identical on both
     * partners' devices so they can verify zero MITM tampering.
     */
    fun computeSafetyNumber(publicKeyABase64: String, publicKeyBBase64: String): String {
        val sorted = listOf(publicKeyABase64.trim(), publicKeyBBase64.trim()).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("FOREVER_US_SAFETY_V1".toByteArray(StandardCharsets.UTF_8))
        sorted.forEach { digest.update(it.toByteArray(StandardCharsets.UTF_8)) }
        val hash1 = digest.digest()
        val hash2 = MessageDigest.getInstance("SHA-256").digest(hash1)
        val combined = hash1 + hash2

        val groups = (0 until 12).map { index ->
            val offset = index * 4
            val value = ((combined[offset].toLong() and 0xFF) shl 24) or
                ((combined[offset + 1].toLong() and 0xFF) shl 16) or
                ((combined[offset + 2].toLong() and 0xFF) shl 8) or
                (combined[offset + 3].toLong() and 0xFF)
            ((value and 0x7FFFFFFFL) % 100000L).toString().padStart(5, '0')
        }
        return groups.joinToString(" ")
    }

    fun shortFingerprint(publicKeyBase64: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBase64.toByteArray(StandardCharsets.UTF_8))
        return hash.take(8).joinToString(":") { "%02X".format(it) }
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, lengthBytes: Int): ByteArray {
        val extractMac = Mac.getInstance("HmacSHA256")
        extractMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        val okm = ByteArray(lengthBytes)
        var t = ByteArray(0)
        var copied = 0
        var counter = 1
        while (copied < lengthBytes) {
            expandMac.reset()
            expandMac.update(t)
            expandMac.update(info)
            expandMac.update(counter.toByte())
            t = expandMac.doFinal()
            val toCopy = minOf(t.size, lengthBytes - copied)
            System.arraycopy(t, 0, okm, copied, toCopy)
            copied += toCopy
            counter++
        }
        return okm
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun wrapWithKeystore(plainBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val wrapped = cipher.doFinal(plainBytes)
        return wrapped to cipher.iv
    }

    private fun unwrapWithKeystore(wrappedBytes: ByteArray, iv: ByteArray): ByteArray {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(wrappedBytes)
    }
}
