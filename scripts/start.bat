@echo off
REM ============================================================
REM AList-Media-Sync Windows 启动脚本
REM ============================================================
REM 用法：
REM   start.bat          前台启动（双击或命令行）
REM
REM 环境变量（可通过 set 设置或启动前定义）：
REM   SERVER_PORT=8080           应用监听端口
REM   DATA_DIR=.\data            数据目录路径
REM   LOGGING_LEVEL=INFO         日志级别
REM   JAVA_OPTS=-Xms128m -Xmx256m  JVM 额外参数
REM   DISK_SPACE_THRESHOLD_MB=100    磁盘空间警告阈值（MB）
REM   APP_AUTH_PASSWORD          覆盖配置文件中的认证密码
REM   CI=true                    非交互模式（跳过确认提示）
REM
REM 退出码：
REM   0   正常退出
REM   1   预检查失败
REM   2   用户拒绝继续
REM ============================================================

setlocal enabledelayedexpansion

REM ---- 路径解析 ----
REM 通过脚本自身位置定位启动包根目录
set "SCRIPT_DIR=%~dp0"
REM 去掉尾部反斜杠
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
cd /d "%SCRIPT_DIR%"

REM ---- 默认环境变量 ----
if not defined SERVER_PORT set "SERVER_PORT=8080"
if not defined DATA_DIR set "DATA_DIR=.\data"
if not defined LOGGING_LEVEL set "LOGGING_LEVEL=INFO"
if not defined JAVA_OPTS set "JAVA_OPTS=-Xms128m -Xmx256m"
if not defined DISK_SPACE_THRESHOLD_MB set "DISK_SPACE_THRESHOLD_MB=100"

REM 处理 Windows 终端编码（chcp 65001 = UTF-8）
chcp 65001 >nul 2>&1

echo.
echo ========================================
echo   AList-Media-Sync 一体化启动包
echo ========================================
echo.

set START_TIME=%TIME%

echo [信息] 正在执行启动前检查...

REM ---- 预检查 1：JRE 存在性与架构匹配 ----
call :check_jre
if errorlevel 1 exit /b 1

REM ---- 预检查 2：配置文件存在性 ----
call :check_config
if errorlevel 1 exit /b 1

REM ---- 预检查 3：端口占用检测 ----
call :check_port
if errorlevel 1 exit /b 1

REM ---- 预检查 4：磁盘空间检测 ----
call :check_disk
if errorlevel 1 exit /b 1

REM ---- 预检查 5：数据目录写入权限 ----
call :check_write_permission
if errorlevel 1 exit /b 1

REM ---- 预检查 6：已有实例检测 ----
call :check_existing_instance
if errorlevel 1 exit /b 1

REM ---- 查找 JAR 文件 ----
call :find_jar
if errorlevel 1 exit /b 1

echo [信息] 所有预检查通过。

REM 创建日志目录
if not exist "%SCRIPT_DIR%\logs" mkdir "%SCRIPT_DIR%\logs"

REM 写入 PID 文件（写入 data/ 目录，支持多实例数据目录互斥）
set PID_FILE=%DATA_DIR%\app.pid
echo !PID! > "!PID_FILE!"

REM 组装 JVM 参数
set "JVM_ARGS=%JAVA_OPTS% -Dserver.port=%SERVER_PORT% -Dapp.data-dir=%DATA_DIR% -Dlogging.level.root=%LOGGING_LEVEL%"

REM 如果设置了 APP_AUTH_PASSWORD 环境变量，传递给 JVM
if defined APP_AUTH_PASSWORD set "JVM_ARGS=%JVM_ARGS% -Dapp.auth.password=%APP_AUTH_PASSWORD%"

echo [信息] 正在启动应用...
echo.

REM 启动 Java 进程（前台运行）
"%JAVA_EXEC%" %JVM_ARGS% -jar "%JAR_FILE%" 2>&1

REM 清理 PID 文件
if exist "!PID_FILE!" del "!PID_FILE!" >nul 2>&1

echo [信息] 应用已关闭。
exit /b 0

REM ============================================================
REM 预检查函数
REM ============================================================

:check_jre
    REM 检测系统架构
    if /i not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
        if /i not "%PROCESSOR_ARCHITECTURE%"=="EM64T" (
            echo [错误] 当前系统架构为 %PROCESSOR_ARCHITECTURE%，本启动包仅支持 x86_64 架构。
            echo 建议：请下载对应架构的启动包，或使用 Docker 部署方案。
            exit /b 1
        )
    )

    set "JAVA_EXEC=%SCRIPT_DIR%\runtime\bin\java.exe"

    REM 检查内置 JRE
    if exist "%JAVA_EXEC%" (
        echo [信息] 使用内置 JRE
        exit /b 0
    )

    REM 回退到系统 Java
    where java >nul 2>&1
    if not errorlevel 1 (
        for /f "delims=" %%i in ('where java') do set "JAVA_EXEC=%%i"
        echo [警告] 未找到内置 JRE，使用系统 Java。
        exit /b 0
    )

    echo [错误] 未检测到 Java 运行环境。
    echo 建议：本启动包已内置 JRE，但似乎在解压或复制过程中丢失。请重新下载完整的启动包。
    exit /b 1

:check_config
    set "CONFIG_FILE=%SCRIPT_DIR%\config\application.yaml"
    if not exist "%CONFIG_FILE%" (
        echo [错误] 配置文件 config\application.yaml 不存在或格式错误。
        echo 建议：请检查 config\ 目录下的配置文件是否完整，可从模板文件 config\application.template.yaml 复制一份并修改。
        exit /b 1
    )
    exit /b 0

