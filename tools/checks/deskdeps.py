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

DESKTOP_SRC = _src.DESKTOP
CORE_SRC = _src.CORE
BUILD = _src.REPO / "desktop/build.gradle.kts"

# **چرا `:core` هم اسکن می‌شود — این نسخهٔ اول را از دست داده بود.**
#
# بارِ اول فقط فایل‌های خودِ `:desktop` خوانده می‌شد. آن روز درست بود:
# `:core` هیچ Composeای نداشت. از موجِ ۱ به بعد سی صفحهٔ Compose به
# `:core` رفت و آنجا وابستگی‌ها **`compileOnly`**اند — عمداً، تا گرافِ
# اپِ اندروید دست‌نخورده بماند. `compileOnly` یعنی به گرافِ اجرا
# نمی‌رسد، پس هر بستهٔ Composeای که `:core` ایمپورت می‌کند باید در
# `:desktop` صریح خواسته شود وگرنه فقط سرِ اجرا معلوم می‌شود.
#
# و دقیقاً همین افتاد: آیکن‌ها هیچ‌جا `implementation` نبودند. کامپایل
# سبز بود، پنجره باز می‌شد، و هر بخشی که کاربر باز می‌کرد با
# `NoClassDefFoundError` می‌مرد. این بررسی سبز مانده بود چون به `:core`
# نگاه نمی‌کرد.

# خانواده‌هایی که `compose.desktop.currentOs` با خودش می‌آورد.
# اینها ایمپورت‌شان وابستگیِ تازه نمی‌خواهد.
BUNDLED = {"runtime", "ui", "foundation", "animation"}

# خانواده → نشانه‌هایی که پذیرفته‌اند. هرکدام بود، یعنی خواسته شده.
#
# دو شکلِ نوشتن هست و هر دو معتبرند: نامِ DSLِ افزونهٔ Compose
# (`compose.material3`) و مختصاتِ رشته‌ای
# (`"org.jetbrains.compose.material:material-icons-extended-desktop:…"`).
# آیکن‌ها ناچار شکلِ دوم‌اند، چون نسخه‌شان باید روی ۱.۷.۳ قفل بماند —
# `compose.materialIconsExtended` نسخهٔ افزونه (۱.۸.۰) را می‌خواهد و آن
# برای دسکتاپ منتشر نشده. پس بررسی باید هر دو شکل را بشناسد، وگرنه
# وابستگیِ درست را «نبود» گزارش می‌کند.
NEEDS = {
    "material3": ("compose.material3", "compose.material3:material3"),
    "material": ("compose.material", "compose.material:material"),
    "materialIconsExtended": (
        "compose.materialIconsExtended",
        "material-icons-extended",
    ),
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

# (فایل، ریشه، نامِ ماژول) — نامِ ماژول در گزارش می‌آید تا معلوم باشد
# ایمپورت از کجاست.
files = [(p, DESKTOP_SRC, ":desktop") for p in sorted(DESKTOP_SRC.rglob("*.kt"))]
files += [(p, CORE_SRC, ":core") for p in sorted(CORE_SRC.rglob("*.kt"))]
if not files:
    print("✗ هیچ فایلی در :desktop و :core نیست — بررسی پوچ بود، مسیر را ببینید")
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
def rel(p, root):
    """مسیرِ نسبی — `:core` سه پوشهٔ منبع دارد و ریشه‌اش یکی نیست."""
    return _src.rel_to_core(p) if root is _src.CORE else p.relative_to(root)


for p, root, mod in files:
    for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
        if LIFECYCLE.match(line.strip()) and needs_lifecycle is None:
            needs_lifecycle = (f"{mod}/{rel(p, root)}", i)
        m = IMPORT.match(line.strip())
        if not m:
            continue
        fam = m.group(1)
        if fam == "material" and ".icons." in line:
            fam = "materialIconsExtended"
        if fam in BUNDLED:
            continue
        used.setdefault(fam, (f"{mod}/{rel(p, root)}", i))

# متنِ خامِ خطوطِ وابستگی — برای مختصاتِ رشته‌ای که `DECLARE` نمی‌گیرد.
declared_text = "\n".join(build_lines)

missing = []
for fam, (f, i) in sorted(used.items()):
    tokens = NEEDS.get(fam)
    if tokens is None:
        # خانوادهٔ ناشناخته — نه در فهرستِ همراه، نه در فهرستِ شناخته‌شده.
        # بی‌صدا رد نمی‌شود؛ یا واقعاً وابستگی می‌خواهد یا فهرست کهنه است.
        missing.append((f"androidx.compose.{fam}", f, i, f"compose.{fam} (ناشناخته)"))
        continue
    ok = any(t in declared for t in tokens) or any(t in declared_text for t in tokens)
    if not ok:
        missing.append((f"androidx.compose.{fam}", f, i, tokens[0]))

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
