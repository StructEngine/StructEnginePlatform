# structengine-platform

像素三视图转 Minecraft 方块的极简一体化服务（Java 后端 + Vue 前端）。

## 项目简介

用户上传已像素化的物体三视图（正视、侧视、俯视），服务调用多模态 AI 进行空间推理，通过三视图投影交集运算输出确定性的 Minecraft 体素坐标，并以紧凑二维数组 JSON 文件对外分发，供 Fabric 模组通过 `/buildmodel <url>` 导入构建。

## 技术栈

- 后端：Java 21，Spring Boot 4（内嵌 Tomcat）+ Spring WebSocket
- 并发：JDK 21 虚拟线程（`spring.threads.virtual.enabled=true`），WebSocket RPC 同步阻塞不占用 OS 线程
- 前端：Vue 3 + Element Plus + Leaflet + Vite（构建产物 `dist` 由后端统一托管）
- 存储：本地文件系统（`./data/images` 存原图、`./data/models` 存模型 JSON），无数据库
- 托管：单进程统一托管 API 路由、模型静态分发与前端 `dist` 静态资源（SPA 回退），无 Nginx

## 环境变量

服务所有配置均通过环境变量注入，命名遵循 `STRUCTENGINE_` 前缀 + UPPER_SNAKE_CASE。

| 变量 | 含义 | 默认值 | 必填 |
|------|------|--------|------|
| `STRUCTENGINE_SERVER_PORT` | 服务监听端口 | `8080` | 否 |
| `STRUCTENGINE_AI_API_URL` | AI 推理服务地址（OpenAI 兼容，不含 `/chat/completions`，如 `https://api.example.com/v1`） | 无 | 是 |
| `STRUCTENGINE_AI_API_KEY` | AI 认证密钥（仅服务端使用，不会出现在日志与响应中） | 无 | 是 |
| `STRUCTENGINE_AI_MODEL` | AI 视觉模型名 | 无 | 是 |
| `STRUCTENGINE_DATA_DIR` | 数据根目录（下含 `images`/`models` 子目录） | `./data` | 否 |
| `STRUCTENGINE_MAX_IMAGE_MB` | 单图片大小上限（MB） | `10` | 否 |
| `STRUCTENGINE_GAME_TOKEN` | 游戏服 WebSocket 握手令牌；为空时不校验（仅限本地开发） | 空 | 生产建议 |
| `STRUCTENGINE_GAME_RPC_TIMEOUT_SECONDS` | 等待插件 `RESPONSE_Y` 的超时秒数 | `5` | 否 |
| `STRUCTENGINE_PUBLIC_BASE_URL` | 模型文件对外基础地址，如 `https://build.example.com`；为空按请求 Host 推导 | 空 | 反向代理时建议 |

> 三个必填项（`STRUCTENGINE_AI_API_URL` / `STRUCTENGINE_AI_API_KEY` / `STRUCTENGINE_AI_MODEL`）缺失或为空时，服务将拒绝启动并报错。
>
> `dist` 目录为**固定值 `./dist`**（对齐镜像 `/app/dist`），不通过环境变量配置。

配置方式（任选其一）：

```bash
# 1. 临时设置（当前 shell）
export STRUCTENGINE_AI_API_URL="https://api.example.com/v1"
export STRUCTENGINE_AI_API_KEY="sk-xxx"
export STRUCTENGINE_AI_MODEL="your-vision-model"

# 2. 使用 .env 文件 + docker-compose（见下文 Docker 部署）
```

## 本地启动

```bash
# 后端（需 JDK 21 + Maven）
cd backend
mvn -B -DskipTests package
STRUCTENGINE_AI_API_URL="https://api.example.com/v1" \
STRUCTENGINE_AI_API_KEY="sk-xxx" \
STRUCTENGINE_AI_MODEL="your-vision-model" \
java -jar target/app.jar

# 前端（开发模式，Vite dev server，/api 代理到后端）
cd frontend
npm install
npm run dev   # 默认 http://localhost:5173，代理目标见 .env.development
```

## Docker 部署

### 镜像说明

镜像采用多阶段构建：阶段 1 用 `maven:3.9-eclipse-temurin-21` 编译打包 `app.jar`，阶段 2 用 `eclipse-temurin:21-jre-alpine` 运行，仅包含 JRE、`app.jar`、前端 `dist` 静态产物与数据挂载点，**不含** Maven/Node 构建工具链。

### 方式一：本地手动构建镜像

```bash
# 1. 构建前端产物（Node 20+）
cd frontend
npm ci
npm run build
cd ..
cp -r frontend/dist dist

# 2. 构建镜像（多阶段，内部完成 Maven 编译）
docker build -t structengine-platform .
```

