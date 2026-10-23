# ═══════════════════════════════════════════════════════════════════
#  گامِ ۲ (بهتر) — ساخت و امضا، با `.exe`ِ امضاشده داخلِ MSI
# ═══════════════════════════════════════════════════════════════════
#
#  به‌جای «.\gradlew.bat :desktop:packageMsi» و بعد «2-امضا.ps1»، فقط این:
#
#      .\tools\windows-signing\3-ساخت-و-امضا.ps1
#
#  چرا: «2-امضا.ps1» فقط MSI را امضا می‌کند و KhayatYar.exe داخلِ آن
#  بی‌امضا می‌ماند، چون MSI پیش از امضا ساخته شده. این اسکریپت Gradle را با
#  KY_SIGN_PFX می‌زند: `signWindowsApp` KhayatYar.exe را در پوشهٔ برنامه امضا
#  می‌کند و `packageSignedMsi` MSI را با jpackage --app-image از همان پوشه
#  می‌سازد (:desktop:packageMsi این کار را نمی‌کند: روی ویندوز jpackage یک
#  .exe تازه می‌سازد و امضا کنار می‌رود). بعد خودِ MSI را امضا می‌کند، و آخر
#  MSI را باز می‌کند و **می‌سنجد** که .exe داخلش با همان گواهی امضا شده و
#  UpgradeCode همان است که باید باشد.
#
#  پیش‌نیاز: JDK 17 کامل (jpackage) و WiX 3 — همان‌ها که packageMsi لازم دارد.
#
#  رمز را می‌پرسد. در CI می‌شود با KY_SIGN_PASSWORD (و KY_SIGN_PFX) پیش‌پر کرد.

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if ($env:KY_SIGN_PFX) {
    $pfxPath = $env:KY_SIGN_PFX
} else {
    $pfxPath = Join-Path (Join-Path $env:USERPROFILE "KhayatYar-امضا") "KhayatYar-signing.pfx"
}
if (-not (Test-Path $pfxPath)) {
    throw "گواهی پیدا نشد. اول گامِ ۱ را اجرا کنید:`n  $pfxPath"
}

# ─── signtool (همان جستجوی 2-امضا.ps1) ───────────────────────────
$signtool = Get-Command signtool.exe -ErrorAction SilentlyContinue |
    Select-Object -First 1 |
    ForEach-Object { Get-Item $_.Source }
if (-not $signtool) {
    $signtool = Get-ChildItem -Path @(
            "${env:ProgramFiles(x86)}\Windows Kits\10\bin",
            "${env:ProgramFiles}\Windows Kits\10\bin"
        ) -Filter "signtool.exe" -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\(x64|x86)\\' } |
        Sort-Object FullName -Descending |
        Select-Object -First 1
}
if (-not $signtool) {
    throw "signtool.exe پیدا نشد (Windows SDK یا بستهٔ NuGetِ Microsoft.Windows.SDK.BuildTools؛ README را ببینید)."
}
Write-Host "  signtool: $($signtool.FullName)" -ForegroundColor DarkGray

# ─── رمز ─────────────────────────────────────────────────────────
if ($env:KY_SIGN_PASSWORD) {
    $plain = $env:KY_SIGN_PASSWORD
} else {
    $certPwd = Read-Host "`n  رمزِ گواهی" -AsSecureString
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
               [Runtime.InteropServices.Marshal]::SecureStringToBSTR($certPwd))
}

# ─── ساخت: Gradle .exe را پیش از بسته‌بندی امضا می‌کند ────────────
$env:KY_SIGN_PFX = $pfxPath
$env:KY_SIGN_PASSWORD = $plain
$env:KY_SIGNTOOL = $signtool.FullName
Push-Location $repoRoot
try {
    & .\gradlew.bat --no-daemon :desktop:packageSignedMsi
    if ($LASTEXITCODE -ne 0) { throw "ساخت شکست خورد (کد $LASTEXITCODE)." }
} finally {
    Pop-Location
    Remove-Item Env:KY_SIGN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:KY_SIGNTOOL -ErrorAction SilentlyContinue
}

$msiDir = Join-Path $repoRoot "desktop\build\compose\binaries\main\msi"
$msi = Get-ChildItem -Path $msiDir -Filter "*.msi" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $msi) { throw "MSI ساخته نشد: $msiDir" }

