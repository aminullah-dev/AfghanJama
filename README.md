<div dir="rtl">

# خیاط‌یار

**نرم‌افزارِ ادارهٔ کارگاهِ خیاطی — از سفارشِ مشتری تا دفترِ پول.**

یک کارگاهِ خیاطی چند دفتر دارد که هیچ‌کدام با هم نمی‌خوانند: دفترِ
سفارش، دفترِ پارچه، دفترِ خیاط‌ها، و دفترِ پول. خیاط‌یار هر چهار را یکی
می‌کند، به فارسی/دری و راست‌به‌چپ، روی گوشیِ اندروید و کامپیوترِ ویندوز
و مک — با **یک** دیتابیس که بینشان جابه‌جا می‌شود.

بی‌اینترنت کار می‌کند. همهٔ داده روی خودِ دستگاه می‌مانَد.

</div>

---

<div dir="rtl">

## چه کاری می‌کند

| | |
|---|---|
| **سفارش** | ثبت، مراحلِ تولید (برش → دوخت → نظارت → انبار)، مهلتِ تحویل، تحویلِ تکه‌تکه |
| **پول** | صندوق، دفترِ کل، ژورنال، بدهی و طلب، **اقساط و سررسید**، **وضعیتِ مالی** (نقد، طلب، کالا، بدهی، «نقد تا چند روز کفاف می‌دهد») |
| **پارچه و انبار** | موجودی، حدِ هشدار، ضایعات، پیش‌بینیِ اتمام، انبارِ محصولِ آماده، **پیشنهادِ دوخت** برای پرفروش‌های رو به اتمام و **کالای راکد** |
| **آدم‌ها** | خیاط‌ها و کارمزدشان، حضور و غیاب با **کارتِ QR** (دوربین، اسکنرِ USB یا تایپ)، حقوق، نقشِ مدیر/فروشنده/خیاط |
| **خرید** | تأمین‌کننده، فاکتور، برگشت از خرید |
| **حساب‌وکتاب** | سود هر فروش، **قیمت‌دهی** پیش از ثبتِ سفارش، صورتحسابِ مشتری |
| **مرکزِ اقدام** | بیش از بیست هشدارِ خودکار: سفارشِ معطل، مهلتِ گذشته، موادِ رو به اتمام، قسطِ سررسیدگذشته، بکاپِ نگرفته، نقدِ کمتر از بدهی، افتِ فروش، پرفروشِ رو به اتمام |

### دو چیزی که در دلِ طراحی است

**یک عدد، یک جا.** هر رقمِ پول فقط از یک فرمول می‌آید. ماشین‌حسابِ
قیمت‌دهی از همان `Margin` می‌خوانَد که دفتر سود را با آن نشان می‌دهد؛
اقساط پرچمِ «پرداخت‌شده» ندارد و تسویه‌اش از خودِ دریافتی‌ها مشتق
می‌شود. جایی که دو محاسبه ممکن بود از هم بیفتند، یکی شده است.

**یک تعریف، هر سکو.** اندروید، ویندوز، مک و آیفون یک ماژولِ مشترک
دارند (`:core`) و یک نسخهٔ دیتابیس. مهاجرتِ طرح یک بار در
`SchemaMigrations` نوشته می‌شود و به همه‌شان می‌رسد.

</div>

---

<div dir="rtl">

## نصب

**اندروید ۷ به بالا** (API ۲۴) —
[دانلودِ `KhayatYar-1.8.0.apk`](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.8.0/KhayatYar-1.8.0.apk)
(۱۶ مگابایت). چون از بازار نمی‌آید، گوشی یک بار اجازه می‌خواهد:
«نصب از منابع ناشناس» را برای مرورگر یا فایل‌منیجر روشن کنید.
اگر نسخهٔ قبلی روی گوشی‌تان نصب است،
[راهنمای رفتن به ۱.۸.۰](docs/MIGRATION-ANDROID-1.8.0.md) را بخوانید —
باید یک بار پشتیبان بگیرید و از نو نصب کنید.

**ویندوز ۱۰ به بالا (۶۴-بیتی)** —
[دانلودِ `KhayatYar-1.8.0.msi`](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.8.0/KhayatYar-1.8.0.msi)
(۱۳۶ مگابایت). راهنمای کامل در
[`docs/WINDOWS-NASB.md`](docs/WINDOWS-NASB.md).

