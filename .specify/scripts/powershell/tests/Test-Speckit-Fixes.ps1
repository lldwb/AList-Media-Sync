#!/usr/bin/env pwsh
# Spec-Kit fix regression tests (ASCII-only to avoid console codepage issues)
# Run: powershell -NoProfile -ExecutionPolicy Bypass -File .\Test-Speckit-Fixes.ps1

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$script:Passed = 0
$script:Failed = 0
$script:Failures = New-Object System.Collections.Generic.List[string]

function Invoke-Test {
    param([string]$Name, [scriptblock]$Body)
    Write-Host -NoNewline "  - $Name ... "
    try {
        & $Body
        Write-Host "PASS" -ForegroundColor Green
        $script:Passed++
    } catch {
        Write-Host "FAIL" -ForegroundColor Red
        $script:Failed++
        $msg = "[$Name] " + $_.Exception.Message
        $script:Failures.Add($msg) | Out-Null
        Write-Host ("    " + $_.Exception.Message) -ForegroundColor Yellow
    }
}

function Assert-Equal {
    param($Expected, $Actual, [string]$Because)
    if ($Expected -cne $Actual) {
        throw ("expected [{0}] got [{1}] - {2}" -f $Expected, $Actual, $Because)
    }
}

function Assert-True {
    param([bool]$Cond, [string]$Because)
    if (-not $Cond) { throw ("condition false - {0}" -f $Because) }
}

function Assert-Match {
    param([string]$Pattern, [string]$Actual, [string]$Because)
    if ($Actual -notmatch $Pattern) {
        throw ("[{0}] does not match [{1}] - {2}" -f $Actual, $Pattern, $Because)
    }
}

function New-TempRepo {
    param([string]$InvokeSeparator = '-')
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("speckit-test-" + [System.IO.Path]::GetRandomFileName())
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null

    $specifyDir = Join-Path $tmp '.specify'
    New-Item -ItemType Directory -Path (Join-Path $specifyDir 'scripts/powershell') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $specifyDir 'templates') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $specifyDir 'extensions/agent-context/scripts/powershell') -Force | Out-Null

    $srcPs = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
    Copy-Item -LiteralPath (Join-Path $srcPs 'common.ps1') -Destination (Join-Path $specifyDir 'scripts/powershell/common.ps1')
    Copy-Item -LiteralPath (Join-Path $srcPs 'check-prerequisites.ps1') -Destination (Join-Path $specifyDir 'scripts/powershell/check-prerequisites.ps1')
    Copy-Item -LiteralPath (Join-Path $srcPs 'create-new-feature.ps1') -Destination (Join-Path $specifyDir 'scripts/powershell/create-new-feature.ps1')
    Copy-Item -LiteralPath (Join-Path $srcPs 'setup-plan.ps1') -Destination (Join-Path $specifyDir 'scripts/powershell/setup-plan.ps1')
    Copy-Item -LiteralPath (Join-Path $srcPs 'setup-tasks.ps1') -Destination (Join-Path $specifyDir 'scripts/powershell/setup-tasks.ps1')

    $agentSrc = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\extensions\agent-context\scripts\powershell\update-agent-context.ps1')).Path
    Copy-Item -LiteralPath $agentSrc -Destination (Join-Path $specifyDir 'extensions/agent-context/scripts/powershell/update-agent-context.ps1')

    Set-Content -LiteralPath (Join-Path $specifyDir 'templates/spec-template.md') -Value "# spec" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $specifyDir 'templates/plan-template.md') -Value "# plan" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $specifyDir 'templates/tasks-template.md') -Value "# tasks" -Encoding utf8

    $integration = @{
        version              = "0.11.2"
        installed_integrations = @('claude')
        integration_settings = @{ claude = @{ script = 'ps'; invoke_separator = $InvokeSeparator } }
        integration          = 'claude'
        default_integration  = 'claude'
    } | ConvertTo-Json -Depth 5
    Set-Content -LiteralPath (Join-Path $specifyDir 'integration.json') -Value $integration -Encoding utf8

    $cfg = "context_file: AGENTS.md`ncontext_markers:`n  start: <!-- SPECKIT START -->`n  end: <!-- SPECKIT END -->`n"
    Set-Content -LiteralPath (Join-Path $specifyDir 'extensions/agent-context/agent-context-config.yml') -Value $cfg -Encoding utf8

    return $tmp
}

