#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""اسکریپت‌های PowerShell با BOM ذخیره می‌شوند و `$pwd` را نمی‌پوشانند.

**اشکالی که این بررسی را ساخت** (۲۰۲۶-۱۰-۲۱، اولین اجرای امضای ویندوز):

PowerShell 5.1 که با خودِ ویندوز می‌آید، فایلِ `.ps1`ِ بی BOM را با
کدبرگِ ANSI می‌خواند، نه UTF-8. اسکریپت‌های `tools/windows-signing`
فارسی‌اند؛ پس پیام‌ها ناخوانا می‌شد و جوابِ «بله» در اسکریپتِ مشتری
هیچ‌وقت با «بله»ِ درونِ فایل برابر نمی‌شد — نصبِ گواهی روی کمپیوترِ
مشتری اصلاً پیش نمی‌رفت. روی مک و در CI هیچ چیز این را نمی‌دید.

دومی: `$pwd` (بی‌حساس به بزرگی و کوچکی، همان `$PWD`) متغیرِ خودکارِ
PowerShell برای پوشهٔ فعلی است. گذاشتنِ رمزِ گواهی در آن، پوشهٔ فعلی
را هم می‌پوشاند.

قاعده: هر `.ps1`ی که حرفِ غیرِ ASCII دارد با BOMِ UTF-8 شروع شود، و
هیچ `.ps1`ی به `$pwd` مقدار ندهد.
"""
import re
import sys

import _src

BOM = b"\xef\xbb\xbf"
ASSIGN_PWD = re.compile(r"\$pwd\s*=(?!=)", re.IGNORECASE)
SKIP = {"build", ".gradle", "node_modules", ".git"}


def problems(raw: bytes) -> list[str]:
    """ایرادهای یک فایل؛ فهرستِ خالی یعنی سالم."""
    out = []
    if not raw.startswith(BOM) and any(b > 0x7F for b in raw):
        out.append("حرفِ غیرِ ASCII دارد ولی BOMِ UTF-8 ندارد — PowerShell 5.1 خرابش می‌خواند.")
    text = raw[len(BOM):] if raw.startswith(BOM) else raw
    for n, line in enumerate(text.decode("utf-8", "replace").splitlines(), 1):
        if ASSIGN_PWD.search(line.split("#", 1)[0]):
            out.append(f"خطِ {n}: به `$pwd` مقدار می‌دهد — متغیرِ خودکارِ پوشهٔ فعلی است.")
    return out


# ── خودآزمایی: بررسی باید نمونه‌های خراب را بگیرد ──────────────────
GOOD = BOM + 'Write-Host "سلام"\n$certPwd = Read-Host "رمز"\n'.encode("utf-8")
BROKEN = {
    "بی BOM با فارسی": 'Write-Host "سلام"\n'.encode("utf-8"),
    "$pwd": BOM + b'$pwd = Read-Host "x"\n',
    "$PWD بزرگ": BOM + b'$PWD= 1\n',
}
fail = []
if problems(GOOD):
    fail.append("خودآزمایی: فایلِ درست رد شد — بررسی خراب است.")
for name, raw in BROKEN.items():
    if not problems(raw):
        fail.append(f"خودآزمایی: «{name}» گرفته نشد — بررسی پوچ است.")
if problems(b'Write-Host "ok"\nif ($pwd -eq $x) { }\n'):
    fail.append("خودآزمایی: خواندنِ $pwd (نه مقدار دادن) رد شد.")

# ── خودِ پروژه ────────────────────────────────────────────────────
files = [p for p in _src.REPO.rglob("*.ps1") if not SKIP & set(p.relative_to(_src.REPO).parts)]
if not files:
    fail.append("هیچ .ps1ی پیدا نشد — بررسی پوچ می‌شد.")
for p in files:
    fail += [f"{p.relative_to(_src.REPO)}: {x}" for x in problems(p.read_bytes())]

if fail:
    print(f"✗ {len(fail)} مشکل در اسکریپت‌های PowerShell")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ PowerShell: {len(files)} اسکریپت با BOM و بی مقدار دادن به $pwd "
      f"({len(BROKEN)} نمونهٔ خراب گرفته شد)")
