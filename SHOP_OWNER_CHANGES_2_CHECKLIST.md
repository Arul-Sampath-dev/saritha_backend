# SHOP_OWNER pages — change round 2: gaps and checklist

Requested 2026-11-18 after reviewing the new shop-owner pages. **Status:** items 1–4 done (2026-11-18). Backend must be restarted for item 4; nothing committed. This file lists what exists
today, what is missing (frontend **[FE]** / backend **[BE]**), the decisions needed, and the steps to finish each item.

Legend: `[ ]` to do · `[x]` done · **[?]** decision needed.

| # | Request | Backend work? | Size |
|---|---|---|---|
| 1 | Invoice date field (today pre-filled, only past dates) instead of the back-date checkbox | No | Small |
| 2 | Date inputs in **dd-mm-yyyy** instead of mm-dd-yyyy | No | Small–medium |
| 3 | Remove the invoice-level "Activity" section (keep each item's stage history) | No | Small |
| 4 | "Mark complete" on one item, and "Mark all complete" for the whole invoice | **Yes** (new endpoints) | Medium |

---

## 1. Invoice date instead of the back-date checkbox

**Today**
- Wizard step 3 (`ShopOwner/Invoices/wizard/WizardSteps.jsx`, `PaymentStep`) shows a checkbox *"Date this invoice in the
  past (back-date)"*; ticking it reveals an *Invoice date* field (`max = today`).
- The wizard sends `isBackDate` + `backDate` (`InvoiceWizard.jsx` → `submitCreate`).
- Backend (`InvoiceWriteServiceImpl.resolveInvoiceDate`): `isBackDate = false` → invoice date = **now** (date + time);
  `isBackDate = true` → `backDate` (strict `yyyy-MM-dd`, not in the future) at **00:00 IST**.

**Gaps**
- [FE] No always-visible invoice-date field; the checkbox is an extra step.
- [FE] If today's date is sent as a back-date, the invoice gets 00:00 instead of the real time — the field must send
  `isBackDate` **only when the chosen date is before today**.
- [FE] The draft (localStorage) would bring back yesterday's pre-filled date the next day.

**Checklist**
- [x] [FE] Replace the checkbox with an **Invoice date \*** field, pre-filled with today (IST), `max = today` (only today or
  earlier can be picked). Place it first in step 3, next to *Expected delivery*.
- [x] [FE] Submit: date < today → `isBackDate: true, backDate: <date>`; date = today → `isBackDate: false` (no `backDate`).
- [x] [FE] Hint under the field when a past date is chosen: *"Back-dated invoice — dated <date>"*; the review summary shows
  the invoice date.
- [x] [FE] Draft: store the invoice date only if the user changed it; otherwise reset to today when the draft is restored.
- [x] [FE] Expected delivery: keep `min = today` (or `min = invoice date` — see decision 1b).
- [ ] Test: create with today (time kept), with yesterday (shows yesterday on the list / PDF), try a future date (blocked).

**Decisions**
- **[?] 1a.** Limit how far back? (Backend allows any past date.) Suggestion: no limit, show the hint.
- **[?] 1b.** Expected-delivery minimum: today (current) or the invoice date? Suggestion: keep today.
- Note: the **invoice number** is generated with today's date even for a back-dated invoice (unchanged behaviour).

---

## 2. Date format dd-mm-yyyy in the input fields

**Today**
- Every date field is a native `<input type="date">`. The browser draws it in the **computer's language/region format**
  (en-US Windows/Chrome → mm/dd/yyyy). `lang="en"` on `index.html` does not change it, and Chrome ignores `lang` for this.
  The app cannot force dd-mm-yyyy on a native date input.
- Values sent to the backend are `yyyy-MM-dd` (correct; unchanged).
- Displayed (read-only) dates use `formatDate` → "17 Nov 2026".
- `react-datepicker` **8.2.1 is already installed** (in `package.json`) but not used anywhere.

**Date inputs on the shop-owner pages (9)**

| File | Field |
|---|---|
| `components/PeriodFilter.jsx` | Custom range: from, to (Dashboard, Employees, Expenses) |
| `Invoices/InvoicesPage.jsx` | Invoice date / deleted between: from, to |
| `Invoices/wizard/WizardSteps.jsx` | Expected delivery; invoice (back) date |
| `Invoices/InvoiceModals.jsx` | Edit details → expected delivery |
| `Expenses/ExpenseModals.jsx` | Expense date (two places in the same form) |

**Gaps**
- [FE] No date field component that shows dd-mm-yyyy.

**Checklist**
- [x] [FE] One shared `DateField` in `ShopOwner/components/` built on `react-datepicker`: shows **dd-mm-yyyy**, typing
  allowed (`dd-MM-yyyy`), calendar pop-up, `minDate` / `maxDate`, clear button where optional; value in / out stays
  `yyyy-MM-dd` so no API code changes. Styled like `.so-input` (import the datepicker CSS once, override inside `.so-app`).
- [x] [FE] Weeks start on Monday (matches "This week"); IST "today" from `lib/period.js`.
- [x] [FE] Replace all 9 inputs above with `DateField`; keep their min/max rules and error states.
- [ ] [FE] Keyboard + mobile check (calendar usable at 375 px; `aria-label`s kept).
- [x] [FE] Fix (2026-11-18, reported): in popups (New invoice, Edit details, expense form) the calendar opened *behind* the
  popup backdrop — `react-datepicker.css` loads after `shopOwner.css`, so its `.react-datepicker-popper { z-index: 1 }` won.
  Now `.react-datepicker-popper.so-calendar-popper { z-index: 1100 }` (above the popup's 1000) + a calendar icon in every
  date field (click it or the field to open).
- [ ] Test: type `05-12-2026`, pick from the calendar, out-of-range dates blocked, values reach the API as `2026-12-05`.

**Decisions**
- **[?] 2a.** Read-only dates (tables, detail pages) currently show "17 Nov 2026". Keep that, or also show `17-11-2026`?
  Suggestion: keep "17 Nov 2026" (no day/month confusion); only inputs change.
- **[?] 2b.** Separator: `dd-mm-yyyy` (requested) or `dd/mm/yyyy`? Suggestion: `dd-mm-yyyy` as asked.
- The tailor / tailor-master / shop-assistant / owner screens are not part of this change.

---

## 3. Remove the invoice-level "Activity" section

**Today** (`ShopOwner/Invoices/InvoiceDetail.jsx`)
- Left column: **Items** card (each item has a collapsible *Stage history* — kept) and an **Activity** card fed by
  `GET /reports/activities?invoiceId=` (to be removed).

**Checklist**
- [x] [FE] Remove the Activity card, its state (`activities`), the `getActivities` call in `load()` and the import.
- [x] [FE] Keep the per-item *Stage history* (start → end, duration, who) unchanged.
- [x] [FE] `nameOf` keeps working from the employee list + item workers (it does not depend on activities).
- [x] Backend: nothing to change. `GET /reports/activities?invoiceId=` (BE-9) stays available but unused by this page.
- [x] Update `SHOP_OWNER_PAGES_CHECKLIST.md` §2.3 (timeline = item stage history only).

---

## 4. "Mark complete" — one item, and all items of an invoice

**Need:** a tailor / shop assistant sometimes finishes the work without updating the app. The shop owner must be able to
mark an item **completed** (ready for delivery) directly, and mark **all** items of an invoice completed in one go.

**Today — backend**
- `PATCH /invoices/{id}/items/{itemId}/status` moves an item **one step only** (`ItemWorkflow.STATUS_RULES`):
  ORDER_ACCEPTED→CUTTING, TAILOR_ASSIGNED→STITCHING, SHOP_ASSISTANT_ASSIGNED→HEMMING→IRONING→ORDER_COMPLETED.
- CUTTING and STITCHING can only move on by **assigning** a tailor / shop assistant. So an item in CUTTING needs at least
  5 actions (assign tailor, stitching, assign assistant, hemming, ironing, completed) to reach ORDER_COMPLETED.
- There is **no** way to jump to ORDER_COMPLETED and **no** bulk action.
- Things that already react to ORDER_COMPLETED (no change needed): invoice `stage` → READY when all items are completed;
  `completedItemCount`; the delivery dashboard's `ready`; the receipt / "Deliver" rule (fully paid **and** all items
  ORDER_COMPLETED); the shop-owner queue drops completed items; tailors' / assistants' queues drop the item.
- Each item change is a compare-and-set on the current status (`ItemWorkflowRepository.updateItemIfUnchanged`) + an
  `assignmentHistory` entry + an activity log entry (`ActivityType.ORDER_COMPLETED`, note "(by shop owner)").
- Employee analytics: a shop assistant's "completed" = `ORDER_COMPLETED` activities **done by them**; a tailor's =
  `ASSIGNED_TO_ASSISTANT`. A completion recorded under the shop owner gives the worker **no credit**.

**Today — frontend**
- Item card (`ItemCard.jsx`) shows only the buttons from `availableActions` (next step, assign, remove worker).
- No invoice-level item actions.

**Gaps**
- [BE] No "complete now" operation for one item (from any not-completed status).
- [BE] No bulk "complete all items" operation for an invoice.
- [BE] `availableActions` does not tell the UI that "complete" is allowed.
- [FE] No buttons, confirmation or result handling.

### 4.1 Backend checklist
- [x] [BE] **`POST /v1/api/invoices/{invoiceId}/items/{itemUniqueId}/complete`** — SHOP_OWNER only (own shop). Body
  optional `{ "note": "…" }` (≤ 200). Allowed from **any** status except ORDER_COMPLETED; refused when the invoice is
  delivered. Compare-and-set on the current status (409 if it changed meanwhile). Sets `itemOrderStatus = ORDER_COMPLETED`,
  pushes history `{tailorId: <shop owner id>, action: "ORDER_COMPLETED", timestamp}` (+ `skippedFrom: <old status>` —
  see decision 4c), keeps `assignedTo` / `assignedToWhichShopAssistant` as they are. Returns the WorkItem.
- [x] [BE] **`POST /v1/api/invoices/{invoiceId}/items/complete`** — same rules for every not-completed item of the invoice.
  Returns `{ completed: [itemIds], alreadyCompleted: n, failed: [{itemId, reason}] }`. Implementation: one compare-and-set
  per item (handles the legacy `assignmentHistory = null` case the same way as today), so one item changed by a worker in
  the meantime does not block the rest.
- [x] [BE] Activity log: one entry per item, type per decision 4b, note
  *"Marked completed by shop owner (was STITCHING)"* + the optional note; item quantity / price as today.
- [x] [BE] `availableActions`: add `COMPLETE` for the shop owner when the item is not completed and the invoice is not
  delivered (`ProductionReadServiceImpl.availableActions`).
- [x] [BE] Security: both paths SHOP_OWNER only (`WebSecurityConfig` / service check), shop scoping via the actor's shop.
- [x] [BE] Tests: from every status (ORDER_ACCEPTED … IRONING); already completed (no-op / 400); delivered invoice (400);
  another shop (403/400); concurrent change (409); bulk with a mix of statuses; history and activity written; stage → READY;
  receipt allowed afterwards when fully paid.
  *Done: harness 43/43 — every status, history + activity, note trim/limit, COMPLETE offered to the shop owner only,
  tailor refused, delivered / not found / already completed, concurrent change (409), bulk with mixed statuses, legacy
  null history and missing status, one item changed meanwhile (reported, others completed), re-run. Not run against a
  live MongoDB.*
- [x] [BE] Docs: `MODULES_API_DOCUMENTATION.md`, `FRONTEND_BUILD_GUIDE.md`. *(Also: `InvoiceAssignmentEntry` gained optional
  `markedByShopOwner`, `skippedFrom`, `note` so `GET /invoices/{id}` returns them and an items edit keeps them; new
  `ActivityType.ITEM_MARKED_COMPLETED`; security rule `POST /invoices/*/items/complete` + `/items/*/complete` → SHOP_OWNER.)*

### 4.2 Frontend checklist
- [x] [FE] `api/production.js`: `completeItem(invoiceId, itemId, note?)`, `completeAllItems(invoiceId, note?)`.
- [x] [FE] **Item card** — a *Mark complete* button (check icon, secondary style) in the item's action row, shown when the
  item is not completed and the invoice is not delivered (from `availableActions` `COMPLETE`). Placed **after** the normal
  "Move to <next step>" button so the regular flow stays first.
- [x] [FE] **Invoice level** — *Mark all complete (n)* button in the **Items card header** (right side, next to the item
  count), shown when at least one item is not completed; `n` = items left. Hidden after delivery.
- [x] [FE] Confirmation (both): list the item(s) and their current step, say the remaining steps are skipped and it is
  recorded as done by the shop owner; optional note field. Wording example: *"Mark Blouse as completed? It is at
  STITCHING — hemming and ironing will be skipped. This can't be undone."*
- [x] [FE] After success: toast, reload the invoice (stage badge → Ready when all done; the **Deliver & generate receipt**
  button becomes active if fully paid). Bulk with failures: show which items were not completed and why.
- [x] [FE] Stage history: show the forced completion as *"Completed (marked by shop owner) — skipped hemming, ironing"*.
- [x] [FE] 409 → reload and ask to retry (existing `run()` wrapper).

**Proposed placement**

```
┌ Items ─────────────────────────────── 3 items · [✓ Mark all complete (2)] ┐
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ [img] Blouse            2 × ₹850 = ₹1,700            ● Stitching     │ │
│ │ Tailor: Ravi · Shop assistant: —                                     │ │
│ │ Bust 34 · Waist 30 · …                                               │ │
│ │ [Assign shop assistant]  [✓ Mark complete]       [Remove tailor]     │ │
│ │ ▸ Stage history (3)                                                  │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────────────┐ │
│ │ [img] Saree Fall        1 × ₹150                     ✓ Completed     │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────┘
```
On phones the header button wraps under the title; item buttons wrap in the action row.

### 4.3 Decisions
- **[?] 4a.** Allow *Mark complete* from **every** status, including ORDER_ACCEPTED / CUTTING (nobody assigned)?
  Suggestion: yes — e.g. Saree Fall / Pico done by the owner.
- **[?] 4b.** Activity type: reuse `ORDER_COMPLETED` (counts like any completion, credited to the shop owner) or a new
  type `ITEM_MARKED_COMPLETED`? Suggestion: **new type**, so worker analytics stay honest and the owner can see how often
  steps are skipped.
- **[?] 4c.** Should the assigned worker get credit (tailor's stitching / assistant's completion) when the owner marks it
  complete? Suggestion: **no automatic credit** (they did not record it); revisit if salaries depend on these numbers.
- **[?] 4d.** Undo: there is no backward move today. Add *"Reopen item"* (back to its previous status)? Suggestion: not
  now — strong confirmation instead.
- **[?] 4e.** Optional note on the confirmation (e.g. "Ravi finished it on Monday")? Suggestion: yes, optional.

---

## 5. Order of work and release

1. [x] Decisions 1a/1b, 2a/2b, 4a–4e. *(2026-11-18: "start work" → the suggestions in this file were used.)*
2. [x] Item 3 (remove Activity) — frontend only.
3. [x] Item 1 (invoice date field) — frontend only.
4. [x] Item 2 (`DateField` + replace 9 inputs) — frontend only. *(The expense form had the date field twice for layout reasons; it is one field now, so 8 inputs in the code.)*
5. [x] Item 4 backend (endpoints, `availableActions`, tests, docs) → **restart the backend**.
6. [x] Item 4 frontend (buttons, confirmations, reload).
7. [x] `npm run build` + lint on the touched files; `mvn test-compile` + harness for the new endpoints.
8. [ ] Tick this file and `SHOP_OWNER_PAGES_CHECKLIST.md`; nothing is committed until asked.
