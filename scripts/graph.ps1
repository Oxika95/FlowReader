<#
Refresh the local Graphify code graphs (docs/agent/graph.md) of this repo and ../flow-reader-plugins:
`graphify update` when <repo>\graphify-out\graph.json exists, else a full code-only extract.
Usage: powershell -NoProfile -File scripts\graph.ps1   (verify.ps1 runs it when the app or plugins repo changed)
AST only, GRAPHIFY_BACKEND=none: no AI backend, nothing leaves the machine. Prints a WARNING and exits 0 when
graphify is missing, fails or times out; the graph is a lookup aid and rg confirms.
#>

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repos = @($root, (Join-Path (Split-Path -Parent $root) 'flow-reader-plugins')) | Where-Object { Test-Path $_ }
# `graphify update` can hang on Windows (Graphify #3646). Measured 2026-11-06: full app extract 7 s, update 1-5 s.
$timeoutMs = 120000

$exe = (Get-Command graphify -ErrorAction SilentlyContinue).Source
if (-not $exe) { $exe = Join-Path $env:USERPROFILE '.local\bin\graphify.exe' }
if (-not (Test-Path $exe)) {
    Write-Output 'graph: WARNING graphify is not installed; setup in docs/agent/graph.md'
    exit 0
}

foreach ($repo in $repos) {
    $name = Split-Path -Leaf $repo
    $cmd = if (Test-Path (Join-Path $repo 'graphify-out\graph.json')) { @('update', '.') } else { @('extract', '.', '--code-only') }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = $cmd -join ' '
    $psi.WorkingDirectory = $repo
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $psi.EnvironmentVariables['GRAPHIFY_BACKEND'] = 'none'
    $psi.EnvironmentVariables['PYTHONUTF8'] = '1'
    $p = [System.Diagnostics.Process]::Start($psi)
    $out = $p.StandardOutput.ReadToEndAsync()
    $err = $p.StandardError.ReadToEndAsync()
    if (-not $p.WaitForExit($timeoutMs)) {
        & taskkill.exe /PID $p.Id /T /F | Out-Null
        Write-Output "graph: WARNING $name graphify $($cmd[0]) timed out after $($timeoutMs / 1000) s; the graph may be stale, confirm with rg"
        continue
    }
    if ($p.ExitCode -ne 0) {
        $tail = (($out.Result + $err.Result) -split "`r?`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -Last 5) -join "`n  "
        Write-Output "graph: WARNING $name graphify $($cmd[0]) failed (exit $($p.ExitCode)); the graph may be stale, confirm with rg`n  $tail"
        continue
    }
    Write-Output "graph: $name $($cmd[0]) ok"
}
exit 0
