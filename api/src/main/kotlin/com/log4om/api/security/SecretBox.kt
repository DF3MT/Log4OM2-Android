package com.log4om.api.security

import com.log4om.api.config.Log4omProperties
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class SecretBox(props: Log4omProperties) {
    private val keyBytes: ByteArray = normalizeKey(props.encryption.key)

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val out = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(encrypted, 0, out, iv.size, encrypted.size)
        return Base64.getEncoder().encodeToString(out)
    }

    fun decrypt(blob: String): String {
        if (blob.isBlank()) return ""
        val raw = Base64.getDecoder().decode(blob)
        require(raw.size > 12) { "Invalid ciphertext" }
        val iv = raw.copyOfRange(0, 12)
        val encrypted = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun normalizeKey(raw: String): ByteArray {
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        return when {
            bytes.size == 32 -> bytes
            bytes.size == 16 || bytes.size == 24 -> bytes
            else -> MessageDigest.getInstance("SHA-256").digest(bytes)
        }
    }
}