**مک (اپل سیلیکون)** —
[دانلودِ `KhayatYar-1.8.0.dmg`](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.8.0/KhayatYar-1.8.0.dmg)
(۱۵۸ مگابایت). **امضاشده و مهرخوردهٔ اپل** — باز می‌شود بی هیچ هشداری،
حتی روی کمپیوترِ بی‌اینترنت. مهر هم روی خودِ برنامه نشسته و هم روی
DMG، پس کشیدنِ برنامه روی `/Applications` مهرش را از بین نمی‌برد.

نسخهٔ ویندوز و اندروید هنوز امضای رسمی ندارند، پس آن دو سیستم یک بار
هشدار می‌دهند. کلیدهای امضا روی کمپیوترِ خودِ کارفرما می‌مانند و
هیچ‌وقت بیرون نمی‌روند:
[`tools/macos-signing/`](tools/macos-signing/README.md) و
[`tools/android-signing/`](tools/android-signing/README.md). همهٔ نسخه‌ها در
[صفحهٔ Releases](https://github.com/aminullah-dev/AfghanJama/releases).

راهنمای کارِ روزمره: [`RAHNAMA.md`](RAHNAMA.md)

**نسخهٔ بعد: ۱.۹.۱ (شمارهٔ ساخت ۱۵) — هنوز منتشر نشده.** چهار PR:
#27 درست‌کاریِ اشتراکِ کارگاه، #28 امنیتِ آن (یک دیتابیس، روشن‌شدنِ خودکار
فقط در شبکهٔ کارگاه، سقفِ رمز، نقش)، #29 پیدا شدنش روی مک و ویندوز، و #30
یک نسخه برای همهٔ سکوها: هر سه بسته از یک تگ و یک کامیت، و نسخه و کامیت در
«دربارهٔ خیاط‌یار» خودِ برنامه. **هر گوشیِ اندرویدیِ کارگر باید ۱.۹.۱ را
نصب کند**؛ گوشیِ ۱.۹.۰ به هیچ دستگاهِ اصلی‌ای وصل نمی‌شود. متنِ کامل:
[`docs/RELEASE-1.9.1.md`](docs/RELEASE-1.9.1.md).

**پس از آن: ۱.۱۰.۰ (شمارهٔ ساخت ۱۶) — کامپیوتر هم‌پای گوشی.** سه PR: #32
نوارِ گروه‌بندی‌شده، «خانه»، «تنظیمات»، حساب و خروجیِ اکسل؛ #33 پشتیبانِ
خودکارِ روزانه با عکس‌ها؛ و #34 فاکتور از انبارِ محصول، تماس و واتساپ،
یادآور و چاپِ برچسب. قراردادِ اشتراکِ کارگاه عوض نشده است. متنِ کامل:
[`docs/RELEASE-1.10.0.md`](docs/RELEASE-1.10.0.md).

**پس از آن: ۱.۱۱.۰ (شمارهٔ ساخت ۱۷) — کارگاهِ وصل.** کامپیوترِ دفتر مرکزِ
کارگاه است و هر گوشی (کارگر، صاحبِ کار، تابلو؛ اندروید و آیفون) یک بار با QR
جفت می‌شود. قراردادِ ۲: هر پیام رمز و امضا شده، کلیدِ هر گوشی در Keystore یا
Keychain، و گوشیِ جفت‌نشده هیچ داده‌ای نمی‌گیرد. گوشی‌های ۱.۹ و ۱.۱۰ تا ۳۰ روز
پس از آخرین‌شان کار می‌کنند و بعد پیامِ «به‌روز کنید» می‌گیرند. دفتر به ۶۷
می‌رسد. چهار PR: #36 تا #40. متنِ کامل:
[`docs/RELEASE-1.11.0.md`](docs/RELEASE-1.11.0.md).

**پس از آن: ۱.۱۲.۰ (شمارهٔ ساخت ۱۸) — داشبوردِ مادر.** صفحهٔ اولِ کامپیوتر
داشبورد است و هر عدد منبع و تازگی‌اش را می‌گوید؛ نامعلوم «نامعلوم» است، نه
صفر. ورود روی هر سه سکو کاشیِ هر نفر است و نقش را فقط مدیر عوض می‌کند. یک تم
برای گوشی، آیفون و کامپیوتر. متنِ کامل:
[`docs/RELEASE-1.12.0.md`](docs/RELEASE-1.12.0.md).

