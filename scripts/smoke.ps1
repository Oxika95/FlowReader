<#
Smoke check on the first running emulator: install the debug build, open each debug route, and fail on a crash.
Usage: powershell -NoProfile -File scripts\smoke.ps1 [-SkipInstall]
Routes are the `flow_debug_route` intent extra values that MainActivity accepts in debug builds
(app/src/main/java/com/personal/flowreader/ui/debug/DebugRoute.kt).
Per route: start MainActivity with the extra, wait 2 seconds, then check the crash log buffer for
FATAL EXCEPTION and check the app process is still alive. No screenshots.
Only emulator-* devices are used; a physical device is never touched.
Exit 0 = every route passed, 1 = a route failed or the install/manifest check failed, 2 = no emulator running.
#>
param([switch]$SkipInstall)

$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$package = 'com.personal.flowreader'
$routes = @(
    'library',
    'settings/about', 'settings/audio', 'settings/filters', 'settings/import', 'settings/layout', 'settings/plugins',
    'reader/first',
    'plugin/royalroad', 'plugin/patreon'
)

$adb = $null
foreach ($candidate in @(
        $(if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' }),
        $(if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe' }),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'))) {
    if ($candidate -and (Test-Path $candidate)) { $adb = $candidate; break }
}
if (-not $adb) {
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { $adb = $cmd.Source }
}
if (-not $adb) { Write-Output 'smoke: adb not found (set ANDROID_HOME)'; exit 1 }

# adb writes status lines to stderr; capture both streams as text and never let them become PowerShell errors.
function Invoke-Adb {
    param([string[]]$AdbArgs, [string]$Serial)
    $all = if ($Serial) { @('-s', $Serial) + $AdbArgs } else { $AdbArgs }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $adb @all 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference = $prev }
    return (($out -join "`n").Trim())
}

& $adb start-server 2>&1 | Out-Null
$serial = $null
foreach ($line in ((Invoke-Adb @('devices')) -split "`n")) {
    if ($line -match '^(emulator-\d+)\s+device\s*$') { $serial = $Matches[1]; break }
}
if (-not $serial) {
    Write-Output 'smoke: no emulator running. Start emulator: .\scripts\setup-emulator.ps1'
    exit 2
}

# The activity name comes from the manifest so a rename cannot leave the script starting a stale class.
[xml]$manifest = Get-Content (Join-Path $root 'app\src\main\AndroidManifest.xml') -Raw
$ns = New-Object System.Xml.XmlNamespaceManager($manifest.NameTable)
$ns.AddNamespace('android', 'http://schemas.android.com/apk/res/android')
$launcher = $manifest.SelectSingleNode(
    "//activity[intent-filter/action[@android:name='android.intent.action.MAIN'] and intent-filter/category[@android:name='android.intent.category.LAUNCHER']]", $ns)
if (-not $launcher) { Write-Output 'smoke: no launcher activity in AndroidManifest.xml'; exit 1 }
$activity = $launcher.GetAttribute('name', 'http://schemas.android.com/apk/res/android')
if ($activity.StartsWith('.')) { $activity = $package + $activity }
$component = "$package/$activity"
if ($activity -ne "$package.MainActivity") { Write-Output "smoke: note: launcher activity is $activity" }
Write-Output "smoke: device $serial, activity $component"

if (-not $SkipInstall) {
    $env:ANDROID_SERIAL = $serial
    $install = & (Join-Path $root 'gradlew.bat') ':app:installDebug' '--console=plain' 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) {
        Write-Output 'smoke: FAIL installDebug'
        $install | Where-Object { $_ -match '^e: |What went wrong|^> |FAILED|INSTALL_' } | Select-Object -First 15 | ForEach-Object { Write-Output "  $_" }
        exit 1
    }
}

[void](Invoke-Adb @('logcat', '-c') $serial)
[void](Invoke-Adb @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') $serial)
[void](Invoke-Adb @('shell', 'am', 'force-stop', $package) $serial)

$failed = 0
$timer = [System.Diagnostics.Stopwatch]::StartNew()
foreach ($route in $routes) {
    [void](Invoke-Adb @('logcat', '-b', 'crash', '-c') $serial)
    $start = Invoke-Adb @('shell', 'am', 'start', '-n', $component, '--es', 'flow_debug_route', $route) $serial
    Start-Sleep -Seconds 2
    $crash = Invoke-Adb @('logcat', '-d', '-b', 'crash') $serial
    $pid1 = Invoke-Adb @('shell', 'pidof', $package) $serial
    $reason = $null
    if ($start -match 'Error|Exception') { $reason = "am start failed: $(($start -split "`n" | Select-Object -First 2) -join ' ')" }
    elseif ($crash -match 'FATAL EXCEPTION') {
        $first = ($crash -split "`n" | Where-Object { $_ -match 'FATAL EXCEPTION|Exception|Error' } | Select-Object -First 3) -join ' | '
        $reason = "FATAL EXCEPTION: $first"
    } elseif (-not ($pid1 -match '^\d+')) { $reason = 'app process not running' }
    if ($reason) {
        $failed++
        Write-Output "FAIL $route  $reason"
    } else {
        Write-Output "PASS $route"
    }
}
$total = $routes.Count
$secs = [int]$timer.Elapsed.TotalSeconds
if ($failed -eq 0) {
    Write-Output "smoke PASS: $total/$total routes (${secs}s)"
    exit 0
}
Write-Output "smoke FAIL: $failed of $total routes failed (${secs}s)"
exit 1
