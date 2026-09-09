#!/usr/bin/env bash
#
# امضا و notarizeِ خیاط‌یار برای مک — **روی خودِ مکِ شما، نه در CI.**
#
# چرا اینجا و نه در GitHub: گواهیِ Developer ID کلیدِ خصوصی دارد و اگر
# بیرون برود، هر کسی می‌تواند بدافزاری بسازد که به نامِ شما امضا شده و
# روی هر مکی **بی هیچ هشداری** باز می‌شود. همان دلیلی که `*.jks` و
# `*.pfx` در `.gitignore` هستند. پس کلید از این کمپیوتر بیرون نمی‌رود و
# CI فقط DMGِ بی‌امضا می‌سازد.
#
# پیش‌نیازها (یک بار، توضیحِ کاملشان در README.md کنارِ همین فایل):
#   ۱. گواهیِ «Developer ID Application» در جاکلیدی
#   ۲. رمزِ ویژهٔ اپل در جاکلیدی با نامِ نمایه‌ای که اینجا می‌آید
#
# اجرا:
#   ./tools/macos-signing/sign-and-notarize.sh "Developer ID Application: NAME (TEAMID)"
#
# **بندهای ۰ تا ۴ روی مک آزموده شده‌اند؛ بندهای ۵ و ۶ نه.** ساخت، امضای
# ۴۶ فایلِ بومی، امضای بسته، `--verify --strict` و ساخت و امضای DMG همه
# روی macOS 26.6 گرفتند. `notarytool` و `stapler` آزموده نشدند چون
# گواهیِ Developer ID روی آن مک نبود. اگر آن دو چیزی گفتند، متنِ خطا را
# بردارید و بیاورید.

# `-u` هم هست: نامِ متغیرِ غلط باید بترکد، نه اینکه بی‌صدا خالی بماند و
# مثلاً `codesign` را روی مسیرِ `/` بیندازد.
set -euo pipefail

IDENTITY="${1:-}"
# نامِ نمایهٔ جاکلیدی که `notarytool store-credentials` ساخته.
PROFILE="${NOTARY_PROFILE:-KhayatYar}"

if [ -z "$IDENTITY" ]; then
  echo "استفاده: $0 \"Developer ID Application: NAME (TEAMID)\"" >&2
  echo >&2
  echo "نامِ دقیقِ گواهی‌های موجود:" >&2
  security find-identity -v -p codesigning >&2 || true
  exit 2
fi

cd "$(dirname "$0")/../.."
REPO="$PWD"

# ---------------------------------------------------------------------
# ۰. پیش‌وارسیِ دو پیش‌نیاز — **پیش از هر کارِ سنگین.**
#
# این بند بعد از اولین اجرای واقعی روی مک اضافه شد. بی آن، نبودنِ نمایهٔ
# جاکلیدی تازه در بندِ ۵ معلوم می‌شد: یعنی بعد از ساختِ بسته، امضای ۴۶
# فایل با مهرِ زمانیِ شبکه، ساختِ یک DMGِ ۱۵۰ مگابایتی و امضای آن. همهٔ
# آن کار برای رسیدن به خطایی که از ثانیهٔ اول معلوم بود.
#
# هر دو با خودِ ابزار سنجیده می‌شوند و نه با حدس: `find-identity` همان
# فهرستی است که `codesign` از آن برمی‌دارد، و `history` همان جاکلیدی‌ای
# که `notarytool` می‌خوانَد.
# ---------------------------------------------------------------------
if ! security find-identity -v -p codesigning | grep -qF "$IDENTITY"; then
  echo "✗ گواهیِ «${IDENTITY}» در جاکلیدی نیست." >&2
  echo >&2
  echo "آنچه هست:" >&2
  security find-identity -v -p codesigning >&2 || true
  echo >&2
  echo "اگر «Developer ID Application» در فهرست نیست، هنوز ساخته نشده؛" >&2
  echo "ساختنش در README.md کنارِ همین فایل، بندِ پیش‌نیازها." >&2
  exit 2
fi

if ! xcrun notarytool history --keychain-profile "$PROFILE" >/dev/null 2>&1; then
  echo "✗ نمایهٔ جاکلیدیِ «${PROFILE}» ساخته نشده." >&2
  echo >&2
  echo "یک بار بسازیدش (رمزِ ویژه لازم است، نه رمزِ اصلیِ اپل):" >&2
  echo "    xcrun notarytool store-credentials $PROFILE \\" >&2
  echo "      --apple-id \"ایمیلِ اپلتان\" --team-id \"TEAMID\" --password \"رمزِ-ویژه\"" >&2
  echo >&2
  echo "توضیحِ کاملش در README.md کنارِ همین فایل." >&2
  exit 2
fi

