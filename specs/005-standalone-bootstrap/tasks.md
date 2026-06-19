# 任务：一体化启动包

**输入**：来自 `/specs/005-standalone-bootstrap/` 的设计文档

**前提条件**：plan.md（必需）、spec.md（用户故事必需）、research.md、data-model.md、contracts/

**测试**：章程原则 V 要求测试不可省略 — 密码加密和地址输出增强均需单元测试。

**组织方式**：任务按用户故事分组，以支持每个故事的独立实现和测试。

## 格式：`[ID] [P?] [Story] 描述`

- **[P]**：可以并行运行（不同文件，无依赖）
- **[Story]**：此任务属于哪个用户故事（例如，US1、US2、US3、US4、US5）
- 在描述中包含确切的文件路径

## 路径约定

- **单项目**：仓库根目录下的 `src/`、`assembly/`、`scripts/`
- 启动脚本位于项目根目录（与 `mvnw` 同级）
- 打包描述符位于 `assembly/` 子目录

---

## 阶段 1：设置（共享基础设施）

**目的**：项目初始化和基本结构 — 创建目录、准备模板文件

- [ ] T001 在项目根目录创建 `scripts/` 目录和 `assembly/` 目录结构
- [ ] T002 [P] 创建 `src/main/resources/application.template.yaml` 配置模板文件（基于现有 `application.yaml`，注释完整，密码字段留空）
- [ ] T003 [P] 创建 `src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor` SPI 注册文件

---

## 阶段 2：基础（阻塞性前置条件）

**目的**：在任何用户故事可以开始实现之前必须完成的核心基础设施

**⚠️ 关键**：在此阶段完成之前，不能开始任何用户故事的工作

- [ ] T004 在 `pom.xml` 中添加 `bootstrap` Maven Profile，包含 `maven-assembly-plugin` 和 `exec-maven-plugin` 配置（参考 contracts/build-packaging.md 生命周期绑定表）
- [ ] T005 [P] 创建 `assembly/bootstrap.xml` Maven Assembly Descriptor，定义启动包目录结构（参考 data-model.md 实体 1 和 contracts/build-packaging.md 目录结构映射表）
- [ ] T006 [P] 在 `pom.xml` 的 `spring-boot-maven-plugin` 配置中添加 `executable` 和 `layout` 属性（确保可执行 JAR 正常生成）

**检查点**：基础就绪 — Maven 打包骨架已建立，现在可以并行开始用户故事实现

---

## 阶段 3：用户故事 1 — 开箱即用的首次启动（优先级：P1）🎯 MVP

**目标**：用户解压启动包后执行一条命令即可完成服务启动，无需手动安装 Java/Maven/Node.js。启动完成后控制台输出醒目的地址横幅。

**独立测试**：在一台全新安装的操作系统上解压启动包、执行启动脚本，验证服务是否成功启动并能通过浏览器访问管理界面。

### 用户故事 1 的实现

- [ ] T007 [P] [US1] 创建 `scripts/start.sh` Linux 启动脚本（以 `#!/bin/sh` 开头，不使用 bash 专有语法以确保 `sh start.sh` 兼容），实现：路径解析（`readlink -f`，处理符号链接和网络挂载路径）、JRE 检测（含架构匹配检测 `uname -m`，非 x86_64 架构输出明确提示）、配置文件检测、端口检测（`ss`）、磁盘空间检测（`df`）、已有实例检测（PID 文件 + `kill -0`，PID 文件写入 `data/` 目录以支持多实例数据目录互斥）、JVM 启动、PID 写入、Ctrl+C 优雅退出（参考 contracts/startup-scripts.md）
- [ ] T008 [P] [US1] 创建 `scripts/start.bat` Windows 启动脚本，实现：路径解析（`%~dp0`，处理符号链接和 UNC 路径）、JRE 检测（含架构匹配检测 `echo %PROCESSOR_ARCHITECTURE%`，非 AMD64 架构输出明确提示）、配置文件检测、端口检测（`netstat`）、磁盘空间检测（`wmic`）、已有实例检测（`tasklist`，PID 文件写入 `data/` 目录以支持多实例数据目录互斥）、JVM 启动、PID 写入、Ctrl+C 优雅退出（参考 contracts/startup-scripts.md）
- [ ] T009 [US1] 增强 `src/main/java/top/lldwb/alistmediasync/util/ServerAddressLogger.java`：添加应用名称/版本输出、功能路径列表（`/app/`、`/api/`、`/actuator/health`、`/h2-console`）、网络接口名称标注、醒目的启动成功横幅格式（参考 data-model.md 实体 4 输出格式示例）
- [ ] T010 [US1] 在 `src/main/resources/application.yaml` 中添加 `app.version` 和 `app.name` 配置项注释，调整 `data-dir` 默认值为 `./data`（启动包场景）

