# Tailor screens (TAILOR role)

Same look and behaviour as the tailor master screens (shared theme, job cards / shop owner tables, instant
pills, 20 per page). Started 2026-11-19.

## Workflow (backend rules, `ItemWorkflow`)

- The tailor's queue: only items assigned to them, in `TAILOR_ASSIGNED` or `STITCHING`.
- **Start stitching**: `TAILOR_ASSIGNED → STITCHING` (`PATCH .../status`), assigned tailor only.
- **Hand over**: `PUT .../shop-assistant` from `STITCHING` → `SHOP_ASSISTANT_ASSIGNED`; the item leaves the queue.
  Assigning the shop assistant is what finishes stitching (no separate "stitched" step).
- Activity: `TAILOR_STITCHING` (stitching started), `ASSIGNED_TO_ASSISTANT` (handed over) → My work.

## Gaps found and decisions (2026-11-19)

| # | Gap | Decision |
|---|---|---|
| G1 | No workload numbers for shop assistants | **Done** — `GET /items/assignees/shop-assistants?withLoad=true` (assigned = SHOP_ASSISTANT_ASSIGNED, inProgress = HEMMING + IRONING, late) |
| G2 | The tailor could re-assign the shop assistant through the API while the item was with the assistant | **Done** — `AssignmentRule.workerAllowedFrom`: the tailor only from `STITCHING`; the shop owner still from any (test `ItemWorkflowTest`) |
| G3 | No "stitching finished" step | Keep: assign = done |
| G4 | No list of items the tailor already handed over | Not now (My work shows counts + chart) |
| G5 | Reusable pieces lived in the tailor master folder | **Done** — moved to `src/Pages/shared/production/` (`DeskParts`, `Modals`, `WorkChart`, `desk.js`, new shared `AssignBoard`, `MyWork`) |

## Screens

- [x] `/tailor/queue` **Work queue** (login home): `TAILOR_ASSIGNED` items, soonest delivery first; All / Due today /
  Overdue pills; job cards or table; design photo, Measurements, Track (step timings); **Start stitching** per item
  or for every ticked item (confirm popup); per-item ticks, Select this page, Excel `Tailor_Orders_<date>.xlsx`.
- [x] `/tailor/assign` **Assign board**: `STITCHING` items; shop assistant dropdown + Assign per item; workload pills
  of the shop assistants (in hand, red when late; hover: waiting / hemming-ironing / late); no re-assign.
- [x] `/tailor/my-work` **My work**: Stitching started · Handed over · All actions · Busiest day + the period chart.
- [x] Layout `TailorLayout` (sidebar: Work queue · Assign board · My work), route `/tailor` → `/tailor/queue`,
  login home `/tailor/queue`. The old `/tailor` and `/tailor/:invoiceId` routes (NavBar) were removed.
- [x] 2026-11-19 tabs renamed: **New jobs** (`/tailor/queue`) · **Stitching** (`/tailor/assign`) · **My progress**
  (`/tailor/my-work`); page titles match, URLs unchanged.
- [ ] Manual test with a tailor login (+ a shop assistant account).
- [ ] Delete the old tailor screens (with OK): `src/Pages/TailorDashboard/TailorDashboard.jsx`, `.css`,
  `TailorOrderDetails.jsx`, `AssignShopAssistant/AssignShopAssistant.jsx`, `.css`.
