#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""قراردادِ ۲ («کارگاهِ وصل»): رمزنگاری فقط از سکو، مرزِ نشانی پیش از اتصال، و
هیچ کلیدی در گزارش.

**چرا این بررسی هست.** امنیتِ قراردادِ ۲ در چند خط است که هیچ کامپایلری
نمی‌بیند: یک `Cipher.getInstance` در فایلِ دیگری، یک `Random` به‌جای
`SecureRandom`، یک `toString` که کلید را چاپ کند، یا مشتری‌ای که پیش از
سنجیدنِ نشانی وصل شود. آزمون‌ها رفتار را می‌سنجند؛ این بررسی جای آن خط‌ها را.

  ۱ **رمزنگاری فقط در `LanPrims`.** هیچ فایلِ دیگری در `lan/`
    `javax.crypto`، `KeyAgreement`، `KeyPairGenerator`، `SecureRandom`،
    `CommonCrypto` یا `kotlin.random` را نام نمی‌برد. هر چیزِ تازه از
    `LanPrims` و `LanCrypto` می‌گذرد، که با بردارهای منتشرشده آزموده
    می‌شوند. (`LanGuard` فقط SHA-256 برای مقایسهٔ رمزِ قراردادِ ۱ دارد.)
  ۲ **مرزِ نشانی پیش از اتصال.** هر دو مشتریِ HTTPِ قراردادِ ۲
    (`HubTransport.jvm.kt` و `.ios.kt`) پیش از ساختنِ اتصال
    `workshopHostRefusal` را صدا می‌زنند، و هیچ‌کدام دنبالِ حواله نمی‌رود.
  ۳ **کلید چاپ نمی‌شود.** هر کلاسی که کلید یا راز نگه می‌دارد `toString`
    خودش را دارد.
  ۴ **گزارشی از `lan/` بیرون نمی‌رود** — نه `println`، نه `Log.`، نه
    `printStackTrace`. پیامِ خطا به کاربر از متن‌های ثابت است.
  ۵ **حافظهٔ nonce دست‌کم دو برابرِ بازهٔ زمان است**؛ وگرنه درخواستِ
    ضبط‌شده در دقیقه‌های آخرِ بازه دوباره پذیرفته می‌شود.
