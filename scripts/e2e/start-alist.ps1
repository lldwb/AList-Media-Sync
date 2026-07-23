# ============================================================
# AList 启动脚本（E2E 测试用）
# ============================================================
# 用法：
#   .\scripts\e2e\start-alist.ps1 -Port <动态端口> [-DataDir "scripts/e2e/data/alist"]
#
# 启动 AList 实例，绑定动态端口，轮询 /ping 就绪，初始化存储挂载。
# 端口由调用方（E2ELifecycleManager）动态分配，禁止固定端口假设（FR-014）。
#
# 参见 specs/012-e2e-test-infrastructure/contracts/env-prep-script.md
# ============================================================

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [int]$Port,

    [string]$DataDir = ""
)

$ErrorActionPreference = "Stop"

# 退出口
$EXIT_SUCCESS = 0
$EXIT_START_FAILED = 1
$EXIT_READY_TIMEOUT = 3

# 脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AlistBinDir = Join-Path $ScriptDir "bin" "alist"
$AlistExe = Join-Path $AlistBinDir "alist.exe"

# 默认数据目录
if ([string]::IsNullOrEmpty($DataDir)) {
    $DataDir = Join-Path $ScriptDir "data" "alist"
}

# PID 文件路径（在脚本目录根）
$PidFile = Join-Path $ScriptDir "alist.pid"
$PortsFile = Join-Path $ScriptDir "data" "ports.json"

# ============================================================
# 辅助函数
# ============================================================

function Write-Info {
    param([string]$Message)
    Write-Host "[信息] $Message" -ForegroundColor Cyan
}

function Write-Err {
    param([string]$Message)
    Write-Host "[错误] $Message" -ForegroundColor Red
}

# 检查 AList 是否已在运行（幂等，FR-009）
function Test-AlistRunning {
    param([int]$CheckPort)
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$CheckPort/ping" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($response.Content -match "pong") {
            return $true
        }
    } catch {
        # 未就绪或未启动
    }
    return $false
}

# ============================================================
# 幂等检查
# ============================================================
if (Test-AlistRunning -CheckPort $Port) {
    Write-Info "AList 已在端口 $Port 上运行，跳过启动（FR-009 幂等）"
    exit $EXIT_SUCCESS
}

# 检查 PID 文件是否存在且进程存活
if (Test-Path $PidFile) {
    $oldPid = Get-Content $PidFile -Raw | ForEach-Object { $_.Trim() }
    if ($oldPid -match '^\d+$') {
        $running = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
        if ($running -and $running.ProcessName -match "alist") {
            Write-Info "AList 已在运行（PID $oldPid），跳过启动"
            exit $EXIT_SUCCESS
        }
    }
    # 残留 PID 文件，删除
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

# ============================================================
# 就绪检查
# ============================================================
# 检查 alist.exe 是否存在
if (-not (Test-Path $AlistExe)) {
    Write-Err "未找到 AList 可执行文件：$AlistExe"
    Write-Err "请先运行 prepare-e2e-env.ps1 下载 AList"
    exit $EXIT_START_FAILED
}

# 创建数据目录与日志目录
$LogDir = Join-Path $ScriptDir "data"
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}
if (-not (Test-Path $DataDir)) {
    New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
    Write-Info "创建数据目录：$DataDir"
}

# ============================================================
# 启动 AList
# ============================================================
Write-Info "正在启动 AList（端口 $Port，数据目录：$DataDir）..."

# 设置环境变量：强制绑定端口与数据目录
$env:ALIST_PORT = $Port.ToString()
$env:ALIST_DATA = $DataDir

# 启动后台进程
$stdoutLog = Join-Path $LogDir "alist-stdout.log"
$stderrLog = Join-Path $LogDir "alist-stderr.log"
$process = Start-Process -FilePath $AlistExe `
    -ArgumentList "server" `
    -WorkingDirectory $AlistBinDir `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog

if (-not $process) {
    Write-Err "AList 启动失败"
    exit $EXIT_START_FAILED
}

# 写入 PID 文件
$process.Id | Out-File -FilePath $PidFile -Encoding ASCII
Write-Info "AList 已启动，PID：$($process.Id)"

# 记录端口信息到 ports.json
$portsDir = Split-Path $PortsFile -Parent
if (-not (Test-Path $portsDir)) {
    New-Item -ItemType Directory -Path $portsDir -Force | Out-Null
}

$portsInfo = @{
    alist = @{
        port = $Port
        pid  = $process.Id
    }
}
$portsInfo | ConvertTo-Json -Compress | Out-File -FilePath $PortsFile -Encoding UTF8

# ============================================================
# 轮询 /ping 就绪
# ============================================================
$MaxWaitSeconds = 30
$PollIntervalSeconds = 2
$Waited = 0

Write-Info "正在等待 AList 就绪（$MaxWaitSeconds 秒超时）..."

while ($Waited -lt $MaxWaitSeconds) {
    Start-Sleep -Seconds $PollIntervalSeconds
    $Waited += $PollIntervalSeconds

    if (Test-AlistRunning -CheckPort $Port) {
        Write-Info "AList 就绪（端口 $Port），耗时 ${Waited} 秒"
        break
    }

    Write-Info "  等待中...（${Waited}s/${MaxWaitSeconds}s）"
}

if ($Waited -ge $MaxWaitSeconds) {
    Write-Err "AList 就绪超时（${MaxWaitSeconds} 秒），请检查端口 $Port 是否正确"
    exit $EXIT_READY_TIMEOUT
}

# ============================================================
# 初始化存储挂载
# ============================================================
Write-Info "正在初始化存储挂载 /e2e-test ..."

# 通过 AList API 创建存储挂载
$storagePayload = @{
    mount_path = "/e2e-test"
    driver     = "Local"
    status     = "work"
    addition   = @{
        root_folder_path = $DataDir
    }
} | ConvertTo-Json

try {
    $authResponse = Invoke-WebRequest -Uri "http://localhost:$Port/api/auth/login" `
        -Method POST `
        -Body (@{username = "admin"; password = "admin"} | ConvertTo-Json) `
        -ContentType "application/json" `
        -UseBasicParsing `
        -ErrorAction SilentlyContinue

    if ($authResponse.StatusCode -eq 200) {
        $authBody = $authResponse.Content | ConvertFrom-Json
        $token = $authBody.token
        $headers = @{
            "Authorization" = "Bearer $token"
        }

        # 创建存储
        $storageResponse = Invoke-WebRequest -Uri "http://localhost:$Port/api/admin/storage/create" `
            -Method POST `
            -Body $storagePayload `
            -ContentType "application/json" `
            -Headers $headers `
            -UseBasicParsing `
            -ErrorAction SilentlyContinue

        if ($storageResponse.StatusCode -eq 200) {
            Write-Info "存储挂载 /e2e-test 初始化成功"
        } else {
            Write-Warn "存储挂载初始化失败（HTTP $($storageResponse.StatusCode)），可手动配置"
        }
    } else {
        Write-Warn "AList 登录失败（HTTP $($authResponse.StatusCode)），跳过存储挂载初始化"
    }
} catch {
    Write-Warn "存储挂载初始化异常：$($_.Exception.Message)，可手动配置"
}

# ============================================================
# 输出就绪摘要
# ============================================================
Write-Info "=== AList 启动完成 ==="
Write-Info "  端口：$Port"
Write-Info "  数据目录：$DataDir"
Write-Info "  PID：$($process.Id)"
Write-Info "  健康检查：http://localhost:${Port}/ping"

exit $EXIT_SUCCESS