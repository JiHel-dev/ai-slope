#!/usr/bin/env python3
"""Execute Linux checks from JSON and return Uptime Kuma–parsable results."""

from __future__ import annotations

import json
import os
import re
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

HOST = os.environ.get("HOST", "0.0.0.0")
PORT = int(os.environ.get("PORT", "8080"))
AUTH_TOKEN = os.environ.get("AUTH_TOKEN", "")
CONFIG_PATH = Path(os.environ.get("CONFIG_PATH", "config.json"))
MAX_COMMANDS = int(os.environ.get("MAX_COMMANDS", "20"))
DEFAULT_TIMEOUT = float(os.environ.get("DEFAULT_TIMEOUT", "10"))
MAX_TIMEOUT = float(os.environ.get("MAX_TIMEOUT", "60"))
MAX_OUTPUT_BYTES = int(os.environ.get("MAX_OUTPUT_BYTES", "65536"))
# When true, failed checks return HTTP 503 (still with JSON body).
FAIL_HTTP = os.environ.get("FAIL_HTTP", "0") in ("1", "true", "yes")


def load_config() -> dict[str, Any]:
    if not CONFIG_PATH.is_file():
        return {"presets": {}}
    with CONFIG_PATH.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise ValueError("config root must be an object")
    data.setdefault("presets", {})
    return data


CONFIG = load_config()


def truncate(text: str, limit: int = MAX_OUTPUT_BYTES) -> str:
    raw = text.encode("utf-8", errors="replace")
    if len(raw) <= limit:
        return text
    return raw[:limit].decode("utf-8", errors="replace") + "…(truncated)"


def run_one(check: dict[str, Any]) -> dict[str, Any]:
    name = str(check.get("name") or check.get("cmd") or "unnamed")
    timeout = float(check.get("timeout", DEFAULT_TIMEOUT))
    timeout = min(max(timeout, 0.1), MAX_TIMEOUT)
    expect_exit = int(check.get("expect_exit", 0))
    expect_stdout = check.get("expect_stdout")
    expect_stdout_re = check.get("expect_stdout_re")
    expect_stderr_empty = bool(check.get("expect_stderr_empty", False))
    cwd = check.get("cwd")
    env_extra = check.get("env") or {}

    argv = check.get("argv")
    cmd = check.get("cmd")
    if argv is None and cmd is None:
        return {
            "name": name,
            "ok": False,
            "error": "missing cmd or argv",
            "exit_code": None,
            "stdout": "",
            "stderr": "",
            "duration_ms": 0,
        }

    env = os.environ.copy()
    if isinstance(env_extra, dict):
        env.update({str(k): str(v) for k, v in env_extra.items()})

    started = time.monotonic()
    try:
        if argv is not None:
            if not isinstance(argv, list) or not all(isinstance(x, str) for x in argv):
                raise ValueError("argv must be a list of strings")
            proc = subprocess.run(
                argv,
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=cwd,
                env=env,
                shell=False,
            )
        else:
            if not isinstance(cmd, str) or not cmd.strip():
                raise ValueError("cmd must be a non-empty string")
            proc = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=timeout,
                cwd=cwd,
                env=env,
                shell=True,
                executable="/bin/bash",
            )
        duration_ms = int((time.monotonic() - started) * 1000)
        stdout = truncate(proc.stdout or "")
        stderr = truncate(proc.stderr or "")
        ok = proc.returncode == expect_exit
        reason = None
        if not ok:
            reason = f"exit {proc.returncode} != expected {expect_exit}"
        if ok and expect_stdout is not None:
            if expect_stdout not in stdout:
                ok = False
                reason = "expect_stdout not found"
        if ok and expect_stdout_re is not None:
            if re.search(str(expect_stdout_re), stdout, re.MULTILINE) is None:
                ok = False
                reason = "expect_stdout_re did not match"
        if ok and expect_stderr_empty and stderr.strip():
            ok = False
            reason = "stderr was not empty"
        result: dict[str, Any] = {
            "name": name,
            "ok": ok,
            "exit_code": proc.returncode,
            "stdout": stdout.rstrip("\n"),
            "stderr": stderr.rstrip("\n"),
            "duration_ms": duration_ms,
        }
        if reason:
            result["error"] = reason
        return result
    except subprocess.TimeoutExpired as exc:
        duration_ms = int((time.monotonic() - started) * 1000)
        return {
            "name": name,
            "ok": False,
            "error": f"timeout after {timeout}s",
            "exit_code": None,
            "stdout": truncate((exc.stdout or "") if isinstance(exc.stdout, str) else ""),
            "stderr": truncate((exc.stderr or "") if isinstance(exc.stderr, str) else ""),
            "duration_ms": duration_ms,
        }
    except Exception as exc:  # noqa: BLE001 - surface to monitor payload
        duration_ms = int((time.monotonic() - started) * 1000)
        return {
            "name": name,
            "ok": False,
            "error": str(exc),
            "exit_code": None,
            "stdout": "",
            "stderr": "",
            "duration_ms": duration_ms,
        }


