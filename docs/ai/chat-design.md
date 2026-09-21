# Chat surface design contract — Academic Indigo

Status: shipped and verified on device (light + dark).
Source of truth: `apps/android/app/src/main/java/com/campusute/app/feature/chat/ChatPalette.kt`.
Every value below is transcribed from that file, not from a mockup — if the two disagree, the code wins.

## Scope: why chat is indigo and the rest of the app is blue

The assistant is the only surface where the product speaks in its own voice, so it carries its own
Material 3 colour scheme. `CampusTheme` (CampusBlue `#0B5FA5`) is **unchanged** and still themes the
top bar, navigation bar, and every other feature. `ChatTheme` wraps only the chat content subtree,
so the app-blue chrome around an indigo body is the intended composition, not drift.

This was a user decision, recorded because it overturns an earlier working note that proposed
re-tinting the whole app.

| Rule | Reason |
| --- | --- |
| Never add `Color(0x…)` literals inside `ChatScreen.kt` | Roles come from `MaterialTheme.colorScheme`, so a re-brand stays a single-file change. |
| Never widen `ChatTheme` past the chat subtree | Two themes in one screen is how a design system dies. |
| Read roles, not hex, in composables | `surfaceContainerLow`, not `#F5F3FF`. |

## Colour

### Light

| Role | Hex | Used by |
| --- | --- | --- |
| `primary` | `#4338CA` | accent stripe, marker styling, send button, links |
| `onPrimary` | `#FFFFFF` | send glyph |
| `primaryContainer` | `#E0E7FF` | user bubble |
| `onPrimaryContainer` | `#312E81` | user bubble text |
| `secondary` | `#6366F1` | reserved |
| `tertiary` | `#0284C7` | reserved |
| `secondaryContainer` | `#E0E7FF` | unconsumed today — set so a future reader cannot fall through to the M3 baseline pink |
| `tertiaryContainer` | `#E0F2FE` | offline banner |
| `onTertiaryContainer` | `#082F49` | offline banner text |
| `background` / `surface` | `#FAF8FF` | canvas |
| `surfaceContainerLowest` | `#FFFFFF` | sheet, chip fill |
| `surfaceContainerLow` | `#F5F3FF` | assistant bubble, composer |
| `surfaceContainer` | `#EEF2FF` | starter-prompt chips |
| `surfaceContainerHigh` | `#E2E8F0` | raised surfaces |
| `surfaceContainerHighest` | `#E2E8F0` | highest tier |
| `onSurface` | `#0D1C2E` | body text |
| `onSurfaceVariant` | `#464554` | labels, meta |
| `outline` | `#CBD5E1` | hairlines |
| `outlineVariant` | `#E2E8F0` | dividers |
| `error` / `errorContainer` / `onErrorContainer` | `#BA1A1A` / `#FFDAD6` / `#410002` | failure row |

### Dark

| Role | Hex | Used by |
| --- | --- | --- |
| `primary` | `#C3C0FF` | accent stripe, markers, send button |
| `primaryContainer` / `onPrimaryContainer` | `#372ABF` / `#E3DFFF` | user bubble |
| `secondaryContainer` / `onSecondaryContainer` | `#1E1B4B` / `#C7D2FE` | reserved, set defensively |
| `tertiaryContainer` / `onTertiaryContainer` | `#0C4A6E` / `#BAE6FD` | offline banner |
| `background` / `surface` | `#121319` | canvas (tinted neutral, never pure black) |
| `surfaceContainerLowest` | `#0D0E13` | deepest tier |
| `surfaceContainerLow` | `#1A1B22` | assistant bubble, composer |
| `surfaceContainer` | `#1E1F27` | chips |
| `surfaceContainerHigh` | `#292A33` | raised |
| `onSurface` / `onSurfaceVariant` | `#E4E2EC` / `#C6C5D6` | text |
| `outline` / `outlineVariant` | `#909098` / `#45464E` | lines |
| `error` / `errorContainer` / `onErrorContainer` | `#FFB4AB` / `#93000A` / `#FFDAD6` | failure row |

