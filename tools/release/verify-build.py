#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""یک ساخت همان نسخه، کامیت و کانالی را می‌گوید که باید؟

`BuildInfo` (در `core/build.gradle.kts`) سرِ ساخت از گیت خوانده می‌شود.
این اسکریپت دو جا را می‌سنجد:

  - فایلِ تولیدشدهٔ `BuildInfo.kt` در `core/build/generated/…` — یعنی
    آنچه همین ساخت کامپایل کرد؛
  - با `--jars`، خودِ `BuildInfo.class` داخلِ jarهای یک بستهٔ ساخته‌شده
    (پوشهٔ `.app`ِ مک یا پوشهٔ اجراییِ ویندوز). این همان چیزی است که به
    دستِ کارگاه می‌رسد؛ اگر Gradle بسته‌ای کهنه را دست‌نخورده گذاشته
    باشد، فقط همین‌جا دیده می‌شود.

کلاس با خواندنِ جدولِ ثابت‌هایش سنجیده می‌شود، نه با `strings`: رشته‌ها
آنجا جدا و دقیق‌اند.

اجرا:
    python3 tools/release/verify-build.py --commit 0e109d9 --channel release \\
        --jars desktop/build/compose/binaries/main/app
"""
import argparse
import re
import struct
import sys
import zipfile
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

REPO = Path(__file__).resolve().parents[2]
GENERATED = REPO / "core/build/generated/buildinfo/commonMain/kotlin/com/afghanjama/BuildInfo.kt"
CLASS = "com/afghanjama/BuildInfo.class"


def props():
    out = {}
    for line in (REPO / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            out[k.strip()] = v.strip()
    return out


def utf8_constants(data):
    """همهٔ رشته‌های جدولِ ثابت‌های یک فایلِ `.class`."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("فایلِ class نیست")
    count = struct.unpack(">H", data[8:10])[0]
    i, idx, out = 10, 1, set()
    while idx < count:
        tag = data[i]
        i += 1
        if tag == 1:
            n = struct.unpack(">H", data[i:i + 2])[0]
            out.add(data[i + 2:i + 2 + n].decode("utf-8", "replace"))
            i += 2 + n
        elif tag in (3, 4):
            i += 4
        elif tag in (5, 6):
            i += 8
            idx += 1
        elif tag in (7, 8, 16, 19, 20):
            i += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            i += 4
        elif tag == 15:
            i += 3
        else:
            raise ValueError(f"نوعِ ناشناختهٔ ثابت: {tag}")
        idx += 1
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--commit", required=True, help="هفت حرفِ اولِ کامیت")
    ap.add_argument("--channel", required=True, choices=["release", "test build"])
    ap.add_argument("--jars", action="append", default=[],
                    help="پوشهٔ یک بستهٔ ساخته‌شده که jarهایش سنجیده شود")
    args = ap.parse_args()

    p = props()
    want = {
        "VERSION": p.get("appVersion", ""),
        "CODE": p.get("appVersionCode", ""),
        "COMMIT": args.commit,
        "CHANNEL": args.channel,
    }
    problems = []

    if not GENERATED.exists():
        problems.append(f"{GENERATED.relative_to(REPO)} نیست — :core:generateBuildInfo اجرا نشده")
    else:
        src = GENERATED.read_text(encoding="utf-8")
        got = dict(re.findall(r'const val (\w+)\s*:\s*\w+\s*=\s*"?([^"\n]*)"?', src))
        for k, v in want.items():
            if got.get(k) != v:
                problems.append(f"BuildInfo.kt: {k}={got.get(k)!r}، انتظار {v!r}")
        if not problems:
            print(f"  BuildInfo.kt   {want['VERSION']} ({want['CODE']}) {want['COMMIT']} {want['CHANNEL']}")

    for d in args.jars:
        root = Path(d)
        found = None
        for jar in sorted(root.rglob("*.jar")):
            try:
                with zipfile.ZipFile(jar) as z:
                    if CLASS in z.namelist():
                        found = (jar, utf8_constants(z.read(CLASS)))
                        break
            except zipfile.BadZipFile:
                continue
        if found is None:
            problems.append(f"{d}: هیچ jarی {CLASS} ندارد")
            continue
        jar, consts = found
        missing = [k for k in ("VERSION", "COMMIT", "CHANNEL") if want[k] not in consts]
        for k in missing:
            problems.append(f"{jar.name}: {k}={want[k]!r} در BuildInfo.class نیست")
        if not missing:
            print(f"  {jar.name}: همان نسخه، کامیت و کانال")

    if problems:
        print(f"✗ {len(problems)} ایراد — این ساخت آن چیزی نیست که انتظار می‌رفت:")
        for m in problems:
            print(f"  {m}")
        return 1
    print(f"✓ ساخت همان {want['VERSION']} از {want['COMMIT']} است، کانالِ «{want['CHANNEL']}»")
    return 0


if __name__ == "__main__":
    sys.exit(main())
