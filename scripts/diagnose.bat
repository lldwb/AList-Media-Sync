@echo off
REM ============================================================
REM AList-Media-Sync 一键诊断脚本（Windows）
REM ============================================================
REM 用法：
REM   diagnose.bat                                  默认输出至 .\diagnostics\latest
REM   diagnose.bat --output X:\diag --max-lines 500 自定义输出目录与日志行数
REM   diagnose.bat --trace-id manual-test-001       指定 traceId
REM
REM 退出码：
REM   0  成功
REM   1  生成失败
REM   2  部分信息不可用
REM ============================================================

setlocal enabledelayedexpansion

REM ---- 路径解析 ----
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM 脚本可能位于项目根 scripts/ 或一体化包根
if exist "%SCRIPT_DIR%\..\pom.xml" (
    pushd "%SCRIPT_DIR%\.." >nul
    set "PROJECT_ROOT=!CD!"
    popd >nul
) else (
    set "PROJECT_ROOT=%SCRIPT_DIR%"
)
cd /d "%PROJECT_ROOT%"

REM ---- 默认参数 ----
set "OUTPUT_DIR=.\diagnostics\latest"
set "TRACE_ID="
set "MAX_LINES=2000"

REM ---- 解析参数 ----
:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="--output" (
    set "OUTPUT_DIR=%~2"
    shift & shift & goto parse_args
)
if /i "%~1"=="--trace-id" (
    set "TRACE_ID=%~2"
    shift & shift & goto parse_args
)
if /i "%~1"=="--max-lines" (
    set "MAX_LINES=%~2"
    shift & shift & goto parse_args
)
echo [警告] 未知参数：%~1
shift
goto parse_args

:args_done

if not defined LOG_PATH set "LOG_PATH=.\logs"
if not defined DATA_DIR set "DATA_DIR=.\data"

REM ---- 生成 traceId ----
if "%TRACE_ID%"=="" (
    for /f "tokens=2 delims==" %%I in ('"wmic os get localdatetime /value" 2^>nul ^| find "="') do set "LDT=%%I"
    set "TRACE_ID=diag-!LDT:~0,14!-%RANDOM%"
)

echo [信息] 诊断 traceId：%TRACE_ID%
echo [信息] 输出目录：%OUTPUT_DIR%
echo [信息] 日志目录：%LOG_PATH%

REM ---- 准备临时目录 ----
for %%I in ("%OUTPUT_DIR%") do set "OUTPUT_PARENT=%%~dpI"
set "TMP_DIR=%OUTPUT_PARENT%tmp-%RANDOM%"
mkdir "%TMP_DIR%" 2>nul
mkdir "%TMP_DIR%\logs" 2>nul
mkdir "%TMP_DIR%\config" 2>nul

set "MISSING="

REM ---- 拷贝日志摘录（PowerShell tail） ----
call :copy_log_tail "%LOG_PATH%\error.log" "%TMP_DIR%\logs\error.log"
call :copy_log_tail "%LOG_PATH%\app.log" "%TMP_DIR%\logs\app.log"

REM ---- 收集环境信息 ----
(
    echo 诊断 traceId: %TRACE_ID%
    echo 生成时间: %DATE% %TIME%
    if exist "%PROJECT_ROOT%\runtime\bin\java.exe" (
        echo 部署形态: 一体化启动包
    ) else (
        echo 部署形态: 本地开发
    )
    echo 操作系统: %OS% %PROCESSOR_ARCHITECTURE%
    echo CPU 核心: %NUMBER_OF_PROCESSORS%
    echo.
    echo 环境变量（敏感字段已脱敏）:
) > "%TMP_DIR%\environment.txt"

