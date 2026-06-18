#!/usr/bin/env pwsh
# update-agent-context.ps1
#
# 刷新编码代理上下文文件（例如 CLAUDE.md、.github/copilot-instructions.md、AGENTS.md）
# 中受管理的 Spec Kit 区块。
#
# 从代理上下文扩展配置中读取 `context_file` 和 `context_markers.{start,end}`：
#   .specify/extensions/agent-context/agent-context-config.yml
#
# 用法：update-agent-context.ps1 [plan_path]

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$PlanPath
)

function Get-ConfigValue {
    param(
        [AllowNull()][object]$Object,
        [Parameter(Mandatory = $true)][string]$Key
    )

    if ($null -eq $Object) {
        return $null
    }
    if ($Object -is [System.Collections.IDictionary]) {
        return $Object[$Key]
    }
    $prop = $Object.PSObject.Properties[$Key]
    if ($prop) {
        return $prop.Value
    }
    return $null
}

function Test-ConfigObject {
    param(
        [AllowNull()][object]$Object
    )

    if ($null -eq $Object) {
        return $false
    }
    if ($Object -is [System.Collections.IDictionary]) {
        return $true
    }
    if ($Object -is [System.Management.Automation.PSCustomObject]) {
        return $true
    }
    return $false
}

$ErrorActionPreference = 'Stop'
$DefaultStart = '<!-- SPECKIT START -->'
$DefaultEnd   = '<!-- SPECKIT END -->'
$ProjectRoot  = (Get-Location).Path
$ExtConfig    = Join-Path $ProjectRoot '.specify/extensions/agent-context/agent-context-config.yml'

if (-not (Test-Path -LiteralPath $ExtConfig)) {
    Write-Warning "agent-context: $ExtConfig 未找到；无需操作。"
    exit 0
}

$Options = $null
if (Get-Command ConvertFrom-Yaml -ErrorAction SilentlyContinue) {
    try {
        $Options = Get-Content -LiteralPath $ExtConfig -Raw | ConvertFrom-Yaml -ErrorAction Stop
    } catch {
        # 回退到 Python 备用方案
    }
}

if ($null -eq $Options) {
    # ConvertFrom-Yaml 不可用或失败；回退到 Python+PyYAML。
    $pythonCmd = $null
    foreach ($candidate in @('python3', 'python')) {
        if (Get-Command $candidate -ErrorAction SilentlyContinue) {
            # 验证它是 Python 3
            $verOut = & $candidate --version 2>&1
            if ($verOut -match 'Python 3') {
                $pythonCmd = $candidate
                break
            }
        }
    }

    if ($pythonCmd) {
        try {
            $jsonOut = & $pythonCmd -c @'
import json
import sys
try:
    import yaml
except ImportError:
    print(
        "agent-context: 解析扩展配置需要 PyYAML；无法更新上下文。",
        file=sys.stderr,
    )
    sys.exit(2)

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        data = yaml.safe_load(fh)
except Exception as exc:
    print(
        f"agent-context: 无法解析 {sys.argv[1]} ({exc})；无法更新上下文。",
        file=sys.stderr,
    )
    sys.exit(2)

if not isinstance(data, dict):
    data = {}

print(json.dumps(data))
'@ $ExtConfig
            if ($LASTEXITCODE -eq 0 -and $jsonOut) {
                $Options = $jsonOut | ConvertFrom-Json -ErrorAction Stop
            }
        } catch {
            $Options = $null
        }
    }

    if (-not $Options) {
        Write-Warning "agent-context: 无法解析 $ExtConfig；跳过更新。"
        exit 0
    }
}

if (-not (Test-ConfigObject -Object $Options)) {
    Write-Warning "agent-context: $ExtConfig 必须包含一个 YAML 映射；跳过更新。"
    exit 0
}

$ContextFile = Get-ConfigValue -Object $Options -Key 'context_file'
if (-not $ContextFile) {
    Write-Warning 'agent-context: 扩展配置中未设置 context_file；无需操作。'
    exit 0
}

# 拒绝 context_file 中的绝对路径和 '..' 路径段
if ([System.IO.Path]::IsPathRooted($ContextFile)) {
    Write-Warning "agent-context: context_file 必须是项目相对路径；得到 '$ContextFile'。"
    exit 1
}
$cfSegments = $ContextFile -split '[/\\]'
if ($cfSegments -contains '..') {
    Write-Warning "agent-context: context_file 不得包含 '..' 路径段；得到 '$ContextFile'。"
    exit 1
}

