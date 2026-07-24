param(
    [string]$ConfigFile = "examples/gitcode_issue_evolver/config/evolver-config.local.json",
    [string]$SecretsFile = "examples/gitcode_issue_evolver/config/evolver-secrets.local.json",
    [string]$ModelConfig = "examples/apiconfig.json",
    [string]$Cloudflared = "cloudflared",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $scriptRoot "../../..")).Path
Set-Location -LiteralPath $repositoryRoot

function Resolve-RequiredFile {
    param([string]$Value, [string]$Name)
    $candidate = if ([IO.Path]::IsPathRooted($Value)) { $Value } else { Join-Path $repositoryRoot $Value }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "$Name file does not exist: $candidate"
    }
    return (Resolve-Path -LiteralPath $candidate).Path
}

function Quote-ProcessArgument {
    param([string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}

$configPath = Resolve-RequiredFile $ConfigFile "Runtime configuration"
$secretsPath = Resolve-RequiredFile $SecretsFile "Local secrets"
$modelPath = Resolve-RequiredFile $ModelConfig "Model configuration"
$runtimeDir = Join-Path $repositoryRoot "examples/gitcode_issue_evolver/.runtime"
$runtimeDir = [IO.Path]::GetFullPath($runtimeDir)
$exampleRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot "examples/gitcode_issue_evolver"))
if (-not $runtimeDir.StartsWith($exampleRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Resolved runtime directory escaped the Example directory"
}
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$stateFile = Join-Path $runtimeDir "processes.json"
if (Test-Path -LiteralPath $stateFile) {
    $oldState = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $active = @($oldState.servicePid, $oldState.tunnelPid) | Where-Object {
        $_ -and (Get-Process -Id $_ -ErrorAction SilentlyContinue)
    }
    if ($active.Count -gt 0) {
        throw "The demo already has running processes. Run stop-demo.ps1 first."
    }
    Remove-Item -LiteralPath $stateFile -Force
}

$exampleClasses = Join-Path $runtimeDir "classes"
$classPathFile = Join-Path $runtimeDir "compile-classpath.txt"
if (-not $SkipBuild) {
    & mvn.cmd -B -ntp "-Dmaven.test.skip=true" compile
    if ($LASTEXITCODE -ne 0) {
        throw "Maven compilation failed"
    }
    & mvn.cmd -B -ntp -DincludeScope=compile "-Dmdep.outputFile=$classPathFile" dependency:build-classpath
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to build the Example compilation classpath"
    }
    if (Test-Path -LiteralPath $exampleClasses) {
        Remove-Item -LiteralPath $exampleClasses -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $exampleClasses | Out-Null
    $dependencies = (Get-Content -LiteralPath $classPathFile -Raw).Trim()
    $compileClassPath = (Join-Path $repositoryRoot "target/classes") + [IO.Path]::PathSeparator + $dependencies
    $sourceFiles = @(
        Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "examples/gitcode_issue_evolver/src/main/java") -Recurse -Filter "*.java" |
            Select-Object -ExpandProperty FullName
    )
    $sourceFiles += (Join-Path $repositoryRoot "examples/utils/SharedExampleApiConfigLoader.java")
    $javacArguments = @("-encoding", "UTF-8", "-parameters", "-cp", $compileClassPath,
        "-d", $exampleClasses) + $sourceFiles
    & javac @javacArguments
    if ($LASTEXITCODE -ne 0) {
        throw "GitCode Issue Evolver Example compilation failed"
    }
}

if (-not (Test-Path -LiteralPath $exampleClasses -PathType Container) -or
        -not (Test-Path -LiteralPath $classPathFile -PathType Leaf)) {
    throw "Example build output is missing; start without -SkipBuild first"
}
$dependencies = (Get-Content -LiteralPath $classPathFile -Raw).Trim()
$runClassPath = $exampleClasses + [IO.Path]::PathSeparator +
        (Join-Path $repositoryRoot "target/classes") + [IO.Path]::PathSeparator + $dependencies
$serviceOut = Join-Path $runtimeDir "service.out.log"
$serviceErr = Join-Path $runtimeDir "service.err.log"
$javaArguments = @(
    "-cp", (Quote-ProcessArgument $runClassPath),
    "examples.gitcode_issue_evolver.GitCodeIssueEvolverExample",
    "--config", (Quote-ProcessArgument $configPath),
    "--secrets", (Quote-ProcessArgument $secretsPath),
    "--llm-config", (Quote-ProcessArgument $modelPath)
)
$service = Start-Process -FilePath "java" -ArgumentList $javaArguments -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $serviceOut -RedirectStandardError $serviceErr

$runtimeConfig = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$port = if ($runtimeConfig.port) { [int]$runtimeConfig.port } else { 8081 }
$healthUrl = "http://127.0.0.1:$port/health/ready"
$ready = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($service.HasExited) {
        throw "The Java service stopped before readiness; inspect $serviceErr"
    }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
}
if (-not $ready) {
    Stop-Process -Id $service.Id -Force -ErrorAction SilentlyContinue
    throw "The Java service did not become ready; inspect $serviceErr"
}

$tunnelOut = Join-Path $runtimeDir "cloudflared.out.log"
$tunnelErr = Join-Path $runtimeDir "cloudflared.err.log"
$tunnel = Start-Process -FilePath $Cloudflared -ArgumentList @(
    "tunnel", "--url", "http://127.0.0.1:$port", "--protocol", "http2", "--no-autoupdate"
) -PassThru -WindowStyle Hidden -RedirectStandardOutput $tunnelOut -RedirectStandardError $tunnelErr

$publicUrl = $null
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    if ($tunnel.HasExited) {
        Stop-Process -Id $service.Id -Force -ErrorAction SilentlyContinue
        throw "Cloudflared stopped before creating a Quick Tunnel; inspect $tunnelErr"
    }
    $logs = ""
    if (Test-Path -LiteralPath $tunnelOut) {
        $logs += Get-Content -LiteralPath $tunnelOut -Raw
    }
    if (Test-Path -LiteralPath $tunnelErr) {
        $logs += Get-Content -LiteralPath $tunnelErr -Raw
    }
    $match = [regex]::Match($logs, "https://[a-z0-9-]+\.trycloudflare\.com")
    if ($match.Success) {
        $publicUrl = $match.Value
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $publicUrl) {
    Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $service.Id -Force -ErrorAction SilentlyContinue
    throw "Cloudflared did not publish a Quick Tunnel URL; inspect $tunnelErr"
}

@{
    servicePid = $service.Id
    tunnelPid = $tunnel.Id
    localHealthUrl = $healthUrl
    publicUrl = $publicUrl
} | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8

Write-Host "Service ready: $healthUrl"
Write-Host "Webhook URL: $publicUrl/webhooks/gitcode"
Write-Host "Update the GitCode Issue and Pull Request webhook manually."