**لایسنس (از نسخهٔ ۱.۹.۰).** هر کارگاه یک لایسنس می‌خواهد که لینومیک
بی‌اینترنت فعال می‌کند و به دستگاهِ اصلی (یا تنها) بسته است؛ گوشی‌های
کارگر زیرِ همان‌اند. بی‌لایسنس، ۱۴ روز و ۲۰ سفارش کامل کار می‌کند و بعد
فقط‌خواندنی می‌شود — هیچ داده‌ای پاک یا پنهان نمی‌شود. صدور و فعال‌سازی:
[`docs/LICENSING.md`](docs/LICENSING.md).

</div>

---

<div dir="rtl">

## ساختِ پروژه

نیاز: **JDK 17**. اندروید با Android Studio، ویندوز با Gradle.

```bash
# نسخهٔ اندروید — در Android Studio: Build → Generate Signed App Bundle / APK
./gradlew :app:assembleDebug

# نسخهٔ ویندوز — فقط روی خودِ ویندوز (jpackage به WiX نیاز دارد)
gradlew.bat :desktop:packageMsi

# نسخهٔ مک — فقط روی خودِ مک (jpackage به hdiutil نیاز دارد)
./gradlew :desktop:packageDmg

# آزمون‌های ریاضیِ پول
./gradlew testDebugUnitTest

# ۵۸ بررسیِ ساختاری — چند ثانیه، بی‌نیاز به Gradle
python3 tools/checks/run_all.py

# پیش از تگ زدن: نسخه از ریلیزِ قبلی بالاتر است؟
python3 tools/release/gate.py
```

**انتشار فقط از یک تگ.** تگِ `v<appVersion>` روی کامیتِ ادغام‌شده
`release.yml` را راه می‌اندازد: APK، DMG و MSI از همان یک کامیت. امضای
مک فقط روی همان تگ و با درختِ تمیز انجام می‌شود. هر ساختِ دیگری در
«دربارهٔ خیاط‌یار» «آزمایشی» می‌گوید.

مراحلِ کاملِ انتشار: [`DELIVERY.md`](DELIVERY.md)

**پیش از ادغامِ هر شاخه‌ای، [`docs/BRANCHES.md`](docs/BRANCHES.md) را بخوانید.** سه شاخهٔ کهنه که ادغامشان ده‌ها هزار خط را پاک می‌کرد در ۲۰ سپتامبر ۲۰۲۶ پاک شدند؛ آن سند می‌گوید چرا و چطور وارسی شد.

</div>

---

<div dir="rtl">

## ساختار

```
app/       نسخهٔ اندروید — Activity، ناوبری، مهاجرت‌های تاریخی
core/      مشترکِ هر سه سکو — صفحه‌ها، ViewModelها، Room، منطقِ پول
desktop/   نسخهٔ ویندوز و مک — پنجره، فهرستِ کنار، MSI و DMG
iosApp/    پوستهٔ SwiftUI که همان :core را سوار می‌کند
tools/     ۵۸ بررسیِ ساختاری، دروازهٔ انتشار، ناشرِ وردپرس، امضای ویندوز و مک
docs/      معماری، راهنمای ویندوز، مسئله‌های شناخته‌شده
```

**Kotlin + Jetpack Compose + Room.** ۴۷۶ فایلِ کاتلین، ۴۶ صفحه،
۴۳ جدول، نسخهٔ دیتابیسِ ۶۵.

### بررسی‌های ساختاری

`tools/checks/` پنجاه‌وهشت بررسیِ پایتونی دارد که در چند ثانیه می‌دوند و
چیزهایی را می‌گیرند که کامپایلر نمی‌گیرد: ایمپورتِ جاافتاده، مهاجرتِ
گم‌شده، عملیاتِ غیراتمیک، صفحهٔ بی‌مسیر، فاصله‌های خارج از شبکهٔ ۴dp،
و منطقِ پول که با شبیه‌سازیِ مستقل سنجیده می‌شود.

قاعده‌شان در [`tools/checks/README.md`](tools/checks/README.md) است و
مهم‌ترینش این است: **هر بررسیِ تازه باید روی کدِ عمداً خراب قرمز شود،
وگرنه پوچ است.**

</div>

---

## English

**KhayatYar** — workshop-management software for a tailoring business:
orders, production stages, fabric inventory, tailor wages, payroll,
purchasing, and a full double-entry money ledger. Persian/Dari, RTL,
offline-first. All data stays on the device.

