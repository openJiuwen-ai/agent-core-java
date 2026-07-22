[CmdletBinding()]
param(
    [ValidateSet("Check", "Start", "Status", "Stop")]
    [string]$Action = "Start",
    [string]$RepositoryRoot = "",
    [string]$ConfigFile = "examples/gitcode_issue_evolver/config/evolver-config.local.json",
    [string]$SecretsFile = "examples/gitcode_issue_evolver/config/evolver-secrets.local.json",
    [string]$ModelConfig = "examples/apiconfig.json",
    [string]$Cloudflared = "cloudflared",
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-RepositoryRoot {
    param([string]$ConfiguredRoot)
    $candidate = if ([string]::IsNullOrWhiteSpace($ConfiguredRoot)) {
        Join-Path $PSScriptRoot "../../../.."
    } else {
        $ConfiguredRoot
    }
    $item = Get-Item -LiteralPath $candidate -Force -ErrorAction Stop
    if (-not $item.PSIsContainer) {
        throw "RepositoryRoot must be a directory"
    }
    $root = $item.FullName.TrimEnd([IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath (Join-Path $root "pom.xml") -PathType Leaf)) {
        throw "RepositoryRoot does not contain pom.xml"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $root "examples/gitcode_issue_evolver") `
            -PathType Container)) {
        throw "RepositoryRoot does not contain examples/gitcode_issue_evolver"
    }
    return $root
}

function Resolve-InputFile {
    param([string]$Root, [string]$ConfiguredPath, [string]$Name)
    if ([string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        throw "$Name path is required"
    }
    $candidate = if ([IO.Path]::IsPathRooted($ConfiguredPath)) {
        $ConfiguredPath
    } else {
        Join-Path $Root $ConfiguredPath
    }
    $item = Get-Item -LiteralPath $candidate -Force -ErrorAction Stop
    if ($item.PSIsContainer -or $item.Length -eq 0) {
        throw "$Name must be a non-empty file"
    }
    return $item.FullName
}

function Resolve-ConfiguredPath {
    param([string]$Root, [string]$ConfiguredPath, [string]$Name)
    if ([string]::IsNullOrWhiteSpace($ConfiguredPath)) {
        throw "$Name is required"
    }
    $candidate = if ([IO.Path]::IsPathRooted($ConfiguredPath)) {
        $ConfiguredPath
    } else {
        Join-Path $Root $ConfiguredPath
    }
    return [IO.Path]::GetFullPath($candidate)
}

function Get-RequiredProperty {
    param([object]$Config, [string]$Name)
    $property = $Config.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "Runtime configuration is missing $Name"
    }
    return $property.Value
}

function Test-IsPlaceholder {
    param([string]$Value)
    return [string]::IsNullOrWhiteSpace($Value) -or $Value.Contains("<") -or $Value.Contains(">")
}

function Test-IsInside {
    param([string]$Candidate, [string]$Parent)
    $normalizedCandidate = [IO.Path]::GetFullPath($Candidate).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $normalizedParent = [IO.Path]::GetFullPath($Parent).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ($normalizedCandidate.Equals($normalizedParent, [StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    $prefix = $normalizedParent + [IO.Path]::DirectorySeparatorChar
    return $normalizedCandidate.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)
}

function Test-Overlaps {
    param([string]$First, [string]$Second)
    return (Test-IsInside $First $Second) -or (Test-IsInside $Second $First)
}

function Test-DirectoryWritable {
    param([string]$Directory)
    $probeRoot = $Directory
    while (-not (Test-Path -LiteralPath $probeRoot)) {
        $parent = Split-Path -Parent $probeRoot
        if ([string]::IsNullOrWhiteSpace($parent) -or $parent -eq $probeRoot) {
            return $false
        }
        $probeRoot = $parent
    }
    $item = Get-Item -LiteralPath $probeRoot -Force -ErrorAction Stop
    if (-not $item.PSIsContainer) {
        return $false
    }
    $probe = Join-Path $item.FullName (".evolver-check-" + [Guid]::NewGuid().ToString("N") + ".tmp")
    try {
        [IO.File]::WriteAllText($probe, "", [Text.UTF8Encoding]::new($false))
        return $true
    } catch {
        return $false
    } finally {
        if (Test-Path -LiteralPath $probe -PathType Leaf) {
            Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
        }
    }
}

function Read-RuntimeConfiguration {
    param([string]$Root, [string]$Path)
    try {
        $config = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        throw "Unable to parse the non-secret runtime configuration"
    }
    $repositoryPattern = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}$"
    $accountPattern = "^[A-Za-z0-9_.-]+$"
    $target = [string](Get-RequiredProperty $config "targetRepository")
    $publish = [string](Get-RequiredProperty $config "publishRepository")
    $baseBranch = [string](Get-RequiredProperty $config "baseBranch")
    if ((Test-IsPlaceholder $target) -or $target -notmatch $repositoryPattern) {
        throw "targetRepository must use owner/name format"
    }
    if ((Test-IsPlaceholder $publish) -or $publish -notmatch $repositoryPattern) {
        throw "publishRepository must be replaced with a valid Fork owner/name"
    }
    if ((Test-IsPlaceholder $baseBranch) -or
            $baseBranch -notmatch "^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$" -or
            $baseBranch.EndsWith("/") -or $baseBranch.EndsWith(".") -or
            $baseBranch.EndsWith(".lock") -or $baseBranch.Contains("..") -or
            $baseBranch.Contains("//") -or $baseBranch -match "(^|/)\.") {
        throw "baseBranch is invalid"
    }
    $assignees = @((Get-RequiredProperty $config "assignees"))
    if ($assignees.Count -eq 0 -or @($assignees | Where-Object {
                (Test-IsPlaceholder ([string]$_)) -or [string]$_ -notmatch $accountPattern
            }).Count -gt 0) {
        throw "assignees must contain at least one configured GitCode username"
    }
    $port = [int](Get-RequiredProperty $config "port")
    if ($port -lt 1 -or $port -gt 65535) {
        throw "port must be between 1 and 65535"
    }
    $workerConcurrency = [int](Get-RequiredProperty $config "workerConcurrency")
    if ($workerConcurrency -ne 1) {
        throw "workerConcurrency must be 1 for the SQLite demo"
    }
    $localRepository = Resolve-ConfiguredPath $Root `
            ([string](Get-RequiredProperty $config "localRepository")) "localRepository"
    if (-not (Test-Path -LiteralPath $localRepository -PathType Container) -or
            -not (Test-Path -LiteralPath (Join-Path $localRepository ".git"))) {
        throw "localRepository must point to a Git repository"
    }
    $codingSkill = Resolve-ConfiguredPath $Root `
            ([string](Get-RequiredProperty $config "codingStandardSkill")) "codingStandardSkill"
    $workerSkill = Resolve-ConfiguredPath $Root `
            ([string](Get-RequiredProperty $config "issueWorkerSkill")) "issueWorkerSkill"
    foreach ($skill in @($codingSkill, $workerSkill)) {
        if (-not (Test-Path -LiteralPath $skill -PathType Container) -or
                -not (Test-Path -LiteralPath (Join-Path $skill "SKILL.md") -PathType Leaf)) {
            throw "Configured Skill directory is unavailable"
        }
    }
    $dataDir = Resolve-ConfiguredPath $Root `
            ([string](Get-RequiredProperty $config "dataDir")) "dataDir"
    $worktreeRoot = Resolve-ConfiguredPath $Root `
            ([string](Get-RequiredProperty $config "worktreeRoot")) "worktreeRoot"
    if (Test-Overlaps $dataDir $localRepository) {
        throw "dataDir must be outside localRepository"
    }
    if (Test-Overlaps $worktreeRoot $localRepository) {
        throw "worktreeRoot must be outside localRepository"
    }
    if ((Test-Overlaps $worktreeRoot $codingSkill) -or
            (Test-Overlaps $worktreeRoot $workerSkill)) {
        throw "worktreeRoot must be outside trusted Skill directories"
    }
    if (-not (Test-DirectoryWritable $dataDir)) {
        throw "dataDir must be creatable or writable"
    }
    if (-not (Test-DirectoryWritable $worktreeRoot)) {
        throw "worktreeRoot must be creatable or writable"
    }
    return [PSCustomObject]@{
        Port = $port
        TargetRepository = $target
        PublishRepository = $publish
        BaseBranch = $baseBranch
    }
}

function Resolve-Executable {
    param([string]$Name, [string]$DisplayName)
    if ([IO.Path]::IsPathRooted($Name) -or $Name.Contains([IO.Path]::DirectorySeparatorChar)) {
        $item = Get-Item -LiteralPath $Name -Force -ErrorAction Stop
        if ($item.PSIsContainer) {
            throw "$DisplayName executable is invalid"
        }
        return $item.FullName
    }
    $command = Get-Command -Name $Name -CommandType Application -ErrorAction Stop |
            Select-Object -First 1
    if ($null -eq $command) {
        throw "$DisplayName is not available on PATH"
    }
    return $command.Source
}

function Invoke-VersionCommand {
    param([string]$Executable, [string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = (& $Executable @Arguments 2>&1 | Out-String)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Assert-Toolchain {
    param([string]$CloudflaredCommand)
    if (-not [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
            [Runtime.InteropServices.OSPlatform]::Windows)) {
        throw "The first version of this Skill supports Windows only"
    }
    $java = Resolve-Executable "java.exe" "java"
    $javac = Resolve-Executable "javac.exe" "javac"
    $maven = Resolve-Executable "mvn.cmd" "Maven"
    $git = Resolve-Executable "git.exe" "Git"
    $cloudflaredExecutable = Resolve-Executable $CloudflaredCommand "cloudflared"
    $javaVersion = Invoke-VersionCommand $java @("-version")
    if ($javaVersion.ExitCode -ne 0 -or $javaVersion.Output -notmatch 'version\s+"(?<major>[0-9]+)') {
        throw "Unable to determine the Java version"
    }
    if ([int]$Matches.major -ne 17) {
        throw "JDK 17 is required"
    }
    $javacVersion = Invoke-VersionCommand $javac @("-version")
    if ($javacVersion.ExitCode -ne 0 -or $javacVersion.Output -notmatch '^javac\s+17(?:\.|\s|$)') {
        throw "javac from JDK 17 is required"
    }
    $mavenVersion = Invoke-VersionCommand $maven @("-version")
    if ($mavenVersion.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($mavenVersion.Output)) {
        throw "Maven is not runnable"
    }
    $gitVersion = Invoke-VersionCommand $git @("--version")
    if ($gitVersion.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($gitVersion.Output)) {
        throw "Git is not runnable"
    }
    $cloudflaredVersion = Invoke-VersionCommand $cloudflaredExecutable @("--version")
    if ($cloudflaredVersion.ExitCode -ne 0 -or
            [string]::IsNullOrWhiteSpace($cloudflaredVersion.Output)) {
        throw "cloudflared is not runnable"
    }
    return $cloudflaredExecutable
}

function Read-ProcessState {
    param([string]$StateFile)
    if (-not (Test-Path -LiteralPath $StateFile -PathType Leaf)) {
        return $null
    }
    try {
        $state = Get-Content -LiteralPath $StateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    } catch {
        throw "The demo process state is invalid"
    }
    $servicePid = 0
    $tunnelPid = 0
    if (-not [int]::TryParse([string]$state.servicePid, [ref]$servicePid) -or $servicePid -le 0 -or
            -not [int]::TryParse([string]$state.tunnelPid, [ref]$tunnelPid) -or $tunnelPid -le 0) {
        throw "The demo process state contains invalid process identifiers"
    }
    $localHealthUrl = [string]$state.localHealthUrl
    $publicUrl = [string]$state.publicUrl
    if ($localHealthUrl -notmatch '^http://127\.0\.0\.1:[0-9]+/health/ready$' -or
            $publicUrl -notmatch '^https://[a-z0-9-]+\.trycloudflare\.com$') {
        throw "The demo process state contains invalid health URLs"
    }
    return [PSCustomObject]@{
        ServicePid = $servicePid
        TunnelPid = $tunnelPid
        LocalHealthUrl = $localHealthUrl
        PublicUrl = $publicUrl
        PublicHealthUrl = "$publicUrl/health/ready"
        WebhookUrl = "$publicUrl/webhooks/gitcode"
    }
}

function Test-ProcessActive {
    param([int]$ProcessId)
    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Test-Health {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Wait-Health {
    param([string]$Url, [int]$Attempts)
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        if (Test-Health $Url) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Test-PortOpen {
    param([int]$Port)
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync("127.0.0.1", $Port)
        return $task.Wait(400) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-StateStatus {
    param([object]$State)
    if ($null -eq $State) {
        return [PSCustomObject]@{ Name = "STOPPED"; ServiceActive = $false; TunnelActive = $false }
    }
    $serviceActive = Test-ProcessActive $State.ServicePid
    $tunnelActive = Test-ProcessActive $State.TunnelPid
    $localReady = $serviceActive -and (Test-Health $State.LocalHealthUrl)
    $publicReady = $tunnelActive -and (Test-Health $State.PublicHealthUrl)
    $name = if ($serviceActive -and $tunnelActive -and $localReady -and $publicReady) {
        "RUNNING"
    } elseif ($serviceActive -or $tunnelActive) {
        "UNHEALTHY"
    } else {
        "STALE"
    }
    return [PSCustomObject]@{
        Name = $name
        ServiceActive = $serviceActive
        TunnelActive = $tunnelActive
    }
}

function New-Result {
    param([string]$RequestedAction, [string]$Status, [object]$State,
          [bool]$Reused, [string]$Message, [object]$RuntimeConfig)
    $localHealth = if ($null -eq $State) { "" } else { $State.LocalHealthUrl }
    $publicHealth = if ($null -eq $State) { "" } else { $State.PublicHealthUrl }
    $webhook = if ($null -eq $State) { "" } else { $State.WebhookUrl }
    $target = if ($null -eq $RuntimeConfig) { "" } else { $RuntimeConfig.TargetRepository }
    $publish = if ($null -eq $RuntimeConfig) { "" } else { $RuntimeConfig.PublishRepository }
    $base = if ($null -eq $RuntimeConfig) { "" } else { $RuntimeConfig.BaseBranch }
    return [ordered]@{
        action = $RequestedAction
        status = $Status
        reused = $Reused
        targetRepository = $target
        publishRepository = $publish
        baseBranch = $base
        localHealthUrl = $localHealth
        publicHealthUrl = $publicHealth
        webhookUrl = $webhook
        triggerLabel = "bug"
        manualWebhookUpdateRequired = $true
        webhookEvents = @("Issue", "Pull Request")
        message = $Message
        manualSteps = @(
            "Configure the returned webhook URL manually for Issue and Pull Request events.",
            "Use the same locally stored Webhook Secret without pasting it into the Agent.",
            "Trigger the demo by explicitly adding the bug label to an open Issue."
        )
    }
}

function Write-Result {
    param([object]$Result)
    $Result | ConvertTo-Json -Depth 4
}

function Invoke-StopScript {
    param([string]$StopScript)
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $StopScript | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to stop the GitCode Issue Evolver demo"
    }
}

try {
    $root = Resolve-RepositoryRoot $RepositoryRoot
    $exampleRoot = Join-Path $root "examples/gitcode_issue_evolver"
    $startScript = Join-Path $exampleRoot "scripts/start-demo.ps1"
    $stopScript = Join-Path $exampleRoot "scripts/stop-demo.ps1"
    $stateFile = Join-Path $exampleRoot ".runtime/processes.json"
    if (-not (Test-Path -LiteralPath $startScript -PathType Leaf) -or
            -not (Test-Path -LiteralPath $stopScript -PathType Leaf)) {
        throw "The GitCode Issue Evolver Example scripts are unavailable"
    }
    $state = Read-ProcessState $stateFile
    $status = Get-StateStatus $state

    if ($Action -eq "Status") {
        Write-Result (New-Result $Action $status.Name $state $false `
                "Process state and health checks completed" $null)
        exit 0
    }

    if ($Action -eq "Stop") {
        if (Test-Path -LiteralPath $stateFile -PathType Leaf) {
            Invoke-StopScript $stopScript
        }
        Write-Result (New-Result $Action "STOPPED" $null $false "Demo processes are stopped" $null)
        exit 0
    }

    if ($Action -eq "Start" -and $status.Name -eq "RUNNING") {
        Write-Result (New-Result $Action "RUNNING" $state $true `
                "The existing healthy demo instance was reused" $null)
        exit 0
    }
    if ($Action -eq "Start" -and $status.Name -eq "UNHEALTHY") {
        throw "Demo processes are active but unhealthy; run Action Stop explicitly before restarting"
    }

    $configPath = Resolve-InputFile $root $ConfigFile "Runtime configuration"
    $secretsPath = Resolve-InputFile $root $SecretsFile "Local secrets"
    $modelPath = Resolve-InputFile $root $ModelConfig "Model configuration"
    $runtimeConfig = Read-RuntimeConfiguration $root $configPath
    $cloudflaredExecutable = Assert-Toolchain $Cloudflared
    $state = Read-ProcessState $stateFile
    $status = Get-StateStatus $state

    if ($Action -eq "Check") {
        if ($status.Name -eq "UNHEALTHY") {
            throw "Demo processes are active but unhealthy; run Action Stop explicitly before restarting"
        }
        if ($status.Name -ne "RUNNING" -and (Test-PortOpen $runtimeConfig.Port)) {
            throw "The configured port is occupied by another process"
        }
        $checkStatus = if ($status.Name -eq "RUNNING") { "RUNNING" } else { "READY" }
        Write-Result (New-Result $Action $checkStatus $state ($status.Name -eq "RUNNING") `
                "Prerequisites and non-secret runtime configuration are valid" $runtimeConfig)
        exit 0
    }

    if ($status.Name -eq "RUNNING") {
        Write-Result (New-Result $Action "RUNNING" $state $true `
                "The existing healthy demo instance was reused" $runtimeConfig)
        exit 0
    }
    if ($status.Name -eq "UNHEALTHY") {
        throw "Demo processes are active but unhealthy; run Action Stop explicitly before restarting"
    }
    if ($status.Name -eq "STALE") {
        Remove-Item -LiteralPath $stateFile -Force
    }
    if (Test-PortOpen $runtimeConfig.Port) {
        throw "The configured port is occupied by another process"
    }

    $arguments = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $startScript,
        "-ConfigFile", $configPath,
        "-SecretsFile", $secretsPath,
        "-ModelConfig", $modelPath,
        "-Cloudflared", $cloudflaredExecutable
    )
    if ($SkipBuild) {
        $arguments += "-SkipBuild"
    }
    & powershell.exe @arguments | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "The demo startup script failed; inspect the ignored .runtime logs"
    }

    $state = Read-ProcessState $stateFile
    if ($null -eq $state) {
        throw "The demo startup script did not create process state"
    }
    if (-not (Wait-Health $state.LocalHealthUrl 15) -or
            -not (Wait-Health $state.PublicHealthUrl 90)) {
        Invoke-StopScript $stopScript
        throw "The demo did not become healthy through both local and public endpoints"
    }
    $status = Get-StateStatus $state
    if ($status.Name -ne "RUNNING") {
        Invoke-StopScript $stopScript
        throw "The demo processes did not remain healthy after startup"
    }
    Write-Result (New-Result $Action "RUNNING" $state $false `
            "Service and Cloudflare Quick Tunnel are ready" $runtimeConfig)
    exit 0
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
