#!/usr/bin/env node
/**
 * Stitch MCP launcher for ZCode.
 *
 * ZCode starts stdio MCP servers with only the MCP SDK's default environment
 * whitelist, so a custom variable such as STITCH_API_KEY is never inherited
 * from the ZCode process. This launcher closes that gap: it resolves the key
 * from the environment or from a local .env file, then hands it to the Stitch
 * MCP proxy.
 *
 * The key is read at runtime and is never written into an MCP config file.
 */

import { execFileSync, spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");

// Only STITCH_* variables cross into the child process. A .env file also holds
// database and JWT secrets; none of those belong in an MCP server.
const FORWARDED = /^STITCH_[A-Z0-9_]+$/;

// Nearest first. A globally installed copy still honours a project-local
// override, because the MCP server inherits the workspace as its cwd.
const ENV_FILES = [
  process.env.STITCH_ENV_FILE,
  join(process.cwd(), ".env"),
  join(REPO_ROOT, ".env"),
  join(homedir(), ".agentkit", ".env"),
  join(homedir(), ".zcode", ".env"),
].filter((path, index, all) => typeof path === "string" && path.length > 0 && all.indexOf(path) === index);

function parseEnvFile(text) {
  const values = {};
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line === "" || line.startsWith("#")) continue;

    const separator = line.indexOf("=");
    if (separator === -1) continue;

    const key = line.slice(0, separator).trim().replace(/^export\s+/, "");
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) continue;

    let value = line.slice(separator + 1).trim();
    const quoted =
      value.length >= 2 &&
      ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'")));
    if (quoted) value = value.slice(1, -1);

    values[key] = value;
  }
  return values;
}

/**
 * Reads STITCH_* variables straight from the Windows per-user environment.
 *
 * ZCode starts MCP servers with the MCP SDK's env whitelist, so a variable the
 * user set in the Windows environment UI is absent from this process. The
 * registry is the authoritative user-level store, so read it there.
 * One call returns every variable; no per-name lookup is needed.
 */
function readWindowsUserEnv() {
  if (process.platform !== "win32") return {};

  let output;
  try {
    output = execFileSync("reg", ["query", "HKCU\\Environment"], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
      windowsHide: true,
    });
  } catch {
    return {};
  }

  const found = {};
  for (const line of output.split(/\r?\n/)) {
    const match = line.match(/^\s+([A-Za-z_][A-Za-z0-9_]*)\s+REG_(?:SZ|EXPAND_SZ)\s+(.+?)\s*$/);
    if (!match) continue;
    const [, name, value] = match;
    if (FORWARDED.test(name) && value && !found[name]) found[name] = value;
  }
  return found;
}

/** Layered lookup: environment, then .env files, then the Windows user store. */
function resolveStitchEnv() {
  const resolved = {};
  const searched = [];

  for (const [key, value] of Object.entries(process.env)) {
    if (FORWARDED.test(key) && value) resolved[key] = value;
  }

  for (const envFile of ENV_FILES) {
    searched.push(envFile);
    if (!existsSync(envFile)) continue;
    let parsed;
    try {
      parsed = parseEnvFile(readFileSync(envFile, "utf8"));
    } catch (error) {
      process.stderr.write(`[stitch-mcp] cannot read ${envFile}: ${error.message}\n`);
      continue;
    }
    for (const [key, value] of Object.entries(parsed)) {
      // First definition wins, so a nearer file overrides a farther one.
      if (FORWARDED.test(key) && value && !resolved[key]) resolved[key] = value;
    }
  }

  // Only touch the registry when something is still missing.
  if (!resolved.STITCH_API_KEY) {
    searched.push("HKCU\\Environment (Windows user environment variables)");
    for (const [key, value] of Object.entries(readWindowsUserEnv())) {
      if (!resolved[key]) resolved[key] = value;
    }
  }

  return { resolved, searched };
}

const { resolved, searched } = resolveStitchEnv();

if (!resolved.STITCH_API_KEY) {
  process.stderr.write(
    [
      "[stitch-mcp] STITCH_API_KEY is not set; the Stitch MCP server cannot start.",
      `[stitch-mcp] Looked in the process environment, then: ${searched.join(", ")}`,
      "[stitch-mcp] Create a key at https://stitch.withgoogle.com -> Settings -> API Keys,",
      "[stitch-mcp] then store it either way:",
      "[stitch-mcp]   Windows user variable (applies everywhere, survives ZCode's env whitelist):",
      '[stitch-mcp]     [Environment]::SetEnvironmentVariable("STITCH_API_KEY","<key>","User")',
      `[stitch-mcp]   or this project's gitignored env file, ${join(REPO_ROOT, ".env")}:`,
      "[stitch-mcp]     STITCH_API_KEY=<key>",
      "",
    ].join("\n"),
  );
  process.exit(1);
}

// Node refuses to spawn a .cmd shim directly since CVE-2024-27980, so Windows
// goes through cmd.exe. Passing the command line as one argument avoids the
// `shell: true` deprecation warning (DEP0190); every token below is a literal.
const isWindows = process.platform === "win32";
const PROXY_PACKAGE = "@_davideast/stitch-mcp";
const [child, childArgs] = isWindows
  ? ["cmd.exe", ["/d", "/s", "/c", `npx.cmd -y ${PROXY_PACKAGE} proxy`]]
  : ["npx", ["-y", PROXY_PACKAGE, "proxy"]];

const proxy = spawn(child, childArgs, {
  stdio: "inherit",
  env: { ...process.env, ...resolved },
});

proxy.on("error", (error) => {
  process.stderr.write(`[stitch-mcp] failed to start the Stitch MCP proxy: ${error.message}\n`);
  process.exit(1);
});

proxy.on("exit", (code, signal) => {
  process.exit(signal ? 1 : (code ?? 0));
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => proxy.kill(signal));
}
