#!/usr/bin/env pwsh
# 通用 PowerShell 函数，类似于 common.sh

# 通过向上搜索 .specify 目录查找仓库根目录
# 这是 spec-kit 项目的主要标记
function Find-SpecifyRoot {
    param([string]$StartDir = (Get-Location).Path)

    # 规范化为绝对路径以防止相对路径问题
    # 使用 -LiteralPath 处理含有通配符（[, ], *, ?）的路径
    $resolved = Resolve-Path -LiteralPath $StartDir -ErrorAction SilentlyContinue
    $current = if ($resolved) { $resolved.Path } else { $null }
    if (-not $current) { return $null }

    while ($true) {
        if (Test-Path -LiteralPath (Join-Path $current ".specify") -PathType Container) {
            return $current
        }
        $parent = Split-Path $current -Parent
        if ([string]::IsNullOrEmpty($parent) -or $parent -eq $current) {
            return $null
        }
        $current = $parent
    }
}

# 获取仓库根目录，优先使用 .specify 目录
# 这可以防止当 spec-kit 在子目录中初始化时使用父级仓库
function Get-RepoRoot {
    # 首先，查找 .specify 目录（spec-kit 自身的标记）
    $specifyRoot = Find-SpecifyRoot
    if ($specifyRoot) {
        return $specifyRoot
    }

    # 最终回退到脚本位置
    # 使用 -LiteralPath 处理含有通配符的路径
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "../../..")).Path
}

function Get-CurrentBranch {
    # 仅从显式状态返回功能名称。
    # 功能状态由 SPECIFY_FEATURE（来自 create-new-feature 或
    # git 扩展）或通过 .specify/feature.json 隐式设置。
    if ($env:SPECIFY_FEATURE) {
        return $env:SPECIFY_FEATURE
    }

    # 未设置显式功能 — 返回空以表示"未知"。
    return ""
}



# 将 feature_directory 值持久化到 .specify/feature.json。
# 仅在文件缺失或值与存储值不同时写入。
function Save-FeatureJson {
    param(
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [Parameter(Mandatory = $true)][string]$FeatureDirectory
    )

    # 如果值是绝对路径且在仓库根目录下，去除仓库根目录前缀。
    # 在 Windows 上使用不区分大小写的比较（其他地方使用区分大小写的文件系统）。
    $prefix = $RepoRoot + [System.IO.Path]::DirectorySeparatorChar
    if ($null -ne $IsWindows) { $onWin = $IsWindows } else { $onWin = $true }
    if ($onWin) {
        $cmp = [System.StringComparison]::OrdinalIgnoreCase
    } else {
        $cmp = [System.StringComparison]::Ordinal
    }
    if ($FeatureDirectory.StartsWith($prefix, $cmp)) {
        $FeatureDirectory = $FeatureDirectory.Substring($prefix.Length)
    }

    $fjPath = Join-Path (Join-Path $RepoRoot '.specify') 'feature.json'

    # 读取当前值并在未变更时跳过写入
    if (Test-Path -LiteralPath $fjPath -PathType Leaf) {
        try {
            $raw = Get-Content -LiteralPath $fjPath -Raw
            $cfg = $raw | ConvertFrom-Json
            if ($cfg.feature_directory -eq $FeatureDirectory) {
                return
            }
        } catch {
            # 文件损坏或不可读 — 覆写它
        }
    }

    # 确保 .specify/ 目录存在
    $specifyDir = Join-Path $RepoRoot '.specify'
    if (-not (Test-Path -LiteralPath $specifyDir -PathType Container)) {
        New-Item -ItemType Directory -Path $specifyDir -Force | Out-Null
    }

    # 写入 feature.json
    $json = @{ feature_directory = $FeatureDirectory } | ConvertTo-Json -Compress
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($fjPath, $json, $utf8NoBom)
}

