# App design contract and screen catalogue — CampusUTE

Status: proposal, partially implemented. The token layer described in §1 is shipped; the screen
catalogue in §4 is the queue to design and build.
Source of truth for colour: `core/designsystem/theme/CampusTheme.kt`,
`core/designsystem/theme/CampusTypography.kt`, and (chat only) `feature/chat/ChatPalette.kt`.
The chat surface's own contract is [chat-design.md](chat-design.md).

## 1. Two-tier colour, and why

`CampusBlue` themes the app shell. `Academic Indigo` is scoped to the assistant subtree
(`CampusApp.kt` `tab == 3`). The split is deliberate: indigo is the signal that the app is speaking
in its own voice, and it is the one durable result of the assistant work. A global re-brand is a
separate decision, not something to smuggle into individual screens.

**The defect this section fixed.** `CampusTheme` used to declare only `primary` and `secondary`.
`lightColorScheme` falls back to the Material 3 baseline for every role it is not given, and that
baseline is purple — so `primaryContainer`, the `surfaceContainer*` tiers, `tertiary*` and the
sheet/chip fills had no meaning for four of the five tabs. Any screen designed against "the existing
theme" was designed against undefined tokens. All 25 roles are now declared for light and dark.

**Completing the scheme is a visible change, not a no-op.** Grepping for explicit
`colorScheme.<role>` reads finds only four roles outside chat, which invites the conclusion that
filling the rest cannot alter anything on screen. That reasoning is wrong, because Material 3
components consume roles implicitly without the call site naming them:

| Consumer | Role it takes implicitly | Before | After |
| --- | --- | --- | --- |
| `Card(...)` — `core/designsystem/components/CampusComponents.kt:61` | `surfaceContainer` | baseline violet `#E7E0EC` | `#E8EFF6` |
| `NavigationBarItem` indicator — `feature/appshell/CampusApp.kt:119-144` | `secondaryContainer` | baseline violet `#E8DEF8` | amber `#FFE9C5` |

Verified on device after installing the themed build: the home greeting card, the assignment card
and the selected-tab pill all render in the blue/amber family instead of violet. So the correct
statement is that this change **retints the shell from accidental Material purple to the brand
hues** — an improvement, but a visual one, and it should be looked at before it is merged. Every
screen catalogue entry in §4 now has a real token to be built against.

Rules, unchanged from the chat contract:
- No `Color(0x…)` literal outside the three palette files.
- No second theme inside one screen. The AI-summary affordance in Notes stays blue — it is not the
  assistant tab.
- Read roles, not hex.

## 2. Type

`CampusTypography` is the shared scale, ≥ 1.5× line height on every role, because Material's default
~1.43× clips stacked Vietnamese tone marks (Ệ Ở Ứ ọ). It is a localization constraint, so it lives
in the shell. Chat previously carried a private copy of the same scale; it now imports it, since a
nested `MaterialTheme` replaces rather than inherits and must pass the scale explicitly.

Be Vietnam Pro and Inter are **not** bundled and adding a font dependency is out of scope, so the
typeface is the platform default while the metrics follow this scale. Every mockup in §4 shows the
branded faces; that gap is known and accepted, not a surprise to rediscover at implementation time.

## 3. Hard ceilings on what may be designed

| Ceiling | Evidence | Consequence for a mockup |
| --- | --- | --- |
| Icons: core set only | `material-icons-extended` is not a dependency and `isMinifyEnabled = false` (`app/build.gradle.kts:26`) | ~49 `Icons.Filled` names are available. No `Mic`, `AttachFile`, `Bookmark`, `Schedule`, `School`, `Article`, `SmartToy`, `FilterList`, `BarChart`, `Help`, `Visibility`, `QrCode`, `Logout`. **`Icons.Filled.Error` does not exist** — the shipped chat error row uses `Warning`. |
| `minSdk = 26` (`app/build.gradle.kts:16`) | `Modifier.blur` compiles to `RenderEffect`, which is API 31+ | Frosted glass / blurred backdrops build green, look right on a modern emulator, and silently degrade on Android 8–11. Banned. |
| No image loader, no charting library, no `androidx.graphics` shapes in the dependency graph | `apps/android/gradle/libs.versions.toml` | No photos, no data-viz frames, no custom `Shape` paths. |
| Navigation is five tabs plus two sub-screen routes | `feature/appshell/CampusApp.kt` (`NavHost` declares `login`, `home`, `grades`, `events`) | The tab bar still cannot host a sixth destination; anything new is either a section of an existing tab or another `SubScreen` route with a back affordance |
| Untrusted text stays plain and non-interactive | `feature/chat/ChatViewModel.kt` `sanitizeUntrusted`, `docs/ai/chat-design.md` | No markdown, no links, no tappable server-supplied URLs inside `answer`/`excerpt`. |
| Data must already be reachable | `core/network/CampusApi.kt` (17 endpoints) vs `packages/api-contracts/openapi.json` (9 paths) | The **frozen contract is not the source of truth** — it omits grades, events, assignments, notifications, notes and tasks that the client really calls. Gate a screen against `CampusApi.kt` and the controllers, not the snapshot. |