## Type

Line height is **at least 1.5×** the size everywhere, because Vietnamese diacritics stack
(Ệ Ở Ứ ọ) and clip at Material 3's default 1.43×.

| Role | Size / line height | Weight |
| --- | --- | --- |
| `titleLarge` | 18 / 26 | SemiBold |
| `titleMedium` | 16 / 24 | SemiBold |
| `titleSmall` | 14 / 20 | SemiBold |
| `bodyLarge` | 16 / 26 | Regular |
| `bodyMedium` | 15 / 24 | Regular |
| `bodySmall` | 13 / 20 | Regular |
| `labelLarge` | 14 / 20 | Medium |
| `labelMedium` | 12 / 16 | Medium |
| `labelSmall` | 11 / 15 | Medium |

**Known deviation:** the Stitch design system specifies Be Vietnam Pro for headings and Inter for
body. Neither is bundled, because adding a font dependency was out of scope for this change set. The
typeface is therefore the platform default while the *metrics* follow the system. This is the one
acceptance criterion met on metrics rather than on typeface.

## Shape and space

- 8pt grid with a 4dp sub-grid; the accent stripe is exactly 4dp.
- Bubbles use a pill shape with the tail on the speaker's side: trailing for the user, leading for
  the assistant.
- Assistant bubbles carry a 4dp `primary` stripe painted in `drawBehind` — not a child with
  `fillMaxHeight` inside an intrinsic-height row, which measures unreliably in Compose.
- Icons are Material Symbols only. No emoji anywhere in the surface.

## States the surface must handle

The design source for this matrix is the Stitch screen
`plans/260921-1040-chatbot-ai-deepening/assets/stitch-states-matrix.png` (four panels: Khởi động /
Đang nghi / Lỗi — thử lại / Trích dẫn).

| State | Rendering | Verified on device |
| --- | --- | --- |
| First run | greeting card + 4 starter prompts, no transcript | yes |
| Thinking | animated three-dot row, composer disabled, send becomes a spinner | yes |
| Answer | agent badge, accent stripe, inline `[n]` markers | yes (light + dark) |
| Citation | one chip per source, numbered `"[n] document · tr.page"`, tap opens a `ModalBottomSheet` with the excerpt, page, source, and a "đối chiếu bản gốc" disclaimer | yes (light + dark) |
| Failure | error row with warning icon and "Thử lại" | yes |
| Rate limited | server's own message, **no retry affordance** (resending only re-rolls the window) | unit-tested |
| Offline | `tertiaryContainer` banner above the composer | yes |

Marker and chip numbering are derived **per source, not per sentence**, so a `[3]` in the body always
has a third chip to tap and no chip ever cites nothing.

## Non-negotiable rendering rules

`answer` and `citations[].excerpt` are untrusted text from ingested documents.

- Render as plain, non-interactive `Text`. No markdown renderer, no `autoLink`, no clickable
  server-supplied URL.
- Citation chips call local handlers only; the payload never supplies a destination.
- Control, bidi-override and zero-width characters are stripped at the parse boundary
  (`sanitizeUntrusted`), and the same codepoints are stripped server-side in
  `ai-service/app/agents.py` so no consumer has to remember to.
- Nothing in the surface may assert verified-official provenance. The sheet says the assistant
  *extracted* the text and tells the student to check the original.

## Verification

Design QA was graded against eight acceptance criteria using screenshots of the running app, and the
two defects that pass only when you look at pixels — a double stacked header, and a `[3]` marker with
no matching chip — were caught that way. The grading, the archived frames, and the Stitch source
screens live under `plans/260921-1040-chatbot-ai-deepening/`, which is **gitignored working
material**, not repository history: treat those paths as local-only and this file as the durable
contract. Contrast was judged by eye — no a11y measurement harness exists in this repo, which is a
real limitation of this contract.
