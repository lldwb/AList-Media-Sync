# ============================================================
# E2E 环境停止脚本
# ============================================================
# 用法：
#   .\scripts\e2e\stop-e2e-env.ps1 [-CleanData]
#
# 停止 AList 与录播姬（如启动）实例，基于 PID 文件精准停止进程。
# -CleanData 清理数据目录，不删除二进制文件。
#
# 参见 specs/012-e2e-test-infrastructure/contracts/env-prep-script.md
# ============================================================

[CmdletBinding()]
param(
    [switch]$CleanData
)

$ErrorActionPreference = "Stop"

# 退出码
$EXIT_SUCCESS = 0
$EXIT_STOP_FAILED = 1

# 脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFileAlist = Join-Path $ScriptDir "alist.pid"
$PidFileDanmuji = Join-Path $ScriptDir "danmuji.pid"
$PortsFile = Join-Path $ScriptDir "data" "ports.json"
$DataDir = Join-Path $ScriptDir "data"

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

# 停止进程
function Stop-ProcessById {
    param(
        [string]$Name,
        [string]$PidFilePath,
        [string]$ServiceName
    )

    if (Test-Path $PidFilePath) {
        $pidStr = Get-Content $PidFilePath -Raw | ForEach-Object { $_.Trim() }
        if ($pidStr -match '^\d+$') {
            $pid = [int]$pidStr
            $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($proc) {
                Write-Info "正在停止 $Name（PID $pid）..."
                try {
                    Stop-Process -Id $pid -Force -ErrorAction Stop
                    Write-Info "$Name 已停止"
                } catch {
                    Write-Err "停止 $Name 失败：$($_.Exception.Message)"
                    return $EXIT_STOP_FAILED
                }
            } else {
                Write-Warn "$Name（PID $pid）进程不存在，可能已停止"
            }
        }
        Remove-Item $PidFilePath -Force -ErrorAction SilentlyContinue
    } else {
        Write-Warn "$Name PID 文件缺失：$PidFilePath"
        # 尝试按 ports.json 记录的端口查找进程
        return $null  # 表示需按端口查找
    }
    return $EXIT_SUCCESS
}

# 按端口查找进程
function Stop-ProcessByPort {
    param(
        [string]$Name,
        [int]$Port,
        [string]$ProcessName
    )

    if ($Port -le 0) {
        return $EXIT_SUCCESS
    }

    Write-Warn "PID 文件缺失，按端口 $Port 查找 $Name 进程..."
    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
        foreach ($conn in $connections) {
            $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
            if ($proc -and $proc.ProcessName -match $ProcessName) {
                Write-Info "正在停止 $Name（PID $($proc.Id)，端口 $Port）..."
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
                Write-Info "$Name 已停止"
            }
        }
    } catch {
        Write-Warn "按端口查找 $Name 进程失败：$($_.Exception.Message)"
    }
    return $EXIT_SUCCESS
}

# ============================================================
# 主流程
# ============================================================

$hasError = $false

Write-Info "=== 停止 E2E 环境 ==="

# 读取 ports.json（如果存在）
$portsInfo = @{}
if (Test-Path $PortsFile) {
    try {
        $portsInfo = Get-Content $PortsFile -Raw | ConvertFrom-Json -AsHashtable
    } catch {
        Write-Warn "ports.json 解析失败，可能已损坏"
    }
}

# 停止 AList
$alistResult = Stop-ProcessById -Name "AList" -PidFilePath $PidFileAlist -ServiceName "alist"
if ($null -eq $alistResult) {
    # PID 文件缺失，尝试按端口停止
    $alistPort = if ($portsInfo.ContainsKey("alist")) { $portsInfo["alist"].port } else { 0 }
    Stop-ProcessByPort -Name "AList" -Port $alistPort -ProcessName "alist"
}

# 停止录播姬（如果 PID 文件存在，容错处理）
if (Test-Path $PidFileDanmuji) {
    $danmujiResult = Stop-ProcessById -Name "录播姬" -PidFilePath $PidFileDanmuji -ServiceName "BililiveRecorder"
    if ($null -eq $danmujiResult) {
        $danmujiPort = if ($portsInfo.ContainsKey("danmuji")) { $portsInfo["danmuji"].webuiPort } else { 0 }
        Stop-ProcessByPort -Name "录播姬" -Port $danmujiPort -ProcessName "BililiveRecorder"
    }
} else {
    Write-Info "录播姬未启动或 PID 文件不存在，跳过（可选组件，容忍）"
}

# 清理 ports.json
if (Test-Path $PortsFile) {
    Remove-Item $PortsFile -Force -ErrorAction SilentlyContinue
    Write-Info "已清理 ports.json"
}

# ============================================================
# 清理数据目录（-CleanData）
# ============================================================
if ($CleanData) {
    if (Test-Path $DataDir) {
        Write-Info "正在清理数据目录（保留 bin/ 二进制文件）..."
        try {
            Remove-Item $DataDir -Recurse -Force -ErrorAction Stop
            Write-Info "数据目录已清理：$DataDir"
        } catch {
            Write-Err "清理数据目录失败：$($_.Exception.Message)"
            $hasError = $true
        }
    } else {
        Write-Info "数据目录不存在，跳过清理"
    }
} else {
    Write-Info "未指定 -CleanData，跳过数据目录清理"
}

# ============================================================
# 输出停止摘要
# ============================================================
Write-Info "=== E2E 环境停止完成 ==="

if ($hasError) {
    exit $EXIT_STOP_FAILED
}
exit $EXIT_SUCCESS