<#
Verify the working tree: build and unit tests (when code changed), then the repo checks in scripts/agent.
Usage: powershell -NoProfile -File scripts\verify.ps1 [-Full]
  fast (default): gradle only when a changed path isn't docs/scripts-only; rules, duplicate, notes (changed notes only)
                  and completeness checks always; plugins repo tests and version-bump check when it changed;
                  code graphs refreshed by scripts\graph.ps1 (warnings only) when the app or plugins repo changed.
  -Full:          also every note, plugins `npm run check`, API version and host parity, and scripts\smoke.ps1
                  when app code changed.
Changed paths = content map now vs the last passing stamp (or the merge-base with origin/master when none).
Writes <repo>\.git\flow-agent\stamp.json for each repo checked. Exit 0 = pass, 1 = fail, 3 = lock timeout.
#>
param([switch]$Full)

$ErrorActionPreference = 'Continue'
$timer = [System.Diagnostics.Stopwatch]::StartNew()
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$parent = Split-Path -Parent $root
$pluginsRoot = Join-Path $parent 'flow-reader-plugins'
$chatsRoot = Join-Path $parent 'flow-reader-chats'
$agent = Join-Path $root 'scripts\agent'
$mode = if ($Full) { 'full' } else { 'fast' }
$nodeExe = (Get-Command node -ErrorAction Stop).Source
$npmCmd = (Get-Command npm.cmd -ErrorAction SilentlyContinue)
if ($npmCmd) { $npmCmd = $npmCmd.Source }

# Run a native program with stdout and stderr captured separately; stderr text never becomes a PowerShell error.
function Invoke-Native {
    param([string]$File, [string[]]$ArgList = @(), [string]$WorkDir = $root, [hashtable]$EnvVars = @{})
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $File
    $quoted = foreach ($a in $ArgList) { if ($a -match '[\s"]') { '"' + ($a -replace '"', '\"') + '"' } else { $a } }
    $psi.Arguments = ($quoted -join ' ')
    $psi.WorkingDirectory = $WorkDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    foreach ($k in $EnvVars.Keys) { $psi.EnvironmentVariables[$k] = [string]$EnvVars[$k] }
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo = $psi
    [void]$p.Start()
    $outTask = $p.StandardOutput.ReadToEndAsync()
    $errTask = $p.StandardError.ReadToEndAsync()
    $p.WaitForExit()
    $out = $outTask.Result
    $err = $errTask.Result
    return New-Object psobject -Property @{ Code = $p.ExitCode; Out = $out; Err = $err; Text = ($out + $err) }
}

$failures = @()
$ran = @{ app = @(); plugins = @(); chats = @() }

function Add-Failure {
    param([string]$RepoName, [string]$Name, [string]$Text, [int]$Max = 20)
    $lines = @($Text -split "`r?`n" | Where-Object { $_.Trim() -ne '' })
    if ($lines.Count -gt $Max) { $lines = @($lines[0..($Max - 1)]) + @("... $($lines.Count - $Max) more lines") }
    $script:failures += New-Object psobject -Property @{ Repo = $RepoName; Name = $Name; Detail = ($lines -join "`n") }
}

function Get-GradleSummary {
    param([string]$Text)
    $lines = @($Text -split "`r?`n")
    $hits = @($lines | Where-Object { $_ -match '^e: |FAILED|^\* What went wrong|^> |error:|BUILD FAILED|tests completed|Unresolved' })
    if ($hits.Count -gt 0) { return ($hits | Select-Object -First 25) -join "`n" }
    return ($lines | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 20) -join "`n"
}

function Invoke-AgentScript {
    param([string]$RepoName, [string]$Label, [string]$Script, [string[]]$ScriptArgs = @(), [hashtable]$EnvVars = @{})
    $res = Invoke-Native $nodeExe (@((Join-Path $agent $Script)) + $ScriptArgs) $root $EnvVars
    $script:ran[$RepoName] += $Label
    if ($res.Code -ne 0) { Add-Failure $RepoName $Label $res.Text }
}

