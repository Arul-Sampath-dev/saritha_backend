# SHOP_OWNER pages — change round 3: Deliveries redesign, "deleted by", undo for deletions

Requested 2026-11-18. **Status:** Part 1 (Deliveries page, backend-ready ideas) built in the frontend; Parts 2 and 3 not started. This file has what exists today, the gaps (frontend **[FE]** /
backend **[BE]**), the decisions needed, and the checklist for each part.

Legend: `[ ]` to do · `[x]` done · **[?]** decision needed.

| # | Request | Backend work? | Size |
|---|---|---|---|
| 1 | Deliveries page in the new theme + what else it can show | Optional (a few small additions) | Medium |
| 2 | Show **who** deleted an invoice | Yes — a **bug** hides the name today | Small |
| 3 | **Undo** for every deletion (soft delete) | Yes | Large |

---

## 1. Deliveries page — redesign (brainstorm)

### 1.1 Today
- `/shopOwner/deliveries` renders the **old** component `Pages/PaymentInvoice/Deliveries/Deliveries.jsx` (own CSS, old
  look) inside the new shell.
- Data: `GET /reports/deliveries?date=yyyy-MM-dd` (one day, default today) →
  - `summary`: `dueCount`, `dueReadyCount`, `dueInProductionCount`, `overdueCount`, `deliveredCount`,
    `withoutDeliveryDateCount`, `balanceToCollect`
  - `due[]` / `overdue[]` (not delivered): `invoiceId`, `customerName`, `customerPhone`, `expectedDeliveryDate`,
    `daysOverdue`, `ready`, `itemCount`, `completedItemCount`, `items[{itemName, itemQuantity, status}]`,
    `totalAmountWithGST`, `balanceDue`, `paymentStatus`, `invoiceDate`
  - `delivered[]` (receipts made that day): `receiptNo`, `invoiceId`, `customerName`, `customerPhone`, `deliveredAt`,
    `totalAmountWithGST`
- Old page: date input (mm/dd/yyyy), Refresh, summary boxes, three lists, "open invoice", receipt download.

### 1.2 What the page is for
The shop owner opening the shop in the morning: **what must go out today, what is late, what is ready, what money is
still to collect** — and acting on it in one click (call / WhatsApp the customer, collect the balance, finish the items,
deliver).

### 1.3 Proposed layout (new theme)

```
Deliveries                                   [‹] 18-11-2026 [›]  [Today] [Tomorrow]   (DateField)
─────────────────────────────────────────────────────────────────────────────────────────────
[ Due today 6 ] [ Ready 4 ] [ In production 2 ] [ Overdue 3 ] [ Delivered 5 ] [ To collect ₹8,450 ]
 (KPI tiles; clicking one jumps / filters to its list)

[ Search name / phone / invoice ]   Show: (All) (Ready) (In production) (Unpaid)

┌ Overdue · 3 ───────────────────────────────────────────────────────────────── (red accent) ┐
│ Priya K · 98765 43210   SARI-…-0031   3d late   2/3 items ▓▓▓░   Balance ₹1,650  Partly paid │
│ Blouse ✓ · Saree Fall ✓ · Top (Stitching)                                                   │
│ [Call] [WhatsApp] [Open invoice] [Collect ₹1,650] [Mark all complete] [Deliver]              │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
┌ Due today · 6  —  Ready to hand over (4) first, then In production (2) ─────────────────────┐
┌ Delivered today · 5 ─────────── receipt no., time, amount, [Receipt PDF] [Open invoice] ────┐
┌ No delivery date · 1 ─────────── (link / list, see gap) ────────────────────────────────────┐
```
Phone: one column of cards, action buttons wrap; KPI tiles 2 per row.

