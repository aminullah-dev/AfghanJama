package com.afghanjama.desktop.data

import com.afghanjama.prefs.Settings
import java.io.File
import java.util.Properties

/**
 * [Settings] روی ویندوز — یک فایلِ `.properties` به‌ازای هر فضای‌نام،
 * کنارِ خودِ دیتابیس.
 *
 * چرا کنارِ دیتابیس: پشتیبان‌گیری از کارگاه یعنی کپیِ یک پوشه. اگر
 * تنظیمات جای دیگری می‌رفت (مثلاً `%APPDATA%`)، پشتیبانِ کارگاه نامِ
 * خودش و رمزِ اتصالش را در بر نمی‌گرفت و کسی هم نمی‌فهمید تا روزِ
 * بازگردانی.
 *
 * چرا `Properties`ِ جاوا: در خودِ JDK است و `jpackage` هیچ ماژولِ
 * اضافه‌ای نمی‌خواهد. با `store` و `load`ِ رشته‌ایِ UTF-8 نامِ فارسیِ
 * کارگاه هم سالم می‌مانَد.
 *
 * نوشتن **بلافاصله** روی دیسک می‌نشیند. کندیِ ناچیزی دارد ولی معنایش
 * این است که اگر برق کارگاه برود، تنظیماتی که کاربر همین حالا ذخیره
 * کرده از دست نمی‌رود.
 */
class FileSettings(private val dir: File) : Settings {

    init {
        dir.mkdirs()
    }

    private val cache = mutableMapOf<String, Properties>()

    private fun fileOf(name: String) = File(dir, "$name.properties")

    @Synchronized
    private fun props(name: String): Properties = cache.getOrPut(name) {
        Properties().apply {
            val f = fileOf(name)
            if (f.exists()) f.reader(Charsets.UTF_8).use { load(it) }
        }
    }

    @Synchronized
    private fun write(name: String) {
        fileOf(name).writer(Charsets.UTF_8).use {
            props(name).store(it, "khayatyar")
        }
    }

    @Synchronized
    override fun getString(file: String, key: String, def: String): String =
        props(file).getProperty(key) ?: def

    @Synchronized
    override fun putString(file: String, key: String, value: String) {
        props(file).setProperty(key, value)
        write(file)
    }

    @Synchronized
    override fun getBoolean(file: String, key: String, def: Boolean): Boolean =
        props(file).getProperty(key)?.toBooleanStrictOrNull() ?: def

    @Synchronized
    override fun putBoolean(file: String, key: String, value: Boolean) {
        props(file).setProperty(key, value.toString())
        write(file)
    }

    @Synchronized
    override fun getLong(file: String, key: String, def: Long): Long =
        props(file).getProperty(key)?.toLongOrNull() ?: def

    @Synchronized
    override fun putLong(file: String, key: String, value: Long) {
        props(file).setProperty(key, value.toString())
        write(file)
    }

    @Synchronized
    override fun remove(file: String, key: String) {
        props(file).remove(key)
        write(file)
    }
}