### 方式二：使用 CI 推送的镜像

推送到 `main` 分支或手动触发 `build-and-push` 工作流后，镜像会推送至 GHCR：

```bash
docker pull ghcr.io/<owner>/<repo>:latest
```

### 运行容器

```bash
docker run -d \
  --name structengine \
  -p 8080:8080 \
  -e STRUCTENGINE_AI_API_URL="https://api.example.com/v1" \
  -e STRUCTENGINE_AI_API_KEY="sk-xxx" \
  -e STRUCTENGINE_AI_MODEL="your-vision-model" \
  -e STRUCTENGINE_SERVER_PORT=8080 \
  -e STRUCTENGINE_MAX_IMAGE_MB=10 \
  -v structengine-data:/app/data \
  --restart unless-stopped \
  structengine-platform
```

参数说明：

| 参数 | 说明 |
|------|------|
| `-p 8080:8080` | 将宿主机 8080 端口映射到容器监听端口 |
| `-e STRUCTENGINE_*` | 注入环境变量（必填项必须提供，否则容器启动失败） |
| `-v structengine-data:/app/data` | 挂载命名卷持久化图片与模型数据 |
| `--restart unless-stopped` | 容器异常退出或宿主机重启后自动恢复 |

### docker-compose 示例

```yaml
services:
  structengine:
    image: ghcr.io/<owner>/<repo>:latest
    container_name: structengine
    ports:
      - "8080:8080"
    environment:
      STRUCTENGINE_AI_API_URL: "https://api.example.com/v1"
      STRUCTENGINE_AI_API_KEY: "sk-xxx"
      STRUCTENGINE_AI_MODEL: "your-vision-model"
      STRUCTENGINE_SERVER_PORT: "8080"
      STRUCTENGINE_MAX_IMAGE_MB: "10"
    volumes:
      - structengine-data:/app/data
    restart: unless-stopped

volumes:
  structengine-data:
```

启动与停止：

```bash
docker compose up -d     # 启动
docker compose logs -f   # 查看日志
docker compose down      # 停止并移除容器（保留数据卷）
```

### 数据持久化

服务将用户上传的原图与生成的模型 JSON 持久化在容器的 `/app/data` 目录下：

- `images/`：原图，命名 `{uuid}_{index}.jpg`
- `models/`：模型文件，命名 `{uuid}.json`

请务必通过 `-v`（或 compose 的 `volumes`）将该目录挂载到宿主机或命名卷，否则容器重建后数据会丢失。

## 地图选点（Squaremap）

