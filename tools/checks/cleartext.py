#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""متنِ ساده (`http://`) برای اشتراکِ کارگاه باز است — و مرزش در کد است.

**اشکالی که این بررسی را ساخت** (تا ۱.۹.۰، روی هر گوشیِ کارگر):

`network_security_config.xml` متنِ ساده را در `base-config` بسته بود و
فقط برای سه «دامنه» باز می‌کرد: `10.0.0.0`، `192.168.0.0`،
`172.16.0.0`. ولی `<domain>` نامِ میزبان را **عیناً** می‌سنجد، نه بازهٔ
نشانی. پس `http://192.168.1.7:8797` با هیچ‌کدام جور نمی‌شد، اندروید
اتصال را پیش از رفتن رد می‌کرد، و کارگر فقط «وصل نشد» می‌دید — به هر
دستگاهِ اصلی‌ای، گوشی یا کامپیوتر. کامپایل بی‌عیب بود و هیچ آزمونی
روی JVM این را نمی‌دید، چون این قاعده را فقط خودِ اندروید اجرا می‌کند.

**دو نیمه که باید با هم بمانند:**

  ۱ پیکربندی: `base-config` با `cleartextTrafficPermitted="true"`، و
    **هیچ** `<domain>`ی که نشانیِ IP باشد (کار نمی‌کند و فقط گمراه
    می‌کند). مانیفست هم باید همین فایل را نام ببرد.
  ۲ مرزِ کد: چون پیکربندی حالا برای همه باز است، `LanClient` باید پیش
    از هر اتصال `HostAddress.isPrivate` را بپرسد — و **بیرون از**
    `runCatching`، وگرنه پیامِ روشنِ «این نشانی مالِ کارگاه نیست» زیرِ
    پیامِ کلیِ «وصل نشد» گم می‌شود. `HostAddress.check` هم همین را
    می‌پرسد تا نشانیِ بد اصلاً ذخیره نشود.

اگر یکی بی دیگری بماند، یا اتصال باز نمی‌شود (۱) یا متنِ ساده به هر
نشانی‌ای می‌رود (۲).

  ۳ حواله: `LanClient.send` باید `instanceFollowRedirects = false`
    بگذارد. `HttpURLConnection` پیش‌فرض دنبالِ ۳۰۲ از http به http می‌رود
    و سرخطِ رمز را هم می‌بَرد؛ پس مرزِ (۲) فقط برای نشانیِ اول بود و هر
    چیزی روی یک نشانیِ خصوصی می‌توانست گوشی را به هر میزبانی بفرستد.
