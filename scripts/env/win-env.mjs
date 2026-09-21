#!/usr/bin/env node
/**
 * win-env — read deploy credentials from the Windows per-user environment.
 *
 * Why this exists: a process only sees the environment it was *started* with.
 * ZCode (and every IDE, terminal, and long-running shell) therefore misses any
 * variable that was added or changed after it launched, and an agent that only
 * reads `$NAME` concludes the credential does not exist. The per-user store in
 * `HKCU\Environment` is the authoritative source and is always current.
 *
 * Commands
 *   list [prefix]              Names and value lengths. Never prints values.
 *   check NAME [NAME...]       Presence in the user store, in this process,
 *                              and on disk in env files. Never prints values.
 *   diff                       Variables the user store has that this process
 *                              cannot see — the "why is my deploy failing" view.
 *   run --with A,B -- CMD ...  Run CMD with those credentials injected into its
 *                              environment. The values never touch the command
 *                              line, stdout, or a log.
 *   get NAME                   Print one value to stdout, for piping into a
 *                              command substitution. Goes to stderr with a
 *                              reminder, because the value lands in scrollback.
 *
 * Exit codes: 0 success, 1 a requested name is missing (fail closed), 2 usage.
 */

import { execFileSync, spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;
const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const ENV_FILES = [
  join(REPO_ROOT, ".env"),
  join(homedir(), ".agentkit", ".env"),
  join(homedir(), ".zcode", ".env"),
];

function fail(message, code = 1) {
  process.stderr.write(`win-env: ${message}\n`);
  process.exit(code);
}

function assertNames(names) {
  for (const name of names) {
    if (!NAME_PATTERN.test(name)) fail(`invalid variable name: ${name}`, 2);
  }
}

/**
 * The authoritative store. One `reg query` returns every variable, so this
 * costs a single process spawn regardless of how many names are requested.
 * Values are taken verbatim — no %VAR% expansion, so what is stored is what
 * the deploy sees.
 */
function readUserStore() {
  if (process.platform !== "win32") {
    return { supported: false, variables: new Map() };
  }

  let output;
  try {
    output = execFileSync("reg", ["query", "HKCU\\Environment"], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
      windowsHide: true,
    });
  } catch (error) {
    fail(`cannot read HKCU\\Environment: ${error.message}`);
  }

  const variables = new Map();
  for (const line of output.split(/\r?\n/)) {
    const match = line.match(/^\s+([A-Za-z_][A-Za-z0-9_]*)\s+REG_(?:SZ|EXPAND_SZ)\s+(.*?)\s*$/);
    if (!match) continue;
    const [, name, value] = match;
    if (value) variables.set(name, value);
  }
  return { supported: true, variables };
}

/** Names defined in the env files, without loading their values into output. */
function readEnvFileNames() {
  const names = new Set();
  for (const file of ENV_FILES) {
    if (!existsSync(file)) continue;
    let text;
    try {
      text = readFileSync(file, "utf8");
    } catch {
      continue;
    }
    for (const rawLine of text.split(/\r?\n/)) {
      const line = rawLine.trim();
      if (!line || line.startsWith("#")) continue;
      const separator = line.indexOf("=");
      if (separator === -1) continue;
      const name = line.slice(0, separator).trim().replace(/^export\s+/, "");
      const value = line.slice(separator + 1).trim();
      if (NAME_PATTERN.test(name) && value) names.add(name);
    }
  }
  return names;
}

const CREDENTIAL_HINT = /(API_KEY|ACCESS_TOKEN|_TOKEN|SECRET|PASSWORD|CONNECTION_STRING|CREDENTIAL)/i;

function isCredentialLike(name) {
  return CREDENTIAL_HINT.test(name);
}

function commandList(prefix) {
  const store = readUserStore();
  if (!store.supported) fail("the Windows user environment is only available on Windows");

  const names = [...store.variables.keys()]
    .filter((name) => !prefix || name.toLowerCase().startsWith(prefix.toLowerCase()))
    .sort((a, b) => a.localeCompare(b));

  if (names.length === 0) {
    process.stdout.write(`no variables matched${prefix ? ` prefix "${prefix}"` : ""}\n`);
    return;
  }

  const credentials = names.filter(isCredentialLike);
  const others = names.filter((name) => !isCredentialLike(name));

  const render = (group, title) => {
    if (group.length === 0) return;
    process.stdout.write(`${title}\n`);
    const width = Math.max(...group.map((name) => name.length));
    for (const name of group) {
      const value = store.variables.get(name) ?? "";
      process.stdout.write(`  ${name.padEnd(width)}  len ${value.length}\n`);
    }
  };

  render(credentials, "Credentials (values never printed):");
  render(others, "Other variables:");
  process.stdout.write(`\n${names.length} variable(s). Read a value with: win-env get <NAME>\n`);
}

