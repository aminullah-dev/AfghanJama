#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر خانوادهٔ Compose که :desktop ایمپورت می‌کند باید خواسته شده باشد.

روی اندروید همهٔ بسته‌های Compose در `app/build.gradle.kts` صریح نوشته
شده‌اند. روی دسکتاپ یک خطِ کوتاه هست — `compose.desktop.currentOs` — که
آدم فکر می‌کند «همهٔ Compose» را می‌آورد. نمی‌آورد: فقط `runtime`،
`ui`، `foundation` و `animation`. **Material و Material3 بستهٔ جدا
هستند.**

فازِ ۳ دقیقاً روی همین افتاد: ۱۶ خطای «Unresolved reference 'material3'»
که هیچ‌کدام اشکالِ کد نبود، فقط یک وابستگیِ نانوشته. سه دقیقه ساختِ
اندروید هم با آن سوخت.

اینجا Gradle اجرا نمی‌شود (`dl.google.com` بسته است)، پس تنها تأییدِ
کامپایل از CI می‌آید و هر رفت‌وبرگشت چند دقیقه است. این بررسی همان را
در یک ثانیه می‌گوید.
"""
import pathlib
import re
import sys

import _src

DESKTOP_SRC = _src.ROOTS[-1]
BUILD = _src.REPO / "desktop/build.gradle.kts"

# خانواده‌هایی که `compose.desktop.currentOs` با خودش می‌آورد.
# اینها ایمپورت‌شان وابستگیِ تازه نمی‌خواهد.
BUNDLED = {"runtime", "ui", "foundation", "animation"}

# خانواده → نامی که باید در فایلِ ساخت باشد.
NEEDS = {
    "material3": "compose.material3",
    "material": "compose.material",
    "materialIconsExtended": "compose.materialIconsExtended",
}

IMPORT = re.compile(r"^import\s+androidx\.compose\.([A-Za-z0-9_]+)")

# خانوادهٔ lifecycle هم همین داستان را دارد: `ViewModel` از `:core`
# می‌آید (آنجا `api` است و ترابری‌اش می‌کند) ولی `viewModel { }`ِ
# Compose در بستهٔ جداست و باید در `:desktop` خواسته شود.
LIFECYCLE = re.compile(r"^import\s+androidx\.lifecycle\.viewmodel\.compose\.")
LIFECYCLE_DEP = "androidx.lifecycle:lifecycle-viewmodel-compose"

if not BUILD.exists():
    print(f"✗ {BUILD.name} نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

files = sorted(DESKTOP_SRC.rglob("*.kt"))
if not files:
    print("✗ هیچ فایلی در :desktop نیست — بررسی پوچ بود، مسیر را ببینید")
    sys.exit(1)

# **بدونِ حذفِ توضیحات این بررسی پوچ است.** بارِ اول همین‌طور نوشته شد
# و وقتی وابستگی را عمداً برداشتم، سبز ماند — چون خودِ توضیحی که بالای
# آن خط نوشته بودم نامِ بسته را داشت. بررسی‌ای که همیشه سبز است بدتر از
# نبودنش است.
build_lines = [
    ln.split("//", 1)[0]
    for ln in BUILD.read_text(encoding="utf-8").splitlines()
    if not ln.lstrip().startswith(("*", "/*", "//"))
]

# فقط خطی که واقعاً وابستگی اعلام می‌کند به حساب می‌آید.
DECLARE = re.compile(
    r"\b(implementation|api|compileOnly|runtimeOnly)\s*\(\s*([A-Za-z0-9_.]+)"
)
declared = {m.group(2) for ln in build_lines for m in DECLARE.finditer(ln)}

# `material.icons` زیرِ خانوادهٔ `material` است ولی از بستهٔ
# icons می‌آید؛ برای همین جدا نگاه می‌شود.
used = {}
needs_lifecycle = None
for p in files:
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if LIFECYCLE.match(line.strip()) and needs_lifecycle is None:
            needs_lifecycle = (p.relative_to(DESKTOP_SRC), i)
        m = IMPORT.match(line.strip())
        if not m:
            continue
        fam = m.group(1)
        if fam == "material" and ".icons." in line:
            fam = "materialIconsExtended"
        if fam in BUNDLED:
            continue
        used.setdefault(fam, (p.relative_to(DESKTOP_SRC), i))

missing = []
for fam, (f, i) in sorted(used.items()):
    dep = NEEDS.get(fam)
    if dep is None:
        # خانوادهٔ ناشناخته — نه در فهرستِ همراه، نه در فهرستِ شناخته‌شده.
        # بی‌صدا رد نمی‌شود؛ یا واقعاً وابستگی می‌خواهد یا فهرست کهنه است.
        missing.append((f"androidx.compose.{fam}", f, i, f"compose.{fam} (ناشناخته)"))
    elif dep not in declared:
        missing.append((f"androidx.compose.{fam}", f, i, dep))

# وابستگی‌های مختصاتی (رشته‌ای) جدا سنجیده می‌شوند، چون `DECLARE`
# فقط نام‌های DSL مثلِ `compose.material3` را می‌گیرد نه رشته را.
if needs_lifecycle is not None:
    f, i = needs_lifecycle
    if not any(LIFECYCLE_DEP in ln for ln in build_lines):
        missing.append(("androidx.lifecycle.viewmodel.compose", f, i, f'"{LIFECYCLE_DEP}:…"'))

if missing:
    print(f"✗ {len(missing)} بستهٔ Compose در :desktop استفاده شده ولی خواسته نشده")
    for name, f, i, dep in missing:
        print(f"  {f}:{i}  {name}  →  {dep}")
    print(f"\n  `compose.desktop.currentOs` فقط {'، '.join(sorted(BUNDLED))} را می‌آورد.")
    print(f"  بقیه باید در {BUILD.name} صریح نوشته شوند.")
    sys.exit(1)

extra = ", ".join(sorted(used)) or "هیچ"
print(f"✓ وابستگی‌های Compose در :desktop کامل است (جدا از currentOs: {extra})")