$exitCode = 0
$mutex = New-Object System.Threading.Mutex($false, 'Global\FlowReaderVerify')
$held = $false
try {
    try { $held = $mutex.WaitOne([TimeSpan]::FromMinutes(10)) }
    catch [System.Threading.AbandonedMutexException] { $held = $true }
    if (-not $held) {
        Write-Output 'verify: another verify run held the lock for more than 10 minutes'
        $exitCode = 3
    } else {
        $repos = @(@{ Name = 'app'; Root = $root })
        if (Test-Path $pluginsRoot) { $repos += @{ Name = 'plugins'; Root = $pluginsRoot } }
        if (Test-Path $chatsRoot) { $repos += @{ Name = 'chats'; Root = $chatsRoot } }

        # Which paths changed since the last passing stamp, per repo.
        $plans = @{}
        foreach ($r in $repos) {
            $res = Invoke-Native $nodeExe @((Join-Path $agent 'verify-state.mjs'), 'plan', $r.Root)
            if ($res.Code -ne 0) { Add-Failure $r.Name 'state' $res.Text; continue }
            $plans[$r.Name] = ($res.Out.Trim() | ConvertFrom-Json)
        }
        $appPlan = $plans['app']
        $appState = Join-Path $root '.git\flow-agent'
        $gradleNote = ''

        # App: build and unit tests.
        $needsBuild = $true
        if ($appPlan) { $needsBuild = [bool]$appPlan.needsBuild }
        if ($needsBuild) {
            $ran.app += 'gradle'
            $gradle = Join-Path $root 'gradlew.bat'
            $gradleArgs = @(':app:compileDebugKotlin', ':app:testDebugUnitTest', '--console=plain')
            $g = Invoke-Native $gradle $gradleArgs $root
            if ($g.Code -ne 0 -and $g.Text -match 'NotImplementedError: Unknown file') {
                $kotlinCache = Join-Path $root 'app\build\kotlin'
                if (Test-Path $kotlinCache) { Remove-Item -Recurse -Force $kotlinCache }
                $g = Invoke-Native $gradle $gradleArgs $root
            }
            Set-Content -Path (Join-Path $appState 'gradle.log') -Value $g.Text -Encoding UTF8
            if ($g.Code -ne 0) { Add-Failure 'app' 'gradle (compile and unit tests; full log in .git\flow-agent\gradle.log)' (Get-GradleSummary $g.Text) 25 }
            $gradleNote = 'gradle ran'
        } elseif ($appPlan -and $appPlan.changed -eq 0) {
            $gradleNote = 'gradle skipped: no changes since last pass'
        } else {
            $gradleNote = 'gradle skipped: docs and scripts only'
        }

        # App: always-on checks.
        $rulesEnv = @{}
        if ($appPlan -and $appPlan.baseKind -ne 'none') { $rulesEnv['FLOW_STAMP_BASE'] = (Join-Path $appState 'base-map.json') }
        Invoke-AgentScript 'app' 'rules-check' 'rules-check.mjs' @('--write-allow') $rulesEnv
        Invoke-AgentScript 'app' 'dup-check' 'dup-check.mjs' @('--write-allow')
        $notesArgs = @('--repo', $root)
        if ($Full) { $notesArgs += '--full' }
        Invoke-AgentScript 'app' 'notes-check' 'notes-check.mjs' $notesArgs
        Invoke-AgentScript 'app' 'seams-check' 'seams-check.mjs' @('--write-allow')

        # Plugins repo.
        $pp = $plans['plugins']
        if ($pp -and $pp.changed -gt 0) {
            if ($pp.needsTests -and $npmCmd) {
                $ran.plugins += 'npm test'
                $t = Invoke-Native $npmCmd @('test') $pluginsRoot
                if ($t.Code -ne 0) {
                    $tail = ($t.Text -split "`r?`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 25) -join "`n"
                    Add-Failure 'plugins' 'npm test' $tail 25
                }
                $ran.plugins += 'version-bump'
                $b = Invoke-Native $nodeExe @((Join-Path $pluginsRoot 'scripts\build-index.mjs'), '--bump-only') $pluginsRoot
                if ($b.Code -ne 0) { Add-Failure 'plugins' 'version-bump' $b.Text }
            }
            $ran.plugins += 'notes-check'
            $n = Invoke-Native $nodeExe (@((Join-Path $agent 'notes-check.mjs'), '--repo', $pluginsRoot) + $(if ($Full) { @('--full') } else { @() })) $root
            if ($n.Code -ne 0) { Add-Failure 'plugins' 'notes-check' $n.Text }
        }

        # Chats repo: notes only.
        $cp = $plans['chats']
        if ($cp -and $cp.changed -gt 0) {
            $ran.chats += 'notes-check'
            $n = Invoke-Native $nodeExe (@((Join-Path $agent 'notes-check.mjs'), '--repo', $chatsRoot) + $(if ($Full) { @('--full') } else { @() })) $root
            if ($n.Code -ne 0) { Add-Failure 'chats' 'notes-check' $n.Text }
        }

        # Code graphs: refreshed when the app or plugins repo changed; warnings only, never a failure.
        if (($appPlan -and $appPlan.changed -gt 0) -or ($pp -and $pp.changed -gt 0)) {
            $gr = Invoke-Native 'powershell.exe' @('-NoProfile', '-File', (Join-Path $root 'scripts\graph.ps1')) $root
            foreach ($line in ($gr.Out -split "`r?`n" | Where-Object { $_.Trim() -ne '' -and $_ -notmatch ' ok$' })) { Write-Output $line }
        }

        # -Full extras.
        if ($Full) {
            if ($pp -and $npmCmd) {
                $ran.plugins += 'npm run check'
                $c = Invoke-Native $npmCmd @('run', 'check') $pluginsRoot
                if ($c.Code -ne 0) { Add-Failure 'plugins' 'npm run check' $c.Text }
            }
            Invoke-AgentScript 'app' 'contract-check' 'contract-check.mjs'
            $smoke = Join-Path $root 'scripts\smoke.ps1'
            if ($needsBuild -and (Test-Path $smoke)) {
                $s = Invoke-Native 'powershell.exe' @('-NoProfile', '-File', $smoke) $root
                if ($s.Code -eq 2) {
                    Write-Output 'verify: WARNING smoke skipped, no emulator running. Start emulator: .\scripts\setup-emulator.ps1'
                    $gradleNote = $gradleNote + '; smoke skipped: no emulator'
                } else {
                    $ran.app += 'smoke'
                    if ($s.Code -ne 0) { Add-Failure 'app' 'smoke' $s.Text }
                }
            } elseif ($needsBuild) {
                $gradleNote = $gradleNote + '; smoke skipped: scripts\smoke.ps1 missing'
            }
        }

        # Stamps.
        foreach ($r in $repos) {
            if ($ran[$r.Name].Count -eq 0 -and -not ($failures | Where-Object { $_.Repo -eq $r.Name })) { continue }
            if (-not $plans.ContainsKey($r.Name)) { continue }
            $mine = @($failures | Where-Object { $_.Repo -eq $r.Name })
            $stateDir = Join-Path $r.Root '.git\flow-agent'
            $failFile = Join-Path $stateDir 'failures.txt'
            $names = @($mine | ForEach-Object { $_.Name })
            Set-Content -Path $failFile -Value $names -Encoding UTF8
            $ok = if ($mine.Count -eq 0) { '1' } else { '0' }
            $st = Invoke-Native $nodeExe @((Join-Path $agent 'verify-state.mjs'), 'stamp', $r.Root, '--ok', $ok, '--mode', $mode, '--ran', ($ran[$r.Name] -join ','), '--failures-file', $failFile)
            if ($st.Code -ne 0) { Write-Output "verify: could not write stamp for $($r.Name): $($st.Text.Trim())" }
        }

        # Report: failures, rule changes, summary.
        foreach ($f in $failures) {
            Write-Output "FAIL [$($f.Repo)] $($f.Name)"
            foreach ($line in ($f.Detail -split "`n")) { Write-Output "  $line" }
        }
        $rulesLines = @()
        foreach ($r in $repos) {
            if ($r.Name -eq 'chats' -or -not $plans.ContainsKey($r.Name)) { continue }
            $rc = Invoke-Native $nodeExe @((Join-Path $agent 'verify-state.mjs'), 'rules-changed', $r.Root)
            if ($rc.Code -eq 0 -and $rc.Out.Trim() -ne '') {
                foreach ($line in ($rc.Out -split "`r?`n" | Where-Object { $_.Trim() -ne '' })) { $rulesLines += "[$($r.Name)] $line" }
            }
        }
        if ($rulesLines.Count -gt 0) {
            Write-Output 'Rules changed:'
            foreach ($line in ($rulesLines | Select-Object -First 30)) { Write-Output "  $line" }
        }
        $verdict = if ($failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
        $detail = if ($failures.Count -eq 0) { $gradleNote } else { "$($failures.Count) failure(s); $gradleNote" }
        $elapsed = $timer.Elapsed
        Write-Output ('verify {0}: {1} ({2}) {3}:{4:00}' -f $mode, $verdict, $detail, [int][math]::Floor($elapsed.TotalMinutes), $elapsed.Seconds)
        if ($failures.Count -gt 0) { $exitCode = 1 }
    }
} finally {
    if ($held) { try { $mutex.ReleaseMutex() } catch { } }
    $mutex.Dispose()
}
exit $exitCode
