<div dir="rtl">

# لایسنسِ خیاط‌یار

**تصمیمِ کارفرما (۸ اکتوبر ۲۰۲۶):** هر کارگاه لایسنس می‌خواهد و لینومیک
فعالش می‌کند — کاملاً بی‌اینترنت، و بسته به یک دستگاه. دستگاهِ **تنها**
یا **اصلیِ** کارگاه لایسنس را نگه می‌دارد؛ گوشی‌های **کارگر** زیرِ همان
لایسنس‌اند و لایسنسِ جدا نمی‌خواهند. «یک لایسنس برای کلِ کارگاه — گوشی‌ها
و کمپیوترِ دفتر.»

پروتکل مشترکِ همهٔ محصولاتِ لینومیک است و جای دیگری نوشته شده:
`~/Projects/Multiplatform/Linumic/licensing/PROTOCOL.md`. این سند فقط
می‌گوید در خیاط‌یار چطور کار می‌کند.

---

## ۱. مشتری کدِ دستگاه را از کجا پیدا کند

| سکو | راه |
|---|---|
| اندروید | تنظیمات ← کارتِ اول «لایسنس» ← «لایسنس و کدِ دستگاه» |
| ویندوز / مک | نوارِ کنار ← «لایسنس» |
| آیفون | تنظیمات ← «لایسنس» |

و در دورهٔ آزمایشی یا فقط‌خواندنی، ضربه روی **بنرِ لایسنس** در خانه هم
همان صفحه را باز می‌کند.

کد شانزده نویسه است، چهار-چهار: مثلاً `K7Q2-9XMB-4T1D-WP3C`. دکمهٔ
«کپی» آن را در حافظه می‌گذارد و «فرستادن» مستقیم در واتساپ (یا هر
برنامهٔ دیگر) می‌فرستد. پشتِ تلفن هم خوانده می‌شود: حروفِ I، L، O و U
در کد نیستند، و اگر کسی «O» گفت یعنی صفر.

> **مهم برای اندروید:** کدِ گوشی به **کلیدِ امضای اپ** هم بسته است
> (`ANDROID_ID`). نسخهٔ دیباگ و نسخهٔ امضاشدهٔ release روی یک گوشی دو
> کدِ متفاوت نشان می‌دهند. لایسنس را همیشه از کدی صادر کنید که **نسخهٔ
> امضاشده‌ای که به مشتری داده‌اید** نشان می‌دهد.

---

## ۲. صدورِ لایسنس (روی مکِ لینومیک)

```bash
cd ~/Projects/Multiplatform/Linumic/licensing

# همیشگی
.venv/bin/python linumic_license.py issue --product khayatyar \
    --customer "خیاطیِ نمونه، کابل" --machine K7Q2-9XMB-4T1D-WP3C

# تاریخ‌دار (آخرین روزِ معتبر)
.venv/bin/python linumic_license.py issue --product khayatyar \
    --customer "خیاطیِ نمونه، کابل" --machine K7Q2-9XMB-4T1D-WP3C --expires 2027-10-08
```

ابزار شمارهٔ بعدی را خودش می‌دهد (`KY-2026-0001`، …)، کلید را چاپ
می‌کند، فایلِ `~/.linumic/licenses/<شماره>.lnmlic` را می‌سازد و در دفترِ
`~/.linumic/licenses/issued.csv` می‌نویسد. پیش از دادن، خودش کلید را
می‌سنجد.

برای سنجیدنِ کلیدی که کسی فرستاده:

```bash
.venv/bin/python linumic_license.py verify --product khayatyar "LNM1.…"
```

کلیدِ خصوصی (`~/.linumic/license-keys/khayatyar-private.pem`) هرگز از آن
مک بیرون نمی‌رود و هرگز در هیچ مخزن یا اپی نمی‌نشیند. **از آن در دو جای
رمزگذاری‌شده پشتیبان بگیرید**؛ گم شدنش یعنی دیگر لایسنسِ تازه‌ای نمی‌شود
داد (لایسنس‌های داده‌شده کار می‌کنند).

`--machine "*"` لایسنسی می‌سازد که روی هر دستگاهی کار می‌کند. فقط برای
موردِ خاص؛ چنین کلیدی را می‌شود به هر کارگاهِ دیگری داد.

---

## ۳. فعال‌سازی (روی دستگاهِ مشتری)

در همان صفحهٔ لایسنس:

