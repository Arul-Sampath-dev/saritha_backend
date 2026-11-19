# TAILOR_MASTER pages — brainstorm, backend gaps and checklist

Written 2026-11-18. **Status:** decisions taken ("do" = go with the suggestions, see section 6). **Step 1 done:**
backend P1 (BE-1, BE-2, BE-3, BE-12) built, compiled, `QueueFilterTest` 5/5, docs updated. **Next: step 2** — shared
components + TM layout / routes, then the Workplace.

> **2026-11-18 — change of direction.** The new Dashboard / Workplace (steps 3–4) were "not good"; the user asked to
> **go back to the old tailor master functionality with the modern UI first**, and improve from there.
> Done:
> - `/tailorMaster/desk` **Master desk** (= old dashboard): whole queue by invoice date (Today / Yesterday / This week
>   Sun–Sat / This month / This year / Custom + Apply / Reset), search by invoice / customer over the whole queue,
>   Job cards (one per item) or Table (one per invoice: Inspect items (n), urgency, Unassigned: N / All assigned),
>   12 / 24 / 48 per page with « ‹ 1–5 › », filters in the URL, per-invoice select + select-all + Excel
>   `Master_Queue_<date>.xlsx` (with Tailor), Mark cut, Assign / Re-assign tailor, Track, Measurements, "Cutting queue".
> - `/tailorMaster/cutting` **Cutting queue** (= old My orders): ORDER_ACCEPTED items minus Embroidery / Lining
>   Material / Saree Fall / Saree Pico / Designing; ranges with "Last week" (8 days before today); cards or table (one
>   per item); Mark cut; select + `Cutting_Orders_<date>.xlsx`; Back to master desk.
> - `/tailorMaster/orders/:invoiceId` **Order items** (= old OrderDetails): table of items with measurements, design
>   photo (placeholder when missing), status, Mark cut (new items — replaces the broken my-orders variant), Assign /
>   Re-assign (allowed only when the backend says so), Track status.
> - Popups: read-only measurements (garment template order + notes), 9-step track timeline, design photo, assign tailor
>   (tailor list, "already assigned to …").
> - Old bugs not carried over: card "Assign tailor" works (was broken), blank "Customer ID" column dropped, pages no
>   longer contain invoices with no visible items, URL search re-runs, `/my-orders/:orderId` dead route not needed.
> - Login home `/tailorMaster/desk`. Sidebar: Master desk · Cutting queue.
> - Steps 3–4 files are no longer routed (`TailorMaster/Dashboard/*`, `TailorMaster/Workplace/*`, `lib/workplace.js`,
>   `lib/useItemActions.js`) — waiting for OK to delete. Backend step-1 endpoints stay (additive, unused for now).
> - [ ] Test with a TM login; then decide improvements one by one.
>
> **2026-11-19 — improvements on the rebuilt screens ("do all": F, A, C, B, D, E).** Frontend only; the data comes
> from the existing queue / tailor-load / my-activity endpoints (no backend change).
> - [x] **F** Cutting queue: **Mark cut (n)** for every item of the ticked orders (confirm popup listing the items,
>   one call per item, failures reported). Master desk: overdue orders listed first (most overdue on top).
> - [x] **A** Master desk count strip: To cut · Waiting for a tailor · With tailors · Overdue (whole queue). A tile
>   filters the whole queue to its items (URL `stage`), clicking again or the chip clears it; Apply / Reset clear it.
> - [x] **C** Printable **cutting sheet** (`components/CuttingSheet.jsx`): one block per item — invoice, customer,
>   garment × qty, ordered / delivery date, measurements grid, notes, design photo, "Cut by / Date / Checked" line.
>   From the Cutting queue (**Print sheets (n)** for ticked orders) and the Order items page (**Print cutting sheet**).
> - [x] **B** `/tailorMaster/assign` **Assign board**: cut items with no tailor (soonest due first, filter, tick /
>   tick all, measurements) + tailors with their load (waiting · stitching · late) → "Assign n items to <tailor>".
> - [x] **D** `/tailorMaster/follow-up` **Follow-up**: items with tailors grouped by tailor (most late first) —
>   Overdue / Due in 3 days / All with tailors (URL `show`), Track, Re-assign, open the order.
> - [x] **E** `/tailorMaster/my-work` **My work**: `GET /reports/my-activity` for a range (default this week; max 366
>   days) — Items cut, Tailor assignments (re-assignments included), All actions, Busiest day + per-day chart / table.
> - Sidebar: Master desk · Cutting queue · Assign board · Follow-up · My work. `WorkChart.jsx` copied to
>   `TailorMaster/components/` (the Dashboard copy goes with the steps 3–4 delete).
> - [ ] Test with a TM login (and 2 tailors) — see the test list in the chat / checklist.
>
> **2026-11-19 — review change 1: Master desk removed.** Page `Desk/MasterDeskPage.jsx`, its route and sidebar link
> deleted (with its count strip / stage filter helpers and CSS). Login home and `/tailorMaster` now open the
> **Cutting queue**; Order items "back" / empty-state go there too. Sidebar: Cutting queue · Assign board ·
> Follow-up · My work. Order items is still reachable from Follow-up (invoice link).
>
> **2026-11-19 — review change 2: simple Assign board.** No tick boxes and no tailor panel any more: each waiting item
> is one row with a **tailor dropdown** ("Name — n in hand, n late") and its own **Assign** button. A read-only strip
> on top shows each tailor's work in hand / late. Search and Measurements stay; soonest delivery first.
>
> **2026-11-19 — review change 2b.** Assign board: **Tailors' workload** card back on top (per tailor: jobs in hand,
> waiting · stitching · late); waiting items as **Cards or Table** (`?view=table`); the native select replaced by
> `components/TailorPicker.jsx` (design.md style: hairline border, 8px radius, initials, mono meta "n in hand · n late",
> "Least work" tag, keyboard ↑ ↓ Enter Esc, floating panel not clipped by the table).
>
> **2026-11-19 — review change 3: instant filters (no Apply button).** Chosen by the user: due-date pills, same on the
> Assign board, instant pills on My work.
> - Cutting queue: date-range dropdown + Apply replaced by pills **All · Overdue · Due today · Tomorrow · This week**
>   (today … today + 6) with item counts (URL `due`); orders sorted soonest delivery first (no date last).
> - Assign board: the same pills (count of waiting items) above the search.
> - My work: pills Today · Yesterday · This week · This month · This year · Custom; Custom shows two date fields and
>   loads as soon as both are valid (max 366 days).
> - `DateRangeBar` and `CUTTING_RANGES` removed; new `FilterPills` (design.md pill-outline, filled when active).
>
> **2026-11-19 — review change 4.** Assign board: workload made small — a 240px right column (sticky; above the list
> on screens under 1024px) listing each tailor with jobs in hand and late count (hover: waiting / stitching / late).
> Tailor dropdown back to a classic `<select>` ("Name — n in hand, n late"); `TailorPicker.jsx` removed.
>
> **2026-11-19 — review change 5.** Assign board: dropdown shows tailor names only; the right workload column replaced
> by a one-line "Workload" strip above the list (name + jobs in hand, red "(n late)"; hover: waiting / stitching /
> late) so the table gets the full width. The select is styled to design.md (white field, 1px hairline border, 8px
> radius, thin slate chevron, violet focus ring, grey "Choose tailor…" placeholder).
>
> **2026-11-19 — review change 6.**
> - Assign board workload as **pills** ("Name [n]" outline pills, count red when the tailor has late items; hover:
>   waiting / stitching / late).
> - Tables like the shop owner ones on Cutting queue, Assign board and Follow-up: two-line cells (invoice + order
>   date, garment + qty, due date + urgency badge), the row opens the order (click / Enter; buttons and the dropdown
>   don't), the shop owner `Pagination` ("1–20 of N · Page x of y"), **20 per page, no page-size picker**
>   (`DeskPager` / `pageWindow` removed; `pageOf` + `PAGE_SIZE` in `lib/desk.js`). Assign board now pages too.
> - My work chart: Today / Yesterday / This week → last 7 days (day by day); This month → week by week (Sun–Sat,
>   cut to the month); This year → month by month; Custom → day (≤ 31 days) / month (≤ 366) / year (longer, up to
>   10 years, read in 366-day pieces and merged). Card title "Performance · …"; table view follows the grouping.
>
> **2026-11-19 — review change 7.**
> - Due pills on Cutting queue and Assign board: only **All · Due today · Overdue** (Tomorrow / This week removed).
> - Follow-up redesigned: page "Tailors" lists **every tailor of the shop** as a card (jobs in hand, waiting,
>   stitching, late; late first, then busiest). Clicking a tailor (`?tailor=<userId>`) shows that tailor's current
>   items (assigned / stitching), soonest delivery first, as **cards or table** (shop owner table style, row opens
>   the order, 20 per page) with All / Due today / Overdue pills, Track and Re-assign; "All tailors" goes back.
>
> **2026-11-19 — review change 8.** Cutting queue shows the **design photo**: a thumbnail on each job card (56px) and
> in the table's Garment cell (44px); a grey image icon when there is no photo or it fails to load; click opens the
> design popup (full size link). The data was already there (`imageUrl` on every work item) — only the UI was missing.
> New `DesignThumb` in `components/DeskParts.jsx` (JobCard takes optional `onDesign`).
> Same thumbnails on the **Assign board** (cards + table) → design popup.
>
> **2026-11-19 — review change 9 (Cutting queue fixes).** Ticks were per **invoice** (old behaviour), so ticking one
> card ticked every card of that invoice → now **per item**. Paging counted invoices while the pills / cards counted
> items (page "1–20 of 12" with 30 cards) → the list is now one row per item, so the pager, the pills, the subtitle
> and Export / Print / Mark cut counts all count items. "Select this page / Clear this page" added in the toolbar
> (cards and table). Export groups the ticked items under their invoice.
>
> **2026-11-19 — review change 10 (item timings).** Work items already carry `history` [{userId, action, timestamp}].
> - Track popup (Follow-up and Order items): every reached step shows when it started and "took 2d 4h" (current
>   step: "1d 3h so far"). *(The collapsible stage-history list was added, then removed on request — steps only.)*
> - Follow-up tailor items: a **Time** column / card line — "In stitching for 1d 4h" and "With tailor 2d 3h"
>   (from the latest assignment to this tailor); the button stays "Track".
> - Helpers in `lib/desk.js`: `workTimeline`, `stepTimes`, `inStatusSince`, `withTailorSince`.
>
> **2026-11-19 — tabs renamed:** Cutting queue · **Assign tailors** (was Assign board) · **Tailors** (was
> Follow-up) · **My progress** (was My work); page titles match, URLs unchanged. Shared pieces now in
> `src/Pages/shared/production/`.

### Progress
- [x] **Step 1 [BE] P1** (2026-11-18)
  - [x] BE-12 `WorkItem.dueDate` (yyyy-MM-dd) + `daysOverdue` (IST) on every work item.
  - [x] BE-2 `GET /items/queue` + `search`, `sort=oldest|newest|dueSoonest`, `due=today|overdue|week|none` (whole
    queue read in Java when any is set, max 5000 items; plain status query still paged in the database).
  - [x] BE-2 `GET /items/queue/orders` — same filters, grouped by invoice, paged per order, counts per status.
  - [x] BE-1 `GET /items/queue/summary` — countsByStatus, totalItems, orders, late, dueToday, dueThisWeek,
    withoutDueDate.
  - [x] BE-3 `GET /items/assignees/tailors?withLoad=true` — `assigned`, `inProgress`, `late` per tailor.
  - [x] Unit test `QueueFilterTest` (parameters, search, due filters, sorts, daysOverdue) — 5/5.
  - [x] `MODULES_API_DOCUMENTATION.md` section 4 updated.
  - [ ] Live check against MongoDB with a TM login (after restart).
- [x] **Step 2 [FE]** shared components move + TM layout, routes, login home (2026-11-18)
  - [x] Moved (git mv, imports rewritten in 19 files): `ShopOwner/components/*` → `Pages/shared/components/`,
    `ShopOwner/lib/{format,period,invoice}.js` → `Pages/shared/lib/`, `ShopOwner/shopOwner.css` →
    `Pages/shared/theme.css`. Role-specific `lib/invoiceForm.js` and `lib/deliveries.js` stay in `ShopOwner/lib/`.
  - [x] `Pages/shared/AppShell.jsx` = the sidebar shell (role guard, nav, user, logout); `ShopOwnerLayout` now uses it.
  - [x] `Pages/TailorMaster/TailorMasterLayout.jsx` (nav: Dashboard · Workplace), placeholder pages
    `Dashboard/TailorMasterDashboardPage.jsx`, `Workplace/WorkplacePage.jsx` (links to the old screen).
  - [x] Routes: new top-level `/tailorMaster` (index → dashboard, `dashboard`, `workplace`); the old NavBar branch keeps
    `:invoiceId`, `my-orders`, `my-orders/:orderId`; the old dashboard moved from the index to `/tailorMaster/legacy`.
    Checked with `matchRoutes` (7 paths).
  - [x] `Login.jsx` `ROLE_HOME.TAILOR_MASTER = '/tailorMaster/dashboard'`.
  - [x] `api/production.js`: `getQueue`, `getQueueOrders`, `getQueueSummary`, `listTailorsWithLoad`;
    `api/reports.js`: `getMyActivity`.
  - [ ] Test: log in as TM → new shell; shop owner pages still look the same (theme moved).
- [x] **Step 3 [FE]** Workplace (+ order page) (2026-11-18)
  - [x] `Workplace/WorkplacePage.jsx` — tabs New · Cutting · With tailors · All (counts from `/items/queue/summary`),
    search (300 ms), delivery filter (Any / Late / Due today / This week), sort (Due soonest default / Oldest / Newest),
    filters + page in the URL (Back from an order keeps them), "N items are past the delivery date" shortcut,
    pages of 10 orders (`/items/queue/orders`), auto-refresh every 3 min while idle.
  - [x] `Workplace/OrderCard.jsx` — order header (invoice no., customer, ordered date, due badge: late / today /
    tomorrow / in N days), item rows (photo thumb, name ×qty, status, tailor, "N measurements"), buttons from
    `availableActions`: Start cutting · Assign tailor · Re-assign; footer: "Start cutting all (n)", "One tailor for all
    cut items (n)", View order.
  - [x] `Workplace/AssignTailorModal.jsx` — tailors least busy first with "2 waiting · 1 stitching · 1 late"
    (`withLoad=true`), last tailor used pre-selected, current tailor disabled, works for one or many items.
  - [x] `Workplace/ItemDetailsModal.jsx` — photo, measurement table, stage history.
  - [x] `Workplace/OrderPage.jsx` (`/tailorMaster/workplace/:invoiceId`) — all TM-stage items of the order with photo,
    measurements, actions, stage history; bulk buttons; **Print cutting slip** (print CSS hides the shell / buttons).
  - [x] `lib/useItemActions.js` — start cutting (asks first; TM can't undo), assign; many items one after the other,
    failures listed at the end, 409 → "changed meanwhile", always reloads.
  - [x] `lib/workplace.js` helpers (checked in Node: due text, load sort, tab counts, timeline, labels).
  - [x] CSS `tm-*` in `shared/theme.css`.
  - [ ] Test with a TM login + 2 tailors (cut → assign → re-assign; bulk; print; phone width).
- [x] **Step 4 [FE]** Dashboard (2026-11-18) — `Dashboard/TailorMasterDashboardPage.jsx` + `Dashboard/WorkChart.jsx`
  - [x] Greeting + date; Refresh; "Open workplace"; late-items note (→ workplace Late filter).
  - [x] "Waiting in your stages" tiles: New — to cut · Cutting — to assign · With tailors (waiting / stitching) · Late
    (+ due today / this week) — each opens the matching workplace tab / filter.
  - [x] "Your work today" tiles: Cut today (`TAILOR_MASTER_CUTTING`) · Assigned today (`ASSIGNED_TO_TAILOR`, incl.
    re-assignments) · Due today · Open orders.
  - [x] "Your work — last 14 days": bar chart of actions per day (one series, validated blue, tooltip, Chart / Table
    toggle); subtitle "N cut · M tailor assignments". Split per type per day needs BE-4 (not done).
  - [x] Tailors: load bars (waiting + stitching), most loaded first, "2 waiting · 1 stitching · 1 late".
  - [x] "Due soonest — still to cut or assign": 8 New / Cutting items by delivery date → order page.
  - [x] Each block loads on its own (one failing call shows its own error); auto-refresh every 3 min.
  - [ ] Test with a TM login (numbers match the workplace tabs; chart after cutting / assigning).
- [ ] Step 5 — second round: Tailors, My work (+ BE-4 / BE-5 when needed).

Legend: **[FE]** frontend · **[BE]** backend · **[?]** decision needed · ✅ ready today · ⚠️ works but weak · ❌ missing

---

## 1. What the tailor master does (workflow)

```
ORDER_ACCEPTED ──(TM: start cutting)──▶ CUTTING ──(TM: assign tailor)──▶ TAILOR_ASSIGNED ──(tailor)──▶ STITCHING
      ▲ new items from invoices              cutting work                 tailor's queue                  │
                                                                                                           ▼
                                                     (tailor assigns shop assistant → hemming → ironing → completed)
```

The tailor master (TM) owns the **first part** of every item:
1. sees new items as soon as the shop owner creates an invoice;
2. moves an item to **CUTTING** (the cloth is being cut);
3. when cutting is done, **assigns a tailor** → item goes to TAILOR_ASSIGNED;
4. keeps an eye on the tailors (who is stitching what, what is late) and can **re-assign** a tailor while the item is
   TAILOR_ASSIGNED / STITCHING.

The TM does **not** take payments, create invoices, assign shop assistants or deliver.

---

## 2. What exists today

### 2.1 Backend (ready)

| Endpoint | TM can | Notes |
|---|---|---|
| `GET /items/queue?status=&pageNumber=&pageSize=` | ✅ | Items of the shop in ORDER_ACCEPTED, CUTTING, TAILOR_ASSIGNED, STITCHING (all items of the shop, not only the ones this TM cut), **oldest invoice first**, page ≤ 100. Each item: invoice no., item, qty, image, status, tailor, shop assistant, measurements, customer name, invoice date, expected delivery, `availableActions`, `history`. |
| `GET /invoices/{id}/items/{itemId}` | ✅ | One item (same shape). |
| `PATCH …/items/{itemId}/status {status:"CUTTING"}` | ✅ | ORDER_ACCEPTED → CUTTING only. Logged as `TAILOR_MASTER_CUTTING` under the TM. |
| `PUT …/items/{itemId}/tailor {tailorId}` | ✅ | From CUTTING (first time) or TAILOR_ASSIGNED / STITCHING (re-assign; the new tailor restarts at TAILOR_ASSIGNED). Logged as `ASSIGNED_TO_TAILOR` under the TM (with the tailor's id). |
| `GET /items/assignees/tailors` | ✅ | `[{userId, name}]` of the shop's tailors — **no workload**. |
| `GET /reports/my-activity?from=&to=` | ✅ | The TM's own counts by activity type + per day (e.g. `TAILOR_MASTER_CUTTING`, `ASSIGNED_TO_TAILOR`). |
| `GET /auth/me` | ✅ | Name, shop. |

**Not allowed for TM** (by design today): `GET /invoices/**` (invoice detail), all reports except my-activity, unassign
tailor (shop owner only), assign shop assistant, mark complete, move any status except → CUTTING.

### 2.2 Frontend (old)

- Route `/tailorMaster` → `TailorMasterDashboard.jsx` (843 lines, old Tailwind/CSS look), `/tailorMaster/:invoiceId`
  → `OrderDetails.jsx`, `/tailorMaster/my-orders` → `MyOrdersTM.jsx` (682 lines), `AssignTailor.jsx`,
  `OrderStatusUpdater.jsx`, `TailorMasterTrackStatus.jsx`.
- All of them load the queue through `utils/workQueue.js`, which **downloads up to 20 pages × 100 items** and groups
  them into invoices in the browser (search, date filter, paging all in the browser).
- No real dashboard (no counts, no "my work" numbers), old theme, cards/table toggle, NavBar layout.

---

## 3. Brainstorm — the new TM app (same theme as SHOP_OWNER: `.so-app`, sidebar, KPI tiles, cards)

Sidebar: **Dashboard · Workplace · Tailors · My work** (+ profile / logout).

### 3.1 Dashboard (`/tailorMaster/dashboard`)

| Block | What it shows | Data | Status |
|---|---|---|---|
| KPI: **New orders** | items in ORDER_ACCEPTED (waiting to be cut) | queue count per status | ⚠️ needs one queue call per status (`total`) — BE-1 makes it one call |
| KPI: **Cutting now** | items in CUTTING | same | ⚠️ |
| KPI: **With tailors** | TAILOR_ASSIGNED + STITCHING | same | ⚠️ |
| KPI: **Late** | items in the TM stages whose invoice delivery date has passed | queue + date compare | ⚠️ browser-side over all pages — BE-1 |
| KPI: **Cut today / Assigned today** | the TM's own work today | `my-activity?from=today&to=today` | ✅ |
| **My work — last 14 / 30 days** chart | items cut + tailors assigned per day | `my-activity` daily | ⚠️ daily is a *total* per day, not split per type — BE-4 |
| **Due soon** list | next 5–10 items by delivery date still in cutting / not assigned | queue sorted by due date | ❌ queue only sorts by invoice date — BE-2 |
| **Tailor load** strip | each tailor: items assigned / stitching now | per-tailor counts | ❌ BE-3 |
| **Recent activity** | "You assigned Blouse (SARI-…-42) to Ravi · 10:15" | the TM's activity list | ❌ only counts today — BE-5 (optional) |

### 3.2 Workplace (`/tailorMaster/workplace`) — "what got ordered"

Board grouped **by invoice (order)**, each order a card with its items:

```
┌ SARI-18-11-2026-00042 · Priya R · ordered 18 Nov · due 22 Nov (in 4 days) ────────────────┐
│ ○ Blouse ×1        New             [Start cutting]                                         │
│ ✂ Saree fall ×1    Cutting          [Assign tailor ▾]                                      │
│ 👤 Kurti ×2        Ravi · Stitching [Re-assign]                                            │
│ [Start cutting all new (1)]  [Assign one tailor to all cut items]        [View order ›]  │
└────────────────────────────────────────────────────────────────────────────────────────────┘
```

- **Tabs:** New (has ORDER_ACCEPTED items) · Cutting · With tailors · All — with counts.
- **Search:** customer, invoice no., item name. **Sort:** due date (default, soonest first) / newest / oldest.
- **Filters:** due today / overdue / this week; tailor (for the "With tailors" tab).
- **Item row:** name, qty, measurement chips (open full list), reference photo thumbnail, status badge, tailor.
- **Actions (from `availableActions`):** Start cutting (ORDER_ACCEPTED), Assign tailor (CUTTING), Re-assign
  (TAILOR_ASSIGNED / STITCHING). Bulk on an order: "Start cutting all", "Assign tailor to all cut items" (loop of the
  single calls, or BE-6).
- **Assign dialog:** tailor list **with current load** ("Ravi — 3 assigned, 2 stitching") so the TM picks the least busy;
  remembers the last tailor used.
- **Order page** (`/tailorMaster/workplace/:invoiceId`): all items of that order with full measurements, photos, notes,
  stage history (who cut, who stitches), delivery date; print a **cutting slip** (measurements per item) — browser print.
- Auto-refresh every 2–5 min (new invoices show up); optional WebSocket later.

### 3.3 Tailors (`/tailorMaster/tailors`) — supervision

- One card per tailor: items assigned (not started), stitching, late items, finished this week.
- Click → that tailor's items (re-assign from here when someone is absent).
- Needs BE-3 (per-tailor counts / items in TAILOR_ASSIGNED+STITCHING by tailor — the queue already returns `tailor`
  so the list can be built from the queue; counts of *finished* work need BE-7).

### 3.4 My work (`/tailorMaster/my-work`)

- Period filter (today / week / month / custom — reuse `PeriodFilter`).
- Totals: items cut, tailors assigned, re-assignments; per-day chart; table of items the TM cut / assigned (BE-5).

---

## 4. Backend gaps

| # | Gap | Why | Proposal | Priority |
|---|---|---|---|---|
| **BE-1** | No queue **summary** | Dashboard + tab counts need counts per status and "late" without downloading every page | `GET /items/queue/summary` → `{ countsByStatus: {ORDER_ACCEPTED: n, …}, late: n, dueToday: n, invoices: n }` for the caller's queue | P1 |
| **BE-2** | Queue: no **search / sort / due filters**, item rows only | The board needs search (customer / invoice / item), sort by due date, due today / overdue filter; the frontend now downloads up to 2000 items | Add `search`, `sort=dueSoonest|newest|oldest`, `due=today|overdue|week` to `GET /items/queue`; **and** `GET /items/queue/orders` = the same queue **grouped by invoice** (page of orders, each with its items in TM's stages + counts) | P1 |
| **BE-3** | Assignee list has **no workload** | Pick the least busy tailor; Tailors page | `GET /items/assignees/tailors?withLoad=true` → `[{userId, name, assigned, stitching, late}]` | P1 |
| **BE-4** | `my-activity` daily is one total | Chart "cut vs assigned per day" | Add `dailyByType` (`[{date, counts:{TYPE:n}}]`) to `MyActivityResponse` (additive) | P2 |
| **BE-5** | No list of **my activity entries** | "Recent activity" + My work table | `GET /reports/my-activity/entries?from=&to=&page=` → `[{at, type, invoiceId, itemName, note, assigneeName}]` | P2 |
| **BE-6** | No **bulk** actions | "Start cutting all" / "Assign one tailor to all cut items" = N calls, partial failures | `POST /invoices/{id}/items/cutting` and `PUT /invoices/{id}/items/tailor {tailorId, itemIds?}` → `{done[], failed[{itemId, reason}]}` (same pattern as complete-all) — optional, frontend can loop first | P3 |
| **BE-7** | TM can't see **tailors' finished work** | Tailors page "finished this week", late per tailor | Allow TM a limited `GET /reports/activity/by-employee?role=TAILOR` (own shop, tailors only) or a TM-specific `GET /items/tailors/summary` | P3 |
| **BE-8** | TM can't open an **invoice** (403) | Order page wants invoice notes, all items (incl. ones past stitching), delivery date | Either (a) build the order page from the queue (items in TM stages only — **no backend change**), or (b) `GET /items/orders/{invoiceId}` read-only, no prices/payments | **[?] 8** |
| **BE-9** | Can't **undo cutting** / remove a tailor | Mistakes (wrong item moved to cutting, wrong tailor) | Allow TM `CUTTING → ORDER_ACCEPTED` (undo) and `DELETE …/tailor` while TAILOR_ASSIGNED (not after stitching started) | **[?] 9** |
| **BE-10** | Cutting has **no "done"** step | Today "cutting finished" = assigning a tailor; a cut piece waiting for a free tailor still shows "Cutting" | Optional status `CUT_READY` (big change: enum, reports, all roles) — **recommend: no**, show "Cutting · waiting for tailor" by time | **[?] 10** |
| **BE-11** | Several TMs in one shop | Queue shows all TM items; no "cut by me" filter | `cutBy` from history (`CUTTING` entry userId) in WorkItem + `mine=true` filter | P3 / **[?] 11** |
| **BE-12** | `expectedDeliveryDate` is a raw string in WorkItem; no `daysOverdue` | Late badges, sorting | Add parsed `dueDate` (yyyy-MM-dd) + `daysOverdue` to WorkItem (additive) | P1 (with BE-2) |
| **BE-13** | No push for **new orders** | TM learns about new invoices only on refresh | Reuse the existing WebSocket (`OrderStatusUpdates`) — publish "invoice created" to the shop topic | P3 |

Nothing in the existing endpoints has to change shape — every proposal above is **additive**.

---

## 5. Frontend checklist

**Shell**
- [ ] [FE] `Pages/TailorMaster/` app on the shared `.so-app` theme: reuse `components/ui.jsx`, `status.jsx`,
      `PeriodFilter`, `DateField`, `lib/format.js` (move the shared bits to a `Pages/shared/` folder or import from
      `ShopOwner/`) — **[?] 12**.
- [ ] [FE] `TailorMasterLayout` (sidebar: Dashboard, Workplace, Tailors, My work), routes `/tailorMaster/dashboard`,
      `/workplace`, `/workplace/:invoiceId`, `/tailors`, `/my-work`; `Login.jsx` `ROLE_HOME.TAILOR_MASTER =
      '/tailorMaster/dashboard'`.
- [ ] [FE] `api/production.js` already has queue / move / assign; add `getQueueSummary`, `getQueueOrders`,
      `getTailorsWithLoad`, `getMyActivity` (+ entries) as the backend lands.

**Dashboard** — [ ] KPIs (BE-1 or N calls) · [ ] my work today (my-activity ✅) · [ ] chart (BE-4, or total per day ✅)
· [ ] due-soon list (BE-2) · [ ] tailor load strip (BE-3).

**Workplace** — [ ] tabs + counts · [ ] order cards with items · [ ] search / sort / due filters (BE-2) · [ ] Start
cutting · [ ] Assign / Re-assign dialog with load (BE-3) · [ ] bulk per order (loop, later BE-6) · [ ] measurement +
photo viewer · [ ] order page + print cutting slip · [ ] auto-refresh · [ ] 409 → reload + toast (same as shop owner).

**Tailors** — [ ] tailor cards (from queue + BE-3) · [ ] tailor's items + re-assign · [ ] finished work (BE-7).

**My work** — [ ] period filter, totals, chart (my-activity ✅), entries table (BE-5).

**Cleanup** — [ ] after the new app works: list the old `TailorMasterDashboard/*` files + the `/tailorMaster` routes
for deletion (show the list first, delete on OK). Keep `utils/workQueue.js` until the TAILOR and SHOP_ASSISTANT pages
are rebuilt (they use it too).

---

## 6. Decisions

**Taken 2026-11-18 ("do"):** 1 yes · 2 backend P1 first · 8 order page from the queue only (no backend change) ·
9 no undo cutting / no remove tailor for TM (unchanged) · 10 keep CUTTING without a done step · 11 one TM per shop
(no "cut by me" now) · 12 move shared components · 13 current load only (no finished-work stats) · 14 bulk = loop in
the browser.

- **[?] 1.** Pages: Dashboard + Workplace (+ order page) first; Tailors and My work in a second step? *(suggested: yes)*
- **[?] 2.** Backend first (BE-1, BE-2 + BE-12, BE-3 = P1) and then the frontend — or frontend first on today's API
  (download-all queue, like the old page) and switch when the backend lands? *(suggested: P1 backend first, it is
  small and removes the 2000-item download)*
- **[?] 8.** Order page: from the queue only (no backend change, items past stitching not shown) or a read-only
  order endpoint for TM?
- **[?] 9.** Allow TM to undo cutting and remove a tailor before stitching starts?
- **[?] 10.** Keep CUTTING without a "done" step? *(suggested: keep)*
- **[?] 11.** Can a shop have more than one tailor master? If yes, add "cut by me" (BE-11).
- **[?] 12.** Share the theme components between roles (move to `Pages/shared/`) — needed again for TAILOR and
  SHOP_ASSISTANT later. *(suggested: yes, move once now)*
- **[?] 13.** Tailor view for TM: should the TM see a tailor's *finished* items / performance (BE-7), or only current
  load?
- **[?] 14.** Bulk actions: loop in the browser first, backend bulk endpoint later? *(suggested: loop first)*

## 7. Suggested order of work

1. Decisions above.
2. [BE] P1: BE-1 summary, BE-2 search/sort/due + orders grouping, BE-12 due fields, BE-3 tailor load; docs.
3. [FE] shared components move (if 12 = yes), layout + routes + login home.
4. [FE] Workplace (board, actions, assign dialog, order page).
5. [FE] Dashboard.
6. [BE] P2 (BE-4, BE-5) → [FE] My work; [BE] P3 / decisions 9, 11, 13 → [FE] Tailors.
7. Manual test with a TM + 2 tailors; then old-page cleanup (with OK).
