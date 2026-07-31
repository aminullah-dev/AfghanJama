#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""سود فروش: نه زیانِ پنهان، نه درصدِ ساختگی."""
import pathlib as _pl
import sys

ROOT = _pl.Path(__file__).resolve().parents[2] / "app/src/main/java/com/afghanjama"
fails = []
def check(c, m):
    if not c: fails.append(m)

def line(cost, price, qty, *, pct_on_price=False, fake_pct=False, cost_is_loss=False):
    profit = price * qty - cost * qty
    if cost > 0:
        base = price if (pct_on_price and price) else cost
        pct = int((price - cost) * 100 / base)
    else:
        pct = 0 if fake_pct else None
    losing = (cost > 0 and 0 < price < cost) or (cost_is_loss and cost == 0)
    return dict(profit=profit, pct=pct, losing=losing, unknown=cost <= 0)

def invoice(rows, disc, **kw):
    ls = [line(c, p, q, **kw) for c, p, q in rows]
    cost = sum(c * q for c, _, q in rows)
    rev = sum(p * q for _, p, q in rows) - disc
    return dict(cost=cost, rev=rev, profit=rev - cost,
                unknown=any(l["unknown"] for l in ls),
                losing=rev - cost < 0,
                pct=int((rev - cost) * 100 / cost) if cost > 0 else None)

def run(**kw):
    bad = []
    def a(c, n):
        if not c: bad.append(n)
    r = line(100, 120, 3, **kw)
    a(r["profit"] == 60, "سودِ ساده"); a(r["pct"] == 20, "درصد نسبت به بها")
    l = line(100, 80, 2, **kw)
    a(l["profit"] == -40, "زیان"); a(l["pct"] == -20, "درصدِ منفی"); a(l["losing"], "علامتِ زیان")
    u = line(0, 500, 1, **kw)
    a(u["pct"] is None, "بی‌بها درصد ندهد"); a(u["unknown"], "علامتِ نامعلوم")
    a(not u["losing"], "بی‌بها زیان شمرده نشود")
    e = line(100, 100, 1, **kw)
    a(e["profit"] == 0 and e["pct"] == 0 and not e["losing"], "سربه‌سر")
    i = invoice([(100,150,2),(200,250,1)], 0, **kw)
    a(i["cost"] == 400 and i["rev"] == 550 and i["profit"] == 150, "جمعِ فاکتور")
    d = invoice([(100,150,2)], 60, **kw)
    a(d["cost"] == 200 and d["rev"] == 240 and d["profit"] == 40, "تخفیف از سود کم شود")
    z = invoice([(100,120,2)], 80, **kw)
    a(z["profit"] == -40 and z["losing"], "تخفیفِ زیاد زیان بسازد")
    m = invoice([(100,150,1),(0,300,1)], 0, **kw)
    a(m["unknown"], "ردیفِ بی‌بها پنهان نماند")
    return bad

check(not run(), f"قاعدهٔ درست ادعا رد کرد: {run()}")
BROKEN = {
    "درصد نسبت به قیمتِ فروش حساب شود": dict(pct_on_price=True),
    "بی بهای تمام‌شده درصدِ ساختگی داده شود": dict(fake_pct=True),
    "بهای نامعلوم زیان شمرده شود": dict(cost_is_loss=True),
}
for label, kw in BROKEN.items():
    check(len(run(**kw)) > 0, f"نسخهٔ خرابِ «{label}» گرفته نشد")

src = (ROOT / "data/Margin.kt").read_text(encoding="utf-8")
check("import android" not in src, "Margin وابستگیِ اندرویدی گرفت")
for f in ("profit", "percent", "losing", "unknownCost", "hasUnknownCost"):
    check(f in src, f"{f} نیست")
st = (ROOT / "selftest/SelfTest.kt").read_text(encoding="utf-8")
check("fun checkMargin(" in st, "بررسیِ خودآزمایی نیست")
for p in [ROOT / "ui/vm/SelfTestViewModel.kt",
          _pl.Path(__file__).resolve().parents[2] / "app/src/test/java/com/afghanjama/SelfTestJvmTest.kt"]:
    check("checkMargin(" in p.read_text(encoding="utf-8"), f"{p.name} صدا نمی‌زند")

if fails:
    print(f"✗ {len(fails)} اشکال"); [print("  -", f) for f in fails]; sys.exit(1)
print(f"✓ سود فروش: قاعده درست و هر {len(BROKEN)} نسخهٔ خراب گرفته شد")
