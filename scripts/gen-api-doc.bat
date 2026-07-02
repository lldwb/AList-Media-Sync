@echo off
REM ============================================================
REM gen-api-doc.bat — API 文档自动生成脚本（Windows）
REM ============================================================
REM 5 阶段流水线（详见 specs/011-docs-system-optimization/contracts/api-doc-generation-contract.md §1）
REM 行为契约：C-001/C-003/C-005/C-006，生成区规则 R-Gen-001/R-Gen-002
REM ============================================================
setlocal enabledelayedexpansion

set REPO_ROOT=%~dp0..
set OPENAPI_YAML=%REPO_ROOT%\target\openapi.yaml
set DOC_FILE=%REPO_ROOT%\docs\05-API接口文档.md
set START_MARKER=<!-- GENERATED START -->
set END_MARKER=<!-- GENERATED END -->

echo ==^> 阶段 1-4：Maven 静态导出 OpenAPI
cd /d "%REPO_ROOT%"
call mvnw.cmd verify -Pgen-api-doc -DskipTests
if errorlevel 1 (
    echo 错误：Maven 构建失败 >&2
    exit /b 1
)

REM C-005：校验 openapi.yaml 非空
if not exist "%OPENAPI_YAML%" (
    echo 错误：%OPENAPI_YAML% 不存在 >&2
    exit /b 1
)

echo ==^> 阶段 5：转换 OpenAPI YAML ^> Markdown
set TMP_MD=%TEMP%\openapi-md-%RANDOM%.md
where openapi-to-md >nul 2>&1
if errorlevel 1 (
    where npx >nul 2>&1
    if errorlevel 1 (
        echo 错误：未找到 openapi-to-md，且 npx 不可用。请先执行 npm install -g openapi-to-md >&2
        exit /b 1
    )
    call npx --yes openapi-to-md -i "%OPENAPI_YAML%" -o "%TMP_MD%"
) else (
    openapi-to-md -i "%OPENAPI_YAML%" -o "%TMP_MD%"
)
if errorlevel 1 (
    echo 错误：openapi-to-md 转换失败 >&2
    exit /b 1
)

REM R-Gen-002：校验 docs/05 锚点存在
if not exist "%DOC_FILE%" (
    echo 错误：%DOC_FILE% 不存在。请先由 T021 创建含手工引导区与锚点的骨架文件。 >&2
    exit /b 1
)

findstr /C:"%START_MARKER%" "%DOC_FILE%" >nul 2>&1
if errorlevel 1 (
    echo 错误：%DOC_FILE% 缺少 GENERATED START 锚点 >&2
    exit /b 1
)
findstr /C:"%END_MARKER%" "%DOC_FILE%" >nul 2>&1
if errorlevel 1 (
    echo 错误：%DOC_FILE% 缺少 GENERATED END 锚点 >&2
    exit /b 1
)

echo ==^> 阶段 5b：重写生成区（保留手工引导区）
REM 生成区重写依赖 PowerShell 精确替换锚点间内容
powershell -NoProfile -Command ^
  "$doc = Get-Content -Raw -Path '%DOC_FILE%';" ^
  "$gen = Get-Content -Raw -Path '%TMP_MD%';" ^
  "$time = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz';" ^
  "$block = '%START_MARKER%' + \"`n> 本区块由 scripts/gen-api-doc 自动生成，请勿手工编辑。`n> 源真值位于 src/main/java/top/lldwb/alistmediasync/**/controller/*.java`n> 与 **/dto/*.java 的 OpenAPI 注解中。`n> 最后生成时间：\" + $time + \"`n`n\" + $gen + \"`n%END_MARKER%'\";" ^
  "$pattern = '(?s)(%START_MARKER%)(.*?)(%END_MARKER%)';" ^
  "$new = [regex]::Replace($doc, $pattern, [System.Text.RegularExpressions.Regex]::Escape($block).Replace('\','\\'));" ^
  "Set-Content -Path '%DOC_FILE%' -Value $new -NoNewline -Encoding UTF8"

if errorlevel 1 (
    echo 错误：生成区重写失败 >&2
    del "%TMP_MD%" 2>nul
    exit /b 1
)

del "%TMP_MD%" 2>nul
echo ==^> 完成：%DOC_FILE% 生成区已更新
endlocal
