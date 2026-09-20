#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر نمادِ پروژه که :core استفاده می‌کند باید در خودِ :core باشد.

**چرا لازم شد:** در موجِ ۴ فازِ ۴.۵، `LoginScreen` و `SelfTestScreen` به
`:core` منتقل شدند در حالی که ViewModelشان (`AuthViewModel` و
`SelfTestViewModel`) در `:app` مانده بود. ۵۸ خطای کامپایل در CI، بعد از
سه دقیقه.

**چرا هیچ بررسی‌ای نگرفتش:** خطِ

    import com.afghanjama.ui.vm.AuthViewModel

کاملاً سالم به‌نظر می‌رسد. بستهٔ `com.afghanjama.ui.vm` در **هر دو**
ماژول وجود دارد، پس `refcheck` نمادش را در پروژه پیدا می‌کند و
`purecheck` هم چیزی اندرویدی نمی‌بیند. نامِ بسته یکی است؛ ماژول‌ها فرق
دارند — و هیچ‌کدام از بررسی‌های قبلی ماژول را نمی‌دیدند.

این همان دامِ «هم‌بستهٔ بین‌ماژولی» است که در `purecheck` برای نامِ
کاملاً مقید گرفته شده بود، ولی برای `import` نه.

از این به بعد، بردنِ یک صفحه به `:core` بدونِ ViewModelش در یک ثانیه
معلوم می‌شود، نه در دقیقهٔ سومِ CI.
"""
import re
import sys

import _src

# نمادهای **سطحِ بالا** — و «سطحِ بالا» یعنی ستونِ صفر.
#
# نسخهٔ اول `^\s*` داشت و هر `val`ِ محلیِ توی هر تابعی را اعلانِ ماژول
# می‌شمرد. نتیجه ۱۴۸ خطای نادرست بود: `file`، `icon`، `onClick`، `to`…
# یعنی دقیقاً همان «بررسیِ همیشه‌قرمز» که در README نوشته شده بدتر از
# نبودنِ بررسی است.
TYPE_DECL = re.compile(
    r"^(?:public |internal |private |sealed |abstract |open |value |expect |actual )*"
    r"(?:data class|enum class|annotation class|class|object|interface|typealias)\s+([A-Za-z_]\w*)",
    re.M)

# تابع و ویژگیِ سطحِ بالا — با پشتیبانی از **الحاقی**.
#
# `fun Long.afn()` نامش `afn` است نه `Long`. نسخهٔ اول این را نمی‌گرفت و
# در عوض هر `import …ui.format.afn` را «هیچ‌جا پیدا نشد» اعلام می‌کرد.
# `expect ` و `actual ` از وقتی `:core` چندسکویی شد لازم شدند.
# `TYPE_DECL` بالا این دو را از قبل داشت و همین باعث شد شکاف دیر
# دیده شود: `expect class` شناخته می‌شد ولی `expect fun` نه، و
# `lanClient` — که تابع است — «هیچ‌جا پیدا نشد» گزارش می‌شد.
FUN_DECL = re.compile(
    r"^(?:public |internal |private |inline |suspend |operator |infix "
    r"|expect |actual )*"
    r"fun\s+(?:<[^>]*>\s*)?(?:[\w.<>,\s?\[\]]+\.)?([A-Za-z_]\w*)\s*\(", re.M)
VAL_DECL = re.compile(
    r"^(?:public |internal |private |const |expect |actual )*"
    r"va[lr]\s+(?:<[^>]*>\s*)?(?:[\w.<>,\s?\[\]]+\.)?([A-Za-z_]\w*)\s*[:=]", re.M)

# ایمپورتِ ستاره‌دار عمداً بیرون است: `…entities.*` نامِ مشخصی ندارد که
# بشود دنبالش گشت. در این پروژه ستاره فقط برای `data.entities` استفاده
# می‌شود که کاملاً در `:core` است.
IMPORT_PROJ = re.compile(r"^import\s+(com\.afghanjama\.[\w.]*[A-Za-z_]\w*)\s*$", re.M)


def declared_in(root):
    """نامِ هر چیزی که در این ماژول اعلام شده، و بستهٔ هر فایل."""
    names, pkgs = set(), {}
    for p in sorted(root.rglob("*.kt")):
        src = p.read_text(encoding="utf-8")
        m = re.search(r"^package\s+([\w.]+)", src, re.M)
        pkg = m.group(1) if m else ""
        found = set(TYPE_DECL.findall(src)) | set(FUN_DECL.findall(src)) \
            | set(VAL_DECL.findall(src))
        names |= found
        pkgs.setdefault(pkg, set())
        pkgs[pkg] |= found
        # عضوهای enum هم از بیرون صدا زده می‌شوند
        for em in re.finditer(r"enum class\s+\w+[^{]*\{([^}]*)\}", src):
            for e in re.findall(r"\b([A-Z][A-Z0-9_]+)\b", em.group(1)):
                names.add(e)
                pkgs[pkg].add(e)
    return names, pkgs


core_names, core_pkgs = declared_in(_src.CORE)
app_names, app_pkgs = declared_in(_src.APP)

core_files = sorted(_src.CORE.rglob("*.kt"))
if not core_files or not app_names:
    print("✗ ماژولی خالی دیده شد — بررسی پوچ بود، مسیرها را ببینید")
    sys.exit(1)

bad = []
for p in core_files:
    src = p.read_text(encoding="utf-8")
    rel = _src.rel_to_core(p)

    # ---- ۱. ایمپورتِ نمادی که در :core نیست ----
    for m in IMPORT_PROJ.finditer(src):
        simple = m.group(1).split(".")[-1]
        if simple == "*" or simple in core_names:
            continue
        where = "در :app است" if simple in app_names else "هیچ‌جا پیدا نشد"
        line = src[:m.start()].count("\n") + 1
        bad.append((rel, line, m.group(1), where))

    # ---- ۲. نامِ هم‌بسته‌ای که فقط در :app اعلام شده ----
    #
    # کاتلین هم‌بسته‌ها را بی‌ایمپورت می‌بیند، پس این حالت حتی یک خطِ
    # `import` هم ندارد که بشود نگاهش کرد.
    pm = re.search(r"^package\s+([\w.]+)", src, re.M)
    pkg = pm.group(1) if pm else ""
    # فقط نامِ با حرفِ بزرگ. همان‌هایی که این خرابی را می‌سازند
    # (ViewModel، صفحه، جزء) — و نامِ کوچک زیادی با متغیرهای محلیِ
    # `:core` هم‌نام می‌شود و هشدارِ نادرست می‌دهد.
    only_app = {n for n in app_pkgs.get(pkg, set()) - core_names if n[:1].isupper()}
    if not only_app:
        continue
    body = re.sub(r"^\s*(?:import|package)\s+.*$", "", src, flags=re.M)
    body = re.sub(r"/\*(?:.|\n)*?\*/|//[^\n]*", " ", body)
    for name in sorted(only_app):
        if re.search(r"(?<![.\w])" + re.escape(name) + r"\b", body):
            bad.append((rel, 0, f"{pkg}.{name}", "هم‌بسته، ولی فقط در :app"))

if bad:
    print(f"✗ {len(bad)} نمادی که :core استفاده می‌کند ولی در :core نیست")
    for f, line, name, where in bad:
        at = f":{line}" if line else ""
        print(f"  {f}{at}  {name}  →  {where}")
    print("\n  نامِ بسته در دو ماژول یکی است، پس ایمپورت سالم به‌نظر می‌رسد.")
    print("  یا آن نماد هم باید به :core بیاید، یا این فایل باید در :app بماند.")
    sys.exit(1)

print(f"✓ {len(core_files)} فایلِ :core — هیچ‌کدام به نمادِ :app تکیه نکرده")
