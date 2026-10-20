#!/usr/bin/env bash
#
# نسخهٔ آیفون از `gradle.properties` — **پیش از `xcodegen`.**
#
# تا ۱.۹.۰ پروژهٔ آیفون هیچ نسخه‌ای نداشت و xcodegen پیش‌فرضِ خودش را
# می‌گذاشت (۱.۰). اندروید و مک و ویندوز از `gradle.properties`
# می‌خواندند و آیفون نه؛ ساختِ آیفون هم از بقیه کهنه‌تر بود و هیچ‌چیز این
# را نشان نمی‌داد.
#
# این اسکریپت دو عدد را از همان یک منبع در `iosApp/Version.xcconfig`
# می‌نویسد، و `iosApp/project.yml` آن فایل را به هدف می‌دهد:
#
#   appVersion      → MARKETING_VERSION        → CFBundleShortVersionString
#   appVersionCode  → CURRENT_PROJECT_VERSION  → CFBundleVersion
#
# فایلِ ساخته‌شده در گیت نیست (`.gitignore`): اگر بود، جای دومی برای
# نسخه می‌شد — همان چیزی که این کار برای برداشتنش است.
#
# اجرا (محلی، و در `ios.yml` پیش از xcodegen):
#   tools/release/ios-version.sh && (cd iosApp && xcodegen generate)
#
# اگر یادتان رفت: بی این فایل xcodegen خطا می‌دهد، و اگر فایل کهنه باشد
# (نسخه بالا رفته و این دوباره اجرا نشده)، پلهٔ پیش از ساختِ Xcode همین
# اسکریپت را می‌زند و ساخت را با پیامِ روشن نگه می‌دارد.
set -euo pipefail

cd "$(dirname "$0")/../.."

VERSION=$(sed -n 's/^appVersion=//p' gradle.properties | tr -d '[:space:]')
CODE=$(sed -n 's/^appVersionCode=//p' gradle.properties | tr -d '[:space:]')

# همان قالبی که `tools/checks/appversion.py` می‌خواهد. اینجا دوباره
# سنجیده می‌شود چون عددِ بد در xcconfig بی‌صدا به Info.plist می‌رود.
if ! printf '%s' "$VERSION" | grep -Eq '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'; then
  echo "✗ appVersion='$VERSION' در gradle.properties سه بخشِ عددی نیست." >&2
  exit 1
fi
if ! printf '%s' "$CODE" | grep -Eq '^[1-9][0-9]*$'; then
  echo "✗ appVersionCode='$CODE' در gradle.properties عددِ صحیحِ مثبت نیست." >&2
  exit 1
fi

OUT=iosApp/Version.xcconfig
NEW="// تولیدشده با tools/release/ios-version.sh از gradle.properties — دستی ویرایش نکنید.
MARKETING_VERSION = $VERSION
CURRENT_PROJECT_VERSION = $CODE"

# فقط اگر عوض شده باشد نوشته می‌شود، تا Xcode بی‌دلیل همه‌چیز را از نو
# نسازد.
if [ -f "$OUT" ] && [ "$(cat "$OUT")" = "$NEW" ]; then
  echo "✓ $OUT به‌روز است — $VERSION ($CODE)"
else
  printf '%s\n' "$NEW" > "$OUT"
  echo "✓ $OUT نوشته شد — $VERSION ($CODE)"
fi
