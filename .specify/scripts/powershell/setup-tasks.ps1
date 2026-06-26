#!/usr/bin/env pwsh

[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    Write-Output "用法：setup-tasks.ps1 [-Json] [-Help]"
    exit 0
}

# 导入通用函数
. "$PSScriptRoot/common.ps1"

# 获取功能路径
$paths = Get-FeaturePathsEnv

if (-not (Test-Path $paths.IMPL_PLAN -PathType Leaf)) {
    [Console]::Error.WriteLine("错误：plan.md 未在 $($paths.FEATURE_DIR) 中找到")
    $planCommand = Format-SpecKitCommand -CommandName 'plan' -RepoRoot $paths.REPO_ROOT
    [Console]::Error.WriteLine("请先运行 $planCommand 创建实现计划。")
    exit 1
}

if (-not (Test-Path $paths.FEATURE_SPEC -PathType Leaf)) {
    [Console]::Error.WriteLine("错误：spec.md 未在 $($paths.FEATURE_DIR) 中找到")
    $specifyCommand = Format-SpecKitCommand -CommandName 'specify' -RepoRoot $paths.REPO_ROOT
    [Console]::Error.WriteLine("请先运行 $specifyCommand 创建功能结构。")
    exit 1
}

# 构建可用文档列表
$docs = @()
if (Test-Path $paths.RESEARCH) { $docs += 'research.md' }
if (Test-Path $paths.DATA_MODEL) { $docs += 'data-model.md' }
if ((Test-Path $paths.CONTRACTS_DIR) -and (Get-ChildItem -Path $paths.CONTRACTS_DIR -ErrorAction SilentlyContinue | Select-Object -First 1)) {
    $docs += 'contracts/'
}
if (Test-Path $paths.QUICKSTART) { $docs += 'quickstart.md' }

# 通过覆盖栈解析任务模板
$tasksTemplate = Resolve-Template -TemplateName 'tasks-template' -RepoRoot $paths.REPO_ROOT
if (-not $tasksTemplate -or -not (Test-Path -LiteralPath $tasksTemplate -PathType Leaf)) {
    $expectedCoreTemplate = Join-Path $paths.REPO_ROOT '.specify/templates/tasks-template.md'
    [Console]::Error.WriteLine("错误：未找到仓库根目录的任务模板：$($paths.REPO_ROOT)`n模板解析顺序：覆盖 -> 预设 -> 扩展 -> 核心。`n预期的共享/核心模板位置：$expectedCoreTemplate`n要继续，请验证 'tasks-template.md' 在 '.specify/templates/overrides/'、预设模板、扩展模板中是否可用，或恢复共享/核心模板（例如通过重新运行 'specify init'）以确保 '.specify/templates/tasks-template.md' 存在。")
    exit 1
}
$tasksTemplate = (Resolve-Path -LiteralPath $tasksTemplate).Path

# 输出结果
if ($Json) {
    [PSCustomObject]@{
        FEATURE_DIR    = $paths.FEATURE_DIR
        AVAILABLE_DOCS = $docs
        TASKS_TEMPLATE = $tasksTemplate
    } | ConvertTo-Json -Compress
} else {
    Write-Output "FEATURE_DIR: $($paths.FEATURE_DIR)"
    Write-Output "TASKS_TEMPLATE: $(if ($tasksTemplate) { $tasksTemplate } else { '未找到' })"
    Write-Output "AVAILABLE_DOCS:"
    Test-FileExists -Path $paths.RESEARCH -Description 'research.md' | Out-Null
    Test-FileExists -Path $paths.DATA_MODEL -Description 'data-model.md' | Out-Null
    Test-DirHasFiles -Path $paths.CONTRACTS_DIR -Description 'contracts/' | Out-Null
    Test-FileExists -Path $paths.QUICKSTART -Description 'quickstart.md' | Out-Null
}