**检查点**：此时，用户故事 1 应完全功能可用 — 启动脚本可执行，地址横幅正确输出

---

## 阶段 4：用户故事 2 — 启动错误自动检测与友好提示（优先级：P1）

**目标**：启动过程中遇到常见问题（端口占用、配置文件损坏、磁盘空间不足、JRE 缺失、数据目录无写入权限）时，系统自动检测并以中文给出明确的错误原因和解决建议。

**独立测试**：人为制造各种启动故障（占用端口、删除配置文件、设置数据目录为只读等），执行启动脚本，验证是否输出中文错误描述和修复建议。

### 用户故事 2 的实现

> **注意**：US2 的预检查逻辑已在 US1 的启动脚本中实现了基础框架。本阶段聚焦于完善错误消息中文文案、边界情况处理和交互式确认。

- [ ] T011 [US2] 完善 `scripts/start.sh` 中所有预检查的中文错误输出格式：统一 `[错误]` / `[警告]` 前缀，每种故障输出具体修复建议，包括 JRE 缺失（"未检测到 Java 运行环境"）和 JRE 架构不匹配（"内置 JRE 架构与当前系统不匹配"）场景的中文提示（参考 contracts/startup-scripts.md 预检查错误输出契约）
- [ ] T012 [US2] 完善 `scripts/start.bat` 中所有预检查的中文错误输出格式（与 T011 对齐，包括 JRE 缺失和架构不匹配场景，Windows 终端编码处理）
- [ ] T013 [US2] 在 `scripts/start.sh` 中实现磁盘空间不足时的交互式确认（`read -p "是否继续启动？[y/N]"`）和非交互模式自动退出逻辑（检测 `CI=true` 环境变量或 stdin 非终端时自动退出）。磁盘空间阈值从环境变量 `DISK_SPACE_THRESHOLD_MB` 读取，默认 100MB
- [ ] T014 [US2] 在 `scripts/start.bat` 中实现磁盘空间不足时的交互式确认（`choice` 命令）和非交互模式自动退出逻辑（检测 `CI=true` 环境变量或非交互式终端）。磁盘空间阈值从环境变量 `DISK_SPACE_THRESHOLD_MB` 读取，默认 100MB
- [ ] T015 [US2] 在 `scripts/start.sh` 和 `scripts/start.bat` 中实现已有实例检测的交互式确认（询问是否强制重启，旧进程终止后继续启动）

**检查点**：此时，6 种常见启动故障（端口占用、配置文件缺失、磁盘空间不足、JRE 缺失、JRE 架构不匹配、数据目录无写入权限）的检测覆盖率达到 100%

---

## 阶段 5：用户故事 3 — 配置密码明文自动加密（优先级：P2）

**目标**：用户在配置文件中填写明文密码，系统每次启动时自动加密为 BCrypt 哈希值并保存在内存中的 Spring Environment 内。加密值绝不回写到 YAML 文件，配置文件始终保持用户写入的原始值。

