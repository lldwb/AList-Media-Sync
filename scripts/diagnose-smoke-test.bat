@echo off
REM ============================================================
REM diagnose.bat smoke test
REM ============================================================
REM 流程：
REM   1. 在临时目录中准备最小日志样本
REM   2. 执行 diagnose.bat
REM   3. 校验 summary.md 与证据文件存在，敏感字段被脱敏
REM ============================================================

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
pushd "%SCRIPT_DIR%\.." >nul
set "PROJECT_ROOT=%CD%"
popd >nul

set "TMP_BASE=%TEMP%\diagnose-smoke-%RANDOM%"
mkdir "%TMP_BASE%\logs" 2>nul
mkdir "%TMP_BASE%\scripts" 2>nul

copy /Y "%PROJECT_ROOT%\scripts\diagnose.bat" "%TMP_BASE%\scripts\diagnose.bat" >nul

(
    echo 2026-06-27 00:00:00 INFO 启动
    echo 2026-06-27 00:00:01 ERROR 调用失败 Authorization: Bearer SUPER-SECRET-TOKEN-123
    echo {"password":"plaintextpwd","other":"safe"}
) > "%TMP_BASE%\logs\app.log"
copy /Y "%TMP_BASE%\logs\app.log" "%TMP_BASE%\logs\error.log" >nul

cd /d "%TMP_BASE%"
set "LOG_PATH=%TMP_BASE%\logs"
call "%TMP_BASE%\scripts\diagnose.bat" --output "%TMP_BASE%\diagnostics\latest" --max-lines 100
set RC=%ERRORLEVEL%

if not %RC%==0 if not %RC%==2 (
    echo [FAIL] diagnose.bat 返回非预期退出码：%RC%
    rmdir /s /q "%TMP_BASE%" 2>nul
    exit /b 1
)

if not exist "%TMP_BASE%\diagnostics\latest\summary.md" (
    echo [FAIL] summary.md 不存在
    rmdir /s /q "%TMP_BASE%" 2>nul
    exit /b 1
)
if not exist "%TMP_BASE%\diagnostics\latest\logs\error.log" (
    echo [FAIL] logs\error.log 不存在
    rmdir /s /q "%TMP_BASE%" 2>nul
    exit /b 1
)
if not exist "%TMP_BASE%\diagnostics\latest\environment.txt" (
    echo [FAIL] environment.txt 不存在
    rmdir /s /q "%TMP_BASE%" 2>nul
    exit /b 1
)

findstr /C:"SUPER-SECRET-TOKEN-123" "%TMP_BASE%\diagnostics\latest\logs\error.log" >nul
if not errorlevel 1 (
    echo [FAIL] 原始 Token 未脱敏
    rmdir /s /q "%TMP_BASE%" 2>nul
    exit /b 1
)
findstr /C:"plaintextpwd" "%TMP_BASE%\diagnostics\latest\logs\error.log" >nul
if not errorlevel 1 (
    echo [FAIL] 原始 password 未脱敏
    rmdir /s /q "%TMP_BASE%" 2>nul
    exit /b 1
)

echo [PASS] diagnose.bat smoke test 通过
rmdir /s /q "%TMP_BASE%" 2>nul
exit /b 0