# ---------------------------------------------------------------------
# ۱. بسته را بساز
#
# `createDistributable` و نه `packageDmg`: DMG را خودمان **بعد از امضا**
# می‌سازیم. اگر برعکسش کنیم، `packageDmg` دوباره `.app` را از نو
# می‌سازد و امضا را دور می‌ریزد — و چون خروجی‌اش هم ساخته می‌شود، هیچ
# خطایی نمی‌بینید. یک DMGِ سالمِ بی‌امضا، که بدترین حالت است.
# ---------------------------------------------------------------------
echo "==> ساختِ بسته"
./gradlew --no-daemon :desktop:createDistributable

APP=$(find "$REPO/desktop/build/compose/binaries" -maxdepth 4 -name 'KhayatYar.app' -type d | head -1)
if [ -z "$APP" ]; then
  echo "✗ KhayatYar.app پیدا نشد — ساخت چیزی تولید نکرده است." >&2
  exit 1
fi
echo "    $APP"

VERSION=$(sed -n 's/^appVersion=//p' "$REPO/gradle.properties" | tr -d '[:space:]')
if [ -z "$VERSION" ]; then
  echo "✗ appVersion در gradle.properties نبود." >&2
  exit 1
fi

# ---------------------------------------------------------------------
# ۲. مجوزها (entitlements)
#
# **هیچ‌کدام اختیاری نیستند و هر کدام یک خرابیِ مشخص را می‌بندند.**
# برنامه‌ای که JVM دارد بدونِ این‌ها امضا می‌شود، Gatekeeper هم قبولش
# می‌کند، و بعد سرِ اجرا می‌میرد:
#
#   allow-jit + allow-unsigned-executable-memory
#       JVM کدِ ماشین را در زمانِ اجرا می‌نویسد و اجرا می‌کند. بی
#       این‌ها، hardened runtime همان لحظه فرایند را می‌کشد.
#
#   disable-library-validation
#       **مهم‌ترینشان برای این برنامه.** Skiko (موتورِ رسمِ Compose)
#       کتابخانهٔ بومی‌اش را سرِ اجرا از داخلِ jar بیرون می‌کشد و در
#       پوشهٔ موقت بار می‌کند. آن فایل بخشی از بستهٔ امضاشده نیست، پس
#       با اعتبارسنجیِ کتابخانه بار نمی‌شود و پنجره اصلاً باز نمی‌شود.
#
#   allow-dyld-environment-variables
#       لانچرِ `jpackage` مسیرِ کتابخانه‌ها را با متغیرهای محیطی
#       می‌دهد.
# ---------------------------------------------------------------------
# فایلِ موقت با پسوند ساخته می‌شود، نه اینکه پسوند به رشتهٔ خروجیِ
# `mktemp` چسبانده شود — آن‌جوری فایلِ ساخته‌شده یکی است و فایلی که
# نوشته می‌شود یکی دیگر، و اولی هم پشتِ سر جا می‌مانَد.
ENT=$(mktemp -t khayatyar-entitlements)
cat > "$ENT" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
    <key>com.apple.security.cs.allow-dyld-environment-variables</key>
    <true/>
</dict>
</plist>
PLIST
trap 'rm -f "$ENT"' EXIT

# ---------------------------------------------------------------------
# ۳. امضا — از درون به بیرون
#
# **و چرا `--deep` اینجا نیست.** اپل خودش از ۲۰۱۹ منسوخش اعلام کرده و
# برای بستهٔ جاوا خطرناک هم هست: `--deep` مجوزها را به همه‌چیز یکسان
# می‌دهد و امضای اجزایی را که از قبل درست امضا شده‌اند بازنویسی می‌کند.
# روی بستهٔ `jpackage` نتیجه‌اش امضایی است که `codesign --verify` قبول
# می‌کند ولی notarize ردش می‌کند.
#
# پس هر فایلِ بومی جدا امضا می‌شود و بستهٔ بیرونی در آخر. ترتیب مهم
# است: امضای بسته امضای درونی‌ها را در برمی‌گیرد، پس اگر بعد از آن
# چیزی داخلش عوض شود، امضای بیرونی می‌شکند.
#
# `--force` لازم است چون بعضی کتابخانه‌ها با امضای سازنده‌شان می‌آیند و
# باید با گواهیِ ما دوباره امضا شوند.
# ---------------------------------------------------------------------
echo "==> امضای فایل‌های بومیِ داخلِ بسته"
# چرا `-print0` و `read -d ''`: مسیرها می‌توانند فاصله داشته باشند و
# حلقهٔ ساده روی فاصله می‌شکند.
# **چرا `file` و نه فهرستِ پسوندها.** نسخهٔ اولِ این حلقه دنبالِ
# `*.dylib` و `*.so` می‌گشت و JVMِ همراه را جا می‌انداخت: `runtime/…/bin/java`
# و ده‌ها فایلِ دیگر هم Mach-O هستند و پسوند ندارند. نتیجه‌اش امضایی
# است که `codesign --verify` قبول می‌کند و اپل سرِ notarize ردش
# می‌کند — یعنی خطا چند دقیقه دیرتر و از سرورِ اپل می‌آید، نه از
# اینجا.
count=0
while IFS= read -r -d '' f; do
  case "$(file -b "$f")" in
    *Mach-O*)
      codesign --force --timestamp --options runtime \
        --entitlements "$ENT" --sign "$IDENTITY" "$f"
      count=$((count + 1))
      ;;
  esac
