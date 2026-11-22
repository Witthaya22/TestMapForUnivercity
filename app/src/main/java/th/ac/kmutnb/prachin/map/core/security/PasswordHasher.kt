package th.ac.kmutnb.prachin.map.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2, so the passwords kept on the phone cannot be read back or looked up in
 * a table. HmacSHA1 because it is the variant every Android version from minSdk 24 has.
 *
 * Stored as `iterations:salt:hash` in hex (java.util.Base64 is API 26; minSdk is 24), so the cost can be raised later without
 * locking out existing accounts.
 */
object PasswordHasher {
    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return encode(ITERATIONS, salt, derive(password, salt, ITERATIONS))
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = fromHex(parts[1]) ?: return false
        val expected = fromHex(parts[2]) ?: return false
        // Constant-time comparison: a byte-by-byte early exit leaks how much matched.
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    /** A 6-digit one-time code for resetting a password. */
    fun otp(): String = (random.nextInt(900_000) + 100_000).toString()

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(iterations: Int, salt: ByteArray, hash: ByteArray) = "$iterations:${toHex(salt)}:${toHex(hash)}"

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun fromHex(text: String): ByteArray? {
        if (text.length % 2 != 0) return null
        return runCatching { ByteArray(text.length / 2) { i -> text.substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
    }
}
