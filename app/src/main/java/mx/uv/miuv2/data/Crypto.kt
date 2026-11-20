package mx.uv.miuv2.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Guarda la cuenta cifrada con el Keystore de Android (la contraseña nunca queda en texto plano). */
object Vault {
    private const val ALIAS = "miuv2_cuenta"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }
    private fun prefs(c: Context) = c.getSharedPreferences("vault", Context.MODE_PRIVATE)

    fun save(c: Context, user: String, pass: String) {
        val ci = Cipher.getInstance("AES/GCM/NoPadding"); ci.init(Cipher.ENCRYPT_MODE, key())
        val enc = ci.doFinal(pass.toByteArray())
        prefs(c).edit().putString("u", user)
            .putString("p", Base64.encodeToString(ci.iv + enc, Base64.NO_WRAP)).apply()
    }
    fun user(c: Context): String? = prefs(c).getString("u", null)
    fun pass(c: Context): String? = runCatching {
        val raw = Base64.decode(prefs(c).getString("p", null) ?: return null, Base64.NO_WRAP)
        val ci = Cipher.getInstance("AES/GCM/NoPadding")
        ci.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(ci.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()
    fun clear(c: Context) = prefs(c).edit().clear().apply()
}

/** Réplica del cifrado que usa la página de Eminus 4 antes de enviar la contraseña (CryptoJS PBKDF2 + AES-CBC). */
object EminusCipher {
    private const val KEY = "ow7dxys8glfor9tnc2ansdfo1etkgjcv"
    fun encrypt(plain: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(16).also(rnd::nextBytes)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(PBEKeySpec(KEY.toCharArray(), salt, 100, 256)).encoded
        val ci = Cipher.getInstance("AES/CBC/PKCS5Padding")
        ci.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), IvParameterSpec(iv))
        return Base64.encodeToString(salt + iv + ci.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }
}
