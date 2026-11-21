package com.example

import com.example.crypto.AppLockManager
import com.example.crypto.E2EECryptoEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.GeneralSecurityException
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class E2EECryptoEngineTest {

    @Test
    fun ecdhHkdfAndAesGcmRoundTripBetweenTwoPartners() {
        val aliceKeys = E2EECryptoEngine.generateEcdhKeyPair()
        val bobKeys = E2EECryptoEngine.generateEcdhKeyPair()

        val alicePubB64 = Base64.getEncoder().encodeToString(aliceKeys.public.encoded)
        val bobPubB64 = Base64.getEncoder().encodeToString(bobKeys.public.encoded)
        val coupleId = "couple_forever_us_test_01"

        val aliceSessionKey = E2EECryptoEngine.deriveSharedSessionKey(aliceKeys.private, bobPubB64, coupleId)
        val bobSessionKey = E2EECryptoEngine.deriveSharedSessionKey(bobKeys.private, alicePubB64, coupleId)

        assertArrayEquals(aliceSessionKey.encoded, bobSessionKey.encoded)

        val secretMessage = "Happy Anniversary! Meet me under the stars at 8pm."
        val aad = "$coupleId|alice_uid"
        val payload1 = E2EECryptoEngine.encryptText(secretMessage, aliceSessionKey, aad)
        val payload2 = E2EECryptoEngine.encryptText(secretMessage, aliceSessionKey, aad)

        // Random 96-bit IV guarantees semantic security (different ciphertexts for same plaintext)
        assertNotEquals(payload1.ivBase64, payload2.ivBase64)
        assertNotEquals(payload1.ciphertextBase64, payload2.ciphertextBase64)
        assertFalse(payload1.ciphertextBase64.contains("Anniversary"))

        val decrypted = E2EECryptoEngine.decryptText(
            payload1.ciphertextBase64,
            payload1.ivBase64,
            bobSessionKey,
            aad
        )
        assertEquals(secretMessage, decrypted)
    }

    @Test(expected = GeneralSecurityException::class)
    fun aesGcmRejectsTamperedAad() {
        val aliceKeys = E2EECryptoEngine.generateEcdhKeyPair()
        val bobKeys = E2EECryptoEngine.generateEcdhKeyPair()
        val bobPubB64 = Base64.getEncoder().encodeToString(bobKeys.public.encoded)
        val coupleId = "couple_aad_test"

        val sessionKey = E2EECryptoEngine.deriveSharedSessionKey(aliceKeys.private, bobPubB64, coupleId)
        val encrypted = E2EECryptoEngine.encryptText("Private whisper", sessionKey, "$coupleId|alice")

        // Attempting decryption with forged sender AAD must throw AEADBadTagException
        E2EECryptoEngine.decryptText(encrypted.ciphertextBase64, encrypted.ivBase64, sessionKey, "$coupleId|eve")
    }

    @Test
    fun safetyNumberIsDeterministicAndSymmetric() {
        val aliceKeys = E2EECryptoEngine.generateEcdhKeyPair()
        val bobKeys = E2EECryptoEngine.generateEcdhKeyPair()
        val alicePub = Base64.getEncoder().encodeToString(aliceKeys.public.encoded)
        val bobPub = Base64.getEncoder().encodeToString(bobKeys.public.encoded)

        val safetyA = E2EECryptoEngine.computeSafetyNumber(alicePub, bobPub)
        val safetyB = E2EECryptoEngine.computeSafetyNumber(bobPub, alicePub)

        assertEquals(safetyA, safetyB)
        assertEquals(12, safetyA.split(" ").size)
        assertTrue(safetyA.split(" ").all { it.length == 5 && it.all(Char::isDigit) })
    }

    @Test
    fun pbkdf2PinHashingAndConstantTimeComparison() {
        val salt = ByteArray(16) { it.toByte() }
        val hash1 = AppLockManager.hashPin("246810", salt)
        val hash2 = AppLockManager.hashPin("246810", salt)
        val wrongHash = AppLockManager.hashPin("000000", salt)

        assertTrue(AppLockManager.constantTimeEquals(hash1, hash2))
        assertFalse(AppLockManager.constantTimeEquals(hash1, wrongHash))
    }
}