function Get-FeaturePathsEnv {
    $repoRoot = Get-RepoRoot
    $currentBranch = Get-CurrentBranch

    # 解析功能目录。优先级：
    #   1. SPECIFY_FEATURE_DIRECTORY 环境变量（显式覆盖）
    #   2. .specify/feature.json "feature_directory" 键（由 specify 命令持久化）
    #   3. 错误 — 无可用的功能上下文
    $featureJson = Join-Path $repoRoot '.specify/feature.json'
    if ($env:SPECIFY_FEATURE_DIRECTORY) {
        $featureDir = $env:SPECIFY_FEATURE_DIRECTORY
        # 将相对路径规范化为仓库根目录下的绝对路径
        if (-not [System.IO.Path]::IsPathRooted($featureDir)) {
            $featureDir = Join-Path $repoRoot $featureDir
        }
        # 持久化到 feature.json，使以后没有环境变量的会话仍然有效
        Save-FeatureJson -RepoRoot $repoRoot -FeatureDirectory $env:SPECIFY_FEATURE_DIRECTORY
    } elseif (Test-Path $featureJson) {
        $featureJsonRaw = Get-Content -LiteralPath $featureJson -Raw
        try {
            $featureConfig = $featureJsonRaw | ConvertFrom-Json
        } catch {
            [Console]::Error.WriteLine("错误：无法解析 .specify/feature.json：$_")
            exit 1
        }
        if ($featureConfig.feature_directory) {
            $featureDir = $featureConfig.feature_directory
            # 将相对路径规范化为仓库根目录下的绝对路径
            if (-not [System.IO.Path]::IsPathRooted($featureDir)) {
                $featureDir = Join-Path $repoRoot $featureDir
            }
        } else {
            [Console]::Error.WriteLine("错误：功能目录未找到。请设置 SPECIFY_FEATURE_DIRECTORY 或确保 .specify/feature.json 包含 feature_directory。")
            exit 1
        }
    } else {
        [Console]::Error.WriteLine("错误：功能目录未找到。请设置 SPECIFY_FEATURE_DIRECTORY 或运行 specify 命令创建 .specify/feature.json。")
        exit 1
    }

    [PSCustomObject]@{
        REPO_ROOT     = $repoRoot
        CURRENT_BRANCH = $currentBranch
        FEATURE_DIR   = $featureDir
        FEATURE_SPEC  = Join-Path $featureDir 'spec.md'
        IMPL_PLAN     = Join-Path $featureDir 'plan.md'
        TASKS         = Join-Path $featureDir 'tasks.md'
        RESEARCH      = Join-Path $featureDir 'research.md'
        DATA_MODEL    = Join-Path $featureDir 'data-model.md'
        QUICKSTART    = Join-Path $featureDir 'quickstart.md'
        CONTRACTS_DIR = Join-Path $featureDir 'contracts'
    }
}

function Test-FileExists {
    param([string]$Path, [string]$Description)
    if (Test-Path -Path $Path -PathType Leaf) {
        Write-Output "  [OK] $Description"
        return $true
    } else {
        Write-Output "  [FAIL] $Description"
        return $false
    }
}

function Test-DirHasFiles {
    param([string]$Path, [string]$Description)
    if ((Test-Path -Path $Path -PathType Container) -and (Get-ChildItem -Path $Path -ErrorAction SilentlyContinue | Where-Object { -not $_.PSIsContainer } | Select-Object -First 1)) {
        Write-Output "  [OK] $Description"
        return $true
    } else {
        Write-Output "  [FAIL] $Description"
        return $false
    }
}

function Get-InvokeSeparator {
    param([string]$RepoRoot = (Get-RepoRoot))

    if ($null -eq $script:SpecKitInvokeSeparatorCache) {
        $script:SpecKitInvokeSeparatorCache = @{}
    }
    if ($script:SpecKitInvokeSeparatorCache.ContainsKey($RepoRoot)) {
        return $script:SpecKitInvokeSeparatorCache[$RepoRoot]
    }

    $separator = '.'
    $integrationJson = Join-Path $RepoRoot '.specify/integration.json'
    if (Test-Path -LiteralPath $integrationJson -PathType Leaf) {
        try {
            $state = Get-Content -LiteralPath $integrationJson -Raw | ConvertFrom-Json
            $key = if ($state.default_integration) { [string]$state.default_integration } elseif ($state.integration) { [string]$state.integration } else { '' }
            if ($key -and $state.integration_settings) {
                $settingProperty = $state.integration_settings.PSObject.Properties[$key]
                if ($settingProperty) {
                    $setting = $settingProperty.Value
                    if ($setting -and ($setting.invoke_separator -eq '.' -or $setting.invoke_separator -eq '-')) {
                        $separator = [string]$setting.invoke_separator
                    }
                }
            }
        } catch {
            $separator = '.'
        }
    }

    $script:SpecKitInvokeSeparatorCache[$RepoRoot] = $separator
    return $separator
}

