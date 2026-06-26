#!/usr/bin/env pwsh
# 为功能设置实现计划

[CmdletBinding()]
param(
    [switch]$Json,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# 如果请求帮助则显示
if ($Help) {
    Write-Output "用法：./setup-plan.ps1 [-Json] [-Help]"
    Write-Output "  -Json     以 JSON 格式输出结果"
    Write-Output "  -Help     显示此帮助信息"
    exit 0
}

# 加载通用函数
. "$PSScriptRoot/common.ps1"

# 从通用函数获取所有路径和变量
$paths = Get-FeaturePathsEnv

# 确保功能目录存在
New-Item -ItemType Directory -Path $paths.FEATURE_DIR -Force | Out-Null

# 如果计划尚不存在，复制计划模板
if (Test-Path $paths.IMPL_PLAN -PathType Leaf) {
    if ($Json) {
        [Console]::Error.WriteLine("计划已存在于 $($paths.IMPL_PLAN)，跳过模板复制")
    } else {
        Write-Output "计划已存在于 $($paths.IMPL_PLAN)，跳过模板复制"
    }
} else {
    $template = Resolve-Template -TemplateName 'plan-template' -RepoRoot $paths.REPO_ROOT
    if ($template -and (Test-Path $template)) {
        # 读取模板内容并以无 BOM 的 UTF-8 编码写入实现计划文件
        $content = [System.IO.File]::ReadAllText($template)
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($paths.IMPL_PLAN, $content, $utf8NoBom)
    } else {
        Write-Warning "未找到计划模板"
        # 如果模板不存在则创建基本计划文件
        New-Item -ItemType File -Path $paths.IMPL_PLAN -Force | Out-Null
    }
}

# 输出结果
if ($Json) {
    $result = [PSCustomObject]@{
        FEATURE_SPEC = $paths.FEATURE_SPEC
        IMPL_PLAN = $paths.IMPL_PLAN
        SPECS_DIR = $paths.FEATURE_DIR
        BRANCH = $paths.CURRENT_BRANCH
    }
    $result | ConvertTo-Json -Compress
} else {
    Write-Output "FEATURE_SPEC: $($paths.FEATURE_SPEC)"
    Write-Output "IMPL_PLAN: $($paths.IMPL_PLAN)"
    Write-Output "SPECS_DIR: $($paths.FEATURE_DIR)"
    Write-Output "BRANCH: $($paths.CURRENT_BRANCH)"
}
