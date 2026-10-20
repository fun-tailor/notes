package com.swan.notes.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class SyncCryptoTest {

    @Test
    fun keyCheckRoundTrips() {
        val salt = SyncCrypto.newSalt()
        val key = SyncCrypto.deriveKey("口令-123", salt)
        val check = SyncCrypto.keyCheck(key)

        assertTrue(SyncCrypto.verifyKeyCheck(key, check))

        val wrongKey = SyncCrypto.deriveKey("wrong", salt)
        assertFalse(SyncCrypto.verifyKeyCheck(wrongKey, check))
    }

    @Test
    fun encryptDecryptWithSaltHeader() {
        val salt = SyncCrypto.newSalt()
        val key = SyncCrypto.deriveKey("passphrase", salt)
        val payload = SyncCrypto.encryptText("正文\n第二行", key, salt)

        assertTrue(payload.isNotEmpty())
        assertTrue(SyncCrypto.readSalt(payload).contentEquals(salt))
        assertEquals("正文\n第二行", SyncCrypto.decryptText(payload, key))
        assertEquals("正文\n第二行", SyncCrypto.decryptWithPassphrase(payload, "passphrase"))
    }

    @Test
    fun samePlaintextDifferentIvProducesDifferentCiphertext() {
        val salt = SyncCrypto.newSalt()
        val key = SyncCrypto.deriveKey("passphrase", salt)
        val first = SyncCrypto.encryptText("同样的正文", key, salt)
        val second = SyncCrypto.encryptText("同样的正文", key, salt)
        assertNotEquals(first, second)
    }

    @Test
    fun differentSaltsProduceDifferentCiphertext() {
        val firstSalt = SyncCrypto.newSalt()
        val secondSalt = SyncCrypto.newSalt()
        val first = SyncCrypto.encryptText("正文", SyncCrypto.deriveKey("passphrase", firstSalt), firstSalt)
        val second = SyncCrypto.encryptText("正文", SyncCrypto.deriveKey("passphrase", secondSalt), secondSalt)
        assertNotEquals(first, second)
    }

    @Test
    fun tamperedCiphertextFails() {
        val salt = SyncCrypto.newSalt()
        val key = SyncCrypto.deriveKey("passphrase", salt)
        val payload = SyncCrypto.encryptText("正文", key, salt)
        val bytes = SyncCrypto.fromBase64(payload)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()

        try {
            SyncCrypto.decryptText(SyncCrypto.base64(bytes), key)
            fail("tampered payload should not decrypt")
        } catch (expected: AEADBadTagException) {
            assertTrue(true)
        }
    }

    @Test
    fun keyDerivationIsDeterministic() {
        val salt = SyncCrypto.newSalt()
        val first = SyncCrypto.deriveKey("passphrase", salt).encoded
        val second = SyncCrypto.deriveKey("passphrase", salt).encoded
        assertTrue(first.contentEquals(second))
    }
}
