# 研究文档：一体化启动包

**功能分支**：`005-standalone-bootstrap`
**日期**：2026-06-20

## 研究任务清单

| # | 研究主题 | 来源 | 状态 |
|---|---------|------|------|
| 1 | Adoptium API JRE 下载方式 | 技术上下文 — JRE 获取 | ✅ 已解决 |
| 2 | 密码加密实现时机选择 | 技术上下文 — EnvironmentPostProcessor vs ApplicationContextInitializer | ✅ 已解决 |
| 3 | 容器环境检测方式 | 技术上下文 — Docker 环境判定 | ✅ 已解决 |
| 4 | Maven 打包插件选型 | 技术上下文 — 打包产物生成 | ✅ 已解决 |
| 5 | 端口检测跨平台实现 | 技术上下文 — 启动脚本预检查 | ✅ 已解决 |
| 6 | 磁盘空间检测阈值 | 技术上下文 — 配置化阈值 | ✅ 已解决 |
| 7 | 已有实例检测 | 技术上下文 — PID 文件与端口双重检测 | ✅ 已解决 |
| 8 | JRE 架构匹配检测 | 技术上下文 — 启动脚本预检查 | ✅ 已解决 |

---

## 决策 1：JRE 下载方式 — Adoptium API v3

**决策**：使用 Adoptium API v3 的 `/v3/binary/latest/` 端点下载 Eclipse Temurin JRE 21。

**理由**：
- Adoptium API v3 无需认证即可访问，支持稳定 URL 模式
- 提供 JRE（非 JDK）下载以减少启动包体积（JRE ≈ 40MB vs JDK ≈ 180MB）
- URL 模式：`https://api.adoptium.net/v3/binary/latest/21/ga/{os}/{arch}/jre/hotspot/normal/eclipse`
- 支持 SHA-256 校验和验证（同一 URL 追加 `.sha256.txt`）
- Adoptium Temurin 允许重新分发（Eclipse 许可）

**考虑的替代方案**：
- **Oracle JDK**：需认证，许可限制多，体积大
- **Microsoft OpenJDK**：API 不稳定，下载流程较复杂
- **Amazon Corretto**：API 格式不统一，需额外解析 JSON 获取下载链接
- **手动预置 JRE**：需开发者手动下载放置，打包脚本体检查不存在时警告

**打包脚本实现要点**：
```bash
# Linux x64
API_URL="https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jre/hotspot/normal/eclipse"
FETCH_URL=$(curl -sL -w '%{redirect_url}' "$API_URL" -o /dev/null)
curl -L "$FETCH_URL" -o runtime/jre.tar.gz
# 校验 SHA-256
curl -sL "${FETCH_URL}.sha256.txt" | sha256sum -c
```

---

## 决策 2：密码加密实现层 — EnvironmentPostProcessor

**决策**：使用 `EnvironmentPostProcessor` 在 Spring Boot 环境准备阶段对密码进行检测与加密。

**理由**：
- `EnvironmentPostProcessor` 在 `ConfigDataEnvironment` 之后、`ApplicationContext` 创建之前执行，是 Spring Boot 最早的可扩展点之一
- 可以对 `Environment` 中的属性值进行读取和修改，使加密后的值立即可用于 Bean 绑定
- 相比 `ApplicationContextInitializer`，`EnvironmentPostProcessor` 不需要内部 `ApplicationContext` 引用，更加解耦
- 通过 `spring.factories` 或 `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor` 注册，无侵入性
- 可在此阶段读取 `Environment` 中的属性值并修改，使加密后的值立即可用于 Bean 绑定

**考虑的替代方案**：
- **`ApplicationContextInitializer`**：也可实现类似功能，但需访问内部容器，耦合度更高
- **`ApplicationListener<ApplicationPreparedEvent>`**：时机太晚，此时 `AppProperties` Bean 已创建且 `@ConfigurationProperties` 已绑定
- **启动脚本层面的 `sed`/PowerShell 替换**：跨平台兼容性差，YAML 解析不可靠，不符合 A6（Java 层面的启动后处理用 Java 实现）
- **Jasypt**：第三方依赖，违反 YAGNI 原则，且新版本对 Spring Boot 4.x 兼容性不确定

