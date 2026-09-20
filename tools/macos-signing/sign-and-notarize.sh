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
# **این اسکریپت تا آخر روی مک اجرا شده و اپل بسته را پذیرفته.**
# ساخت، امضای ۴۶ فایلِ بومی و سه فایلِ داخلِ jar، امضای بسته، ارسال به
# اپل، و مهرِ تأیید — همه روی macOS 26.6 گرفتند و خروجی‌اش با
# `spctl` سنجیده شد: `accepted, source=Notarized Developer ID`.
#
# **یک هشدار دربارهٔ زمان:** مرحلهٔ اپل معمولاً چند دقیقه است ولی
# تضمینی نیست؛ در همان اجرای واقعی یک بار ۱۷ دقیقه طول کشید و بارِ
# بعدی ۱ ساعت و ۴۸ دقیقه. `--wait` تا آخرش می‌مانَد. و چون حالا دو بار
# به اپل می‌رود (دلیلش در بندِ ۴)، این زمان دو برابر می‌شود.

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
# ---------------------------------------------------------------------
# **اول داخلِ jarها — و این را اپل به ما یاد داد.**
#
# اولین ارسالِ واقعی `Invalid` برگشت با سه خطا، هر سه از یک جنس:
#
#     …/skiko-awt-runtime-macos-arm64-….jar/libskiko-macos-x64.dylib
#     …/sqlite-bundled-jvm-….jar/natives/osx_x64/libsqliteJni.dylib
#     …/sqlite-bundled-jvm-….jar/natives/osx_arm64/libsqliteJni.dylib
#     "The binary is not signed with a valid Developer ID certificate."
#
# سرویسِ اپل jarها را **باز می‌کند** و هر Mach-Oی داخلشان را هم
# می‌سنجد. حلقهٔ پایین روی فایل‌های روی دیسک راه می‌رود، پس این سه‌تا
# را اصلاً نمی‌دید — نه خطایی، نه هشداری. `codesign --verify --deep
# --strict` هم سبز بود، چون برای او jar فقط یک فایلِ داده است.
#
# ترتیب مهم است: jar باید **پیش از** امضای بسته دست‌کاری شود، وگرنه
# مهرِ بیرونی می‌شکند.
#
# هر jar باز می‌شود و محتوایش با `file` سنجیده می‌شود، نه با پسوند. در
# همین بسته `sqlite-bundled-jvm` چهار فایلِ بومی دارد ولی دوتاشان ELFِ
# لینوکس‌اند؛ امضایشان هم بی‌معنی است و هم شکست می‌خورد. `file` آن دو
# را خودش کنار می‌گذارد.
#
# نوشتنِ دوباره با `zip` روی همان jar است و نه ساختِ jarِ نو: فقط همان
# یک ورودی جایگزین می‌شود و بقیهٔ بایگانی — از جمله `META-INF` — دست
# نخورده می‌مانَد.
# ---------------------------------------------------------------------
echo "==> امضای فایل‌های بومیِ داخلِ jarها"
jarcount=0
UNJAR=$(mktemp -d -t khayatyar-jars)
trap 'rm -f "$ENT"; rm -rf "$UNJAR"' EXIT
while IFS= read -r -d '' jar; do
  d="$UNJAR/$(basename "$jar" .jar)"
  rm -rf "$d"; mkdir -p "$d"
  unzip -qq -o "$jar" -d "$d" 2>/dev/null || continue
  while IFS= read -r -d '' inner; do
    case "$(file -b "$inner")" in
      *Mach-O*)
        codesign --force --timestamp --options runtime \
          --entitlements "$ENT" --sign "$IDENTITY" "$inner"
        # مسیرِ نسبی لازم است تا `zip` همان ورودی را جایگزین کند و
        # ورودیِ تازه‌ای با مسیرِ مطلق نسازد.
        rel="${inner#"$d"/}"
        ( cd "$d" && zip -q "$jar" "$rel" )
        echo "    $(basename "$jar") ← $rel"
        jarcount=$((jarcount + 1))
        ;;
    esac
  done < <(find "$d" -type f ! -name '*.class' -print0)
  rm -rf "$d"
