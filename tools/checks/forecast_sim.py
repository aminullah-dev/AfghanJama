#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""پیش‌بینی کمبود: نه دیر هشدار بدهد، نه الکی."""
import pathlib as _pl
import re
import sys
import sys as _s, pathlib as _p
_s.path.insert(0, str(_p.Path(__file__).resolve().parent))
import _src


ROOT = _src.ANY
fails = []
def check(c, m):
    if not c: fails.append(m)

WARN = 14

def forecast(items, draws, days, *, no_window=False, no_rate=False, guess_zero=False):
    days = max(days, 1)
    used = {}
    for n, u, q, at in draws:
        if q <= 0: continue
        if not no_window and not (0 <= at < days): continue
        used[(n, u)] = used.get((n, u), 0.0) + q
    out = {}
    for n, u, amt, mn in items:
        per = used.get((n, u), 0.0) / (1 if no_rate else days)
        if per > 0 and amt > 0:
            left = min(999, round(amt / per))
        elif guess_zero:
            left = 0
        else:
            left = None
        below = mn > 0 and amt <= mn
        out[n] = dict(per=per, left=left, below=below,
                      urgent=below or (left is not None and left <= WARN))
    return out

ITEMS = [("مخمل سرخ","متر",100.0,0.0), ("کتان","متر",300.0,0.0),
         ("زیپ","عدد",40.0,0.0), ("دکمه","بسته",2.0,5.0)]
DRAWS = [("مخمل سرخ","متر",200.0,5), ("کتان","متر",20.0,3), ("دکمه","بسته",1.0,1)]

def run(**kw):
    bad = []
    o = forecast(ITEMS, DRAWS, 20, **kw)
    def a(c, n):
        if not c: bad.append(n)
    a(o["مخمل سرخ"]["per"] == 10.0, "نرخ")
    a(o["مخمل سرخ"]["left"] == 10, "۱۰ روز")
    a(o["کتان"]["left"] == 300, "۳۰۰ روز")
    a(o["زیپ"]["left"] is None, "بی‌مصرف پیش‌بینی ندارد")
    a(o["زیپ"]["urgent"] is False, "بی‌مصرف فوری نشود")
    a(o["دکمه"]["below"] and o["دکمه"]["urgent"], "حدِ هشدار")
    a(o["کتان"]["urgent"] is False, "کتان نباید هشدار دهد")
    a(o["مخمل سرخ"]["urgent"], "مخمل باید هشدار دهد")
    w = forecast(ITEMS, DRAWS, 40, **kw)
    a(w["مخمل سرخ"]["per"] == 5.0, "پنجرهٔ دوبرابر نرخِ نصف")
    a(w["مخمل سرخ"]["left"] == 20, "عمرِ دوبرابر")
    old = forecast(ITEMS, [("مخمل سرخ","متر",200.0,99)], 20, **kw)
    a(old["مخمل سرخ"]["per"] == 0.0, "مصرفِ قدیمی نباید بیاید")
    a(old["مخمل سرخ"]["left"] is None, "و پیش‌بینی ندهد")
    return bad

check(not run(), f"قاعدهٔ درست ادعا رد کرد: {run()}")
BROKEN = {
    "پنجره نادیده گرفته شود": dict(no_window=True),
    "نرخ بر روز تقسیم نشود": dict(no_rate=True),
    "قلمِ بی‌مصرف صفر روز حدس زده شود": dict(guess_zero=True),
}
for label, kw in BROKEN.items():
    check(len(run(**kw)) > 0, f"نسخهٔ خرابِ «{label}» گرفته نشد")

src = (ROOT / "data/StockForecast.kt").read_text(encoding="utf-8")
check("import android" not in src, "StockForecast وابستگیِ اندرویدی گرفت")
for f in ("forecast", "needsAttention", "WARN_DAYS", "WINDOW_DAYS"):
    check(f in src, f"{f} نیست")
st = (ROOT / "selftest/SelfTest.kt").read_text(encoding="utf-8")
check("fun checkStockForecast(" in st, "بررسیِ خودآزمایی نیست")
for p in [ROOT / "ui/vm/SelfTestViewModel.kt",
          _pl.Path(__file__).resolve().parents[2] / "app/src/test/java/com/afghanjama/SelfTestJvmTest.kt"]:
    check("checkStockForecast(" in p.read_text(encoding="utf-8"), f"{p.name} صدا نمی‌زند")

if fails:
    print(f"✗ {len(fails)} اشکال"); [print("  -", f) for f in fails]; sys.exit(1)
print(f"✓ پیش‌بینی کمبود: قاعده درست و هر {len(BROKEN)} نسخهٔ خراب گرفته شد")