**实现方案要点**：
1. 实现 `PasswordEncryptionPostProcessor implements EnvironmentPostProcessor`
2. 读取 `app.auth.password` 属性值
3. 判断是否以 `{bcrypt}` 开头 — 若是则跳过
4. 若不是（明文或空值）：
   - 明文：使用 `BCryptPasswordEncoder` 加密并生成 `{bcrypt}$2a$...` 格式
   - 空值：仅记录警告日志
5. 将加密后的值设置到 `Environment` 的 `app.auth.password` 属性，确保后续 `AppProperties` 绑定到已加密值
6. **加密值仅保存在内存中的 Spring Environment，绝不回写到 YAML 文件**

---

## 决策 3：容器环境检测 — 多条件叠加判断

**决策**：使用组合检测策略：① 检查 `/.dockerenv` 文件存在性；② 检查是否有 `DOCKER_CONTAINER` 环境变量；③ 作为最后手段，检查 `/proc/1/cgroup` 中是否包含 `docker`/`containerd` 关键字。

**理由**：
- `/.dockerenv` 是 Docker 引擎在所有 Linux 容器中自动创建的标记文件，检测开销最小
- Windows 容器不包含 `/.dockerenv`（但本项目 Docker 镜像基于 `eclipse-temurin:21-jre-alpine`，运行于 Linux 容器，此限制不相关）
- 叠加 `DOCKER_CONTAINER` 环境变量检测提供兜底（某些容器运行时（如 Podman）可能未创建 `.dockerenv`）
- `/proc/1/cgroup` 检查作为最后手段，在 cgroup v2 环境中可能无 `docker` 字符串

**考虑的替代方案**：
- **仅检查 `.dockerenv`**：简单但不够健壮（部分非 Docker 的 OCI 运行时未必创建此文件）
- **通过环境变量注入**：需要修改 Dockerfile，增加 `ENV` 指令。但这与我们"复用现有 Docker 构建"的原则冲突，最小化变更
- **Spring Boot `CloudPlatform` 检测**：`CloudPlatform.KUBERNETES` 可检测 K8s 但不检测普通 Docker，不满足需求

**Java 端实现**：
```java
public static boolean isRunningInContainer() {
    // 方法1：检查 .dockerenv
    if (Files.exists(Path.of("/.dockerenv"))) return true;
    // 方法2：检查环境变量
    if (System.getenv("DOCKER_CONTAINER") != null) return true;
    // 方法3：检查 cgroup
    try {
        String cgroup = Files.readString(Path.of("/proc/1/cgroup"));
        return cgroup.contains("docker") || cgroup.contains("containerd");
    } catch (Exception e) {
        return false;
    }
}
```

此方法放置于 `ServerAddressLogger.java` 或新建的 `ContainerDetector.java` 工具类。

---

## 决策 4：Maven 打包方案 — Assembly Plugin

**决策**：使用 Apache Maven Assembly Plugin + 自定义 Assembly Descriptor 生成启动包归档。

**理由**：
- Assembly Plugin 是 Maven 生态中标配的打包工具，成熟稳定
- 通过自定义 `assembly.xml` 描述符可精确控制目录结构、文件集、权限
- 支持生成 `.zip` 和 `.tar.gz` 格式（通过不同 `<format>` 配置）
- 可通过 `<dependencySets>` 自动包含项目 JAR 及运行时依赖
- 可通过 `<fileSets>` 指定外部目录（如 JRE 解压后的 `runtime/`、启动脚本、配置文件）
- 可用 `<outputFileNameMapping>` 灵活控制文件名

**考虑的替代方案**：
- **Maven Wrapper + Shell 脚本**：简单直接但不够声明式，难以在 Maven 生命周期中集成，跨平台兼容性差
- **Gradle Application Plugin**：项目使用 Maven，引入 Gradle 违反 YAGNI
- **jlink + jpackage**：仅适用于 JDK 自身模块化，对第三方 Spring Boot 应用不适用
- **Spring Boot Maven Plugin `executable`**：仅生成可执行 JAR，不包含 JRE 打包
- **Maven Dependency Plugin + AntRun**：可行但配置冗长，Assembly Plugin 更声明式

