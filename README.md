# StructEnginePlatform

StructEngine 的云端控制面：管理多个 Minecraft 游戏服、BlueMap 地图选点、玩家选择、Spring AI 对话生成体素模型、参考图生成体素模型，以及模型部署/回滚/恢复。

## 架构

```text
浏览器
  ├─ 管理台 /api（Spring Boot）
  └─ BlueMap iframe（每个 Minecraft Server 自带 BlueMap 5.16）
        │ postMessage 选点
        ▼
StructEnginePlatform
  ├─ WebSocket /ws/game  ← 多个 Minecraft Server 插件（协议 v2）
  ├─ Spring AI → OpenAI-compatible 上游
  ├─ SQLite（模型、任务、对话）
  └─ data/models/*.json（外网可访问）
        ▲
        │ BUILD / ROLLBACK / REDO
Minecraft Server 插件 ── 原有分批构建 / 回滚 / 恢复核心
```

### 关键流程

1. Minecraft Server 插件通过 `ws://platform:8080/ws/game?serverId=...` 连接 Platform。
2. 插件发送 `HELLO`（serverId、serverName、BlueMap URL、mapId、协议版本），并每 10 秒/玩家事件时上报 `PLAYER_LIST`。
3. Web 选择 Minecraft Server → 加载该服的 BlueMap 定制页 → 点击选点（建筑中心 X/Z）→ 选择在线/离线玩家。
4. 在 AI 对话窗口描述建筑；调用 `/api/models/generate` 生成严格 voxel JSON。
5. 生成后预览模型，点击部署；Platform 查询地表 Y、计算中心偏移与旋转，落盘部署模型并发送 `BUILD`。
6. 插件下载模型、按绝对原点入队，使用原有分批构建/快照机制执行，并通过 `BUILD_STATUS` 回报进度。
7. Web 通过 `/api/servers/{serverId}/history` 查看插件历史，调用 rollback/redo。

## 技术栈

- 后端：Java 21、Spring Boot 4、Spring WebSocket、Spring AI、SQLite JDBC
- 前端：Vue 3、Element Plus、Three.js 模型预览
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
| `STRUCTENGINE_ADMIN_PASSWORD` | 单管理员登录密码 | `change-me`（生产必须覆盖） |
| `STRUCTENGINE_GAME_TOKEN` | Minecraft Server WebSocket 握手令牌 | 空（生产必须设置） |
| `STRUCTENGINE_GAME_RPC_TIMEOUT_SECONDS` | 等待插件回包的秒数 | `5` |
| `STRUCTENGINE_AI_BASE_URL` | OpenAI-compatible Base URL | `https://token.dcepstc.com/v1` |
| `STRUCTENGINE_AI_API_KEY` | AI API Key | 空 |
| `STRUCTENGINE_AI_MODEL` | AI 模型 ID | `cn:deepseek-v4.1-flash` |
| `STRUCTENGINE_MAX_MODEL_BLOCKS` | 单模型最大方块数 | `20000` |

> 本地开发把敏感配置写入 `backend/src/main/resources/application-local.yml`，并通过
> `STRUCTENGINE_LOCAL_CONFIG` 指定绝对/相对路径；默认值为 `./src/main/resources/application-local.yml`。
> 该文件已被 `.gitignore` 忽略，并且通过 Maven `resources` 排除，不会进入 Jar。
>
> 如果 API Key 曾出现在聊天/日志中，请立即在服务商侧轮换。

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
export STRUCTENGINE_WS_URL=ws://192.168.1.201:8080/ws/game
export STRUCTENGINE_SERVER_ID=neoforge-local
export STRUCTENGINE_SERVER_NAME="NeoForge 测试服"
export STRUCTENGINE_WS_TOKEN=change-me
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

1. 打开 `http://192.168.1.201:8080/`，输入管理员密码。
2. 选择游戏服。
3. 在 BlueMap 地图点击选择建筑中心。
4. 选择在线玩家，或切换到离线玩家输入名称/UUID。
5. 在 AI 对话窗口讨论方案；填写模型描述。
6. 点击“生成模型”，等待模型预览。
7. 选择朝向（南/东/北/西/朝向玩家），点击“部署到 ...”。
8. 在任务状态中查看 `QUEUED → DOWNLOADING → BUILDING → COMPLETED`。
9. 在构建历史中执行回滚/恢复。

### 参考图建模（三视图仅为建议）

在顶部导航切换到“三视图建模”页：

- 上传 **1~3 张参考图**即可。正视图、侧视图、俯视图只是推荐输入，不要求齐全。
- 参考图不足三视图时也可以直接提交：后端会把图片与可选补充说明发送给多模态模型，由模型推断缺失的进深、侧面和屋顶，补全为完整建筑。
- 可填写建筑类型、层数、屋顶、材质、颜色、门窗位置等补充说明，帮助模型更准确地补全。
- 生成成功后返回体素模型 JSON 地址，可按 `/buildmodel <url>` 在游戏内构建。

```bash
curl -b cookies.txt -F "images=@front.png" -F "images=@side.png" \
  -F "description=两层石砖小屋，红色木屋顶，正面开门" \
  http://192.168.1.201:8080/api/generate
```

## HTTP API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 管理员登录，返回会话 Cookie |
| GET | `/api/auth/status` | 登录状态 |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/servers` | 已连接游戏服、BlueMap URL、玩家列表 |
| GET | `/api/servers/{serverId}/history` | 查询玩家构建历史 |
| POST | `/api/servers/{serverId}/rollback` | 回滚指定历史 |
| POST | `/api/servers/{serverId}/redo` | 恢复指定历史 |
| POST | `/api/ai/chat` | AI 对话 |
| GET | `/api/ai/chat/{sessionId}` | 对话历史 |
| POST | `/api/models/generate` | 对话描述生成模型 |
| POST | `/api/generate` | 参考图（1~3 张）+ 可选 `description` 生成体素模型 |
| POST | `/api/tasks/deploy` | 部署模型 |
| GET | `/api/tasks/{taskId}` | 任务状态 |
| GET | `/api/tasks?serverId=&playerUuid=` | 玩家任务列表 |
| GET | `/models/{filename}` | 模型公开分发（Minecraft Server 下载） |

除 `/models/**`、`/api/auth/**` 与游戏服 WebSocket 外，API 需要管理员会话。

> 旧 `/api/map/build` 与 Squaremap 页面已删除。

## WebSocket 协议 v2

主要消息：

```text
Minecraft Server -> Platform: HELLO, PLAYER_LIST, RESPONSE_Y, BUILD_STATUS, HISTORY_RESPONSE, ERROR, PING
Platform -> Minecraft Server: QUERY_Y, BUILD, HISTORY_REQUEST, ROLLBACK, REDO, PONG
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
  - `model_draft`
  - `build_task`
  - `chat_message`
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
- WebSocket 握手/HELLO/玩家列表/历史 RPC 集成测试；
- Spring AI 配置启动与真实上游调用（本地配置存在时）；
- 模型生成/部署/回滚的 API 与真机 RCON 验收脚本。
