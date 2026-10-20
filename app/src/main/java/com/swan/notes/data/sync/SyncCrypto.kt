package com.swan.notes.data.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object SyncCrypto {

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    private val secureRandom = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun fromBase64(value: String): ByteArray = Base64.getDecoder().decode(value)

    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = SYNC_PBKDF2_ITERATIONS): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun keyCheck(key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, ByteArray(IV_BYTES)),
        )
        val cipherText = cipher.doFinal(SYNC_FORMAT_NAME.toByteArray(Charsets.UTF_8))
        return base64(cipherText)
    }

    fun verifyKeyCheck(key: SecretKey, keyCheck: String): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, ByteArray(IV_BYTES)),
        )
        return runCatching {
            val plain = cipher.doFinal(fromBase64(keyCheck))
            String(plain, Charsets.UTF_8) == SYNC_FORMAT_NAME
        }.getOrDefault(false)
    }

    fun encryptText(plain: String, key: SecretKey, salt: ByteArray = newSalt()): String {
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(salt.size + iv.size + cipherText.size)
        salt.copyInto(payload, 0)
        iv.copyInto(payload, salt.size)
        cipherText.copyInto(payload, salt.size + iv.size)
        return base64(payload)
    }

    fun decryptText(payloadBase64: String, key: SecretKey): String {
        val payload = fromBase64(payloadBase64.trim())
        require(payload.size > SALT_BYTES + IV_BYTES) { "加密内容不完整" }
        val iv = payload.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val cipherText = payload.copyOfRange(SALT_BYTES + IV_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    /**
     * 解密时以文件头里的盐为准；清单里的盐只用于校验/展示。
     */
    fun readSalt(payloadBase64: String): ByteArray {
        val payload = fromBase64(payloadBase64.trim())
        require(payload.size >= SALT_BYTES) { "加密内容不完整" }
        return payload.copyOfRange(0, SALT_BYTES)
    }

    fun decryptWithPassphrase(
        payloadBase64: String,
        passphrase: String,
        iterations: Int = SYNC_PBKDF2_ITERATIONS,
    ): String {
        val salt = readSalt(payloadBase64)
        val key = deriveKey(passphrase, salt, iterations)
        return decryptText(payloadBase64, key)
    }
}
