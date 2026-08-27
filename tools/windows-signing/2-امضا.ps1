# ═══════════════════════════════════════════════════════════════════
#  گامِ ۲ — امضای نسخهٔ ویندوز (هر بار که خروجیِ تازه می‌گیرید)
# ═══════════════════════════════════════════════════════════════════
#
#  اول نسخه را بسازید:
#      .\gradlew.bat :desktop:packageMsi
#
#  بعد این را بزنید:
#      .\tools\windows-signing\2-امضا.ps1
#
#  هم فایلِ اجرایی را امضا می‌کند هم نصب‌کنندهٔ MSI را.
#
#  ⚠ روی ویندوز آزموده نشده — من ویندوز و PowerShell در دسترس ندارم.
#    اگر خطا داد، متنش را بفرستید.

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OutDir   = Join-Path $env:USERPROFILE "KhayatYar-امضا"
$pfxPath  = Join-Path $OutDir "KhayatYar-signing.pfx"

if (-not (Test-Path $pfxPath)) {
    throw "گواهی پیدا نشد. اول گامِ ۱ را اجرا کنید:`n  $pfxPath"
}

# ─── پیدا کردنِ signtool ─────────────────────────────────────────
# در Windows SDK است و مسیرش با هر نسخه فرق می‌کند، پس دنبالش
# می‌گردیم و **تازه‌ترین** را برمی‌داریم.
$signtool = Get-ChildItem -Path @(
        "${env:ProgramFiles(x86)}\Windows Kits\10\bin",
        "${env:ProgramFiles}\Windows Kits\10\bin"
    ) -Filter "signtool.exe" -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\(x64|x86)\\' } |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if (-not $signtool) {
    Write-Host ""
    Write-Host "  signtool.exe پیدا نشد." -ForegroundColor Red
    Write-Host ""
    Write-Host "  این ابزار در «Windows SDK» است. نصبش:" -ForegroundColor Yellow
    Write-Host "    Visual Studio Installer → Individual components →" -ForegroundColor Yellow
    Write-Host "    «Windows 11 SDK» را تیک بزنید" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  یا SDK را جدا از سایتِ مایکروسافت بگیرید." -ForegroundColor Yellow
    Write-Host ""
    throw "signtool لازم است."
}
Write-Host "  signtool: $($signtool.FullName)" -ForegroundColor DarkGray

# ─── چه چیزهایی امضا شوند ───────────────────────────────────────
$binaries = Join-Path $repoRoot "desktop\build\compose\binaries\main"
$targets = @()
$targets += Get-ChildItem -Path (Join-Path $binaries "app") -Filter "*.exe" `
             -Recurse -ErrorAction SilentlyContinue
$targets += Get-ChildItem -Path (Join-Path $binaries "msi") -Filter "*.msi" `
             -Recurse -ErrorAction SilentlyContinue

if ($targets.Count -eq 0) {
    throw "چیزی برای امضا پیدا نشد. اول بسازید:`n  .\gradlew.bat :desktop:packageMsi"
}

Write-Host ""
Write-Host "  $($targets.Count) فایل برای امضا:" -ForegroundColor Cyan
$targets | ForEach-Object { Write-Host "    $($_.Name)" -ForegroundColor DarkGray }

$pwd = Read-Host "`n  رمزِ گواهی" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
           [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pwd))

# ─── امضا ────────────────────────────────────────────────────────
# `/tr` مهرِ زمانی است و **اختیاری نیست**. بی آن، روزی که گواهی
# منقضی شود همهٔ نسخه‌های امضاشده هم بی‌اعتبار می‌شوند. با آن،
# ویندوز می‌داند امضا در زمانی زده شده که گواهی معتبر بوده.
$timestampUrl = "http://timestamp.digicert.com"
$failed = @()

foreach ($t in $targets) {
    Write-Host ""
    Write-Host "  امضای $($t.Name) …" -ForegroundColor Cyan
    & $signtool.FullName sign `
        /fd SHA256 /td SHA256 /tr $timestampUrl `
        /f $pfxPath /p $plain `
        $t.FullName
    if ($LASTEXITCODE -ne 0) { $failed += $t.Name }
}

$plain = $null

if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "  ✗ این‌ها امضا نشدند:" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
    throw "امضا ناتمام ماند."
}

# ─── وارسی ───────────────────────────────────────────────────────
# امضا زدن کافی نیست؛ باید **خوانده** هم بشود. `/pa` یعنی با همان
# قاعده‌ای بسنج که خودِ ویندوز سرِ اجرا می‌سنجد.
#
# نکته: چون گواهی خود-امضاست، این وارسی روی کمپیوترِ شما که گواهی را
# در حسابتان دارد قبول می‌شود، ولی روی کمپیوترِ مشتری تا وقتی گواهی
# نصب نشده رد می‌شود. این طبیعی است، نه خرابی.
Write-Host ""
Write-Host "  وارسی…" -ForegroundColor Cyan
foreach ($t in $targets) {
    & $signtool.FullName verify /pa /q $t.FullName
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    ✓ $($t.Name)" -ForegroundColor Green
    } else {
        Write-Host "    ! $($t.Name) — امضا هست ولی زنجیرهٔ اعتماد کامل نیست" -ForegroundColor Yellow
        Write-Host "      (روی کمپیوتری که گواهی نصب نشده طبیعی است)" -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "  ✓ تمام شد." -ForegroundColor Green
Write-Host ""
Write-Host "  فایلی که به مشتری می‌دهید:" -ForegroundColor Cyan
Get-ChildItem -Path (Join-Path $binaries "msi") -Filter "*.msi" -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "    $($_.FullName)" }
Write-Host ""
Write-Host "  همراهش فایلِ .cer و اسکریپتِ نصبِ گواهی را هم بدهید،" -ForegroundColor Yellow
Write-Host "  وگرنه ویندوزِ مشتری باز هم هشدار می‌دهد." -ForegroundColor Yellow
Write-Host ""
