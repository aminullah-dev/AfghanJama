#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""خرج‌کار: پول باید واقعاً از جایی برود و بدهی صاحب داشته باشد."""
import pathlib as _pl
import re
import sys

ROOT = _pl.Path(__file__).resolve().parents[2] / "app/src/main/java/com/afghanjama"
fails = []
def check(c, m):
    if not c: fails.append(m)

def post(work_cost, source, balance, *, no_cash=False, no_guard=False):
    """برمی‌گرداند: (ثبت شد؟, تغییرِ صندوق, تغییرِ بدهی, بدهکارِ کار در جریان)"""
    src = (source or "CREDIT").upper()
    cash = src != "CREDIT"
    if cash and not no_guard and work_cost > balance:
        return (False, 0, 0, 0)          # هیچ چیز ثبت نشود
    if cash and not no_cash:
        return (True, -work_cost, 0, work_cost)
    if cash and no_cash:                  # نسخهٔ خراب: ژورنال بزند ولی صندوق کم نشود
        return (True, 0, 0, work_cost)
    return (True, 0, work_cost, work_cost)

def run(**kw):
    bad = []
    def a(c, n):
        if not c: bad.append(n)
    # نقدی: صندوق کم شود و کار در جریان همان‌قدر بدهکار
    ok, cash, debt, wip = post(500, "WALLET", 2000, **kw)
    a(ok, "نقدیِ قابلِ پرداخت باید ثبت شود")
    a(cash == -500, f"صندوق باید ۵۰۰ کم شود، شد {cash}")
    a(wip == 500, "کار در جریان باید ۵۰۰ بدهکار شود")
    a(debt == 0, "نقدی نباید بدهی بسازد")
    a(cash + wip == 0, "سند باید تراز باشد")

    # نسیه: بدهی بسازد، صندوق دست نخورد
    ok2, cash2, debt2, wip2 = post(500, "CREDIT", 0, **kw)
    a(ok2 and cash2 == 0 and debt2 == 500 and wip2 == 500, "نسیه")
    a(debt2 - wip2 == 0, "سندِ نسیه باید تراز باشد")

    # نقدیِ بیش از موجودی: هیچ چیز ثبت نشود
    ok3, cash3, debt3, wip3 = post(500, "WALLET", 100, **kw)
    a(not ok3, "خرج بیش از موجودی نباید ثبت شود")
    a(cash3 == 0 and debt3 == 0 and wip3 == 0, "و هیچ اثری نگذارد")

    # صندوق هرگز منفی نشود
    for bal, amt in ((0, 1), (10, 11), (999, 1000)):
        okx, cx, _, _ = post(amt, "WALLET", bal, **kw)
        a(not okx or bal + cx >= 0, f"صندوق منفی شد: {bal}+{cx}")
    return bad

check(not run(), f"قاعدهٔ درست ادعا رد کرد: {run()}")
for label, kw in {
    "ژورنال بزند ولی صندوق کم نشود": dict(no_cash=True),
    "نگهبانِ موجودی نباشد": dict(no_guard=True),
}.items():
    check(len(run(**kw)) > 0, f"نسخهٔ خرابِ «{label}» گرفته نشد")

repo = (ROOT / "data/repo/Repo.kt").read_text(encoding="utf-8")
check("workCostSource" in repo, "منبعِ پرداخت در Repo خوانده نمی‌شود")
check("hasFunds(src, order.workCost)" in repo, "نگهبانِ موجودی برای خرج‌کار نیست")
check(re.search(r'spend\(\s*source = src', repo) is not None, "صندوق واقعاً کم نمی‌شود")
check('"SUPPLIER"' in repo and "workCostPayee" in repo, "بدهیِ خرج‌کار طرفِ حساب ندارد")
ent = (ROOT / "data/entities/Order.kt").read_text(encoding="utf-8")
for col in ("workCostSource", "workCostPayee"):
    check(col in ent, f"ستونِ {col} روی سفارش نیست")
mig = (ROOT / "data/Migrations.kt").read_text(encoding="utf-8")
check("MIGRATION_60_61" in mig, "مهاجرتِ ۶۰→۶۱ نیست")
vm = (ROOT / "ui/vm/ProductionViewModel.kt").read_text(encoding="utf-8")
check("workCostPayee.isBlank()" in vm, "نسیه بی نامِ طرف رد نمی‌شود")

if fails:
    print(f"✗ {len(fails)} اشکال"); [print("  -", f) for f in fails[:8]]; sys.exit(1)
print("✓ خرج‌کار: نقدی صندوق را کم می‌کند، نسیه صاحب دارد، و صندوق منفی نمی‌شود")