- متنِ کلید را (که با `LNM1.` شروع می‌شود) در خانه بچسبانید و
  «فعال‌سازی» را بزنید. فاصله و خطِ تازه مهم نیست؛ واتساپ هر جا خط
  شکسته باشد، اشکالی ندارد.
- یا «باز کردنِ فایلِ لایسنس» را بزنید و فایلِ `.lnmlic` را انتخاب کنید
  (گوشی و ویندوز/مک). اگر گوشی فایل را در فهرست نشان نداد، همان متن را
  بچسبانید.

کلیدِ درست **همان لحظه** همهٔ محدودیت‌ها را برمی‌دارد: بی نصبِ دوباره،
بی واردکردنِ دوبارهٔ داده. هر چه در دورهٔ آزمایشی ثبت شده، می‌ماند.

کلیدِ نادرست هیچ چیزی را عوض نمی‌کند و می‌گوید چرا (کلید نیست، ناقص است،
امضایش نمی‌خوانَد، برای برنامهٔ دیگری است). کلیدی که برای دستگاهِ دیگری
صادر شده پذیرفته می‌شود ولی به «مهلتِ دستگاهِ تازه» می‌رود و صریح می‌گوید
کلیدِ این دستگاه را بگیرید.

---

## ۴. رفتارِ اپ در هر حالت

| حالت | کِی | چه می‌شود |
|---|---|---|
| **لایسنس فعال** | کلیدِ درست، همین دستگاه (یا `*`)، تاریخ نگذشته | همه‌چیز، بی نشان. ۳۰ روز پیش از تاریخِ لایسنس بنرِ یادآوری |
| **مهلتِ دستگاهِ تازه** | کلیدِ درست ولی برای دستگاهِ دیگر (سخت‌افزار یا سیستم عوض شده) | ۳۰ روز از اولین بار کامل کار می‌کند، با بنر |
| **مهلتِ پس از تاریخ** | تاریخِ لایسنس گذشته | ۳۰ روزِ دیگر کامل، با شمارشِ معکوس |
| **آزمایشی** | بی‌لایسنس، ۱۴ روزِ اول و زیرِ ۲۰ سفارشِ تازه | کامل؛ روی هر کاغذ «نسخهٔ آزمایشی — بدون لایسنس»؛ بنر با روز و سفارشِ مانده |
| **فقط‌خواندنی** | آزمایشی تمام شد، یا ۲۰ سفارش پر شد، یا یکی از مهلت‌ها گذشت | پایین را ببینید |

**دورهٔ آزمایشی** از اولین اجرای نسخهٔ لایسنس‌دار شروع می‌شود (نه از
روزِ نصبِ اول). سقفِ ۲۰ سفارش فقط سفارش‌هایی را می‌شمارد که **پس از**
شروعِ دوره ساخته شده‌اند — کارگاهی که با نسخهٔ قبلی صدها سفارش دارد، با
آمدنِ نسخهٔ تازه چهارده روزِ کامل می‌گیرد. سفارش‌های کارگاهِ نمونه هم
شمرده می‌شوند تا وقتی با «پاک‌کردن کارها و حساب‌ها» بروند.

شروعِ دوره هم در تنظیماتِ دستگاه نوشته می‌شود و هم در **خودِ دفتر**؛ پس
«پاک کن، دوباره نصب کن، پشتیبان را برگردان» دوره را از صفر شروع
نمی‌کند.

### فقط‌خواندنی دقیقاً چه می‌بندد

**بسته:** هر چیزی که سندی می‌سازد یا عوض می‌کند — سفارش و مراحلش،
فروش، خرید، دریافت و پرداخت، حقوق، انبار، حضور و غیاب، اطلاعاتِ پایه،
مشتری و اندازه، عکس، هزینهٔ ثابت، تأییدِ درخواست‌های گوشیِ کارگر، کارگاهِ
نمونه، و «پاک‌کردن کارها و حساب‌ها».

**همیشه باز:** باز شدنِ برنامه، دیدنِ همه‌چیز، جست‌وجو، گزارش‌ها، چاپ،
PDF، تصویر برای واتساپ (با نشان)، پیامِ آمادهٔ تحویل به مشتری،
پشتیبان‌گیری و بازیابی، و خودِ صفحهٔ لایسنس.

