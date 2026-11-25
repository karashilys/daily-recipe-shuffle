# /// script
# dependencies = []
# ///
"""開発用サーバーにRCONでコマンドを送るクライアント。

使い方: uv run tools/rcon.py "time set 1d"
接続先とパスワードは run/server.properties から読み取る。
"""

from __future__ import annotations

import socket
import struct
import sys
from pathlib import Path

PROPERTIES_PATH = Path("run/server.properties")
HOST = "127.0.0.1"
TYPE_RESPONSE = 0
TYPE_COMMAND = 2
TYPE_LOGIN = 3


def load_properties(path: Path) -> dict[str, str]:
    """server.properties を読み込み、キーと値の辞書を返す。

    Args:
        path: server.properties のパス。

    Returns:
        プロパティ名から値への辞書。コメント行と空行は含まない。
    """
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#") and "=" in line:
            key, _, value = line.partition("=")
            props[key] = value
    return props


def send_packet(sock: socket.socket, request_id: int, packet_type: int, body: str) -> None:
    """RCONパケットを1つ送信する。

    Args:
        sock: 接続済みのソケット。
        request_id: リクエスト識別子。
        packet_type: パケット種別（3=ログイン、2=コマンド）。
        body: パケット本文。
    """
    payload = struct.pack("<ii", request_id, packet_type) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def recv_exact(sock: socket.socket, size: int) -> bytes:
    """指定バイト数をちょうど受信する。

    Args:
        sock: 接続済みのソケット。
        size: 受信するバイト数。

    Returns:
        受信したバイト列。

    Raises:
        ConnectionError: 途中で接続が閉じられた場合。
    """
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("RCON接続が閉じられました")
        data += chunk
    return data


def recv_packet(sock: socket.socket) -> tuple[int, int, str]:
    """RCONパケットを1つ受信する。

    Args:
        sock: 接続済みのソケット。

    Returns:
        (リクエスト識別子, パケット種別, 本文) のタプル。
    """
    (length,) = struct.unpack("<i", recv_exact(sock, 4))
    data = recv_exact(sock, length)
    request_id, packet_type = struct.unpack("<ii", data[:8])
    return request_id, packet_type, data[8:-2].decode("utf-8", errors="replace")


def run_command(command: str) -> str:
    """サーバーにログインしてコマンドを1つ実行し、応答を返す。

    Args:
        command: 実行するコマンド（先頭の `/` は不要）。

    Returns:
        サーバーからの応答文字列。

    Raises:
        PermissionError: パスワードが拒否された場合。
    """
    props = load_properties(PROPERTIES_PATH)
    port = int(props.get("rcon.port", "25575"))
    with socket.create_connection((HOST, port), timeout=10) as sock:
        send_packet(sock, 1, TYPE_LOGIN, props["rcon.password"])
        request_id, _, _ = recv_packet(sock)
        if request_id == -1:
            raise PermissionError("RCONのパスワードが拒否されました")
        send_packet(sock, 2, TYPE_COMMAND, command)
        _, _, body = recv_packet(sock)
        return body


def main() -> int:
    """コマンドライン引数をコマンドとして送信し、応答を標準出力へ出す。

    Returns:
        終了コード。引数がなければ 2、それ以外は 0。
    """
    if len(sys.argv) < 2:
        print('使い方: uv run tools/rcon.py "<command>"', file=sys.stderr)
        return 2
    print(run_command(" ".join(sys.argv[1:])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
