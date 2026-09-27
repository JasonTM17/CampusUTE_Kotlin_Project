# Public repository and release privacy

This guide defines what belongs in the public CampusUTE repository and how to
respond when private material is found. It complements the application
[threat model](threat-model.md); it does not replace credential rotation,
provider-side revocation, or incident response.

## Keep local tooling out of the public tree

Agent runtimes, their local registries, session state, and execution plans are
developer tooling. Keep them on the working machine and ignore their explicit
directories. The repository intentionally retains `.github/workflows/` because
CI and release automation are part of the project.

Before adding a new local tool directory, check the root `.gitignore` and add a
specific rule if it contains local state. Do not add a blanket rule for hidden
files: project configuration such as `.github/` and safe templates such as
`.env.example` must remain available to contributors.

To confirm the current rules and tracked paths:

```bash
git check-ignore -v --no-index .agentkit/example .agents/example .codex/example plans/example
git ls-files -- .agentkit/ .agents/ .codex/ plans/
git ls-files --error-unmatch .github/workflows/repo-guard.yml
```

If one of the local tooling trees was already tracked, remove it from the Git
index while preserving the working copy:

```bash
git rm -r --cached -- .agentkit .agents .codex
git status --short
```

Review the exact staged paths before committing. An ignored path can still be
added deliberately with `git add -f`, so inspect staged content and run the
repository secret scanner before every publication.

## Protect credentials and personal data

- Store working credentials in a local `.env` file or an approved secret
  manager. Never commit access tokens, signing keys, private certificates,
  service-account files, database dumps, or real student records.
- Treat `.env.example` as documentation: use visibly fake placeholders and
  verify that no value grants access to a real service. The scanner allows one
  exact long JWT placeholder; its regression check proves that replacing it is
  still detected and reported without printing the value.
- Keep demo seeding disabled by default. Enable it only for an isolated local
  development or test environment with synthetic data.
- The Android release must not contain server credentials or provider API
  keys. Put provider credentials on the server and rotate any value that may
  have been exposed.
- Inspect every screenshot and decoded GIF frame before publication. Capture
  from a disposable environment with synthetic data, replace identifying demo
  fields with generic values, and state when a screen shows mock or empty
  states. Secret scanners do not reliably inspect image pixels.
- Run `bash scripts/secret-scan.sh` and review its result with a human. Pattern
  scanners reduce risk but cannot prove that a repository contains no secrets.

The repository guard runs the scanner in CI. A green scan is evidence for the
tracked files in that commit; it does not inspect ignored local state, every
historical commit, uploaded release assets, registry layers, screenshots, or
external provider logs.

## Understand what ignore rules change

Adding a `.gitignore` rule affects future Git additions. It does not remove an
already tracked file until that file is removed from the index, and it does not
erase the file from earlier commits. Rewriting public history changes commit
identities and may not remove copies already cloned or cached elsewhere.

If a real credential or personal record was published:

1. Revoke or rotate it at the issuing service immediately.
2. Identify affected commits, releases, packages, logs, and downstream copies.
3. Notify the repository owner through a private channel; do not paste the
   value into a public issue or pull request.
4. Coordinate any history rewrite and release replacement with the owner, then
   verify the new repository and registry state independently.

Removing local tooling from the latest tree is not a claim that earlier public
history has been scrubbed. This repository does not rewrite history
automatically.

## Published containers

The backend and AI service are distributed through Docker Hub and GitHub
Container Registry (GHCR). See [container images and deployment](../deployment/container-images.md)
for the current release tag, manifest digests, package links, Compose setup, and
the limits of the provenance available for older images. Prefer digest-pinned
images for repeatable deployments. Do not infer a vulnerability scan or signed
provenance result unless the release publishes and verifies one.