function Format-SpecKitCommand {
    param(
        [Parameter(Mandatory = $true)][string]$CommandName,
        [string]$RepoRoot = (Get-RepoRoot)
    )

    $separator = Get-InvokeSeparator -RepoRoot $RepoRoot
    $name = $CommandName.TrimStart('/')
    if ($name.StartsWith('speckit.')) {
        $name = $name.Substring(8)
    } elseif ($name.StartsWith('speckit-')) {
        $name = $name.Substring(8)
    }
    $name = $name -replace '\.', $separator

    return "/speckit$separator$name"
}

# 查找可用的 Python 3 可执行文件（python3、python 或 py -3）。
# 返回命令/参数数组，如果未找到则返回 $null。
function Get-Python3Command {
    if (Get-Command python3 -ErrorAction SilentlyContinue) { return @('python3') }
    if (Get-Command python -ErrorAction SilentlyContinue) {
        $ver = & python --version 2>&1
        if ($ver -match 'Python 3') { return @('python') }
    }
    if (Get-Command py -ErrorAction SilentlyContinue) {
        $ver = & py -3 --version 2>&1
        if ($ver -match 'Python 3') { return @('py', '-3') }
    }
    return $null
}

# 使用优先级栈将模板名称解析为文件路径：
#   1. .specify/templates/overrides/
#   2. .specify/presets/<preset-id>/templates/（按 .registry 中的优先级排序）
#   3. .specify/extensions/<ext-id>/templates/
#   4. .specify/templates/（核心）
function Resolve-Template {
    param(
        [Parameter(Mandatory=$true)][string]$TemplateName,
        [Parameter(Mandatory=$true)][string]$RepoRoot
    )

    $base = Join-Path $RepoRoot '.specify/templates'

    # 优先级 1：项目覆盖
    $override = Join-Path $base "overrides/$TemplateName.md"
    if (Test-Path $override) { return $override }

    # 优先级 2：已安装的预设（按 .registry 中的优先级排序）
    $presetsDir = Join-Path $RepoRoot '.specify/presets'
    if (Test-Path $presetsDir) {
        $registryFile = Join-Path $presetsDir '.registry'
        $sortedPresets = @()
        if (Test-Path $registryFile) {
            try {
                $registryData = Get-Content $registryFile -Raw | ConvertFrom-Json
                $presets = $registryData.presets
                if ($presets) {
                    $sortedPresets = $presets.PSObject.Properties |
                        Where-Object { $null -eq $_.Value.enabled -or $_.Value.enabled -ne $false } |
                        Sort-Object { if ($null -ne $_.Value.priority) { $_.Value.priority } else { 10 } } |
                        ForEach-Object { $_.Name }
                }
            } catch {
                # 回退：按字母顺序的目录排序
                $sortedPresets = @()
            }
        }

        if ($sortedPresets.Count -gt 0) {
            foreach ($presetId in $sortedPresets) {
                $candidate = Join-Path $presetsDir "$presetId/templates/$TemplateName.md"
                if (Test-Path $candidate) { return $candidate }
            }
        } else {
            # 回退：按字母顺序的目录排序
            foreach ($preset in Get-ChildItem -Path $presetsDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '.*' }) {
                $candidate = Join-Path $preset.FullName "templates/$TemplateName.md"
                if (Test-Path $candidate) { return $candidate }
            }
        }
    }

    # 优先级 3：扩展提供的模板
    $extDir = Join-Path $RepoRoot '.specify/extensions'
    if (Test-Path $extDir) {
        foreach ($ext in Get-ChildItem -Path $extDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '.*' } | Sort-Object Name) {
            $candidate = Join-Path $ext.FullName "templates/$TemplateName.md"
            if (Test-Path $candidate) { return $candidate }
        }
    }

    # 优先级 4：核心模板
    $core = Join-Path $base "$TemplateName.md"
    if (Test-Path $core) { return $core }

    return $null
}

