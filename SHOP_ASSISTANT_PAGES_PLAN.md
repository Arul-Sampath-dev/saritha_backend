# Shop assistant screens (SHOP_ASSISTANT role)

Same look and behaviour as the tailor master / tailor screens (shared `src/Pages/shared/production/`). 2026-11-19.

## Workflow (backend, no change needed)

- Queue: only items assigned to the shop assistant, in `SHOP_ASSISTANT_ASSIGNED`, `HEMMING` or `IRONING`.
- Steps (assigned shop assistant only, `PATCH .../status`): `SHOP_ASSISTANT_ASSIGNED → HEMMING → IRONING →
  ORDER_COMPLETED`. A completed item leaves the queue.
- Activity: `ASSISTANT_HEMMING`, `ASSISTANT_IRONING`, `ORDER_COMPLETED` → My progress (`/reports/my-activity`).
- Gaps checked: none (queue, rules, activity log and my-activity already allow the shop assistant).

## Screens

- [x] `/shopAssistant/jobs` **My jobs** (login home): items soonest delivery first; step pills All · To start ·
  Hemming · Ironing and due pills All · Due today · Overdue (with counts); job cards or table (shop owner style,
  20 per page); design photo, Measurements, Track (step timings); the one next step per item — **Start hemming**,
  **Start ironing**, **Mark completed**; tick items → **Next step (n)** (confirm popup lists each item's step).
- [x] `/shopAssistant/my-work` **My progress**: Hemming started · Items completed · All actions · Busiest day + chart.
- [x] `ShopAssistantLayout` (sidebar: My jobs · My progress); `/shopAssistant` → `/shopAssistant/jobs`; old
  `/ShopAssistance` address redirects there; login home `/shopAssistant/jobs`.
- [ ] Manual test with a shop assistant login.
- [ ] Delete the old screen (with OK): `src/Pages/ShopAssistance/ShopAssistance.jsx`.
