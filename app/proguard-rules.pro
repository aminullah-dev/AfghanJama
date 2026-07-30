# قاعده‌های نگه‌داری برای نسخهٔ release.
#
# فعلاً minify خاموش است (دلیلش در build.gradle.kts نوشته شده)، ولی این
# فایل از حالا هست تا وقتی روشن شد، پایه‌اش آماده باشد.
#
# چیزهایی که در این اپ با بازتاب کار می‌کنند و اگر حذف شوند در زمانِ
# اجرا می‌شکنند — نه هنگامِ ساخت:

# Room — کلاس‌های تولیدشده و موجودیت‌ها با نام پیدا می‌شوند
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# zxing — اسکنر QR با نام کلاس کار می‌کند
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# Compose — نگه‌داشتنِ توابعِ Composable
-keep class androidx.compose.runtime.** { *; }

# مدل‌های داده‌ای که در پشتیبان‌گیری سریال می‌شوند
-keep class com.afghanjama.data.entities.** { *; }
