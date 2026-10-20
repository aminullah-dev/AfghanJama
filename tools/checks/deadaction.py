#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""بندِ منو باید کاری بکند، نه اینکه فقط دیده شود.

**وضعی که از آن آمدیم.** کارفرما اسکرین‌شاتِ اپِ رقیب را فرستاد — یک
منوی «⋮» با نُه بند — و یک جمله نوشت:

    «ببین این‌ها باز میشه و عمل ره انجام میده نه اینکه فقط نمایش باشد»

ترسِ درستی است. منو ارزان‌ترین جای دنیا برای اضافه‌کردنِ یک سطر است:
یک `Text` و تمام. سطر دیده می‌شود، انگشت رویش می‌رود، منو بسته می‌شود
و **هیچ اتفاقی نمی‌افتد**. کاربر فکر می‌کند اپ خراب است یا خودش
اشتباه زده؛ هیچ‌کدام. آن بند از اول هیچ نبود.

این بدتر از نبودنِ آن قابلیت است. نبودنش را کاربر می‌بیند و راهِ
دیگری پیدا می‌کند؛ بودنِ توخالی‌اش وقتش را می‌گیرد و اعتمادش را
می‌برد.

**قاعده.** بدنهٔ `onClick`ِ یک بندِ منو نباید فقط بستن باشد. اگر همهٔ
کاری که می‌کند مقداردهیِ `false` و `null` است، هیچ در را باز نکرده —
`false` و `null` فقط می‌بندند.

    onClick = { menu = false }                       ← مرده
    onClick = { menu = false; historyOf = item }     ← زنده
    onClick = { menu = false; vm.deleteRow(item) }   ← زنده

`true` عمداً «زنده» شمرده می‌شود: `showAdd = true` واقعاً دیالوگ باز
می‌کند. و این قاعده فقط به **بندِ منو** کار دارد، نه به هر دکمه —
دکمهٔ «بستن»ِ یک دیالوگ حقِ طبیعی دارد که فقط `null` بگذارد.

