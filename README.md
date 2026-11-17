# StructEnginePlatform

StructEngine 的云端控制面：多用户/JWT 管理、Minecraft 游戏服管理、BlueMap 地图选点、玩家选择、Spring AI 对话生成体素模型、参考图生成体素模型，以及模型部署/回滚/恢复。

## 架构

```text
浏览器
  ├─ 管理台 /api（Spring Boot）
  └─ BlueMap iframe（每个 Minecraft Server 自带 BlueMap 5.16）
        │ postMessage 选点
        ▼
StructEnginePlatform
  ├─ WebSocket /ws/game  ← 多个 Minecraft Server 插件（协议 v3，每服 secret）
  ├─ Spring AI → OpenAI-compatible 上游
  ├─ SQLite（模型、任务、对话）
  └─ data/models/*.json（外网可访问）
        ▲
        │ BUILD / ROLLBACK / REDO
Minecraft Server 插件 ── 原有分批构建 / 回滚 / 恢复核心
```

### 关键流程

1. 用户在“服务器管理”创建服务器，平台生成一次性 secret；插件用 `STRUCTENGINE_PLATFORM_URL + STRUCTENGINE_SERVER_SECRET` 连接 Platform。
2. 插件握手携带 `X-StructEngine-Secret`，发送 `HELLO`（serverName、BlueMap URL、mapId、secret、协议版本 3），并每 10 秒/玩家事件时上报 `PLAYER_LIST`。
3. Web 选择 Minecraft Server → 加载该服的 BlueMap 定制页 → 点击选点（建筑中心 X/Z）→ 选择在线/离线玩家。
4. 在服务器详情 → “AI 地图建造”中描述建筑；调用 `/api/servers/{serverId}/models/generate` 生成严格 voxel JSON 并登记模型产物。
5. 预览模型后调用 `/api/servers/{serverId}/tasks/deploy`；Platform 查询地表 Y、计算中心偏移与旋转，落盘部署模型并发送 `BUILD`。
6. 插件下载模型、按绝对原点入队，使用原有分批构建/快照机制执行，并通过 `BUILD_STATUS` 回报进度。
7. Web 通过 `/api/servers/{serverId}/history` 查看插件历史，调用 rollback/redo。

## 技术栈

- 后端：Java 21、Spring Boot 4、Spring Security + JWT、Spring WebSocket、Spring AI、SQLite JDBC
- AI：统一 `spring.ai.openai.*` 配置；地图 AI 对话与参考图多模态共用同一套 Spring AI 接入
- 前端：Vue 3、Vue Router、Element Plus、Three.js 模型预览
- 地图：BlueMap 5.16（Paper/Fabric/NeoForge 对应版本）
- 存储：SQLite + 本地模型文件（`data/models`）
- 游戏端：Fabric / Paper / NeoForge 插件

## 环境变量

| 变量 | 含义 | 默认值 |
|---|---|---|
| `STRUCTENGINE_SERVER_PORT` | Platform 监听端口 | `8080` |
| `STRUCTENGINE_PROJECT_ROOT` | 项目根目录，data/dist 的解析基准 | JVM `user.dir` |
| `STRUCTENGINE_LOCAL_CONFIG` | 本地敏感覆盖文件路径；默认 `./src/main/resources/application-local.yml` | 见左 |
| `STRUCTENGINE_DATA_DIR` | 数据目录（含 models/images/structengine.db） | `./data` |
| `STRUCTENGINE_PUBLIC_BASE_URL` | 模型文件对外基础地址；为空按请求 Host 推导 | 空 |
| `STRUCTENGINE_JWT_SECRET` | JWT HS256 密钥，至少 32 字节 | 无，必须配置 |
| `STRUCTENGINE_JWT_TTL_HOURS` | JWT 有效期（小时） | `24` |
| `STRUCTENGINE_ADMIN_INITIAL_PASSWORD` | 首次安装 admin 初始密码（6~16 位） | 无，首次必须配置 |
| `STRUCTENGINE_SNOWFLAKE_WORKER_ID` | Snowflake workerId（0~1023） | `0` |
| `STRUCTENGINE_GAME_RPC_TIMEOUT_SECONDS` | 等待插件回包的秒数 | `5` |
| `STRUCTENGINE_TASK_TIMEOUT_SECONDS` | 构建任务多久无进度后标记失败（秒） | `600` |
| `STRUCTENGINE_AI_BASE_URL` | OpenAI-compatible Base URL | `https://token.dcepstc.com/v1` |
| `STRUCTENGINE_AI_API_KEY` | AI API Key | 空 |
| `STRUCTENGINE_AI_MODEL` | AI 模型 ID | `cn:deepseek-v4.1-flash` |
| `STRUCTENGINE_MAX_MODEL_BLOCKS` | 单模型最大方块数 | `20000` |