REM 列出环境变量并脱敏
powershell.exe -NoProfile -Command "Get-ChildItem env: | Sort-Object Name | ForEach-Object { $n=$_.Name; $v=$_.Value; if ($n -match '(?i)password^|passwd^|pwd^|token^|secret^|credential^|authorization^|auth^|cookie^|session^|apikey^|api[-_]key^|privatekey^|private[-_]key^|accesskey^|access[-_]key^|salt^|signature^|cryptokey^|crypto[-_]key') { '  ' + $n + '=***REDACTED***' } else { '  ' + $n + '=' + $v } }" >> "%TMP_DIR%\environment.txt" 2>nul

REM ---- 生成配置摘要 ----
(
    echo {
    echo   "sourceFiles": [
    if exist "%PROJECT_ROOT%\src\main\resources\application.yaml" echo     "src/main/resources/application.yaml",
    if exist "%PROJECT_ROOT%\config\application.yaml" echo     "config/application.yaml",
    echo     "_end_"
    echo   ],
    echo   "redactionNote": "脚本采集到的配置文件值已被脱敏。详细配置请参考应用内 /api/diagnostics/run 输出。"
    echo }
) > "%TMP_DIR%\config\config.redacted.json"

REM ---- 生成 summary.md ----
(
    echo # 诊断摘要
    echo.
    echo ## 基本信息
    echo - 生成时间：%DATE% %TIME%
    if exist "%PROJECT_ROOT%\runtime\bin\java.exe" (
        echo - 部署形态：一体化启动包
    ) else (
        echo - 部署形态：本地开发
    )
    echo - Trace ID：%TRACE_ID%
    echo.
    echo ## 最近一次失败
    if exist "%TMP_DIR%\logs\error.log" (
        echo - Trace ID：参见 logs/error.log 中最新错误条目
    ) else (
        echo - 未发现近期错误日志
    )
    echo.
    echo ## 关键证据
    echo - 错误日志：logs/error.log
    echo - 应用日志：logs/app.log
    echo - 配置摘要：config/config.redacted.json
    echo - 环境摘要：environment.txt
    echo.
    echo ## 缺失信息
    if "!MISSING!"=="" (
        echo - 无
    ) else (
        echo !MISSING!
    )
    echo.
    echo ## 建议下一步
    echo - 通过响应头 X-Trace-Id 或上述 Trace ID 串联日志
    echo - 如服务在运行，可调用 POST /api/diagnostics/run 获取更完整的诊断包
) > "%TMP_DIR%\summary.md"

REM ---- 原子替换 latest ----
if exist "%OUTPUT_DIR%" rmdir /s /q "%OUTPUT_DIR%"
move "%TMP_DIR%" "%OUTPUT_DIR%" >nul

if "!MISSING!"=="" (
    echo 诊断包已生成：%OUTPUT_DIR%
    echo 摘要文件：%OUTPUT_DIR%\summary.md
    exit /b 0
) else (
    echo 诊断包已生成但信息不完整：%OUTPUT_DIR%
    echo 摘要文件：%OUTPUT_DIR%\summary.md
    echo 缺失信息：!MISSING!
    exit /b 2
)

REM ============================================================
REM 子例程
REM ============================================================

:copy_log_tail
    if not exist "%~1" (
        set "MISSING=!MISSING! [%~1 不存在或不可读]"
        exit /b 0
    )
    powershell.exe -NoProfile -Command "Get-Content -Path '%~1' -Tail %MAX_LINES% | ForEach-Object { $_ -replace '(?i)(Authorization:\s*)\S+','$1***REDACTED***' -replace '(?i)(Cookie:\s*)\S+','$1***REDACTED***' -replace '(?i)(\"(password^|token^|secret^|key^|cookie^|authorization)[^\"]*\"\s*:\s*\")[^\"]*\"','$1***REDACTED***\"' -replace '(?i)([?&](password^|token^|secret^|key^|cookie^|authorization)[^=]*=)[^&\s]*','$1***REDACTED***' } | Set-Content -Path '%~2' -Encoding UTF8" 2>nul
    exit /b 0
