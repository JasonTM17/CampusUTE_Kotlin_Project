# Stitch MCP for ZCode

Wires Google Stitch into ZCode as a stdio MCP server, registered at **user
scope** so it is available in every workspace. The API key is read at startup
from the Windows user environment, never stored in configuration.

## Files

| Path | Role |
|---|---|
| `scripts/mcp/stitch-mcp-launcher.mjs` | Source of truth, committed here. Resolves the key, then runs the Stitch MCP proxy. |
| `~/.zcode/mcp/stitch-mcp-launcher.mjs` | Installed copy that ZCode actually runs. Keep it in step with the source. |
| `~/.zcode/cli/config.json` | User-scope MCP registration (`mcp.servers.stitch`). |
| `.env` | Optional per-project override for this repo. Gitignored, and normally unnecessary — the key lives in the Windows user environment. |

## Install or reinstall

```bash
mkdir -p ~/.zcode/mcp
cp scripts/mcp/stitch-mcp-launcher.mjs ~/.zcode/mcp/
```

Then confirm the registration exists in `~/.zcode/cli/config.json`:

```json
{
  "mcp": {
    "servers": {
      "stitch": {
        "type": "stdio",
        "command": "node",
        "args": ["C:\\Users\\<you>\\.zcode\\mcp\\stitch-mcp-launcher.mjs"],
        "timeoutMs": 120000
      }
    }
  }
}
```

No `cwd` is set, so the server inherits the workspace as its working directory —
that is what lets a project-local `.env` still act as an override.

Finally, provide the key — one place only:

```powershell
[Environment]::SetEnvironmentVariable('STITCH_API_KEY','<key>','User')
```

Get one at <https://stitch.withgoogle.com> → Settings → API Keys.

## Restart is required

ZCode reads MCP configuration at session start. A newly registered server does
not appear until the session restarts, and it will not show up in
**Settings → MCP** before then. Check the **User** tab there; the entry appears
under "Installed".

## Verify without ZCode

The launcher is a plain Node script, so it can be driven directly:

```bash
# Send an MCP initialize frame and read the handshake.
printf '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}\n' \
  | node ~/.zcode/mcp/stitch-mcp-launcher.mjs
```

A missing key exits `1` with an actionable message listing every location
searched. A present key makes the proxy answer the handshake.

The handshake itself does **not** validate the key — the proxy lists its tools
regardless. To confirm credentials, make a read-only call such as
`list_projects` through the proxy and check that it returns data rather than an
error. Never smoke-test authentication with a create or delete call.

## Why a launcher instead of `env` in the config

`@_davideast/stitch-mcp` reads `STITCH_API_KEY` from its own process
environment, and two ZCode behaviours get in the way of setting that directly:

- **ZCode MCP config files do not expand `${...}`.** A literal
  `"STITCH_API_KEY": "${STITCH_API_KEY}"` is passed through as that exact
  string, so the proxy rejects it as an invalid key.
- **ZCode spawns stdio MCP servers with the MCP SDK's default environment
  whitelist**, not the full parent environment, so a custom variable is never
  inherited.

Verified against the shipped CLI bundle rather than assumed. `zcode.exe`
contains the transport's spawn call:

```js
env: { ...getDefaultEnvironment(), ...this._serverParams.env },
shell: false,
```

On Windows `getDefaultEnvironment()` yields only `APPDATA, HOMEDRIVE, HOMEPATH,
LOCALAPPDATA, OS, PATH, PROCESSOR_ARCHITECTURE, SYSTEMDRIVE, SYSTEMROOT, TEMP,
USERNAME, USERPROFILE, PROGRAMFILES`. Nothing else crosses into an MCP server.

### Resolution order in the launcher

1. the process environment — covers running the launcher by hand;
2. env files, nearest first: `$STITCH_ENV_FILE`, `<cwd>/.env`, `<launcher repo>/.env`,
   `~/.agentkit/.env`, `~/.zcode/.env`;
3. **`HKCU\Environment`** — the Windows per-user store.

Step 3 is what makes the normal case work. Because ZCode strips the inherited
environment, a key configured through the Windows environment UI is invisible to
the server unless the launcher reads the user store directly. The registry is
queried with a single `reg query HKCU\Environment` call, and only when steps 1
and 2 came up empty.

The launcher forwards **only `STITCH_*` variables** to the proxy. The user store
also holds `GITHUB_PERSONAL_ACCESS_TOKEN`, `RENDER_API_KEY`,
`SUPABASE_ACCESS_TOKEN`, and DB connection strings; forwarding everything would
hand unrelated credentials to a design tool.

## Why user scope

User scope overrides workspace scope for a same-named server, so a workspace
entry would be silently shadowed — editing it would appear to do nothing. One
definition, in one place. It also means the server is available in every project
without repeating the registration.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Not listed in **Settings → MCP** | The session has not restarted since the registration was written | Restart ZCode. Configuration is read at session start. |
| Listed but not visible in the UI | You are on the wrong scope tab | Switch the scope selector from **User** to the workspace, or vice versa — the entry lives at user scope. |
| `failed` with the launcher's help text | `STITCH_API_KEY` absent from the environment, the env files, and the Windows user store | The message lists every location checked. Set the user variable. |
| You are sure the variable is set, but the launcher disagrees | In Git Bash, `reg query ... /v NAME` fails with `ERROR: Invalid syntax.` because MSYS rewrites the `/v` argument — a false negative | Check with PowerShell (`[Environment]::GetEnvironmentVariable('NAME','User')`) before concluding it is missing. |
| Server absent from the config entirely | Unknown key in the JSON, or `command` is not a string | ZCode's config schema is strict — an unknown key drops the server silently. Keep only `type`, `command`, `args`, `cwd`, `env`, `enabled`, `timeoutMs`. |
| `spawn node ENOENT` | `node` not on the PATH ZCode inherits | Use an absolute path to `node` in `command`. |
| `timed out after 30000ms` on first start | `npx` is downloading the package | `timeoutMs` is already raised to `120000`; confirm network access to the npm registry. |
| `AUTH_FAILED` / `Permission Denied` **on a tool call** | Key revoked, or the Stitch API is not enabled for the Google Cloud project | Regenerate the key; run `npx @_davideast/stitch-mcp doctor --verbose`. |
| Designs appear under an unexpected project | Stitch auto-isolates by git repo | Pass `--project-name` to the skill's generate script, or set `STITCH_PROJECT_ID`. |

## Related

`scripts/env/win-env.mjs` reads the same Windows user store for deploy
credentials — `check`, `diff`, and `run --with`. See `scripts/env/README.md`.
