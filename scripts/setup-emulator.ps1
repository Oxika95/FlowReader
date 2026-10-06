# Creates (or recreates) the Flow Reader test AVD, boots it windowed, and loads public-domain test books.
# Usage: .\scripts\setup-emulator.ps1 [-Recreate] [-Install] [-Import] [-NoLaunch]
# -Import opens each book in the app via VIEW intent; run once per fresh install (re-running adds duplicates).
param(
    [string]$Name = 'flowreader_api36',
    [string]$Image = 'system-images;android-36.1;google_apis_playstore;x86_64',
    [string]$Device = 'pixel_6',
    [switch]$Recreate,
    [switch]$Install,
    [switch]$Import,
    [switch]$NoLaunch
)
$ErrorActionPreference = 'Stop'

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$avdmanager = "$sdk\cmdline-tools\latest\bin\avdmanager.bat"
$emulator = "$sdk\emulator\emulator.exe"
$adb = "$sdk\platform-tools\adb.exe"
$repo = Split-Path $PSScriptRoot -Parent
$bookDir = Join-Path $repo 'docs\_sample_books'
$deviceBookDir = '/sdcard/Download/flow-samples'
$package = 'com.personal.flowreader'

# Project Gutenberg EPUB3 (with images); public domain in the US.
$books = [ordered]@{
    'alice.epub'        = 11
    'pride.epub'        = 1342
    'frankenstein.epub' = 84
    'holmes.epub'       = 1661
}

$existing = & $emulator -list-avds
if ($Recreate -and $existing -contains $Name) {
    & $avdmanager delete avd -n $Name | Out-Null
    $existing = @()
}
if ($existing -notcontains $Name) {
    'no' | & $avdmanager create avd -n $Name -k $Image -d $Device --force | Out-Null
    $config = "$env:USERPROFILE\.android\avd\$Name.avd\config.ini"
    $overrides = @{
        'hw.ramSize'             = '4096'
        'disk.dataPartition.size' = '8G'
        'hw.keyboard'            = 'yes'
        'showDeviceFrame'        = 'yes'
        'hw.gpu.enabled'         = 'yes'
        'hw.gpu.mode'            = 'auto'
    }
    $lines = Get-Content $config | Where-Object { $overrides.Keys -notcontains ($_ -split '=', 2)[0].Trim() }
    $lines += $overrides.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
    Set-Content $config $lines -Encoding ascii
    "Created AVD $Name"
}

New-Item -ItemType Directory -Force $bookDir | Out-Null
foreach ($b in $books.GetEnumerator()) {
    $path = Join-Path $bookDir $b.Key
    if (-not (Test-Path $path)) {
        "Downloading $($b.Key)"
        Invoke-WebRequest "https://www.gutenberg.org/ebooks/$($b.Value).epub3.images" -OutFile $path -UseBasicParsing
    }
}

if ($NoLaunch) { return }

# adb/emulator write status to stderr, which Windows PowerShell 5 turns into terminating errors under Stop.
$ErrorActionPreference = 'Continue'
& $adb start-server 2>$null | Out-Null

if (-not ((& $adb devices) -match '^emulator-\d+\s+device')) {
    Start-Process $emulator -ArgumentList '-avd', $Name
}
& $adb wait-for-device
while ((& $adb shell getprop sys.boot_completed 2>$null) -ne '1') { Start-Sleep 2 }
while (-not (& $adb shell 'test -w /sdcard/Download && echo ok' 2>$null)) { Start-Sleep 2 }
"Booted"

& $adb shell mkdir -p $deviceBookDir
foreach ($b in $books.Keys) {
    $local = Join-Path $bookDir $b
    $size = (Get-Item $local).Length
    for ($try = 1; $try -le 3; $try++) {
        & $adb push $local "$deviceBookDir/$b" 2>&1 | Out-Null
        if ((& $adb shell stat -c %s "$deviceBookDir/$b" 2>$null) -eq "$size") { break }
        Start-Sleep 2
    }
    & $adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$deviceBookDir/$b" | Out-Null
}
"Books in ${deviceBookDir}:"
& $adb shell ls $deviceBookDir

& $adb shell settings put secure immersive_mode_confirmations confirmed

if ($Install) {
    Push-Location $repo
    try { & .\gradlew.bat :app:installDebug } finally { Pop-Location }
}

if ($Import) {
    $rows = & $adb shell content query --uri content://media/external/file --projection _id:_data
    foreach ($b in $books.Keys) {
        $row = $rows | Where-Object { $_ -match "_id=(\d+), _data=.*/flow-samples/$([regex]::Escape($b))$" } | Select-Object -First 1
        if (-not $row) { "Not indexed yet, skipped: $b"; continue }
        $id = [regex]::Match($row, '_id=(\d+)').Groups[1].Value
        & $adb shell am start -a android.intent.action.VIEW -t application/epub+zip -d "content://media/external/file/$id" --grant-read-uri-permission -n "$package/.MainActivity" 2>&1 | Out-Null
        "Imported $b"
        Start-Sleep 5
    }
    & $adb shell am force-stop $package
    & $adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$package/.MainActivity" 2>&1 | Out-Null
}
