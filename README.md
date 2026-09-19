<div align="center">

<img src="assets/branding/logo-mark.svg" width="96" alt="CampusUTE logo"/>

# CampusUTE

**AI-Powered Smart Digital Campus Platform — HCMUTE**

Kotlin · Jetpack Compose · Spring Boot · FastAPI + OpenAI Agents SDK · RAG · pgvector · Offline-First

[![CI](https://github.com/JasonTM17/CampusUTE_Kotlin_Project/actions/workflows/repo-guard.yml/badge.svg)](./.github/workflows/repo-guard.yml)
[![Release](https://img.shields.io/github/v/release/JasonTM17/CampusUTE_Kotlin_Project)](../../releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Docker-green)](#getting-started)

</div>

> ✅ **Status: v1.0.0 shipped.** Platform phases 0–8 of the roadmap below are
> DONE and verified end-to-end on an emulator against a live docker stack
> (demo GIF + screenshots below). Scoped out of v1.0 honestly and kept on the
> roadmap: secondary modules (map/library/career/analytics), Kafka/ClickHouse
> event analytics, SSE chat streaming, offline notes sync.

## ✨ What is CampusUTE?

One app for nearly everything a HCMUTE student does on campus — backed by a
production-minded platform:

- 📅 **Offline-first timetable & exam schedule** — Room DB is the single source
  of truth; works in elevators, weak Wi-Fi, backend outages.
- 🤖 **Agentic AI Campus Assistant** — router + 8 specialist agents, tool
  calling re-verified by the backend (LLM is *never* the security boundary),
  RAG over university regulations with **mandatory citations** (document,
  page, excerpt).
- 🧠 **Smart Study Planner, Notes + AI** (summarize / flashcards / quiz —
  propose-only, user confirms). Notes CRUD is remote-first (`/api/v1/notes`);
  the offline-first Room SSOT applies to the schedule/timetable (offline notes
  sync is on the roadmap).
- ✅ **Secure QR attendance** — short-lived rotating tokens signed server-side,
  anti-replay nonce; nothing trusted from the client.
- 📊 **Event-driven analytics** — Transactional Outbox → Kafka → ClickHouse
  (architecture ready; SCOPED OUT of v1.0 — core runs fine without it).
- 🔐 **RBAC (6 roles) + ABAC**, JWT with refresh rotation, Argon2, audit logs,
  rate limiting, threat model documented.

## 🏗 Architecture (system context)

```mermaid
flowchart LR
    subgraph Client
        A["Android App<br/>Kotlin + Compose<br/>Room SSOT · WorkManager"]
    end
    subgraph Platform["Monorepo — docker compose (core services)"]
        B["core-api<br/>Kotlin · Spring Boot<br/>REST /api/v1 · SSE"]
        C["ai-service<br/>Python · FastAPI<br/>OpenAI Agents SDK"]
        D[("PostgreSQL<br/>+ pgvector")]
        E[("Redis")]
        F[("MinIO<br/>S3-compatible")]
        G{{Kafka}} --> H[("ClickHouse")] --> I[Grafana]
    end
    A -->|"HTTPS / JWT"| B
    B --> C
    B --> D
    B --> E
    C -->|"vector + hybrid search"| D
    B --> F
    B -.->|"Transactional Outbox"| G
```

Deep dives: [system-overview](docs/architecture/system-overview.md) ·
[Android architecture](docs/architecture/android-architecture.md) ·
[Backend architecture](docs/architecture/backend-architecture.md) ·
[RAG](docs/ai/rag-architecture.md) · [Agents](docs/ai/agent-architecture.md) ·
[Threat model](docs/security/threat-model.md) ·
[ADRs](docs/adr/) · [Database](docs/database/database-design.md)

## 📁 Project structure

```
CampusUTE_Kotlin_Project/
├── apps/android/          # Kotlin + Compose app (multi-module, offline-first)
├── backend/               # Kotlin Spring Boot modular monolith (/api/v1)
├── ai-service/            # Python FastAPI + OpenAI Agents SDK (RAG, tools)
├── packages/api-contracts/  # Frozen OpenAPI snapshot shared by 3 codebases
├── deploy/                # reserved for deployment overlays (compose file at repo root)
├── database/              # migrations are in backend (Flyway); seeds & diagrams
├── docs/                  # architecture, ai, security, adr, demo
├── assets/                # demo GIF, diagrams, screenshots (regenerable)
├── scripts/               # dev scripts (secret-scan, seed, demo, diagrams)
└── .github/workflows/     # repo-guard CI, android/backend/ai CI, release (tag-gated)
```

## 🚀 Getting started

Prerequisites: **Docker**, **JDK 17/21/24** (Gradle 8.14 — *not* JDK 26),
**Android Studio** (SDK 34+).

```bash
git clone https://github.com/JasonTM17/CampusUTE_Kotlin_Project.git
cd CampusUTE_Kotlin_Project
cp .env.example .env                 # fill in only what you need; never commit it
docker compose up -d                 # postgres+pgvector, redis, minio, backend, ai
```

- Backend API: http://localhost:18080/swagger-ui (OpenAPI: `/v3/api-docs`)
- AI service health: http://localhost:8600/health
- Android: open `apps/android` in Android Studio → run `app` (demo accounts
  below; dev-only, synthetic data, disabled in release builds).

> AI works out of the box in **mock/replay mode** (no API key needed). Set
> `OPENAI_API_KEY` in `.env` to enable live models — the key never enters the
> APK.

## 📸 Demo

Full demo flow (login → bell mark-read → notes CRUD with propose-only AI
summarize → timetable → cited AI chat), recorded on the API-35 emulator
against the live docker stack:

![CampusUTE demo flow](assets/demo/demo.gif)

| Login | Timetable (offline-first) |
|---|---|
| ![Login screen](assets/screenshots/login.png) | ![Timetable](assets/screenshots/timetable.png) |
| **Notes with propose-only AI summarize** | **AI chat with mandatory citations** |
| ![Notes AI summarize](assets/screenshots/notes-ai-summarize.png) | ![AI chat citations](assets/screenshots/ai-chat-citations.png) |

Dev-only demo accounts (synthetic seed data — see `backend` seeder):
`student@demo.campusute.vn` / `Demo#Student1`,
`lecturer@demo.campusute.vn` / `Demo#Lecturer1`.

## 🗺 Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Environment gate, bootstrap, CI guard | ✅ DONE |
| 1 | Backend platform + Android core + **contract freeze** | ✅ DONE |
| 2 | Vertical slice: Student Schedule E2E (offline) | ✅ DONE |
| 3 | Generic sync engine (delta, pending queue, optimistic) | ✅ DONE |
| 4 | Academic core (assignment, grade/GPA, QR attendance, events) | ✅ DONE |
| 5 | AI core: agents + RAG + citations | ✅ DONE |
| 6 | 8 agents + eval harness + notes AI | ✅ DONE |
| 7 | Secondary modules + Kafka/ClickHouse analytics | 🗂 SCOPED OUT of v1.0 — roadmap tương lai |
| 8 | Hardening, docs, demo GIF, release v1.0.0 | ✅ DONE |

## 🔐 Security & privacy

- No real HCMUTE personal data — all seed data is synthetic. University SSO is
  an adapter interface (`UniversitySSOAuthProvider`), not a fake integration.
- Secrets live in `.env` (git-ignored) / Android Keystore — never in the repo
  or the APK; CI runs a secret scan on every push.
- The AI layer treats retrieved documents as **untrusted data** (prompt
  injection defense) and filters retrieval by permission **before** the LLM
  sees any context. See the [threat model](docs/security/threat-model.md).

## 🤝 Contributing

Course project — single mainline. Conventional Commits only; every commit must
build; push after each milestone; `plans/` and secrets are never committed.

## 📄 License

[MIT](LICENSE)
