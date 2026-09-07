#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""اقساط: پولی که مشتری داده، از قدیمی‌ترین قسط به بعد پخش می‌شود.

جدولِ `customer_installments` عمداً پرچمِ «پرداخت شد» ندارد — تسویه از
`customer_payments` مشتق می‌شود. یعنی کلِ درستیِ این قابلیت روی یک تابع
سوار است: `Installments.allocate`.

این بررسی همان قاعده را **مستقل از کد** پیاده می‌کند و چهار ثابت را
می‌سنجد:

  ۱ جمع    — پول نه ساخته می‌شود نه گم
  ۲ کران   — هیچ قسطی بیش از مبلغِ خودش نمی‌گیرد
  ۳ ترتیب  — ترتیبِ ورودی جواب را عوض نمی‌کند
  ۴ یکنوایی — پرداختِ بیشتر هیچ قسطی را کم‌تر-پرداخت نمی‌کند

و ثابتِ پنجم که تفاوتِ اصلی است: با ۱۰۰ افغانی روی دو قسطِ ۱۰۰تایی،
پول به قسطِ **زودتر** می‌رسد. اگر برعکس پخش شود، قسطِ گذشته برای همیشه
سررسیدگذشته می‌مانَد در حالی که مشتری پولش را داده.
"""
import random
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src

fails = []


def check(cond, msg):
    if not cond:
        fails.append(msg)


# ── قاعده، مستقل از کد ───────────────────────────────────────────
def allocate(schedule, total_paid, *, newest_first=False,
             no_amount_clamp=False, no_pool_clamp=False, unsorted=False):
    """schedule: [(id, due, amount)] → [(id, applied)]"""
    rows = schedule if unsorted else sorted(
        schedule, key=lambda r: (r[1], r[0]), reverse=newest_first)
    pool = max(total_paid, 0)
    out = []
    for (i, _due, amt) in rows:
        if no_amount_clamp:
            applied = pool
        elif no_pool_clamp:
            applied = max(amt, 0)
        else:
            applied = min(pool, max(amt, 0))
        pool -= applied
        out.append((i, applied))
    return out


random.seed(7)


def gen(n):
    return [(k, random.randint(1, 40), random.randint(1, 5000)) for k in range(n)]


TRIALS = 3000
broken = {k: 0 for k in ("newest_first", "no_amount_clamp", "no_pool_clamp", "unsorted")}

for _ in range(TRIALS):
    sch = gen(random.randint(0, 6))
    total = random.randint(0, 30000)
    res = allocate(sch, total)
    amt = {i: a for i, _, a in sch}

    check(sum(a for _, a in res) == min(total, sum(amt.values())),
          f"جمع نخواند: {sch} با {total}")
    check(all(0 <= a <= amt[i] for i, a in res),
          f"قسطی بیش از مبلغش گرفت: {sch} با {total}")

    shuffled = sch[:]
    random.shuffle(shuffled)
    check(dict(allocate(shuffled, total)) == dict(res),
          f"ترتیبِ ورودی جواب را عوض کرد: {sch} با {total}")

    more = dict(allocate(sch, total + random.randint(1, 900)))
    check(all(more[i] >= a for i, a in res),
          f"پرداختِ بیشتر یک قسط را کم‌تر-پرداخت کرد: {sch} با {total}")

    # هر نسخهٔ خراب باید دستِ‌کم گاهی جوابِ دیگری بدهد
    for kind in broken:
        if dict(allocate(sch, total, **{kind: True})) != dict(res):
            broken[kind] += 1

for kind, hits in broken.items():
    check(hits > 0,
          f"نسخهٔ خرابِ «{kind}» هیچ‌وقت جوابِ متفاوت نداد — بررسی پوچ است")

# ── قدیمی‌ترین-اول، صریح ─────────────────────────────────────────
two = [(1, 10, 100), (2, 20, 100)]
check(dict(allocate(two, 100)) == {1: 100, 2: 0},
      "۱۰۰ افغانی باید به قسطِ زودتر برسد")
check(dict(allocate(two, 100, newest_first=True)) == {2: 100, 1: 0},
      "نسخهٔ تازه‌ترین-اول باید فرق کند وگرنه ترتیب بی‌معناست")

# ── لبه‌ها ───────────────────────────────────────────────────────
check(allocate([], 500) == [], "قسطِ خالی نباید چیزی بسازد")
check(dict(allocate(two, 0)) == {1: 0, 2: 0}, "بی پرداخت، هیچ قسطی سهمی ندارد")
check(dict(allocate(two, -5)) == {1: 0, 2: 0}, "پرداختِ منفی باید صفر حساب شود")
check(dict(allocate(two, 10**9)) == {1: 100, 2: 100},
      "پرداختِ بیش از جمعِ قسط‌ها نباید از سقفِ هر قسط بگذرد")

# ── و حالا: کد همین را می‌گوید؟ ──────────────────────────────────
SRC = (_src.CORE / "com/afghanjama/data/Installments.kt").read_text(encoding="utf-8")

check("fun allocate" in SRC, "Installments.allocate پیدا نشد")

body = re.search(r"fun allocate\([^)]*\)[^{]*\{(.*?)\n    \}", SRC, re.S)
check(body is not None, "بدنهٔ allocate خوانده نشد")
if body:
    b = body.group(1)
    check("sortedWith" in b and "compareBy" in b,
          "allocate باید خودش مرتب کند، وگرنه ترتیبِ ورودی جواب را عوض می‌کند")
    check("it.dueDate" in b and "it.id" in b,
          "مرتب‌سازی باید روی dueDate و بعد id باشد تا جواب قطعی بماند")
    check("coerceAtMost" in b,
          "سهمِ هر قسط باید به مبلغِ خودش محدود شود")
    # دو نگهبانِ جدا، و جدا هم سنجیده می‌شوند: بارِ اول این بند یکی
    # `coerceAtLeast(0` می‌خواست، و چون آن یکی نگهبان همان شکل را
    # داشت، برداشتنِ نگهبانِ پرداخت از چشمش می‌افتاد.
    check("totalPaid.coerceAtLeast(0" in b,
          "پرداختِ منفی باید به صفر برسد، وگرنه استخر منفی می‌شود")
    check("amount.coerceAtLeast(0" in b,
          "مبلغِ منفیِ قسط باید به صفر برسد، وگرنه سهم منفی می‌گیرد")
    check("sortedByDescending" not in b and "reversed()" not in b,
          "allocate نباید برعکس مرتب کند — پول اول به قسطِ زودتر می‌رسد")

# ── جدول در نقشهٔ ریست دسته‌بندی شده باشد ────────────────────────
PLAN = (_src.CORE / "com/afghanjama/data/ResetPlan.kt").read_text(encoding="utf-8")
check('"customer_installments"' in PLAN,
      "customer_installments در ResetPlan نیست — resetcheck هم می‌گیردش")

if fails:
    print(f"✗ {len(fails)} مشکل در اقساط:")
    for f in fails[:12]:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ اقساط: {TRIALS} برنامهٔ تصادفی، ۴ ثابت، و هر ۴ نسخهٔ خراب گرفته شد")
