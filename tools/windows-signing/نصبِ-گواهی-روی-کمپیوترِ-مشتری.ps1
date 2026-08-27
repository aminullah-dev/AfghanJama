# ═══════════════════════════════════════════════════════════════════
#  نصبِ گواهی روی کمپیوترِ مشتری — یک بار برای هر کمپیوتر
# ═══════════════════════════════════════════════════════════════════
#
#  این را کنارِ فایلِ .cer بگذارید و روی کمپیوترِ مشتری، **با
#  دسترسیِ ادمین**، اجرا کنید:
#
#      راست‌کلیک → Run with PowerShell     (به‌عنوان ادمین)
#
#  بعد از این، نسخهٔ امضاشدهٔ خیاط‌یار روی این کمپیوتر بدونِ هشدار
#  نصب و اجرا می‌شود.
#
#  ⚠ این کار یعنی چه — بخوانید و به مشتری هم بگویید:
#
#    گواهیِ شما به فهرستِ «ناشرانِ مورد اعتمادِ» این کمپیوتر اضافه
#    می‌شود. یعنی این کمپیوتر از این پس **هر برنامه‌ای** را که با
#    همان گواهی امضا شده باشد بی‌چون‌وچرا قبول می‌کند. برای همین است
#    که فایلِ .pfx باید پیشِ خودتان بماند: اگر لو برود، هر بدافزاری
#    که با آن امضا شود روی این کمپیوترها مورد اعتماد باز می‌شود.
#
#  ⚠ روی ویندوز آزموده نشده — من ویندوز در دسترس ندارم.

$ErrorActionPreference = "Stop"

# ─── ادمین لازم است ──────────────────────────────────────────────
$isAdmin = ([Security.Principal.WindowsPrincipal] `
            [Security.Principal.WindowsIdentity]::GetCurrent()
           ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host ""
    Write-Host "  این اسکریپت دسترسیِ ادمین می‌خواهد." -ForegroundColor Red
    Write-Host ""
    Write-Host "  PowerShell را ببندید، روی آیکونش راست‌کلیک کنید و" -ForegroundColor Yellow
    Write-Host "  «Run as administrator» را بزنید، بعد دوباره اجرا کنید." -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# ─── پیدا کردنِ فایلِ گواهی ──────────────────────────────────────
$cer = Get-ChildItem -Path $PSScriptRoot -Filter "*.cer" |
       Select-Object -First 1

if (-not $cer) {
    throw "فایلِ .cer کنارِ این اسکریپت نیست. هر دو را در یک پوشه بگذارید."
}

Write-Host ""
Write-Host "  گواهی: $($cer.Name)" -ForegroundColor Cyan

$certObj = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2 $cer.FullName
Write-Host "  ناشر:   $($certObj.Subject)" -ForegroundColor Cyan
Write-Host "  معتبر تا: $($certObj.NotAfter.ToString('yyyy-MM-dd'))" -ForegroundColor Cyan
Write-Host "  اثرِ انگشت: $($certObj.Thumbprint)" -ForegroundColor DarkGray
Write-Host ""

# اثرِ انگشت را با آنچه گامِ ۱ چاپ کرده مقایسه کنید. اگر یکی نبود،
# این فایل آنی نیست که شما فرستاده‌اید — نصبش نکنید.
$ok = Read-Host "  اثرِ انگشت با آنچه فرستاده‌اید یکی است؟ (بله/نه)"
if ($ok -ne "بله") {
    Write-Host "  کاری نشد." -ForegroundColor Yellow
    exit 0
}

# ─── نصب در دو جا ────────────────────────────────────────────────
# Root          → ویندوز خودِ گواهی را معتبر می‌داند
# TrustedPublisher → و برنامه‌های امضاشده با آن را بی هشدار اجرا می‌کند
#
# هر دو لازم‌اند. فقط یکی کافی نیست.
foreach ($store in @("Root", "TrustedPublisher")) {
    Import-Certificate -FilePath $cer.FullName `
        -CertStoreLocation "Cert:\LocalMachine\$store" | Out-Null
    Write-Host "  ✓ نصب شد در LocalMachine\$store" -ForegroundColor Green
}

Write-Host ""
Write-Host "  ✓ تمام شد. حالا نصب‌کنندهٔ خیاط‌یار را اجرا کنید." -ForegroundColor Green
Write-Host ""
Write-Host "  اگر باز هم پیامِ «Smart App Control» دیدید، آن یکی" -ForegroundColor Yellow
Write-Host "  جداست و با گواهیِ خود-امضا برداشته نمی‌شود. در" -ForegroundColor Yellow
Write-Host "  README همین پوشه توضیح داده شده." -ForegroundColor Yellow
Write-Host ""
