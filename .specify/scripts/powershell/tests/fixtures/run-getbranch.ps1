#!/usr/bin/env pwsh
# fixture: 加载传入的 common.ps1 并打印 Get-CurrentBranch 输出
# 切换到 common.ps1 所在仓库的根目录，避免 Find-SpecifyRoot 跳到外层项目
param([Parameter(Mandatory = $true)][string]$CommonPath)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent (Resolve-Path -LiteralPath $CommonPath).Path))
Set-Location -LiteralPath $repoRoot
. $CommonPath
Write-Output (Get-CurrentBranch)
