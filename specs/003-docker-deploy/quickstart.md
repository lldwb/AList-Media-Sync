# 快速入门验证指南：Docker 容器化部署支持

**功能**：Docker 容器化部署 | **日期**：2026-06-19

## 前提条件

- Docker Engine 20.10+（或 Docker Desktop 4.x）
- Docker Compose 2.0+（`docker compose` 子命令，非旧版 `docker-compose`）
- 至少 2GB 可用磁盘空间（镜像 + 卷）
- 至少 512MB 可用内存（JVM 最小堆 128MB）

## 快速验证流程

### 场景 1：验证镜像构建（对应 US-1）

```bash
# 步骤 1：进入项目根目录
cd <项目根目录>

# 步骤 2：构建镜像
docker build -t alist-media-sync .

# 预期结果：
# - 构建过程显示两阶段（builder → runtime）
# - 输出 "Successfully built <image-id>"
# - 输出 "Successfully tagged alist-media-sync:latest"
```

```bash
# 步骤 3：检查镜像大小
docker images alist-media-sync --format "{{.Size}}"

# 预期结果：镜像大小 < 250MB（符合 SC-002）
```

### 场景 2：验证容器启动与健康检查（对应 US-1）

```bash
# 步骤 1：启动容器（需提供 ALIST_BASE_URL 和 ALIST_TOKEN）
docker run -d \
  --name alist-media-sync-test \
  -p 8080:8080 \
  -e ALIST_BASE_URL=https://alist-demo.example.com \
  -e ALIST_TOKEN=demo-token \
  alist-media-sync

# 步骤 2：等待启动（冷启动 < 30 秒）
sleep 30
```

```bash
# 步骤 3：检查健康端点
curl http://localhost:8080/actuator/health

# 预期结果：{"status":"UP"}（符合 SC-003）
```

```bash
# 步骤 4：查看日志
docker logs alist-media-sync-test

# 预期结果：日志输出到 stdout，包含 Spring Boot 启动横幅
```

```bash
# 步骤 5：停止并删除容器
docker stop alist-media-sync-test && docker rm alist-media-sync-test

# 预期结果：容器在 15 秒内停止（符合 SC-004）
```

### 场景 3：验证数据持久化（对应 US-1 验收场景 3）

```bash
# 步骤 1：创建命名卷并启动容器
docker volume create alist-media-sync-data-test
docker run -d \
  --name alist-media-sync-persist \
  -p 8080:8080 \
  -v alist-media-sync-data-test:/app/data \
  -e ALIST_BASE_URL=https://alist-demo.example.com \
  -e ALIST_TOKEN=demo-token \
  alist-media-sync

# 步骤 2：停止并删除容器
docker stop alist-media-sync-persist && docker rm alist-media-sync-persist

# 步骤 3：重新创建容器，挂载同一卷
docker run -d \
  --name alist-media-sync-persist-2 \
  -p 8080:8080 \
  -v alist-media-sync-data-test:/app/data \
  -e ALIST_BASE_URL=https://alist-demo.example.com \
  -e ALIST_TOKEN=demo-token \
  alist-media-sync

# 步骤 4：验证数据库文件仍在
docker exec alist-media-sync-persist-2 ls -la /app/data/

# 预期结果：H2 数据库文件（*.mv.db）存在且未被重建（符合 SC-006）

# 清理
docker stop alist-media-sync-persist-2 && docker rm alist-media-sync-persist-2
docker volume rm alist-media-sync-data-test
```

### 场景 4：验证 Docker Compose 编排（对应 US-2）

```bash
# 步骤 1：创建 .env 文件
cat > .env << EOF
PORT=8080
LOGGING_LEVEL=INFO
ALIST_BASE_URL=https://alist-demo.example.com
ALIST_TOKEN=demo-token
JAVA_OPTS=-Xms128m -Xmx256m
EOF

# 步骤 2：通过 Compose 启动
docker compose up -d

# 预期结果：服务在 30 秒内启动，可通过 http://localhost:8080 访问
```

```bash
# 步骤 3：查看日志
docker compose logs -f

# 预期结果：实时显示应用日志
```

```bash
# 步骤 4：验证端口修改生效
# 修改 .env 中 PORT=9090
sed -i 's/PORT=8080/PORT=9090/' .env
docker compose down
docker compose up -d
curl http://localhost:9090/actuator/health

# 预期结果：返回 {"status":"UP"}（符合 FR-004）

# 恢复并清理
docker compose down
```

### 场景 5：验证环境变量配置（对应 US-3）

```bash
# 步骤 1：验证 SERVER_PORT
docker run -d \
  --name alist-media-sync-env \
  -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e ALIST_BASE_URL=https://alist-demo.example.com \
  -e ALIST_TOKEN=demo-token \
  alist-media-sync

curl http://localhost:9090/actuator/health

# 预期结果：{"status":"UP"}（符合 US-3 场景 1）

docker stop alist-media-sync-env && docker rm alist-media-sync-env
```

```bash
# 步骤 2：验证 LOGGING_LEVEL
docker run -d \
  --name alist-media-sync-debug \
  -p 8080:8080 \
  -e LOGGING_LEVEL=DEBUG \
  -e ALIST_BASE_URL=https://alist-demo.example.com \
  -e ALIST_TOKEN=demo-token \
  alist-media-sync

docker logs alist-media-sync-debug 2>&1 | head -20

# 预期结果：日志中包含 DEBUG 级别信息（符合 US-3 场景 3）

docker stop alist-media-sync-debug && docker rm alist-media-sync-debug
```

### 场景 6：验证非 root 用户运行

```bash
docker run -d \
  --name alist-media-sync-user \
  -e ALIST_BASE_URL=https://alist-demo.example.com \
  -e ALIST_TOKEN=demo-token \
  alist-media-sync

docker exec alist-media-sync-user whoami

# 预期结果：appuser（非 root）（符合 FR-010）

docker stop alist-media-sync-user && docker rm alist-media-sync-user
```

## 边界情况验证

### 验证卷权限不足时的错误提示

```bash
# 挂载一个只读卷，预期启动失败并有明确错误
docker run --rm \
  -v /nonexistent:/app/data:ro \
  -e ALIST_BASE_URL=https://alist.example.com \
  -e ALIST_TOKEN=test \
  alist-media-sync

# 预期结果：容器启动失败，日志中包含路径相关错误信息
```

### 验证内存不足警告

```bash
docker run --rm \
  --memory=128m \
  -e JAVA_OPTS="-Xms256m -Xmx256m" \
  -e ALIST_BASE_URL=https://alist.example.com \
  -e ALIST_TOKEN=test \
  alist-media-sync

# 预期结果：JVM 内存分配失败或日志中有内存不足警告
```

## 清理全部测试资源

```bash
# 停止所有相关容器
docker ps -a --filter "name=alist-media-sync" --format "{{.ID}}" | xargs -r docker rm -f

# 删除测试卷
docker volume rm alist-media-sync-data-test 2>/dev/null

# 删除镜像（可选）
docker rmi alist-media-sync
```
