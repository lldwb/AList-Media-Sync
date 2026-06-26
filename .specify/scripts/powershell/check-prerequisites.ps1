#!/usr/bin/env pwsh

# 统一前提条件检查脚本（PowerShell）
#
# 此脚本为规范驱动开发工作流提供统一的前提条件检查。
# 它替换了之前分散在多个脚本中的功能。
#
# 用法：./check-prerequisites.ps1 [OPTIONS]
#
# OPTIONS:
#   -Json               以 JSON 格式输出
#   -RequireTasks       要求 tasks.md 存在（用于实现阶段）
#   -IncludeTasks       将 tasks.md 包含在 AVAILABLE_DOCS 列表中
#   -PathsOnly          仅输出路径变量（不进行验证）
#   -Help, -h           显示帮助信息

[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$RequireTasks,
    [switch]$IncludeTasks,
    [switch]$PathsOnly,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# 如果请求帮助则显示
if ($Help) {
    Write-Output @"
用法：check-prerequisites.ps1 [OPTIONS]

规范驱动开发工作流的统一前提条件检查。

OPTIONS:
  -Json               以 JSON 格式输出
  -RequireTasks       要求 tasks.md 存在（用于实现阶段）
  -IncludeTasks       将 tasks.md 包含在 AVAILABLE_DOCS 列表中
  -PathsOnly          仅输出路径变量（不进行前提条件验证）
  -Help, -h           显示此帮助信息

EXAMPLES:
  # 检查任务前提条件（需要 plan.md）
  .\check-prerequisites.ps1 -Json

  # 检查实现前提条件（需要 plan.md + tasks.md）
  .\check-prerequisites.ps1 -Json -RequireTasks -IncludeTasks

  # 仅获取功能路径（不验证）
  .\check-prerequisites.ps1 -PathsOnly

"@
    exit 0
}

# 导入通用函数
. "$PSScriptRoot/common.ps1"

# 获取功能路径
$paths = Get-FeaturePathsEnv

# 如果仅路径模式，输出路径并退出（不验证）
if ($PathsOnly) {
    if ($Json) {
        [PSCustomObject]@{
            REPO_ROOT    = $paths.REPO_ROOT
            BRANCH       = $paths.CURRENT_BRANCH
            FEATURE_DIR  = $paths.FEATURE_DIR
            FEATURE_SPEC = $paths.FEATURE_SPEC
            IMPL_PLAN    = $paths.IMPL_PLAN
            TASKS        = $paths.TASKS
        } | ConvertTo-Json -Compress
    } else {
        Write-Output "REPO_ROOT: $($paths.REPO_ROOT)"
        Write-Output "BRANCH: $($paths.CURRENT_BRANCH)"
        Write-Output "FEATURE_DIR: $($paths.FEATURE_DIR)"
        Write-Output "FEATURE_SPEC: $($paths.FEATURE_SPEC)"
        Write-Output "IMPL_PLAN: $($paths.IMPL_PLAN)"
        Write-Output "TASKS: $($paths.TASKS)"
    }
    exit 0
}

# 验证必需的目录和文件
if (-not (Test-Path $paths.FEATURE_DIR -PathType Container)) {
    Write-Output "错误：功能目录未找到：$($paths.FEATURE_DIR)"
    $specifyCommand = Format-SpecKitCommand -CommandName 'specify' -RepoRoot $paths.REPO_ROOT
    Write-Output "请先运行 $specifyCommand 创建功能结构。"
    exit 1
}

if (-not (Test-Path $paths.IMPL_PLAN -PathType Leaf)) {
    Write-Output "错误：plan.md 未在 $($paths.FEATURE_DIR) 中找到"
    $planCommand = Format-SpecKitCommand -CommandName 'plan' -RepoRoot $paths.REPO_ROOT
    Write-Output "请先运行 $planCommand 创建实现计划。"
    exit 1
}

# 如果需要 tasks.md 则检查
if ($RequireTasks -and -not (Test-Path $paths.TASKS -PathType Leaf)) {
    Write-Output "错误：tasks.md 未在 $($paths.FEATURE_DIR) 中找到"
    $tasksCommand = Format-SpecKitCommand -CommandName 'tasks' -RepoRoot $paths.REPO_ROOT
    Write-Output "请先运行 $tasksCommand 创建任务列表。"
    exit 1
}

# 构建可用文档列表
$docs = @()

# 始终检查这些可选文档
if (Test-Path $paths.RESEARCH) { $docs += 'research.md' }
if (Test-Path $paths.DATA_MODEL) { $docs += 'data-model.md' }

# 检查 contracts 目录（仅当它存在且有文件时）
if ((Test-Path $paths.CONTRACTS_DIR) -and (Get-ChildItem -Path $paths.CONTRACTS_DIR -ErrorAction SilentlyContinue | Select-Object -First 1)) {
    $docs += 'contracts/'
}

if (Test-Path $paths.QUICKSTART) { $docs += 'quickstart.md' }

# 如果请求了 tasks.md 且其存在，则包含
if ($IncludeTasks -and (Test-Path $paths.TASKS)) {
    $docs += 'tasks.md'
}

# 输出结果
if ($Json) {
    # JSON 输出
    [PSCustomObject]@{
        FEATURE_DIR = $paths.FEATURE_DIR
        AVAILABLE_DOCS = $docs
    } | ConvertTo-Json -Compress
} else {
    # 文本输出
    Write-Output "FEATURE_DIR:$($paths.FEATURE_DIR)"
    Write-Output "AVAILABLE_DOCS:"

    # 显示每个潜在文档的状态
    Test-FileExists -Path $paths.RESEARCH -Description 'research.md' | Out-Null
    Test-FileExists -Path $paths.DATA_MODEL -Description 'data-model.md' | Out-Null
    Test-DirHasFiles -Path $paths.CONTRACTS_DIR -Description 'contracts/' | Out-Null
    Test-FileExists -Path $paths.QUICKSTART -Description 'quickstart.md' | Out-Null

    if ($IncludeTasks) {
        Test-FileExists -Path $paths.TASKS -Description 'tasks.md' | Out-Null
    }
}
