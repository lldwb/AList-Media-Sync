#!/usr/bin/env pwsh
# 创建新功能
[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$AllowExistingBranch,
    [switch]$DryRun,
    [string]$ShortName,
    [Parameter()]
    [long]$Number = 0,
    [switch]$Timestamp,
    [switch]$Help,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$FeatureDescription
)
$ErrorActionPreference = 'Stop'

# 如果请求帮助则显示
if ($Help) {
    Write-Host "用法：./create-new-feature.ps1 [-Json] [-DryRun] [-AllowExistingBranch] [-ShortName <name>] [-Number N] [-Timestamp] <feature description>"
    Write-Host ""
    Write-Host "选项："
    Write-Host "  -Json               以 JSON 格式输出"
    Write-Host "  -DryRun             计算功能名称和路径，不创建目录或文件"
    Write-Host "  -AllowExistingBranch  如果已存在，重用现有功能目录"
    Write-Host "  -ShortName <name>   提供自定义短名称（2-4 个词）用于功能"
    Write-Host "  -Number N           手动指定分支编号（覆盖自动检测）"
    Write-Host "  -Timestamp          使用时间戳前缀（YYYYMMDD-HHMMSS）而非顺序编号"
    Write-Host "  -Help               显示此帮助信息"
    Write-Host ""
    Write-Host "示例："
    Write-Host "  ./create-new-feature.ps1 '添加用户认证系统' -ShortName 'user-auth'"
    Write-Host "  ./create-new-feature.ps1 '为 API 实现 OAuth2 集成'"
    Write-Host "  ./create-new-feature.ps1 -Timestamp -ShortName 'user-auth' '添加用户认证'"
    exit 0
}

# 检查是否提供了功能描述
if (-not $FeatureDescription -or $FeatureDescription.Count -eq 0) {
    Write-Error "用法：./create-new-feature.ps1 [-Json] [-DryRun] [-AllowExistingBranch] [-ShortName <name>] [-Number N] [-Timestamp] <feature description>"
    exit 1
}

$featureDesc = ($FeatureDescription -join ' ').Trim()

# 验证描述在修剪后不为空（例如，用户只传递了空白字符）
if ([string]::IsNullOrWhiteSpace($featureDesc)) {
    Write-Error "错误：功能描述不能为空或仅包含空白字符"
    exit 1
}

function Get-HighestNumberFromSpecs {
    param([string]$SpecsDir)

    [long]$highest = 0
    if (Test-Path $SpecsDir) {
        Get-ChildItem -Path $SpecsDir -Directory | ForEach-Object {
            # 匹配顺序前缀（>=3 位数字），但跳过时间戳目录。
            if ($_.Name -match '^(\d{3,})-' -and $_.Name -notmatch '^\d{8}-\d{6}-') {
                [long]$num = 0
                if ([long]::TryParse($matches[1], [ref]$num) -and $num -gt $highest) {
                    $highest = $num
                }
            }
        }
    }
    return $highest
}

function ConvertTo-CleanBranchName {
    param([string]$Name)

    return $Name.ToLower() -replace '[^a-z0-9]', '-' -replace '-{2,}', '-' -replace '^-', '' -replace '-$', ''
}
# 加载通用函数（包括 Get-RepoRoot 和 Resolve-Template）
. "$PSScriptRoot/common.ps1"

# 使用 common.ps1 中优先使用 .specify 的函数
$repoRoot = Get-RepoRoot

Set-Location $repoRoot

$specsDir = Join-Path $repoRoot 'specs'
if (-not $DryRun) {
    New-Item -ItemType Directory -Path $specsDir -Force | Out-Null
}

# 生成带有停用词过滤和长度过滤的分支名称的函数
function Get-BranchName {
    param([string]$Description)

    # 常见停用词列表
    $stopWords = @(
        'i', 'a', 'an', 'the', 'to', 'for', 'of', 'in', 'on', 'at', 'by', 'with', 'from',
        'is', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had',
        'do', 'does', 'did', 'will', 'would', 'should', 'could', 'can', 'may', 'might', 'must', 'shall',
        'this', 'that', 'these', 'those', 'my', 'your', 'our', 'their',
        'want', 'need', 'add', 'get', 'set'
    )

    # 转换为小写并提取单词（仅字母数字）
    $cleanName = $Description.ToLower() -replace '[^a-z0-9\s]', ' '
    $words = $cleanName -split '\s+' | Where-Object { $_ }

    # 过滤单词：移除停用词和短于 3 个字符的单词（除非在原始文本中是大写缩写）
    $meaningfulWords = @()
    foreach ($word in $words) {
        # 跳过停用词
        if ($stopWords -contains $word) { continue }

        # 保留长度 >= 3 的词或在原始文本中作为大写出现的词（可能是缩写）
        if ($word.Length -ge 3) {
            $meaningfulWords += $word
        } elseif ($Description -match "\b$($word.ToUpper())\b") {
            # 如果短词在原始文本中作为大写出现（可能是缩写），则保留
            $meaningfulWords += $word
        }
    }

    # 如果有有意义的词，取前 3-4 个
    if ($meaningfulWords.Count -gt 0) {
        $maxWords = if ($meaningfulWords.Count -eq 4) { 4 } else { 3 }
        $result = ($meaningfulWords | Select-Object -First $maxWords) -join '-'
        return $result
    } else {
        # 如果未找到有意义的词，回退到原始逻辑
        $result = ConvertTo-CleanBranchName -Name $Description
        $fallbackWords = ($result -split '-') | Where-Object { $_ } | Select-Object -First 3
        return [string]::Join('-', $fallbackWords)
    }
}

