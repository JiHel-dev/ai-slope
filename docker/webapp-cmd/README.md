# cmd-kuma-check

HTTP service that runs Linux commands from JSON (or named presets) and returns a response Uptime Kuma can monitor with **JSON Query**.

## Response shape

Every check returns JSON like:

```json
{
  "status": "ok",
  "msg": "all checks passed",
  "passed": 2,
  "failed": 0,
  "duration_ms": 18,
  "checks": [
    {
      "name": "nginx-active",
      "ok": true,
      "exit_code": 0,
      "stdout": "active",
      "stderr": "",
      "duration_ms": 7
    }
  ]
}
```

`status` is `"ok"` when all commands succeed, otherwise `"down"`.

### Uptime Kuma setup

1. Monitor type: **HTTP(s) - Json Query**
2. URL: `http://host:8080/preset/host-alive` (or `POST http://host:8080/check`)
3. JSON Query: `$.status`
4. Expected Value: `ok`
5. Headers: `Authorization: Bearer YOUR_TOKEN` (or `X-API-Key: YOUR_TOKEN`)

For POST monitors, set method to POST and body to your command JSON.

## Auth token

Generate a random secret and put it in the environment (or `.env` for Compose):

```bash
# openssl (common on servers)
openssl rand -hex 32

# or Python
python3 -c 'import secrets; print(secrets.token_urlsafe(32))'
```

Use it as `AUTH_TOKEN`. Clients send either:

- `Authorization: Bearer <token>`
- `X-API-Key: <token>`

Example:

```bash
export AUTH_TOKEN="$(openssl rand -hex 32)"
# Compose:
# echo "AUTH_TOKEN=$AUTH_TOKEN" > .env
```

If `AUTH_TOKEN` is unset, the API is open (a warning is printed on startup). Always set one outside local experiments.

## Quick start

```bash
export AUTH_TOKEN="$(openssl rand -hex 32)"
cp config.example.json config.json
python3 app.py
```

```bash
# Health (no auth)
curl -s http://127.0.0.1:8080/health

# Preset
curl -s -H "Authorization: Bearer $AUTH_TOKEN" \
  http://127.0.0.1:8080/preset/disk-root

# Ad-hoc commands
curl -s -H "Authorization: Bearer $AUTH_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "commands": [
      {"name": "ping-local", "cmd": "true"},
      {"name": "uname", "cmd": "uname -s", "expect_stdout": "Linux"}
    ]
  }' \
  http://127.0.0.1:8080/check
```

## API

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/health` | no | Service alive (`status: ok`) |
| GET | `/presets` | yes | List preset names |
| GET | `/preset/<name>` | yes | Run a preset from `config.json` |
| POST | `/check` | yes | Run `commands` from body, or `{"preset":"name"}` |

### Command object

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Label in the response |
| `cmd` | string | Shell command (`bash -c`) |
| `argv` | string[] | Exec without shell (preferred when possible) |
| `timeout` | number | Seconds (default 10, max 60) |
| `expect_exit` | int | Expected exit code (default 0) |
| `expect_stdout` | string | Substring that must appear in stdout |
| `expect_stdout_re` | string | Regex that must match stdout |
| `expect_stderr_empty` | bool | Fail if stderr has content |
| `cwd` | string | Working directory |
| `env` | object | Extra environment variables |

Shorthand: `"commands": ["true", "uname -s"]` is allowed.

## Config

`config.json`:

```json
{
  "presets": {
    "my-check": {
      "commands": [
        {"name": "svc", "cmd": "systemctl is-active myapp", "expect_stdout": "active"}
      ]
    }
  }
}
```

Point `CONFIG_PATH` at another file if needed.

## Environment

| Variable | Default | Meaning |
|----------|---------|---------|
| `HOST` | `0.0.0.0` | Bind address |
| `PORT` | `8080` | Listen port |
| `AUTH_TOKEN` | empty | Required Bearer / X-API-Key (open if unset) |
| `CONFIG_PATH` | `config.json` | Presets file |
| `DEFAULT_TIMEOUT` | `10` | Default command timeout (s) |
| `MAX_TIMEOUT` | `60` | Cap per command |
| `MAX_COMMANDS` | `20` | Cap per request |
| `FAIL_HTTP` | `0` | If `1`, failed checks return HTTP 503 |

## Security

This service runs shell commands on the host. Treat it like root-capable remote exec:

- Always set `AUTH_TOKEN`
- Prefer **presets** over ad-hoc `POST /check` from the public internet
- Prefer `argv` over `cmd` when you do not need a shell
- Bind to localhost / private network and put a reverse proxy in front if exposed

## Docker Compose

```bash
cp config.example.json config.json
echo "AUTH_TOKEN=$(openssl rand -hex 32)" > .env
docker compose up -d --build
```

Presets are loaded from `./config.json`. Override port with `PORT=9090` in `.env` if needed.

To check **host** services (`systemctl`, host disks via special mounts, etc.), uncomment `network_mode: host` / `pid: host` in `docker-compose.yml`.

### Plain Docker

```bash
docker build -t cmd-kuma-check .
docker run --rm -p 8080:8080 \
  -e AUTH_TOKEN="$(openssl rand -hex 32)" \
  -v "$PWD/config.json:/app/config.json:ro" \
  cmd-kuma-check
```