> 本地开发把敏感配置写入 `backend/src/main/resources/application-local.yml`，并通过
> `STRUCTENGINE_LOCAL_CONFIG` 指定绝对/相对路径；默认值为 `./src/main/resources/application-local.yml`。
> 该文件已被 `.gitignore` 忽略，并且通过 Maven `resources` 排除，不会进入 Jar。
>
> 如果 API Key 曾出现在聊天/日志中，请立即在服务商侧轮换。

## 登录与账号

- 首次启动且 `admin_account` 为空时，平台用 `STRUCTENGINE_ADMIN_INITIAL_PASSWORD` 创建 `admin` 账号，并标记为首次登录需改密。
- 普通用户可在登录页自助注册：用户名 4~16 位英文字母，密码 6~16 位，不要求邮箱。
- 注册和登录都需要图形验证码；登录成功后返回 JWT，前端存 `localStorage`，请求通过 `Authorization: Bearer <token>` 携带。
- 修改密码、管理员重置密码或禁用用户后，旧 JWT 立即失效。
- 第一阶段（用户体系）已完成；服务器归属、每服 secret 与 WS v3 将在第二阶段接入，因此当前业务接口仍只允许 `ADMIN` 使用。

## BlueMap 5.16 安装

三端分别放入：

```text
Fabric:   /opt/minecraft/server/mods/bluemap-5.16-fabric.jar
Paper:    /opt/minecraft/paper/plugins/bluemap-5.16-paper.jar
NeoForge: /opt/minecraft/neoforge/mods/bluemap-5.16-neoforge.jar
```

首次启动后在 BlueMap 配置中接受 Mojang 资源下载：

```yaml
accept-download: true
```

Web 端口配置（本文测试环境）：

| 平台 | 端口 | mapId |
|---|---|---|
| Fabric | 8100 | world |
| Paper | 8101 | world |
| NeoForge | 8102 | world |

把定制地图页安装到 BlueMap webroot：

```bash
./scripts/install-bluemap-page.sh /opt/minecraft/server/bluemap/web
./scripts/install-bluemap-page.sh /opt/minecraft/paper/bluemap/web
./scripts/install-bluemap-page.sh /opt/minecraft/neoforge/bluemap/web
```

页面地址：

```text
http://192.168.1.201:8100/structengine-map.html
http://192.168.1.201:8101/structengine-map.html
http://192.168.1.201:8102/structengine-map.html
```

页面只保留 BlueMap 画布，通过 `postMessage` 向 Platform 发送 `{x,z,rawY,mapId}`。

## 游戏服插件配置

在 Minecraft Server 启动前设置（以 NeoForge 为例）：

```bash
export STRUCTENGINE_PLATFORM_URL=ws://192.168.1.201:8080/ws/game
export STRUCTENGINE_SERVER_SECRET=在平台服务器详情中复制的一次性 secret
export STRUCTENGINE_SERVER_NAME="NeoForge 测试服"
export STRUCTENGINE_BLUEMAP_URL=http://192.168.1.201:8102/structengine-map.html
export STRUCTENGINE_BLUEMAP_MAP_ID=world
```

插件文件：

```text
Fabric:   mods/structengine-fabric-1.0.0.jar
Paper:    plugins/structengine-paper-1.0.0.jar
NeoForge: mods/structengine-neoforge-1.0.0.jar
```

原有 `/buildmodel <url>`、`/rollback`、`/redo` 命令继续保留。

## 启动

```bash
cd backend
mvn -B -DskipTests package
java -jar target/app.jar
```

