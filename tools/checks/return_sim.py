#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""برگشت از فروش: هر عددِ گزارش خالص باشد، و برگشتی پنهان نشود.

**چرا لازم شد:** کارگاه ۲۰ عدد فروخته بود و ۲ عدد برگشت خورده بود.
همهٔ مراحل درست ثبت شده بود — انبار، پول، ژورنال — ولی صفحهٔ گزارش‌ها
همان ۲۰ را نشان می‌داد. یعنی دو نمای متفاوت از یک واقعیت در اپ بود:

  • صورت سود و زیان و ترازنامه از **ژورنال** می‌آیند و برگشت را
    می‌شناختند ✓
  • داشبورد از `netTotal` می‌خواند و می‌شناخت ✓
  • گزارش‌ها از `total`/`cost`/`qty`ِ ناخالص می‌خواندند ✗

سودِ گزارش‌شده از سودِ واقعی بیشتر بود، و بدتر از آن، دو صفحهٔ همین اپ
دو عدد می‌دادند.

قاعده‌ای که اینجا مستقل پیاده و بعد با کد سنجیده می‌شود:

  ۱ خالص = ناخالص − برگشتی، در تعداد و مبلغ و بهای تمام‌شده
  ۲ سهمِ برگشتی از روی **جمعِ تجمعی** حساب شود، نه «تعداد × فی»؛
    وگرنه ردیفِ تخفیف‌دار بیشتر از دریافتی پس می‌دهد و تقسیمِ صحیح
    ته‌مانده جا می‌گذارد
  ۳ برگشتِ کامل، خالص را دقیقاً صفر کند — نه یک افغانی کم یا زیاد
  ۴ برگشتی **پنهان نشود**: عددِ ناخالص هم در دسترس بماند

**شکافی که می‌دانیم هست:** بندهای سمتِ صفحه متن را می‌سنجند، پس اگر
کسی شرطِ نمایش را به `if (false)` عوض کند گرفته نمی‌شود — سطرها هنوز
در فایل‌اند. حذفِ بلوک، که پس‌رفتِ واقع‌بینانه است، گرفته می‌شود.
اندازه‌گیری شد، نه حدس؛ و بندی که برای همین حالتِ ساختگی سفت شود فقط
شکنندگی اضافه می‌کند.
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


class Sale:
    """یک ردیفِ فروش با برگشتی — آینهٔ `FinishedSale`."""

    def __init__(self, qty, total, cost, returned=0, *,
                 flat_share=False, gross_net=False, drop_gross=False):
        self.qty, self.total, self.cost, self.returned = qty, total, cost, returned
        self.flat_share = flat_share      # نسخهٔ خراب: «تعداد × فی»
        self.gross_net = gross_net        # نسخهٔ خراب: خالص = ناخالص
        self.drop_gross = drop_gross      # نسخهٔ خراب: ناخالص گم شود

    def share(self, amount, n):
        if self.qty <= 0 or n <= 0:
            return 0
        if self.flat_share:
            return amount // self.qty * n
        done = amount if self.returned >= self.qty else amount * self.returned // self.qty
        remaining = self.qty - self.returned
        if n >= remaining:
            return amount - done
        return amount * (self.returned + n) // self.qty - done

    def refund_for(self, n):
        return self.share(self.total, n)

    def cost_for(self, n):
        return self.share(self.cost, n)

    @property
    def net_total(self):
        if self.gross_net:
            return self.total
        if self.returned >= self.qty:
            return 0
        return self.total - self.total * self.returned // self.qty

    @property
    def net_cost(self):
        if self.gross_net:
            return self.cost
        if self.returned >= self.qty:
            return 0
        return self.cost - self.cost * self.returned // self.qty

    @property
    def net_qty(self):
        return self.qty if self.gross_net else self.qty - self.returned

    @property
    def gross_qty(self):
        # ناخالص باید همیشه در دسترس بماند، وگرنه کاربر که فاکتورها را
        # می‌شمارد عددِ دیگری می‌بیند و به گزارش شک می‌کند.
        return None if self.drop_gross else self.net_qty + self.returned


def run(**kw):
    bad = []

    def a(c, n):
        if not c:
            bad.append(n)

    # ── همان مثالِ کارگاه: ۲۰ فروش، ۲ برگشت ────────────────────
    s = Sale(20, 20_000, 12_000, returned=2, **kw)
    a(s.net_qty == 18, "تعدادِ خالص ۱۸ شود")
    a(s.net_total == 18_000, "درآمدِ خالص ۱۸٬۰۰۰ شود")
    a(s.net_cost == 10_800, "بهای تمام‌شدهٔ خالص کم شود")
    a(s.gross_qty == 20, "ناخالص پنهان نشود")

    # ── ردیفِ تخفیف‌دار: جمع ≠ تعداد × فی ───────────────────────
    d = Sale(3, 250, 150, **kw)          # ۳ عدد، جمعِ ۲۵۰ (تخفیف خورده)
    total_back = sum_returns(d, [1, 1, 1], **kw)
    a(total_back == 250, f"جمعِ برگشت‌ها دقیقاً ۲۵۰ شود، نه {total_back}")

    # ── تقسیمِ صحیح ته‌مانده جا نگذارد ──────────────────────────
    t = Sale(3, 100, 70, **kw)
    parts = returns_one_by_one(t, 3, **kw)
    a(sum(parts) == 100, f"جمعِ سه برگشت ۱۰۰ شود، نه {sum(parts)}")

    # ── برگشتِ کامل: خالص دقیقاً صفر ────────────────────────────
    f = Sale(5, 999, 501, returned=5, **kw)
    a(f.net_total == 0 and f.net_cost == 0, "برگشتِ کامل خالص را صفر کند")
    a(f.net_qty == 0, "تعدادِ خالصِ برگشتِ کامل صفر شود")

    # ── بدونِ برگشت هیچ چیزی عوض نشود ──────────────────────────
    n = Sale(7, 700, 400, **kw)
    a(n.net_total == 700 and n.net_cost == 400 and n.net_qty == 7,
      "بی برگشت، خالص همان ناخالص است")

    # ── جمعِ گزارش روی چند ردیف ────────────────────────────────
    rows = [Sale(20, 20_000, 12_000, 2, **kw), Sale(5, 5_000, 3_000, 5, **kw),
            Sale(4, 4_000, 2_000, 0, **kw)]
    a(sum(r.net_total for r in rows) == 22_000, "جمعِ درآمدِ خالص")
    a(sum(r.net_qty for r in rows) == 22, "جمعِ تعدادِ خالص")
    a(sum(r.total - r.net_total for r in rows) == 7_000, "جمعِ مبلغِ برگشتی")
    # فروشی که تمامش برگشته دیگر «یک فروش» نیست
    a(sum(1 for r in rows if r.returned < r.qty) == 2, "فروشِ کاملاً برگشته شمرده نشود")
    return bad