def run_checks(commands: list[dict[str, Any]]) -> dict[str, Any]:
    if len(commands) > MAX_COMMANDS:
        return {
            "status": "down",
            "msg": f"too many commands (max {MAX_COMMANDS})",
            "passed": 0,
            "failed": 0,
            "duration_ms": 0,
            "checks": [],
        }

    started = time.monotonic()
    results = [run_one(c) for c in commands]
    passed = sum(1 for r in results if r.get("ok"))
    failed = len(results) - passed
    duration_ms = int((time.monotonic() - started) * 1000)
    ok = failed == 0 and len(results) > 0
    if not results:
        return {
            "status": "down",
            "msg": "no commands provided",
            "passed": 0,
            "failed": 0,
            "duration_ms": duration_ms,
            "checks": [],
        }
    failed_names = [r["name"] for r in results if not r.get("ok")]
    return {
        "status": "ok" if ok else "down",
        "msg": "all checks passed" if ok else f"failed: {', '.join(failed_names)}",
        "passed": passed,
        "failed": failed,
        "duration_ms": duration_ms,
        "checks": results,
    }


def authorized(handler: BaseHTTPRequestHandler) -> bool:
    if not AUTH_TOKEN:
        return True
    auth = handler.headers.get("Authorization", "")
    api_key = handler.headers.get("X-API-Key", "")
    if api_key and api_key == AUTH_TOKEN:
        return True
    if auth.startswith("Bearer ") and auth[7:] == AUTH_TOKEN:
        return True
    return False


class Handler(BaseHTTPRequestHandler):
    server_version = "cmd-kuma-check/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"{self.address_string()} - {fmt % args}")

    def _send_json(self, code: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self) -> dict[str, Any] | None:
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length <= 0:
            return {}
        if length > 1_000_000:
            self._send_json(413, {"status": "down", "msg": "body too large"})
            return None
        raw = self.rfile.read(length)
        try:
            data = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._send_json(400, {"status": "down", "msg": "invalid JSON"})
            return None
        if not isinstance(data, dict):
            self._send_json(400, {"status": "down", "msg": "JSON body must be an object"})
            return None
        return data

    def _require_auth(self) -> bool:
        if authorized(self):
            return True
        self._send_json(401, {"status": "down", "msg": "unauthorized"})
        return False

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"

        if path == "/health":
            self._send_json(200, {"status": "ok", "msg": "alive"})
            return

        if not self._require_auth():
            return

        if path.startswith("/preset/"):
            name = path[len("/preset/") :]
            presets = CONFIG.get("presets") or {}
            preset = presets.get(name)
            if preset is None:
                self._send_json(404, {"status": "down", "msg": f"unknown preset: {name}"})
                return
            commands = preset.get("commands") if isinstance(preset, dict) else None
            if not isinstance(commands, list):
                self._send_json(500, {"status": "down", "msg": "invalid preset config"})
                return
            result = run_checks(commands)
            code = 200 if result["status"] == "ok" or not FAIL_HTTP else 503
            self._send_json(code, result)
            return

        if path == "/presets":
            names = sorted((CONFIG.get("presets") or {}).keys())
            self._send_json(200, {"status": "ok", "presets": names})
            return

        self._send_json(
            404,
            {
                "status": "down",
                "msg": "not found",
                "hint": "GET /health, GET /presets, GET /preset/<name>, POST /check",
            },
        )

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path.rstrip("/") or "/"

        if not self._require_auth():
            return

        if path != "/check":
            self._send_json(404, {"status": "down", "msg": "not found"})
            return

        data = self._read_json()
        if data is None:
            return

        commands = data.get("commands")
        if commands is None and "preset" in data:
            preset = (CONFIG.get("presets") or {}).get(str(data["preset"]))
            if not isinstance(preset, dict):
                self._send_json(404, {"status": "down", "msg": f"unknown preset: {data['preset']}"})
                return
            commands = preset.get("commands")

        if not isinstance(commands, list):
            self._send_json(400, {"status": "down", "msg": "commands must be a list"})
            return

        normalized: list[dict[str, Any]] = []
        for i, item in enumerate(commands):
            if isinstance(item, str):
                normalized.append({"name": f"cmd{i}", "cmd": item})
            elif isinstance(item, dict):
                normalized.append(item)
            else:
                self._send_json(400, {"status": "down", "msg": f"commands[{i}] invalid"})
                return

        result = run_checks(normalized)
        code = 200 if result["status"] == "ok" or not FAIL_HTTP else 503
        self._send_json(code, result)


def main() -> None:
    if not AUTH_TOKEN:
        print("WARNING: AUTH_TOKEN is unset — endpoint is open. Set AUTH_TOKEN in production.")
    httpd = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Listening on http://{HOST}:{PORT}")
    print(f"Config: {CONFIG_PATH.resolve()} ({len(CONFIG.get('presets') or {})} presets)")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down")
        httpd.server_close()


if __name__ == "__main__":
    main()
