#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""آزمونِ شبیه‌ساز با کد هم‌خوان است — وگرنه بی‌صدا بی‌معنا می‌شود.

`tools/ci/smoke.sh` برای رد شدن از دروازهٔ ورود، فایلِ تنظیماتِ ورود را
**مستقیم روی دیسکِ اپ** می‌نویسد. یعنی نام‌های کلید در آن اسکریپت کپیِ
دستیِ چیزی است که `AuthViewModel` می‌خواند.

**چرا این خطرناک است:** اگر روزی آن نام‌ها در کاتلین عوض شوند، اسکریپت
شکست نمی‌خورد — اپ فقط به صفحهٔ ورود می‌افتد و آزمون **سبز** می‌ماند.
همان «بررسیِ همیشه‌سبز» که در README.md نوشته‌ایم از نبودنش بدتر است.
دقیقاً همین اتفاق یک بار افتاد: نسخهٔ اولِ آزمون سبز بود در حالی که اپ
روی گوشیِ کارگاه می‌ترکید، چون هرگز از صفحهٔ ورود جلوتر نرفت.

پس این بررسی نگه می‌دارد که:
  • نامِ فایلِ تنظیمات با `getSharedPreferences(...)` یکی باشد
  • هر کلیدی که اسکریپت می‌نویسد واقعاً در `AuthViewModel` خوانده شود
  • هر نقشی که اسکریپت می‌گذارد در `UserRole` وجود داشته باشد
  • نامِ دیتابیس و نامِ بسته با کد یکی بمانند
"""
import pathlib
import re
import sys

import _src

SMOKE = _src.REPO / "tools/ci/smoke.sh"

if not SMOKE.exists():
    print(f"✗ {SMOKE} نیست — آزمونِ شبیه‌ساز برداشته شده؟")
    sys.exit(1)

smoke = SMOKE.read_text(encoding="utf-8")

# توضیحاتِ shell را برمی‌داریم: نامی که فقط در توضیح آمده نباید به حسابِ
# کد گذاشته شود (قاعدهٔ ۳ در README).
smoke_code = "\n".join(
    line for line in smoke.splitlines() if not line.lstrip().startswith("#")
)

auth = _src.read("ui/vm/AuthViewModel.kt")
roles_src = _src.read("ui/vm/UserRole.kt")

fail = []

# ── ۱. نامِ فایلِ تنظیمات ──────────────────────────────────────────
m = re.search(r'getSharedPreferences\(\s*"([^"]+)"', auth)
if not m:
    print("✗ `getSharedPreferences` در AuthViewModel پیدا نشد — بررسی پوچ شد.")
    sys.exit(1)
prefs_file = m.group(1)
if f"{prefs_file}.xml" not in smoke_code:
    fail.append(
        f"اسکریپت فایلِ «{prefs_file}.xml» را نمی‌نویسد، ولی AuthViewModel "
        f"همان را می‌خواند."
    )

# ── ۲. کلیدها ────────────────────────────────────────────────────
known_keys = set(re.findall(r'val\s+KEY_\w+\s*=\s*"([^"]+)"', auth))
if not known_keys:
    print("✗ هیچ KEY_ای در AuthViewModel پیدا نشد — بررسی پوچ شد.")
    sys.exit(1)

# کلیدهایی که اسکریپت در XML می‌نویسد: <string name="..."> و <boolean name="...">
written = set(re.findall(r'<(?:string|boolean)\s+name="([^"]+)"', smoke_code))
if not written:
    fail.append("اسکریپت هیچ کلیدی نمی‌نویسد — یعنی از صفحهٔ ورود جلوتر نمی‌رود.")

for k in sorted(written - known_keys):
    fail.append(f"اسکریپت کلیدِ «{k}» را می‌نویسد ولی AuthViewModel آن را نمی‌خواند.")

# دو کلید بدونِ‌شان اپ روی صفحهٔ ورود می‌ماند و آزمون بی‌معنا می‌شود.
for need in ("pin", "logged_in"):
    if need in known_keys and need not in written:
        fail.append(
            f"اسکریپت «{need}» را نمی‌نویسد — بدونِ آن اپ وارد نمی‌شود و "
            f"آزمون سبزِ بی‌معنا می‌دهد."
        )

# ── ۳. نقش‌ها ────────────────────────────────────────────────────
valid_roles = set(
    re.findall(r"^\s{4}([A-Z_]+)\s*,?\s*$", roles_src, re.M)
)
if not valid_roles:
    print("✗ هیچ نقشی در UserRole پیدا نشد — بررسی پوچ شد.")
    sys.exit(1)

seeded = set(re.findall(r'<string name="role">([^<]+)</string>', smoke_code))
seeded |= set(re.findall(r"^\s*seed_auth\s+([A-Z_]+)", smoke_code, re.M))
seeded.discard("$role")

for r in sorted(seeded - valid_roles):
    fail.append(f"اسکریپت نقشِ «{r}» را می‌گذارد ولی چنین نقشی در UserRole نیست.")
if not seeded:
    fail.append("اسکریپت هیچ نقشی نمی‌گذارد.")

# ── ۴. نامِ دیتابیس و بسته ───────────────────────────────────────
m = re.search(r'const val DB_NAME\s*=\s*"([^"]+)"', _src.read("data/DbSchema.kt"))
if m:
    # **هر** نامِ دیتابیسی که در اسکریپت آمده سنجیده می‌شود، نه فقط یکی.
    #
    # نسخهٔ اولِ همین بند فقط می‌پرسید «آیا نامِ درست جایی در اسکریپت هست؟»
    # و وقتی مسیرِ `DB=` را عمداً خراب کردم **سبز ماند** — چون همان نام در
    # خطِ `grep` هنوز بود. یعنی دو جا از هم می‌افتادند و بررسی چیزی
    # نمی‌گفت. حالا اگر حتی یکی از آن‌ها فرق کند گرفته می‌شود.
    names = set(re.findall(r"([A-Za-z_][A-Za-z0-9_]*\.db)", smoke_code))
    if not names:
        fail.append("اسکریپت هیچ نامِ دیتابیسی ندارد — باز شدنِ دفتر سنجیده نمی‌شود.")
    for n in sorted(names - {m.group(1)}):
        fail.append(
            f"اسکریپت از نامِ «{n}» استفاده می‌کند ولی دیتابیس «{m.group(1)}» "
            f"است — وارسیِ باز شدنِ دفتر همیشه شکست می‌خورد."
        )

gradle = (_src.REPO / "app/build.gradle.kts").read_text(encoding="utf-8")
m = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
if m and f'PKG="{m.group(1)}"' not in smoke:
    fail.append(
        f"نامِ بسته در اسکریپت با applicationId («{m.group(1)}») یکی نیست — "
        f"اسکریپت اپِ دیگری را نصب و باز می‌کند."
    )

if fail:
    print(f"✗ {len(fail)} ناهم‌خوانی بینِ آزمونِ شبیه‌ساز و کد")
    for f in fail:
        print(f"  • {f}")
    print("\n  tools/ci/smoke.sh حالتِ ورود را دستی روی دیسک می‌نویسد؛ اگر")
    print("  نام‌ها از هم بیفتند، آزمون بی‌آنکه چیزی بسنجد سبز می‌ماند.")
    sys.exit(1)

print(
    f"✓ آزمونِ شبیه‌ساز هم‌خوان است — {len(written)} کلید، "
    f"{len(seeded)} نقش، فایلِ {prefs_file}"
)
