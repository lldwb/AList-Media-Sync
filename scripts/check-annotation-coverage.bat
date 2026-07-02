@echo off
REM ============================================================
REM check-annotation-coverage.bat — OpenAPI 注解覆盖率校验（Windows）
REM ============================================================
setlocal enabledelayedexpansion

set REPO_ROOT=%~dp0..
set SRC_DIR=%REPO_ROOT%\src\main\java\top\lldwb\alistmediasync
set EXIT_CODE=0
set MISSING_COUNT=0

echo ==^> 扫描 Controller 注解覆盖率...
for /r "%SRC_DIR%" %%F in (*Controller.java) do (
    findstr /C:"@Tag" "%%F" >nul 2>&1
    if errorlevel 1 (
        echo 缺失 @Tag（类级）：%%F
        set EXIT_CODE=1
        set /a MISSING_COUNT+=1
    )
    findstr /R /C:"@.*Mapping" "%%F" >nul 2>&1
    if not errorlevel 1 (
        findstr /C:"@Operation" "%%F" >nul 2>&1
        if errorlevel 1 (
            echo 缺失 @Operation（方法）：%%F
            set EXIT_CODE=1
            set /a MISSING_COUNT+=1
        )
    )
)

echo ==^> 扫描 DTO 注解覆盖率...
for /r "%SRC_DIR%" %%F in (*.java) do (
    echo %%F | findstr /C:"\dto\" >nul 2>&1
    if not errorlevel 1 (
        findstr /C:"@Schema" "%%F" >nul 2>&1
        if errorlevel 1 (
            findstr /R /C:"class \|record \|enum " "%%F" >nul 2>&1
            if not errorlevel 1 (
                echo 缺失 @Schema（类级）：%%F
                set EXIT_CODE=1
                set /a MISSING_COUNT+=1
            )
        )
    )
)

echo ----------------------------------------
if %MISSING_COUNT% equ 0 (
    echo ✓ 所有 Controller/DTO 注解覆盖率 100%%
) else (
    echo ✗ 发现 %MISSING_COUNT% 处注解缺失
)
exit /b %EXIT_CODE%