"""
import re
import sys
import xml.etree.ElementTree as ET

import _src

CONFIG = _src.REPO / "app/src/main/res/xml/network_security_config.xml"
MANIFEST = _src.REPO / "app/src/main/AndroidManifest.xml"
CLIENT = _src.CORE / "com/afghanjama/lan/LanClient.kt"
HOST = _src.CORE / "com/afghanjama/lan/HostAddress.kt"

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
IP_LITERAL = re.compile(r"^\s*(\d{1,3}(\.\d{1,3}){3}|\[?[0-9a-fA-F:]*:[0-9a-fA-F:]*\]?)\s*$")


def config_problems(xml_text: str) -> list[str]:
    """ایرادهای پیکربندی؛ فهرستِ خالی یعنی سالم."""
    out = []
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as e:
        return [f"فایل خوانده نشد: {e}"]
    if root.tag != "network-security-config":
        out.append(f"ریشه «{root.tag}» است، نه network-security-config.")
    base = root.find("base-config")
    if base is None:
        out.append("base-config نیست — پیش‌فرضِ اندروید ۹ به بعد متنِ ساده را می‌بندد.")
    elif base.get("cleartextTrafficPermitted") != "true":
        out.append(
            "base-config متنِ ساده را باز نمی‌کند — گوشیِ کارگر به هیچ "
            "دستگاهِ اصلی‌ای وصل نمی‌شود."
        )
    for d in root.iter("domain"):
        if IP_LITERAL.match(d.text or ""):
            out.append(
                f"<domain>{(d.text or '').strip()}</domain> — دامنه نامِ میزبان را "
                f"عیناً می‌سنجد، نه بازه را؛ این نشانی هیچ چیزی را باز نمی‌کند."
            )
    return out


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


def client_problems(kt: str) -> list[str]:
    """مرزِ کد در `LanClient.call`؛ فهرستِ خالی یعنی سالم."""
    src = strip(kt)
    m = re.search(r"private suspend fun call\((.|\n)*?\n    }\n", src)
    if not m:
        return ["`LanClient.call` پیدا نشد — بررسیِ مرزِ کد پوچ می‌شد."]
    body = m.group(0)
    guard = body.find("HostAddress.isPrivate(")
    if guard < 0:
        return ["`LanClient.call` پیش از اتصال `HostAddress.isPrivate` را نمی‌پرسد — "
                "متنِ ساده به هر نشانی‌ای می‌رود."]
    catch = body.find("runCatching")
    send = body.find("send(")
    first_net = min(i for i in (catch, send) if i >= 0) if (catch >= 0 or send >= 0) else -1
    if 0 <= first_net < guard:
        return ["`LanClient.call` نشانی را بعد از شروعِ اتصال (یا درونِ runCatching) "
                "می‌سنجد — پیامِ روشنِ رد زیرِ «وصل نشد» گم می‌شود."]
    return []


def redirect_problems(kt: str) -> list[str]:
    """حواله در `LanClient.send` بسته است؟ فهرستِ خالی یعنی سالم."""
    src = strip(kt)
    m = re.search(r"private fun send\((.|\n)*?\n    }\n", src)
    if not m:
        return ["`LanClient.send` پیدا نشد — بررسیِ حواله پوچ می‌شد."]
    if not re.search(r"instanceFollowRedirects\s*=\s*false", m.group(0)):
        return ["`LanClient.send` حواله را نمی‌بندد (`instanceFollowRedirects = false`) — "
                "یک ۳۰۲ گوشی و رمزش را به هر میزبانی می‌فرستد."]
    return []


def host_problems(kt: str) -> list[str]:
    src = strip(kt)
    m = re.search(r"fun check\((.|\n)*?\n    }\n", src)
    if not m or "isPrivate(" not in m.group(0):
        return ["`HostAddress.check` نشانیِ بیرون از شبکهٔ کارگاه را قبول می‌کند — "
                "نشانیِ بد ذخیره می‌شود و بعد هر بار رد می‌شود."]
    return []


# ── خودآزمایی: بررسی‌ای که خرابی را نگیرد بدتر از نبودنش است ──────
GOOD = '<network-security-config><base-config cleartextTrafficPermitted="true" /></network-security-config>'
BROKEN = {
    "۱.۹.۰": (
        '<network-security-config><base-config cleartextTrafficPermitted="false" />'
        '<domain-config cleartextTrafficPermitted="true">'
        '<domain includeSubdomains="false">192.168.0.0</domain></domain-config>'
        '</network-security-config>'
    ),
    "بی base-config": "<network-security-config />",
    "دامنهٔ IP کنارِ base باز": (
        '<network-security-config><base-config cleartextTrafficPermitted="true" />'
        '<domain-config cleartextTrafficPermitted="true">'
        '<domain>10.0.0.0</domain></domain-config></network-security-config>'
    ),
}
GOOD_CLIENT = (
    "class C {\n    private suspend fun call(p: String): R {\n"
    "        if (!HostAddress.isPrivate(host)) return Err(x)\n"
    "        return withContext(IO) { send(p) }\n    }\n}\n"
)
BROKEN_CLIENT = {
    "بی مرز": "class C {\n    private suspend fun call(p: String): R {\n"
              "        return withContext(IO) { send(p) }\n    }\n}\n",
    "مرز درونِ runCatching": "class C {\n    private suspend fun call(p: String): R =\n"
              "        runCatching { if (!HostAddress.isPrivate(host)) error(1); send(p) }\n    }\n}\n",
}

GOOD_SEND = (
    "class C {\n    private fun send(p: String): R {\n"
    "        val c = open(p).apply { instanceFollowRedirects = false }\n"
    "        return read(c)\n    }\n}\n"
)
BROKEN_SEND = {
    "حوالهٔ باز (پیش‌فرض)": "class C {\n    private fun send(p: String): R {\n"
                           "        return read(open(p))\n    }\n}\n",
    "حوالهٔ صریحاً باز": "class C {\n    private fun send(p: String): R {\n"
                         "        val c = open(p).apply { instanceFollowRedirects = true }\n"
                         "        return read(c)\n    }\n}\n",
}

fail = []
if config_problems(GOOD):
    fail.append("خودآزمایی: پیکربندیِ درست رد شد — بررسی خراب است.")
for name, x in BROKEN.items():
    if not config_problems(x):
        fail.append(f"خودآزمایی: نمونهٔ خرابِ «{name}» گرفته نشد — بررسی پوچ است.")
if client_problems(GOOD_CLIENT):
    fail.append("خودآزمایی: مرزِ درستِ LanClient رد شد — بررسی خراب است.")
for name, x in BROKEN_CLIENT.items():
    if not client_problems(x):
        fail.append(f"خودآزمایی: LanClientِ «{name}» گرفته نشد — بررسی پوچ است.")
if redirect_problems(GOOD_SEND):
    fail.append("خودآزمایی: sendِ درست رد شد — بررسیِ حواله خراب است.")
for name, x in BROKEN_SEND.items():
    if not redirect_problems(x):
        fail.append(f"خودآزمایی: sendِ «{name}» گرفته نشد — بررسیِ حواله پوچ است.")

# ── خودِ پروژه ────────────────────────────────────────────────────
for path in (CONFIG, MANIFEST, CLIENT, HOST):
    if not path.exists():
        fail.append(f"{path.relative_to(_src.REPO)} نیست — بررسی پوچ می‌شد.")

if not fail:
    fail += [f"{CONFIG.relative_to(_src.REPO)}: {p}"
             for p in config_problems(CONFIG.read_text(encoding="utf-8"))]

    app = ET.fromstring(MANIFEST.read_text(encoding="utf-8")).find("application")
    ref = app.get(f"{ANDROID_NS}networkSecurityConfig") if app is not None else None
    if ref != "@xml/network_security_config":
        fail.append(
            f"مانیفست به network_security_config اشاره نمی‌کند (دیده شد: {ref!r}) — "
            f"پیکربندی هیچ اثری ندارد."
        )
    if app is not None and app.get(f"{ANDROID_NS}usesCleartextTraffic") == "false":
        fail.append("مانیفست usesCleartextTraffic=false دارد و با پیکربندی می‌جنگد.")

    fail += [f"{CLIENT.relative_to(_src.REPO)}: {p}"
             for p in client_problems(CLIENT.read_text(encoding="utf-8"))]
    fail += [f"{CLIENT.relative_to(_src.REPO)}: {p}"
             for p in redirect_problems(CLIENT.read_text(encoding="utf-8"))]
    fail += [f"{HOST.relative_to(_src.REPO)}: {p}"
             for p in host_problems(HOST.read_text(encoding="utf-8"))]

if fail:
    print(f"✗ {len(fail)} مشکل در متنِ سادهٔ اشتراکِ کارگاه")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ متنِ ساده: base-config باز، بی دامنهٔ IP، LanClient پیش از اتصال "
      f"نشانیِ کارگاه را می‌سنجد و دنبالِ حواله نمی‌رود "
      f"({len(BROKEN) + len(BROKEN_CLIENT) + len(BROKEN_SEND)} نمونهٔ خراب گرفته شد)")
