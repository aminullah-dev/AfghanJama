#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ریشه‌های کدِ کاتلین — یک‌جا، برای همهٔ بررسی‌ها.

پروژه از یک ماژول به دو ماژول رفت (`:app` و `:core`). بررسی‌ها تا آن
روز مسیرِ `app/src/main/java` را ثابت در خودشان داشتند؛ اگر همان‌طور
می‌ماندند، ۴٬۰۰۰ خطی که به `:core` رفت از دیدشان بیرون می‌افتاد و
**بی‌سروصدا سبز** می‌ماندند — همان اشتباهی که یک بار افتاد و در
README.md نوشته شد.

پس مسیرها از اینجا می‌آیند و هر ماژولِ تازه فقط یک سطر اضافه می‌خواهد.
"""
import pathlib
import sys

# ═══ خروجی همیشه UTF-8 ═══
#
# **این بررسی‌ها روی ویندوز، هر ۴۸ تا، بی‌استثنا می‌ترکیدند.**
#
# پایتونِ ویندوز وقتی خروجی‌اش لوله باشد (یعنی وقتی `run_all` صدایشان
# می‌زند، یا هر جای دیگری که خروجی گرفته شود) کدگذاریِ محلی را
# برمی‌دارد: cp1252. و `✓` (U+2713) در cp1252 اصلاً وجود ندارد:
#
#     UnicodeEncodeError: 'charmap' codec can't encode character
#     '✓' in position 0: character maps to <undefined>
#
# یعنی هر بررسی درست کار می‌کرد، درست نتیجه می‌گرفت، و بعد **سرِ چاپِ
# پیامِ موفقیتش** می‌مرد و با کدِ ۱ برمی‌گشت. `run_all` هر ۴۸ تا را
# «شکست‌خورده» می‌شمرد و بعد خودش هم سرِ چاپِ `✗` می‌ترکید.
#
# نتیجه‌اش بدترین حالتِ ممکن بود: پلهٔ بررسی‌های ساختاریِ `windows.yml`
# **همیشه قرمز** بود، پس قرمزی‌اش هیچ معنایی نداشت و کسی نمی‌خواندش —
# و در عمل هیچ‌کدام از این ۴۸ نگهبان روی ویندوز کار نمی‌کرد.
#
# هر بررسی `_src` را ایمپورت می‌کند، پس همین‌جا درست می‌شود: یک بار،
# برای همه، چه از `run_all` و چه دستی.
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        # خروجی ممکن است TextIOWrapper نباشد؛ آن وقت کاری از دستمان
        # برنمی‌آید و نباید خودِ ایمپورت را بشکنیم.
        pass

REPO = pathlib.Path(__file__).resolve().parents[2]

#: ریشه‌های `:core` — از وقتی چندسکویی شد، **سه‌تا**.
#:
#: تا دیروز یک پوشه بود (`core/src/main/kotlin`). با آمدنِ iOS، کاتلین
#: کد را بر اساسِ سکو تقسیم می‌کند و هر سه اینجا واقعی‌اند:
#:
#:     commonMain      کدی که هر سه سکو می‌سازند
#:     jvmAndroidMain  ویندوز و گوشی (شبکه، رمزنگاری، زیپ)
#:     iosMain         سهمِ آیفون
#:
#: **و این تغییر همان دامی را زد که این فایل برای بستنش نوشته شده
#: بود.** با جابه‌جا شدنِ پوشه، هر ۵۲ بررسی فایل‌ها را گم کردند. خوب
#: است که قرمز شدند و نه سبز — همان چیزی که توضیحِ بالا وعده‌اش را
#: داده بود — ولی نشان می‌دهد فهرستِ ریشه‌ها باید با ساختِ ماژول‌ها
#: هم‌گام بماند.
CORE_ROOTS = [
    REPO / "core/src/commonMain/kotlin",
    REPO / "core/src/jvmAndroidMain/kotlin",
    REPO / "core/src/iosMain/kotlin",
]

#: ریشه‌های سورس، به ترتیبِ جست‌وجو
ROOTS = CORE_ROOTS + [
    REPO / "app/src/main/java",
    REPO / "desktop/src/main/kotlin",
]

#: ریشه‌ها با نام — برای بررسی‌هایی که به یک ماژولِ مشخص کار دارند.
APP = REPO / "app/src/main/java"
DESKTOP = REPO / "desktop/src/main/kotlin"

#: همان ریشه‌ها، تا سرِ بستهٔ اصلی
PKG_ROOTS = [r / "com/afghanjama" for r in ROOTS]


def kt_files():
    """همهٔ فایل‌های کاتلینِ پروژه، از هر ماژولی."""
    out = []
    for r in ROOTS:
        out.extend(sorted(r.rglob("*.kt")))
    return out


def find(rel):
    """یک فایل با مسیرِ نسبی به `com/afghanjama` — از هر ماژولی که باشد.

    مثال: ``find("data/Margin.kt")`` چه در `:core` باشد چه در `:app`.
    """
    for r in PKG_ROOTS:
        p = r / rel
        if p.exists():
            return p
    raise FileNotFoundError(
        f"{rel} در هیچ‌کدام از ریشه‌ها نیست: {[str(r) for r in PKG_ROOTS]}"
    )


def glob(pattern):
    """الگوی فایل در هر دو ریشه — مثلِ ``glob("data/entities/*.kt")``."""
    import glob as _g
    out = []
    for r in PKG_ROOTS:
        out.extend(_g.glob(str(r / pattern)))
    return sorted(out)


def read(rel):
    """محتوای یک فایل با مسیرِ نسبی."""
    return find(rel).read_text(encoding="utf-8")


class _AnyRoot:
    """ریشه‌ای که نمی‌داند فایل در کدام ماژول است — و لازم هم ندارد بداند.

    بررسی‌ها همه به شکلِ ``ROOT / "data/Margin.kt"`` نوشته شده‌اند. با
    دو ماژول شدنِ پروژه، مسیرِ ثابت دیگر جواب نمی‌داد. این کلاس همان
    نوشتار را نگه می‌دارد ولی فایل را در هر ریشه‌ای که باشد پیدا می‌کند،
    و اگر نبود **بلند خطا می‌دهد** نه اینکه بی‌صدا رد شود.
    """

    def __truediv__(self, rel):
        return find(str(rel))

    def __add__(self, rel):
        return str(find(str(rel).lstrip("/")))

    def __str__(self):
        return str(PKG_ROOTS[-1])


#: ریشهٔ «هر ماژولی» — جایگزینِ مسیرِ ثابتِ قدیمی
ANY = _AnyRoot()


class _CoreRoot:
    """`:core` که حالا سه پوشه است ولی مثلِ یکی رفتار می‌کند.

    بررسی‌ها همه به شکلِ ``_src.CORE / "com/afghanjama/…"`` یا
    ``_src.CORE.rglob("*.kt")`` نوشته شده‌اند و آن نوشتار درست است —
    آنچه عوض شده این است که کاتلین کدِ یک ماژول را در چند پوشه
    می‌چیند. پس همان نوشتار می‌مانَد و جست‌وجو در هر سه انجام می‌شود.

    مثلِ [_AnyRoot]، فایلِ نبوده **بلند خطا می‌دهد** نه اینکه بی‌صدا رد
    شود.
    """

    def __truediv__(self, rel):
        rel = str(rel)
        for r in CORE_ROOTS:
            p = r / rel
            if p.exists():
                return p
        raise FileNotFoundError(
            f"{rel} در هیچ‌کدام از منبع‌های :core نیست: "
            f"{[str(r) for r in CORE_ROOTS]}"
        )

    def rglob(self, pattern):
        out = []
        for r in CORE_ROOTS:
            out.extend(r.rglob(pattern))
        return sorted(out)

    def exists(self):
        return any(r.exists() for r in CORE_ROOTS)

    def __str__(self):
        return " + ".join(str(r) for r in CORE_ROOTS)


#: `:core` — سه پوشه، یک ماژول
CORE = _CoreRoot()


def rel_to_core(path):
    """مسیرِ نسبیِ یک فایلِ `:core`، از هر کدام از سه پوشه که باشد.

    جایگزینِ `p.relative_to(CORE)` که وقتی ریشه یکی بود کار می‌کرد.
    """
    for r in CORE_ROOTS:
        try:
            return path.relative_to(r)
        except ValueError:
            continue
    return path