**Assembly Descriptor 结构**：
```xml
<assembly>
  <id>bootstrap</id>
  <formats>
    <format>zip</format><!-- Windows -->
    <format>tar.gz</format><!-- Linux -->
  </formats>
  <includeBaseDirectory>true</includeBaseDirectory>
  <baseDirectory>alist-media-sync</baseDirectory>
  
  <fileSets>
    <!-- 启动脚本 -->
    <fileSet><directory>${project.basedir}/scripts</directory>
      <outputDirectory>/</outputDirectory><fileMode>0755</fileMode></fileSet>
    <!-- JRE -->
    <fileSet><directory>${project.build.directory}/runtime</directory>
      <outputDirectory>runtime/</outputDirectory></fileSet>
    <!-- 配置文件 -->
    <fileSet><directory>${project.basedir}/src/main/resources</directory>
      <includes><include>application.yaml</include></includes>
      <outputDirectory>config/</outputDirectory></fileSet>
    <!-- 数据目录模板 -->
    <fileSet><directory>${project.build.directory}/data-template</directory>
      <outputDirectory>data/</outputDirectory></fileSet>
  </fileSets>
  
  <dependencySets>
    <dependencySet>
      <outputDirectory>lib</outputDirectory>
      <includes><include>${project.groupId}:${project.artifactId}</include></includes>
    </dependencySet>
  </dependencySets>
</assembly>
```

启动脚本中引用方式：
```bash
# start.sh
SCRIPT_DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
JAVA_EXEC="$SCRIPT_DIR/runtime/bin/java"
JAR_FILE="$SCRIPT_DIR/lib/$(ls "$SCRIPT_DIR/lib/"*.jar | head -1)"
exec "$JAVA_EXEC" -jar "$JAR_FILE"
```

---

## 决策 5：端口检测 — 脚本层实现，平台分叉

**决策**：在启动脚本中通过操作系统原生工具检测端口占用。

**理由**：
- 在 Java 进程启动之前执行，避免 Tomcat 绑定失败后的错误堆栈
- 符合功能规格 FR-008 的要求（预检查在启动 Java 进程前执行）
- 可直接向用户输出友好信息

**Windows（`start.bat`）实现**：
```batch
netstat -ano | findstr ":8080 " | findstr "LISTENING"
:: 解析 PID：for /f "tokens=5" %%i in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do set PID=%%i
:: 解析进程名：tasklist /fi "PID eq %PID%" /fo csv /nh
```

**Linux（`start.sh`）实现**：
```bash
PORT=8080
PID=$(ss -tlnp "sport = :$PORT" 2>/dev/null | awk 'NR>1 {print $NF}' | grep -oP '(?<=pid=)\d+')
if [ -n "$PID" ]; then
    PROC_NAME=$(ps -p "$PID" -o comm= 2>/dev/null)
    echo "端口 $PORT 已被进程 $PROC_NAME (PID: $PID) 占用。"
fi
```

**考虑的替代方案**：
- **Java 内部检测**：通过尝试绑定 `ServerSocket`，虽然可行但需启动 JVM，开销大且错误信息不够友好
- **`nc -z localhost $PORT` 仅检测端口是否开放**：无法区分是应用自身还是其他进程占用

---

## 决策 6：磁盘空间检测 — 脚本层实现，阈值可配置

**决策**：在启动脚本中检查数据目录所在磁盘的剩余空间，阈值默认 100MB。

**理由**：
- 简单可靠，使用系统通用命令
- 通过读取 `application.yaml` 中的 `DISK_SPACE_THRESHOLD` 或环境变量实现可配置

**Linux 实现**：
```bash
DATA_DIR="${DATA_DIR:-./data}"
THRESHOLD_MB="${DISK_SPACE_THRESHOLD:-100}"
AVAIL_KB=$(df "$DATA_DIR" 2>/dev/null | awk 'NR==2 {print $4}')
AVAIL_MB=$((AVAIL_KB / 1024))
if [ "$AVAIL_MB" -lt "$THRESHOLD_MB" ]; then
    echo "数据目录所在磁盘剩余空间不足 ${THRESHOLD_MB}MB..."
    # 交互模式：询问；非交互：退出
fi
```