前端「地图选点建造」页面把 [Squaremap](https://github.com/jpenilla/squaremap) 瓦片嵌入 Leaflet，管理员点击地图即可拾取精确的 `(X, Z)`，随后由 Platform 通过 WebSocket 向游戏服查询地表高度 `Y`，调用 AI 生成模型并下发构建。

### 前置条件

1. 游戏服安装并启用 **Squaremap**，开放其 Web Server 端口（默认 `8123`，以实际配置为准）。
2. 游戏服安装 **StructEngine** 插件（Fabric / NeoForge / Paper 任一），并配置 `STRUCTENGINE_WS_URL` 指向本 Platform 的 `ws://<platform-host>:<port>/ws/game`（阶段 3 提供）。
3. 前端构建时注入以下变量（开发模式默认值见 `frontend/.env.development`，生产默认值见 `frontend/.env.production`）：

| 变量 | 含义 | 示例 |
|------|------|------|
| `VITE_SQUAREMAP_URL` | Squaremap Web 根地址，或同源反向代理路径 | `/squaremap` 或 `http://map.example.com:8123` |
| `VITE_SQUAREMAP_WORLD` | 要展示的 Squaremap 世界名 | `world` |
| `VITE_SQUAREMAP_PROXY_TARGET` | 仅开发模式：Vite 把 `/squaremap` 反代到该地址 | `http://localhost:8123` |

> 生产环境需由 Nginx / Caddy 把 `/squaremap` 反向代理到 Squaremap Web Server；若直接把 `VITE_SQUAREMAP_URL` 配成完整 URL，则需确认 Squaremap 允许跨域访问瓦片与 `settings.json`。

### 使用流程

1. 打开页面右上角「地图选点建造」。
2. 点击地图选点，坐标按 `Math.floor` 向下取整，保证负坐标也落在正确方块内。
3. 填写玩家名与建筑提示词，选择已连接的游戏服务器。
4. 点击「确定构建」，页面进入 Loading，Platform 等待游戏服回传 `Y` 后生成模型并下发 `BUILD`。
5. 成功后展示完整坐标 `X / Y / Z` 与模型 URL。

### 像素化渲染

地图容器的 `.leaflet-tile-pane img` 已强制应用：

```css
image-rendering: pixelated;
image-rendering: crisp-edges;
-ms-interpolation-mode: nearest-neighbor;
filter: none !important;
```

确保地图放大时方块边缘锋利、不会被浏览器双线性插值模糊。

## 地图选点建造（WebSocket 调度）

### 链路

```
Web 点击 (X,Z)
   → POST /api/map/build
   → Platform 生成 taskId，向插件发 QUERY_Y
   → 插件主线程 getHighestBlockYAt(x,z)
   → 插件回传 RESPONSE_Y(taskId,y)
   → Platform 虚拟线程 future.get(5s) 被唤醒
   → Mock/Spring AI 生成体素模型，落盘 data/models/{taskId}.json
   → Platform 下发 BUILD(taskId, playerName, x,y,z, modelUrl)
   → 插件异步下载 JSON，压入原有 BuildTaskQueue，按绝对坐标分批构建
```

### WebSocket 端点

- 地址：`ws://<platform-host>:<port>/ws/game?serverId=<serverId>`
- 鉴权：配置 `STRUCTENGINE_GAME_TOKEN` 后，插件必须携带请求头 `X-StructEngine-Token: <token>`（或 `Authorization: Bearer <token>`）。
- 心跳：插件每 20 秒发送 `PING`，Platform 回 `PONG`。
- 断线重连：插件按 1s → 30s 指数退避重连；Platform 会立即失败该服尚未完成的 `QUERY_Y` Future，避免请求线程空等。

统一消息体（空字段不序列化）：

```json
{"action":"QUERY_Y","taskId":"uuid","playerName":"Steve","x":12,"z":-8}
{"action":"RESPONSE_Y","taskId":"uuid","y":70}
{"action":"BUILD","taskId":"uuid","playerName":"Steve","x":12,"y":70,"z":-8,"modelUrl":"http://platform/models/uuid.json"}
{"action":"ERROR","taskId":"uuid","message":"主线程查询失败"}
```

### 构建接口

`GET /api/map/servers`：

```json
{"code":200,"message":"success","data":[{"serverId":"survival"}]}
```

`POST /api/map/build`：

```json
{"serverId":"survival","x":12,"z":-8,"playerName":"Steve","prompt":"红石城堡"}
```

成功响应（`y` 为插件实时回传的地表高度）：

```json
{"code":200,"message":"success","data":{
  "taskId":"uuid","serverId":"survival","playerName":"Steve",
  "x":12,"y":70,"z":-8,"modelUrl":"http://platform/models/uuid.json"
}}
```

> 当前模型生成由 `MockStructureGenerator` 提供确定性占位建筑；`StructureGenerator` 已抽象，
> 后续接入 Spring AI 时新增实现并替换 Bean 即可，RPC/落盘/下发链路无需改动。
>
> 模型文件使用项目根目录解析后的绝对路径 `{data-dir}/models/{taskId}.json` 落盘，
> 并由 `GET /models/{filename}` 对外分发；游戏服必须能访问 `modelUrl`。

## 接口说明

### 上传转换

`POST /api/generate`，`Content-Type: multipart/form-data`，字段 `images`（1 张拼合三视图，或 3 张分立正/侧/俯视图）。

```json
// 成功响应
{"code":200,"message":"success","data":{"json_url":"http://<server-host>/models/3f2a8b9c.json"}}
// 失败响应（data 省略）
{"code":400,"message":"输入形态不合法：请上传 1 张拼合三视图或 3 张分立三视图"}
```

### 模型分发

`GET /models/:filename`，返回纯 JSON 紧凑二维数组：

```json
[["minecraft:stone",0,0,0],["minecraft:cobblestone",1,0,0],["minecraft:glass",0,1,0]]
```

## 错误码

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 输入非法 |
| 413 | 文件过大或数量超限 |
| 422 | 数据解析失败 |
| 502 | 上游推理失败 |
| 503 | 游戏服务器未连接 |
| 504 | 游戏服务器响应超时 |
| 500 | 存储失败 |

## 测试

单元测试按模块分批执行（每批不超过 64 个测试项）：

```bash
cd backend
mvn -B -Dtest=com.structengine.platform.llm.** test
mvn -B -Dtest=com.structengine.platform.service.** test
mvn -B -Dtest=com.structengine.platform.domain.**,com.structengine.platform.common.**,com.structengine.platform.config.** test
mvn -B -Dtest=com.structengine.platform.game.** test
```

`GameWebSocketIntegrationTest` 会启动随机端口的完整 Spring 上下文，模拟插件完成
`QUERY_Y → RESPONSE_Y → BUILD` 全链路，并校验模型静态 URL 与绝对路径落盘。
