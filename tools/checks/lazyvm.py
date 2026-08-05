#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ViewModelها پیش از باز شدنِ صفحه‌شان ساخته نشوند — و کارخانه کامل بماند.

**وضعی که از آن آمدیم:** `MainActivity` هر ۳۷ ViewModel را در
`setContent` می‌ساخت و به `AppNav` می‌داد که ۳۷ پارامتر داشت. هرکدام
سرِ ساخته شدن `stateIn` می‌زد روی جریان‌هایی که **کلِ جدول** را
می‌خوانند — ۹ تای‌شان جدولِ سفارش‌ها را. یعنی در ثانیهٔ اولِ اجرا همان
داده تا ۹ بار در حافظه می‌نشست، برای صفحه‌هایی که کاربر شاید هرگز باز
نکند.

سه قاعده:

  ۱ `MainActivity` هیچ ViewModelی نسازد — فقط `VmFactory`
  ۲ `AppNav` هیچ ViewModelی به‌عنوان پارامتر نگیرد؛ وگرنه فراخوانی‌اش
    دوباره همه را زودهنگام می‌سازد
  ۳ **کارخانه کامل باشد:** هر ViewModelی که `AppNav` می‌خواهد باید در
    `VmFactory` شاخه داشته باشد

قاعدهٔ سوم مهم‌ترین است. نبودنِ شاخه هیچ خطای کامپایلی نمی‌دهد — اپ
می‌سازد، نصب می‌شود، و **سرِ باز کردنِ همان یک صفحه** با «ViewModel
ناشناخته» می‌ترکد. دقیقاً همان دسته خرابی که این پروژه یک بار روی گوشیِ
کارگاه دید.
"""
import re
import sys

import _src

MAIN = _src.APP / "com/afghanjama/MainActivity.kt"
NAV = _src.APP / "com/afghanjama/ui/nav/AppNav.kt"
FACTORY = _src.APP / "com/afghanjama/ui/vm/VmFactory.kt"


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", src)


for p in (MAIN, NAV, FACTORY):
    if not p.exists():
        print(f"✗ {p.name} نیست — بررسی پوچ می‌شد.")
        sys.exit(1)

main = strip(MAIN.read_text(encoding="utf-8"))
nav = strip(NAV.read_text(encoding="utf-8"))
factory = strip(FACTORY.read_text(encoding="utf-8"))

fail = []

# ── ۱ ─────────────────────────────────────────────────────────────
for m in re.finditer(r"(\w+ViewModel)\s*\(", main):
    fail.append(
        f"MainActivity: «{m.group(1)}» را مستقیم می‌سازد — باید از "
        f"VmFactory و داخلِ مقصدِ خودش بیاید."
    )

# ── ۲ ─────────────────────────────────────────────────────────────
m = re.search(r"fun AppNav\(([^)]*)\)", nav, re.S)
if not m:
    print("✗ امضای AppNav پیدا نشد — بررسی پوچ شد.")
    sys.exit(1)
for p in re.findall(r"(\w+)\s*:\s*\w*ViewModel\b", m.group(1)):
    fail.append(
        f"AppNav: پارامترِ «{p}» یک ViewModel است — هر پارامتری یعنی "
        f"سازنده باید پیش از فراخوانی بسازدش."
    )

# ── ۳ ─────────────────────────────────────────────────────────────
wanted = set(re.findall(r"viewModel<(\w+)>", nav))
if not wanted:
    fail.append("AppNav هیچ ViewModelی نمی‌سازد — بررسی پوچ می‌شد.")
known = set(re.findall(r"(\w+)::class\.java\s*->", factory))
if not known:
    fail.append("VmFactory هیچ شاخه‌ای ندارد.")

for n in sorted(wanted - known):
    fail.append(
        f"VmFactory شاخهٔ «{n}» را ندارد — اپ کامپایل می‌شود ولی سرِ باز "
        f"کردنِ آن صفحه می‌ترکد."
    )
for n in sorted(known - wanted):
    fail.append(
        f"VmFactory شاخهٔ «{n}» را دارد ولی هیچ‌کس نمی‌خواهدش — یا صفحه‌اش "
        f"حذف شده یا از جای دیگری ساخته می‌شود."
    )

# کارخانه باید سرِ نامِ ناشناخته بشکند، نه بی‌صدا null بدهد
if "error(" not in factory:
    fail.append("VmFactory سرِ نامِ ناشناخته نمی‌شکند — سکوت یعنی کرشِ دیرتر.")

if fail:
    print(f"✗ {len(fail)} مشکل در ساختِ ViewModel")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ ViewModel: {len(wanted)} تا تنبل ساخته می‌شوند و کارخانه کامل است")
