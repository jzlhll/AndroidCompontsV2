#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = ["websocket-client>=1.8,<2"]
# ///

from contextlib import contextmanager
import json
import signal
import sys
from threading import Event, Thread
from time import monotonic
from uuid import uuid4

from build_apk import main as build_and_upload
from build_apk import request, upload_apk_chunks


@contextmanager
def receiver_connection(phone):
    try:
        from websocket import ABNF, WebSocketException, WebSocketTimeoutException, create_connection
    except ImportError as error:
        raise RuntimeError("Run with uv run build_apk_to_phone.py or install websocket-client") from error

    mode = request(phone, "/get-mode", timeout=10)
    if mode != "Receiver":
        raise RuntimeError(f"MyDroid must be in Receiver mode (current mode: {mode!r})")
    endpoint = request(phone, "/read-websocket-ip-port", b"{}", timeout=10)
    port = endpoint.get("port") if isinstance(endpoint, dict) else None
    if type(port) is not int or not 1 <= port <= 65535:
        raise RuntimeError("Invalid WebSocket endpoint")
    host, http_port = phone
    host = f"[{host}]" if ":" in host else host
    origin = f"http://{host}:{http_port}" if http_port != 80 else f"http://{host}"
    handshake_deadline = monotonic() + 10
    try:
        socket = create_connection(
            f"ws://{host}:{port}/ws-test", timeout=10, origin=origin, http_no_proxy=["*"], redirect_limit=0,
        )
    except WebSocketException as error:
        raise RuntimeError(f"WebSocket connection failed: {error}") from error

    stopped = Event()
    ready = Event()
    failure = None

    def listen():
        nonlocal failure
        try:
            socket.send(json.dumps({"api": "c_wsInit", "wsName": uuid4().hex[:6], "platform": "html"}))
            next_ping = None
            while not stopped.is_set():
                now = monotonic()
                if next_ping is not None and now >= next_ping:
                    socket.send('{"api":"c_ping"}')
                    next_ping = now + 12
                socket.settimeout(1 if next_ping is None else min(1, next_ping - now))
                try:
                    opcode, message = socket.recv_data()
                except WebSocketTimeoutException:
                    continue
                if opcode == ABNF.OPCODE_CLOSE:
                    raise RuntimeError("Receiver closed the WebSocket connection")
                if opcode != ABNF.OPCODE_TEXT:
                    continue
                try:
                    frame = json.loads(message)
                except ValueError:
                    continue
                if not isinstance(frame, dict):
                    continue
                api = frame.get("api")
                if api == "s_clientInitBack" and next_ping is None:
                    next_ping = monotonic() + 12
                    ready.set()
                elif api == "s_leftSpace":
                    data = frame.get("data")
                    if isinstance(data, dict) and isinstance(data.get("leftSpace"), str):
                        print(f"Phone free space: {data['leftSpace']}", flush=True)
        except (OSError, RuntimeError, WebSocketException) as error:
            failure = error
            ready.set()

    def check_connection():
        if failure is not None:
            raise RuntimeError(f"WebSocket session failed: {failure}") from failure

    listener = Thread(target=listen, daemon=True)
    try:
        listener.start()
        if not ready.wait(handshake_deadline - monotonic()):
            raise RuntimeError("Timed out waiting for s_clientInitBack")
        check_connection()
        yield check_connection
    finally:
        stopped.set()
        try:
            if listener.ident is not None:
                listener.join(timeout=2)
            if not listener.is_alive():
                socket.close(timeout=1)
        finally:
            socket.shutdown()


@contextmanager
def finish_merge():
    interrupted = False

    def wait_for_result(_signum, _frame):
        nonlocal interrupted
        interrupted = True

    # 合并开始后等待手机返回结果，再停止向其余手机发送。
    original_handler = signal.signal(signal.SIGINT, wait_for_result)
    try:
        yield
    except Exception as error:
        if interrupted:
            raise KeyboardInterrupt(f"Merge failed: {error}") from None
        raise
    else:
        if interrupted:
            raise KeyboardInterrupt("APK received. Stopped after completing the merge.")
    finally:
        signal.signal(signal.SIGINT, original_handler)


def upload_to_receiver(apk, _build_info, phone):
    with receiver_connection(phone) as check_connection:
        upload_apk_chunks(apk, phone, check_connection=check_connection, merge_guard=finish_merge)


if __name__ == "__main__":
    try:
        sys.exit(build_and_upload(
            upload=upload_to_receiver,
            description="编译指定的 Gradle 子项目，并将 APK 发送到 MyDroid 的「主机 → 网页-文件接收」。",
            additional_help=("\n\n通过 uv 安装脚本依赖并发送\n"
                             "  uv run %(prog)s mydroid 192.168.1.10:15555"),
        ))
    except KeyboardInterrupt as error:
        print(str(error) or "Interrupted", file=sys.stderr)
        sys.exit(130)