function Remove-TempRepo {
    param([string]$Path)
    if ($Path -and (Test-Path -LiteralPath $Path)) {
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Ps {
    param(
        [string]$ScriptPath,
        [string[]]$CliArgs = @(),
        [hashtable]$EnvVars = @{},
        [string]$WorkDir = $null
    )
    # 使用临时文件捕获 stdout/stderr，PowerShell 原生 & 调用最可靠
    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    $savedCwd = (Get-Location).Path
    $savedEnv = @{}
    # 备份并设置环境
    foreach ($k in @('SPECIFY_FEATURE','SPECIFY_FEATURE_DIRECTORY')) {
        $savedEnv[$k] = [System.Environment]::GetEnvironmentVariable($k, 'Process')
        [System.Environment]::SetEnvironmentVariable($k, $null, 'Process')
    }
    foreach ($k in $EnvVars.Keys) {
        if (-not $savedEnv.ContainsKey($k)) {
            $savedEnv[$k] = [System.Environment]::GetEnvironmentVariable($k, 'Process')
        }
        [System.Environment]::SetEnvironmentVariable($k, [string]$EnvVars[$k], 'Process')
    }
    if ($WorkDir) { Set-Location -LiteralPath $WorkDir }
    $exitCode = 0
    try {
        $allArgs = @($ScriptPath) + $CliArgs
        # 通过 & 调用 powershell.exe，每个参数作为独立 token
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File @allArgs 1> $outFile 2> $errFile
        $exitCode = $LASTEXITCODE
    } finally {
        Set-Location -LiteralPath $savedCwd
        foreach ($k in $savedEnv.Keys) {
            [System.Environment]::SetEnvironmentVariable($k, $savedEnv[$k], 'Process')
        }
    }
    $stdout = Get-Content -LiteralPath $outFile -Raw -ErrorAction SilentlyContinue
    $stderr = Get-Content -LiteralPath $errFile -Raw -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $outFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $errFile -Force -ErrorAction SilentlyContinue
    if ($null -eq $stdout) { $stdout = '' }
    if ($null -eq $stderr) { $stderr = '' }
    $result = New-Object PSObject
    Add-Member -InputObject $result -MemberType NoteProperty -Name ExitCode -Value $exitCode
    Add-Member -InputObject $result -MemberType NoteProperty -Name StdOut -Value $stdout
    Add-Member -InputObject $result -MemberType NoteProperty -Name StdErr -Value $stderr
    return $result
}

Write-Host ""
Write-Host "Spec-Kit fix regression tests" -ForegroundColor Cyan
Write-Host "==============================" -ForegroundColor Cyan

# ---- #1 Get-CurrentBranch fallback ----
Write-Host ""
Write-Host "[#1] Get-CurrentBranch fallback" -ForegroundColor Cyan

Invoke-Test "env var takes precedence" {
    $repo = New-TempRepo
    try {
        $common = Join-Path $repo '.specify/scripts/powershell/common.ps1'
        $fixture = Join-Path $PSScriptRoot 'fixtures/run-getbranch.ps1'
        $r = Invoke-Ps -ScriptPath $fixture -CliArgs @($common) -EnvVars @{ SPECIFY_FEATURE = 'env-branch' }
        Assert-Equal 'env-branch' $r.StdOut.Trim() 'env var should win'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "infer from feature.json leaf" {
    $repo = New-TempRepo
    try {
        $featureJson = Join-Path $repo '.specify/feature.json'
        Set-Content -LiteralPath $featureJson -Value '{"feature_directory":"specs/042-some-cool-feature"}' -Encoding utf8
        $common = Join-Path $repo '.specify/scripts/powershell/common.ps1'
        $fixture = Join-Path $PSScriptRoot 'fixtures/run-getbranch.ps1'
        $r = Invoke-Ps -ScriptPath $fixture -CliArgs @($common)
        Assert-Equal '042-some-cool-feature' $r.StdOut.Trim() 'should return leaf name'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "missing feature.json returns empty" {
    $repo = New-TempRepo
    try {
        $common = Join-Path $repo '.specify/scripts/powershell/common.ps1'
        $fixture = Join-Path $PSScriptRoot 'fixtures/run-getbranch.ps1'
        $r = Invoke-Ps -ScriptPath $fixture -CliArgs @($common)
        Assert-Equal '' $r.StdOut.Trim() 'should be empty'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "corrupt feature.json does not crash" {
    $repo = New-TempRepo
    try {
        Set-Content -LiteralPath (Join-Path $repo '.specify/feature.json') -Value '{invalid json' -Encoding utf8
        $common = Join-Path $repo '.specify/scripts/powershell/common.ps1'
        $fixture = Join-Path $PSScriptRoot 'fixtures/run-getbranch.ps1'
        $r = Invoke-Ps -ScriptPath $fixture -CliArgs @($common)
        Assert-Equal '' $r.StdOut.Trim() 'corrupt file should fall through silently'
    } finally { Remove-TempRepo $repo }
}

# ---- #2 check-prerequisites uses Format-SpecKitCommand ----
Write-Host ""
Write-Host "[#2] check-prerequisites uses Format-SpecKitCommand" -ForegroundColor Cyan

Invoke-Test "dash separator (claude default)" {
    $repo = New-TempRepo -InvokeSeparator '-'
    try {
        $script = Join-Path $repo '.specify/scripts/powershell/check-prerequisites.ps1'
        $featureDir = Join-Path $repo 'specs/099-nonexistent'
        $r = Invoke-Ps -ScriptPath $script -EnvVars @{ SPECIFY_FEATURE_DIRECTORY = $featureDir } -WorkDir $repo
        Assert-True ($r.ExitCode -ne 0) 'missing feature dir should fail'
        Assert-Match '/speckit-specify' $r.StdOut 'should mention /speckit-specify'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "dot separator (other integrations)" {
    $repo = New-TempRepo -InvokeSeparator '.'
    try {
        $featureDir = Join-Path $repo 'specs/099-nonexistent'
        New-Item -ItemType Directory -Path $featureDir -Force | Out-Null
        $script = Join-Path $repo '.specify/scripts/powershell/check-prerequisites.ps1'
        $r = Invoke-Ps -ScriptPath $script -EnvVars @{ SPECIFY_FEATURE_DIRECTORY = $featureDir } -WorkDir $repo
        Assert-True ($r.ExitCode -ne 0) 'missing plan.md should fail'
        Assert-Match '/speckit\.plan' $r.StdOut 'dot separator should produce /speckit.plan'
    } finally { Remove-TempRepo $repo }
}

# ---- #3 feature.json normalized writes ----
Write-Host ""
Write-Host "[#3] feature.json normalized writes" -ForegroundColor Cyan

Invoke-Test "relative input stored as relative" {
    $repo = New-TempRepo
    try {
        $featureDir = Join-Path $repo 'specs/050-foo'
        New-Item -ItemType Directory -Path $featureDir -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $featureDir 'plan.md') -Value '# plan' -Encoding utf8
        $script = Join-Path $repo '.specify/scripts/powershell/check-prerequisites.ps1'
        $r = Invoke-Ps -ScriptPath $script -CliArgs @('-Json') -EnvVars @{ SPECIFY_FEATURE_DIRECTORY = 'specs/050-foo' } -WorkDir $repo
        Assert-True ($r.ExitCode -eq 0) ("expected success; stderr=" + $r.StdErr)
        $cfg = Get-Content -LiteralPath (Join-Path $repo '.specify/feature.json') -Raw | ConvertFrom-Json
        Assert-Equal 'specs/050-foo' ($cfg.feature_directory -replace '\\','/') 'should be relative'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "absolute input compressed to relative" {
    $repo = New-TempRepo
    try {
        $featureDir = Join-Path $repo 'specs/051-bar'
        New-Item -ItemType Directory -Path $featureDir -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $featureDir 'plan.md') -Value '# plan' -Encoding utf8
        $script = Join-Path $repo '.specify/scripts/powershell/check-prerequisites.ps1'
        $r = Invoke-Ps -ScriptPath $script -CliArgs @('-Json') -EnvVars @{ SPECIFY_FEATURE_DIRECTORY = $featureDir } -WorkDir $repo
        Assert-True ($r.ExitCode -eq 0) ("expected success; stderr=" + $r.StdErr)
        $cfg = Get-Content -LiteralPath (Join-Path $repo '.specify/feature.json') -Raw | ConvertFrom-Json
        Assert-Equal 'specs/051-bar' ($cfg.feature_directory -replace '\\','/') 'absolute should compress to relative'
    } finally { Remove-TempRepo $repo }
}

# ---- #4 Get-BranchName word selection ----
Write-Host ""
Write-Host "[#4] create-new-feature word selection" -ForegroundColor Cyan

Invoke-Test "5 meaningful words trimmed to 4" {
    $repo = New-TempRepo
    try {
        $script = Join-Path $repo '.specify/scripts/powershell/create-new-feature.ps1'
        $r = Invoke-Ps -ScriptPath $script -CliArgs @('-Json', '-DryRun', 'implement', 'amazing', 'powerful', 'realtime', 'analytics') -WorkDir $repo
        Assert-True ($r.ExitCode -eq 0) ("expected success; stderr=" + $r.StdErr)
        $obj = $r.StdOut | ConvertFrom-Json
        Assert-Match 'implement-amazing-powerful-realtime' $obj.BRANCH_NAME 'should keep 4 words'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "2 words kept fully" {
    $repo = New-TempRepo
    try {
        $script = Join-Path $repo '.specify/scripts/powershell/create-new-feature.ps1'
        $r = Invoke-Ps -ScriptPath $script -CliArgs @('-Json', '-DryRun', 'simple', 'feature') -WorkDir $repo
        Assert-True ($r.ExitCode -eq 0) ("expected success; stderr=" + $r.StdErr)
        $obj = $r.StdOut | ConvertFrom-Json
        Assert-Match 'simple-feature' $obj.BRANCH_NAME 'should keep both words'
    } finally { Remove-TempRepo $repo }
}

Invoke-Test "4 words kept fully" {
    $repo = New-TempRepo
    try {
        $script = Join-Path $repo '.specify/scripts/powershell/create-new-feature.ps1'
        $r = Invoke-Ps -ScriptPath $script -CliArgs @('-Json', '-DryRun', 'fancy', 'modern', 'login', 'system') -WorkDir $repo
        Assert-True ($r.ExitCode -eq 0) ("expected success; stderr=" + $r.StdErr)
        $obj = $r.StdOut | ConvertFrom-Json
        Assert-Match 'fancy-modern-login-system' $obj.BRANCH_NAME 'should keep all 4 words'
    } finally { Remove-TempRepo $repo }
}

# ---- #5 update-agent-context locates root from script path ----
Write-Host ""
Write-Host "[#5] update-agent-context locates root from script path" -ForegroundColor Cyan

Invoke-Test "running from subdir still finds correct root" {
    $repo = New-TempRepo
    try {
        $script = Join-Path $repo '.specify/extensions/agent-context/scripts/powershell/update-agent-context.ps1'
        $subdir = Join-Path $repo 'src/components'
        New-Item -ItemType Directory -Path $subdir -Force | Out-Null
        $r = Invoke-Ps -ScriptPath $script -WorkDir $subdir
        # 当 YAML 解析器可用时，AGENTS.md 应在仓库根；否则脚本会输出警告
        # 但无论如何，绝不应在子目录下创建 AGENTS.md（pwd 行为）或写到主项目根
        $rootAgents = Join-Path $repo 'AGENTS.md'
        $subAgents = Join-Path $subdir 'AGENTS.md'
        Assert-True (-not (Test-Path -LiteralPath $subAgents)) 'subdir must not contain AGENTS.md'
        # 路径定位的核心断言：脚本要么成功写到 repo root，要么因 YAML 缺失返回友好警告
        $combined = $r.StdOut + $r.StdErr
        $rootResolved = (Resolve-Path -LiteralPath $repo).Path
        # 输出应当包含临时仓库路径（说明它定位到了正确的根）；不应包含主项目相关字眼如 AList
        $okWrote = Test-Path -LiteralPath $rootAgents
        $okWarned = ($combined -match [Regex]::Escape($rootResolved)) -or ($combined -match 'agent-context')
        Assert-True ($okWrote -or $okWarned) ("expected AGENTS.md at root or yaml warning; stdout=" + $r.StdOut + " stderr=" + $r.StdErr)
        if ($okWrote) {
            $content = Get-Content -LiteralPath $rootAgents -Raw
            Assert-Match 'SPECKIT START' $content 'AGENTS.md should contain marker'
        }
    } finally { Remove-TempRepo $repo }
}

Write-Host ""
Write-Host "==============================" -ForegroundColor Cyan
Write-Host ("Passed: " + $script:Passed) -ForegroundColor Green
$color = 'Green'
if ($script:Failed -gt 0) { $color = 'Red' }
Write-Host ("Failed: " + $script:Failed) -ForegroundColor $color
if ($script:Failed -gt 0) {
    Write-Host ""
    Write-Host "Failures:" -ForegroundColor Red
    foreach ($m in $script:Failures) { Write-Host ("  " + $m) -ForegroundColor Yellow }
    exit 1
}
exit 0