:check_port
    REM 使用 netstat 检测端口占用
    for /f "tokens=5" %%i in ('netstat -ano ^| findstr ":%SERVER_PORT% " ^| findstr "LISTENING" 2^>nul') do (
        set "OCC_PID=%%i"
        goto :port_occupied
    )
    exit /b 0

:port_occupied
    REM 获取进程名称
    set "PROC_NAME=未知"
    for /f "tokens=1,2 delims=," %%a in ('tasklist /fi "PID eq !OCC_PID!" /fo csv /nh 2^>nul') do (
        set "PROC_NAME=%%a"
        set "PROC_NAME=!PROC_NAME:"=!"
    )
    echo [错误] 端口 %SERVER_PORT% 已被进程 !PROC_NAME! (PID: !OCC_PID!) 占用。
    echo 建议：1^) 修改 config\application.yaml 中的 server.port 配置项；
    echo       2^) 或终止占用进程后重试（taskkill /PID !OCC_PID!）。
    exit /b 1

:check_disk
    REM 确保数据目录存在以进行磁盘检查
    if not exist "%DATA_DIR%" mkdir "%DATA_DIR%" >nul 2>&1

    REM 获取数据目录所在磁盘驱动器号
    set "DATA_DRIVE=%~d0"
    REM 如果 DATA_DIR 是绝对路径，取其驱动器号；否则使用脚本所在驱动器
    for /f "tokens=1 delims=:" %%d in ("%DATA_DIR%") do (
        if "%%d" neq "." if "%%d" neq ".." set "DATA_DRIVE=%%d:"
    )

    REM 使用 wmic 获取磁盘剩余空间
    for /f "tokens=2 delims==" %%d in ('wmic logicaldisk where "DeviceID='%DATA_DRIVE%'" get FreeSpace /format:value 2^>nul') do (
        set "FREE_BYTES=%%d"
    )

    if not defined FREE_BYTES exit /b 0

    REM 将字节转换为 MB
    set /a FREE_MB=%FREE_BYTES% / 1048576

    if %FREE_MB% LSS %DISK_SPACE_THRESHOLD_MB% (
        echo [警告] 数据目录所在磁盘剩余空间不足 %DISK_SPACE_THRESHOLD_MB%MB（当前仅 %FREE_MB%MB），长期运行可能导致数据库写入失败。
        echo 建议：清理磁盘空间或将数据目录迁移到空间充足的磁盘。

        REM 非交互模式检测
        if "%CI%"=="true" (
            echo [信息] 检测到非交互模式，自动退出。
            exit /b 2
        )

        choice /c YN /m "是否继续启动？[Y/N]"
        if errorlevel 2 (
            echo [信息] 用户取消启动。
            exit /b 2
        )
        echo [信息] 用户选择继续启动。
    )
    exit /b 0

:check_write_permission
    REM 确保数据目录存在
    if not exist "%DATA_DIR%" mkdir "%DATA_DIR%" >nul 2>&1

    REM 通过尝试创建并写入临时文件来检测写入权限
    set "TEST_FILE=%DATA_DIR%\.write_test"
    (echo test > "!TEST_FILE!") >nul 2>&1
    if exist "!TEST_FILE!" (
        del "!TEST_FILE!" >nul 2>&1
        exit /b 0
    )

    echo [错误] 数据目录 %DATA_DIR% 无写入权限。
    echo 建议：请检查目录权限，或将数据目录迁移到有写入权限的路径。
    exit /b 1

:check_existing_instance
    set "PID_FILE=%DATA_DIR%\app.pid"

    if not exist "%PID_FILE%" exit /b 0

    set /p OLD_PID=<"%PID_FILE%"
    if "!OLD_PID!"=="" exit /b 0

    REM 检查该 PID 的进程是否仍在运行
    tasklist /fi "PID eq !OLD_PID!" 2>nul | findstr /i "!OLD_PID!" >nul
    if errorlevel 1 (
        REM PID 文件是陈旧的（进程已退出），清理
        del "%PID_FILE%" >nul 2>&1
        exit /b 0
    )

    echo [警告] 检测到已有实例正在运行（PID: !OLD_PID!）。

    REM 非交互模式检测
    if "%CI%"=="true" (
        echo [信息] 检测到非交互模式，自动退出。
        exit /b 1
    )

    choice /c YN /m "是否强制重启？[Y/N]"
    if errorlevel 2 (
        echo [信息] 用户取消启动。
        exit /b 2
    )

    echo [信息] 正在终止旧进程（PID: !OLD_PID!）...
    taskkill /PID !OLD_PID! /F >nul 2>&1
    timeout /t 2 /nobreak >nul
    echo [信息] 旧进程已终止，继续启动。
    exit /b 0

:find_jar
    REM 在 lib/ 目录下查找 JAR 文件
    set "JAR_FILE="
    for %%f in ("%SCRIPT_DIR%\lib\*.jar") do (
        if "!JAR_FILE!"=="" set "JAR_FILE=%%f"
    )
    if "!JAR_FILE!"=="" (
        echo [错误] 未找到应用 JAR 文件（lib\*.jar）。
        echo 建议：请检查 lib\ 目录下的 JAR 文件是否完整，可能需要重新下载启动包。
        exit /b 1
    )
    exit /b 0
