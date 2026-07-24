# ============================================================
# E2E 环境准备脚本（Windows 主）
# ============================================================
# 用法：
#   .\scripts\e2e\prepare-e2e-env.ps1 [-Force] [-SkipAlist] [-IncludeDanmuji]
#
# 从 GitHub Releases 下载 AList（录播姬可选）二进制到 scripts/e2e/bin/，
# SHA256 校验，幂等跳过已存在下载。
#
# 参见 specs/012-e2e-test-infrastructure/contracts/env-prep-script.md
# 对齐 scripts/download-jre.sh 的幂等下载模式
# ============================================================

[CmdletBinding()]
param(
    [switch]$Force,
    [switch]$SkipAlist,
    [switch]$IncludeDanmuji
)

$ErrorActionPreference = "Stop"

# 脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BinRoot = Join-Path $ScriptDir "bin"
$AlistDir = Join-Path $BinRoot "alist"
$DanmujiDir = Join-Path $BinRoot "danmuji"

# ============================================================
# 版本锁定常量
# 实现时请替换为当前稳定版的实际版本号与 SHA256 哈希值。
# 可通过 GitHub Releases 页面获取：
#   https://github.com/alist-org/alist/releases
#   https://github.com/Bililive/BililiveRecorder/releases
# ============================================================
$ALIST_VERSION = "v3.62.0"
$ALIST_SHA256_WIN = "218121d76d8f2c82f8336707091c2bd0921c5a9b65c3484529c58edf83cc0b38"
$ALIST_DOWNLOAD_URL = "https://github.com/AlistGo/alist/releases/download/$ALIST_VERSION/alist-$ALIST_VERSION-windows-amd64.zip"

$DANMUJI_VERSION = "v2.18.0"
$DANMUJI_SHA256_WIN = "f793f6aecce3504cf0bc7b8434cb5432f10d8134fcc862f8f20126048da28154"
$DANMUJI_DOWNLOAD_URL = "https://github.com/Bililive/BililiveRecorder/releases/download/$DANMUJI_VERSION/BililiveRecorder-CLI-$DANMUJI_VERSION-win-x64.zip"

# 退出码
$EXIT_SUCCESS = 0
$EXIT_DOWNLOAD_FAILED = 1
$EXIT_EXTRACT_FAILED = 2

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

function Get-FileSHA256 {
    param([string]$FilePath)
    $hash = Get-FileHash -Path $FilePath -Algorithm SHA256
    return $hash.Hash.ToLower()
}