**独立测试**：在 `application.yaml` 中设置 `app.auth.password: "test123"`（明文），启动系统，验证日志输出加密消息，用 `test123` 登录成功。关闭应用后检查配置文件，确认密码值未被修改（仍为明文）。再次启动，验证每次启动均输出加密日志（每次启动均重新检测并加密到内存）。

### 用户故事 3 的测试 ⚠️

> **注意：首先编写这些测试，确保它们在实现前失败（章程 V）**

- [ ] T016 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/config/PasswordEncryptionPostProcessorTest.java` 中编写单元测试：覆盖明文加密到内存、已加密跳过、空值警告、环境变量覆盖场景（参考 contracts/password-encryption.md 处理流程）
- [ ] T017 [P] [US3] 在 `src/test/java/top/lldwb/alistmediasync/util/ServerAddressLoggerTest.java` 中编写单元测试：覆盖地址收集、容器环境检测、横幅格式输出

### 用户故事 3 的实现

- [ ] T018 [US3] 创建 `src/main/java/top/lldwb/alistmediasync/config/PasswordEncryptionPostProcessor.java`，实现 `EnvironmentPostProcessor` 接口：读取 `app.auth.password` → 判断 `{bcrypt}` 前缀 → BCrypt 加密 → 将加密值设置到内存 Environment（绝不回写 YAML 文件）（参考 contracts/password-encryption.md 处理流程图）
- [ ] T019 [US3] 在 `PasswordEncryptionPostProcessor` 中实现 BCrypt 加密逻辑：使用 `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`（10 轮哈希），生成 `{bcrypt}$2a$...` 格式的加密值，通过 `MutablePropertySources` 或系统属性将加密后的值注入 Environment
- [ ] T020 [US3] 在 `PasswordEncryptionPostProcessor` 中实现配置来源检测：区分 YAML 文件和环境变量两种密码来源，对两种来源的明文值均执行加密到内存的逻辑。环境变量值同样不写回文件

**检查点**：此时，密码自动加密功能完整可用 — 每次启动均检测并加密明文密码到内存，配置文件中的值保持不变

---

## 阶段 6：用户故事 4 — 一键打包生成启动包（优先级：P2）

**目标**：执行一条 Maven 命令即可自动完成整合打包（编译后端、构建前端、复制 JRE、生成启动脚本、打包为 zip/tar.gz），产物经过完整性校验。

**独立测试**：从干净仓库执行 `./mvnw package -P bootstrap`，验证生成的 zip/tar.gz 文件包含完整目录结构、启动脚本有可执行权限（Linux）、解压后启动脚本能正常运行。

### 用户故事 4 的实现

- [ ] T022 [US4] 在 `pom.xml` 的 `bootstrap` profile 中配置 JRE 下载逻辑：通过 `exec-maven-plugin` 调用 Adoptium API v3 下载对应平台的 Temurin JRE 21，解压到 `${project.build.directory}/runtime/`，校验 SHA-256。使用 `${project.build.directory}/bootstrap-tmp/` 作为临时工作目录，配置 `maven-clean-plugin` 在 `clean` 阶段清理临时文件，确保打包中断后残留文件可被清理（参考 research.md 决策 1 和 contracts/build-packaging.md JRE 下载契约）
- [ ] T023 [US4] 在 `pom.xml` 的 `bootstrap` profile 中配置前端构建集成：`validate` 阶段检查 Node.js 可用性，`compile` 阶段执行 `npm run build`（若 `skip.frontend=false`）（参考 contracts/build-packaging.md 生命周期绑定表）
- [ ] T024 [US4] 完善 `assembly/bootstrap.xml`：配置 `dependencySets`（JAR 到 `lib/`）、`fileSets`（JRE 到 `runtime/`、启动脚本到根目录、配置文件到 `config/`、空目录 `data/` 和 `logs/`），区分 Windows（`.zip`）和 Linux（`.tar.gz`）格式（参考 contracts/build-packaging.md Assembly Descriptor 契约）
- [ ] T025 [US4] 在 `pom.xml` 的 `bootstrap` profile 中配置 `verify` 阶段完整性校验：解压产物到临时目录，验证关键文件存在（JAR、JRE 可执行文件、配置文件、启动脚本），校验产物大小（压缩后 ≤150MB，解压后 ≤400MB，超限则构建失败），输出打包摘要（参考 contracts/build-packaging.md 完整性校验契约和打包摘要输出）。另增加离线验证步骤：在无外网访问的 Docker 容器中解压启动包并执行启动脚本，验证无需联网即可正常运行（覆盖 SC-006）
- [ ] T026 [US4] 在 `assembly/bootstrap.xml` 中配置 Linux 启动脚本的文件权限（`fileMode=0755`），确保 `start.sh` 解压后可直接执行

**检查点**：此时，`./mvnw package -P bootstrap` 可生成完整的启动包归档文件

---

## 阶段 7：用户故事 5 — Docker 控制台地址输出（优先级：P3）

**目标**：Docker 容器启动后在 `docker logs` 中输出与服务网络可访问地址相同格式的信息，包括容器内部端口映射关系提示。

**独立测试**：构建 Docker 镜像并启动容器，查看 `docker logs` 输出，验证是否包含与服务启动后相同格式的地址列表。

### 用户故事 5 的实现

- [ ] T027 [US5] 在 `src/main/java/top/lldwb/alistmediasync/util/ServerAddressLogger.java` 中实现容器环境检测方法：检查 `/.dockerenv` 文件 + `DOCKER_CONTAINER` 环境变量 + `/proc/1/cgroup` 内容（参考 research.md 决策 3 和 data-model.md 实体 4 容器环境输出格式）
- [ ] T028 [US5] 在 `ServerAddressLogger.java` 中实现容器环境下的差异化横幅输出：地址旁注明"容器内部端口"，末尾提示"容器内部端口可能映射到宿主机不同端口，请参阅 docker-compose.yml 中的 ports 配置"（参考 data-model.md 实体 4 容器环境输出格式示例）
- [ ] T029 [US5] 在 `Dockerfile` 中添加 `ENV DOCKER_CONTAINER=true` 环境变量标记（可选，作为容器检测的兜底手段）

**检查点**：此时，Docker 容器日志中的地址输出与一体化启动包体验一致

---

## 阶段 8：润色与跨领域关注点

**目的**：影响多个用户故事的改进和最终验证

- [ ] T030 [P] 在 `src/main/resources/application.yaml` 中添加 `app.auth.password` 的详细注释说明（支持明文和 BCrypt 格式、每次启动自动加密到内存、环境变量覆盖）
- [ ] T031 [P] 更新项目根目录 `README.md`，添加一体化启动包使用说明章节（下载、解压、启动、配置、常见问题）
- [ ] T032 端到端验证：按 `specs/005-standalone-bootstrap/quickstart.md` 中所有 10 个场景逐一验证（场景 1-10）
- [ ] T033 [P] 在 `scripts/start.sh` 和 `scripts/start.bat` 中添加启动耗时统计（脚本开始时间 → Java 进程启动完成时间）
- [ ] T034 代码清理：检查所有新增文件的注释一致性（中文注释）、移除调试日志、统一错误消息格式

---

## 依赖与执行顺序

### 阶段依赖

- **设置（阶段 1）**：无依赖 — 可立即开始
- **基础（阶段 2）**：依赖设置完成 — 阻塞所有用户故事
- **用户故事 1（阶段 3）**：依赖基础完成 — P1 MVP
- **用户故事 2（阶段 4）**：依赖 US1 启动脚本框架 — P1（与 US1 同为 P1，但需 US1 脚本骨架先就位）
- **用户故事 3（阶段 5）**：依赖基础完成 — P2，可独立于 US1/US2 并行开发
- **用户故事 4（阶段 6）**：依赖 US1（启动脚本）、US3（密码加密类）完成 — P2，需等待产物就位
- **用户故事 5（阶段 7）**：依赖 US1（ServerAddressLogger 增强）完成 — P3
- **润色（阶段 8）**：依赖所有期望的用户故事完成

### 用户故事依赖

- **用户故事 1（P1）**：可在基础（阶段 2）后开始 — 不依赖其他故事
- **用户故事 2（P1）**：依赖 US1 的启动脚本骨架（T007、T008）完成后开始
- **用户故事 3（P2）**：可在基础（阶段 2）后开始 — 独立于 US1/US2
- **用户故事 4（P2）**：依赖 US1（T007、T008）和 US3（T018）完成
- **用户故事 5（P3）**：依赖 US1（T009）完成

### 每个用户故事内部

- 测试（US3）必须在实现之前编写并失败
- 启动脚本（US1）先于预检查完善（US2）
- 核心实现先于边界情况处理
- 故事完成后再进入下一个优先级

### 并行机会

- T001、T002、T003 可并行（阶段 1）
- T004、T005、T006 可并行（阶段 2）
- T007 和 T008 可并行（US1 的 Linux 和 Windows 脚本）
- T011 和 T012 可并行（US2 的 Linux 和 Windows 错误消息完善）
- T016 和 T017 可并行（US3 的测试）
- US3 整体可与 US1/US2 并行开发（不同文件，无依赖）
- T030、T031、T033 可并行（阶段 8）
- US5 可与 US3/US4 并行开发

---

## 并行示例：用户故事 1

```bash
# 一起启动 Linux 和 Windows 启动脚本（不同文件，无依赖）：
任务：T007 "创建 scripts/start.sh Linux 启动脚本"
任务：T008 "创建 scripts/start.bat Windows 启动脚本"

