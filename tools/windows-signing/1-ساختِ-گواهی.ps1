# ═══════════════════════════════════════════════════════════════════
#  گامِ ۱ — ساختنِ گواهیِ امضای کد (یک بار، روی کمپیوترِ خودتان)
# ═══════════════════════════════════════════════════════════════════
#
#  این اسکریپت دو فایل می‌سازد:
#
#    KhayatYar-signing.pfx   کلیدِ خصوصی — **هرگز به کسی ندهید**
#    KhayatYar-signing.cer   گواهیِ عمومی — این را به مشتری می‌دهید
#
#  مثلِ همان کی‌استورِ اندروید است: `.pfx` را گم کنید یا لو برود،
#  کارتان خراب می‌شود. یک کاپی در جای امن نگه دارید.
#
#  اجرا: PowerShell را باز کنید و بزنید
#      .\1-ساختِ-گواهی.ps1
#
#  ادمین لازم ندارد — گواهی در حسابِ کاربریِ خودتان ساخته می‌شود.
#
#  ⚠ این اسکریپت روی ویندوز آزموده نشده است. من در محیطی کار می‌کنم
#    که ویندوز و PowerShell ندارد، پس نمی‌توانستم اجرایش کنم. اگر
#    خطایی داد، متنِ خطا را برایم بفرستید تا درستش کنم.

$ErrorActionPreference = "Stop"

# ─── نام و مدت ───────────────────────────────────────────────────
# نامی که مشتری در پنجرهٔ ویندوز می‌بیند. اسمِ واقعیِ کسب‌وکارتان را
# بگذارید؛ همین در «Verified publisher» ظاهر می‌شود.
$Publisher = "Linumic"
$Years     = 5

# ─── کجا ذخیره شود ───────────────────────────────────────────────
# **عمداً بیرون از پوشهٔ پروژه.** اگر کلید داخلِ مخزن ساخته شود، یک
# `git add -A` بی‌حواس کافی است تا به GitHub برود. `.gitignore` هم
# آن را می‌گیرد، ولی به یک قاعده در یک فایل تکیه نمی‌کنیم.
$OutDir = Join-Path $env:USERPROFILE "KhayatYar-امضا"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ($OutDir.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "پوشهٔ خروجی داخلِ مخزن است. کلیدِ خصوصی نباید کنارِ کد بنشیند."
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$pfxPath = Join-Path $OutDir "KhayatYar-signing.pfx"
$cerPath = Join-Path $OutDir "KhayatYar-signing.cer"

if (Test-Path $pfxPath) {
    Write-Host ""
    Write-Host "  از قبل یک گواهی اینجا هست:" -ForegroundColor Yellow
    Write-Host "    $pfxPath"
    Write-Host ""
    Write-Host "  اگر گواهیِ تازه بسازید، نسخه‌هایی که با قبلی امضا شده‌اند" -ForegroundColor Yellow
    Write-Host "  دیگر با گواهیِ تازه جور نمی‌آیند و مشتری باید دوباره" -ForegroundColor Yellow
    Write-Host "  گواهی را نصب کند." -ForegroundColor Yellow
    Write-Host ""
    $answer = Read-Host "  می‌خواهید همان قبلی بماند؟ (بله/نه)"
    if ($answer -ne "نه") { Write-Host "  کاری نشد."; exit 0 }
}

# ─── رمز ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  یک رمز برای کلید بگذارید. جایی یادداشتش کنید —" -ForegroundColor Cyan
Write-Host "  بدونِ آن دیگر نمی‌توانید امضا کنید." -ForegroundColor Cyan
Write-Host ""
$pwd1 = Read-Host "  رمز" -AsSecureString
$pwd2 = Read-Host "  یک بارِ دیگر" -AsSecureString

$p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pwd1))
$p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pwd2))
if ($p1 -ne $p2)      { throw "دو رمز یکی نیستند." }
if ($p1.Length -lt 8) { throw "رمز کوتاه است — دستِ‌کم ۸ نویسه." }

# ─── ساختِ گواهی ─────────────────────────────────────────────────
Write-Host ""
Write-Host "  در حالِ ساختنِ گواهی…" -ForegroundColor Cyan

$cert = New-SelfSignedCertificate `
    -Type CodeSigningCert `
    -Subject "CN=$Publisher, O=$Publisher" `
    -FriendlyName "KhayatYar Code Signing" `
    -CertStoreLocation "Cert:\CurrentUser\My" `
    -KeyExportPolicy Exportable `
    -KeySpec Signature `
    -KeyLength 3072 `
    -HashAlgorithm SHA256 `
    -NotAfter (Get-Date).AddYears($Years)

Export-PfxCertificate -Cert $cert -FilePath $pfxPath -Password $pwd1 | Out-Null
Export-Certificate   -Cert $cert -FilePath $cerPath -Type CERT      | Out-Null

Write-Host ""
Write-Host "  ✓ گواهی ساخته شد." -ForegroundColor Green
Write-Host ""
Write-Host "    کلیدِ خصوصی (پیشِ خودتان بماند):" -ForegroundColor Yellow
Write-Host "      $pfxPath"
Write-Host ""
Write-Host "    گواهیِ عمومی (این را به مشتری می‌دهید):" -ForegroundColor Green
Write-Host "      $cerPath"
Write-Host ""
Write-Host "    اثرِ انگشت (SHA-256):" -ForegroundColor Cyan
Write-Host "      $($cert.Thumbprint)"
Write-Host ""
Write-Host "    تا $((Get-Date).AddYears($Years).ToString('yyyy-MM-dd')) معتبر است." -ForegroundColor Cyan
Write-Host "    نسخه‌هایی که با مهرِ زمانی امضا شوند بعد از این تاریخ هم" -ForegroundColor Cyan
Write-Host "    معتبر می‌مانند — گامِ ۲ این کار را می‌کند." -ForegroundColor Cyan
Write-Host ""
Write-Host "  یک کاپی از فایلِ .pfx در جای امن نگه دارید." -ForegroundColor Yellow
Write-Host "  گم شود، دیگر ساختنی نیست." -ForegroundColor Yellow
Write-Host ""
