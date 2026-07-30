# --- ریشهٔ مخزن از محلِ خودِ این فایل پیدا می‌شود ---
# نه از پوشهٔ اجرا (که یک بار همهٔ بررسی‌ها را بی‌سروصدا پوچ کرد) و نه
# مطلقِ یک کامپیوترِ خاص (که روی CI نبود).
import pathlib as _pl
_REPO = str(_pl.Path(__file__).resolve().parents[2])
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
نمادهای کتابخانه‌ای (androidx/kotlinx/java) که استفاده شده‌اند ولی ایمپورت نه.

refcheck فقط نمادهای **خودِ پروژه** را می‌شناسد، پس `BorderStroke` را
نگرفت و خطا در CI بیرون آمد. اینجا از خودِ پروژه یاد می‌گیریم: هر نمادی
که جایی در پروژه ایمپورت شده، یک نامِ شناخته‌شده است؛ اگر فایلی از آن
استفاده کند و ایمپورتش نکند، همان‌جا خطای کامپایل می‌شود.

هیچ فهرستِ دستی‌ای لازم نیست و با رشدِ پروژه خودش کامل‌تر می‌شود.
"""
import glob
import re
import sys
from collections import defaultdict

SRC = _REPO + "/app/src/main/java/com/afghanjama"
files = sorted(glob.glob(f"{SRC}/**/*.kt", recursive=True))
if len(files) < 50:
    raise SystemExit(f"✗ فقط {len(files)} فایل — مسیر اشتباه است، بررسی پوچ بود")


def strip_code(src):
    src = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    src = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)
    src = re.sub(r"/\*(?:.|\n)*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


# ---- ۱. نامِ هر نمادِ کتابخانه‌ای که جایی ایمپورت شده ----
owner_of = defaultdict(set)      # نامِ ساده → مجموعهٔ مسیرهای کامل
for f in files:
    for m in re.finditer(r"^import\s+((?:androidx|kotlinx|java|org|com\.google)[\w.]*)",
                         open(f).read(), re.M):
        path = m.group(1)
        simple = path.split(".")[-1]
        if simple == "*" or not simple[:1].isupper():
            continue                       # فقط کلاس/شیء، نه تابعِ الحاقی
        owner_of[simple].add(path)

# نامی که دو مسیرِ مختلف دارد مبهم است — کنار گذاشته می‌شود تا هشدارِ
# نادرست ندهیم (مثلاً Card در material و material3)
AMBIGUOUS = {s for s, paths in owner_of.items() if len(paths) > 1}

problems = []
for f in files:
    raw = open(f).read()
    code = strip_code(raw)
    imported = set()
    star = False
    for m in re.finditer(r"^import\s+([\w.*]+)(?:\s+as\s+(\w+))?", raw, re.M):
        path, alias = m.group(1), m.group(2)
        if path.endswith(".*"):
            star = True
        imported.add(alias or path.split(".")[-1])
    if star:
        continue                            # ایمپورتِ ستاره‌دار — نمی‌شود قضاوت کرد

    # نمادهایی که خودِ همین فایل تعریف کرده
    local = set(re.findall(
        r"\b(?:class|object|interface|enum class|data class|annotation class)\s+([A-Za-z_]\w*)",
        code))

    used = set(re.findall(r"(?<![.\w])([A-Z][A-Za-z0-9_]*)\b", code))
    for sym in used:
        if sym in AMBIGUOUS or sym not in owner_of:
            continue
        if sym in imported or sym in local:
            continue
        path = next(iter(owner_of[sym]))
        if path in raw:
            continue
        problems.append((f.replace(SRC + "/", ""), sym, path))

if problems:
    print(f"✗ {len(problems)} نمادِ ایمپورت‌نشده")
    for f, sym, path in problems[:25]:
        print(f"  {f:52} '{sym}'  →  import {path}")
    sys.exit(1)
print(f"✓ {len(files)} فایل — هر نمادِ کتابخانه‌ایِ شناخته‌شده ایمپورت شده")