### 1.4 Ideas (what we can show / do) — pick in decision 1a
| # | Idea | Data | Backend |
|---|---|---|---|
| D1 | KPI tiles: due, ready, in production, overdue, delivered, balance to collect | `summary` | ✅ ready |
| D2 | Date navigation: ‹ › day, Today, Tomorrow, date picker (dd-mm-yyyy) | `?date=` | ✅ ready |
| D3 | Item chips with each item's step (✓ for completed) + progress bar | `items[]`, counts | ✅ ready |
| D4 | Balance + payment badge; "Ready but unpaid" highlighted ("collect ₹X before handing over") | `balanceDue`, `paymentStatus`, `ready` | ✅ ready |
| D5 | **Call** (`tel:`) and **WhatsApp** (`wa.me/91…` with a ready-made message: "Hi Priya, your order SARI-… is ready for pickup. Balance ₹1,650.") | `customerPhone` | ✅ ready (link only, no Twilio) |
| D6 | **Collect payment** right from the row (same popup as the invoice page) | `POST /invoices/{id}/payments` | ✅ ready |
| D7 | **Mark all complete** from the row (round-2 endpoint) | `POST /invoices/{id}/items/complete` | ✅ ready |
| D8 | **Deliver & receipt** from the row when ready and fully paid, then open the receipt PDF | `POST /invoices/{id}/receipt` | ✅ ready |
| D9 | Delivered list: receipt PDF, time, open invoice | `delivered[]` | ✅ ready |
| D10 | Search (name / phone / invoice no.) and quick filters (Ready / In production / Unpaid) — in the browser | lists | ✅ ready |
| D11 | **Next 7 days** strip (count + amount per day) to plan the week | one call per day (7 calls) or a range endpoint | ⚠️ 7 calls today; **[BE] optional** `GET /reports/deliveries/upcoming?days=7` |
| D12 | **No delivery date** list (today only a count) | — | ❌ **[BE]** return the list (or an invoices filter `noDueDate=true`) |
| D13 | Item photos thumbnail in the row (helps find the garment) | not in the response | ❌ **[BE]** add `imageUrl` to `items[]` |
| D14 | Expected-delivery reschedule from the row (customer asks for another day) | `PATCH /invoices/{id}` `expectedDeliveryDate` | ✅ ready |
| D15 | Print / Excel of the day's list (`xlsx` already installed) | lists | ✅ ready |
| D16 | Auto-refresh every few minutes while the page is open | — | ✅ ready |

### 1.5 Checklist
- [x] **[?] 1a.** Choose the ideas. *(2026-11-18: "backend-ready ones, no backend change" → D1–D10, D11 (with one existing call per
  day), D14, D15, D16 built. D12 (list of orders without a date) and D13 (photos) need the backend — not done; the page shows the
  no-date **count** with a link to Invoices.)*
- [x] [FE] New `ShopOwner/Deliveries/DeliveriesPage.jsx` in the theme (PageHeader, KpiCard, Card, Badge, DateField,
  status badges, Empty / Error / Skeleton states); route `/shopOwner/deliveries` → new page; delete the old
  `Deliveries.jsx` + `Deliveries.css` afterwards (show the file list first). *(Route switched; old files now unused — waiting for OK
  to delete.)*
- [x] [FE] `api/reports.js` already has `getDeliveries`; reuse `recordPayment`, `completeAllItems`, `generateReceipt`,
  `openReceiptPdf` and the `PaymentModal`.
- [x] [FE] Row / card component shared by Overdue and Due (D3–D8, D14); Delivered row (D9).
- [x] [FE] WhatsApp message template (D5) — wording decision 1b. *(English; ready / late / in-progress wording; balance added when
  something is due; `lib/deliveries.js`.)*
