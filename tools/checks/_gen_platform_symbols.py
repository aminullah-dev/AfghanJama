#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""سازندهٔ `platform_symbols.txt` — دستی اجرا می‌شود، نه در CI.

زیرخطِ اول نام یعنی `run_all.py` صدایش نمی‌زند: این اسکریپت اینترنت
می‌خواهد و چند ده مگابایت jar دانلود می‌کند.

**کِی باید دوباره اجرا شود:** هر بار که نسخهٔ Compose در
`core/build.gradle.kts` بالا رفت. وگرنه فهرست کهنه می‌مانَد و نمادهای
تازه از دیدِ حالتِ متنیِ `platformcall` بیرون می‌افتند. (حالتِ بایت‌کد
به فهرست کاری ندارد و همیشه دقیق است — به همین دلیل CI همان را اجباری
می‌کند.)

    python3 tools/checks/_gen_platform_symbols.py

روشِ کار: jarهای دسکتاپ را می‌گیرد، کلاس‌هایی را که نامشان به
`_skikoKt`/`_desktopKt`/`_jvmKt` ختم می‌شود جدا می‌کند — اینها همان
فایل‌های سکو-ویژه‌اند — و با `javap` نامِ توابع و ویژگی‌های عمومی‌شان را
درمی‌آورد.
"""
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

BASE = "https://repo1.maven.org/maven2/org/jetbrains/compose"
ARTIFACTS = [
    ("material3", "material3-desktop"),
    ("foundation", "foundation-desktop"),
    ("ui", "ui-desktop"),
    ("runtime", "runtime-desktop"),
    ("animation", "animation-desktop"),
]
SUFFIXES = ("_skikoKt", "_desktopKt", "_jvmKt", "_awtKt", "_skikoMainKt")

OUT = pathlib.Path(__file__).resolve().parent / "platform_symbols.txt"
GRADLE = pathlib.Path(__file__).resolve().parents[2] / "core/build.gradle.kts"


def compose_version() -> str:
    """نسخه از خودِ فایلِ ساخت خوانده می‌شود، نه از عددِ سفت‌شده اینجا."""
    m = re.search(r'val compose\s*=\s*"([^"]+)"', GRADLE.read_text(encoding="utf-8"))
    if not m:
        sys.exit("نسخهٔ Compose در core/build.gradle.kts پیدا نشد.")
    return m.group(1)


def main():
    ver = compose_version()
    print(f"Compose {ver}")
    tmp = pathlib.Path(tempfile.mkdtemp())
    classes = tmp / "x"
    classes.mkdir()

    for group, name in ARTIFACTS:
        url = f"{BASE}/{group}/{name}/{ver}/{name}-{ver}.jar"
        jar = tmp / f"{name}.jar"
        print(f"  {name}")
        urllib.request.urlretrieve(url, jar)
        with zipfile.ZipFile(jar) as z:
            z.extractall(classes)

    targets = [
        p for p in classes.rglob("*.class")
        if "$" not in p.name and any(p.stem.endswith(s) for s in SUFFIXES)
    ]
    print(f"  {len(targets)} فایلِ سکو-ویژه")

    rows = {}
    for p in targets:
        cls = str(p.relative_to(classes))[:-6].replace("/", ".")
        out = subprocess.run(
            ["javap", "-public", "-cp", str(classes), cls],
            capture_output=True, text=True
        ).stdout
        short = cls.split(".")[-1]
        # نامِ مانگل‌شده هم باید گرفته شود: `DropdownMenu-IlH_yew`
        for m in re.finditer(r"\s([A-Za-z_]\w*)(?:-[A-Za-z0-9_]+)?\(", out):
            if m.group(1)[0].isupper():
                rows.setdefault(m.group(1), set()).add(short)
        # ویژگی‌های سطحِ فایل به شکلِ getXxx() درمی‌آیند
        for m in re.finditer(r"\sget([A-Z]\w*)(?:-[A-Za-z0-9_]+)?\(\)", out):
            rows.setdefault(m.group(1), set()).add(short)

    header = [
        "# نمادهای Compose که کلاسِ کامپایل‌شده‌شان بینِ اندروید و دسکتاپ",
        "# فرق می‌کند — یعنی `:core` نباید مستقیم صداشان بزند.",
        "#",
        f"# ساخته‌شده از jarهای دسکتاپِ Compose Multiplatform {ver} با",
        "# `python3 tools/checks/_gen_platform_symbols.py`. هر بار که نسخهٔ",
        "# Compose بالا رفت، دوباره ساخته شود.",
        "#",
        "# قالب: <نام> <کلاسِ دسکتاپ>",
        "",
    ]
    body = [f"{k} {','.join(sorted(v))}" for k, v in sorted(rows.items())]
    OUT.write_text("\n".join(header + body) + "\n", encoding="utf-8")
    print(f"✓ {len(rows)} نماد در {OUT.name}")
    print(json.dumps(sorted(rows)[:8], ensure_ascii=False))


if __name__ == "__main__":
    main()
