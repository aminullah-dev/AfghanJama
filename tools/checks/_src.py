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

REPO = pathlib.Path(__file__).resolve().parents[2]

#: ریشه‌های سورس، به ترتیبِ جست‌وجو
ROOTS = [
    REPO / "core/src/main/kotlin",
    REPO / "app/src/main/java",
]

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