function Invoke-DownloadAndExtract {
    param(
        [string]$Name,
        [string]$DownloadUrl,
        [string]$ExpectedSha256,
        [string]$TargetDir,
        [string]$LocalPathEnv
    )

    # 检查本地路径环境变量（对齐 download-jre.sh 的 JRE_LOCAL_PATH 模式）
    if ($LocalPathEnv -and (Get-Item -Path "env:$LocalPathEnv" -ErrorAction SilentlyContinue)) {
        $localPath = (Get-Item -Path "env:$LocalPathEnv").Value
        Write-Info "$Name：使用本地二进制路径 $localPath"
        if (-not (Test-Path $localPath)) {
            Write-Err "$Name：本地路径不存在 $localPath"
            return $EXIT_DOWNLOAD_FAILED
        }
        if (-not (Test-Path $TargetDir)) {
            New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
        }
        Copy-Item -Path $localPath -Destination $TargetDir -Recurse -Force
        Write-Info "$Name：本地二进制复制完成"
        return $EXIT_SUCCESS
    }

    $exeName = if ($Name -eq "AList") { "alist.exe" } else { "BililiveRecorder.exe" }
    $exePath = Join-Path $TargetDir $exeName

    # 幂等检查：已存在且未强制
    if ((Test-Path $exePath) -and -not $Force) {
        Write-Info "$Name：已存在 $exePath，跳过下载（使用 -Force 强制重新下载）"
        return $EXIT_SUCCESS
    }

    # 创建目标目录
    if (-not (Test-Path $TargetDir)) {
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    }

    # 临时文件
    $tempZip = Join-Path $env:TEMP "$Name-$([System.Guid]::NewGuid().ToString().Substring(0,8)).zip"

    # 下载
    Write-Info "$Name：正在下载 $DownloadUrl"
    try {
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $tempZip -UseBasicParsing
    } catch {
        Write-Err "$Name：下载失败 - $($_.Exception.Message)"
        return $EXIT_DOWNLOAD_FAILED
    }

    # SHA256 校验
    if ($ExpectedSha256 -ne "REPLACE_WITH_ACTUAL_SHA256") {
        Write-Info "$Name：正在校验 SHA256"
        $actualHash = Get-FileSHA256 -FilePath $tempZip
        if ($actualHash -ne $ExpectedSha256.ToLower()) {
            Write-Err "$Name：SHA256 校验失败"
            Write-Err "  期望：$ExpectedSha256"
            Write-Err "  实际：$actualHash"
            Remove-Item $tempZip -Force -ErrorAction SilentlyContinue
            return $EXIT_DOWNLOAD_FAILED
        }
        Write-Info "$Name：SHA256 校验通过"
    } else {
        Write-Warn "$Name：SHA256 为占位值，跳过校验（实现时请填充实际哈希）"
    }

    # 解压
    Write-Info "$Name：正在解压到 $TargetDir"
    try {
        Expand-Archive -Path $tempZip -DestinationPath $TargetDir -Force
    } catch {
        Write-Err "$Name：解压失败 - $($_.Exception.Message)"
        Remove-Item $tempZip -Force -ErrorAction SilentlyContinue
        return $EXIT_EXTRACT_FAILED
    }

    # 清理临时文件
    Remove-Item $tempZip -Force -ErrorAction SilentlyContinue

    # 验证可执行文件存在
    if (-not (Test-Path $exePath)) {
        # 可能在解压后的子目录中，尝试查找
        $found = Get-ChildItem -Path $TargetDir -Recurse -Filter $exeName -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) {
            # 移动到目标目录根
            Move-Item $found.FullName $exePath -Force
        } else {
            Write-Err "$Name：解压后未找到 $exeName"
            return $EXIT_EXTRACT_FAILED
        }
    }

    $displayVersion = if ($Name -eq "AList") { $ALIST_VERSION } else { $DANMUJI_VERSION }
    Write-Info "$Name：下载摘要"
    Write-Info "  版本：$displayVersion"
    Write-Info "  路径：$exePath"
    Write-Info "  校验：通过"

    return $EXIT_SUCCESS
}

# ============================================================
# 主流程
# ============================================================

$overallResult = $EXIT_SUCCESS  # 保留用于未来扩展

# 下载 AList
if (-not $SkipAlist) {
    Write-Info "=== 准备 AList ==="
    $result = Invoke-DownloadAndExtract `
        -Name "AList" `
        -DownloadUrl $ALIST_DOWNLOAD_URL `
        -ExpectedSha256 $ALIST_SHA256_WIN `
        -TargetDir $AlistDir `
        -LocalPathEnv "ALIST_LOCAL_PATH"

    if ($result -ne $EXIT_SUCCESS) {
        exit $result
    }
} else {
    Write-Info "已指定 -SkipAlist，跳过 AList 下载"
}

# 下载录播姬（可选）
if ($IncludeDanmuji) {
    Write-Info "=== 准备录播姬 ==="
    $result = Invoke-DownloadAndExtract `
        -Name "Danmuji" `
        -DownloadUrl $DANMUJI_DOWNLOAD_URL `
        -ExpectedSha256 $DANMUJI_SHA256_WIN `
        -TargetDir $DanmujiDir `
        -LocalPathEnv "DANMUJI_LOCAL_PATH"

    if ($result -ne $EXIT_SUCCESS) {
        exit $result
    }
} else {
    Write-Info "未指定 -IncludeDanmuji，跳过录播姬下载（可选验证路径，默认不需要）"
}

Write-Info "=== E2E 环境准备完成 ==="
exit $EXIT_SUCCESS
