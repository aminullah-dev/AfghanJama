#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""هر کارتِ داشبورد پشتِ دسترسیِ صفحهٔ خودش است — فروشنده پول نمی‌بیند.

`HubDashboard.kt` کارت‌ها را در یک فهرست می‌چیند و هر کدام را با یک
`Section` به دسترسی می‌بندد (`dashVisible`). `dashSmoke` فقط می‌سنجد که
`dashVisible(Section.Finance, …)` برای فروشنده بسته است؛ اگر کسی روزی
`MoneyCard` را به `Section.Home` (باز برای همه) ببندد، آن آزمون سبز می‌ماند
و فروشنده صندوق و بانک را می‌بیند. این بررسی همان جفت‌ها را نگه می‌دارد:

  • هر کارت دقیقاً یک بار در فهرست است، با همان `Section` که اینجا آمده؛
  • هیچ کارتِ `…Card(` بیرون از فهرستِ دروازه‌دار صدا زده نمی‌شود؛
  • فهرست با `.filter { dashVisible(` بسته می‌شود؛
  • کارتِ درخواست‌ها دروازهٔ دومِ `seesRequests` را دارد.

کارتِ تازه یعنی یک سطرِ تازه در `EXPECTED` — و این یعنی کسی آگاهانه گفته
آن کارت پشتِ کدام در است.
"""
import re
import sys

import _src

SRC = _src.REPO / "desktop/src/main/kotlin/com/afghanjama/desktop/HubDashboard.kt"

EXPECTED = {
    "AlertsCard": "ActionCenter",
    "RequestsCard": "WorkshopLink",
    "TodayCard": "Inventory",
    "ReadyCard": "DeliveryQueue",
    "InsideCard": "Attendance",
    "PipelineCard": "Inventory",
    "RiskCard": "WorkshopLoad",
    "TailorsCard": "WorkshopLoad",
    "MoneyCard": "Finance",
    "HealthCard": "Finance",
    "SalesCard": "Finance",
    "DebtsCard": "FollowUp",
    "WagesCard": "Payroll",
    "MaterialsCard": "PurchasePlan",
    "ProductsCard": "FinishedWarehouse",
    "BelowCostCard": "FinishedWarehouse",
}

src = SRC.read_text(encoding="utf-8")
code = re.sub(r"//[^\n]*", "", src)
fail = []

start = code.find("val cards = buildList {")
end = code.find(".filter { dashVisible(", start)
if start < 0 or end < 0:
    print("✗ فهرستِ کارت‌ها یا `.filter { dashVisible(` در HubDashboard.kt پیدا نشد")
    sys.exit(1)
block = code[start:end]

adds = re.findall(r"add\(DashCard\(\s*\d+\s*,\s*Section\.(\w+)(.*?)\)\s*\{\s*(\w+Card)\(", block)
seen = {}
for section, extra, card in adds:
    seen.setdefault(card, []).append((section, extra.strip()))

for card, want in EXPECTED.items():
    got = seen.get(card, [])
    if len(got) != 1:
        fail.append(f"{card}: {len(got)} بار در فهرست (باید ۱)")
    elif got[0][0] != want:
        fail.append(f"{card}: پشتِ Section.{got[0][0]} است، باید Section.{want}")
for card in seen:
    if card not in EXPECTED:
        fail.append(f"{card}: کارتِ تازه بی سطر در EXPECTED — بگویید پشتِ کدام در است")

req = seen.get("RequestsCard", [])
if req and "seesRequests" not in req[0][1]:
    fail.append("RequestsCard دروازهٔ دومِ WorkshopLinkAccess.seesRequests را ندارد")

defined = set(re.findall(r"private fun (\w+Card)\(", code))
outside = code[:start] + code[end:]
for card in defined:
    for m in re.finditer(rf"\b{card}\(", outside):
        if not re.match(rf"private fun {card}\(", outside[max(0, m.start() - 12):m.end()]):
            fail.append(f"{card} بیرون از فهرستِ دروازه‌دار صدا زده شده")
            break

if fail:
    print("✗ داشبورد: " + "؛ ".join(fail))
    sys.exit(1)
print(f"✓ داشبورد: هر {len(EXPECTED)} کارت پشتِ دسترسیِ صفحهٔ خودش است")