done < <(find "$APP/Contents" -type f -name '*.jar' -print0)
echo "    $jarcount فایلِ بومیِ داخلِ jar امضا شد"

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
# فرستادن به اپل و خواندنِ **وضعیت**، نه کدِ خروج.
#
# `notarytool submit --wait` وقتی اپل بسته را **رد** می‌کند هم با کدِ
# صفر برمی‌گردد. کدِ خروج فقط می‌گوید «گفت‌وگو با سرور انجام شد»، نه
# «بسته پذیرفته شد». در اولین اجرای واقعی همین شد: وضعیت `Invalid`
# بود، `if` نگرفت، و اسکریپت رفت سراغِ `stapler` — که آن‌وقت با
# `Error 65` مرد. یعنی پیامی که کاربر می‌دید دربارهٔ `stapler` بود، نه
# دربارهٔ سه dylibِ امضانشده که علتِ واقعی بودند.
#
# پس `status: Accepted` صریح خوانده می‌شود، و اگر نبود، خودِ گزارشِ اپل
# همین‌جا چاپ می‌شود — نه اینکه کاربر را دنبالِ دستورِ بعدی بفرستیم.
#
# تابع است چون دو بار لازم می‌شود: یک بار برای خودِ برنامه و یک بار
# برای DMG. دلیلش در بندِ ۴ آمده.
# ---------------------------------------------------------------------
SUBMIT_OUT=$(mktemp -t khayatyar-notary)
trap 'rm -f "$ENT" "$SUBMIT_OUT" "$APPZIP"; rm -rf "$UNJAR" "$STAGE"' EXIT

notarize() {
  local what="$1"
  xcrun notarytool submit "$what" --keychain-profile "$PROFILE" --wait 2>&1 | tee "$SUBMIT_OUT"

  if grep -q "status: Accepted" "$SUBMIT_OUT"; then
    return 0
  fi

  local subid
  subid=$(sed -n 's/.*id: \([0-9a-f][0-9a-f-]\{30,\}\).*/\1/p' "$SUBMIT_OUT" | head -1)
  echo >&2
  echo "✗ اپل نپذیرفت: $what" >&2
  if [ -n "$subid" ]; then
    echo >&2
    echo "گزارشِ اپل برای $subid:" >&2
    xcrun notarytool log "$subid" --keychain-profile "$PROFILE" >&2 || true
  else
    echo "  شناسهٔ ارسال در خروجی نبود — یعنی چیزی اصلاً به اپل نرسید." >&2
    echo "  پیامِ بالا علتش را می‌گوید (معمولاً شبکه یا اعتبارنامه)." >&2
  fi
  exit 1
}

