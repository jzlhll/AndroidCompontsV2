#!/usr/bin/env python3

import argparse
from contextlib import nullcontext
import hashlib
import http.client
import ipaddress
import json
import os
from pathlib import Path
import subprocess
import sys
from uuid import uuid4


ROOT = Path(__file__).resolve().parent
APP_PROJECTS = (
    "app", "jobstudy", "autoClickFloat", "mydroid", "child-monitor",
    "appMultiMedia", "appcompose", "agit", "picnote",
)
ANDROID_LIBRARIES = (
    "Module-AndroidCommon", "Module-AndroidColor", "Module-AndroidUi",
    "Module-AndroidUiEx", "Module-Okhttp", "Module-Nested",
    "Module-ImageCompressed", "Module-Native", "Module-AndroidLogSystem",
    "Module-AuSimplePermission", "Module-AuGsonMMKV", "Module-AuKson",
)
JVM_LIBRARIES = ("ClassNameAnnotations", "ClassNameAnnotations-ksp")
PROJECT_DIRS = {"child-monitor": "child-monitor/androidApp", "picnote": "PicNote"}
BUILD_DESCRIPTION = "编译指定的 Gradle 子项目，并可将生成的 APK 通过 MyDroid 分片传到多台手机。"


def parse_phone(target):
    host, separator, port = target.rpartition(":")
    if not separator or not port.isascii() or not port.isdecimal():
        raise ValueError(target)
    port = int(port)
    if not 1 <= port <= 65535:
        raise ValueError(target)
    if host.startswith("[") and host.endswith("]"):
        address = ipaddress.IPv6Address(host[1:-1])
    else:
        address = ipaddress.IPv4Address(host)
    return str(address), port


def parse_args(description=BUILD_DESCRIPTION, additional_help=""):
    parser = argparse.ArgumentParser(
        description=description,
        usage="%(prog)s <project> [debug|release] [clean|no-clean] [IP:PORT ...]",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "可生成 APK 的项目\n  " + ", ".join(APP_PROJECTS) + "\n\n"
            "Android 库\n  " + ", ".join(ANDROID_LIBRARIES[:6]) + "\n  "
            + ", ".join(ANDROID_LIBRARIES[6:]) + "\n\n"
            "JVM 库（统一执行 assemble）\n  " + ", ".join(JVM_LIBRARIES) + "\n\n"
            "示例\n"
            "  ./%(prog)s mydroid\n"
            "  ./%(prog)s mydroid release clean 192.168.1.10:15555 192.168.1.11:15555\n"
            "  ./%(prog)s mydroid 192.168.1.10:15555 '[fd00::10]:15555'"
            + additional_help
        ),
    )
    parser.add_argument("project", metavar="project", help="Gradle 子项目名")
    parser.add_argument(
        "options", nargs="*", metavar="options",
        help="可省略 debug/release 和 clean/no-clean，默认 debug、no-clean。手机地址可重复。",
    )
    if len(sys.argv) == 1:
        parser.print_help(sys.stderr)
        parser.exit(2)
    args = parser.parse_args()
    if args.project not in APP_PROJECTS + ANDROID_LIBRARIES + JVM_LIBRARIES:
        parser.error(f"unknown Gradle project {args.project!r}. See --help for available projects")
    options = args.options
    variant = options.pop(0) if options and options[0] in ("debug", "release") else "debug"
    clean = options.pop(0) == "clean" if options and options[0] in ("clean", "no-clean") else False
    phones = []
    for target in options:
        try:
            phones.append(parse_phone(target))
        except ValueError:
            parser.error(f"invalid phone address {target!r}. Expected IPv4:PORT or [IPv6]:PORT")
    if phones and args.project not in APP_PROJECTS:
        parser.error(f"project {args.project!r} does not produce an APK to send")
    return args.project, variant, clean, phones


def build(project, variant, clean):
    tasks = [f":{project}:clean"] if clean else []
    assemble = "assemble" if project in JVM_LIBRARIES else f"assemble{variant.capitalize()}"
    tasks.append(f":{project}:{assemble}")
    print(f"Running Gradle tasks: {' '.join(tasks)}", flush=True)
    subprocess.run([str(ROOT / "gradlew"), *tasks], cwd=ROOT, check=True)


