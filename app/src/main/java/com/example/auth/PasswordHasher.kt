package com.example.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic password hashing helper using PBKDF2-HMAC-SHA256 with a random 16-byte salt
 * and constant-time comparison. Plaintext passwords are never stored or logged.
 */
object PasswordHasher {
  private const val ALGORITHM = "PBKDF2WithHmacSHA256"
  private const val ITERATIONS = 120_000
  private const val KEY_LENGTH_BITS = 256
  private const val SALT_BYTES = 16

  fun hashPassword(password: String): Pair<String, String> {
    val salt = ByteArray(SALT_BYTES)
    SecureRandom().nextBytes(salt)
    val hash = deriveKey(password.toCharArray(), salt)
    val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
    val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
    return saltB64 to hashB64
  }

  fun verifyPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
    if (saltBase64.isBlank() || expectedHashBase64.isBlank()) return false
    return try {
      val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
      val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
      val actual = deriveKey(password.toCharArray(), salt)
      MessageDigest.isEqual(actual, expected)
    } catch (_: Exception) {
      false
    }
  }

  private fun deriveKey(passwordChars: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(passwordChars, salt, ITERATIONS, KEY_LENGTH_BITS)
    return try {
      SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }
}