前端：

```bash
cd frontend
npm install
npm run dev      # 开发，默认 http://localhost:5173
npm run build    # 产物在 frontend/dist
```

后端从 `STRUCTENGINE_PROJECT_ROOT/dist` 托管前端静态资源；本地可：

```bash
cp -r frontend/dist dist
```

## 管理台使用

1. 打开 `http://192.168.1.201:8080/`，使用 `admin` + 初始密码登录；首次登录会要求改密。
2. 管理员可在“用户管理”中创建/禁用/重置/删除普通用户。
3. 普通用户登录后进入“服务器管理”，创建服务器；平台只显示一次 secret，把它配置到该服的 StructEngine 插件。
4. 插件连接成功后服务器状态变为在线；打开服务器详情可看到 BlueMap、玩家和在线状态。
5. 在服务器详情中使用“AI 地图建造”“参考图生成”“模型产物”“构建任务”四个 tab。
6. AI 地图建造：在 BlueMap 地图点击选择建筑中心 → 选择玩家 → 填写描述/AI 对话 → 生成模型 → 预览 → 部署。
7. 部署后在“构建任务”查看 `QUEUED → DOWNLOADING → BUILDING → COMPLETED`；构建历史中可回滚/恢复。
8. 参考图生成：上传 1~3 张参考图 + 可选说明，生成后模型直接进入该服务器的“模型产物”。

### 参考图建模（三视图仅为建议）

在服务器详情 → “参考图生成” tab：

- 上传 **1~3 张参考图**即可。正视图、侧视图、俯视图只是推荐输入，不要求齐全。
- 参考图不足三视图时也可以直接提交：后端会把图片与可选补充说明发送给多模态模型，由模型推断缺失的进深、侧面和屋顶，补全为完整建筑。
- 可填写建筑类型、层数、屋顶、材质、颜色、门窗位置等补充说明，帮助模型更准确地补全。
- 生成成功后返回体素模型 JSON 地址，可按 `/buildmodel <url>` 在游戏内构建。

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -F "images=@front.png" -F "images=@side.png" \
  -F "description=两层石砖小屋，红色木屋顶，正面开门" \
  http://192.168.1.201:8080/api/servers/{serverId}/models/generate-from-images
```

## HTTP API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/captcha` | 获取图形验证码 |
| POST | `/api/auth/register` | 普通用户注册，返回 JWT |
| POST | `/api/auth/login` | 管理员/用户统一登录，返回 JWT |
| GET | `/api/auth/me` | 当前登录身份 |
| POST | `/api/auth/password` | 修改自己的密码，返回新 JWT |
| GET | `/api/admin/users` | 管理员查询用户 |
| POST | `/api/admin/users` | 管理员创建用户 |
| PUT | `/api/admin/users/{id}` | 管理员修改用户名/状态 |
| PUT | `/api/admin/users/{id}/password` | 管理员重置用户密码 |
| DELETE | `/api/admin/users/{id}` | 管理员删除用户 |
| GET | `/api/servers` | 服务器列表（用户自己的，管理员全部） |
| POST | `/api/servers` | 创建服务器，返回一次性 secret |
| GET | `/api/servers/{serverId}` | 服务器详情 |
| PUT | `/api/servers/{serverId}` | 重命名服务器 |
| POST | `/api/servers/{serverId}/secret/rotate` | 轮换 secret，返回新 secret |
| POST | `/api/servers/{serverId}/disable` | 禁用服务器并断开插件 |
| POST | `/api/servers/{serverId}/enable` | 启用服务器 |
| DELETE | `/api/servers/{serverId}` | 软删除服务器 |
| GET | `/api/servers/{serverId}/history` | 查询玩家构建历史（当前仅管理员） |
| POST | `/api/servers/{serverId}/rollback` | 回滚指定历史 |
| POST | `/api/servers/{serverId}/redo` | 恢复指定历史 |
| POST | `/api/servers/{id}/ai/chat` | 服务器上下文 AI 对话 |
| GET | `/api/servers/{id}/ai/chat/{sessionId}` | 对话历史 |
| POST | `/api/servers/{id}/models/generate` | 描述生成模型（地图 AI） |
| POST | `/api/servers/{id}/models/generate-from-images` | 参考图生成模型 |
| GET | `/api/servers/{id}/models` | 服务器模型产物列表 |
| DELETE | `/api/servers/{id}/models/{modelId}` | 软删除模型 |
| POST | `/api/servers/{id}/tasks/deploy` | 部署模型 |
| GET | `/api/servers/{id}/tasks` | 服务器任务列表 |
| GET | `/api/servers/{id}/tasks/{taskId}` | 任务状态 |
| GET | `/api/servers/{id}/history` | 插件构建历史 |
| POST | `/api/servers/{id}/history/{historyId}/rollback` | 回滚 |
| POST | `/api/servers/{id}/history/{historyId}/redo` | 恢复 |
| GET | `/api/admin/models` | 管理员查看全部模型 |
| GET | `/api/admin/audit` | 管理员查询审计日志 |
| GET | `/models/{filename}` | 模型公开分发（Minecraft Server 下载） |