$MarkerStart = $DefaultStart
$MarkerEnd   = $DefaultEnd
$cm = Get-ConfigValue -Object $Options -Key 'context_markers'
if ($cm) {
    $cmStart = Get-ConfigValue -Object $cm -Key 'start'
    if ($cmStart -is [string] -and $cmStart) {
        $MarkerStart = $cmStart
    }
    $cmEnd = Get-ConfigValue -Object $cm -Key 'end'
    if ($cmEnd -is [string] -and $cmEnd) {
        $MarkerEnd = $cmEnd
    }
}

if (-not $PlanPath) {
    # 发现恰好一级深度下的 plan.md（specs/<feature>/plan.md），
    # 匹配 bash 通配符 specs/*/plan.md。包装在 try/catch 中，使在
    # $ErrorActionPreference = 'Stop' 下的访问错误不会中止脚本。
    try {
        $specsDir = Join-Path $ProjectRoot 'specs'
        $candidate = Get-ChildItem -Path $specsDir -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { Get-Item -LiteralPath (Join-Path $_.FullName 'plan.md') -ErrorAction SilentlyContinue } |
            Where-Object { $_ } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($candidate) {
            $PlanPath = [System.IO.Path]::GetRelativePath($ProjectRoot, $candidate.FullName).Replace('\','/')
        }
    } catch {
        # 非致命：继续执行，不含计划路径。
    }
}

$CtxPath = Join-Path $ProjectRoot $ContextFile
$CtxDir  = Split-Path -Parent $CtxPath
if ($CtxDir -and -not (Test-Path -LiteralPath $CtxDir)) {
    New-Item -ItemType Directory -Path $CtxDir -Force | Out-Null
}

$lines = @($MarkerStart,
           '如需了解要使用的技术、项目结构、Shell 命令及其他重要信息，请参阅当前计划')
if ($PlanPath) {
    $lines += "位于 $PlanPath"
}
$lines += $MarkerEnd
$Section = ($lines -join "`n") + "`n"

if (Test-Path -LiteralPath $CtxPath) {
    $rawBytes = [System.IO.File]::ReadAllBytes($CtxPath)
    # 去除可能存在的 UTF-8 BOM
    if ($rawBytes.Length -ge 3 -and $rawBytes[0] -eq 0xEF -and $rawBytes[1] -eq 0xBB -and $rawBytes[2] -eq 0xBF) {
        $content = [System.Text.Encoding]::UTF8.GetString($rawBytes, 3, $rawBytes.Length - 3)
    } else {
        $content = [System.Text.Encoding]::UTF8.GetString($rawBytes)
    }

    $s = $content.IndexOf($MarkerStart)
    $e = if ($s -ge 0) { $content.IndexOf($MarkerEnd, $s) } else { $content.IndexOf($MarkerEnd) }

    if ($s -ge 0 -and $e -ge 0 -and $e -gt $s) {
        $endOfMarker = $e + $MarkerEnd.Length
        if ($endOfMarker -lt $content.Length -and $content[$endOfMarker] -eq "`r") { $endOfMarker++ }
        if ($endOfMarker -lt $content.Length -and $content[$endOfMarker] -eq "`n") { $endOfMarker++ }
        $newContent = $content.Substring(0, $s) + $Section + $content.Substring($endOfMarker)
    } elseif ($s -ge 0) {
        $newContent = $content.Substring(0, $s) + $Section
    } elseif ($e -ge 0) {
        $endOfMarker = $e + $MarkerEnd.Length
        if ($endOfMarker -lt $content.Length -and $content[$endOfMarker] -eq "`r") { $endOfMarker++ }
        if ($endOfMarker -lt $content.Length -and $content[$endOfMarker] -eq "`n") { $endOfMarker++ }
        $newContent = $Section + $content.Substring($endOfMarker)
    } else {
        if ($content -and -not $content.EndsWith("`n")) { $content += "`n" }
        if ($content) { $newContent = $content + "`n" + $Section } else { $newContent = $Section }
    }
} else {
    $newContent = $Section
}

$newContent = $newContent.Replace("`r`n", "`n").Replace("`r", "`n")
[System.IO.File]::WriteAllText($CtxPath, $newContent, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "agent-context: 已更新 $ContextFile"
