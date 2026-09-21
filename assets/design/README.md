# Design gallery

Tracked Stitch-generated screens for CampusUTE. This folder is the durable design source: `plans/`
and `stitch-exports/` are gitignored, so a mockup stored there is not a commitment and nobody else
can open it.

Contract and rules: [../../docs/ai/app-design.md](../../docs/ai/app-design.md) (app-wide) and
[../../docs/ai/chat-design.md](../../docs/ai/chat-design.md) (assistant surface).

## Assistant (Academic Indigo, shipped and device-verified)

| File | screen |
| --- | --- |
| `chat-main.png` | conversation with user/assistant bubbles and agent attribution |
| `citation-sheet.png` | citation bottom sheet with excerpt, page, source, disclaimer |
| `stitch-states-matrix.png` | four-panel state matrix: first run, thinking, error+retry, citations |

## App shell (Campus Blue, design source only — not yet implemented)

Each row was generated against the ceilings in `docs/ai/app-design.md` §3 and then **inspected
pixel-by-pixel**, not just confirmed to exist.

| File | screen | states carried | constraint check |
| --- | --- | --- | --- |
| `shell-components.png` | component sheet in situ | card, card tonal, section header, error state, empty state with icon, offline strip, filter chips, skeleton, badges | PASS — flat fills only, no blur, every glyph inside the core icon set |
| `home-resilient.png` | Trang chủ | normal list, **per-section error + Thử lại**, skeleton loading, offline strip | PASS — the three states are visually distinct, which is the whole point of the screen |
| `study-tasks.png` | Công việc học tập | done/strikethrough, due-today, **unsynced pill**, sync-conflict row with both resolutions, overdue | PASS — conflict card offers "Giữ phiên bản của tôi" and "Nhận phiên bản máy chủ" |
| `grade-transcript.png` | Điểm & học phần | per-course components, letter pills, failing row, not-yet-graded row, loading | PASS — **no cumulative GPA anywhere** (the contract has no credit counts), footer states điểm do giảng viên cập nhật |
| `assignment-submit.png` | Bài tập + nộp bài | overdue/soon badges, submit sheet, 312/5000 counter, server rejection | PASS — helper line says text-only, and there is **no upload, file or camera affordance** |
| `notifications-inbox.png` | Thông báo | unread/Read sections, type filter chips, mark-all, per-row mark-read | PASS — no chevrons, so no implied deep link (`NotificationDto` has no source id) |
| `schedule-week.png` | Lịch học | week strip, 7-column overview, **overlapping-session conflict outlined in red**, day detail, offline-saved strip | PASS |
| `notes-editor.png` | Ghi chú | editor, saving, **AI proposal with Chèn vào / Bỏ qua**, summarize failure, delete confirm | PASS — proposal caption preserves propose-only, and no sparkle glyph (the shipped `✨` must go) |
| `login-failures.png` | Ma trận lỗi đăng nhập (4 panels) | wrong credentials, **rate-limit lockout with countdown**, offline, expired session on a flat scrim | PASS — matches the real `RATE_LIMITED` and `AUTH_TOKEN_INVALID` responses |
| `events-register.png` | Sự kiện (trong Lịch học) | open/registered/ended, seat text only, seats-lookup failure | PASS after one regeneration: the first frame offered "Chi tiết" actions that the data cannot fill, so it was rebuilt with exactly one action per card |

## Known open questions raised by these screens

1. `events-register` needs a host: the shell has five tabs and none of them is events. The frame
   assumes it lives under Lịch học; that is a product decision, not a styling one.
2. Every frame renders Be Vietnam Pro / Inter. Neither is bundled and no font dependency is in
   scope, so the implemented screens will look slightly different until that decision changes.
3. Contrast in these frames was judged by eye. The repo has no accessibility measurement harness.
4. These are pictures, not code. Each one is only done when the states it draws are covered by a
   Robolectric semantics assertion, per `docs/ai/app-design.md` §5.