def sum_returns(sale, chunks, **kw):
    """برگشتِ چند مرحله‌ای؛ جمعِ پس‌دادنی‌ها باید دقیقاً جمعِ ردیف شود."""
    s = Sale(sale.qty, sale.total, sale.cost, 0, **kw)
    got = 0
    for c in chunks:
        got += s.refund_for(c)
        s.returned += c
    return got


def returns_one_by_one(sale, n, **kw):
    s = Sale(sale.qty, sale.total, sale.cost, 0, **kw)
    out = []
    for _ in range(n):
        out.append(s.refund_for(1))
        s.returned += 1
    return out


check(not run(), f"قاعدهٔ درست ادعا رد کرد: {run()}")

BROKEN = {
    "سهمِ برگشتی «تعداد × فی» حساب شود": dict(flat_share=True),
    "خالص همان ناخالص بماند (اشکالِ اصلی)": dict(gross_net=True),
    "عددِ ناخالص از گزارش حذف شود": dict(drop_gross=True),
}
for label, kw in BROKEN.items():
    check(len(run(**kw)) > 0, f"نسخهٔ خرابِ «{label}» گرفته نشد")

# ── حالا همان قاعده را در کد بسنجیم ────────────────────────────
sale = (ROOT / "data/entities/FinishedSale.kt").read_text(encoding="utf-8")
for f in ("netTotal", "netCost", "returnableQty", "refundFor", "costFor"):
    check(f in sale, f"FinishedSale.{f} نیست")

vm = (ROOT / "ui/vm/ReportsViewModel.kt").read_text(encoding="utf-8")
check("netTotal" in vm and "netCost" in vm,
      "ReportsViewModel هنوز از عددِ ناخالص می‌خواند")
# **اعلانِ فیلد کافی نیست، باید پر هم بشود.** نسخهٔ اولِ این دو بند فقط
# دنبالِ نامِ `salesReturned` در فایل می‌گشتند؛ وقتی سطرِ مقداردهی را
# عمداً برداشتم، نام هنوز در تعریفِ `ReportData` بود و بررسی سبز ماند.
check("salesReturned = salesReturned" in vm,
      "ReportData مبلغِ برگشتی را نمی‌گیرد — فیلد هست ولی پر نمی‌شود")
check("returnedQty = returnedQty" in vm,
      "ReportData تعدادِ برگشتی را نمی‌گیرد")
# اینها همان جاهایی‌اند که عددِ ۲۰ را نشان می‌دادند
for bad in ("sumOf { it.total }", "sumOf { it.cost }", "sumOf { it.qty }"):
    check(bad not in vm, f"ReportsViewModel هنوز `{bad}` دارد — ناخالص")

screen = (ROOT / "ui/screens/ReportsScreen.kt").read_text(encoding="utf-8")
# همان درس: شرطِ واقعی سنجیده می‌شود، نه صرفِ وجودِ نام. با عوض کردنِ
# شرط به `if (false)` نسخهٔ اول سبز مانده بود.
check("r.salesReturned > 0" in screen,
      "صفحهٔ گزارش برگشتی را نشان نمی‌دهد — شرطِ نمایش عوض شده")
check("برگشت از فروش" in screen, "سطرِ «کسر: برگشت از فروش» در صفحه نیست")
check("p.hasReturns" in screen,
      "صفحهٔ گزارش ناخالص/برگشت/خالص را کنارِ هم نمی‌آورد")

dash = (ROOT / "ui/vm/DashboardViewModel.kt").read_text(encoding="utf-8")
check("netTotal" in dash, "داشبورد از عددِ ناخالص می‌خواند")

repo = (ROOT / "data/repo/Repo.kt").read_text(encoding="utf-8")
check("fun recordSaleReturn(" in repo, "ثبتِ برگشت از فروش نیست")
check("SALE_RETURN" in repo, "برگشت در ژورنال ثبت نمی‌شود")

if fails:
    print(f"✗ {len(fails)} اشکال")
    [print("  -", f) for f in fails]
    sys.exit(1)
print(f"✓ برگشت از فروش: قاعده درست و هر {len(BROKEN)} نسخهٔ خراب گرفته شد")