# 然后：
任务：T009 "增强 ServerAddressLogger.java 地址输出"
任务：T010 "调整 application.yaml 配置项"
```

## 并行示例：用户故事 3

```bash
# 先编写测试（确保失败）：
任务：T016 "编写 PasswordEncryptionPostProcessorTest.java"
任务：T017 "编写 ServerAddressLoggerTest.java"

# 然后实现：
任务：T018 "创建 PasswordEncryptionPostProcessor.java"
任务：T019 "实现 BCrypt 加密与内存注入逻辑"
任务：T020 "实现配置来源检测（YAML 文件 / 环境变量）"
```

---

## 实现策略

### MVP 优先（用户故事 1 + 2）

1. 完成阶段 1：设置
2. 完成阶段 2：基础（Maven 打包骨架）
3. 完成阶段 3：用户故事 1（启动脚本 + 地址横幅）
4. 完成阶段 4：用户故事 2（预检查完善）
5. **停止并验证**：手动测试启动脚本，验证 5 种故障检测
6. 如果就绪则部署/演示

### 增量交付

1. 完成设置 + 基础 → 基础就绪
2. 添加用户故事 1 → 独立测试 → 可启动（MVP！）
3. 添加用户故事 2 → 独立测试 → 友好错误提示
4. 添加用户故事 3 → 独立测试 → 密码自动加密到内存
5. 添加用户故事 4 → 独立测试 → 一键打包
6. 添加用户故事 5 → 独立测试 → Docker 地址输出
7. 每个故事增加价值而不破坏之前的的故事

### 并行团队策略

多个开发人员时：

1. 团队一起完成设置 + 基础
2. 基础完成后：
   - 开发人员 A：用户故事 1 + 2（启动脚本）
   - 开发人员 B：用户故事 3（密码加密）
3. US1 和 US3 完成后：
   - 开发人员 A：用户故事 4（打包构建）
   - 开发人员 B：用户故事 5（Docker 地址输出）
4. 各故事独立完成并集成

---

## 备注

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应能独立完成和测试
- 实现前验证测试失败（US3）
- 每个任务或逻辑组后提交
- 在任何检查点停止以独立验证故事
- 启动脚本中的路径始终使用引号包裹（支持含空格路径）
- Linux 脚本换行符必须为 LF（防止 CRLF 导致的执行失败）
- 避免：模糊的任务、同文件冲突、破坏独立性的跨故事依赖