**هیچ داده‌ای هرگز پاک یا پنهان نمی‌شود.** اگر کسی در فقط‌خواندنی
دکمهٔ ثبت را بزند، پنجره‌ای می‌گوید چرا ثبت نشد، چه هنوز کار می‌کند، و
دکمه‌اش به صفحهٔ لایسنس می‌برد.

### ساعتِ دستگاه

اگر ساعتِ دستگاه بیش از یک روز پیش از دیرترین زمانی باشد که اپ دیده،
همان دیرترین زمان «امروز» حساب می‌شود و بنر می‌گوید ساعت را درست کنید.
یعنی عقب کشیدنِ ساعت دوره را دراز نمی‌کند — و هیچ‌وقت هم کسی را بیرون
نمی‌اندازد.

---

## ۵. گوشی‌های کارگر

- دستگاهی که در «اشتراکِ کارگاه» **کارگر** شده، لایسنسِ خودش را
  نمی‌خواهد؛ نه دورهٔ آزمایشی دارد نه سقف.
- پیروِ دستگاهِ اصلی است: دستگاهِ اصلی با هر جواب روی وای‌فای حالتِ
  لایسنسِ خودش را می‌فرستد. اگر اصلی آزمایشی باشد، کاغذِ کارگر هم نشان
  دارد؛ اگر اصلی فقط‌خواندنی شود، کارگر هم چیزی ثبت نمی‌کند.
- دستگاهِ اصلیِ فقط‌خواندنی **درخواستِ تازهٔ کارگر را هم نمی‌پذیرد** و
  به کارگر می‌گوید کارفرما باید کلید را روی دستگاهِ اصلی وارد کند.
- دستگاهِ اصلیِ نسخهٔ قدیمی (پیش از لایسنس) چیزی نمی‌فرستد و کارگر هم
  بی‌دلیل بسته نمی‌شود.

---

## ۶. وقتی دستگاه عوض می‌شود

| اتفاق | کد عوض می‌شود؟ | چه کنید |
|---|---|---|
| به‌روزرسانیِ اپ | نه | هیچ |
| نصبِ دوبارهٔ اپ روی ویندوز/مک | نه | هیچ (کلید در پوشهٔ دفتر می‌ماند) |
| پاک کردن و نصبِ دوبارهٔ اپ روی گوشی | نه، ولی کلید هم پاک می‌شود | همان کلید را دوباره بچسبانید |
| ریستِ کارخانهٔ گوشی، گوشیِ تازه، نصبِ دوبارهٔ ویندوز | بله | ۳۰ روز مهلت؛ کدِ تازه را بگیرید و کلیدِ تازه صادر کنید |
| شناسهٔ سخت‌افزار خوانده نشد | کدِ «جایگزین» | صفحه به رنگِ قرمز می‌گوید؛ این کد با نصبِ دوباره عوض می‌شود |

---

## ۷. در کد کجاست

| | |
|---|---|
| پروتکل، سنجش، کدِ دستگاه | `core/src/commonMain/.../licence/Lnm1.kt` |
| حالت‌ها و عددها (۱۴ روز، ۲۰ سفارش، ۳۰ روز) | `licence/LicencePolicy.kt` — **فقط همین‌جا** عوض شوند |
| درِ نوشتن | `licence/LicenceGate.kt`؛ هر نویسندهٔ `Repo` پیش از نوشتن می‌پرسد |
| خواندنِ تنظیمات و دفتر، فعال‌سازی | `licence/Licensing.kt` |
| همهٔ جمله‌ها | `licence/LicenceText.kt` |
| کلیدِ عمومی | `licence/LicenceKeys.kt` (همان `public/khayatyar-public.pem`) |
| امضا روی هر سکو | `LicenceCrypto.jvm.kt` (جاوا/اندروید)، `LicenceCrypto.ios.kt` (Security) |
| شناسهٔ دستگاه روی هر سکو | `MachineId.android.kt`، `MachineId.jvm.kt`، `MachineId.ios.kt` |
| نشانِ روی کاغذ | `pdf/Watermark.kt` (و `PdfKit.drawWatermark` برای سندهای قدیمیِ اندروید) |
| نگهبان | `tools/checks/licencecheck.py` — هر نویسندهٔ تازهٔ `Repo` که از در نگذرد، قرمز |
| آزمون‌ها | `app/src/test/.../Licence*Test.kt`، و `:desktop:licenceSmoke` روی خودِ ویندوز و مک |

</div>

---

# KhayatYar licensing (English)

