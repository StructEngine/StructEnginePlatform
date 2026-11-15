#!/usr/bin/env python3
"""StructEngine 远程构建真机验收脚本。

流程：
  1. 检查 Platform 是否已连接目标游戏服；
  2. 调用 POST /api/map/build 触发一次构建；
  3. 下载并解析 Platform 生成的模型 JSON；
  4. 通过 Minecraft RCON 逐方块执行 execute unless block，核对世界中
     (x + dx, y + dy, z + dz) 是否真的变成了模型指定的方块。

只依赖 Python 标准库，无需安装第三方包。

示例：
  python3 scripts/verify_remote_build.py \
    --platform http://127.0.0.1:8080 \
    --server-id local-test \
    --x 1000 --z 1000 \
    --player Steve --prompt "验收小屋" \
    --rcon-host 127.0.0.1 --rcon-port 25575 --rcon-password change-me
"""

from __future__ import annotations

import argparse
import json
import socket
import struct
import sys
import time
import urllib.error
import urllib.request

MISMATCH_MARKER = "SE_VERIFY_MISMATCH"
READY_MARKER = "SE_VERIFY_READY"
PROBE_MARKER = "SE_VERIFY_PROBE"


class VerifyError(RuntimeError):
    pass


class RconClient:
    """Minecraft RCON 最小实现：登录 + 单条命令 + 单包响应。"""

    def __init__(self, host: str, port: int, password: str, timeout: float = 5.0) -> None:
        self._sock = socket.create_connection((host, port), timeout=timeout)
        self._sock.settimeout(timeout)
        self._next_id = 1
        try:
            self._auth(password)
        except Exception:
            self.close()
            raise

    def _auth(self, password: str) -> None:
        request_id = self._send_packet(3, password)
        response_id, response_type, _ = self._read_packet()
        if response_id == -1 or response_type != 2:
            raise VerifyError("RCON 鉴权失败，请检查 rcon.password")

    def command(self, command: str) -> str:
        request_id = self._send_packet(2, command)
        # 验收命令输出很短，Minecraft 会以单包返回。
        for _ in range(8):
            response_id, response_type, body = self._read_packet()
            if response_id == request_id and response_type == 0:
                return body
        raise VerifyError(f"RCON 未返回命令响应: {command}")

    def _send_packet(self, packet_type: int, body: str) -> int:
        request_id = self._next_id
        self._next_id += 1
        payload = struct.pack("<ii", request_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
        self._sock.sendall(struct.pack("<i", len(payload)) + payload)
        return request_id

    def _read_packet(self) -> tuple[int, int, str]:
        length = struct.unpack("<i", self._read_exact(4))[0]
        if length < 10 or length > 4 * 1024 * 1024:
            raise VerifyError(f"RCON 包长度异常: {length}")
        payload = self._read_exact(length)
        response_id, response_type = struct.unpack("<ii", payload[:8])
        body = payload[8:-2].decode("utf-8", errors="replace")
        return response_id, response_type, body

    def _read_exact(self, count: int) -> bytes:
        chunks = bytearray()
        while len(chunks) < count:
            chunk = self._sock.recv(count - len(chunks))
            if not chunk:
                raise VerifyError("RCON 连接被服务端关闭")
            chunks.extend(chunk)
        return bytes(chunks)

    def close(self) -> None:
        try:
            self._sock.close()
        except OSError:
            pass


def http_json(method: str, url: str, payload: dict | None = None, timeout: float = 30.0) -> dict:
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise VerifyError(f"HTTP {error.code}: {body}") from error
    except urllib.error.URLError as error:
        raise VerifyError(f"无法访问 {url}: {error.reason}") from error


def fetch_model(url: str) -> list[list]:
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.URLError as error:
        raise VerifyError(f"无法下载模型 {url}: {error.reason}") from error
    try:
        model = json.loads(raw)
    except json.JSONDecodeError as error:
        raise VerifyError(f"模型 JSON 解析失败: {error}") from error
    if not isinstance(model, list) or not model:
        raise VerifyError("模型数据为空或不是数组")
    return model


def validate_model(model: list[list]) -> list[tuple[str, int, int, int]]:
    blocks: list[tuple[str, int, int, int]] = []
    for index, row in enumerate(model):
        if not isinstance(row, list) or len(row) != 4:
            raise VerifyError(f"模型第 {index} 行不是 [id,x,y,z]: {row!r}")
        block_id, dx, dy, dz = row
        if not isinstance(block_id, str) or ":" not in block_id:
            raise VerifyError(f"模型第 {index} 行方块 ID 非法: {block_id!r}")
        for value in (dx, dy, dz):
            if not isinstance(value, int):
                raise VerifyError(f"模型第 {index} 行坐标不是整数: {row!r}")
        blocks.append((block_id, dx, dy, dz))
    return blocks


def choose_server(data: dict, requested: str | None) -> str:
    servers = data.get("data") or []
    if not isinstance(servers, list):
        raise VerifyError(f"游戏服列表格式异常: {data!r}")
    ids = [item.get("serverId") for item in servers if isinstance(item, dict) and item.get("serverId")]
    if requested:
        if requested not in ids:
            raise VerifyError(f"游戏服 {requested} 未连接；当前在线: {ids}")
        return requested
    if len(ids) == 1:
        return ids[0]
    if not ids:
        raise VerifyError("当前没有已连接的游戏服，请先启动插件并检查 STRUCTENGINE_WS_URL")
    raise VerifyError(f"存在多个游戏服，请通过 --server-id 指定: {ids}")


def verify_blocks(rcon: RconClient, blocks: list[tuple[str, int, int, int]], origin: tuple[int, int, int],
                  max_checks: int | None) -> tuple[int, list[str]]:
    x0, y0, z0 = origin
    checked = 0
    failures: list[str] = []

    # 预检 RCON 命令通道与 execute unless 语法。
    if READY_MARKER not in rcon.command(f"say {READY_MARKER}"):
        raise VerifyError("RCON 命令通道异常：say 预检未返回标记")

    max_dy = max(dy for _, _, dy, _ in blocks)
    probe_y = min(319, y0 + max_dy + 20)
    probe = rcon.command(f"execute unless block {x0} {probe_y} {z0} minecraft:bedrock run say {PROBE_MARKER}")
    if PROBE_MARKER not in probe:
        # 探测点恰好是基岩的概率极低；换一个高度再试，仍失败则视为 RCON/命令异常。
        probe_y = min(319, probe_y + 10)
        probe = rcon.command(f"execute unless block {x0} {probe_y} {z0} minecraft:bedrock run say {PROBE_MARKER}")
        if PROBE_MARKER not in probe:
            raise VerifyError(
                "RCON execute unless 预检失败；请确认服务端版本支持 execute if/unless block，"
                f"原始响应: {probe!r}"
            )

    for index, (block_id, dx, dy, dz) in enumerate(blocks):
        if max_checks is not None and index >= max_checks:
            break
        x = x0 + dx
        y = y0 + dy
        z = z0 + dz
        response = rcon.command(
            f"execute unless block {x} {y} {z} {block_id} run say {MISMATCH_MARKER}"
        )
        checked += 1
        if MISMATCH_MARKER in response:
            failures.append(f"({x},{y},{z}) 期望 {block_id}；RCON: {response}")
        if checked % 50 == 0:
            print(f"  已核对 {checked}/{min(len(blocks), max_checks or len(blocks))} 个方块...")
    return checked, failures


def main() -> int:
    parser = argparse.ArgumentParser(description="StructEngine 远程构建真机验收")
    parser.add_argument("--platform", default="http://127.0.0.1:8080", help="Platform 基础地址")
    parser.add_argument("--server-id", default=None, help="目标游戏服 ID；仅一个在线时可省略")
    parser.add_argument("--x", type=int, required=True, help="构建原点 X")
    parser.add_argument("--z", type=int, required=True, help="构建原点 Z")
    parser.add_argument("--player", default="Steve", help="playerName")
    parser.add_argument("--prompt", default="验收小屋", help="建筑提示词")
    parser.add_argument("--expected-y", type=int, default=None, help="可选：断言插件回传的 Y")
    parser.add_argument("--rcon-host", default="127.0.0.1")
    parser.add_argument("--rcon-port", type=int, default=25575)
    parser.add_argument("--rcon-password", default=None)
    parser.add_argument("--max-checks", type=int, default=None,
                        help="只核对前 N 个方块（默认全部核对）")
    parser.add_argument("--skip-rcon", action="store_true",
                        help="只触发构建并打印预期坐标，不连接 RCON")
    parser.add_argument("--timeout", type=float, default=30.0, help="等待构建接口的超时秒数")
    parser.add_argument("--settle-seconds", type=float, default=3.0,
                        help="收到 BUILD 后等待插件下载并分批放置的秒数")
    args = parser.parse_args()

    platform = args.platform.rstrip("/")
    print("[1/4] 检查游戏服连接...")
    servers = http_json("GET", f"{platform}/api/map/servers")
    server_id = choose_server(servers, args.server_id)
    print(f"      已连接: {server_id}")

    print("[2/4] 触发地图构建...")
    started = time.time()
    response = http_json("POST", f"{platform}/api/map/build", {
        "serverId": server_id,
        "x": args.x,
        "z": args.z,
        "playerName": args.player,
        "prompt": args.prompt,
    }, timeout=args.timeout)
    if response.get("code") != 200:
        raise VerifyError(f"构建接口失败: {response}")
    result = response["data"]
    x, y, z = result["x"], result["y"], result["z"]
    task_id = result["taskId"]
    model_url = result["modelUrl"]
    print(f"      taskId={task_id}")
    print(f"      原点=({x},{y},{z})，耗时 {time.time() - started:.2f}s")
    print(f"      modelUrl={model_url}")
    if args.expected_y is not None and y != args.expected_y:
        raise VerifyError(f"Y 轴断言失败：期望 {args.expected_y}，实际 {y}")

    print("[3/4] 下载并校验模型 JSON...")
    model = fetch_model(model_url)
    blocks = validate_model(model)
    print(f"      模型方块数: {len(blocks)}")
    print(f"      首方块: {blocks[0]}")
    print(f"      末方块: {blocks[-1]}")

    if args.skip_rcon:
        print("[4/4] 已跳过 RCON 核对。")
        print("验收结果: 构建已下发，模型文件可访问；未核对世界实际方块。")
        return 0

    if not args.rcon_password:
        raise VerifyError("未提供 --rcon-password；如需跳过世界核对请加 --skip-rcon")

    if args.settle_seconds > 0:
        print(f"      等待插件完成下载与分批放置 {args.settle_seconds:g}s...")
        time.sleep(args.settle_seconds)

    print("[4/4] 通过 RCON 逐方块核对世界...")
    rcon = RconClient(args.rcon_host, args.rcon_port, args.rcon_password)
    try:
        checked, failures = verify_blocks(rcon, blocks, (x, y, z), args.max_checks)
    finally:
        rcon.close()

    if failures:
        print(f"验收失败: 核对 {checked} 个方块，{len(failures)} 处不匹配")
        for failure in failures[:50]:
            print("  - " + failure)
        if len(failures) > 50:
            print(f"  ... 其余 {len(failures) - 50} 处省略")
        return 1

    print(f"验收通过: 核对 {checked} 个方块，全部与模型一致")
    print(f"  任务: {task_id}")
    print(f"  原点: ({x},{y},{z})")
    print(f"  模型: {model_url}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except VerifyError as error:
        print(f"验收中止: {error}", file=sys.stderr)
        sys.exit(2)
