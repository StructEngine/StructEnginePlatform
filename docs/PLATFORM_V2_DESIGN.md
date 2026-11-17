# StructEnginePlatform v2 详细设计

> 状态：待评审  
> 范围：Platform 侧多用户、JWT 鉴权、服务器归属与 secret、两大业务统一、模型产物管理、全表雪花主键  
> 决策基线：无存量数据；Snowflake 落库 `INTEGER`、Java `long`、API 字符串；JWT 使用 `Authorization: Bearer` + `localStorage`；管理员/用户统一登录；服务器 secret 存 SHA-256 哈希、仅创建时显示一次、可轮换。

---

## 1. 目标与非目标

### 1.1 目标

1. 从“单管理员 + Session”升级为“超级管理员 + 多用户 + JWT + Spring Security”。
2. 引入用户自助注册、图形验证码、管理员用户管理。
3. 引入 Minecraft 服务器归属：用户只能管理自己的服务器，管理员可管理全部。
4. 每台服务器一个独立 secret，插件凭 secret 注册并保持 WebSocket；协议升到 v3。
5. 把“AI 地图建造”和“参考图生成”收进服务器管理，共用服务器上下文。
6. AI 配置收敛为一处，两个业务都使用 Spring AI。
7. 模型产物有归属、可列表、可管理；JSON 链接保持永久公开。
8. 所有表主键统一改为雪花 ID。

### 1.2 非目标（本版不做）

- 多实例/集群部署。当前仍是单实例：WebSocket 注册表、待处理 RPC、验证码都在 JVM 内存；SQLite 与模型文件在本地。
- 邮箱、找回密码、第三方登录。
- 多管理员角色细分（本版只有超级管理员 `admin` 与普通用户）。
- 服务器共享/多人协作（一台服务器只有一个 owner）。
- 多世界选择。当前插件侧 `QUERY_Y`/`BUILD` 仍使用默认世界；如果服主有多世界，建议在协议 v3 里预留 `world` 字段（见 5.4）。
- 模型产物的硬删除。公开链接是永久策略，删除只做软删除。

---

## 2. 关键决策

| 决策 | 结论 |
|---|---|
| 数据库 | 继续 SQLite |
| 主键 | 全表 Snowflake；DB `INTEGER PRIMARY KEY`，Java `long`，JSON 字符串 |
| 存量数据 | 无；按新 schema 重建，不保留旧 UUID 模型链接 |
| 管理员 | 单独 `admin_account` 表，固定用户名 `admin`，首次安装初始化 |
| 用户 | `app_user` 表，自助注册 |
| 登录 | 统一 `POST /api/auth/login`，先查 admin 再查 user，返回角色 |
| 鉴权 | Spring Security + JWT；`Authorization: Bearer`；前端 `localStorage` |
| 验证码 | 自绘 PNG，内存存储，5 分钟 TTL，一次性 |
| 服务器身份 | 每服 UUID secret；DB 存 SHA-256 哈希；创建/轮换时仅返回一次 |
| 插件协议 | v3；握手与所有上行消息都带 secret；平台按 secret 反查服务器 |
| AI | 统一 `spring.ai.openai.*`；对话与参考图都走 Spring AI |
| 模型归属 | `model_artifact.owner_user_id` + `server_id`；管理员可看全部 |
| 模型公开 | `GET /models/{id}.json` 永久公开；删除只隐藏管理记录 |

---

## 3. 数据模型

### 3.1 Snowflake ID

- 结构：41 bit 时间戳（自定义 epoch）+ 10 bit workerId + 12 bit sequence。
- workerId：`STRUCTENGINE_SNOWFLAKE_WORKER_ID`，取值 0~1023；单实例默认 0。
- 时钟回拨：小幅等待，大幅直接抛错，避免生成重复 ID。
- Java 类型：`long`。
- JSON：字符串。所有响应 DTO 的 `id`、`ownerUserId`、`serverId`、`modelId`、`taskId` 等标识字段都输出字符串；请求可接受字符串。
- SQLite：`INTEGER PRIMARY KEY`（64 位有符号整数，不使用 `AUTOINCREMENT`）。
- 禁止在数据库中依赖自增；所有 INSERT 必须显式写入雪花 ID。
- 前端所有 ID 按字符串处理，不使用 `Number(id)`、不做数值大小比较。

### 3.2 DDL

> SQLite 方言；时间统一存 epoch milliseconds（`INTEGER`）。

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;

CREATE TABLE IF NOT EXISTS admin_account (
  id                  INTEGER PRIMARY KEY,
  username            TEXT    NOT NULL COLLATE NOCASE UNIQUE,
  password_hash       TEXT    NOT NULL,
  token_version       INTEGER NOT NULL DEFAULT 0,
  must_change_password INTEGER NOT NULL DEFAULT 0,
  created_at          INTEGER NOT NULL,
  updated_at          INTEGER NOT NULL,
  last_login_at       INTEGER
);

CREATE TABLE IF NOT EXISTS app_user (
  id                  INTEGER PRIMARY KEY,
  username            TEXT    NOT NULL COLLATE NOCASE UNIQUE,
  password_hash       TEXT    NOT NULL,
  status              TEXT    NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'DISABLED')),
  token_version       INTEGER NOT NULL DEFAULT 0,
  created_at          INTEGER NOT NULL,
  updated_at          INTEGER NOT NULL,
  last_login_at       INTEGER
);