Highlights: QR employee cards for attendance (phone camera, USB
scanner, or typed code), a financial-status snapshot straight from the
double-entry ledger, restock and dead-stock suggestions for finished
goods, and one-scan pairing for worker phones on the workshop Wi-Fi.

Runs on **Android 7+** (API 24), **Windows 10+**, and **macOS** (Apple
silicon) from one shared Kotlin codebase (`:core`) with a single
database version, so the same file moves between phone and PC. An iOS
shell builds from the same `:core` and is not published yet.

Built with Kotlin, Jetpack Compose, and Room. 476 Kotlin files,
46 screens, 43 tables.

Two ideas shape the design. *One number, one place*: every money figure
comes from a single formula, so the quote calculator and the ledger can
never disagree. *One definition, every platform*: a schema migration is
written once and reaches Android, Windows, macOS and iOS alike.

Alongside the code, `tools/checks/` holds 58 Python checks that run in
seconds and catch what the compiler cannot — missing imports, absent
migrations, non-atomic operations, unreachable screens, and money logic
verified against an independent simulation. Every check must be proven
to fail on deliberately broken code before it is accepted.

Next release: **1.9.1 (build 15)**, not yet published — #27 workshop-link
correctness, #28 its security, #29 finding it on Mac and Windows, and #30 one
version for every platform: every package is built from one tag and one
commit, and the app shows its version, commit and whether it is a release or
a test build under "About". Every Android worker phone must install 1.9.1; a
1.9.0 worker phone cannot reach any main device. Notes (Dari):
[`docs/RELEASE-1.9.1.md`](docs/RELEASE-1.9.1.md).

After that: **1.10.0 (build 16)**, desktop parity — #32 grouped sidebar,
Home, Settings, account and CSV export; #33 daily automatic backup with
photos; #34 invoice from the product warehouse, calls and WhatsApp, reminders
and label printing. The workshop-sharing protocol is unchanged. Notes (Dari):
[`docs/RELEASE-1.10.0.md`](docs/RELEASE-1.10.0.md).

Then: **1.11.0 (build 17)**, the connected workshop — the office computer is
the hub and every phone (worker, owner, wall board; Android and iPhone) pairs
once by QR. Protocol 2: every message is encrypted and authenticated
(ECDH P-256 + HKDF + AES-256-GCM from platform libraries), each phone's key
lives in the Android Keystore or iOS Keychain, and an unpaired device gets
nothing. 1.9/1.10 phones keep working for 30 days after the last one is seen,
then get a Dari "update this phone" message. Database 67. Notes (Dari):
[`docs/RELEASE-1.11.0.md`](docs/RELEASE-1.11.0.md).

Then: **1.12.0 (build 18)**, the smart desktop dashboard — the landing page
on Mac and Windows is a four-column digest where every number names its
source and freshness and an unknown is never shown as 0; login on all three
platforms is by person tiles (name, role pill, own 4-digit PIN, persistent
lockout); one shared theme, shape scale, pill and glass. Database unchanged.
Notes (Dari): [`docs/RELEASE-1.12.0.md`](docs/RELEASE-1.12.0.md).

From 1.9.0 every workshop needs an offline, device-bound licence issued by
Linumic (14-day / 20-order trial, then read-only; worker phones are covered
by the main device) — see [`docs/LICENSING.md`](docs/LICENSING.md).

Build with JDK 17: `./gradlew testDebugUnitTest` for the money tests,
`python3 tools/checks/run_all.py` for the structural checks, and
`gradlew.bat :desktop:packageMsi` on Windows for the installer, and
`./gradlew :desktop:packageDmg` on macOS for the disk image.

Downloads —
[Android APK](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.8.0/KhayatYar-1.8.0.apk)
(16 MB) ·
[Windows MSI](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.8.0/KhayatYar-1.8.0.msi)
(136 MB) ·
[macOS DMG](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.8.0/KhayatYar-1.8.0.dmg)
(158 MB) ·
[all releases](https://github.com/aminullah-dev/AfghanJama/releases).
The macOS DMG is signed and notarized by Apple — it opens with no
warning, even offline. Windows and Android are not signed through an
official channel yet, so those two show one warning on first run.
Signing for macOS runs on the owner's own Mac — the Developer ID
certificate never leaves that machine.
