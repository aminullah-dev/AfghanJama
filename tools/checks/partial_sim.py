#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""جریانِ جزئیِ سفارش: هر عدد که دوخته شد جلو می‌رود، نه یک عددِ خیالی.

اشکالِ اصلی این بود که تأییدِ نظارت کلِ `order.qty` را وارد انبار می‌کرد:
۱۰ عدد برش، ۵ عدد دوخته، ۱۰ عدد وارد انبار — و ۵ تای خیالی فروخته می‌شد.

اینجا همان ریاضی به‌صورت مستقل بازنویسی و سنجیده می‌شود، و مهم‌تر:
نسخه‌های **خراب** هم اجرا می‌شوند تا معلوم شود ادعاها واقعاً چیزی
می‌گیرند و صرفاً همیشه سبز نیستند.
"""
import pathlib as _pl
import random
import sys

ROOT = _pl.Path(__file__).resolve().parents[2] / "app/src/main/java/com/afghanjama"
fails = []


def check(c, m):
    if not c:
        fails.append(m)


# ---------------------------------------------------------------- ریاضی
def ready_to_send(qty, sewn, in_review, stored, *, resend_all=False):
    if resend_all:                       # خرابی: عددهای رفته دوباره شمرده شوند
        return max(sewn, 0)
    accounted = in_review + stored
    return max(min(sewn - accounted, qty - accounted), 0)


def wage_of_first(batches, n, *, spread_wage=False):
    if n <= 0:
        return 0
    if spread_wage:                      # خرابی: دستمزد سرشکن شود
        total = sum(q * w for q, w, _ in batches)
        whole = sum(q for q, _, _ in batches)
        return total * n // whole if whole else 0
    left, out = n, 0
    for q, w, _ in sorted(batches, key=lambda b: b[2]):
        if left <= 0:
            break
        take = min(left, q)
        out += take * w
        left -= take
    return out


def deposit_value(qty, stored, batch, fixed, sewn_cost, batches, stored_cost,
                  *, full_cost=False, no_sweep=False, spread_wage=False):
    if qty <= 0 or batch <= 0:
        return 0
    if full_cost:                        # خرابی: هر دسته کلِ بها را ببرد
        return fixed + sewn_cost
    cum = min(max(stored + batch, 0), qty)
    if cum >= qty and not no_sweep:
        target = fixed + sewn_cost
    else:                                # خرابی: آخرین دسته ته‌مانده را جمع نکند
        target = fixed * cum // qty + wage_of_first(batches, cum, spread_wage=spread_wage)
    return max(target - stored_cost, 0)


class Order:
    """همان حالتی که `Repo` روی سفارش نگه می‌دارد."""

    def __init__(self, qty, fixed, **broken):
        self.qty, self.fixed, self.broken = qty, fixed, broken
        self.in_review = self.stored = self.stored_cost = self.sewn_cost = 0
        self.stock_qty = self.stock_value = 0
        self.wip = fixed
        self.status = "SEWING"
        self.sewn = []
        self.clock = 0

    def sew(self, n, unit_wage):
        self.sewn.append((n, unit_wage, self.clock))
        self.clock += 1
        self.sewn_cost += n * unit_wage
        self.wip += n * unit_wage

    def rework_wage(self, amount):
        """دستمزد بی‌عددِ تازه. روی سفارشِ بسته اثری ندارد — در اپ هم ندارد."""
        if self.status == "STORED" or amount <= 0:
            return
        self.sewn_cost += amount
        self.wip += amount

    def send(self):
        if self.status != "SEWING":
            return False
        newly = ready_to_send(
            self.qty, sum(q for q, _, _ in self.sewn), self.in_review, self.stored,
            resend_all=self.broken.get("resend_all", False),
        )
        if newly <= 0:
            return False
        self.in_review += newly
        self.status = "REVIEW"
        return True

    def approve(self):
        if self.status != "REVIEW":
            return 0
        if self.broken.get("whole_order"):   # خرابیِ اصلی: کلِ سفارش وارد انبار
            batch = self.qty
        else:
            batch = min(self.in_review, self.qty - self.stored)
        if batch <= 0:
            return 0
        value = deposit_value(
            self.qty, self.stored, batch, self.fixed, self.sewn_cost,
            self.sewn, self.stored_cost,
            full_cost=self.broken.get("full_cost", False),
            no_sweep=self.broken.get("no_sweep", False),
            spread_wage=self.broken.get("spread_wage", False),
        )
        self.stock_qty += batch
        self.stock_value += value
        self.wip -= value
        self.stored += batch
        self.stored_cost += value
        if not self.broken.get("keep_review"):   # خرابی: دستهٔ رفته پاک نشود
            self.in_review = 0
        self.status = "STORED" if self.stored >= self.qty else "SEWING"
        return batch

    def reject(self):
        self.in_review = 0
        self.status = "SEWING"


# ---------------------------------------------------------------- ادعاها
def run(**broken):
    bad = []

    def a(c, n):
        if not c:
            bad.append(n)

    # سفارشِ ۱۰تایی که تکه‌تکه تمام می‌شود؛ بهای هر عدد ۲۰۰
    o = Order(10, 1_000, **broken)
    o.sew(3, 100)
    a(o.send(), "ارسالِ جزئی رد شد")
    a(o.approve() == 3, "تأیید تعدادِ دیگری وارد کرد")
    a(o.stock_qty == 3, "انبار ۳ عدد نگرفت")
    a(o.stock_value == 600, "بهای دستهٔ اول نسبتی نبود")
    a(o.status == "SEWING", "سفارشِ نیمه‌کاره بسته شد")
    o.sew(2, 100)
    o.send()
    o.approve()
    a(o.stock_qty == 5 and o.stock_value == 1_000, "دستهٔ دوم درست ننشست")
    o.sew(5, 100)
    o.send()
    o.approve()
    a(o.stock_qty == 10, "جمعِ دسته‌ها با کلِ سفارش نخواند")
    a(o.stock_value == 2_000, "جمعِ بها با کلِ بها نخواند")
    a(o.status == "STORED", "سفارشِ تمام‌شده بسته نشد")
    a(o.wip == 0, "«کار در جریان» صفر نشد")

    # ارسال و تأییدِ دوباره بی‌چیزِ تازه
    o = Order(10, 1_000, **broken)
    o.sew(4, 100)
    o.send()
    a(not o.send(), "ارسالِ دوباره همان عددها را فرستاد")
    o.approve()
    a(o.approve() == 0, "تأییدِ دوباره دسته را دوباره وارد کرد")
    a(o.stock_qty == 4, "موجودی بیش از دوخته‌شده شد")
    a(ready_to_send(10, 4, 4, 0, **{k: v for k, v in broken.items()
                                    if k == "resend_all"}) == 0,
      "شمارش عددهای دستِ نظارت را دوباره شمرد")

    # یکجا کامل شدن — مثلِ امروز
    o = Order(6, 3_000, **broken)
    o.sew(6, 250)
    o.send()
    a(o.approve() == 6 and o.stock_value == 4_500, "سفارشِ یکجا عوض شد")
    a(o.wip == 0, "«کار در جریان» سفارشِ یکجا صفر نشد")

    # برگشت از نظارت
    o = Order(8, 800, **broken)
    o.sew(5, 100)
    o.send()
    o.reject()
    a(o.stock_qty == 0, "برگشت چیزی وارد انبار کرد")
    a(o.send() and o.approve() == 5, "پس از اصلاح همان ۵ عدد نرفت")

    # خیاطِ گران و خیاطِ ارزان
    o = Order(4, 400, **broken)
    o.sew(2, 500)
    o.send()
    o.approve()
    a(o.stock_value == 1_200, "دستهٔ خیاطِ گران بهای خودش را نبرد")
    o.sew(2, 100)
    o.send()
    o.approve()
    a(o.stock_value == 1_600 and o.wip == 0, "جمع با دو نرخ نخواند")

    # دسته‌ای که وسطِ بازرسیِ دستهٔ قبل تمام می‌شود: هر دسته بهای خودش
    o = Order(4, 400, **broken)
    o.sew(2, 500)          # خیاطِ گران، رفت به نظارت
    o.send()
    o.sew(2, 100)          # خیاطِ ارزان، همان موقع تمام کرد
    o.approve()            # ناظر دستهٔ اول را تأیید می‌کند
    a(o.stock_value == 1_200, "دستهٔ زیرِ بازرسی بهای دستهٔ بعدی را گرفت")
    o.send()
    o.approve()
    a(o.stock_value == 1_600 and o.wip == 0, "جمع پس از دو دستهٔ هم‌زمان نخواند")

    # دستمزدی که پس از رفتنِ دسته‌های اول می‌رسد
    o = Order(5, 500, **broken)
    o.sew(3, 100)
    o.send()
    o.approve()
    o.sew(2, 100)
    o.rework_wage(150)     # اصلاحِ کارِ برگشتی — دستمزدِ تازه بی‌عددِ تازه
    o.send()
    a(o.approve() == 2, "دستهٔ آخر تعدادِ دیگری بود")
    a(o.stock_qty == 5, "دستمزدِ دیررس تعدادِ انبار را بالا برد")
    a(o.stock_value == 1_150, "دستمزدِ دیررس در بهای انبار ننشست")
    a(o.wip == 0, "دستمزدِ دیررس در «کار در جریان» جا ماند")

    # ته‌ماندهٔ تقسیم
    o = Order(3, 1_000, **broken)
    for _ in range(3):
        o.sew(1, 0)
        o.send()
        o.approve()
    a(o.stock_value == 1_000 and o.wip == 0, "ته‌ماندهٔ تقسیم گم شد")

    # صدها حالتِ تصادفی
    rnd = random.Random(271828)
    for _ in range(600):
        qty = rnd.randint(1, 30)
        o = Order(qty, rnd.randint(0, 50_000), **broken)
        left = qty
        while left > 0:
            n = min(left, rnd.randint(1, 5))
            o.sew(n, rnd.randint(0, 400))
            left -= n
            if rnd.randint(0, 2) != 0:
                o.send()
                if rnd.randint(0, 5) == 0:
                    o.reject()
                    o.rework_wage(rnd.randint(0, 400))
                else:
                    o.approve()
        o.send()
        o.approve()
        if o.stock_qty != qty:
            a(False, "حالتِ تصادفی: انبار به تعدادِ سفارش نرسید")
            break
        if o.wip != 0:
            a(False, "حالتِ تصادفی: «کار در جریان» صفر نشد")
            break
    return bad


check(not run(), f"قاعدهٔ درست ادعا رد کرد: {run()}")

BROKEN = {
    "تأیید کلِ سفارش را وارد انبار کند": dict(whole_order=True),
    "هر دسته کلِ بهای سفارش را ببرد": dict(full_cost=True),
    "آخرین دسته ته‌ماندهٔ بها را جمع نکند": dict(no_sweep=True),
    "دستهٔ تأییدشده از دستِ نظارت پاک نشود": dict(keep_review=True),
    "عددهای رفته دوباره فرستاده شوند": dict(resend_all=True),
    "دستمزد بینِ همهٔ دسته‌ها سرشکن شود": dict(spread_wage=True),
}
for label, kw in BROKEN.items():
    check(len(run(**kw)) > 0, f"نسخهٔ خرابِ «{label}» گرفته نشد")

# ---------------------------------------------------------------- اتصال‌ها
src = (ROOT / "data/PartialFlow.kt").read_text(encoding="utf-8")
check("import android" not in src, "PartialFlow وابستگیِ اندرویدی گرفت")
for f in ("readyToSend", "wageOfFirst", "depositValue"):
    check(f"fun {f}(" in src, f"{f} نیست")

repo = (ROOT / "data/repo/Repo.kt").read_text(encoding="utf-8")
check("depositBatchToFinished" in repo, "ورودِ دسته‌ای به انبار نیست")
check("depositOrderToFinished" not in repo, "مسیرِ قدیمیِ «کلِ سفارش وارد انبار» هنوز هست")
check("addFinishedStock(order.designTitle, order.size, batch," in repo,
      "انبار هنوز با تعدادِ کلِ سفارش پر می‌شود")
check("fun sendOrderToReview(" in repo, "ارسال به نظارت از Repo نمی‌گذرد")

order = (ROOT / "data/entities/Order.kt").read_text(encoding="utf-8")
for col in ("reviewQty", "storedQty", "storedCost"):
    check(f"val {col}" in order, f"ستونِ {col} روی سفارش نیست")

mig = (ROOT / "data/Migrations.kt").read_text(encoding="utf-8")
for col in ("reviewQty", "storedQty", "storedCost"):
    check(f"`{col}`" in mig, f"مهاجرتِ ستونِ {col} نیست")

vm = (ROOT / "ui/vm/SewingViewModel.kt").read_text(encoding="utf-8")
check("sewn < o.qty" not in vm, "نگهبانِ موقتِ «پس از دوختِ همه» هنوز سرِ جایش است")
screen = (ROOT / "ui/screens/SewingScreen.kt").read_text(encoding="utf-8")
check("هرچه آماده است" in screen, "برچسبِ دکمه برنگشته")
check("پس از دوختِ همه" not in screen, "برچسبِ نگهبان هنوز روی دکمه است")

st = (ROOT / "selftest/SelfTest.kt").read_text(encoding="utf-8")
check("fun checkPartialReview(" in st, "بررسیِ خودآزمایی نیست")
for p in [ROOT / "ui/vm/SelfTestViewModel.kt",
          _pl.Path(__file__).resolve().parents[2] / "app/src/test/java/com/afghanjama/SelfTestJvmTest.kt"]:
    check("checkPartialReview(" in p.read_text(encoding="utf-8"), f"{p.name} صدا نمی‌زند")

if fails:
    print(f"✗ {len(fails)} اشکال")
    [print("  -", f) for f in fails]
    sys.exit(1)
print(f"✓ جریان جزئی: قاعده درست و هر {len(BROKEN)} نسخهٔ خراب گرفته شد")
