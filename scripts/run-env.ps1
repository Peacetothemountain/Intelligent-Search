# Readies this checkout for an unattended run, and says plainly what is still missing. Safe to run any number of times.
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-env.ps1
# Exit 0 means builds, signing and GitHub all work. Device tools are reported separately, since a run can work without them.
# -Emulator also boots IS_Pixel_XL on its own port and waits for it; then device_open emulator-5580.
param([switch]$Emulator)
$ErrorActionPreference = 'Stop'
$blocked = 0
function Say($state, $text) { Write-Output ("{0,-7} {1}" -f $state, $text) }
function Block($text) { Say 'BLOCKED' $text; $script:blocked++ }

$root = (git rev-parse --show-toplevel).Replace('/', '\')
$ownerCheckout = ((git worktree list --porcelain | Select-Object -First 1) -replace '^worktree ', '').Replace('/', '\')

# JDK. Updates change the versioned install path, and apps left running keep the deleted one. This path never changes:
# it is a link that gets moved to the current JDK, so a JDK update can't break anything that holds it.
$stableJdk = Join-Path $env:LOCALAPPDATA 'IntelligentSearch\jdk17'
$jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
    Sort-Object { [version](($_.Name -replace '^jdk-', '') -replace '-hotspot$', '') } -Descending | Select-Object -First 1
if (-not $jdk) {
    Block 'No JDK 17 under C:\Program Files\Microsoft.'
} else {
    $link = Get-Item $stableJdk -Force -ErrorAction SilentlyContinue
    if (-not $link -or ($link.Target | Select-Object -First 1) -ne $jdk.FullName) {
        if ($link) { & cmd /c rmdir "$stableJdk" }   # removes the link only, never the JDK it points to
        New-Item -ItemType Directory -Force (Split-Path $stableJdk) | Out-Null
        New-Item -ItemType Junction -Path $stableJdk -Target $jdk.FullName | Out-Null
    }
    if ([Environment]::GetEnvironmentVariable('JAVA_HOME', 'User') -ne $stableJdk) {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $stableJdk, 'User')
    }
    Say 'OK' "JDK: $stableJdk -> $($jdk.Name)"
}

# Signing. The owner's local.properties names the keystore by a relative path, so in a worktree the release build came
# out unsigned without saying so, and an unsigned APK can't be installed. The keystore stays in the owner's checkout.
$latin1 = [Text.Encoding]::GetEncoding(28591)
$props = Join-Path $root 'local.properties'
$ownerProps = Join-Path $ownerCheckout 'local.properties'
$keystore = Join-Path $ownerCheckout 'release.keystore'
if ($root -ne $ownerCheckout) {
    if (-not (Test-Path $props) -and (Test-Path $ownerProps)) { Copy-Item $ownerProps $props }
    if (Test-Path $props) {
        $lines = [IO.File]::ReadAllLines($props, $latin1)
        $wanted = 'RELEASE_STORE_FILE=' + $keystore.Replace('\', '/')
        $updated = @($lines | ForEach-Object { if ($_ -like 'RELEASE_STORE_FILE=*') { $wanted } else { $_ } })
        if (($updated -join "`n") -cne ($lines -join "`n")) { [IO.File]::WriteAllLines($props, [string[]]$updated, $latin1) }
    }
}
if (-not (Test-Path $props)) { Block "No local.properties here or in $ownerCheckout." }
elseif (-not (Test-Path $keystore)) { Block "No keystore at $keystore, so release builds would be unsigned." }
else { Say 'OK' 'Release signing: this checkout signs with the owner''s keystore' }

# Android SDK and the emulator.
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
foreach ($tool in 'platform-tools\adb.exe', 'emulator\emulator.exe') {
    if (-not (Test-Path (Join-Path $sdk $tool))) { Block "Missing $tool in $sdk." }
}
if (-not (Test-Path (Join-Path $env:USERPROFILE '.android\avd\IS_Pixel_XL.ini'))) { Block 'The IS_Pixel_XL emulator is gone: see docs/DEVICE_TESTING.md.' }

# GitHub.
& cmd /c "gh auth status >nul 2>nul"
if ($LASTEXITCODE -ne 0) { Block 'gh is not signed in.' } else { Say 'OK' 'GitHub: signed in' }

# T3 Code hands its own environment to everything it starts, so one started before a JDK update holds the deleted path.
if ($env:JAVA_HOME -and -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Say 'WAITING' "Device tools: T3 Code still holds a deleted JAVA_HOME ($env:JAVA_HOME). Restart T3 Code once to clear it."
    Say 'INFO' "For Gradle in this run, set JAVA_HOME to $stableJdk."
} else {
    Say 'OK' 'Device tools: JAVA_HOME is valid'
}

# Always this port: device_open picks the lowest free one itself, and old device sessions on this PC still claim 5554, 5558
# and 5560, which makes everything after device_open fail with DEVICE_IN_USE.
if ($Emulator -and $blocked -eq 0) {
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    function Booted { $ErrorActionPreference = 'Continue'; (& $adb -s emulator-5580 shell getprop sys.boot_completed 2>$null) -match '1' }
    if (-not (Booted)) {
        Start-Process (Join-Path $sdk 'emulator\emulator.exe') -ArgumentList '-avd', 'IS_Pixel_XL', '-port', '5580', '-no-boot-anim', '-no-audio', '-gpu', 'host' -WindowStyle Minimized
        $deadline = (Get-Date).AddSeconds(240)
        while (-not (Booted) -and (Get-Date) -lt $deadline) { Start-Sleep 4 }
    }
    if (Booted) { Say 'OK' 'Emulator: IS_Pixel_XL is up as emulator-5580' } else { Block 'The IS_Pixel_XL emulator did not finish booting in 4 minutes.' }
}

if ($blocked -gt 0) { exit 1 }