Designs are only commitments if their artefacts are versioned. `plans/` and `stitch-exports/` are
both gitignored, so generated screens live in **`assets/design/`** (tracked) from now on. The chat
screens generated before this rule existed have been copied there.

## 4. Screen catalogue

Ranked by the gap between what ships and what the data already supports. Each row is one Stitch
screen to generate, then build. All ten have been generated and are versioned under
[`assets/design/`](../../assets/design/README.md) with a per-screen constraint check.

| # | slug | screen | job to be done | states the frame must carry | what the code lacked when the frame was drawn |
| --- | --- | --- | --- | --- | --- |
| 1 | `home-resilient` | Trang chủ | "What needs my attention today?" | skeleton, per-section error + Thử lại, offline, empty-new-student, inbox preview | `HomeViewModel` wrapped every call in `runCatching { }.onSuccess { }` with no else branch, so a 500 rendered as "Chưa có…" — failure was literally indistinguishable from empty |
| 2 | `study-tasks` | Công việc học tập | Track study tasks that survive offline and merge | list, add-row, pending-sync badge, **sync-conflict resolution**, offline | `TasksViewModel` is complete and has no composable at all — a shipped feature with zero UI |
| 3 | `grade-transcript` | Điểm & học phần | "Where do I stand per course?" | loading, empty, course rows with weighted components, letter grade, failing styling | no screen, and no `grades/me` on the client at all; `GpaCalculator` was referenced only by its own test. **Show per-course totals and letters, not a cumulative GPA** — `grades/me` returns no credit counts, which `GpaCalculator` requires |
| 4 | `assignment-submit` | Bài tập + sheet nộp | Know deadlines, submit before cutoff | overdue badge, sắp-hết hạn, submit sheet with 5000-char counter, server reject "Đã quá hạn", 403 not-enrolled, success | `AssignmentsSection.kt` was dead code (defined, never called); the live renderer was inlined in `HomeScreen.kt` and rejections were swallowed to a boolean |
| 5 | `notifications-inbox` | Hộp thông báo | Triage GRADE vs ASSIGNMENT vs SYSTEM | unread separator, type filter chips, mark-read feedback, loading, error+retry, offline-stale, empty | `type` was delivered in `NotificationDto` and never rendered; deep links must **not** be designed — the DTO carries no source id, so tap = mark read only |
| 6 | `schedule-week` | Lịch tuần | See the whole week, not one day | populated grid, conflict pair highlight, today marker, empty week, offline-saved badge | `TimetableScreen` rendered one day; `TimetableUiState.empty` was set but never read; the offline banner used a raw amber hex duplicating the theme |
| 7 | `notes-editor` | Ghi chú | Capture and refine with AI as a proposal | editor, save-busy, summarize-failed, blank-summary fallback, delete-confirm, unsaved-draft warning | a failed AI summary wrote into the same slot as a failed list load, so the only button reloaded notes instead of re-asking; closing an edited draft discarded it silently; `✨` was used as an icon |
| 8 | `login-failures` | Đăng nhập | Recover from each distinct failure | invalid credentials, **rate-limit lockout**, offline, session-expired bounce | one raw red string for every cause, and a real 429 fell into `catch (HttpException)` → "Email hoặc mật khẩu không đúng." |
| 9 | `events-register` | Sự kiện | Find and claim seats | list with `seatsLeft`, registered pill, full event, idempotent double-tap, loading | no UI and no client endpoint despite a live `EventController`. Detail sheets must not invent body copy — `EventDto` has no description |
| 10 | `shell-tokens` | Component sheet | Make the shared vocabulary visible | `CampusCard` tonal + clickable variants, `SectionHeader`, `CampusErrorState`, shared `OfflineBanner`, filter-chip row, skeleton, `CampusEmptyState` **with an icon slot** | these were hand-rolled per feature: `SectionHeader` 3×, error rendering 3×, offline banner 2×, and `CampusEmptyState` was icon-less by construction |