# 使用组合策略将模板名称解析为组合后的内容。
# 从预设清单读取策略元数据，并使用 prepend、append 或 wrap 策略
# 从多个层级组合内容。
function Resolve-TemplateContent {
    param(
        [Parameter(Mandatory=$true)][string]$TemplateName,
        [Parameter(Mandatory=$true)][string]$RepoRoot
    )

    $base = Join-Path $RepoRoot '.specify/templates'

    # 收集所有层级（最高优先级优先）
    $layerPaths = @()
    $layerStrategies = @()

    # 优先级 1：项目覆盖（始终为"替换"）
    $override = Join-Path $base "overrides/$TemplateName.md"
    if (Test-Path $override) {
        $layerPaths += $override
        $layerStrategies += 'replace'
    }

    # 优先级 2：已安装的预设（按 .registry 中的优先级排序）
    $presetsDir = Join-Path $RepoRoot '.specify/presets'
    if (Test-Path $presetsDir) {
        $registryFile = Join-Path $presetsDir '.registry'
        $sortedPresets = @()
        if (Test-Path $registryFile) {
            try {
                $registryData = Get-Content $registryFile -Raw | ConvertFrom-Json
                $presets = $registryData.presets
                if ($presets) {
                    $sortedPresets = $presets.PSObject.Properties |
                        Where-Object { $null -eq $_.Value.enabled -or $_.Value.enabled -ne $false } |
                        Sort-Object { if ($null -ne $_.Value.priority) { $_.Value.priority } else { 10 } } |
                        ForEach-Object { $_.Name }
                }
            } catch {
                $sortedPresets = @()
            }
        }

        if ($sortedPresets.Count -gt 0) {
            $pyCmd = Get-Python3Command
            if (-not $pyCmd) {
                # 检查是否有任何预设包含会被忽略的 strategy 字段
                foreach ($pid in $sortedPresets) {
                    $mf = Join-Path $presetsDir "$pid/preset.yml"
                    if ((Test-Path $mf) -and (Select-String -Path $mf -Pattern 'strategy:' -Quiet -ErrorAction SilentlyContinue)) {
                        Write-Warning "未找到 Python 3；预设组合策略将被忽略"
                        break
                    }
                }
            }
            $yamlWarned = $false
            foreach ($presetId in $sortedPresets) {
                # 从预设清单读取策略和文件路径
                $strategy = 'replace'
                $manifestFilePath = ''
                $manifest = Join-Path $presetsDir "$presetId/preset.yml"
                if ((Test-Path $manifest) -and $pyCmd) {
                    try {
                        # 使用 Python 解析 YAML 清单以获取策略和文件路径
                        $pyArgs = if ($pyCmd.Count -gt 1) { $pyCmd[1..($pyCmd.Count-1)] } else { @() }
                        $pyStderrFile = [System.IO.Path]::GetTempFileName()
                        $stratResult = & $pyCmd[0] @pyArgs -c @"
import sys
try:
    import yaml
except ImportError:
    print('yaml_missing', file=sys.stderr)
    print('replace\t')
    sys.exit(0)
try:
    with open(sys.argv[1]) as f:
        data = yaml.safe_load(f)
    for t in data.get('provides', {}).get('templates', []):
        if t.get('name') == sys.argv[2] and t.get('type', 'template') == 'template':
            print(t.get('strategy', 'replace') + '\t' + t.get('file', ''))
            sys.exit(0)
    print('replace\t')
except Exception:
    print('replace\t')
"@ $manifest $TemplateName 2>$pyStderrFile
                        if ($stratResult) {
                            $parts = $stratResult.Trim() -split "`t", 2
                            $strategy = $parts[0].ToLowerInvariant()
                            if ($parts.Count -gt 1 -and $parts[1]) { $manifestFilePath = $parts[1] }
                        }
                        if (-not $yamlWarned -and (Test-Path $pyStderrFile) -and (Get-Content $pyStderrFile -Raw -ErrorAction SilentlyContinue) -match 'yaml_missing') {
                            Write-Warning "PyYAML 不可用；组合策略可能被忽略"
                            $yamlWarned = $true
                        }
                        Remove-Item $pyStderrFile -Force -ErrorAction SilentlyContinue
                    } catch {
                        $strategy = 'replace'
                        if ($pyStderrFile) { Remove-Item $pyStderrFile -Force -ErrorAction SilentlyContinue }
                    }
                }
                # 先尝试清单文件路径，然后是约定路径
                $candidate = $null
                if ($manifestFilePath) {
                    # 拒绝绝对路径和父级遍历
                    if ([System.IO.Path]::IsPathRooted($manifestFilePath) -or $manifestFilePath -match '\.\.[\\/]') {
                        $manifestFilePath = ''
                    }
                }
                if ($manifestFilePath) {
                    $mf = Join-Path $presetsDir "$presetId/$manifestFilePath"
                    if (Test-Path $mf) { $candidate = $mf }
                }
                if (-not $candidate) {
                    $cf = Join-Path $presetsDir "$presetId/templates/$TemplateName.md"
                    if (Test-Path $cf) { $candidate = $cf }
                }
                if ($candidate) {
                    $layerPaths += $candidate
                    $layerStrategies += $strategy
                }
            }
        } else {
            # 回退：按字母顺序的目录排序（无注册表或解析失败）
            foreach ($preset in Get-ChildItem -Path $presetsDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '.*' }) {
                $candidate = Join-Path $preset.FullName "templates/$TemplateName.md"
                if (Test-Path $candidate) {
                    $layerPaths += $candidate
                    $layerStrategies += 'replace'
                }
            }
        }
    }

    # 优先级 3：扩展提供的模板（始终为"替换"）
    $extDir = Join-Path $RepoRoot '.specify/extensions'
    if (Test-Path $extDir) {
        foreach ($ext in Get-ChildItem -Path $extDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '.*' } | Sort-Object Name) {
            $candidate = Join-Path $ext.FullName "templates/$TemplateName.md"
            if (Test-Path $candidate) {
                $layerPaths += $candidate
                $layerStrategies += 'replace'
            }
        }
    }

    # 优先级 4：核心模板（始终为"替换"）
    $core = Join-Path $base "$TemplateName.md"
    if (Test-Path $core) {
        $layerPaths += $core
        $layerStrategies += 'replace'
    }

    if ($layerPaths.Count -eq 0) { return $null }

    # 如果顶层（最高优先级）层级是替换，它完全胜出 —
    # 无论其策略如何，较低层级都无关。
    if ($layerStrategies[0] -eq 'replace') {
        return (Get-Content $layerPaths[0] -Raw)
    }

    # 检查是否有任何层级使用非替换策略
    $hasComposition = $false
    foreach ($s in $layerStrategies) {
        if ($s -ne 'replace') { $hasComposition = $true; break }
    }

    if (-not $hasComposition) {
        return (Get-Content $layerPaths[0] -Raw)
    }

    # 找到有效基础：从最高优先级（索引 0）向下扫描
    # 以找到最近的替换层级。仅组合该基础之上的层级。
    $baseIdx = -1
    for ($i = 0; $i -lt $layerPaths.Count; $i++) {
        if ($layerStrategies[$i] -eq 'replace') {
            $baseIdx = $i
            break
        }
    }
    if ($baseIdx -lt 0) { return $null }

    $content = Get-Content $layerPaths[$baseIdx] -Raw

    for ($i = $baseIdx - 1; $i -ge 0; $i--) {
        $path = $layerPaths[$i]
        $strat = $layerStrategies[$i]
        $layerContent = Get-Content $path -Raw

        switch ($strat) {
            'replace' { $content = $layerContent }
            'prepend' { $content = "$layerContent`n`n$content" }
            'append'  { $content = "$content`n`n$layerContent" }
            'wrap'    {
                if (-not $layerContent.Contains('{CORE_TEMPLATE}')) {
                    throw "Wrap 策略缺少 {CORE_TEMPLATE} 占位符"
                }
                $content = $layerContent.Replace('{CORE_TEMPLATE}', $content)
            }
            default { throw "未知策略：$strat" }
        }
    }

    return $content
}
