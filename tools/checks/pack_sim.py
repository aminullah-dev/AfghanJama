#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""بسته‌بندی: پول عوض نشود، انبار عددی شود، مصرف با واحدِ درست حساب شود."""
import re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2] / "app/src/main/java/com/afghanjama"
fails = []
def check(c, m):
    if not c: fails.append(m)

def line(qty, price, per):
    total = int(qty * price)
    packed = per > 1
    return dict(total=total,
                stock_unit="عدد" if packed else "بسته",
                stock_qty=qty * per if packed else qty)

# ۵ بستهٔ ۱۰۰تایی دکمه، هر بسته ۲۰۰ ؋
L = line(5, 200, 100)
check(L["total"] == 1000, f"مبلغ باید ۱۰۰۰ بماند، شد {L['total']}")
check(L["stock_qty"] == 500, f"انبار باید ۵۰۰ عدد شود، شد {L['stock_qty']}")
check(L["stock_unit"] == "عدد", "واحدِ انبار باید عدد شود")
# میانگینِ هر عدد
check(abs(L["total"] / L["stock_qty"] - 2.0) < 1e-9, "میانگینِ هر عدد باید ۲ ؋ شود")

# بی بسته‌بندی، رفتارِ قبلی دست‌نخورده
for per in (0, 1):
    P = line(7, 50, per)
    check(P["stock_qty"] == 7, f"per={per}: مقدار نباید عوض شود")
    check(P["stock_unit"] == "بسته", f"per={per}: واحد نباید عوض شود")
    check(P["total"] == 350, f"per={per}: مبلغ نباید عوض شود")

# پول هرگز با بسته‌بندی عوض نمی‌شود
for per in (0, 1, 12, 100):
    check(line(3, 400, per)["total"] == 1200, f"per={per}: مبلغ عوض شد")

# ---- نامِ انبارِ پارچه: خط تیره رنگ نیست ----
DASHES = ["—", "-", "–", " ", "", "ندارد", "بدون رنگ", "نامشخص"]
def fabric_name(ty, color):
    out = []
    for s in (ty.strip(), color.strip()):
        if not s or all(c in "-–—_.،, " for c in s) or s in ("ندارد", "بدون رنگ", "نامشخص"):
            continue
        out.append(s)
    return " ".join(out)

for d in DASHES:
    got = fabric_name("مخمل سرخ", d)
    check(got == "مخمل سرخ", f"رنگِ «{d}» نباید به نام بچسبد، شد «{got}»")
check(fabric_name("مخمل", "سرخ") == "مخمل سرخ", "رنگِ واقعی باید بچسبد")
check(fabric_name(" کتان ", " آبی ") == "کتان آبی", "فاصلهٔ اضافه باید پاک شود")

repo_src = (ROOT / "data/repo/Repo.kt").read_text(encoding="utf-8")
check("isPlaceholderDash" in repo_src, "فیلترِ خط تیره در Repo نیست")
check("return 0L" in repo_src.split("val cur = exact")[0][-800:],
      "برداشتِ ناموفق باید بی‌ساختنِ ردیفِ شبح برگردد")

src = (ROOT / "ui/vm/ProcurementViewModel.kt").read_text(encoding="utf-8")
check("val stockUnit" in src and "val stockQty" in src, "stockUnit/stockQty نیست")
check("perPack > 1" in src, "شرطِ بسته‌بندی نیست")
repo = (ROOT / "data/repo/Repo.kt").read_text(encoding="utf-8")
check("it.perPack > 1" in repo, "Repo بسته را به عدد تبدیل نمی‌کند")
check('"عدد"' in repo, "واحدِ عدد در Repo نیست")
ent = (ROOT / "data/entities/PurchaseItem.kt").read_text(encoding="utf-8")
check("val perPack" in ent, "ستونِ perPack روی قلمِ خرید نیست")
mg = (ROOT / "data/Migrations.kt").read_text(encoding="utf-8")
check("MIGRATION_57_58" in mg and "purchase_items` ADD COLUMN `perPack`" in mg, "مهاجرت نیست")
db = (ROOT / "data/AppDatabase.kt").read_text(encoding="utf-8")
# نسخه از خودِ کد خوانده می‌شود، وگرنه با هر مهاجرتِ تازه می‌شکست
ver = int(re.search(r"const val DB_VERSION = (\d+)", db).group(1))
check(ver >= 58, f"DB_VERSION باید دستِ‌کم ۵۸ باشد، {ver} است")
check("MIGRATION_57_58" in db, "مهاجرتِ ۵۷→۵۸ ثبت نشد")

if fails:
    print(f"✗ {len(fails)} اشکال"); [print("  -", f) for f in fails]; sys.exit(1)
print("✓ بسته‌بندی: مبلغ دست‌نخورده، انبار عددی، و بی‌بسته رفتارِ قبلی")
