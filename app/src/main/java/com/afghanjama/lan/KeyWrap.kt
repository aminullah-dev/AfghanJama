package com.afghanjama.lan

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * انبارِ امنِ کلیدِ «کارگاهِ وصل» روی اندروید.
 *
 * کلیدِ ۳۲ بایتیِ دستگاه **خام روی دیسک نمی‌رود**: با یک کلیدِ AES-256 که در
 * Android Keystore ساخته شده و هرگز از آن بیرون نمی‌آید، با AES-GCM پیچیده
 * می‌شود و فقط متنِ رمز در تنظیماتِ خصوصیِ برنامه می‌ماند. کسی که فایل‌های
 * برنامه را بردارد (پشتیبانِ اندروید، گوشیِ روت‌شده) بی همان Keystore کلید را
 * ندارد. nonce را خودِ Keystore می‌سازد و کنارِ متنِ رمز نگه داشته می‌شود.
 *
 * هر شکستی (Keystore در دسترس نیست، کلید عوض شده) یعنی «نیست»: گوشی پیامِ
 * «دوباره جفت کنید» می‌گیرد، نه اینکه کلید را جای ناامن بگذارد.
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun put(name: String, value: ByteArray): Boolean = runCatching {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, wrapKey())
        val iv = c.iv
        val sealed = c.doFinal(value)
        val blob = byteArrayOf(iv.size.toByte()) + iv + sealed
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
    }.getOrDefault(false)

    override fun get(name: String): ByteArray? = runCatching {
        val blob = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val n = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + n)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv))
        c.doFinal(blob, 1 + n, blob.size - 1 - n)
    }.getOrNull()

    override fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    private companion object {
        const val FILE = "hub_secrets"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "khayatyar_hub_wrap"
        const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