除 `/models/**`、`/api/auth/captcha`、`/api/auth/register`、`/api/auth/login` 与游戏服 WebSocket 外，API 需要 JWT；`/api/admin/**` 要求 `ROLE_ADMIN`；服务器上下文业务接口要求服务器归属（管理员可操作全部）。

> 旧 `/api/map/build` 与 Squaremap 页面已删除。

## 加固项

- 构建任务：超过 `STRUCTENGINE_TASK_TIMEOUT_SECONDS` 无进度自动标记 `FAILED`；Platform 重启后非终态任务统一 `ABORTED`，避免前端无限轮询。
- 审计：服务器创建/改名/轮换 secret/禁用/删除、模型生成/删除、部署、回滚/恢复写入 `audit_log`，管理员可在“审计日志”页查询。
- 登录/注册：按 IP + 用户名做内存限流（登录 10 次/分钟、注册 5 次/分钟）。
- 安全响应头：`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`、CSP（允许 BlueMap iframe 与 data: 验证码）。
- 前端拆包：服务器详情 tab 使用异步组件，Three.js 预览独立 chunk。

## WebSocket 协议 v3

握手：`GET /ws/game` + `X-StructEngine-Secret: <secret>`（也兼容 `?secret=`）。
平台按 secret 哈希反查服务器并绑定 serverId；所有上行消息都带 `secret`。

```text
Minecraft Server -> Platform: HELLO, PLAYER_LIST, RESPONSE_Y, BUILD_STATUS,
                              HISTORY_RESPONSE, ERROR, PING（均含 secret）
Platform -> Minecraft Server: CONNECTED, QUERY_Y, BUILD, HISTORY_REQUEST,
                              ROLLBACK, REDO, PONG
```

`BUILD` 包含：

```json
{
  "action": "BUILD",
  "taskId": "...",
  "playerUuid": "...",
  "playerName": "HelloKitty",
  "x": 3996, "y": -60, "z": 3996,
  "modelUrl": "http://platform/models/task.json",
  "modelName": "一座小木屋",
  "rotation": 0
}
```

`x/y/z` 是模型原点（最小角），模型 JSON 仍是相对坐标；插件按 `origin + offset` 放置。

## 数据与持久化

- SQLite：`{data-dir}/structengine.db`
  - `admin_account`（超级管理员）
  - `app_user`（Minecraft 服主）
  - `minecraft_server`（服务器归属与 secret 哈希）
  - `model_artifact`（模型产物与归属）
  - `build_task`
  - `chat_message`
  - `audit_log`
- 模型文件：`{data-dir}/models/{id}.json`
- 历史快照仍由插件内存保存，Minecraft Server 重启后清空（与原有行为一致）。

## 测试

```bash
cd backend
mvn test

cd ../frontend
npm test
npm run build
```

测试包含：
- 后端 108 项：Snowflake、JWT、验证码、认证/管理员用户、服务器归属/secret、WebSocket v3/玩家列表/历史 RPC、模型产物、任务超时、审计、模型清洗与文件存储等；
- 前端 11 项：参考图 API、体素解析；
- 真机 RCON 验收脚本已迁移到 `/api/servers/{id}/tasks/deploy`，需要 JWT 与已有模型 ID。
