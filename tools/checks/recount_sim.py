#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""شمارش انبار محصول: تعداد و دفتر با هم تکان می‌خورند، نه یکی بی دیگری.

قاعدهٔ اصلی که این اسکریپت نگه می‌دارد: هر تغییری در ارزشِ ردیفِ انبار
باید دقیقاً همان مبلغی باشد که به حسابِ «موجودی محصول» (۱۰۵۰) می‌رود.
اگر این دو از هم جدا شوند، بررسیِ «حسابِ موجودی محصول = ارزشِ واقعیِ
انبار» در خودآزمایی برای همیشه قرمز می‌مانَد و دیگر هیچ‌وقت هم خودش
درست نمی‌شود.
"""
import pathlib as _pl
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src


ROOT = _src.ANY
fails = []


def check(c, m):
    if not c:
        fails.append(m)


def take_from_stock(qty, total_value, avg_cost, take):
    """همان Repo.takeFromStock — بهای خروج و ارزشِ باقی‌مانده."""
    if take <= 0:
        return 0, total_value
    available = max(qty, 0)
    if take <= available:
        if take >= qty:
            return total_value, 0
        cost = total_value * take // qty
        return cost, total_value - cost
    from_stock = total_value if available > 0 else 0
    excess = take - available
    cost = from_stock + excess * avg_cost
    return cost, total_value - cost


def recount(qty, total_value, avg_cost, counted, *,
            no_journal=False, keep_residual=False, drop_value=False):
    """همان Repo.adjustFinishedStock.

    برمی‌گرداند: (تعدادِ تازه، ارزشِ تازه، مبلغی که به حسابِ ۱۰۵۰ می‌رود)
    """
    target = max(counted, 0)
    if target < qty:
        if drop_value:
            new_value = total_value          # نسخهٔ خراب: تعداد رفت، ارزش ماند
        else:
            _, new_value = take_from_stock(qty, total_value, avg_cost, qty - target)
    elif target > qty:
        new_value = total_value + (target - qty) * avg_cost
    else:
        # ردیفِ خالی نباید ارزشِ ته‌مانده نگه دارد
        new_value = total_value if (target != 0 or keep_residual) else 0

    value_delta = new_value - total_value
    if target == qty and value_delta == 0:
        return qty, total_value, 0
    journal = 0 if no_journal else value_delta
    return target, new_value, journal


def run(**kw):
    bad = []

    def a(c, n):
        if not c:
            bad.append(n)

    # ---- ۱. همان دردی که کاربر دارد: ۱۰ ثبت شده، ۵ تا واقعی است ----
    q, v, j = recount(10, 10_000, 1_000, 5, **kw)
    a(q == 5, "تعداد به شمرده‌شده نرسید")
    a(v == 5_000, "ارزش با تعداد جلو نرفت")
    a(j == -5_000, "سندِ ۱۰۵۰ اندازهٔ تغییرِ ارزش نبود")

    # ---- ۲. قاعدهٔ مرکزی: دفتر و انبار هرگز از هم جدا نشوند ----
    for start_q, start_v, avg, counted in [
        (10, 10_000, 1_000, 5), (7, 3_333, 476, 0), (3, 1_000, 333, 9),
        (1, 999, 999, 0), (12, 7_777, 648, 11), (5, 0, 0, 2),
    ]:
        nq, nv, jr = recount(start_q, start_v, avg, counted, **kw)
        a(nv - start_v == jr,
          f"ارزش و سند نخواندند ({start_q}→{counted}): {nv - start_v} در برابر {jr}")

    # ---- ۳. ردیفی که صفر می‌شود نباید ته‌ماندهٔ ارزش جا بگذارد ----
    # این همان جایی است که تقسیمِ صحیح چند افغانی گم می‌کرد.
    for start_q, start_v, avg in [(3, 1_000, 333), (7, 3_333, 476), (11, 5_000, 454)]:
        nq, nv, _ = recount(start_q, start_v, avg, 0, **kw)
        a(nq == 0 and nv == 0,
          f"ردیفِ صفرشده ارزشِ {nv} جا گذاشت (از {start_q} عدد و {start_v} ؋)")

    # ---- ۴. ارزشِ ته‌ماندهٔ ردیفِ از قبل خالی هم پاک شود ----
    nq, nv, jr = recount(0, 250, 250, 0, **kw)
    a(nv == 0 and jr == -250, "ارزشِ ته‌ماندهٔ ردیفِ خالی پاک نشد")

    # ---- ۵. پیدا شدنِ اضافه: هزینهٔ منفی ----
    q, v, j = recount(4, 4_000, 1_000, 6, **kw)
    a(q == 6 and v == 6_000 and j == 2_000, "اضافهٔ پیداشده درست ارزش نگرفت")

    # ---- ۶. شمارشِ بی‌تغییر نباید سند بزند ----
    q, v, j = recount(8, 8_000, 1_000, 8, **kw)
    a(j == 0, "شمارشِ بی‌تغییر سندِ بیهوده زد")

    # ---- ۷. عددِ منفی به صفر بچسبد، نه اینکه ردیف را منفی کند ----
    q, _, _ = recount(5, 5_000, 1_000, -3, **kw)
    a(q == 0, "تعدادِ منفی صفر نشد")

    return bad


check(not run(), f"قاعدهٔ درست ادعا رد کرد: {run()}")

BROKEN = {
    "تعداد کم شود ولی سندی زده نشود": dict(no_journal=True),
    "ردیفِ خالی ارزشِ ته‌مانده نگه دارد": dict(keep_residual=True),
    "تعداد برود ولی ارزش سرِ جایش بماند": dict(drop_value=True),
}
for label, kw in BROKEN.items():
    check(len(run(**kw)) > 0, f"نسخهٔ خرابِ «{label}» گرفته نشد")

# ---- کد واقعاً همین کار را می‌کند؟ ----
repo = (ROOT / "data/repo/Repo.kt").read_text(encoding="utf-8")
check("suspend fun adjustFinishedStock(" in repo, "adjustFinishedStock در Repo نیست")
body = repo.split("suspend fun adjustFinishedStock(", 1)[1][:2600]
check("takeFromStock(" in body, "ارزش‌گذاری از takeFromStock نمی‌آید")
check("Accounts.FINISHED" in body and "Accounts.EXPENSES" in body,
      "سندِ اصلاح دو طرفِ درست را ندارد")
check("FINISHED_ADJUST" in body, "سند نوعِ مرجعِ خودش را ندارد")
check("audit(" in body, "اصلاحِ انبار در دفترِ رویدادها ثبت نمی‌شود")

vm = (ROOT / "ui/vm/FinishedSaleViewModel.kt").read_text(encoding="utf-8")
check("fun recount(" in vm, "ViewModel راهِ شمارش ندارد")
check("adjustFinishedStock(" in vm, "ViewModel به Repo وصل نیست")

screen = (ROOT / "ui/screens/FinishedWarehouseScreen.kt").read_text(encoding="utf-8")
check("countTarget" in screen and "vm.recount(" in screen, "صفحهٔ انبار دکمهٔ شمارش ندارد")

if fails:
    print(f"✗ {len(fails)} اشکال")
    [print("  -", f) for f in fails]
    sys.exit(1)
print(f"✓ شمارش انبار محصول: قاعده درست و هر {len(BROKEN)} نسخهٔ خراب گرفته شد")