"""
import re
import sys

import _src

LAN_DIRS = [r / "com/afghanjama/lan" for r in _src.CORE_ROOTS]
PRIMS = {"LanPrims.kt", "LanPrims.jvm.kt", "LanPrims.ios.kt"}
# قراردادِ ۱: رمزِ شش‌رقمی با SHA-256 و زمانِ ثابت مقایسه می‌شود — نه رمزنگاری.
SHA_ONLY = {"LanGuard.kt"}
TRANSPORTS = ["HubTransport.jvm.kt", "HubTransport.ios.kt"]
PROTOCOL = "HubProtocol.kt"

# کلاس‌هایی که کلید یا راز دارند — و فایلشان.
SECRET_HOLDERS = {
    "EcKeyPair": "LanPrims.kt",
    "Directional": "LanCrypto.kt",
    "Link": "PairingV2.kt",
    "PairGrant": "HubMessages.kt",
    "PairDraft": "HubEnvelope.kt",
    "PairOpened": "HubEnvelope.kt",
    "DeviceCred": "HubClient.kt",
    "PairPending": "HubClient.kt",
}

FORBIDDEN_CRYPTO = re.compile(
    r"\bjavax\.crypto\b|\bKeyAgreement\b|\bKeyPairGenerator\b|\bSecureRandom\b|"
    r"\bplatform\.CoreCrypto\b|\bkotlin\.random\b|\bRandom\s*\(|\bRandom\.(?:next|Default)|\bCipher\b"
)
FORBIDDEN_LOG = re.compile(r"\bprintln\s*\(|\bprint\s*\(|\bLog\.[a-z]\s*\(|\.printStackTrace\s*\(|System\.(?:out|err)\b")


def strip(src: str) -> str:
    src = re.sub(r"/\*(?:.|\n)*?\*/", lambda m: "\n" * m.group(0).count("\n"), src)
    src = re.sub(r"//[^\n]*", "", src)
    return re.sub(r'"(?:\\.|[^"\\\n])*"', '""', src)


def crypto_problems(name: str, kt: str) -> list[str]:
    """رمزنگاری بیرون از `LanPrims`؛ خالی یعنی سالم."""
    if name in PRIMS:
        return []
    src = strip(kt)
    out = []
    for m in FORBIDDEN_CRYPTO.finditer(src):
        line = src.count("\n", 0, m.start()) + 1
        out.append(f"خطِ {line}: «{m.group(0)}» بیرون از LanPrims — رمزنگاری فقط از راهِ LanPrims.")
    if name not in SHA_ONLY and re.search(r"\bMessageDigest\b", src):
        out.append("`MessageDigest` بیرون از LanPrims — از `LanPrims.sha256` بگیرید.")
    return out


def log_problems(kt: str) -> list[str]:
    src = strip(kt)
    out = []
    for m in FORBIDDEN_LOG.finditer(src):
        line = src.count("\n", 0, m.start()) + 1
        out.append(f"خطِ {line}: «{m.group(0).strip()}» — از lan/ هیچ گزارشی بیرون نمی‌رود (کلید و راز اینجاست).")
    return out


def transport_problems(kt: str) -> list[str]:
    """مرزِ نشانی پیش از اتصال، و بی حواله؛ خالی یعنی سالم."""
    src = strip(kt)
    m = re.search(r"override suspend fun exchange\((?:.|\n)*?\n    }\n", src)
    if not m:
        return ["`exchange` پیدا نشد — بررسیِ مرزِ نشانی پوچ می‌شد."]
    body = m.group(0)
    guard = body.find("workshopHostRefusal(")
    connect = min([i for i in (body.find("openConnection"), body.find("dataTaskWithRequest")) if i >= 0] or [-1])
    out = []
    if guard < 0:
        out.append("`exchange` پیش از اتصال `workshopHostRefusal` را نمی‌پرسد — متنِ ساده به هر نشانی می‌رود.")
    elif connect >= 0 and guard > connect:
        out.append("`exchange` نشانی را پس از ساختنِ اتصال می‌سنجد.")
    elif "runCatching" in body[:guard]:
        out.append("مرزِ نشانی درونِ runCatching است و پیامش گم می‌شود.")
    jvm_redirect = "instanceFollowRedirects = false" in src
    ios_redirect = re.search(r"willPerformHTTPRedirection[^{]*\{\s*completionHandler\(null\)", src) is not None
    if not (jvm_redirect or ios_redirect):
        out.append("حواله بسته نیست — چیزی روی نشانیِ کارگاه گوشی را به میزبانِ دیگری می‌فرستد.")
    if "instanceFollowRedirects = true" in src:
        out.append("حواله صریحاً باز است.")
    return out


def tostring_problems(cls: str, kt: str) -> list[str]:
    src = strip(kt)
    m = re.search(r"\bclass\s+" + cls + r"\b", src)
    if not m:
        return [f"کلاسِ `{cls}` پیدا نشد — بررسیِ چاپِ کلید پوچ می‌شد."]
    # بدنهٔ کلاس: از «{» نخست پس از سازنده تا آکولادِ هم‌تراز.
    i = m.end()
    depth = 0
    while i < len(src) and not (src[i] == "{" and depth == 0):
        if src[i] == "(":
            depth += 1
        elif src[i] == ")":
            depth -= 1
        i += 1
    if i >= len(src):
        return [f"`{cls}` بدنه ندارد و `toString`ِ پیش‌فرض کلید را چاپ می‌کند."]
    depth = 0
    j = i
    while j < len(src):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                break
        j += 1
    if not re.search(r"override\s+fun\s+toString\s*\(", src[i:j]):
        return [f"`{cls}` کلید دارد ولی `toString` خودش را ندارد — کلید در گزارش می‌نشیند."]
    return []


def keep_problems(kt: str) -> list[str]:
    def ms(name):
        m = re.search(r"const val " + name + r"\s*=\s*([0-9_ *L]+)", kt)
        if not m:
            return None
        return eval(m.group(1).replace("_", "").replace("L", ""))
    window, keep = ms("TIME_WINDOW_MS"), ms("NONCE_KEEP_MS")
    if window is None or keep is None:
        return ["`TIME_WINDOW_MS` یا `NONCE_KEEP_MS` پیدا نشد."]
    if keep < 2 * window:
        return [f"حافظهٔ nonce ({keep} ms) کمتر از دو برابرِ بازهٔ زمان ({window} ms) است — تکرار در انتهای بازه پذیرفته می‌شود."]
    return []


# ── خودآزمایی ────────────────────────────────────────────────────────
fail = []
BAD_CRYPTO = {
    "Cipher در فایلِ دیگر": "import javax.crypto.Cipher\nfun f() = Cipher.getInstance(\"AES\")\n",
    "Random برای راز": "fun key() = ByteArray(32).also { kotlin.random.Random.nextBytes(it) }\n",
    "SecureRandom کنارِ LanPrims": "val r = java.security.SecureRandom()\n",
    "Random() ساده": "val r = Random(1)\n",
}
for name, x in BAD_CRYPTO.items():
    if not crypto_problems("HubClient.kt", x):
        fail.append(f"خودآزمایی: «{name}» گرفته نشد — بررسی پوچ است.")
if crypto_problems("HubClient.kt", "fun f() = LanPrims.random(32)\n// javax.crypto در توضیح\n"):
    fail.append("خودآزمایی: صدای درستِ LanPrims رد شد — بررسی خراب است.")
if crypto_problems("LanPrims.jvm.kt", "import javax.crypto.Cipher\n"):
    fail.append("خودآزمایی: LanPrims خودش رد شد — بررسی خراب است.")

BAD_LOG = {"println": "fun f(k: ByteArray) { println(k) }\n", "Log.d": "fun f() { Log.d(\"t\", key) }\n",
           "printStackTrace": "fun f(e: Throwable) { e.printStackTrace() }\n"}
for name, x in BAD_LOG.items():
    if not log_problems(x):
        fail.append(f"خودآزمایی: گزارشِ «{name}» گرفته نشد.")

GOOD_T = (
    "class T {\n    override suspend fun exchange(host: String): R {\n"
    "        workshopHostRefusal(host, false)?.let { return Err(it) }\n"
    "        return runCatching { (URL(u).openConnection() as H).apply { instanceFollowRedirects = false } }\n"
    "    }\n}\n"
)
BAD_T = {
    "بی مرز": "class T {\n    override suspend fun exchange(host: String): R {\n"
              "        return runCatching { (URL(u).openConnection() as H).apply { instanceFollowRedirects = false } }\n    }\n}\n",
    "مرز پس از اتصال": "class T {\n    override suspend fun exchange(host: String): R {\n"
                        "        val c = URL(u).openConnection()\n        workshopHostRefusal(host, false)\n"
                        "        c.instanceFollowRedirects = false\n    }\n}\n",
    "حوالهٔ باز": "class T {\n    override suspend fun exchange(host: String): R {\n"
                  "        workshopHostRefusal(host, false)?.let { return Err(it) }\n"
                  "        return URL(u).openConnection()\n    }\n}\n",
}
if transport_problems(GOOD_T):
    fail.append("خودآزمایی: مشتریِ درست رد شد — بررسی خراب است.")
for name, x in BAD_T.items():
    if not transport_problems(x):
        fail.append(f"خودآزمایی: مشتریِ «{name}» گرفته نشد — بررسی پوچ است.")

if tostring_problems("K", "class K(val key: ByteArray) {\n    override fun toString() = \"K(…)\"\n}\n"):
    fail.append("خودآزمایی: کلاسِ درست رد شد.")
if not tostring_problems("K", "data class K(val key: ByteArray)\n"):
    fail.append("خودآزمایی: کلاسِ بی‌بدنه گرفته نشد.")
if not tostring_problems("K", "class K(val key: ByteArray) {\n    fun x() = 1\n}\n"):
    fail.append("خودآزمایی: کلاسِ بی toString گرفته نشد.")
if not keep_problems("const val TIME_WINDOW_MS = 10 * 60_000L\nconst val NONCE_KEEP_MS = 15 * 60_000L\n"):
    fail.append("خودآزمایی: حافظهٔ کوتاهِ nonce گرفته نشد.")

# ── خودِ پروژه ────────────────────────────────────────────────────
files = {}
for d in LAN_DIRS:
    if d.exists():
        for p in d.glob("*.kt"):
            files[p.name] = p
for need in list(PRIMS) + TRANSPORTS + [PROTOCOL] + sorted(set(SECRET_HOLDERS.values())):
    if need not in files:
        fail.append(f"{need} در lan/ نیست — بررسی پوچ می‌شد.")

if not fail:
    for name, p in sorted(files.items()):
        kt = p.read_text(encoding="utf-8")
        rel = p.relative_to(_src.REPO)
        fail += [f"{rel}: {x}" for x in crypto_problems(name, kt)]
        fail += [f"{rel}: {x}" for x in log_problems(kt)]
    for t in TRANSPORTS:
        fail += [f"{t}: {x}" for x in transport_problems(files[t].read_text(encoding="utf-8"))]
    for cls, fname in SECRET_HOLDERS.items():
        fail += [f"{fname}: {x}" for x in tostring_problems(cls, files[fname].read_text(encoding="utf-8"))]
    fail += [f"{PROTOCOL}: {x}" for x in keep_problems(files[PROTOCOL].read_text(encoding="utf-8"))]

if fail:
    print(f"✗ {len(fail)} مشکل در نگهبانیِ قراردادِ ۲")
    for f in fail:
        print(f"  • {f}")
    sys.exit(1)

print(f"✓ قراردادِ ۲: رمزنگاری فقط در LanPrims، مشتری‌ها پیش از اتصال نشانی را می‌سنجند و حواله ندارند، "
      f"{len(SECRET_HOLDERS)} نگهدارندهٔ کلید چاپ نمی‌شوند، lan/ گزارش نمی‌دهد "
      f"({len(BAD_CRYPTO) + len(BAD_LOG) + len(BAD_T) + 3} نمونهٔ خراب گرفته شد)")
