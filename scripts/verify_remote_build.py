#!/usr/bin/env python3
"""StructEngine 远程构建真机验收脚本（Platform v2 / WS v3）。

流程：
  1. 用管理员/服主 JWT 检查目标服务器已连接；
  2. 调用服务器上下文部署接口，把指定模型产物部署到 (x, z)；
  3. 下载 Platform 生成的部署模型 JSON；
  4. 轮询任务状态，再用 RCON 逐方块核对世界实际方块。

只依赖 Python 标准库。JWT 可从浏览器 localStorage 的
`structengine.token` 复制，或通过环境变量 STRUCTENGINE_ACCESS_TOKEN 传入。

示例：
  STRUCTENGINE_ACCESS_TOKEN=eyJ... python3 scripts/verify_remote_build.py \
    --platform http://127.0.0.1:8080 \
    --server-id 380996850351603712 \
    --model-id 380996850351603713 \
    --x 1000 --z 1000 \
    --player Steve \
    --rcon-host 127.0.0.1 --rcon-port 25575 --rcon-password change-me
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import struct
import sys
import time
import urllib.error
import urllib.request

DEFAULT_SUCCESS_TEXT = "Test passed"
DEFAULT_FAILURE_TEXT = "Test failed"


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


def http_json(method: str, url: str, token: str, payload: dict | None = None,
              timeout: float = 30.0) -> dict:
    data = None
    headers = {"Accept": "application/json", "Authorization": f"Bearer {token}"}
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


def wait_task(platform: str, token: str, server_id: str, task_id: str,
              timeout: float) -> dict:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        response = http_json("GET", f"{platform}/api/servers/{server_id}/tasks/{task_id}",
                             token, timeout=10)
        if response.get("code") != 200:
            raise VerifyError(f"查询任务失败: {response}")
        last = response["data"]
        status = last.get("status")
        if status in ("COMPLETED", "FAILED", "ABORTED"):
            return last
        time.sleep(1.0)
    raise VerifyError(f"任务超时未完成，最后状态: {last}")


def verify_blocks(rcon: RconClient, blocks: list[tuple[str, int, int, int]],
                  origin: tuple[int, int, int], max_checks: int | None,
                  success_text: str, failure_text: str) -> tuple[int, list[str]]:
    x0, y0, z0 = origin
    checked = 0
    failures: list[str] = []

    if not rcon.command("list"):
        raise VerifyError("RCON 命令通道异常：list 未返回任何内容")

    for index, (block_id, dx, dy, dz) in enumerate(blocks):
        if max_checks is not None and index >= max_checks:
            break
        x = x0 + dx
        y = y0 + dy
        z = z0 + dz
        response = rcon.command(f"execute if block {x} {y} {z} {block_id}")
        checked += 1
        if success_text in response:
            pass
        elif failure_text in response:
            failures.append(f"({x},{y},{z}) 期望 {block_id}；RCON: {response}")
        else:
            raise VerifyError(
                f"无法识别 RCON 响应（可能是服务端语言不同）：{response!r}；"
                f"请用 --success-text/--failure-text 指定实际文本"
            )
        if checked % 50 == 0:
            print(f"  已核对 {checked}/{min(len(blocks), max_checks or len(blocks))} 个方块...")
    return checked, failures


def main() -> int:
    parser = argparse.ArgumentParser(description="StructEngine 远程构建真机验收（v2）")
    parser.add_argument("--platform", default="http://127.0.0.1:8080", help="Platform 基础地址")
    parser.add_argument("--token", default=os.environ.get("STRUCTENGINE_ACCESS_TOKEN", ""),
                        help="JWT；也可用 STRUCTENGINE_ACCESS_TOKEN 环境变量")
    parser.add_argument("--server-id", required=True, help="服务器雪花 ID")
    parser.add_argument("--model-id", required=True, help="模型产物雪花 ID")
    parser.add_argument("--x", type=int, required=True, help="构建中心 X")
    parser.add_argument("--z", type=int, required=True, help="构建中心 Z")
    parser.add_argument("--player", default="Steve", help="playerName")
    parser.add_argument("--player-uuid", default="", help="可选 playerUuid")
    parser.add_argument("--rotation", type=int, default=0, choices=[0, 90, 180, 270])
    parser.add_argument("--expected-y", type=int, default=None, help="可选：断言部署原点 Y")
    parser.add_argument("--rcon-host", default="127.0.0.1")
    parser.add_argument("--rcon-port", type=int, default=25575)
    parser.add_argument("--rcon-password", default=None)
    parser.add_argument("--max-checks", type=int, default=None,
                        help="只核对前 N 个方块（默认全部核对）")
    parser.add_argument("--skip-rcon", action="store_true",
                        help="只触发部署并打印任务信息，不连接 RCON")
    parser.add_argument("--timeout", type=float, default=120.0, help="等待任务完成的秒数")
    parser.add_argument("--settle-seconds", type=float, default=2.0,
                        help="任务完成后等待世界稳定的秒数")
    parser.add_argument("--success-text", default=DEFAULT_SUCCESS_TEXT,
                        help="execute if block 命中时 RCON 返回文本（默认 Test passed）")
    parser.add_argument("--failure-text", default=DEFAULT_FAILURE_TEXT,
                        help="execute if block 未命中时 RCON 返回文本（默认 Test failed）")
    args = parser.parse_args()

    if not args.token:
        raise VerifyError("缺少 JWT：请通过 --token 或 STRUCTENGINE_ACCESS_TOKEN 传入")

    platform = args.platform.rstrip("/")
    token = args.token

    print("[1/4] 检查服务器连接...")
    detail = http_json("GET", f"{platform}/api/servers/{args.server_id}", token)
    if detail.get("code") != 200:
        raise VerifyError(f"查询服务器失败: {detail}")
    server = detail["data"]
    if not server.get("connected"):
        raise VerifyError(f"服务器 {args.server_id} 当前未连接，请检查插件 secret 与 WS 会话")
    print(f"      {server.get('serverName')} · {server.get('status')}")

    print("[2/4] 触发模型部署...")
    started = time.time()
    response = http_json("POST", f"{platform}/api/servers/{args.server_id}/tasks/deploy", token, {
        "playerUuid": args.player_uuid,
        "playerName": args.player,
        "modelId": args.model_id,
        "centerX": args.x,
        "centerZ": args.z,
        "rotation": args.rotation,
    })
    if response.get("code") != 200:
        raise VerifyError(f"部署接口失败: {response}")
    task = response["data"]
    task_id = task["id"]
    origin = (int(task["originX"]), int(task["originY"]), int(task["originZ"]))
    print(f"      taskId={task_id}")
    print(f"      原点={origin}，方块数={task['total']}，耗时 {time.time() - started:.2f}s")
    if args.expected_y is not None and origin[1] != args.expected_y:
        raise VerifyError(f"Y 轴断言失败：期望 {args.expected_y}，实际 {origin[1]}")

    print("[3/4] 等待任务完成并下载部署模型...")
    final = wait_task(platform, token, args.server_id, task_id, args.timeout)
    print(f"      状态={final.get('status')} 进度={final.get('placed')}/{final.get('total')}")
    if final.get("status") != "COMPLETED":
        raise VerifyError(f"任务未成功: {final.get('status')} {final.get('error') or ''}")
    if args.settle_seconds > 0:
        time.sleep(args.settle_seconds)

    model_url = f"{platform}/models/{task_id}.json"
    model = fetch_model(model_url)
    blocks = validate_model(model)
    print(f"      modelUrl={model_url}")
    print(f"      模型方块数: {len(blocks)}")
    print(f"      首方块: {blocks[0]}")

    if args.skip_rcon:
        print("[4/4] 已跳过 RCON 核对。")
        print("验收结果: 部署完成，模型文件可访问；未核对世界实际方块。")
        return 0

    if not args.rcon_password:
        raise VerifyError("缺少 --rcon-password；如只想触发部署请加 --skip-rcon")

    print("[4/4] RCON 逐方块核对...")
    rcon = RconClient(args.rcon_host, args.rcon_port, args.rcon_password)
    try:
        checked, failures = verify_blocks(rcon, blocks, origin, args.max_checks,
                                          args.success_text, args.failure_text)
    finally:
        rcon.close()

    print(f"      已核对 {checked} 个方块，失败 {len(failures)} 个。")
    if failures:
        for failure in failures[:20]:
            print("      " + failure)
        print("验收结果: 失败")
        return 1
    print("验收结果: 通过")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except VerifyError as error:
        print(f"验收失败: {error}", file=sys.stderr)
        sys.exit(2)