def find_apk(project, variant):
    build_dir = ROOT / PROJECT_DIRS.get(project, project) / "build"
    # 两处 APK 目录分别对应公开产物和当前 AGP 的中间产物。
    for location in ("outputs", "intermediates"):
        apk_dir = build_dir / location / "apk" / variant
        metadata_path = apk_dir / "output-metadata.json"
        if not metadata_path.is_file():
            continue
        try:
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        except ValueError as error:
            raise RuntimeError(f"Invalid APK metadata in {metadata_path}: {error}") from error
        if not isinstance(metadata, dict) or metadata.get("variantName") != variant:
            continue
        elements = metadata.get("elements")
        if not isinstance(elements, list) or len(elements) != 1 or not isinstance(elements[0], dict):
            raise RuntimeError(f"Expected a single APK in {metadata_path}")
        element = elements[0]
        name = element.get("outputFile")
        if not isinstance(name, str) or Path(name).name != name:
            raise RuntimeError(f"Invalid APK file name in {metadata_path}")
        apk = apk_dir / name
        if apk.suffix.lower() == ".apk" and apk.is_file():
            application_id = metadata.get("applicationId")
            version_name = element.get("versionName")
            version_code = element.get("versionCode")
            if (
                not isinstance(application_id, str) or not application_id
                or not isinstance(version_name, str) or type(version_code) is not int
            ):
                raise RuntimeError(f"Invalid APK build information in {metadata_path}")
            return apk, {
                "applicationId": application_id,
                "versionName": version_name,
                "versionCode": version_code,
                "variantName": metadata["variantName"],
            }
    raise RuntimeError(f"No {variant} APK found in the APK metadata under {build_dir}")


def request(phone, route, body=None, content_type="application/json; charset=utf-8", timeout=30):
    connection = http.client.HTTPConnection(*phone, timeout=timeout)
    try:
        method = "GET" if body is None else "POST"
        headers = {"Content-Type": content_type} if body is not None else {}
        connection.request(method, route, body, headers)
        response = connection.getresponse()
        if not 200 <= response.status < 300:
            raise RuntimeError(f"{route}: HTTP {response.status} {response.reason}")
        try:
            payload = json.loads(response.read(64 * 1024))
        except ValueError as error:
            raise RuntimeError(f"{route}: invalid JSON response") from error
        if not isinstance(payload, dict) or "code" not in payload:
            raise RuntimeError(f"{route}: missing MyDroid response code")
        if str(payload["code"]) != "0":
            raise RuntimeError(f"{route}: code {payload['code']}, {payload.get('msg') or 'request failed'}")
        return payload.get("data")
    finally:
        connection.close()


def upload_chunk(phone, route, fields, chunk):
    boundary = f"mydroid-{uuid4().hex}"
    parts = [
        f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode()
        for key, value in fields.items()
    ]
    filename = fields["fileName"].replace('"', "%22").replace("\r", "%0D").replace("\n", "%0A")
    parts.extend((
        f'--{boundary}\r\nContent-Disposition: form-data; name="chunk"; filename="{filename}"\r\n'
        'Content-Type: application/octet-stream\r\n\r\n'.encode(),
        chunk,
        f"\r\n--{boundary}--\r\n".encode(),
    ))
    content_type = f"multipart/form-data; boundary={boundary}; charset=utf-8"
    request(phone, route, b"".join(parts), content_type)


def upload_apk(apk, build_info, phone):
    mode = request(phone, "/get-mode", timeout=10)
    if mode != "InstallApk":
        raise RuntimeError(f"MyDroid must be in InstallApk mode (current mode: {mode!r})")
    upload_apk_chunks(apk, phone, route_prefix="/apk", build_info=build_info)


