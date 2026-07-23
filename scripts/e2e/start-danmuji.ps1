# ============================================================
# 录播姬启动脚本（E2E 测试用，可选验证路径）
# ============================================================
# 用法：
#   .\scripts\e2e\start-danmuji.ps1 -WebuiPort <动态端口> [-WorkDir "scripts/e2e/data/danmuji"] [-WebhookUrl "http://localhost:{端口}/api/webhooks/recorder"]
#
# 启动录播姬实例，端口动态分配（FR-014），等待 WebUI 就绪。
# 仅当需验证真实录播姬 webhook 格式时调用，默认 E2E 运行由 WebhookEventReplayer 重放驱动。
#
# 参见 specs/012-e2e-test-infrastructure/contracts/env-prep-script.md
# ============================================================

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [int]$WebuiPort,

    [string]$WorkDir = "",

    [string]$WebhookUrl = ""
)

$ErrorActionPreference = "Stop"

# 退出码
$EXIT_SUCCESS = 0
$EXIT_START_FAILED = 1
$EXIT_READY_TIMEOUT = 3

# 脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DanmujiBinDir = Join-Path $ScriptDir "bin" "danmuji"
$DanmujiExe = Join-Path $DanmujiBinDir "BililiveRecorder.exe"

# 默认工作目录与 webhook URL
if ([string]::IsNullOrEmpty($WorkDir)) {
    $WorkDir = Join-Path $ScriptDir "data" "danmuji"
}

if ([string]::IsNullOrEmpty($WebhookUrl)) {
    $WebhookUrl = "http://localhost:${WebuiPort}/api/webhooks/recorder"
}

# 配置模板路径
$ConfigTemplate = Join-Path $ScriptDir "e2e-config" "danmuji.config.toml"
$ConfigOutput = Join-Path $WorkDir "config.toml"

# PID 文件
$PidFile = Join-Path $ScriptDir "danmuji.pid"
$PortsFile = Join-Path $ScriptDir "data" "ports.json"

# ============================================================
# 辅助函数
# ============================================================

function Write-Info {
    param([string]$Message)
    Write-Host "[信息] $Message" -ForegroundColor Cyan
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[警告] $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Message)
    Write-Host "[错误] $Message" -ForegroundColor Red
}

# ============================================================
# 幂等检查
# ============================================================
# 检查 WebUI 是否已就绪
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:$WebuiPort" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
    if ($resp.StatusCode -eq 200) {
        Write-Info "录播姬 WebUI 已在端口 $WebuiPort 上响应，跳过启动（FR-009 幂等）"
        exit $EXIT_SUCCESS
    }
} catch {
    # 未就绪，继续启动
}

