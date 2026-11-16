#!/usr/bin/env bash
# 将 StructEngine 的 BlueMap 定制画布页安装到 BlueMap webroot。
# 用法: scripts/install-bluemap-page.sh /opt/minecraft/paper/bluemap/web
set -euo pipefail
WEBROOT="${1:?usage: $0 <bluemap-webroot>}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE="${SCRIPT_DIR}/../bluemap/structengine-map.html"
[[ -f "$SOURCE" ]] || { echo "missing $SOURCE" >&2; exit 1; }
[[ -d "$WEBROOT" ]] || { echo "webroot not found: $WEBROOT" >&2; exit 1; }
install -m 0644 "$SOURCE" "${WEBROOT%/}/structengine-map.html"
echo "installed ${WEBROOT%/}/structengine-map.html"