**به‌علاوه:** هر کنشِ کلیک‌شونده‌ای که بدنه‌اش کاملاً خالی است، هرجای
اپ که باشد، مرده است.
"""
import re
import sys

import _src


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r'"""(?:.|\n)*?"""', lambda m: "\n" * m.group(0).count("\n"), src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


#: بندِ منو — همان چیزی که کارفرما دربارهٔ آن حرف زد.
#:
#: `(?<!fun )` عمدی است: خودِ **اعلانِ** `fun AppDropdownMenuItem(` در
#: مرزِ `Widgets` هم به این الگو می‌خورد ولی فراخوان نیست و `onClick`
#: هم ندارد — نسخهٔ اولِ این بررسی روی همان دو اعلان قرمزِ نادرست داد.
MENU_ITEM = re.compile(r"(?<!fun )\b(?:App)?DropdownMenuItem\s*\(")

#: ردیفِ خاموش دروغ نمی‌گوید: ظاهرش می‌گوید زدنی نیست.
DISABLED = re.compile(r"\benabled\s*=\s*false\b")

#: هر چیزی که کلیک می‌خورد — برای قاعدهٔ «بدنهٔ کاملاً خالی»
CLICKABLE = re.compile(
    r"\b(?:App)?DropdownMenuItem\s*\(|"
    r"\b(?:Text|Outlined|Elevated|Filled|FilledTonal|Brand)?Button\s*\(|"
    r"\bIconButton\s*\(|"
    r"\b(?:Extended)?FloatingActionButton\s*\(|"
    r"\.clickable\s*[({]"
)

#: `onClick = {` — شروعِ بدنهٔ کنشِ درجا
ONCLICK = re.compile(r"\bonClick\s*=\s*\{")

#: هر شکلی از دادنِ کنش — از جمله `onClick = onClick`ِ مرزِ سکو، که
#: بدنه‌اش جای دیگری است و اینجا خواندنی نیست.
HAS_ONCLICK = re.compile(r"\bonClick\s*=")

#: یک مقداردهیِ صرفاً «بستن»: چیزی = false یا null
CLOSING = re.compile(r"^\s*[\w.]+\s*=\s*(?:false|null)\s*$")


def brace_body(src: str, open_idx: int):
    """متنِ داخلِ آکولادی که در `open_idx` باز شده."""
    depth = 0
    for j in range(open_idx, len(src)):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                return src[open_idx + 1:j]
    return None


def call_body(src: str, open_paren: int):
    """آرگومان‌های یک فراخوان — متنِ داخلِ پرانتزِ باز‌شده در `open_paren`."""
    depth = 0
    for j in range(open_paren, len(src)):
        if src[j] == "(":
            depth += 1
        elif src[j] == ")":
            depth -= 1
            if depth == 0:
                return src[open_paren + 1:j], j
    return None, len(src)


def statements(body: str):
    """جمله‌های یک بدنه — با `;` یا خطِ تازه از هم جدا."""
    out = []
    for chunk in re.split(r"[;\n]", body):
        c = chunk.strip()
        if c:
            out.append(c)
    return out


fail = []
scanned = 0
menus = 0

for path in sorted(_src.CORE.rglob("*.kt")) + sorted(_src.APP.rglob("*.kt")):
    src = strip(path.read_text(encoding="utf-8"))
    scanned += 1
    rel = path.relative_to(_src.REPO)

    # ── ۱ بندهای منو: بدنه‌ای که فقط می‌بندد ──────────────────────
    for m in MENU_ITEM.finditer(src):
        args, _ = call_body(src, m.end() - 1)
        if args is None:
            continue
        menus += 1
        # ردیفِ خاموش کنشی وعده نمی‌دهد، پس نمی‌تواند وعده‌خلافی کند.
        if DISABLED.search(args):
            continue
        if not HAS_ONCLICK.search(args):
            fail.append(
                f"{rel}:{src[:m.start()].count(chr(10)) + 1} — بندِ منو "
                f"`onClick` ندارد؛ زدنش هیچ نمی‌کند."
            )
            continue
        c = ONCLICK.search(args)
        if not c:
            # کنش از بیرون می‌آید (`onClick = onClick`) — بدنه اینجا نیست
            continue
        body = brace_body(args, c.end() - 1)
        if body is None:
            continue
        stmts = statements(body)
        if stmts and all(CLOSING.match(s) for s in stmts):
            line = src[:m.start()].count("\n") + 1
            fail.append(
                f"{rel}:{line} — بندِ منو فقط «{'؛ '.join(stmts)}» می‌کند. "
                f"`false` و `null` فقط می‌بندند؛ این بند هیچ در را باز "
                f"نمی‌کند و کاربر فکر می‌کند اپ خراب است."
            )

    # ── ۲ هر کنشِ کلیک‌شونده با بدنهٔ کاملاً خالی ─────────────────
    for m in CLICKABLE.finditer(src):
        args, end = call_body(src, m.end() - 1) if src[m.end() - 1] == "(" else (None, 0)
        scope = args if args is not None else ""
        for c in ONCLICK.finditer(scope):
            body = brace_body(scope, c.end() - 1)
            if body is not None and not body.strip():
                line = src[:m.start()].count("\n") + 1
                fail.append(
                    f"{rel}:{line} — `onClick` با بدنهٔ خالی. دکمه‌ای که "
                    f"زده می‌شود و هیچ نمی‌کند."
                )

# ── ۳ کامپیوتر: کنشی که به صفحهٔ مشترک خالی داده می‌شود، یا اصلاً داده نمی‌شود ──
#
# **خرابی‌ای که این بند را لازم کرد (۱.۹).** روی کامپیوتر دو دکمهٔ «انبارِ
# محصول» («افزودن به فاکتور» و «فاکتور») و چرخ‌دندهٔ «سفارش‌ها» هیچ کاری
# نمی‌کردند. هیچ‌کدام `onClick = {}` در خودِ صفحه نبود — صفحه درست بود. خرابی
# در `Shell.kt` بود: یکی `onGoSettings = {}` گرفته بود، و دوتای دیگر اصلاً
# داده نشده بودند و پیش‌فرضشان `{}` بود. بندهای بالا فقط `:core` و `:app` را
# می‌دیدند.
#
# دو قاعده، فقط برای کدِ برنامهٔ کامپیوتر (نه دودآزمایی‌ها، که عمداً `{}`
# می‌دهند):
#   الف) `onX = {}` در فراخوانی — کنشی که داده شده ولی هیچ نمی‌کند.
#   ب) صفحهٔ مشترکی که کنشی با پیش‌فرضِ `{}` دارد و کامپیوتر آن را نمی‌دهد.
# هر استثنا باید با دلیل در فهرستِ زیر نوشته شود.

#: (فایل، نامِ کنش) → دلیل
DESK_ALLOWED_EMPTY = {
    ("Main.kt", "onLoggedIn"):
        "ورود حالتِ `AuthViewModel` را عوض می‌کند و بازترکیب خودش صفحه را "
        "عوض می‌کند؛ کنشِ جدا لازم نیست.",
}

#: (صفحه، نامِ کنش) → دلیل
DESK_ALLOWED_MISSING: dict = {}

EMPTY_ARG = re.compile(r"\b(on[A-Z]\w*)\s*=\s*\{\s*\}")
SCREEN_DECL = re.compile(r"\bfun\s+(\w+Screen)\s*\(")
SCREEN_CALL = re.compile(r"(?<!fun )(?<![\w.])(\w+Screen)\s*\(")


def split_top(args: str):
    """پارامترها یا آرگومان‌ها در سطحِ اول — ویرگولِ داخلِ پرانتز و آکولاد نه."""
    out, depth, cur = [], 0, []
    prev = ""
    for ch in args:
        # `<…>`ِ نوعِ عام ویرگول دارد (`Map<A, B>`)؛ ولی `>`ِ پیکانِ `->` بسته نیست.
        if ch in "([{<":
            depth += 1
        elif ch in ")]}" or (ch == ">" and prev != "-"):
            depth -= 1
        prev = ch
        if ch == "," and depth == 0:
            out.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
    if "".join(cur).strip():
        out.append("".join(cur))
    return out


# صفحه‌های مشترک و کنش‌هایی (`on…`) که پیش‌فرضشان `{}` است. جای خالیِ
# اسکلت‌ها (`AppScreen`ِ `actions` و `snackbarHost`) کنش نیستند؛ فقط
# صفحه‌های `ui/screens`.
empty_defaults = {}
for path in sorted((_src.COMMON / "com/afghanjama/ui/screens").rglob("*.kt")):
    src = strip(path.read_text(encoding="utf-8"))
    for m in SCREEN_DECL.finditer(src):
        args, _ = call_body(src, m.end() - 1)
        if args is None:
            continue
        for param in split_top(args):
            pm = re.match(r"\s*(?:@\w+\s*)*(\w+)\s*:(.*)=\s*\{\s*\}\s*$", param, re.S)
            if pm and pm.group(1).startswith("on") and "->" in pm.group(2) \
                    and "Unit" in pm.group(2) and "@Composable" not in pm.group(2):
                empty_defaults.setdefault(m.group(1), set()).add(pm.group(1))

desk_files = [p for p in sorted(_src.DESKTOP.rglob("*.kt")) if "Smoke" not in p.name]
desk_calls = 0
for path in desk_files:
    src = strip(path.read_text(encoding="utf-8"))
    scanned += 1
    rel = path.relative_to(_src.REPO)
    for m in EMPTY_ARG.finditer(src):
        if (path.name, m.group(1)) in DESK_ALLOWED_EMPTY:
            continue
        line = src[:m.start()].count("\n") + 1
        fail.append(
            f"{rel}:{line} — «{m.group(1)} = {{}}»: کنشی که به صفحه داده شده و هیچ "
            f"نمی‌کند. یا جایی ببرد، یا با دلیل در DESK_ALLOWED_EMPTY بیاید."
        )
    for m in SCREEN_CALL.finditer(src):
        name = m.group(1)
        if name not in empty_defaults:
            continue
        args, _ = call_body(src, m.end() - 1)
        if args is None:
            continue
        desk_calls += 1
        for param in sorted(empty_defaults[name]):
            if (name, param) in DESK_ALLOWED_MISSING:
                continue
            if not re.search(rf"\b{param}\s*=", args):
                line = src[:m.start()].count("\n") + 1
                fail.append(
                    f"{rel}:{line} — «{name}» بی «{param}» صدا زده شده؛ پیش‌فرضش `{{}}` "
                    f"است، پس دکمه‌اش روی کامپیوتر هیچ نمی‌کند."
                )

if not desk_files:
    print("✗ هیچ فایلی از :desktop اسکن نشد — بند ۳ پوچ می‌شد.")
    sys.exit(1)
if not empty_defaults:
    print("✗ هیچ صفحه‌ای با کنشِ پیش‌فرضِ `{}` پیدا نشد — یا الگو عوض شده، بند ۳ پوچ می‌شود.")
    sys.exit(1)

if not scanned:
    print("✗ هیچ فایلی اسکن نشد — بررسی پوچ می‌شد.")
    sys.exit(1)

if menus == 0:
    print("✗ هیچ بندِ منویی پیدا نشد — یا نامِ کامپوننت عوض شده، بررسی پوچ می‌شود.")
    sys.exit(1)

if fail:
    print(f"✗ {len(fail)} کنشِ مرده")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(
    f"✓ کنش‌ها: هر {menus} بندِ منو کاری می‌کند، هیچ onClickی خالی نیست، و "
    f"کامپیوتر به {desk_calls} صفحهٔ دارای کنشِ پیش‌فرضِ خالی همهٔ کنش‌ها را می‌دهد"
)
