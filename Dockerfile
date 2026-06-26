# ============================================================
# AList-Media-Sync Dockerfile（多阶段构建）
# ============================================================
# 阶段 1：frontend-build — Node.js + Vite 构建前端 SPA
# 阶段 2：builder — JDK + Maven Wrapper 编译打包
# 阶段 3：runtime — 仅包含 JRE + JAR，最小化最终镜像体积
# ============================================================

# ---- 阶段 1：前端构建 ----
FROM node:22-alpine AS frontend-build

WORKDIR /app/frontend

# 安装依赖（利用 Docker 层缓存）
COPY src/main/frontend/package.json src/main/frontend/package-lock.json* ./
RUN npm ci --prefer-offline 2>/dev/null || npm install

# 复制前端源码
COPY src/main/frontend/ .

# 构建前端（输出到 dist/）
RUN npm run build

# ---- 阶段 2：后端构建 ----
FROM eclipse-temurin:21-jdk-alpine AS builder

# 设置 Maven 构建 JVM 参数
ENV MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=256m"

WORKDIR /build

# 先复制 Maven Wrapper 和 pom.xml，利用 Docker 层缓存加速重复构建
COPY mvnw mvnw.cmd ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# 下载依赖（此层在 pom.xml 未变化时可复用缓存）
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -B -q

# 复制源码
COPY src/ src/

# 复制前端构建产物到静态资源目录
COPY --from=frontend-build /app/frontend/dist/ src/main/resources/static/app/

# 编译打包（跳过测试以加快构建速度；测试应在 CI 中单独执行）
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw package -DskipTests -B -q

# ---- 阶段 3：运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

# 创建非 root 用户并安装健康检查依赖（wget）
# 安全最佳实践：CIS Docker Benchmark 4.1
RUN apk add --no-cache wget && \
    addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

# 设置工作目录
WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /build/target/*.jar /app/app.jar

# 复制诊断脚本（轻量诊断系统：容器内 sh scripts/diagnose.sh 可生成诊断包）
COPY scripts/diagnose.sh /app/scripts/diagnose.sh

# 创建数据/日志/诊断目录并设置权限和脚本可执行位
RUN mkdir -p /app/data /app/logs /app/diagnostics && \
    chmod +x /app/scripts/diagnose.sh && \
    chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 声明容器监听端口
EXPOSE 8080/tcp

# 容器入口点：Spring Boot 可执行 JAR
# JAVA_OPTS 环境变量可注入 JVM 参数（如 -Xms128m -Xmx256m）
# DOCKER_CONTAINER=true 标记容器环境，供 ServerAddressLogger 检测使用
ENV DOCKER_CONTAINER=true
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