function commandCheck(names) {
  assertNames(names);
  const store = readUserStore();
  const fileNames = readEnvFileNames();

  const width = Math.max(10, ...names.map((name) => name.length));
  process.stdout.write(
    `${"NAME".padEnd(width)}  ${"USER STORE".padEnd(10)}  ${"THIS PROCESS".padEnd(12)}  ENV FILES\n`,
  );

  let missing = 0;
  for (const name of names) {
    const inStore = store.variables.has(name);
    const inProcess = Boolean(process.env[name]);
    const inFile = fileNames.has(name);
    if (!inStore && !inProcess && !inFile) missing += 1;
    process.stdout.write(
      `${name.padEnd(width)}  ${(inStore ? "set" : "-").padEnd(10)}  ` +
        `${(inProcess ? "visible" : "MISSING").padEnd(12)}  ${inFile ? "present" : "-"}\n`,
    );
  }

  process.stdout.write(
    "\nUSER STORE is authoritative. THIS PROCESS is a snapshot from launch,\n" +
      "so MISSING there means this app started before the variable existed.\n",
  );
  if (missing > 0) {
    process.stdout.write(`\n${missing} of ${names.length} name(s) not found anywhere.\n`);
    process.exit(1);
  }
}

function commandDiff() {
  const store = readUserStore();
  if (!store.supported) fail("the Windows user environment is only available on Windows");

  const hidden = [...store.variables.keys()]
    .filter((name) => !process.env[name])
    .sort((a, b) => a.localeCompare(b));
  const extra = Object.keys(process.env)
    .filter((name) => isCredentialLike(name) && !store.variables.has(name))
    .sort((a, b) => a.localeCompare(b));

  if (hidden.length > 0) {
    process.stdout.write(
      "In the user store but NOT visible to this process.\n" +
        "This is why a deploy can fail while the variable is definitely set —\n" +
        "and every one of these is still usable via `win-env run`.\n\n",
    );
    const width = Math.max(...hidden.map((name) => name.length));
    for (const name of hidden) {
      const marker = isCredentialLike(name) ? "  <- credential" : "";
      process.stdout.write(`  ${name.padEnd(width)}${marker}\n`);
    }
  } else {
    process.stdout.write(
      "Every user-store variable is visible to this process.\n" +
        "Nothing is stale right now. Re-run this after adding a variable without\n" +
        "restarting the app to see the staleness case.\n",
    );
  }

  if (extra.length > 0) {
    process.stdout.write(
      "\nVisible to this process but not in the user store\n" +
        "(set by a shell profile or a parent process — not durable):\n\n",
    );
    for (const name of extra) process.stdout.write(`  ${name}\n`);
  }
}

/**
 * Resolves a command the way cmd.exe would: a bare name is matched against
 * PATH using PATHEXT order. Windows cannot execute an extensionless file, so
 * the bare name is never a candidate — for `npx` the match is `npx.cmd`.
 */
function resolveExecutable(program) {
  const dirs = (process.env.PATH ?? "").split(";").filter(Boolean);
  const exts = (process.env.PATHEXT ?? ".COM;.EXE;.BAT;.CMD").split(";").filter(Boolean);

  if (process.platform !== "win32") return existsSync(program) ? program : null;

  const hasSeparator = program.includes("\\") || program.includes("/");
  const candidates = [];
  if (hasSeparator) {
    candidates.push(program);
    for (const ext of exts) candidates.push(program + ext);
  } else {
    for (const dir of dirs) {
      for (const ext of exts) candidates.push(join(dir, program + ext));
    }
  }
  return candidates.find((candidate) => existsSync(candidate)) ?? null;
}

/**
 * On Windows the deploy CLIs — `render`, `supabase`, `gh`, `vercel`, `npx` — are
 * installed as `.cmd` shims. Node refuses to spawn a `.cmd` directly since
 * CVE-2024-27980 and does not apply PATHEXT to a bare name, so `spawn("render",
 * …)` fails with ENOENT even though the command works in a terminal.
 *
 * Only that case needs cmd.exe. A real executable is spawned directly so Node
 * does the argument quoting, and only the cmd.exe branch quotes tokens itself —
 * mixing the two is what turns an argument like `-e "a || b"` into a syntax
 * error.
 */
