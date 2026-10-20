#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""انتشار فقط از یک تگ — امضای مک و `release.yml` هر دو.

**وضعی که از آن آمدیم.** تا ۱.۹.۰ سه بسته‌ای که خودشان را «۱.۹.۰»
می‌نامیدند از سه کامیتِ متفاوت بودند. DMGِ مک را
`sign-and-notarize.sh` از هر شاخه و هر درختی امضا و مهر می‌کرد، پس
ساختِ آزمایشی هم با مهرِ اپل «رسمی» دیده می‌شد.

۱.۹.۱ دو نگهبان گذاشت و این بررسی نمی‌گذارد بی‌صدا برداشته شوند:

  ۱. `tools/macos-signing/sign-and-notarize.sh` پیش از ساخت و پیش از
     دست زدن به جاکلیدی می‌سنجد که HEAD روی تگِ `v<appVersion>` است و
     درخت تمیز، و بعد از ساخت و پیش از اولین `codesign` با
     `verify-build.py` می‌سنجد که بسته همان کامیت را با کانالِ `release`
     می‌گوید.
  ۲. `.github/workflows/release.yml` روی تگِ `v*` راه می‌افتد، دروازه
     (`gate.py --tag`) را پیش از هر ساختی می‌زند، و هر سه بسته (اندروید،
     مک، ویندوز) را با `verify-build.py` می‌سنجد.

ترتیب مهم است، نه فقط بودن: نگهبانی که بعد از امضا بیاید فقط دیر خبر
می‌دهد.
"""
import re
import sys

import _src

SIGN = _src.REPO / "tools/macos-signing/sign-and-notarize.sh"
RELEASE = _src.REPO / ".github/workflows/release.yml"
GATE = _src.REPO / "tools/release/gate.py"
VERIFY = _src.REPO / "tools/release/verify-build.py"

for p in (SIGN, RELEASE, GATE, VERIFY):
    if not p.exists():
        print(f"✗ {p.relative_to(_src.REPO)} نیست — بررسی پوچ می‌شد.")
        sys.exit(1)

problems = []


def code_only(text):
    """خط‌های غیرِ توضیح — توضیح‌ها حق دارند از همین فرمان‌ها حرف بزنند."""
    return "\n".join(l for l in text.splitlines() if not l.lstrip().startswith("#"))


# ---- ۱) اسکریپتِ امضای مک ----
sign = code_only(SIGN.read_text(encoding="utf-8"))


def first(pattern):
    m = re.search(pattern, sign)
    return m.start() if m else None


tag_check = first(r'rev-parse -q --verify "refs/tags/\$TAG"')
head_check = first(r'"\$HEAD_SHA" != "\$TAG_SHA"')
clean_check = first(r'status --porcelain')
keychain = first(r"security find-identity")
build = first(r":desktop:createDistributable")
verify = first(r"tools/release/verify-build\.py[^\n]*\\?\n?[^\n]*--channel release")
codesign = first(r"\bcodesign --force")

for name, pos in (
    ("وارسیِ بودنِ تگِ v<appVersion>", tag_check),
    ("وارسیِ HEAD = تگ", head_check),
    ("وارسیِ درختِ تمیز (git status --porcelain)", clean_check),
    ("وارسیِ بسته با verify-build.py --channel release", verify),
):
    if pos is None:
        problems.append(f"sign-and-notarize.sh — {name} نیست")

if None not in (tag_check, head_check, clean_check, keychain, build):
    guard_end = max(tag_check, head_check, clean_check)
    if guard_end > keychain:
        problems.append(
            "sign-and-notarize.sh — وارسیِ تگ و درخت باید پیش از جاکلیدی "
            "(security find-identity) بیاید"
        )
    if guard_end > build:
        problems.append(
            "sign-and-notarize.sh — وارسیِ تگ و درخت باید پیش از ساختِ بسته بیاید"
        )
if None not in (verify, build, codesign):
    if not (build < verify < codesign):
        problems.append(
            "sign-and-notarize.sh — verify-build.py باید بعد از ساخت و پیش از "
            "اولین codesign بیاید"
        )

# راهِ دورزدن: متغیری که وارسی را خاموش کند.
if re.search(r"\b(SKIP|FORCE|ALLOW)_[A-Z_]*\b", sign):
    problems.append("sign-and-notarize.sh — راهِ دورزدنِ وارسیِ تگ نباید باشد")

# ---- ۲) release.yml ----
rel = code_only(RELEASE.read_text(encoding="utf-8"))
if not re.search(r"^\s*tags:\s*\[\s*[\"']v\*[\"']\s*\]", rel, re.M):
    problems.append("release.yml — روی تگِ v* راه نمی‌افتد")
if "tools/release/gate.py --tag" not in rel:
    problems.append("release.yml — دروازه را با --tag نمی‌زند")

jobs = {}
for m in re.finditer(r"^  (\w+):\n(.*?)(?=^  \w+:\n|\Z)", rel, re.M | re.S):
    jobs[m.group(1)] = m.group(2)
for job in ("android", "macos", "windows"):
    body = jobs.get(job)
    if body is None:
        problems.append(f"release.yml — کارِ «{job}» نیست")
        continue
    if "needs: gate" not in body:
        problems.append(f"release.yml — «{job}» پیش از دروازه می‌دود (needs: gate نیست)")
    if "tools/release/verify-build.py" not in body:
        problems.append(f"release.yml — «{job}» بسته‌اش را با verify-build.py نمی‌سنجد")
    if "fetch-depth: 0" not in body:
        problems.append(
            f"release.yml — «{job}» بی fetch-depth: 0؛ ساخت تگ را نمی‌بیند و "
            f"کانال «test build» می‌شود"
        )
if "xcodebuild" in rel and "archive" in rel:
    problems.append("release.yml — آرشیوِ آیفون اینجا جایی ندارد؛ CI امضا ندارد")

if problems:
    print(f"✗ {len(problems)} ایراد در نگهبانِ انتشار")
    for p in problems:
        print(f"  {p}")
    sys.exit(1)

print(
    "✓ انتشار فقط از تگ: امضای مک پیش از جاکلیدی و ساخت تگ و درخت را "
    "می‌سنجد، و release.yml هر سه بسته را با همان کامیت می‌سنجد"
)