# 生成分支名称
if ($ShortName) {
    # 使用提供的短名称，仅清理它
    $branchSuffix = ConvertTo-CleanBranchName -Name $ShortName
} else {
    # 从描述中通过智能过滤生成
    $branchSuffix = Get-BranchName -Description $featureDesc
}

# 如果同时指定了 -Number 和 -Timestamp 则警告
if ($Timestamp -and $Number -ne 0) {
    Write-Warning "[specify] 警告：使用 -Timestamp 时 -Number 被忽略"
    $Number = 0
}

# 确定分支前缀
if ($Timestamp) {
    $featureNum = Get-Date -Format 'yyyyMMdd-HHmmss'
    $branchName = "$featureNum-$branchSuffix"
} else {
    # 从现有功能目录确定分支编号
    if ($Number -eq 0) {
        $Number = (Get-HighestNumberFromSpecs -SpecsDir $specsDir) + 1
    }

    $featureNum = ('{0:000}' -f $Number)
    $branchName = "$featureNum-$branchSuffix"
}

# GitHub 对分支名称强制执行 244 字节的限制
# 验证并在必要时截断
$maxBranchLength = 244
if ($branchName.Length -gt $maxBranchLength) {
    # 计算需要从后缀中修剪的长度
    # 计入前缀长度：时间戳（15）+ 连字符（1）= 16，或顺序（3）+ 连字符（1）= 4
    $prefixLength = $featureNum.Length + 1
    $maxSuffixLength = $maxBranchLength - $prefixLength

    # 截断后缀
    $truncatedSuffix = $branchSuffix.Substring(0, [Math]::Min($branchSuffix.Length, $maxSuffixLength))
    # 如果截断创建了尾随连字符则移除
    $truncatedSuffix = $truncatedSuffix -replace '-$', ''

    $originalBranchName = $branchName
    $branchName = "$featureNum-$truncatedSuffix"

    Write-Warning "[specify] 分支名称超过 GitHub 的 244 字节限制"
    Write-Warning "[specify] 原始：$originalBranchName（$($originalBranchName.Length) 字节）"
    Write-Warning "[specify] 截断为：$branchName（$($branchName.Length) 字节）"
}

$featureDir = Join-Path $specsDir $branchName
$specFile = Join-Path $featureDir 'spec.md'

if (-not $DryRun) {
    if ((Test-Path -LiteralPath $featureDir -PathType Container) -and -not $AllowExistingBranch) {
        if ($Timestamp) {
            Write-Error "错误：功能目录 '$featureDir' 已存在。重新运行以获得新的时间戳或使用不同的 -ShortName。"
        } else {
            Write-Error "错误：功能目录 '$featureDir' 已存在。请使用不同的功能名称或使用 -Number 指定不同的编号。"
        }
        exit 1
    }

    New-Item -ItemType Directory -Path $featureDir -Force | Out-Null

    if (-not (Test-Path -PathType Leaf $specFile)) {
        $template = Resolve-Template -TemplateName 'spec-template' -RepoRoot $repoRoot
        if ($template -and (Test-Path $template)) {
            # 读取模板内容并以无 BOM 的 UTF-8 编码写入规格文件
            $content = [System.IO.File]::ReadAllText($template)
            $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
            [System.IO.File]::WriteAllText($specFile, $content, $utf8NoBom)
        } else {
            New-Item -ItemType File -Path $specFile -Force | Out-Null
        }
    }

    # 持久化到 .specify/feature.json，以便下游命令可以找到功能
    Save-FeatureJson -RepoRoot $repoRoot -FeatureDirectory $featureDir

    # 为当前会话设置环境变量
    $env:SPECIFY_FEATURE = $branchName
    $env:SPECIFY_FEATURE_DIRECTORY = $featureDir
}

if ($Json) {
    $obj = [PSCustomObject]@{
        BRANCH_NAME = $branchName
        SPEC_FILE = $specFile
        FEATURE_NUM = $featureNum
    }
    if ($DryRun) {
        $obj | Add-Member -NotePropertyName 'DRY_RUN' -NotePropertyValue $true
    }
    $obj | ConvertTo-Json -Compress
} else {
    Write-Output "BRANCH_NAME: $branchName"
    Write-Output "SPEC_FILE: $specFile"
    Write-Output "FEATURE_NUM: $featureNum"
    if (-not $DryRun) {
        Write-Output "SPECIFY_FEATURE 设置为：$branchName"
        Write-Output "SPECIFY_FEATURE_DIRECTORY 设置为：$featureDir"
    }
}