# 检查 PID 文件
if (Test-Path $PidFile) {
    $oldPid = Get-Content $PidFile -Raw | ForEach-Object { $_.Trim() }
    if ($oldPid -match '^\d+$') {
        $running = Get-Process -Id $oldPid -ErrorAction SilentlyContinue
        if ($running -and $running.ProcessName -match "BililiveRecorder") {
            Write-Info "录播姬已在运行（PID $oldPid），跳过启动"
            exit $EXIT_SUCCESS
        }
    }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

# ============================================================
# 日志目录
$LogDir = Join-Path $ScriptDir "data"

# 检查可执行文件
# ============================================================
if (-not (Test-Path $DanmujiExe)) {
    Write-Err "未找到录播姬可执行文件：$DanmujiExe"
    Write-Err "请先运行 prepare-e2e-env.ps1 -IncludeDanmuji 下载录播姬"
    exit $EXIT_START_FAILED
}

# ============================================================
# 生成配置（基于模板注入 webhookUrl 与 WorkDir）
# ============================================================
if (-not (Test-Path $WorkDir)) {
    New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
    Write-Info "创建工作目录：$WorkDir"
}

# 读取模板，注入实际值
if (Test-Path $ConfigTemplate) {
    Write-Info "基于模板生成配置：$ConfigTemplate"
    $configContent = Get-Content $ConfigTemplate -Raw -ErrorAction SilentlyContinue
    if ($configContent) {
        $configContent = $configContent -replace '\$\{WEBUI_PORT\}', $WebuiPort.ToString()
        $configContent = $configContent -replace '\$\{WORK_DIR\}', $WorkDir.Replace('\', '/')
        $configContent = $configContent -replace '\$\{WEBHOOK_URL\}', $WebhookUrl
        $configContent | Out-File -FilePath $ConfigOutput -Encoding UTF8
        Write-Info "配置已写入：$ConfigOutput"
    }
} else {
    Write-Warn "配置模板不存在：$ConfigTemplate，跳过配置生成"
}

# ============================================================
# 启动录播姬
# ============================================================
Write-Info "正在启动录播姬（WebUI 端口：$WebuiPort，工作目录：$WorkDir）..."

$process = Start-Process -FilePath $DanmujiExe `
    -ArgumentList "run" `
    -WorkingDirectory $DanmujiBinDir `
    -NoNewWindow `
    -PassThru `
    -RedirectStandardOutput (Join-Path $LogDir "danmuji-stdout.log") `
    -RedirectStandardError (Join-Path $LogDir "danmuji-stderr.log")

if (-not $process) {
    Write-Err "录播姬启动失败"
    exit $EXIT_START_FAILED
}

# 写入 PID 文件
$process.Id | Out-File -FilePath $PidFile -Encoding ASCII
Write-Info "录播姬已启动，PID：$($process.Id)"

# 记录端口信息到 ports.json
$portsDir = Split-Path $PortsFile -Parent
if (-not (Test-Path $portsDir)) {
    New-Item -ItemType Directory -Path $portsDir -Force | Out-Null
}

# 读取现有 ports.json 或新建
$portsInfo = @{}
if (Test-Path $PortsFile) {
    try {
        $portsInfo = Get-Content $PortsFile -Raw | ConvertFrom-Json -AsHashtable
    } catch {
        $portsInfo = @{}
    }
}

$portsInfo["danmuji"] = @{
    webuiPort = $WebuiPort
    pid       = $process.Id
    webhookUrl = $WebhookUrl
}
$portsInfo | ConvertTo-Json -Compress | Out-File -FilePath $PortsFile -Encoding UTF8

# ============================================================
# 轮询 WebUI 就绪
# ============================================================
$MaxWaitSeconds = 30
$PollIntervalSeconds = 2
$Waited = 0

Write-Info "正在等待录播姬 WebUI 就绪（$MaxWaitSeconds 秒超时）..."

while ($Waited -lt $MaxWaitSeconds) {
    Start-Sleep -Seconds $PollIntervalSeconds
    $Waited += $PollIntervalSeconds

    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:$WebuiPort" -TimeoutSec 3 -UseBasicParsing -ErrorAction SilentlyContinue
        if ($resp.StatusCode -eq 200) {
            Write-Info "录播姬 WebUI 就绪（端口 $WebuiPort），耗时 ${Waited} 秒"
            break
        }
    } catch {
        # 继续等待
    }

    Write-Info "  等待中...（${Waited}s/${MaxWaitSeconds}s）"
}

if ($Waited -ge $MaxWaitSeconds) {
    Write-Err "录播姬 WebUI 就绪超时（${MaxWaitSeconds} 秒）"
    exit $EXIT_READY_TIMEOUT
}

# ============================================================
# 输出就绪摘要
# ============================================================
Write-Info "=== 录播姬启动完成 ==="
Write-Info "  WebUI 端口：$WebuiPort"
Write-Info "  工作目录：$WorkDir"
Write-Info "  Webhook URL：$WebhookUrl"
Write-Info "  PID：$($process.Id)"
Write-Info "  WebUI 地址：http://localhost:${WebuiPort}"

exit $EXIT_SUCCESS