def upload_apk_chunks(apk, phone, *, route_prefix="", build_info=None, check_connection=None,
                      merge_guard=nullcontext):
    session = {"fileName": apk.name, "uploadId": uuid4().hex, "hashMode": "stream"}
    md5_digest = hashlib.md5(usedforsecurity=False)
    sha256_digest = hashlib.sha256() if build_info is not None else None
    with apk.open("rb") as source:
        stat = os.fstat(source.fileno())
        if not stat.st_size:
            raise RuntimeError(f"APK is empty: {apk}")
        # 分片大小与 mydroid-send-to-phone.js 一致。
        mib = 1024 * 1024
        chunk_size = 5 * mib
        for limit, size in ((10, mib // 2), (100, 3 * mib), (500, 4 * mib)):
            if stat.st_size <= limit * mib:
                chunk_size = size
                break
        total_chunks = (stat.st_size + chunk_size - 1) // chunk_size
        merging = False
        try:
            for index in range(1, total_chunks + 1):
                if check_connection is not None:
                    check_connection()
                chunk = source.read(chunk_size)
                if not chunk:
                    raise RuntimeError(f"APK changed during upload: {apk}")
                md5_digest.update(chunk)
                if sha256_digest is not None:
                    sha256_digest.update(chunk)
                upload_chunk(
                    phone, f"{route_prefix}/upload-chunk",
                    dict(session, chunkIndex=index, totalChunks=total_chunks), chunk,
                )
                print(f"Uploaded {source.tell() / mib:.2f} MiB / {stat.st_size / mib:.2f} MiB", flush=True)
            if source.tell() != stat.st_size or os.fstat(source.fileno()).st_mtime_ns != stat.st_mtime_ns:
                raise RuntimeError(f"APK changed during upload: {apk}")
            md5 = md5_digest.hexdigest()
            merge = dict(
                session,
                md5=md5,
                totalChunks=total_chunks,
                lastModified=stat.st_mtime_ns // 1_000_000,
            )
            if sha256_digest is not None:
                sha256 = sha256_digest.hexdigest()
                merge["buildInfo"] = dict(build_info, sha256=sha256)
                print(f"APK SHA-256: {sha256}", flush=True)
            if check_connection is not None:
                check_connection()
            with merge_guard():
                print("整理中...", flush=True)
                # 合并请求发出后由服务端负责清理分片。
                merging = True
                result = request(phone, f"{route_prefix}/merge-chunks", json.dumps(merge).encode(), timeout=180)
                server_md5 = result.get("md5") if isinstance(result, dict) else None
                if not isinstance(server_md5, str) or server_md5.lower() != md5:
                    raise RuntimeError("Merged APK MD5 does not match")
                if sha256_digest is not None and "sha256" in result:
                    server_sha256 = result["sha256"]
                    if not isinstance(server_sha256, str) or server_sha256.lower() != sha256:
                        raise RuntimeError(f"Merged APK SHA-256 does not match (received {server_sha256!r})")
                    print("Receiver reported matching SHA-256", flush=True)
        finally:
            if not merging:
                try:
                    request(phone, f"{route_prefix}/abort-upload-chunks", json.dumps(session).encode(), timeout=5)
                except (OSError, http.client.HTTPException, RuntimeError) as error:
                    print(f"Upload cleanup failed: {error}", file=sys.stderr)


def main(upload=upload_apk, description=BUILD_DESCRIPTION, additional_help=""):
    project, variant, clean, phones = parse_args(description, additional_help)
    try:
        build(project, variant, clean)
        if project not in APP_PROJECTS:
            return 0
        apk, build_info = find_apk(project, variant)
    except subprocess.CalledProcessError as error:
        print(f"Gradle build failed with exit code {error.returncode}", file=sys.stderr)
        return error.returncode if error.returncode > 0 else 128 - error.returncode
    except (OSError, RuntimeError, ValueError) as error:
        print(f"Build error: {error}", file=sys.stderr)
        return 1

    print(f"APK: {apk}", flush=True)
    print(f"APK build: {json.dumps(build_info, ensure_ascii=False)}", flush=True)
    failed = False
    for phone in phones:
        host, port = phone
        target = f"[{host}]:{port}" if ":" in host else f"{host}:{port}"
        print(f"Sending {apk.name} to {target}", flush=True)
        try:
            upload(apk, build_info, phone)
        except (OSError, http.client.HTTPException, RuntimeError) as error:
            print(f"Upload to {target} failed: {error}", file=sys.stderr)
            failed = True
        else:
            print(f"APK received by {target}", flush=True)
    return int(failed)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("Interrupted", file=sys.stderr)
        sys.exit(130)