# ---------------------------------------------------------------------
# ۴. **خودِ برنامه هم notarize و مهر می‌شود — نه فقط DMG.**
#
# این بند بعد از اولین اجرای موفق اضافه شد، چون همان اجرا یک شکافِ
# واقعی نشان داد. DMG مهرِ تأیید داشت و Gatekeeper قبولش می‌کرد، ولی:
#
#     $ xcrun stapler validate "/Volumes/KhayatYar 1.7.0/KhayatYar.app"
#     KhayatYar.app does not have a ticket stapled to it.
#
# تا وقتی برنامه **داخلِ** DMG است مهرِ خودِ DMG پوششش می‌دهد. ولی
# کاربرِ مک کارِ دیگری می‌کند: برنامه را از پنجرهٔ DMG روی
# `/Applications` می‌کشد. آن نسخهٔ کپی‌شده دیگر هیچ مهری ندارد، پس
# مک باید برای تأییدش به سرورِ اپل وصل شود.
#
# **و این دقیقاً همان چیزی است که بندِ `stapler` قرار بود جلویش را
# بگیرد:** روی کمپیوترِ بی‌اینترنتِ کارگاه، همان هشدار برمی‌گردد — این
# بار نه سرِ باز کردنِ DMG، بلکه سرِ اجرای برنامه‌ای که نصب شده و
# کار می‌کرده.
#
# پس ترتیب عوض شد: اول خودِ `.app` به اپل می‌رود و مهر می‌خورد، و DMG
# **بعد از آن** و از روی همان نسخهٔ مهرخورده ساخته می‌شود.
#
# هزینه‌اش یک رفت‌وبرگشتِ اضافه با اپل است. سنجیده شد که مهر امضا را
# نمی‌شکند: `codesign --verify --deep --strict` بعد از `stapler` هم
# سبز است.
#
# `ditto -c -k --keepParent` و نه `zip`: پیوندهای نمادین و فراداده‌های
# مکِ داخلِ بسته باید سالم بمانند، وگرنه اپل بسته‌ای می‌بیند که با
# آنچه امضا شده یکی نیست.
# ---------------------------------------------------------------------
APPZIP=$(mktemp -d -t khayatyar-appzip)/KhayatYar.zip
echo "==> بسته‌بندیِ برنامه برای اپل"
ditto -c -k --keepParent "$APP" "$APPZIP"

echo "==> ارسالِ برنامه به اپل (چند دقیقه طول می‌کشد)"
notarize "$APPZIP"

echo "==> چسباندنِ تأیید به خودِ برنامه"
xcrun stapler staple "$APP"

# ---------------------------------------------------------------------
# ۵. DMG
#
# دستی و نه با `packageDmg`، به همان دلیلِ بندِ ۱ — و حالا دلیلِ
# دومی هم دارد: این DMG باید از روی برنامهٔ **مهرخوردهٔ** بندِ ۴ ساخته
# شود، و `packageDmg` بسته را از نو می‌سازد و مهر را دور می‌ریزد.
#
# پوشهٔ میانی با یک میان‌بر به `/Applications` ساخته می‌شود — همان
# چیزی که کاربرِ مک انتظار دارد: پنجره باز می‌شود و برنامه را روی
# آن آیکن می‌کشد.
# ---------------------------------------------------------------------
DMG="$REPO/desktop/build/compose/binaries/main/dmg/KhayatYar-$VERSION.dmg"
STAGE=$(mktemp -d -t khayatyar-dmg)
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
# ۶. notarize و مهرِ DMG
#
# امضا تنها نیمهٔ کار است. مکِ نسخهٔ ۱۰.۱۵ به بالا برنامهٔ امضاشده ولی
# notarizeنشده را هم با هشدار باز می‌کند.
#
# **این مرحله را نپرید.** بی مهر، مکِ کاربر باید برای تأییدِ notarize
# به سرورِ اپل وصل شود. روی کمپیوترِ بی‌اینترنت — یعنی دقیقاً کارگاهی
# که این برنامه برایش نوشته شده — نتیجه‌اش همان هشداری است که قرار بود
# نباشد.
# ---------------------------------------------------------------------
echo "==> ارسالِ DMG به اپل (چند دقیقه طول می‌کشد)"
notarize "$DMG"

echo "==> چسباندنِ تأیید به DMG"
xcrun stapler staple "$DMG"

# ---------------------------------------------------------------------
# ۷. وارسیِ نهایی — **هر دو، نه فقط DMG.**
#
# برنامه جدا سنجیده می‌شود چون همان است که کاربر از DMG بیرون می‌کشد و
# نگه می‌دارد؛ اگر مهرش جا بیفتد، هیچ‌کدام از بررسی‌های DMG آن را
# نمی‌گیرند.
# ---------------------------------------------------------------------
echo "==> وارسیِ نهایی — همان چیزی که مکِ کاربر می‌بیند"
xcrun stapler validate "$APP"
xcrun stapler validate "$DMG"
spctl --assess --type execute -vv "$APP"
spctl --assess --type open --context context:primary-signature -vv "$DMG"

echo
echo "✓ آماده: $DMG"