# ─── امضای خودِ MSI ──────────────────────────────────────────────
Write-Host ""
Write-Host "  امضای $($msi.Name) …" -ForegroundColor Cyan
& $signtool.FullName sign /fd SHA256 /td SHA256 /tr "http://timestamp.digicert.com" `
    /f $pfxPath /p $plain $msi.FullName
if ($LASTEXITCODE -ne 0) { throw "امضای MSI شکست خورد." }
$plain = $null

# ─── وارسی: UpgradeCode ──────────────────────────────────────────
# با UpgradeCodeی دیگر MSIِ تازه روی نصبِ قبلی نمی‌نشیند و یک برنامهٔ دوم
# کنارش نصب می‌شود (و دفترِ حساب دو جا می‌ماند). باید همان `upgradeUuid`ِ
# desktop/build.gradle.kts باشد.
function Get-MsiProperty([string]$path, [string]$name) {
    $inst = New-Object -ComObject WindowsInstaller.Installer
    $db = $inst.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $inst, @($path, 0))
    $view = $db.GetType().InvokeMember("OpenView", "InvokeMethod", $null, $db,
        @("SELECT Value FROM Property WHERE Property='$name'"))
    $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null) | Out-Null
    $rec = $view.GetType().InvokeMember("Fetch", "InvokeMethod", $null, $view, $null)
    if ($rec) { $rec.GetType().InvokeMember("StringData", "GetProperty", $null, $rec, @(1)) }
}
$expectedUpgrade = "{6E7B1F2C-9A54-4B8E-97C6-3D2A5B41E0F7}"
$gotUpgrade = Get-MsiProperty $msi.FullName "UpgradeCode"
$gotVersion = Get-MsiProperty $msi.FullName "ProductVersion"
Write-Host "  MSI: UpgradeCode=$gotUpgrade ProductVersion=$gotVersion" -ForegroundColor DarkGray
if ($gotUpgrade -ne $expectedUpgrade) {
    throw "✗ UpgradeCode همان نیست: $gotUpgrade (انتظار $expectedUpgrade)."
}

# ─── وارسی: .exe داخلِ MSI واقعاً امضا شده؟ ──────────────────────
# MSI را «نصبِ مدیر» می‌کنیم (فقط فایل‌ها را بیرون می‌ریزد، چیزی نصب نمی‌شود)
# و همان KhayatYar.exe را می‌خوانیم که مشتری خواهد داشت.
Write-Host "  وارسیِ .exe داخلِ MSI …" -ForegroundColor Cyan
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("ky-msi-" + [guid]::NewGuid().ToString("N"))
try {
    $p = Start-Process -FilePath "msiexec.exe" -Wait -PassThru -ArgumentList @(
        "/a", "`"$($msi.FullName)`"", "/qn", "TARGETDIR=`"$tmp`"")
    if ($p.ExitCode -ne 0) { throw "msiexec /a کد $($p.ExitCode) داد." }
    $exe = Get-ChildItem -Path $tmp -Recurse -Filter "KhayatYar.exe" | Select-Object -First 1
    if (-not $exe) { throw "KhayatYar.exe داخلِ MSI پیدا نشد." }
    $sigExe = Get-AuthenticodeSignature $exe.FullName
    $sigMsi = Get-AuthenticodeSignature $msi.FullName
    $bad = @("NotSigned", "HashMismatch")
    if ($sigExe.Status -in $bad -or -not $sigExe.SignerCertificate) {
        throw "✗ KhayatYar.exe داخلِ MSI امضا نشده ($($sigExe.Status))."
    }
    if ($sigExe.SignerCertificate.Thumbprint -ne $sigMsi.SignerCertificate.Thumbprint) {
        throw "✗ امضای .exe با امضای MSI یکی نیست."
    }
    if (-not $sigExe.TimeStamperCertificate) { throw "✗ .exe مهرِ زمانی ندارد." }
    Write-Host "    ✓ KhayatYar.exe: $($sigExe.Status)، اثرِ انگشت $($sigExe.SignerCertificate.Thumbprint)، با مهرِ زمانی" -ForegroundColor Green
    Write-Host "    ✓ MSI: $($sigMsi.Status)" -ForegroundColor Green
} finally {
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "  ✓ تمام شد. فایلِ مشتری:" -ForegroundColor Green
Write-Host "    $($msi.FullName)"
Write-Host ""
