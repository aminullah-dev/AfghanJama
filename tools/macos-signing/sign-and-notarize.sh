#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
#  امضا و notarize کردنِ نسخهٔ مکِ خیاط‌یار
# ═══════════════════════════════════════════════════════════════════
#
#  اول بسازید:   ./gradlew :desktop:packageDmg
#  بعد این را:   ./tools/macos-signing/sign-and-notarize.sh "Developer ID Application: ... (TEAMID)"
#
#  گواهی و رمز از این مک بیرون نمی‌روند.
#
#  ⚠ روی مک آزموده نشده — اگر خطا داد، متنش را بفرستید.

set -euo pipefail

IDENTITY="${1:-}"
KEYCHAIN_PROFILE="${2:-KhayatYar}"

if [ -z "$IDENTITY" ]; then
  echo "استفاده: $0 \"Developer ID Application: نام (TEAMID)\" [نامِ-پروفایل]" >&2
  echo >&2
  echo "برای دیدنِ گواهی‌هایتان:" >&2
  echo "  security find-identity -v -p codesigning | grep 'Developer ID Application'" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BIN="$ROOT/desktop/build/compose/binaries/main"

# ─── ۱) پیدا کردنِ خروجی ─────────────────────────────────────────
APP="$(find "$BIN/app" -maxdepth 1 -name '*.app' 2>/dev/null | head -1)"
DMG="$(find "$BIN/dmg" -maxdepth 1 -name '*.dmg' 2>/dev/null | head -1)"

if [ -z "$APP" ]; then
  echo "خطا: هیچ .app پیدا نشد. اول بسازید:" >&2
  echo "  ./gradlew :desktop:packageDmg" >&2
  exit 1
fi
echo "  برنامه: $APP"

# ─── ۲) امضا ─────────────────────────────────────────────────────
#
# `--options runtime` یعنی «سخت‌گیریِ زمانِ اجرا» و **اختیاری نیست**:
# اپل بی آن notarize نمی‌کند. `--timestamp` هم مهرِ زمانی می‌گذارد تا
# روزی که گواهی منقضی شود، نسخه‌های امضاشده بی‌اعتبار نشوند.
#
# `--deep` عمداً به کار نرفته: اپل خودش کنارش گذاشته و روی بسته‌های
# جاوا که kotlin و skiko داخلشان است، امضاها را به هم می‌ریزد. به‌جایش
# اول کتابخانه‌های داخلی جدا امضا می‌شوند، بعد خودِ بسته.
echo
echo "  امضای کتابخانه‌های داخلی…"
find "$APP" \( -name '*.dylib' -o -name '*.jnilib' -o -name '*.so' \) -print0 |
  while IFS= read -r -d '' lib; do
    codesign --force --timestamp --options runtime --sign "$IDENTITY" "$lib"
  done

echo "  امضای خودِ برنامه…"
codesign --force --timestamp --options runtime --sign "$IDENTITY" "$APP"

echo "  وارسیِ امضا…"
codesign --verify --deep --strict --verbose=2 "$APP"

# ─── ۳) بسته‌بندیِ تازه ──────────────────────────────────────────
#
# DMGی که Gradle ساخته، نسخهٔ **بی‌امضا** را داخلش دارد. حالا که خودِ
# برنامه امضا شده، باید دوباره بسته شود وگرنه کاربر همان بی‌امضا را
# می‌گیرد.
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp -R "$APP" "$STAGE/"
OUT="$BIN/dmg/$(basename "${DMG:-KhayatYar-signed.dmg}")"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"

echo
echo "  ساختنِ DMGِ امضاشده…"
hdiutil create -volname "KhayatYar" -srcfolder "$STAGE" -ov -format UDZO "$OUT"
codesign --force --timestamp --sign "$IDENTITY" "$OUT"

# ─── ۴) notarize ─────────────────────────────────────────────────
#
# اپل فایل را می‌گیرد، بدافزار-یابی می‌کند و یک «بلیت» می‌دهد. بی این،
# مکِ کاربر حتی با امضای درست هم هشدار می‌دهد.
echo
echo "  فرستادن به اپل برای notarize (چند دقیقه طول می‌کشد)…"
xcrun notarytool submit "$OUT" --keychain-profile "$KEYCHAIN_PROFILE" --wait

# ─── ۵) چسباندنِ بلیت ────────────────────────────────────────────
#
# بی این گام، مکِ **بی‌اینترنت** نمی‌تواند بلیت را از اپل بگیرد و باز
# هم هشدار می‌دهد. `staple` بلیت را داخلِ خودِ فایل می‌گذارد — و برای
# کارگاهی که اینترنتش قطع و وصل می‌شود، همین گام تفاوت را می‌سازد.
echo
echo "  چسباندنِ بلیت…"
xcrun stapler staple "$OUT"
xcrun stapler validate "$OUT"

echo
echo "  ✓ تمام شد:"
echo "    $OUT"
echo
echo "  برای اطمینان، همان چیزی را بسنجید که مکِ کاربر می‌سنجد:"
echo "    spctl --assess --type open --context context:primary-signature -v \"$OUT\""