function buildChild(program, args) {
  if (process.platform !== "win32") return { file: program, args, verbatim: false };

  const resolved = resolveExecutable(program);
  const needsShell = resolved === null || /\.(cmd|bat)$/i.test(resolved);
  if (!needsShell) return { file: resolved, args, verbatim: false };

  const quote = (token) => {
    // `%` would be expanded by cmd.exe; `%%` is a literal percent.
    const escaped = token.replace(/%/g, "%%");
    return /[\s"&|<>^()]/.test(escaped) ? `"${escaped.replace(/"/g, '""')}"` : escaped;
  };

  return {
    file: "cmd.exe",
    args: ["/d", "/s", "/c", [program, ...args].map(quote).join(" ")],
    verbatim: true,
  };
}

function commandRun(argv) {
  const separator = argv.indexOf("--with");
  if (separator !== 0) fail("usage: win-env run --with NAME[,NAME...] -- COMMAND [ARGS...]", 2);

  const listEnd = argv.indexOf("--", 1);
  if (listEnd === -1) fail("missing `--` before the command", 2);

  const names = argv
    .slice(separator + 1, listEnd)
    .join(",")
    .split(",")
    .map((name) => name.trim())
    .filter(Boolean);
  const command = argv.slice(listEnd + 1);

  if (names.length === 0) fail("--with needs at least one variable name", 2);
  if (command.length === 0) fail("no command given after `--`", 2);
  assertNames(names);

  const store = readUserStore();
  const childEnv = { ...process.env };
  const resolved = [];
  const unresolved = [];

  for (const name of names) {
    const value = store.variables.get(name) ?? process.env[name];
    if (value) {
      childEnv[name] = value;
      resolved.push(name);
    } else {
      unresolved.push(name);
    }
  }

  if (unresolved.length > 0) {
    // Fail closed: a deploy missing its credential should stop, not half-run.
    fail(
      `not found in the user store or this process: ${unresolved.join(", ")}\n` +
        "         Set it with:  [Environment]::SetEnvironmentVariable('<NAME>','<value>','User')",
    );
  }

  process.stderr.write(`win-env: injected ${resolved.join(", ")} (values not shown)\n`);

  const [program, ...args] = command;
  const child = buildChild(program, args);
  const running = spawn(child.file, child.args, {
    stdio: "inherit",
    env: childEnv,
    shell: false,
    windowsVerbatimArguments: child.verbatim,
  });
  running.on("error", (error) => fail(`cannot run ${program}: ${error.message}`));
  running.on("exit", (code, signal) => process.exit(signal ? 1 : (code ?? 0)));
}

function commandGet(name) {
  if (!name) fail("usage: win-env get NAME", 2);
  assertNames([name]);

  const store = readUserStore();
  const value = store.variables.get(name) ?? process.env[name];
  if (!value) fail(`${name} is not set in the user store or this process`);

  process.stderr.write(
    `win-env: printing ${name} (len ${value.length}) to stdout. Prefer\n` +
      `         \`win-env run --with ${name} -- <command>\` so the value never\n` +
      "         reaches your terminal scrollback or a log.\n",
  );
  process.stdout.write(`${value}\n`);
}

const [subcommand, ...rest] = process.argv.slice(2);

switch (subcommand) {
  case "list":
    commandList(rest[0]);
    break;
  case "check":
    if (rest.length === 0) fail("usage: win-env check NAME [NAME...]", 2);
    commandCheck(rest);
    break;
  case "diff":
    commandDiff();
    break;
  case "run":
    commandRun(rest);
    break;
  case "get":
    commandGet(rest[0]);
    break;
  default:
    process.stdout.write(
      [
        "win-env — read deploy credentials from the Windows per-user environment",
        "",
        "  list [prefix]              names and lengths; never prints values",
        "  check NAME [NAME...]       user store vs this process vs env files",
        "  diff                       what this process cannot see (staleness)",
        "  run --with A,B -- CMD ...  run CMD with the credentials injected",
        "  get NAME                   print one value (prefer `run`)",
        "",
      ].join("\n"),
    );
    process.exit(subcommand ? 2 : 0);
}