### Build status of this catalogue

Nine of the ten are implemented and covered by Robolectric semantics assertions
(`apps/android/app/src/test/.../HomeResilienceUiTest`, `GradesScreenUiTest`,
`EventsScreenUiTest`, `NotesScreenUiTest`, `BellBadgeUiTest`, `LoginViewModelTest`).

- **Built**: 10 `shell-tokens`, 1 `home-resilient`, 5 `notifications-inbox`, 4 `assignment-submit`,
  6 `schedule-week`, 7 `notes-editor`, 8 `login-failures`, 3 `grade-transcript`, 9 `events-register`.
- **Not built**: 2 `study-tasks` — deliberately, for the reason in §5 step 3.
- Screens 3 and 9 needed client endpoints that did not exist (`grades/me`, `events`,
  `events/{id}/register`), so those were added to `CampusApi` against the live controllers.
- Screens 3 and 9 also needed a host. They are NavHost destinations with a real back stack,
  entered from home's "Học vụ" section, which supersedes the §3 note that navigation is tabs only.
- **No screen in this set has been inspected on a device.** The chat surface was, before this
  catalogue was built; these nine are verified by compile, `assembleDebug` and Robolectric
  assertions about which node appears in which state — not by looking at them.

### Explicitly not designing

Attendance QR scanner (no camera dependency, and hand-typing a rotating signed token is not a
flow) · file-upload submission (the backend stores a text note) · password reset, signup, push
settings (no endpoints) · lecturer/admin surfaces (the shell is student-only) · profile/settings
(`/me` returns five fields) · another chat frame (already shipped and device-verified).

### Emoji debt found while auditing — cleared

`✓` and `●` at `AssignmentsSection.kt:36` and `HomeScreen.kt:51`, and `✨` at `NotesScreen.kt:100`
violated the no-emoji rule. All three sites are gone: `AssignmentsSection.kt` was deleted outright
(its replacement is the submit sheet), status now renders as `CampusStatusBadge` text with a tone,
and the notes action uses `Icons.Filled.Edit`. A `grep` for emoji codepoints across
`apps/android/app/src/main` returns nothing; that grep is the gate §6 asks for.

## 5. Sequencing

0. Token + type completion — **done** (§1, §2).
1. Per-tab ViewModel state *before* designing that tab — **done for every tab built**. Exit
   criterion met: `HomeResilienceUiTest`, `GradesScreenUiTest` and `EventsScreenUiTest` each prove
   "API failed" ≠ "no data" against the fake-`CampusApi` harness, and each fails on the old shape.
2. Generate and build two screens per wave, ordered 10 → 1 → 5 → 4 → 6 → 7 → 8 → 3 → 9 (the
   component sheet first, because every other screen consumes it) — **done**, in that order.
3. Never batch with these: any Room version bump (`DatabaseModule.kt` uses
   `fallbackToDestructiveMigration()` with `exportSchema = false`, so a bump wipes `study_tasks`),
   surfacing `TasksViewModel` or `GpaCalculator` (each needs its own plan), and any contract
   amendment. `study-tasks` (catalogue #2) is therefore still unbuilt, and the grades screen uses
   `GpaCalculator.letter`/`courseTotal` for per-course math only — it does not surface the
   cumulative GPA those calls would need credits for.

## 6. Guardrail

There is no lint, detekt, or screenshot test in any workflow, so nothing currently stands between
"pretty mockup" and "committed UI". The cheapest enforcement that adds no dependency is a
`repo-guard.yml` step failing on: `Color(0x` outside the three palette files, emoji codepoints in
`apps/android/app/src/main`, and `Icons.*` names outside the core set. All three checks are run by
hand at each wave boundary and are currently clean; the check itself is not yet in CI, so §3 is
still prose — prose that was already broken once by raw hex in the timetable banner, which is now
replaced by the shared `CampusOfflineBanner`.