done < <(find "$APP/Contents" -type f -print0)
echo "    $count فایلِ بومی امضا شد"

echo "==> امضای خودِ بسته"
codesign --force --timestamp --options runtime \
  --entitlements "$ENT" --sign "$IDENTITY" "$APP"

echo "==> وارسیِ امضا"
# `--strict` عمدی است: بی آن، خطاهایی که notarize بعداً می‌گیرد اینجا
# دیده نمی‌شوند و آن‌وقت باید یک رفت‌وبرگشتِ چنددقیقه‌ای با سرورِ اپل
# منتظر بمانید تا همان را بشنوید.
codesign --verify --deep --strict --verbose=2 "$APP"

# ---------------------------------------------------------------------
# ۴. DMG
#
# دستی و نه با `packageDmg`، به همان دلیلِ بندِ ۱.
#
# پوشهٔ میانی با یک میان‌بر به `/Applications` ساخته می‌شود — همان
# چیزی که کاربرِ مک انتظار دارد: پنجره باز می‌شود و برنامه را روی
# آن آیکن می‌کشد.
# ---------------------------------------------------------------------
DMG="$REPO/desktop/build/compose/binaries/main/dmg/KhayatYar-$VERSION.dmg"
STAGE=$(mktemp -d -t khayatyar-dmg)
# پوشهٔ میانی یک کپیِ کاملِ بسته است — چند صد مگابایت. اگر `hdiutil`
# بشکند، خطِ `rm -rf` پایین اجرا نمی‌شود و آن کپی در `/var/folders`
# می‌مانَد بی اینکه کسی خبردار شود. پس به همان `trap` سپرده می‌شود.
trap 'rm -f "$ENT"; rm -rf "$STAGE"' EXIT
mkdir -p "$(dirname "$DMG")"
rm -f "$DMG"

echo "==> ساختِ DMG"
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
hdiutil create -volname "KhayatYar $VERSION" \
  -srcfolder "$STAGE" -ov -format UDZO "$DMG"
rm -rf "$STAGE"

# خودِ DMG هم امضا می‌شود. بی این، Gatekeeper پیش از باز کردنِ تصویر
# هشدار می‌دهد — یعنی کاربر هشدار را می‌بیند حتی اگر برنامهٔ داخلش
# بی‌عیب امضا شده باشد.
echo "==> امضای DMG"
codesign --force --timestamp --sign "$IDENTITY" "$DMG"

# ---------------------------------------------------------------------
# ۵. notarize
#
# امضا تنها نیمهٔ کار است. مکِ نسخهٔ ۱۰.۱۵ به بالا برنامهٔ امضاشده ولی
# notarizeنشده را هم با هشدار باز می‌کند. تأییدِ اپل چند دقیقه طول
# می‌کشد؛ `--wait` تا آخرش می‌مانَد.
# ---------------------------------------------------------------------
echo "==> ارسال به اپل (چند دقیقه طول می‌کشد)"
if ! xcrun notarytool submit "$DMG" --keychain-profile "$PROFILE" --wait; then
  echo >&2
  echo "✗ مرحلهٔ اپل نگرفت — یا ردش کرد یا ارتباط برقرار نشد." >&2
  echo "  (پیامِ بالا می‌گوید کدام. اگر ردش کرده، دلیلِ دقیقش:)" >&2
  echo "    xcrun notarytool history --keychain-profile $PROFILE" >&2
  echo "    xcrun notarytool log <submission-id> --keychain-profile $PROFILE" >&2
  exit 1
fi

# ---------------------------------------------------------------------
# ۶. staple
#
# **این مرحله را نپرید.** بی آن، مکِ کاربر باید برای تأییدِ notarize به
# سرورِ اپل وصل شود. روی کمپیوترِ بی‌اینترنت — یعنی دقیقاً کارگاهی که
# این برنامه برایش نوشته شده — نتیجه‌اش همان هشداری است که قرار بود
# نباشد. `stapler` تأیید را **داخلِ خودِ فایل** می‌چسباند.
# ---------------------------------------------------------------------
echo "==> چسباندنِ تأیید به فایل"
xcrun stapler staple "$DMG"

echo "==> وارسیِ نهایی — همان چیزی که مکِ کاربر می‌بیند"
xcrun stapler validate "$DMG"
spctl --assess --type open --context context:primary-signature -vv "$DMG"

echo
echo "✓ آماده: $DMG"
