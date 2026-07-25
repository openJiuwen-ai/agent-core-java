# SandboxExample 编译运行脚本
# 所有产物放在当前示例目录或子目录下

$ErrorActionPreference = "Continue"

# 修复中文乱码：保持系统默认编码（中文 Windows 为 GBK/CP936）
# 不要使用 chcp 65001，PowerShell 5 中会导致双重编码产生"锟斤拷"乱码
# javac/java/mvn 在中文 Windows 上默认输出 GBK，与控制台编码一致

$EXAMPLE_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $EXAMPLE_DIR) { $EXAMPLE_DIR = $PWD.Path }
$PROJECT_ROOT = (Resolve-Path (Join-Path $EXAMPLE_DIR "..\..")).Path
$BUILD_DIR = Join-Path $EXAMPLE_DIR "build"
$LOG_DIR = Join-Path $EXAMPLE_DIR "logs"
$ARG_DIR = Join-Path $EXAMPLE_DIR "args"

# 1. 编译主项目（获取依赖 jar）
Write-Host "[1/4] Compiling agent-core-java project..." -ForegroundColor Cyan
Push-Location $PROJECT_ROOT
try {
    mvn clean compile -DskipTests 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Maven compile failed. Run 'mvn compile -DskipTests' manually." -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}

# 2. 构建编译 classpath
Write-Host "[2/4] Building classpath..." -ForegroundColor Cyan
$DEP_DIR = Join-Path $PROJECT_ROOT "target\dependency"

if (-not (Test-Path $DEP_DIR)) {
    Push-Location $PROJECT_ROOT
    mvn dependency:copy-dependencies -DoutputDirectory=target/dependency -DskipTests -q 2>&1 | Out-Null
    Pop-Location
}

$CLASSES_DIR = Join-Path $PROJECT_ROOT "target\classes"
$DEP_JARS = (Get-ChildItem -Path $DEP_DIR -Filter "*.jar").FullName
$CP_COMPILE = @($CLASSES_DIR) + $DEP_JARS -join ";"

New-Item -ItemType Directory -Path $BUILD_DIR -Force | Out-Null
New-Item -ItemType Directory -Path $LOG_DIR -Force | Out-Null
New-Item -ItemType Directory -Path $ARG_DIR -Force | Out-Null

# 3. 编译示例（用 @argfile 避免命令行过长）
Write-Host "[3/4] Compiling SandboxExample..." -ForegroundColor Cyan
$EXAMPLE_SRC = Join-Path $EXAMPLE_DIR "SandboxExample.java"
$UTILS_SRC = Join-Path $PROJECT_ROOT "examples\utils\SharedExampleApiConfigLoader.java"
$COMPILE_ARG = Join-Path $ARG_DIR "compile.args"

@(
    "-source 17",
    "-target 17",
    "-encoding UTF-8",
    "-cp",
    $CP_COMPILE,
    "-d",
    $BUILD_DIR,
    "-proc:none",
    $EXAMPLE_SRC,
    $UTILS_SRC
) | Set-Content $COMPILE_ARG

javac "@$COMPILE_ARG" 2>&1 | Tee-Object -FilePath (Join-Path $LOG_DIR "compile.log")

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Example compilation failed. Check logs/compile.log" -ForegroundColor Red
    exit 1
}
Write-Host "  Compilation successful." -ForegroundColor Green

# 4. 运行示例（用 @argfile 避免命令行过长）
Write-Host "[4/4] Running SandboxExample..." -ForegroundColor Cyan
$CP_RUNTIME = @($BUILD_DIR) + @($CLASSES_DIR) + $DEP_JARS -join ";"
$RUN_ARG = Join-Path $ARG_DIR "run.args"

@(
    "-cp",
    $CP_RUNTIME,
    "examples.sandbox.SandboxExample"
) | Set-Content $RUN_ARG

$API_CONFIG = Join-Path $EXAMPLE_DIR "..\apiconfig.json"
$env:OPENJIUWEN_API_CONFIG = (Resolve-Path $API_CONFIG).Path

Push-Location $EXAMPLE_DIR
try {
    java "@$RUN_ARG" 2>&1 | Tee-Object -FilePath (Join-Path $LOG_DIR "run.log")
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Example run failed. Check logs/run.log" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
    Remove-Item Env:OPENJIUWEN_API_CONFIG
}

Write-Host ""
Write-Host "=== Run Complete ===" -ForegroundColor Green
Write-Host "  Build output: $BUILD_DIR"
Write-Host "  Logs:         $LOG_DIR"
