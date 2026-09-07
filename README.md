<div dir="rtl">

# خیاط‌یار

**نرم‌افزارِ ادارهٔ کارگاهِ خیاطی — از سفارشِ مشتری تا دفترِ پول.**

یک کارگاهِ خیاطی چند دفتر دارد که هیچ‌کدام با هم نمی‌خوانند: دفترِ
سفارش، دفترِ پارچه، دفترِ خیاط‌ها، و دفترِ پول. خیاط‌یار هر چهار را یکی
می‌کند، به فارسی/دری و راست‌به‌چپ، روی گوشیِ اندروید و کامپیوترِ ویندوز
— با **یک** دیتابیس که بینشان جابه‌جا می‌شود.

بی‌اینترنت کار می‌کند. همهٔ داده روی خودِ دستگاه می‌مانَد.

</div>

---

<div dir="rtl">

## چه کاری می‌کند

| | |
|---|---|
| **سفارش** | ثبت، مراحلِ تولید (برش → دوخت → نظارت → انبار)، مهلتِ تحویل، تحویلِ تکه‌تکه |
| **پول** | صندوق، دفترِ کل، ژورنال، بدهی و طلب، **اقساط و سررسید** |
| **پارچه و انبار** | موجودی، حدِ هشدار، ضایعات، پیش‌بینیِ اتمام، انبارِ محصولِ آماده |
| **آدم‌ها** | خیاط‌ها و کارمزدشان، حضور و غیاب، حقوق، نقشِ مدیر/فروشنده/خیاط |
| **خرید** | تأمین‌کننده، فاکتور، برگشت از خرید |
| **حساب‌وکتاب** | سود هر فروش، **قیمت‌دهی** پیش از ثبتِ سفارش، صورتحسابِ مشتری |
| **مرکزِ اقدام** | پانزده هشدارِ خودکار: سفارشِ معطل، مهلتِ گذشته، موادِ رو به اتمام، قسطِ سررسیدگذشته، بکاپِ نگرفته |

### دو چیزی که در دلِ طراحی است

**یک عدد، یک جا.** هر رقمِ پول فقط از یک فرمول می‌آید. ماشین‌حسابِ
قیمت‌دهی از همان `Margin` می‌خوانَد که دفتر سود را با آن نشان می‌دهد؛
اقساط پرچمِ «پرداخت‌شده» ندارد و تسویه‌اش از خودِ دریافتی‌ها مشتق
می‌شود. جایی که دو محاسبه ممکن بود از هم بیفتند، یکی شده است.

**یک تعریف، دو سکو.** اندروید و ویندوز یک ماژولِ مشترک دارند
(`:core`) و یک نسخهٔ دیتابیس. مهاجرتِ طرح یک بار در `SchemaMigrations`
نوشته می‌شود و به هر دو می‌رسد.

</div>

---

<div dir="rtl">

## نصب

**اندروید ۷ به بالا** (API ۲۴) —
[دانلودِ `KhayatYar-1.7.0.apk`](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.7.0/KhayatYar-1.7.0.apk)
(۱۵ مگابایت). چون از بازار نمی‌آید، گوشی یک بار اجازه می‌خواهد:
«نصب از منابع ناشناس» را برای مرورگر یا فایل‌منیجر روشن کنید.

**ویندوز ۱۰ به بالا (۶۴-بیتی)** —
[دانلودِ `KhayatYar-1.7.0.msi`](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.7.0/KhayatYar-1.7.0.msi)
(۱۳۵ مگابایت). راهنمای کامل در
[`docs/WINDOWS-NASB.md`](docs/WINDOWS-NASB.md).

هر دو فایل امضای رسمی ندارند، پس ویندوز و اندروید یک بار هشدار
می‌دهند. همهٔ نسخه‌ها در
[صفحهٔ Releases](https://github.com/aminullah-dev/AfghanJama/releases).

راهنمای کارِ روزمره: [`RAHNAMA.md`](RAHNAMA.md)

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

# آزمون‌های ریاضیِ پول
./gradlew testDebugUnitTest

# ۵۰ بررسیِ ساختاری — چند ثانیه، بی‌نیاز به Gradle
python3 tools/checks/run_all.py
```

مراحلِ کاملِ انتشار: [`DELIVERY.md`](DELIVERY.md)

**پیش از ادغامِ هر شاخه‌ای، [`docs/BRANCHES.md`](docs/BRANCHES.md) را بخوانید.** یک شاخهٔ کهنه در مخزن هست که ادغامش ۳۷ هزار خط را پاک می‌کند.

</div>

---

<div dir="rtl">

## ساختار

```
app/       نسخهٔ اندروید — Activity، ناوبری، مهاجرت‌های تاریخی
core/      مشترکِ هر دو سکو — صفحه‌ها، ViewModelها، Room، منطقِ پول
desktop/   نسخهٔ ویندوز — پنجره، فهرستِ کنار، بسته‌بندیِ MSI
tools/     ۵۰ بررسیِ ساختاری، ناشرِ وردپرس، اسکریپت‌های امضای ویندوز
docs/      معماری، راهنمای ویندوز، مسئله‌های شناخته‌شده
```

**Kotlin + Jetpack Compose + Room.** ۳۱۳ فایلِ کاتلین، ۳۹ صفحه،
۴۲ جدول، نسخهٔ دیتابیسِ ۶۴.

### بررسی‌های ساختاری

`tools/checks/` پنجاه بررسیِ پایتونی دارد که در چند ثانیه می‌دوند و
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

Runs on **Android 7+** (API 24) and **Windows 10+** from one shared Kotlin
codebase (`:core`) with a single database version, so the same file
moves between phone and PC.

Built with Kotlin, Jetpack Compose, and Room. 313 Kotlin files,
39 screens, 42 tables.

Two ideas shape the design. *One number, one place*: every money figure
comes from a single formula, so the quote calculator and the ledger can
never disagree. *One definition, two platforms*: a schema migration is
written once and reaches both Android and Windows.

Alongside the code, `tools/checks/` holds 50 Python checks that run in
seconds and catch what the compiler cannot — missing imports, absent
migrations, non-atomic operations, unreachable screens, and money logic
verified against an independent simulation. Every check must be proven
to fail on deliberately broken code before it is accepted.

Build with JDK 17: `./gradlew testDebugUnitTest` for the money tests,
`python3 tools/checks/run_all.py` for the structural checks, and
`gradlew.bat :desktop:packageMsi` on Windows for the installer.

Downloads —
[Android APK](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.7.0/KhayatYar-1.7.0.apk)
(15 MB) ·
[Windows MSI](https://github.com/aminullah-dev/AfghanJama/releases/download/v1.7.0/KhayatYar-1.7.0.msi)
(135 MB) ·
[all releases](https://github.com/aminullah-dev/AfghanJama/releases).
Neither file is code-signed, so both platforms warn once on first run.