CREATE TABLE IF NOT EXISTS minecraft_server (
  id                  INTEGER PRIMARY KEY,
  owner_user_id       INTEGER NOT NULL,
  name                TEXT    NOT NULL,
  secret_hash         TEXT    NOT NULL UNIQUE,
  status              TEXT    NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'ONLINE', 'OFFLINE', 'DISABLED')),
  blue_map_url        TEXT,
  map_id              TEXT,
  last_seen_at        INTEGER,
  registered_at       INTEGER,
  secret_rotated_at   INTEGER,
  deleted_at          INTEGER,
  created_at          INTEGER NOT NULL,
  updated_at          INTEGER NOT NULL,
  FOREIGN KEY (owner_user_id) REFERENCES app_user(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS model_artifact (
  id                  INTEGER PRIMARY KEY,
  owner_user_id       INTEGER NOT NULL,
  server_id           INTEGER NOT NULL,
  source              TEXT    NOT NULL
                              CHECK (source IN ('MAP_AI', 'REFERENCE_IMAGE')),
  prompt              TEXT,
  model_url           TEXT    NOT NULL,
  block_count         INTEGER NOT NULL,
  min_x               INTEGER NOT NULL,
  max_x               INTEGER NOT NULL,
  min_y               INTEGER NOT NULL,
  max_y               INTEGER NOT NULL,
  min_z               INTEGER NOT NULL,
  max_z               INTEGER NOT NULL,
  created_at          INTEGER NOT NULL,
  deleted_at          INTEGER,
  FOREIGN KEY (owner_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  FOREIGN KEY (server_id) REFERENCES minecraft_server(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS build_task (
  id                  INTEGER PRIMARY KEY,
  owner_user_id       INTEGER NOT NULL,
  server_id           INTEGER NOT NULL,
  model_id            INTEGER NOT NULL,
  player_uuid         TEXT,
  player_name         TEXT    NOT NULL,
  center_x            INTEGER NOT NULL,
  center_y            INTEGER NOT NULL,
  center_z            INTEGER NOT NULL,
  origin_x            INTEGER NOT NULL,
  origin_y            INTEGER NOT NULL,
  origin_z            INTEGER NOT NULL,
  rotation            INTEGER NOT NULL DEFAULT 0,
  status              TEXT    NOT NULL DEFAULT 'QUEUED',
  placed              INTEGER NOT NULL DEFAULT 0,
  total               INTEGER NOT NULL DEFAULT 0,
  error               TEXT,
  created_at          INTEGER NOT NULL,
  updated_at          INTEGER NOT NULL,
  FOREIGN KEY (owner_user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  FOREIGN KEY (server_id) REFERENCES minecraft_server(id) ON DELETE RESTRICT,
  FOREIGN KEY (model_id) REFERENCES model_artifact(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS chat_message (
  id                  INTEGER PRIMARY KEY,
  server_id           INTEGER NOT NULL,
  session_id          TEXT    NOT NULL,
  actor_type          TEXT    NOT NULL CHECK (actor_type IN ('ADMIN', 'USER')),
  actor_id            INTEGER NOT NULL,
  role                TEXT    NOT NULL CHECK (role IN ('user', 'assistant')),
  content             TEXT    NOT NULL,
  created_at          INTEGER NOT NULL,
  FOREIGN KEY (server_id) REFERENCES minecraft_server(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS audit_log (
  id                  INTEGER PRIMARY KEY,
  actor_type          TEXT    NOT NULL CHECK (actor_type IN ('ADMIN', 'USER', 'SYSTEM')),
  actor_id            INTEGER,
  actor_name          TEXT,
  action              TEXT    NOT NULL,
  server_id           INTEGER,
  model_id            INTEGER,
  task_id             INTEGER,
  x                   INTEGER,
  y                   INTEGER,
  z                   INTEGER,
  ip                  TEXT,
  result              TEXT    NOT NULL,
  detail              TEXT,
  created_at          INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_server_owner ON minecraft_server(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_server_status ON minecraft_server(status);
CREATE INDEX IF NOT EXISTS idx_model_owner ON model_artifact(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_model_server ON model_artifact(server_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_owner ON build_task(owner_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_server ON build_task(server_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_actor_server ON chat_message(actor_type, actor_id, server_id, session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_server ON audit_log(server_id, created_at DESC);
```

- `journal_mode=WAL` 只需在数据库上设置一次；`foreign_keys=ON` 与 `busy_timeout` 是连接级，需通过 SQLite JDBC URL 或 Hikari `connection-init-sql` 保证每个连接生效。
- `PRAGMA user_version` 作为 schema 版本，不额外建版本表。

### 3.3 关系

```text
admin_account（单行，固定 admin）

app_user 1 ─── N minecraft_server
app_user 1 ─── N model_artifact
app_user 1 ─── N build_task
minecraft_server 1 ─── N model_artifact
minecraft_server 1 ─── N build_task
model_artifact 1 ─── N build_task
minecraft_server 1 ─── N chat_message
```

- 普通用户只能访问 `owner_user_id = 当前用户` 的资源。
- 管理员绕过归属校验，可访问全部。
- **资源 owner 始终是普通用户**：管理员是平台运营者，不拥有服务器/模型；管理员对某用户服务器执行生成/部署时，产物的 `owner_user_id` 仍归该服务器 owner，审计日志记录 actor=ADMIN。管理员若需要自己的服务器，应创建一个普通用户账号。
- `chat_message` 用 `actor_type + actor_id` 记录实际发起人（管理员或用户），`server_id` 绑定服务器；管理员在用户服务器上的对话不会伪装成该用户。
- `minecraft_server` 与 `model_artifact` 使用软删除（`deleted_at`），避免破坏公开链接和历史记录。
- `app_user` 删除采用“无资源才允许硬删除”，否则返回 409；管理员可先禁用。

### 3.4 迁移策略

由于确认无存量数据：

- 使用 `PRAGMA user_version` 做 schema 版本号，`SchemaMigrator` 负责建表/升级。
- 首次启动检测到旧 `model_draft` / `build_task` / `chat_message`：
  - 如果三张表都为空：删除旧表，按新 schema 重建。
  - 如果非空：启动失败并提示先备份/导出，避免静默丢数据。
- 提供 `scripts/reset-db.sh`（仅开发用）删除 `data/structengine.db`、`data/models`、`data/images`。
- 迁移完成后 `PRAGMA user_version = 1`。
- `data/` 目录不进 Git；模型公开链接在无存量策略下不做 legacy alias。

---

## 4. 认证与用户体系

### 4.1 账号规则

| 项目 | 规则 |
|---|---|
| 管理员用户名 | 固定 `admin`，不可注册 |
| 用户名 | `^[A-Za-z]{4,16}$`，仅英文字母，不区分大小写唯一 |
| 密码 | 6~16 个字符，无复杂度要求；BCrypt 存储 |
| 用户状态 | `ACTIVE` / `DISABLED` |
| 管理员初始化 | 首次安装读取 `STRUCTENGINE_ADMIN_INITIAL_PASSWORD`；为空则启动失败 |
| 管理员改密 | 支持；改密后递增 `token_version`，旧 JWT 失效 |

### 4.2 图形验证码

- `GET /api/auth/captcha` 公开。
- 返回：

```json
{
  "captchaId": "1730000000000000001",
  "image": "data:image/png;base64,...",
  "expiresIn": 300
}
```

- 4 位字符，排除易混淆字符；PNG 由后端 `BufferedImage` 生成。
- 内存 `ConcurrentHashMap` 存储：`captchaId -> {code, expiresAt, used}`；5 分钟 TTL，一次性。
- 校验不区分大小写；失败即作废；过期/重复使用返回 400/429。
- 定时清理过期条目；单实例足够，未来多实例换 Redis/DB。
- 注册、登录都必须校验验证码；管理员统一登录同样需要。
- 登录/注册按 IP + 用户名做简单限流（例如 5 次/分钟），先内存实现。

### 4.3 JWT 与 Spring Security

- 依赖：`spring-boot-starter-security`、`spring-security-oauth2-jose`。
- `SecurityFilterChain`：
  - `sessionCreationPolicy(STATELESS)`
  - `csrf.disable()`
  - 公开：`/api/auth/captcha`、`/api/auth/register`、`/api/auth/login`、`/models/**`、`/ws/**`、静态资源
  - 登录后：`/api/**`
  - 管理员：`/api/admin/**` 要求 `ROLE_ADMIN`
- `JwtAuthenticationFilter` 解析 `Authorization: Bearer <token>`：
  - 校验签名、过期、`sub`、`role`、`ver`
  - 按 `sub` 查 admin/user，校验 `token_version` 与状态
  - 写入 `SecurityContext`
- `JwtService`：
  - HS256；`STRUCTENGINE_JWT_SECRET` 至少 32 字节；为空启动失败
  - TTL：`STRUCTENGINE_JWT_TTL_HOURS` 默认 24
  - claims：`sub`、`username`、`role`、`ver`、`iat`、`exp`、`jti`
- 密码编码：`BCryptPasswordEncoder`。
- 401/403 统一 JSON；不返回 HTML 登录页。
- 不使用 Session；删除 `AdminAuthInterceptor`。
- 前端 `localStorage` 存 token；每次请求带 `Authorization`；收到 401 清 token 并跳转登录。
- 安全说明：localStorage 有 XSS 风险；后续加 CSP、避免 `v-html`、依赖版本审计。若以后要更强的 XSS 防护，可改 HttpOnly Cookie + CSRF。

### 4.4 接口

#### 公开

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/captcha` | 获取验证码 |
| POST | `/api/auth/register` | 用户注册；验证码校验通过后直接返回 token |
| POST | `/api/auth/login` | 管理员/用户统一登录 |

注册请求：

```json
{
  "username": "Steve",
  "password": "123456",
  "captchaId": "1730000000000000001",
  "captchaCode": "A7K9"
}
```

登录响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": {
      "id": "1730000000000000002",
      "username": "Steve",
      "role": "USER",
      "mustChangePassword": false
    }
  }
}
```

#### 登录后

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/auth/me` | 当前身份 |
| POST | `/api/auth/password` | 修改自己的密码；返回新 token |
| POST | `/api/auth/logout` | 客户端删除 token；服务端无状态，可选空实现 |

#### 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/admin/users?page=1&size=20&keyword=` | 用户列表 |
| POST | `/api/admin/users` | 创建用户 |
| PUT | `/api/admin/users/{id}` | 改用户名/状态 |
| PUT | `/api/admin/users/{id}/password` | 重置密码 |
| DELETE | `/api/admin/users/{id}` | 删除；有服务器/模型时返回 409 |
| PUT | `/api/admin/password` | 修改管理员密码 |
| GET | `/api/admin/servers` | 全部服务器 |
| GET | `/api/admin/models` | 全部模型 |

### 4.5 管理员初始化

- 启动时若 `admin_account` 为空：
  - 读取 `STRUCTENGINE_ADMIN_INITIAL_PASSWORD`。
  - 为空：启动失败，提示必须设置；避免默认密码。
  - 非空：创建 `admin`，BCrypt 入库，`must_change_password=1`。
- 首次登录返回 `mustChangePassword=true`；前端强制跳转改密页。
- 改密后 `must_change_password=0`，`token_version++`。

---

## 5. 服务器管理与 WebSocket v3

### 5.1 服务器生命周期

```text
创建（PENDING）
  └─ 插件用 secret 连接 + HELLO 上报 BlueMap
       └─ ONLINE
            ├─ 断线 → OFFLINE
            └─ 管理员/用户禁用 → DISABLED（拒绝连接）

PENDING / OFFLINE 都可以重新连接；DISABLED 必须启用后才能连接。
软删除后 secret 立即失效，连接被关闭。
```

- 创建服务器只填 `name`；平台生成 UUID secret，返回一次。
- 数据库存 `secret_hash = SHA-256(secret)`，不存明文。
- 用户可轮换 secret；轮换后旧 secret 立即失效，平台关闭该服现有 WS。
- BlueMap URL / mapId 由插件 `HELLO` 上报，平台持久化；用户界面只读展示。
- 平台重启时，把所有 `ONLINE` 置为 `OFFLINE`，等待插件重连。
- `last_seen_at` 在每次握手/消息/心跳更新。

### 5.2 secret 规则

- 格式：UUID v4 字符串。
- 创建/轮换接口响应中返回明文一次；列表/详情永远不返回 secret。
- 数据库只存 SHA-256；校验时用常量时间比较。
- 日志、审计、异常信息中禁止出现 secret；统一打码为 `***`。
- 插件配置建议：

```bash
export STRUCTENGINE_PLATFORM_URL=wss://platform.example.com/ws/game
export STRUCTENGINE_SERVER_SECRET=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

也兼容 `?secret=` 查询参数，但推荐 `X-StructEngine-Secret` 请求头，避免 URL 进入访问日志。

### 5.3 服务器管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/servers` | 当前用户的服务器列表（管理员用 `/api/admin/servers`） |
| POST | `/api/servers` | 创建服务器，body `{name}`；响应返回一次 secret |
| GET | `/api/servers/{id}` | 详情：状态、BlueMap、mapId、lastSeen；不返回 secret |
| PUT | `/api/servers/{id}` | 修改名称 |
| POST | `/api/servers/{id}/secret/rotate` | 轮换 secret；返回新 secret 一次并关闭旧连接 |
| POST | `/api/servers/{id}/disable` | 禁用；拒绝插件连接 |
| POST | `/api/servers/{id}/enable` | 启用；恢复允许连接 |
| DELETE | `/api/servers/{id}` | 软删除；secret 立即失效，模型/任务保留 |
| GET | `/api/admin/servers` | 管理员查看全部服务器 |

创建响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "server": {
      "id": "1730000000000000010",
      "name": "生存一服",
      "status": "PENDING",
      "blueMapUrl": null,
      "mapId": null
    },
    "secret": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "wsUrl": "wss://platform.example.com/ws/game"
  }
}
```

- 普通用户只能创建/管理自己的服务器；管理员可对任意服务器执行详情/修改/轮换/禁用/删除，但不拥有服务器。
- 管理员若需要自己的服务器，创建一个普通用户账号后使用该账号。
- 创建响应中的 `secret` 只出现一次，关闭弹窗后无法再取；忘记只能轮换。

### 5.4 WebSocket 协议 v3

#### 握手

```http
GET /ws/game
X-StructEngine-Secret: <uuid>
```

或：

```text
ws://platform/ws/game?secret=<uuid>
```

平台：

1. 取 secret，计算 SHA-256。
2. 按 `secret_hash` 查 `minecraft_server`。
3. 不存在 / `DISABLED` / `deleted_at != null` → 拒绝握手。
4. 绑定 `serverId` 到 WebSocket session；不再接受客户端自报 serverId。
5. 等待 `HELLO`；超时关闭。

#### 上行消息（插件 → 平台）

所有消息都带 `secret`，平台逐条校验：

```json
{
  "action": "HELLO",
  "protocolVersion": 3,
  "secret": "<uuid>",
  "serverName": "生存一服",
  "blueMapUrl": "https://map.example.com/structengine-map.html",
  "mapId": "world",
  "players": []
}
```

| action | 关键字段 |
|---|---|
| `HELLO` | `secret`, `protocolVersion:3`, `serverName`, `blueMapUrl`, `mapId` |
| `PLAYER_LIST` | `secret`, `full`, `players[]` |
| `RESPONSE_Y` | `secret`, `taskId`, `y` |
| `BUILD_STATUS` | `secret`, `taskId`, `state`, `placed`, `total`, `message` |
| `HISTORY_RESPONSE` | `secret`, `requestId`, `history[]` |
| `ERROR` | `secret`, `taskId` 或 `requestId`, `message` |
| `PING` | `secret` |

#### 下行消息（平台 → 插件）

| action | 关键字段 |
|---|---|
| `CONNECTED` | `serverId`（平台侧雪花 ID 字符串）, `serverName`, `protocolVersion:3` |
| `QUERY_Y` | `taskId`, `x`, `z`, `playerName` |
| `BUILD` | `taskId`, `playerUuid`, `playerName`, `x/y/z` 原点, `modelUrl`, `modelName`, `rotation` |
| `HISTORY_REQUEST` | `requestId`, `playerUuid`, `playerName` |
| `ROLLBACK` / `REDO` | `requestId`, `playerUuid`, `playerName`, `historyId` |
| `PONG` | - |

#### 任务 ID

- 平台内部 `long`，发送给插件时转字符串。
- 插件原样回传字符串；平台解析为 `long`。
- `requestId` 仍可用 UUID 字符串（不是表主键），但建议统一用雪花字符串，减少两套 ID 概念。

#### 校验规则

- `protocolVersion != 3`：回 `ERROR` 并关闭。
- 缺少 secret 或 secret 与握手绑定的服务器不一致：关闭（4401）。
- `HELLO` 之前收到其他消息：关闭。
- 单条消息大小上限显式配置（例如 1 MB），避免历史/玩家列表撑爆默认缓冲区。
- 平台重启：所有服务器置 `OFFLINE`；插件重连后重新 `HELLO`。

### 5.5 插件侧改动（StructEngine 仓库）

- 配置：
  - 删除 `STRUCTENGINE_SERVER_ID`、`STRUCTENGINE_WS_TOKEN`。
  - 新增 `STRUCTENGINE_PLATFORM_URL`、`STRUCTENGINE_SERVER_SECRET`。
- `GameServerWsClient`：
  - 握手加 `X-StructEngine-Secret`。
  - `RemoteMessage` 增加 `secret` 字段。
  - 所有上行消息填充 secret。
  - 接收 `CONNECTED` 后记录平台返回的 `serverId`。
  - 重连沿用 secret。
- 协议版本升 3；旧版插件不兼容，Platform 与插件需一起发布。
- 插件首次注册上报 BlueMap 地址；地图页 `structengine-map.html` 支持 `?mapId=world`，不再硬编码 `index.html#world`。

---

## 6. 两大业务与 AI

### 6.1 入口

全部收进服务器上下文：

```text
/login  /register
  └─ /servers                        我的服务器
       ├─ /servers/{id}              概览：状态、secret、BlueMap、插件配置
       ├─ /servers/{id}/build        AI 地图建造
       ├─ /servers/{id}/studio       参考图生成
       ├─ /servers/{id}/models       模型产物
       └─ /servers/{id}/tasks        构建任务/历史
  └─ /admin/users                    管理员：用户
  └─ /admin/servers                  管理员：全部服务器
  └─ /admin/models                   管理员：全部模型
```

- `BuildStudioPage` 去掉服务器下拉，服务器由路由参数提供。
- `GeneratePage` 也带 `serverId`；生成产物归属该服务器与当前用户。
- 没有服务器时，引导创建服务器。

### 6.2 AI 配置统一

只保留一处 Spring AI 配置：

```yaml
spring:
  ai:
    openai:
      base-url: ${STRUCTENGINE_AI_BASE_URL:https://token.dcepstc.com/v1}
      api-key: ${STRUCTENGINE_AI_API_KEY:}
      chat:
        options:
          model: ${STRUCTENGINE_AI_MODEL:cn:deepseek-v4.1-flash}
          temperature: 0.7
```

删除：

- `structengine.ai-api-url`、`ai-api-key`、`ai-model`
- `AppProperties` 中对应字段
- `LlmClient`、`OpenAiCompatibleLlmClient`、`ChatCompletionDtos`
- 自定义 180s `RestClient`

保留：

- `PromptProvider`（提示词）
- `JsonSanitizer`（JSON 清洗）
- `AiChatService`（改为注入 `ChatClient`，已有）

### 6.3 参考图生成改为 Spring AI

新增 `VisionAiService`：

```java
UserMessage user = UserMessage.builder()
        .text(instruction)
        .media(mediaList)
        .build();

String raw = chatClient.prompt()
        .system(PromptProvider.systemPrompt())
        .messages(user)
        .call()
        .content();
```

- `Media` 使用 `Media.builder().mimeType(mimeType).data(new ByteArrayResource(bytes)).build()`。
- MIME 仍由文件头识别（保留 `ImageMimeType`），映射到 `MimeTypeUtils.IMAGE_JPEG/PNG/GIF/WEBP`。
- 多图仍是 1~3 张；`description` 逻辑不变。
- 超时/重试交给 Spring AI/底层 HTTP 客户端配置，不再硬编码 180 秒。
- 测试 mock `ChatClient`/`ChatModel`，不再 mock `LlmClient`。

---

## 7. 模型产物

### 7.1 业务接口总览

所有路径都以 `serverId` 为上下文，进入 Service 前先做服务器归属校验（管理员绕过）：

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/servers/{id}/ai/chat` | AI 对话；body `{sessionId, message}` |
| GET | `/api/servers/{id}/ai/chat/{sessionId}` | 对话历史 |
| POST | `/api/servers/{id}/models/generate` | AI 地图建造：描述生成模型 |
| POST | `/api/servers/{id}/models/generate-from-images` | 参考图生成：multipart `images[] + description` |
| GET | `/api/servers/{id}/models?page=&size=&source=` | 模型列表 |
| DELETE | `/api/servers/{id}/models/{modelId}` | 模型软删除 |
| POST | `/api/servers/{id}/tasks/deploy` | 部署；body `{playerUuid, playerName, modelId, centerX, centerZ, rotation}` |
| GET | `/api/servers/{id}/tasks?playerUuid=` | 任务列表 |
| GET | `/api/servers/{id}/tasks/{taskId}` | 任务状态 |
| GET | `/api/servers/{id}/history?playerUuid=&playerName=` | 插件构建历史 |
| POST | `/api/servers/{id}/history/{historyId}/rollback` | 回滚 |
| POST | `/api/servers/{id}/history/{historyId}/redo` | 恢复 |

- 路径中的 `id`/`modelId`/`taskId` 均为雪花字符串。
- 管理员调用以上接口时，Service 按 `serverId` 取服务器并允许管理员绕过 owner 校验；生成/部署产物仍归服务器 owner。
- 对话历史按 `server_id + actor_type + actor_id + session_id` 过滤，管理员与普通用户的会话互不可见；`sessionId` 长度限制 1~64。
- 旧的 `/api/models/generate`、`/api/generate`、`/api/tasks/deploy`、`/api/ai/chat` 在新版中废弃，前端全部迁移到服务器上下文接口。

### 7.2 生成与登记

两条业务都写入 `model_artifact`：

| 来源 | `source` | 入口 |
|---|---|---|
| AI 地图建造 | `MAP_AI` | `POST /api/servers/{id}/models/generate` |
| 参考图生成 | `REFERENCE_IMAGE` | `POST /api/servers/{id}/models/generate-from-images` |

- 生成时校验服务器归属。
- 模型文件写 `data/models/{id}.json`；`id` 为雪花字符串。
- `model_url` 通过 `BaseUrlResolver` 生成，支持 `STRUCTENGINE_PUBLIC_BASE_URL` 与反向代理头。
- 参考图原图仍写 `data/images/{id}_{index}.jpg`。
- 生成成功写 `model_artifact`，包含包围盒、方块数、prompt、来源。

### 7.3 查询与删除

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/servers/{id}/models?page=&size=&source=` | 当前服务器模型（归属校验） |
| GET | `/api/admin/models?page=&size=&source=&serverId=` | 管理员全部模型 |
| DELETE | `/api/servers/{id}/models/{modelId}` | 软删除；文件与公开链接保留 |

- 列表默认按 `created_at DESC`。
- 软删除只设置 `deleted_at`；`/models/{id}.json` 仍可访问，满足“永久公开”。
- 物理清理不在本版自动执行；以后可做“仅清理从未被引用”的策略，但不能破坏已公开链接。

### 7.4 部署与归属

- `POST /api/servers/{serverId}/tasks/deploy` 必须校验：
  - 当前用户拥有该服务器（管理员绕过）
  - `model.server_id == serverId`
  - `model.owner_user_id == 当前用户`（管理员绕过）
  - `model.deleted_at == null`
- 回滚/恢复同样校验服务器归属。
- 部署前前端弹确认框，展示服务器、坐标、模型、方块数、朝向，降低误操作。
- 审计日志记录 DEPLOY / ROLLBACK / REDO / MODEL_DELETE。

---

## 8. 授权矩阵

| 资源/操作 | 未登录 | 普通用户 | 管理员 |
|---|---|---|---|
| 验证码/注册/登录 | ✅ | ✅ | ✅ |
| `/models/{id}.json` | ✅ | ✅ | ✅ |
| 查看自己的服务器 | ❌ | ✅ | ✅ |
| 查看他人服务器 | ❌ | ❌（404） | ✅ |
| 创建服务器 | ❌ | ✅ | ❌（需普通用户账号，见第 14 节） |
| 修改/删除/轮换自己服务器 | ❌ | ✅ | ✅（对任意服务器） |
| 管理全部服务器 | ❌ | ❌ | ✅ |
| 自己服务器的 AI/部署/回滚 | ❌ | ✅ | ✅ |
| 他人服务器的 AI/部署/回滚 | ❌ | ❌（404） | ✅ |
| 自己的模型/任务/历史 | ❌ | ✅ | ✅ |
| 全部模型/任务/历史 | ❌ | ❌ | ✅ |
| 管理员用户 CRUD | ❌ | ❌ | ✅ |
| WebSocket 游戏服接入 | secret 校验 | - | - |

- 普通用户访问不属于自己的资源统一返回 404，避免枚举。
- 管理员访问 `/api/servers/{id}` 等用户接口时，服务层按角色绕过 owner 校验。
- 任何部署/回滚都必须再次从数据库校验归属，不能只信前端传参。

---

## 9. 前端信息架构与路由

### 9.1 依赖与基础

- 新增 `vue-router`。
- 新增 `src/stores/auth.js`：token、user、login/logout、初始化 `/api/auth/me`。
- `src/api/http.js`：统一 `fetch`，自动加 `Authorization`，401 清 token 并跳登录。
- `App.vue` 改为路由出口 + 布局；删除顶部“AI 地图建造/三视图建模”主导航。

### 9.2 路由

```text
/login
/register
/servers
/servers/:serverId
/servers/:serverId/build
/servers/:serverId/studio
/servers/:serverId/models
/servers/:serverId/tasks
/admin/users
/admin/servers
/admin/models
```

- 路由守卫：未登录 → `/login`；普通用户访问 `/admin/**` → `/servers`；管理员登录后默认 `/admin/users`，也可切到自己的服务器视图。
- 404 → 简单错误页。

### 9.3 页面要点

- 登录/注册：验证码图片、刷新、错误提示；注册成功直接登录。
- 服务器列表：创建按钮、状态徽标、最后在线时间。
- 服务器详情：
  - 概览：状态、BlueMap、mapId、lastSeen、secret 已配置提示、轮换按钮（二次确认）。
  - BlueMap iframe 使用 `blueMapUrl + ?mapId=<mapId>`；定制页读取 `mapId` 后加载 `index.html#<mapId>`，不再硬编码 `world`。
  - 创建成功弹窗：一次性展示 secret + 插件配置片段 + 复制按钮 + “关闭后不再显示”警告。
  - AI 地图建造：复用现有工作台，去掉服务器下拉。
  - 参考图生成：复用现有上传/预览，提交时带 serverId。
  - 模型产物：列表、来源、方块数、创建时间、复制 `/buildmodel`、删除（软删）。
  - 任务：当前任务 + 历史任务、错误信息、回滚/恢复。
- 管理员：
  - 用户列表：搜索、创建、禁用/启用、重置密码、删除（有资源 409 提示）。
  - 全部服务器：按 owner 筛选。
  - 全部模型：按 owner/server/source 筛选。

---

## 10. 响应与错误规范

- 成功：HTTP 200 + `{code:200,message:"success",data:...}`。
- 参数错误：HTTP 400 + `{code:400,message:"..."}`。
- 未登录/Token 失效：HTTP 401 + `{code:401,message:"未登录或登录已过期"}`。
- 无权限：HTTP 403；普通用户访问他人资源用 404。
- 不存在：HTTP 404。
- 冲突：HTTP 409（用户名重复、用户仍有资源不能删）。
- 验证码/限流：HTTP 429。
- 服务端错误：HTTP 500；不再把所有异常都叫“存储失败”。
- 所有 ID 字段为字符串；时间字段为 epoch millis 数字。
- 分页响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [],
    "page": 1,
    "size": 20,
    "total": 0
  }
}
```

---

## 11. 配置变更

### 新增

| 变量 | 说明 | 默认 |
|---|---|---|
| `STRUCTENGINE_JWT_SECRET` | JWT HS256 密钥，至少 32 字节 | 无，必须配置 |
| `STRUCTENGINE_JWT_TTL_HOURS` | Token 有效期 | `24` |
| `STRUCTENGINE_ADMIN_INITIAL_PASSWORD` | 首次安装 admin 密码 | 无，首次必须配置 |
| `STRUCTENGINE_SNOWFLAKE_WORKER_ID` | 雪花 workerId | `0` |

### 保留

- `STRUCTENGINE_SERVER_PORT`
- `STRUCTENGINE_PROJECT_ROOT`
- `STRUCTENGINE_DATA_DIR`
- `STRUCTENGINE_PUBLIC_BASE_URL`
- `STRUCTENGINE_AI_BASE_URL` / `STRUCTENGINE_AI_API_KEY` / `STRUCTENGINE_AI_MODEL`
- `STRUCTENGINE_MAX_MODEL_BLOCKS`
- `STRUCTENGINE_GAME_RPC_TIMEOUT_SECONDS`

### 删除/替换

- `STRUCTENGINE_ADMIN_PASSWORD` → 首次安装改 `STRUCTENGINE_ADMIN_INITIAL_PASSWORD`
- `STRUCTENGINE_GAME_TOKEN` → 每服 secret
- `STRUCTENGINE_AI_*` 的 `structengine.ai-*` 绑定 → 统一映射到 `spring.ai.openai.*`

### 11.1 破坏性变更

- 删除 Session/Cookie 鉴权；旧 `/api/auth/login` 返回结构改变，前端必须同步升级。
- 所有业务 API 改为服务器上下文路径（`/api/servers/{id}/...`），旧顶层路径废弃。
- 业务错误从“HTTP 200 + code”改为匹配的 HTTP 状态码；前端请求层需要适配。
- 所有 ID 从 UUID 改为雪花字符串；旧模型/任务 URL 不再兼容（无存量策略）。
- WebSocket 协议 v3；旧插件（`SERVER_ID + GAME_TOKEN`）无法连接，必须同步升级。
- `STRUCTENGINE_GAME_TOKEN` 移除，改为每服 secret。
- 数据库 schema 重建；旧 `data/structengine.db` 不能直接复用。

---

## 12. 测试计划

### 后端

- Snowflake：并发唯一性、序列递增、时钟回拨。
- Schema：首次建表、版本号、旧空表重建、非空旧表拒绝启动。
- 注册/登录：用户名规则、密码规则、重复用户名、验证码错误/过期/重复使用。
- JWT：签名、过期、`token_version` 失效、禁用用户、角色。
- 管理员：初始化、改密、用户 CRUD、删除有资源用户 409。
- 授权：用户 A 不能读写 B 的服务器/模型/任务；管理员可以。
- 服务器：创建返回 secret 一次、列表不返回 secret、轮换后旧 secret 失效、软删除。
- WebSocket：secret 握手、错误 secret、PENDING→ONLINE、断线→OFFLINE、每条消息 secret 校验、协议版本。
- 模型：两条业务都登记、归属、软删除后 URL 仍可访问、部署越权。
- AI：Spring AI 对话/视觉 mock；配置只读一处。
- 审计：部署/回滚/删除写入日志。

### 前端

- 登录/注册验证码流程。
- Token 注入、401 跳转、路由守卫、管理员路由。
- 服务器创建 secret 一次性展示。
- 服务器详情各 tab 的 serverId 传递。
- 模型列表/删除确认；部署确认框。
- 现有体素解析测试保留。

### 手工验收

1. 首次启动无 admin → 配置初始密码后成功创建。
2. 注册用户 → 登录 → 创建服务器 → 拿到 secret（只显示一次）。
3. 插件配置 secret → 连接 → 状态 ONLINE → BlueMap 地址正确。
4. 用户 A 无法访问用户 B 的服务器/模型（404）。
5. 管理员能看到全部服务器/模型。
6. AI 地图建造与参考图生成都在服务器详情内完成，产物出现在模型列表。
7. 部署 → 任务进度 → 历史 → 回滚/恢复。
8. 轮换 secret → 旧插件断开，新 secret 可重连。

---

## 13. 实施阶段

### Phase 0：设计评审（本文档）

产出：schema、API、协议、迁移、测试计划确认。

### Phase 1：用户体系（A）

- Snowflake + SchemaMigrator + 新表。
- Spring Security + JWT + captcha。
- admin 初始化、用户注册/登录、管理员用户 CRUD。
- 前端登录/注册/改密/管理员用户页。
- 现有业务接口暂只允许 ADMIN（避免用户无服务器可操作）。
- 验收：A 全部测试通过。

### Phase 2：服务器管理与协议（B）

- `minecraft_server`、secret、归属校验。
- WebSocket v3 + 插件改造。
- 服务器列表/详情/创建/轮换/禁用/软删除。
- 两大业务入口迁入服务器详情。
- 验收：服务器归属、secret、WS 全链路。

### Phase 3：AI 与模型产物（C/D）

- 删除自定义 LLM 客户端，统一 Spring AI。
- 参考图生成改用 Spring AI 多模态。
- `model_artifact` 登记两条业务产物、模型列表、软删除。
- 部署/回滚归属校验、审计日志。
- 验收：两条业务产物统一管理。

### Phase 4：加固

- 部署确认、限流、审计查询、CSP/安全头。
- SQLite 索引/备份、日志脱敏。
- 文档与迁移手册。

---

## 14. 风险与待确认

1. **多世界**：当前插件只操作默认世界。若服主有多世界，建议协议 v3 就加 `world` 字段，否则还要升 v4。
2. **secret 每条消息携带**：泄露面比“只握手”大；实现必须日志脱敏，且建议优先用 Header 而非 URL query。
3. **localStorage JWT**：XSS 风险；需要 CSP、输入转义、依赖审计；后续可评估 HttpOnly Cookie。
4. **公开模型永久链接**：软删除后文件仍公开；如果未来要真正删除，需要产品层面重新定义“永久”。
5. **单实例**：验证码、WS 注册表、待处理 RPC 都在内存；扩容前需要 Redis/共享状态。
6. **SQLite 并发写**：任务状态高频更新时需 `busy_timeout`/WAL，后续压测。
7. **旧插件不兼容**：协议 v3 是破坏性升级，需要和插件同版本发布。
8. **用户名规则**：按“全英文”理解为仅 `[A-Za-z]`，不含数字；如果希望字母+数字，需要调整。
9. **JWT TTL**：默认 24 小时；如希望“长期免登录”，可调大或加 refresh token。
10. **参考图产物是否允许部署**：本文默认允许（统一模型产物）；如果只想导出 `/buildmodel`，需要限制。
11. **管理员是否拥有服务器/模型**：当前设计为“管理员不拥有资源，只做全局管理”；管理员要自建服务器需先创建普通用户。如果希望 admin 直接拥有服务器，需要把 owner 从 `app_user` 外键改成 `owner_type + owner_id` 或增加 `owner_admin_id`。

---

## 15. 本轮不做但建议后续补

- 审计日志查询页面。
- 部署前的坐标/方块数二次确认（Phase 4 做）。
- 每用户/每服并发构建限制、日配额。
- 服务器 secret 轮换历史。
- 模型产物的物理清理策略。
- 多世界选择。
- 多实例部署与 Redis 会话/注册表。