- [x] [FE] Dashboard "Today's deliveries" card: keep; its "View all" opens the new page.
- [x] [FE] **Revision 1 (2026-11-18)** — views by date (frontend only):
  - [x] Removed the "Next 7 days" strip (D11) and its 7 extra calls — the page now makes 1 call.
  - [x] Top bar: `[Today | Tomorrow]  ‹ date ›  Refresh  Excel` (Today / Tomorrow left of the date picker); subtitle names the
    view (Today / Upcoming / Past + weekday and date).
  - [x] **Today** → Overdue + Due + Delivered, 6 tiles, no-date note, auto-refresh (as before).
  - [x] **Future date** → only "Expected on <date>" (ready first, then in production); 4 tiles (Expected, Already ready, Still in
    production, To collect on these). "To collect" is summed from the expected list in the browser, because the backend total
    also adds every open order before that date. Deliver allowed early (ready + paid) with an "early" confirm. Auto-refresh on.
  - [x] **Past date** → only Delivered; 2 tiles (Delivered, Total value delivered); search includes receipt no.; no quick
    filters; no auto-refresh. "Missed that day" not shown (they appear in today's Overdue).
  - [x] Excel exports only the lists the view shows (`exportRows(data, view)`); "Nothing to export" toast on an empty day.
  - [x] Helpers `viewOf`, `balanceOf`, `deliveredValue` in `lib/deliveries.js`; `.so-week*` CSS removed; KPI grid `k4` / `k2`.
- [x] **Revision 2 (2026-11-18)**
  - [x] **[BE] bug** — Delivered "Invoice" link opened `/shopOwner/invoices/<database id>`: a receipt stores the invoice's
    database `_id` in `invoiceId`, and `getDeliveries` passed it through. Fixed: `ReportRepository.findInvoiceNumbers` (one query
    per day) + `ReportReadServiceImpl.getDeliveries` maps it to the invoice number (falls back to the stored value). Link,
    "Invoice / receipt" column, search and Excel now show the invoice number.
  - [x] [FE] Order card redesigned (design B, "two-column ticket"): no colored left edge; left = status line (late in red text
    only), customer, phone, invoice no., item list (✓ done / ○ current step), progress, Call / WhatsApp / Open / Change date;
    right = grey money panel (Balance due or Paid in full, total, a one-line hint) with the main action — Collect (+ Mark all
    complete when not ready), Deliver & receipt (ready + paid) or Mark all complete (paid, not ready). Stacks on phones.
    CSS `.so-dt*` replaces `.so-dl-card*`.
- [x] **Delivered date on completed invoices (2026-11-18)**
  - [x] [BE] `deliveredAt` (the delivery receipt's `receiptDate`) added to `GET /invoices` rows and `GET /invoices/{id}`, next
    to `receiptNo` — same receipt query as before, one more field (`ReceiptInfo` record; `findReceiptNo` → `findReceipt`).
  - [x] [FE] Invoices → Completed tab: column "Delivered" = delivered date + time, with "Due <date> · On time / N days late /
    N days early" under it.
  - [x] [FE] Invoice page of a delivered invoice: banner under the header "Delivered on <date, time> · Receipt <no> · expected
    <date> · <timing>" with a Receipt PDF button; "Delivered on" row in the details list. Old receipts without a date show
    "Delivered" / "Not recorded".
  - [x] Follow-up: "On time / N days late / early" text removed (table shows delivered date + "Due <date>"; banner shows
    receipt + expected date); the Completed table's Delivered column shows only the delivered date + time (no due date). The duplicate "Final receipt" button in the Payment card is removed — the receipt PDF opens
    from the Delivered banner only.
  - [x] Banner shows only "Delivered on <date, time>" + receipt no. (expected date removed). Payment card: a divider
    (`.so-card-divider`, same line as the Total row) between the amounts / actions and "Payment history".
  - [ ] Test: Completed tab dates; open one → banner with one Receipt PDF button; Payment card has no receipt button.
- [x] **Revision 3 (2026-11-18)** — Deliveries
  - [x] [FE] KPI tiles all the same height (button stretches, card fills it; the number sits at the bottom so a two-line label
    doesn't push it down).
  - [x] [BE] `delivered[]` rows now also carry `invoiceDate`, `expectedDeliveryDate`, `itemCount`, `items[]` (from the invoice,
    read in the same query that already translated the invoice number — `ReportRepository.findInvoicesByDbId`).
  - [x] [FE] Delivered table = same layout as Invoices → Completed: Invoice (no. + invoice date) · Customer · Items · Amount ·
    Payment · Receipt (PDF link) · Delivered (date + time) · View. Clicking a row (or View / Enter) opens the invoice page.
    Search also matches item names; Excel delivered rows get items and expected date.
- [x] **Revision 4 (2026-11-18)** — Deliveries
  - [x] [FE] Today view: 3 tabs **Overdue · Due today · Delivered** (with counts that follow the search / quick filter) instead
    of three stacked sections; opens on "Due today". KPI tiles switch to their tab + filter and scroll to it. Quick filters are
    hidden on the Delivered tab. Future view = expected list only; past view = delivered only (no tabs).
  - [x] [FE] Delivered table one line per row (`.so-table-1line`): Time · Invoice · Customer · phone · Items (cut with "…",
    full list on hover) · Amount · Paid · Receipt · View. Invoice date and full date-time are in tooltips.
- [ ] [BE] (optional) D12 list of open invoices without a delivery date; D13 `imageUrl` in items. (D11 dropped with the strip.)
- [ ] Test: today / tomorrow / a later date / a past date; early deliver on a future date; collect → deliver flow from the row;
  overdue order; empty day; Excel per view; phone width.

**Decisions**
- **[?] 1a.** Which ideas (above).
- **[?] 1b.** WhatsApp text (English / Tamil / both) and whether to include the balance.

---

## 2. "Deleted by" on deleted invoices

### 2.1 Finding — it IS stored; a lookup bug hides the name
- Deleting an invoice stores `{ invoice, deletedBy: <user id>, deletedAt, reason }` in `deleted_invoices`
  (`InvoiceRecordRepositoryCustomImpl.archiveDeletedInvoice`). The old backend stored `deletedBy` too.
- The APIs already return `deletedBy` + `deletedByName` (`GET /reports/deleted-invoices`, `GET /reports/deleted-invoices/{id}`),
  and the Deleted tab / deleted-invoice page already show "by …".
- **Bug:** `ReportRepository.findUserNames` looks users up by a field named `userId`, but `User.userId` is the Mongo `@Id`
  → stored as **`_id`**. The query never matches, so `deletedByName` is always empty ("by —"). Every other module
  (employees, expenses, production, receipts) correctly uses `_id`.
- The same lookup also names employees in `GET /reports/activities/by-employee` (there it falls back to the name stored
  with the activity, so it is less visible).
- `DeleteInvoiceResponse` (the answer to `DELETE /invoices/{id}`) has no `deletedBy`.
- Expenses already store `deletedByName` at deletion time (good: the name survives if the account is later deleted);
  invoices store only the id.

### 2.2 Checklist
- [ ] [BE] Fix `ReportRepository.findUserNames`: query `_id` (ids converted like the other repositories).
- [ ] [BE] Store `deletedByName` in the invoice archive at deletion time (like expenses); the API uses the stored name
  first, then the lookup (covers old archives and deleted accounts).
- [ ] [BE] Add `deletedBy` / `deletedByName` to `DeleteInvoiceResponse`.
- [ ] [BE] Test with a fake `users` collection shape (`_id` = user id) — the earlier harness faked this method, which is
  why the bug was not caught.
- [ ] [FE] Nothing new needed (already displayed); check the Deleted tab and the deleted-invoice page show the name.

---

## 3. Undo for deletions (soft delete)

### 3.1 Audit — every delete in the backend today

| What | Endpoint | Today | Can be restored today? |
|---|---|---|---|
| Invoice | `DELETE /invoices/{id}?reason=` | Full copy → `deleted_invoices` (who, when, why), then **removed** from `invoice`; customer link removed; **item photos deleted from storage**; if it was the latest invoice **its number is released** and re-used by the next invoice | ❌ No restore API. Photos are gone. Number may already be re-used |
| Expense | `DELETE /expenses/{id}?reason=` | Full copy → `deleted_expenses` (who, name, when, why), removed; shop total and (Cash) petty cash reverted | ❌ No restore API (data is all there). Expenses deleted before BE-1 have no copy |
| Employee — remove from shop | `DELETE /shops/{s}/employees/{u}/assignment` | Account kept; unlinked from the shop; **their open items are unassigned** (back one step); signed out | ⚠️ Can be added back (*Add staff*), but item assignments are not restored |
| Employee — delete account | `DELETE /shops/{s}/employees/{u}` | **Hard delete** of the user document (no copy); open items unassigned; removed from all shops | ❌ Gone for good |
| Unassign tailor / assistant | `DELETE …/items/{id}/tailor` · `…/shop-assistant` | Workflow step back (kept in item history) | ✅ by assigning again — not a deletion |
| Items removed in "Edit items" | `PATCH /invoices/{id}` | Item removed from the list, its photo deleted, **no copy** | ❌ (see decision 3e) |
| Item photo removed / replaced | `PATCH /invoices/{id}` | Old photo deleted from storage | ❌ |
| Mark complete (round 2) | `POST …/complete` | Not a deletion; no undo (round-2 decision 4d) | — |

So: invoice and expense deletions are already "archive + remove" (half-way to soft delete) — they need a **restore**
operation and a few changes; the employee account delete is a hard delete and must change.

### 3.2 Approach
Keep the existing **archive collections** (`deleted_invoices`, `deleted_expenses`, new `deleted_users`) instead of adding
a `deleted: true` flag to every live document — every list, report and total already ignores deleted data because it is
not in the live collection, so nothing else has to change. **Restore** = put the archived document back (same `_id`),
re-apply its side effects, and mark the archive entry `restoredAt` / `restoredBy` (kept for audit, hidden from the Deleted
tabs).

**Undo in the UI** (both):
1. Right after deleting: toast *"Invoice deleted · Undo"* (about 10 seconds) → restore.
2. Later: **Restore** button on each row of the Deleted tabs (invoices, expenses) and on the deleted-invoice page, with a
   confirmation.

### 3.3 Invoice restore
**Gaps / risks**
- [BE] No `POST /invoices/deleted/{archiveId}/restore`.
- [BE] **Photos** are deleted at deletion time → a restored invoice would have broken images.
- [BE] **Invoice number**: when the latest invoice is deleted its number is released and the next invoice gets it. The
  `invoiceId` index is unique, so restoring would fail (or must take a new number).
- [BE] Customer link (`allInvoiceIds`) must be added back.
- [BE] Reports count "deleted invoices" from the archive → restored ones must be excluded (`restoredAt` missing).
- [BE] Old archives (from the old backend) have the same `{invoice, deletedBy, deletedAt, reason}` shape, but their photos
  were already deleted; `deleted_invoices` also holds old *deleted receipts* (must be excluded, as the list already does).

**Checklist**
- [ ] [BE] Stop deleting item photos on invoice delete; delete them only if the archive is purged later (decision 3d).
- [ ] [BE] Invoice number on delete — per decision 3a.
- [ ] [BE] `POST /v1/api/invoices/deleted/{archiveId}/restore` (SHOP_OWNER, own shop): archive must exist, not restored,
  same shop; insert the stored invoice with its original `_id` (duplicate-key → clear 409/400 message); if the number is
  taken → per decision 3a; re-link the customer; mark the archive `restoredAt`, `restoredBy`, `restoredByName` with a
  compare-and-set (two clicks cannot restore twice); activity `INVOICE_RESTORED` (new type). Returns the invoice.
- [ ] [BE] `GET /reports/deleted-invoices` + `deletedInvoiceCount` in summaries / analytics: exclude restored archives.
- [ ] [BE] Tests: restore normal; restore twice; number re-used; other shop; old-shape archive; deleted-receipt rows ignored;
  counts after restore.
- [ ] [FE] Undo toast after delete (invoice page → goes back to the invoice on Undo); **Restore** on the Deleted tab rows and
  on the deleted-invoice page; after restore open the invoice.

### 3.4 Expense restore
**Gaps / risks**
- [BE] No restore endpoint. All data is in `deleted_expenses` (`expense`, `expenseId`, `shopId`, `deletedBy`,
  `deletedByName`, `deletedAt`, `reason`).
- [BE] Money: restoring must add the amount back to the shop's total expense and, for Cash, take it out of petty cash again
  — petty cash may now be too low (decision 3c).

**Checklist**
- [ ] [BE] `POST /v1/api/expenses/deleted/{archiveId}/restore` (SHOP_OWNER): insert the expense with its original id,
  re-apply shop totals / petty cash (same rules and rollback as create), mark the archive `restoredAt/By`; refuse twice.
- [ ] [BE] `GET /expenses/deleted` excludes restored entries.
- [ ] [BE] Tests: Cash with enough / too little petty cash, non-cash, employee expense whose employee no longer exists,
  restore twice, other shop.
- [ ] [FE] Undo toast after delete; **Restore** button on the Deleted tab rows.

### 3.5 Employees
**Gaps / risks**
- [BE] *Delete account* removes the user for good. To undo, it must become an archive delete like invoices: copy to
  `deleted_users` (who / when / why), then remove; restore puts the account back (same id, password hash, roles, salary)
  and re-links the shop. If a new account took the same email meanwhile → refuse with a clear message.
- [BE] Items that were unassigned when the person was removed / deleted are **not** re-assigned on undo (they may have
  been given to someone else already) — the history keeps who had them.
- [BE] *Remove from shop* is already reversible with *Add staff* (unassigned list); an Undo toast can call the same API.

**Checklist**
- [ ] [BE] `deleted_users` archive on delete (+ optional reason); `GET /employees/deleted` (shop's deleted staff);
  `POST /employees/deleted/{archiveId}/restore` (SHOP_OWNER for their shop's staff; platform OWNER any) → user back,
  assigned to the shop, tokens still revoked (they sign in again).
- [ ] [BE] Activity `USER_RESTORED` (new type).
- [ ] [BE] Tests: restore; email re-used meanwhile; restore twice; shop owner of another shop.
- [ ] [FE] Employees page: Undo toast after *Remove from shop* (re-add) and after *Delete account* (restore); a small
  "Deleted staff" list with Restore (in the Add staff popup or a tab).

### 3.6 Decisions
- **[?] 3a. Invoice numbers.** Today a deleted *latest* invoice gives its number to the next invoice (client requirement:
  no gaps). With undo this clashes. Options:
  1. **Keep releasing; restore gives a new number if the old one is taken** (bill numbers stay gap-free; the restored
     invoice's number changes — the PDF/WhatsApp the customer already got shows the old number). *Suggested.*
  2. Keep releasing; **block** restore when the number is taken (customer keeps the old number; some restores impossible).
  3. **Stop releasing** numbers (restore always keeps its number; gaps appear in the bill sequence).
- **[?] 3b. Time limit for restore?** Suggestion: no limit for the Deleted-tab Restore; the Undo toast is ~10 s.
- **[?] 3c. Restoring a Cash expense when petty cash is too low:** refuse (top up first) — *suggested* — or allow negative.
- **[?] 3d. Photos of deleted invoices:** keep forever (*suggested*, they are small) or delete after N days with a purge.
- **[?] 3e. Items removed by "Edit items":** keep them in the invoice as `removedItems` (restorable) or leave as today
  (suggestion: leave as today for now).
- **[?] 3f. Employee account delete:** switch to archive + restore as above (*suggested*), or keep hard delete and rely on
  *Remove from shop* for reversible cases.

---

## 4. Order of work
1. [~] Decisions 1a, 1b done (backend-ready ideas, English WhatsApp text); 3a–3f open.
2. [ ] Part 2 (deleted-by bug + stored name) — small backend fix, restart.
3. [ ] Part 3 backend: invoice restore (+ photos / numbers), expense restore, employee archive + restore; tests; docs.
4. [ ] Part 3 frontend: Undo toasts, Restore buttons, deleted-staff list.
5. [~] Part 1: new Deliveries page built (frontend only); backend additions D12–D13 not done; old component removal waits for OK.
6. [ ] Build, lint, `mvn test-compile`, harnesses; tick this file. Nothing committed until asked.
