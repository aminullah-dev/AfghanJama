#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر بستهٔ Compose که :core ایمپورت می‌کند باید در فایلِ ساختش باشد.

همان درسِ `deskdeps`، این بار برای `:core`. و همان روزِ اول لازم شد:
`MasterDataScreen` از `rememberSaveable` استفاده می‌کند و آن در
`runtime-saveable` است نه `runtime` — یک بستهٔ جدا که آدم فکر می‌کند
داخلِ runtime است.

**چرا اینجا خطرناک‌تر از :desktop است:** وابستگی‌های Compose در `:core`
عمداً `compileOnly`اند تا گرافِ وابستگیِ اپِ اندروید دست‌نخورده بماند.
یعنی اگر یکی جا بیفتد، Gradle خودش جایش را پر نمی‌کند و خطا فقط سرِ
کامپایل — در CI و بعد از چند دقیقه — پیدا می‌شود.

مرزِ بسته‌ها با شهود جور درنمی‌آید: `ui.text` و `ui.unit` و `ui.graphics`
هرکدام بستهٔ جدایی‌اند، ولی `ui.draw` داخلِ خودِ `ui` است. این جدول همان
مرزهاست.
"""
import re
import sys

import _src

CORE_SRC = _src.CORE
BUILD = _src.REPO / "core/build.gradle.kts"

#: پیشوندِ بسته → نامِ آرتیفکت. **ترتیب مهم است**: بلندترین پیشوند اول،
#: چون `ui.text` باید پیش از `ui` بررسی شود.
ARTIFACTS = [
    ("androidx.compose.runtime.saveable", "runtime-saveable"),
    ("androidx.compose.runtime", "runtime"),
    ("androidx.compose.material.icons", "material-icons-extended"),
    ("androidx.compose.material3", "material3"),
    ("androidx.compose.material", "material"),
    ("androidx.compose.foundation", "foundation"),
    ("androidx.compose.animation", "animation"),
    ("androidx.compose.ui.text", "ui-text"),
    ("androidx.compose.ui.unit", "ui-unit"),
    ("androidx.compose.ui.graphics", "ui-graphics"),
    ("androidx.compose.ui.geometry", "ui-geometry"),
    ("androidx.compose.ui", "ui"),
]

IMPORT = re.compile(r"^import\s+(androidx\.compose\.[A-Za-z0-9_.]+)")

for path, need in ((CORE_SRC, "پوشهٔ :core"), (BUILD, "فایلِ ساختِ :core")):
    if not path.exists():
        print(f"✗ {need} پیدا نشد — بررسی پوچ بود، مسیرها را ببینید")
        sys.exit(1)

# توضیحات اول برداشته می‌شوند. `deskdeps` بارِ اول همین را نکرد و نامِ
# بسته را در توضیحِ خودم پیدا کرد؛ یعنی هرگز قرمز نمی‌شد.
build_lines = [
    ln.split("//", 1)[0]
    for ln in BUILD.read_text(encoding="utf-8").splitlines()
    if not ln.lstrip().startswith(("*", "/*", "//"))
]
DECLARE = re.compile(r"\b(implementation|api|compileOnly|runtimeOnly)\s*\(\s*\"([^\"]+)\"")
declared = {m.group(2) for ln in build_lines for m in DECLARE.finditer(ln)}


def artifact_of(pkg: str):
    for prefix, art in ARTIFACTS:
        if pkg == prefix or pkg.startswith(prefix + "."):
            return art
    return None


files = sorted(CORE_SRC.rglob("*.kt"))
if not files:
    print("✗ هیچ فایلی در :core نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

used = {}
unknown = []
for p in files:
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        m = IMPORT.match(line.strip())
        if not m:
            continue
        art = artifact_of(m.group(1))
        if art is None:
            unknown.append((m.group(1), p.relative_to(CORE_SRC), i))
        else:
            used.setdefault(art, (p.relative_to(CORE_SRC), i))

missing = []
for art, (f, i) in sorted(used.items()):
    # آرتیفکت‌ها با پسوندِ `-desktop` نوشته می‌شوند؛ نامِ خودِ بسته باید
    # در یکی از مختصاتِ اعلام‌شده بیاید.
    if not any(f":{art}-desktop:" in d or d.endswith(f":{art}-desktop") for d in declared):
        missing.append((art, f, i))

if unknown:
    print(f"✗ {len(unknown)} بستهٔ Compose که این جدول نمی‌شناسدش")
    for pkg, f, i in unknown:
        print(f"  {f}:{i}  {pkg}")
    print("\n  یا جدولِ ARTIFACTS کهنه است یا این بسته واقعاً تازه است.")
    print("  بی‌صدا رد نمی‌شود، چون همان‌جاست که وابستگیِ جامانده قایم می‌شود.")
    sys.exit(1)

if missing:
    print(f"✗ {len(missing)} بستهٔ Compose در :core استفاده شده ولی خواسته نشده")
    for art, f, i in missing:
        print(f"  {f}:{i}  →  org.jetbrains.compose.…:{art}-desktop")
    print("\n  در :core اینها compileOnly هستند، پس Gradle جایشان را پر")
    print("  نمی‌کند؛ نبودنشان فقط سرِ کامپایل در CI معلوم می‌شود.")
    sys.exit(1)

print(f"✓ {len(files)} فایلِ :core — هر {len(used)} بستهٔ Compose خواسته شده")