**Owner decision, 2026-10-08:** every workshop needs a licence, Linumic
activates it, fully offline, bound to one device. The STANDALONE or MAIN
device holds the licence; WORKER phones are covered by their main device
and never need their own. The shared protocol is
`~/Projects/Multiplatform/Linumic/licensing/PROTOCOL.md` (LNM1).

## 1. Finding the machine code

Android: Settings → first card "لایسنس" → "لایسنس و کدِ دستگاه".
Windows/macOS: sidebar → "لایسنس". iOS: Settings → "لایسنس". The home
banner (trial/grace/read-only) opens the same screen. The code looks like
`K7Q2-9XMB-4T1D-WP3C`; "کپی" copies it, "فرستادن" shares it (WhatsApp).

**Android caveat:** `ANDROID_ID` is per signing key, so a debug build and
the signed release build show different codes on the same phone. Always
issue from the code shown by the signed build the customer actually runs.

## 2. Issuing (on Linumic's Mac)

```bash
cd ~/Projects/Multiplatform/Linumic/licensing
.venv/bin/python linumic_license.py issue --product khayatyar \
    --customer "Workshop name" --machine K7Q2-9XMB-4T1D-WP3C [--expires 2027-10-08]
.venv/bin/python linumic_license.py verify --product khayatyar "LNM1.…"
```

The tool assigns the next id (`KY-2026-0001`), prints the key, writes
`~/.linumic/licenses/<id>.lnmlic` and appends to `issued.csv`. The private
key never leaves that Mac; back it up encrypted in two places. `--machine "*"`
makes a key for any machine — avoid except for special cases.

## 3. Activation

Paste the key (whitespace and line breaks are ignored) and press
"فعال‌سازی", or open the `.lnmlic` file (Android, Windows, macOS). A valid
key lifts every limit immediately, in place — no reinstall, no re-entry. An
invalid key changes nothing and says why. A key issued for another machine
is accepted into the 30-day machine grace with an explicit warning.

## 4. Behaviour

| State | When | Effect |
|---|---|---|
| LICENSED | valid key, this machine (or `*`), not expired | everything, no watermark; banner from 30 days before expiry |
| GRACE_MACHINE | valid key for a different machine | full use for 30 days from first mismatch, banner |
| GRACE_EXPIRY | past the expiry date | full use for 30 more days, countdown |
| TRIAL | no licence, first 14 days and under 20 new orders | full use, watermark "نسخهٔ آزمایشی — بدون لایسنس" on every printed/exported sheet, banner |
| READ_ONLY | trial over, cap reached, or a grace period over | see below |

The trial starts at the first run of a licensing-aware version (1.9.0+).
The 20-order cap counts only orders created since the trial started, so an
existing workshop upgrading from 1.8.0 gets a full trial. The trial start is
stored both in settings and in the book (audit log), so reinstalling and
restoring a backup does not restart it. Sample-workshop orders count until
"reset data" removes them.

**Read-only blocks** every create/change: orders and stages, sales,
purchases, receipts and payments, payroll, stock, attendance, master data,
customers and measurements, photos, recurring expenses, approving worker
requests, the sample workshop, and "reset data". **Always allowed:**
opening the app, viewing, search, reports, print, PDF, WhatsApp image (with
watermark), the delivery message to a customer, backup and restore, and the
licence screen itself. No data is ever deleted or hidden. A refused action
opens one shared dialog explaining why, what still works, and a button to
the licence screen.

**Clock rollback:** if the clock is more than one day behind the latest
time the app has seen, that latest time is used as "now" and a notice is
shown. It never locks anyone out.

## 5. Worker devices

A WORKER device needs no licence, has no trial and no cap. It follows its
main device: the main reports its licence state with every LAN response;
a trial main means a watermark on the worker too, a read-only main makes
the worker read-only. A read-only main also refuses new worker requests on
its LAN endpoint. An older main (pre-licensing) reports nothing and the
worker is not blocked.

## 6. Where it lives in code

`core/.../licence/` (`Lnm1`, `LicencePolicy` — the numbers live only here,
`LicenceGate`, `Licensing`, `LicenceText`, `LicenceKeys`, platform
`LicenceCrypto.*` and `MachineId.*`), `pdf/Watermark.kt`,
`tools/checks/licencecheck.py`, tests in `app/src/test/.../Licence*Test.kt`,
and `:desktop:licenceSmoke` on the Windows and macOS runners.
