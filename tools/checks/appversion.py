#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""نسخهٔ برنامه یک جاست، و قالبش برای نصب‌کنندهٔ ویندوز معتبر است.

**چرا این بررسی هست.** نسخه تا دیروز دو جا بود و از هم افتاده بود:
اندروید `versionName = "1.0"` و ویندوز `packageVersion = "1.0.0"`. هیچ
خطایی نمی‌داد — فقط یک نسخه از یک برنامه دو عدد داشت و روی کاغذِ
پشتیبانی معلوم نمی‌شد کارگاه کدام را دارد.

حالا هر دو از `gradle.properties` می‌خوانند. این بررسی نمی‌گذارد کسی
دوباره عدد را در فایلِ ساخت بنویسد.

سه چیز وارسی می‌شود و هرکدام یک خرابیِ واقعی را می‌بندد:

  الف) هیچ نسخهٔ ثابتی در فایل‌های ساخت نمانده باشد.
  ب) هر دو کلید در `gradle.properties` باشند.
  ج) `appVersion` سه بخشِ عددی و در بازهٔ مجازِ نصب‌کنندهٔ ویندوز باشد.

**بندِ (ج) از همه مهم‌تر است.** `ProductVersion`ِ MSI فقط سه بخشِ اول را
می‌سنجد و بازه‌شان محدود است: دو بخشِ اول تا ۲۵۵ و سومی تا ۶۵۵۳۵. عددی
بیرون از این بازه یا با چهار بخش، یا ساخت را می‌شکند یا بدتر: بسته
ساخته می‌شود ولی ویندوز آن را نسخهٔ تازه نمی‌بیند و به‌روزرسانی روی
پی‌سیِ کارگاه بی‌صدا رد می‌شود.

