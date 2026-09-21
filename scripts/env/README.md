# Deploy credentials on Windows

How an agent or a script gets an API key on this machine, and why the obvious
approach silently fails.

## The problem this solves

A process only sees the environment it was **started** with.

ZCode, an IDE, and any long-running terminal take a snapshot of the environment
at launch. If a variable is added or changed afterwards — or if the app was
started before the variable existed — that app never sees it. An agent that
reads `$RENDER_API_KEY` then finds nothing, reports "the key is not set", and
the deploy stops or runs half-configured.

The variable is not missing. It is in the Windows per-user store, and the
process is looking at a stale snapshot.

There is a second, narrower version of the same problem: ZCode starts MCP
servers with the MCP SDK's environment whitelist, which includes only
`APPDATA, HOMEDRIVE, HOMEPATH, LOCALAPPDATA, OS, PATH, PROCESSOR_ARCHITECTURE,
SYSTEMDRIVE, SYSTEMROOT, TEMP, USERNAME, USERPROFILE, PROGRAMFILES`. A custom
variable never reaches an MCP server by inheritance at all.

**The rule: read credentials from the per-user store (`HKCU\Environment`), not
from the inherited process environment.**

## Where the credentials are

The Windows per-user environment, editable at *Settings → System → Advanced →
Environment Variables → User variables*. List what is there:

```bash
node scripts/env/win-env.mjs list
node scripts/env/win-env.mjs list RENDER          # filter by prefix
```

Names only, with a length — never a value. A length is enough to tell "set"
from "set to an empty string", which is the distinction that matters most often.

## Commands

| Command | What it does |
|---|---|
| `list [prefix]` | Names and value lengths from the user store. |
| `check NAME [NAME...]` | Presence in the user store, in this process, and in env files. Exit 1 if a name is found nowhere. |
| `diff` | Variables the user store has that **this process cannot see**. |
| `run --with A,B -- CMD ...` | Run `CMD` with those credentials injected. Values never touch the command line, stdout, or a log. |
| `get NAME` | Print one value to stdout, for piping. Prefer `run`. |

## Step by step: deploying with a stored credential

**Step 1 — confirm the credential exists in the user store.**

```bash
node scripts/env/win-env.mjs check RENDER_API_KEY
```

```
NAME            USER STORE  THIS PROCESS  ENV FILES
RENDER_API_KEY  set         MISSING       -
```

`set` in USER STORE is the answer that matters. `MISSING` under THIS PROCESS is
expected and harmless — it just means the app started before the variable
existed. Nothing to fix.

**Step 2 — if and only if USER STORE shows `-`, create it.**

```powershell
[Environment]::SetEnvironmentVariable('RENDER_API_KEY','<value>','User')
```

Then confirm with step 1. A process started afterwards inherits it; the ones
already running still will not, which is why step 3 exists.

**Step 3 — run the deploy with the credential injected.**

```bash
node scripts/env/win-env.mjs run --with RENDER_API_KEY -- render deploys create --confirm
```

The wrapper resolves the value from the user store, adds it to the child's
environment only, and runs your command. It prints `injected RENDER_API_KEY
(values not shown)` and nothing else. If the credential is missing it exits
non-zero **before** running anything — a deploy should stop, not run
half-configured.

**`.cmd` shims work.** On Windows the deploy CLIs (`render`, `supabase`, `gh`,
`vercel`, and `npx` itself) are installed as `.cmd` files, which Node refuses to
spawn directly and cannot resolve from a bare name — `spawn("render", …)` fails
with ENOENT even though `render` works in your terminal. `run` detects that case
and routes it through `cmd.exe` the way your shell would, while a real executable
is spawned directly so Node handles its quoting. Arguments containing spaces,
`&`, `|`, `(`, or `%` survive intact either way, and the child's exit code is
passed through unchanged.

Several at once:

```bash
node scripts/env/win-env.mjs run --with RENDER_API_KEY,SUPABASE_ACCESS_TOKEN -- <command>
```

**Step 4 — verify without revealing anything.**

```bash
# Confirm the child really received it, by length only.
node scripts/env/win-env.mjs run --with RENDER_API_KEY -- node -e \
  "console.log('len', (process.env.RENDER_API_KEY||'').length)"
```

## Diagnosing "but the variable IS set"

Run this first. It answers the question directly:

```bash
node scripts/env/win-env.mjs diff
```

```
In the user store but NOT visible to this process.
This is why a deploy can fail while the variable is definitely set —
and every one of these is still usable via `win-env run`.

  RENDER_API_KEY  <- credential
  STITCH_API_KEY  <- credential
```

Anything listed here is usable through `run` right now. Do **not** conclude a
credential is absent from a `diff` result showing it in the store.

### The Git Bash trap

In Git Bash, `reg query "HKCU\Environment" /v NAME` fails with
`ERROR: Invalid syntax.` — MSYS rewrites arguments that begin with `/`, so `/v`
never reaches `reg.exe`. Combined with `2>/dev/null` and a pipe, the failure
looks exactly like "the variable does not exist" and silently produces a wrong
answer.

Use PowerShell or this script instead. If you must call `reg.exe` from Git Bash,
set `MSYS_NO_PATHCONV=1` or double the slash (`//v`).

## Recipes without the script

Any of these read the authoritative store directly.

```powershell
# Presence + length only
$v = [Environment]::GetEnvironmentVariable('RENDER_API_KEY','User')
if ($v) { "RENDER_API_KEY set (len $($v.Length))" } else { "RENDER_API_KEY absent" }
```

```powershell
# List every credential-like name in the user store
(Get-Item 'HKCU:\Environment').Property |
  Where-Object { $_ -match 'KEY|TOKEN|SECRET|CONNECTION_STRING' } | Sort-Object
```

```bash
# Git Bash / cmd — whole store at once, no per-name lookup
reg query 'HKCU\Environment'
```

## Naming convention

| Shape | Use |
|---|---|
| `<SERVICE>_API_KEY` | A service used across projects (`RENDER_API_KEY`) |
| `<SERVICE>_ACCESS_TOKEN` | Same, where the service calls it a token (`SUPABASE_ACCESS_TOKEN`) |
| `<PROJECT>_<SERVICE>_MCP_TOKEN` | A token scoped to one project (`HEALTHCARE_RENDER_MCP_TOKEN`) |
| `..._2` | A second account or workspace on the same service |

## Rules

- Never print a credential value. Report `set` / `absent`, or a length.
- Never copy a value out of the store into `.env`, a workflow file, a compose
  file, or a committed settings file. One source, one place.
- Inject at run time, in the child process only, via `win-env run`.
- Fail closed: a missing credential stops the command before it does anything.
- When a variable is added, restart the app that needs it — or use `win-env run`,
  which does not depend on the app's snapshot.
