#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
همهٔ بررسی‌های ساختاری — پیش از اینکه Gradle ده دقیقه وقت بگیرد.

چرا هست: دو بار در یک روز خطای کامپایلِ ساده (ایمپورتِ جامانده) تا CI
رفت و ساختِ کامل را سوزاند. این‌ها همان‌ها را در چند ثانیه می‌گیرند.

هر بررسی مستقل اجرا می‌شود تا اگر یکی شکست، بقیه هم گزارش شوند و همه در
یک رفت‌وبرگشت دیده شوند، نه یکی‌یکی.
"""
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
# فایل‌های زیرخط‌دار کمکی‌اند نه بررسی — مثلِ _src.py که فقط
# مسیرِ ماژول‌ها را می‌داند.
scripts = sorted(
    p for p in HERE.glob("*.py")
    if p.name != Path(__file__).name and not p.name.startswith("_")
)

if not scripts:
    sys.exit("✗ هیچ بررسی‌ای پیدا نشد — پوشه خالی است، این اجرا پوچ بود")

failed = []
for s in scripts:
    r = subprocess.run([sys.executable, str(s)], capture_output=True, text=True)
    out = (r.stdout + r.stderr).strip()
    last = out.splitlines()[-1] if out else "(بی‌خروجی)"
    print(f"{s.stem:16} {last}")
    if r.returncode != 0:
        failed.append((s.stem, out))

print()
if failed:
    print(f"✗ {len(failed)} بررسی از {len(scripts)} شکست خورد:\n")
    for name, out in failed:
        print(f"--- {name} ---")
        print(out)
        print()
    sys.exit(1)
print(f"✓ هر {len(scripts)} بررسیِ ساختاری قبول شد")