**آنچه این بررسی *نمی‌تواند* بگوید:** اینکه نسخه نسبت به بستهٔ قبلی
**بالا** رفته یا نه. آن به تاریخچه نیاز دارد و اینجا نیست. قاعده‌اش در
`DELIVERY.md` نوشته شده و مسئولیتش با کسی است که نسخه می‌دهد.
"""
import re
import sys

import _src

PROPS = _src.REPO / "gradle.properties"
APP = _src.REPO / "app/build.gradle.kts"
DESKTOP = _src.REPO / "desktop/build.gradle.kts"

for p in (PROPS, APP, DESKTOP):
    if not p.exists():
        print(f"✗ {p} نیست — بررسی پوچ بود، مسیر را ببینید")
        sys.exit(1)

problems = []


def no_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


# ---- الف) نسخهٔ ثابت در فایلِ ساخت نمانده باشد ----
#
# فقط نسخهٔ **ثابت** (رشته یا عددِ نوشته‌شده) ایراد است؛ خواندن از
# `gradleProperty` همان چیزی است که می‌خواهیم.
HARDCODED = [
    (APP, r'versionName\s*=\s*"', "versionName"),
    (APP, r"versionCode\s*=\s*\d", "versionCode"),
    (DESKTOP, r'packageVersion\s*=\s*"', "packageVersion"),
]
for path, pattern, what in HARDCODED:
    src = no_comments(path.read_text(encoding="utf-8"))
    m = re.search(pattern, src)
    if m:
        line = src[: m.start()].count("\n") + 1
        problems.append(
            f"{path.name}:{line} — {what} ثابت نوشته شده؛ باید از "
            f"gradle.properties بیاید"
        )

# و باید واقعاً از آنجا بخوانند.
app_src = no_comments(APP.read_text(encoding="utf-8"))
desk_src = no_comments(DESKTOP.read_text(encoding="utf-8"))
if 'gradleProperty("appVersion")' not in app_src:
    problems.append("app/build.gradle.kts — appVersion را نمی‌خواند")
if 'gradleProperty("appVersionCode")' not in app_src:
    problems.append("app/build.gradle.kts — appVersionCode را نمی‌خواند")
if 'gradleProperty("appVersion")' not in desk_src:
    problems.append("desktop/build.gradle.kts — appVersion را نمی‌خواند")

# ---- ب) کلیدها موجود باشند ----
props = {}
for line in PROPS.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    props[k.strip()] = v.strip()

version = props.get("appVersion")
code = props.get("appVersionCode")

if version is None:
    problems.append("gradle.properties — کلیدِ appVersion نیست")
if code is None:
    problems.append("gradle.properties — کلیدِ appVersionCode نیست")

# ---- ج) قالب و بازه، طبقِ قاعدهٔ نصب‌کنندهٔ ویندوز ----
if version is not None:
    # **صفرِ ابتدایی پذیرفته نمی‌شود، و دلیلش ظریف است.**
    #
    # `01.2.0` با الگوی سادهٔ `\d+` قبول می‌شد، و افزونهٔ Compose هم
    # قبولش می‌کند (`"01".toIntOrNull() == 1`). ولی به نصب‌کننده یک
    # **رشتهٔ متفاوت** می‌رسد: `ProductCode` از روی رشته ساخته می‌شود
    # پس عوض می‌شود، در حالی که `ProductVersion` عددی برابرِ قبلی است.
    #
    # نتیجه همان چیزی است که `upgradeUuid` قرار بود جلویش را بگیرد:
    # برنامه **کنارِ** نسخهٔ قبلی نصب می‌شود، نه به‌جایش. دو خیاط‌یار در
    # «افزودن یا حذف برنامه‌ها»، و کارگاه نمی‌داند کدام را باز کند.
    m = re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", version)
    if not m:
        problems.append(
            f"appVersion='{version}' باید دقیقاً سه بخشِ عددی و بی صفرِ "
            f"ابتدایی باشد (مثلِ 1.1.0) — نصب‌کنندهٔ ویندوز بیش از سه بخش "
            f"را نمی‌سنجد و صفرِ ابتدایی نصبِ موازی می‌سازد"
        )
    else:
        major, minor, build = (int(x) for x in m.groups())
        if major > 255:
            problems.append(f"appVersion — بخشِ اول {major} > ۲۵۵")
        if minor > 255:
            problems.append(f"appVersion — بخشِ دوم {minor} > ۲۵۵")
        if build > 65535:
            problems.append(f"appVersion — بخشِ سوم {build} > ۶۵۵۳۵")
        if major == 0 and minor == 0 and build == 0:
            problems.append("appVersion — نسخهٔ ۰.۰.۰ معنا ندارد")

if code is not None:
    if not re.fullmatch(r"\d+", code) or int(code) < 1:
        problems.append(f"appVersionCode='{code}' باید عددِ صحیحِ مثبت باشد")
    elif int(code) < 2:
        # از `versions.py` آمد، که این بررسی جایش را گرفت.
        #
        # نسخهٔ ۱ روی گوشیِ کارگاه نصب است. ساختِ تازه با همان عدد روی
        # آن **نمی‌نشیند** و اندروید هم پیامِ روشنی نمی‌دهد — کاربر فقط
        # می‌بیند که نصب نشد.
        problems.append(
            f"appVersionCode={code} است. نسخهٔ ۱ روی گوشیِ کارگاه نصب شده؛ "
            f"ساختِ تازه با همان عدد روی آن نمی‌نشیند"
        )

# ---- د) `upgradeUuid` باید سرِ جایش بماند ----
#
# این شناسه است که ویندوز با آن می‌فهمد بستهٔ تازه **همان برنامه** است.
# اگر عوض شود، MSIِ تازه کنارِ نصب‌شده می‌نشیند نه به‌جایش — و کارگاه دو
# خیاط‌یار پیدا می‌کند که هرکدام به دفترِ خودش نگاه می‌کند.
#
# توضیحِ بالای همان خط این را نوشته بود ولی هیچ‌چیز اجرایش نمی‌کرد.
UPGRADE_UUID = "6E7B1F2C-9A54-4B8E-97C6-3D2A5B41E0F7"
if UPGRADE_UUID not in desk_src:
    problems.append(
        f"desktop/build.gradle.kts — upgradeUuid دیگر {UPGRADE_UUID} نیست؛ "
        f"نسخهٔ تازه کنارِ نصب‌شده می‌نشیند نه به‌جایش"
    )

# ---- ه) راه‌های دورزدنِ نسخه بسته بماند ----
#
# افزونهٔ Compose چند کلیدِ دیگر هم دارد که نسخهٔ بسته را جدا تعیین
# می‌کنند. اگر یکی از آن‌ها نوشته شود، `packageVersion` بی‌اثر می‌شود و
# این بررسی هم چیزی نمی‌بیند — نسخه دوباره دو جا می‌شود.
for override in ("msiPackageVersion", "exePackageVersion", "packageBuildVersion"):
    if re.search(rf"\b{override}\s*=", desk_src):
        problems.append(
            f"desktop/build.gradle.kts — {override} نسخه را جدا تعیین می‌کند "
            f"و appVersion را دور می‌زند"
        )

if problems:
    print(f"✗ {len(problems)} ایراد در نسخهٔ برنامه")
    for p in problems:
        print(f"  {p}")
    print("\n  نسخه در gradle.properties نوشته می‌شود (appVersion و appVersionCode).")
    sys.exit(1)

print(
    f"✓ نسخهٔ برنامه یک‌جاست — appVersion={version}، "
    f"appVersionCode={code}، و هر دو سکو از همان می‌خوانند"
)
