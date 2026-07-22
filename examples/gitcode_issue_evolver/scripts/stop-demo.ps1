param()

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot "../../..")).Path
$stateFile = Join-Path $repositoryRoot "examples/gitcode_issue_evolver/.runtime/processes.json"
if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) {
    Write-Host "No GitCode Issue Evolver process state was found."
    return
}
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
foreach ($managedProcess in @(
        @{ Id = $state.tunnelPid; Names = @("cloudflared"); Role = "tunnel" },
        @{ Id = $state.servicePid; Names = @("java", "javaw"); Role = "service" }
    )) {
    if (-not $managedProcess.Id) {
        continue
    }
    $process = Get-Process -Id $managedProcess.Id -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        continue
    }
    if ($managedProcess.Names -notcontains $process.ProcessName) {
        throw "Refusing to stop a process not owned by the $($managedProcess.Role) runtime"
    }
    Stop-Process -Id $process.Id -Force
}
Remove-Item -LiteralPath $stateFile -Force
Write-Host "GitCode Issue Evolver demo processes stopped."