**Windows 实现**（PowerShell 或 WMIC）：
```batch
for /f "tokens=2 delims=:" %%d in ('wmic logicaldisk where "DeviceID='%DATA_DRIVE%'" get FreeSpace /format:value 2^>nul') do set FREE_BYTES=%%d
```

---

## 决策 7：已有实例检测 — PID 文件 + 端口状态双重判断

**决策**：使用 PID 文件（启动时写入 `data/app.pid`，关闭时删除）+ 端口检测双重判断。

**理由**：
- PID 文件是最准确的方法 — 可精确判断是否为同一应用
- 端口检测作为补充——即使 PID 文件被手动删除，端口占用仍可发现
- PID 文件进程状态检测（`kill -0` / `tasklist`）可判断进程是否确实存活

**实现**：
```bash
# Linux: start.sh
PID_FILE="$DATA_DIR/app.pid"
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "检测到已有实例正在运行（PID: $OLD_PID）"
        # 询问是否强制重启
    else
        # PID 文件是陈旧的（进程已退出），清理
        rm -f "$PID_FILE"
    fi
fi
# 启动后写入 PID
echo $$ > "$PID_FILE"
```

**考虑的替代方案**：
- **仅端口检测**：无法区分是同一应用还是其他程序占用端口
- **仅 PID 文件**：如果文件保留但进程已退出，可能导致误判
- **ps 关键字匹配**：不可靠（不同启动方式可能显示不同的进程名）

---

## 决策 8：JRE 架构匹配检测 — 脚本层实现，`uname -m` / `%PROCESSOR_ARCHITECTURE%`

**决策**：在启动脚本中检测当前操作系统架构是否与内置 JRE 匹配。

**理由**：
- 启动包为 x86-64 平台构建，若用户在 ARM 机器上运行将导致 `java` 无法执行
- 在 JRE 存在性检查通过后、JVM 启动前进行架构检测，可给出明确的中文提示而非神秘的"无法执行二进制文件"错误
- `uname -m` 和 `%PROCESSOR_ARCHITECTURE%` 是操作系统标准机制，零额外依赖

**Linux（`start.sh`）实现**：
```bash
ARCH=$(uname -m)
if [ "$ARCH" != "x86_64" ] && [ "$ARCH" != "amd64" ]; then
    echo "[错误] 当前系统架构为 $ARCH，本启动包仅支持 x86_64 架构。"
    echo "建议：请下载对应架构的启动包，或使用 Docker 部署方案。"
    exit 1
fi
```

**Windows（`start.bat`）实现**：
```batch
if /i not "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    echo [错误] 当前系统架构为 %PROCESSOR_ARCHITECTURE%，本启动包仅支持 x86_64 架构。
    echo 建议：请下载对应架构的启动包，或使用 Docker 部署方案。
    exit /b 1
)
```

**考虑的替代方案**：
- **Java 内部检测 `os.arch`**：已太晚，JVM 启动失败后才报错
- **编译时生成多架构包**：v1 范围外，仅预留扩展空间

---

## 总结

所有技术未知项均已通过研究解决。主要技术路线确立如下：

| 领域 | 选型 |
|------|------|
| JRE 获取 | Adoptium API v3 → Temurin JRE 21 |
| 密码加密 | Spring Boot `EnvironmentPostProcessor` + BCrypt |
| 容器检测 | `/.dockerenv` + 环境变量 + cgroup 三重检测 |
| 打包工具 | Maven Assembly Plugin + 自定义 Descriptor |
| 端口检测 | 脚本层 `ss`（Linux）/ `netstat`（Windows） |
| 磁盘检测 | 脚本层 `df`（Linux）/ `wmic`（Windows） |
| 实例检测 | PID 文件 + 端口状态双重判断 |
| 架构检测 | 脚本层 `uname -m`（Linux）/ `%PROCESSOR_ARCHITECTURE%`（Windows） |
