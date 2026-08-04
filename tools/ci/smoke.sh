#!/usr/bin/env bash
#
# آزمونِ راه‌اندازیِ اپِ اندروید روی شبیه‌ساز.
#
# **چرا لازم شد:** تا امروز CI فقط ثابت می‌کرد APK *ساخته* می‌شود. همان
# شکافی که سرِ ویندوز دیدیم — بسته سبز بود و برنامه روی پی‌سی بالا
# نمی‌آمد — برای اندروید هم باز بود، و اپ روی گوشیِ واقعی سرِ باز شدن
# کرش کرد بی‌آنکه هیچ بررسی‌ای چیزی بگوید.
#
# اینجا اپ واقعاً نصب و باز می‌شود. اگر بترکد، **متنِ کاملِ کرش** در لاگِ
# CI چاپ می‌شود — که تنها چیزی است که تشخیص را از حدس جدا می‌کند.
set -uo pipefail

PKG="com.afghanjama"

echo "── نصبِ APK روی شبیه‌ساز"
./gradlew --no-daemon :app:installDebug || exit 1

echo "── پاک کردنِ لاگِ قبلی"
adb logcat -c || true

echo "── باز کردنِ اپ"
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

# ۲۵ ثانیه: باز شدنِ Room، مهاجرت‌ها و اولین ترکیبِ Compose روی شبیه‌سازِ
# CI کند است. کمتر از این هشدارِ نادرست می‌دهد.
sleep 25

echo "── وارسیِ کرش"
CRASH=$(adb logcat -d -b crash 2>/dev/null || true)
FATAL=$(adb logcat -d 2>/dev/null | grep -A80 "FATAL EXCEPTION" || true)

if [ -n "$CRASH" ] || [ -n "$FATAL" ]; then
    echo "════════════ اپ کرش کرد ════════════"
    # هر دو بافر چاپ می‌شوند: گاهی یکی خالی است و آن یکی متن را دارد.
    [ -n "$CRASH" ] && { echo "--- بافرِ crash ---"; echo "$CRASH"; }
    [ -n "$FATAL" ] && { echo "--- FATAL EXCEPTION ---"; echo "$FATAL"; }
    exit 1
fi

# نبودنِ کرش کافی نیست: اپ ممکن است بی‌صدا بسته شده باشد.
PID=$(adb shell pidof "$PKG" 2>/dev/null | tr -d '\r')
if [ -z "$PID" ]; then
    echo "✗ اپ زنده نیست و کرشی هم ثبت نشده — یعنی بی‌صدا بسته شد."
    echo "── آخرین ۲۰۰ خطِ لاگ برای سرنخ:"
    adb logcat -d | tail -200
    exit 1
fi

echo "✓ اپ ۲۵ ثانیه سرِ پا ماند (pid $PID) — راه‌اندازی سالم است."
