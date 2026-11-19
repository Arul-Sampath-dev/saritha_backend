# SHOP_OWNER pages: API map and frontend build checklist

Sprint scope: the four SHOP_OWNER pages — **Dashboard**, **Invoices** (create modal; Pending / Completed / Deleted tabs; invoice
detail page), **Employees**, **Expenses** (with a Deleted tab). Visual design follows `design.md` (gaps in §0).

**Frontend progress (2026-11-17):** Phase 1 (foundation: theme, shell, shared components, formatters, reports/reference API)
and Phase 2 (Dashboard) **built**; Phase 3 (Invoices list with tabs, invoice detail, deleted-invoice detail) **built** — code in
`SarithaFashion_Frontend/src/Pages/ShopOwner/` and `src/api/`. Phase 4 (create wizard + edit items) **built**. Phase 5
(Employees) and Phase 6 (Expenses) **built** — all four SHOP_OWNER pages are done. Classic screens removed (FRONTEND_CHANGE_CHECKLIST §7). Remaining: §6 release checks,
decision 7.

**Status (2026-11-17):** backend tasks BE-1 … BE-12 are **done** (BE-5 and BE-6 closed by decision, no change). Every UI element
below now has an API. The remaining work is the frontend (`[ ]` items) plus the `design.md` decisions in §0.
**The backend must be restarted** to serve the new endpoints.

Legend: ✅ API ready · ⚠️ ready with a caveat · **[FE]** frontend task · **[DS]** design decision · `[ ]` to do · `[x]` done · `[~]` partly done.
All paths are under `/v1/api`; a SHOP_OWNER is always limited to their own shop by the backend (shop id for URLs:
`localStorage.shopId` or `GET /auth/me`). Full request/response details: `MODULES_API_DOCUMENTATION.md`.

---

## Decisions taken

| # | Question | Decision | Backend |
|---|---|---|---|
| 1 | Shop owner changes item statuses? | **Yes, any step**, one step at a time, recorded as the shop owner | BE-4 done |
| 2 | "Full receipt" when fully paid? | **Keep**: every payment gets a payment receipt; the final receipt at delivery (fully paid **and** all items completed) | none |
| 3 | Pending tab content | All not-delivered invoices, with a stage badge (incl. READY) | BE-3 done |
| 4 | Shop owner adds staff accounts? | **No**, stays platform-OWNER only (shop owner can add existing unassigned staff) | none |
| 5 | Advance gets a payment receipt? | Not now (BE-13 dropped) | none |
| 6 | "Overall" chart | **Monthly, from the first record** | BE-8 done |
| 7 | Measurements with units | open — see §2.1 | — |
| 8 | `design.md` app components | open — see §0 | — |

## Backend tasks — done

| # | Task | Result | Verified |
|---|---|---|---|
| BE-1 | Expense soft delete + note, deleted list | `DELETE /expenses/{id}?reason=` archives to `deleted_expenses`; `GET /expenses/deleted` | service harness 25/25 (archive first, race + failure rollbacks) |
| BE-2 | Sort invoices by due date | `GET /invoices?sort=dueSoonest\|dueLatest` (old `dd-MM-yyyy` / `dd/MM/yyyy` dates understood, no date → last) | Reworked 2026-11-17 (reported not working): sorted in Java with `DeliveryDates.parse` (same parser as the delivery dashboard) instead of a Mongo `$dateFromString` pipeline; harness 6/6 (mixed formats, stored dates, no date last, paging) |
| BE-3 | Production stage | `stage` filter (`NEW`/`IN_PRODUCTION`/`READY`/`DELIVERED`), `itemStatus` filter; rows get `stage`, `itemStatusCounts`, `receiptNo` (also on the invoice detail) | filter ≡ row value on 400 random invoices, 0 mismatches |
| BE-4 | Shop owner may move any item status | `PATCH …/items/{id}/status` allowed for SHOP_OWNER; log note "(by shop owner)"; `availableActions` include it | permission matrix 28 checks |
| BE-7 | One payment-method list | `Cash, UPI, Card, Bank Transfer, Other`; old spellings accepted + mapped in reports/filters; `GET /reference/payment-methods`; `POST /admin/migrations/payment-methods?dryRun=true` (OWNER) | Java ≡ Mongo mapping on 33 spellings |
| BE-8 | Monthly series | `GET /reports/financials/monthly` (all time by default) | 21-check report harness |
| BE-9 | Invoice-level activity trace | `GET /reports/activities?invoiceId=` | same harness |
| BE-10 | Expense filter / totals / fuller edit | `expenseType` filter; `GET /expenses/summary`; PATCH edits amount, notes, category, date, payment method (petty cash adjusted), employee | service harness + driver-query capture |
| BE-11 | Expenses by category in summary | `financials/summary` → `expenses.byType` | same harness |
| BE-12 | Archived invoice detail | `GET /reports/deleted-invoices/{archiveId}` | same harness |

Notes: `receiptId` on an invoice is the receipt's **database id** — use the new `receiptNo` for `GET /receipts/{receiptNo}/pdf`.
Old payment-method data: `BANK` (old expense form, labelled "Bank" on two screens and "Debit/Credit Cards" on one) is read as
**Bank Transfer**. Run the migration with `dryRun=true` first, check the list, then `dryRun=false`.

---

## 0. `design.md` — decisions still needed before building

`design.md` describes the Cohere marketing site; it gives colours, type, spacing, radii and the flat/bordered style but no app
components.

- [x] **[DS] Fonts:** proprietary `CohereText` / `Unica77` / `CohereMono` → fallbacks (`Space Grotesk` display, `Inter` UI, a mono).
  *Done: Space Grotesk / Inter / JetBrains Mono from Google Fonts in `ShopOwner/shopOwner.css`.*
- [~] **[DS] App components to define:** app shell (sidebar), KPI card, data table (sortable, row actions, pagination), tabs, period
  filter (Today · This week · This month · Custom · Overall, top-right), centered 3-step modal, status badges (payment `Dues` /
  `Partial` / `Paid`; stages `NEW` / `IN_PRODUCTION` / `READY` / `DELIVERED`; overdue), timeline, toast, empty/loading states,
  confirm dialog, form field states (focus `#9b60aa`, error `#b30000`), chart series colours (ink `#17171c`, deep-green `#003c33`,
  action-blue `#1863dc`, coral `#ff7759`, slate `#75758a`; gridlines `#e5e7eb`).
  *Done (`ShopOwner/components/`, styles scoped under `.so-app`): app shell, KPI card + delta, tabs, segmented period filter,
  badges, modal, bar list, empty / loading (skeleton) / error states, table styles, chart tooltip + legend. Chart series
  changed to a validated set (dataviz validator, light + dark pass): Invoiced `#1863dc`, Collected `#1f9d6b`, Expenses
  `#e5603f` — `#ff7759` and `#75758a` failed contrast / chroma. Still to build: 3-step wizard, timeline, sortable / paged
  table component, confirm dialog (SweetAlert2 for now), form fields.*
- [x] **[DS] Status colours:** the palette has only error red; success / warning / info colours are needed.
  *Done: success `#1a7f4b`/`#edfce9`, warning `#9a5b00`/`#fff6e5`, info `#1863dc`/`#f1f5ff`, danger `#b30000`/`#fdeeee`; badges always carry an icon or text.*
- [x] **[DS] Compact app type scale** (14 px body, 12 px meta) for tables and forms.
- [ ] **[DS]** Fix the front matter (`--` → `---`).

---

## 1. Dashboard

| UI element | Endpoint |
|---|---|
| Period filter: today / week / month / custom | `from` + `to` (`yyyy-MM-dd`, IST, inclusive) on every report ✅ |
| Period filter: overall | KPIs: no `from`/`to` ✅ · chart: `GET /reports/financials/monthly` (no params) ✅ |
| KPIs: sales, collected, expenses, net, outstanding, petty cash, receipts, deleted | `GET /reports/financials/summary` ✅ |
| Change vs previous period | call the summary for the previous range ✅ |
| Collected / expenses by payment method | `collections.byPaymentMethod`, `expenses.byPaymentMethod` (canonical names) ✅ |
| Expenses by category | `expenses.byType` ✅ |
| Items per production stage | `GET /reports/activities/employee-analytics?role=SHOP_OWNER&from&to` → `itemStatusCounts` ✅ · or `GET /invoices?stage=…` counts ✅ |
| Deliveries today / overdue / ready | `GET /reports/deliveries` ✅ |
| Trend chart ≤ 366 days | `GET /reports/financials/daily` ✅ |
| Quick actions: create invoice, add expense (+ petty cash, record payment) | open the wizard / expense modal ✅ |

- [x] [FE] Dashboard page: period filter (shared component), KPI row, charts (daily for ranges, monthly for overall), quick actions,
  deliveries strip. Port logic from `Deliveries.jsx`, `PettyCashManagement.jsx`, `MonthlyProfitChart.jsx`, `FinancialOverviewChart.jsx`.
  *Done: `ShopOwner/Dashboard/Dashboard.jsx` + `TrendChart.jsx`. 8 KPIs with % change vs the same number of days before
  (Overall: no comparison); trend = daily for ranges (Today → last 7 days; custom > 366 days → monthly), monthly for Overall,
  with a Chart / Table toggle; collected by method, expenses by category, items by production step, today's deliveries
  (due / ready / overdue / delivered + 5 rows). Each section loads and fails on its own (Retry).*
- [~] [FE] Quick actions: "New invoice" opens the wizard; "Add expense / petty cash" opens the Expenses page; record payment still
  opens the classic Payment Tracking screen (payments are recorded on each invoice's page) — switch to the
  wizard / expense modal in Phases 4 and 6. *(Delivery rows now open the invoice page; the "Deleted invoices" KPI opens the
  Deleted tab.)*

---

## 2. Invoices

### 2.1 Create invoice — 3-step centered modal (`POST /invoices`, multipart `invoiceJson` + `images` named `item_{index}_…`)

| Step | Fields | Status |
|---|---|---|
| 1. Customer | `customer.name`*, `customer.phone`* (10 digits), `email`, `alternatePhone`, `address`; `customerState`* (GST) | ✅ (old form only had name/phone) |
| 2. Items | per item `itemName`*, `itemQuantity`* (0.01–1000, 2 dp), `itemPrice`* (per unit), `measurements` or `measurementList` [{name, value, unit}], one image | ✅ |
| 3. Payment & delivery | `expectedDeliveryDate`, `advancePaid`, `paymentMethod` (from `GET /reference/payment-methods`), `transactionId`, `discount` (pre-GST), `notes`, `isBackDate` + `backDate` | ✅ (old form had no discount) |
| Submit | returns the invoice → `GET /invoices/{invoiceId}/pdf`; WhatsApp/email sent by the server | ✅ |

- [x] [FE] Wizard with step indicator and per-step validation (backend rules), draft in `localStorage`, **one measurement tab per item**
  (filled/empty marker), review step with the GST preview.
  *Done: `ShopOwner/Invoices/wizard/InvoiceWizard.jsx` + `WizardSteps.jsx`, opened from "New invoice" on the Dashboard and the
  Invoices page. Steps: Customer (name, phone, email, alternate phone, address, state) → Items (one tab per item with
  "n/m measured" or "Needs attention", catalog + "Other" with a typed name, quantity, price per unit, design photo ≤ 10 MB,
  measurement grid from the template) → Payment & review (delivery date, discount, advance + method + reference, notes,
  back-date; summary with CGST/SGST or IGST preview — same maths as `InvoiceAmountCalculator`, checked on its ₹1,680 example).
  Draft kept in `localStorage` (photos excluded) with "Start over". Success panel: invoice no., WhatsApp / email status,
  Invoice PDF, Create another, Open invoice.*
- [x] [FE] One item list + one measurement-template file (replace the two diverged `MeasurementModal.jsx`). *(New pages use
  `constants/catalogItems.js` + `constants/measurementTemplates.js` via `ShopOwner/lib/invoiceForm.js`; the old modals stay
  for the classic screens until the cleanup.)*
- [x] [FE] Payment method dropdown from `GET /reference/payment-methods`.
- [ ] **[?] Decision 7:** store measurements as `measurementList` with units (in / cm)? *(Not decided — the wizard keeps plain
  values in `measurements` like before; adding a unit later only touches `invoiceForm.js` + the measurement grid.)*
- (Next sprint, not in scope: customer lookup by phone with last measurements; item catalog with prices.)

### 2.2 Tabs and table

| Requirement | Endpoint |
|---|---|
| Pending tab | `GET /invoices?status=pending` ✅ |
| Completed tab (delivered = fully paid) | `GET /invoices?status=delivered` ✅ — receipt PDF via `receiptNo` ✅ |
| Deleted tab | `GET /reports/deleted-invoices?search&from&to&pageNumber&pageSize` ✅ |
| Deleted invoice details | `GET /reports/deleted-invoices/{archiveId}` ✅ |
| Columns: invoice id, customer name + phone, price, status | row: `invoiceId`, `customerName`, `customerPhone`, `totalAmountWithGST`, `balanceDue`, `paymentStatus`, `stage`, `itemStatusCounts`, `expectedDeliveryDate` ✅ |
| Search name / phone / invoice no. | `search=` ✅ |
| Sort by invoice date / due date | `sort=newest\|oldest\|dueSoonest\|dueLatest` ✅ (⚠️ check due-date sort live once) |
| Filter by status | `stage=NEW\|IN_PRODUCTION\|READY\|DELIVERED` ✅ · `itemStatus=CUTTING…` ✅ · `paymentStatus=Dues\|Partial\|Paid` ✅ · `from`/`to` ✅ |
| Paging | `pageNumber`, `pageSize` (1–100) ✅ |

- [x] [FE] Invoices page: tabs, shared table, filters bar (search, stage, payment status, date range, sort), pagination, view action →
  detail page. Port from `ViewEditOrderTable.jsx`, `PaymentTracking.jsx`, `DeletedInvoice.jsx`.
  *Done: `ShopOwner/Invoices/InvoicesPage.jsx` at `/shopOwner/invoices` (`?tab=completed|deleted`). Pending: search (debounced),
  stage and payment as one-click pill groups, invoice-date range, sort (newest / oldest / due soonest / due latest);
  Completed: search, dates, sort, receipt PDF link; Deleted: search, deleted-date range, reason + who. 20 per page, tab counts, row click / Enter opens. Item-step filter removed on request; filter bar redesigned 2026-11-17 (search + sort on
  top, labelled filter groups below, result count, Clear filters).*
- [x] [FE] Stage badge from `stage`; optional mini progress from `itemStatusCounts`. *(Progress bar from
  `completedItemCount / itemCount`; "Nd late" badge when the due date has passed.)*

### 2.3 Invoice detail page (`/invoices/:invoiceId`)

| Requirement | Endpoint |
|---|---|
| All details | `GET /invoices/{id}` ✅ (+ `stage`, `itemStatusCounts`, `receiptNo`) |
| Edit (items = full list, delivery date, notes, discount, customer) | `PATCH /invoices/{id}` ✅ (items already in production can't be changed; after delivery only customer details) |
| Delete with reason | `DELETE /invoices/{id}?reason=` ✅ (not after receipt) |
| **Update each item's status** | `PATCH /invoices/{id}/items/{itemId}/status` `{status}` ✅ — shop owner allowed (BE-4); show the button from the item's next step |
| Assign / re-assign / unassign tailor, shop assistant | `PUT\|DELETE …/items/{itemId}/tailor`, `…/shop-assistant`; lists `GET /items/assignees/tailors\|shop-assistants` ✅ |
| Partial / full payment → payment receipt each time | `POST /invoices/{id}/payments` `{amount, paymentMethod, transactionId?}` ✅ → `GET /payment-receipts/{no}/pdf` |
| Discount | `POST /invoices/{id}/discount` `{amount (pre-GST), reason}` ✅ |
| Payment history | `GET /invoices/{id}/account` + `GET /invoices/{id}/payment-receipts` ✅ |
| Final receipt (deliver) | `POST /invoices/{id}/receipt` (fully paid + all items completed) → `GET /receipts/{receiptNo}/pdf` ✅ |
| **Trace / timeline per item** (stage started / ended, by whom) | item `assignmentHistory` [{`tailorId` = who acted, `action`, `timestamp`}] in `GET /invoices/{id}`; start = entry time, end = next entry; ORDER_ACCEPTED starts at `invoiceDate`; names via `GET /shops/{shopId}/employees` ✅ |
| Invoice-level events (created, deleted, delivered, status changes, by whom) | `GET /reports/activities?invoiceId={invoiceNo}` ✅ |

To find the next step for an item (for the "Move to …" button): ORDER_ACCEPTED → CUTTING; TAILOR_ASSIGNED → STITCHING;
SHOP_ASSISTANT_ASSIGNED → HEMMING; HEMMING → IRONING; IRONING → ORDER_COMPLETED. CUTTING and STITCHING continue by **assigning** a
tailor / shop assistant (PUT), not by a status change. (`GET /invoices/{id}/items/{itemId}` also returns `availableActions` per item.)

- [~] [FE] Detail page: header (customer, stage / payment badges, totals, due date), items with measurements, images, per-item
  status button + assign / re-assign / unassign, payment panel (record payment, discount, history, PDFs), deliver button, edit
  (wizard in edit mode), delete with reason, timeline (item history + activities).
  *Done: `ShopOwner/Invoices/InvoiceDetail.jsx` (+ `ItemCard`, `PaymentPanel`, `InvoiceModals`) at `/shopOwner/invoices/:invoiceId`.
  Item buttons come from `availableActions` (`GET …/items/{id}` per item): "Move to <next step>" (confirm), assign / re-assign
  (lists from `/items/assignees/*`), remove worker. Payment: record (amount ≤ balance, methods from `/reference/payment-methods`,
  offers the payment-receipt PDF), discount, history with a receipt PDF per payment, "Deliver & generate receipt" only when all
  items are completed and nothing is due (then opens the receipt). Per-item stage history (start → end, duration, who). *(Round 2, 2026-11-18: the invoice-level Activity card was
  removed; "Mark complete" per item and "Mark all complete" for the invoice were added — see
  `SHOP_OWNER_CHANGES_2_CHECKLIST.md`.)* Delete needs a reason (≤ 300). **Edit covers customer, expected delivery and notes only** — editing
  items needs the wizard in edit mode (Phase 4).*
  *Deleted invoice: `DeletedInvoiceDetail.jsx` at `/shopOwner/invoices/deleted/:archiveId` (reason, who / when, items with
  measurements, amounts, payments).*
- [x] [FE] Edit items (add / change / remove; images) — wizard in edit mode, Phase 4. *("Edit items" on the detail page, hidden
  after delivery: Items → Review → `PATCH` with the complete list (multipart `editJson` when photos change). Items in
  production use the backend's own rule — assigned, or past their start status — and are shown locked and sent back unchanged.)*
- [x] [FE] 409 handling (changed meanwhile → reload + retry) on edit, payment, discount, receipt, status, assignments.
  *(One `run()` wrapper: success toast + reload; 409 → warning toast + reload.)*
- [ ] [FE] Port from `EditOrderModel.jsx`, `InvoiceGenerator.jsx`, `OrderDetails.jsx`, `AssignTailor.jsx`, `AssignShopAssistant.jsx`.

---

## 3. Employees

| Requirement | Endpoint |
|---|---|
| List with details | `GET /shops/{shopId}/employees?search=` ✅ |
| Analytics KPIs per employee (period) | `GET /reports/activities/employee-analytics?employeeId&role&from&to` ✅ |
| Team overview (one call) | `GET /reports/activities/by-employee?from&to` ✅ |
| Activity per day | `GET /reports/activities/daily?employeeId&from&to` ✅ |
| Current workload | `GET /items/queue` (all open items) → count per `tailor` / `shopAssistant` ✅ |
| Salary / advances paid | `GET /expenses?type=employee&employeeUserId=` + `GET /expenses/summary?…` ✅ |
| Add staff | **existing unassigned staff only**: `GET /employees/unassigned` + `PUT /employees/{userId}/assignment` ✅ (new accounts: platform OWNER, decision 4) |
| Edit | `PATCH /shops/{shopId}/employees/{userId}` ✅ |
| Remove from shop / delete account | `DELETE …/employees/{userId}/assignment` · `DELETE …/employees/{userId}` (unassigns their open items) ✅ |

- [x] [FE] Employees page: table (role, contact, salary, workload, period KPIs), detail drawer (analytics, daily chart, salary
  history), edit, add existing staff, remove / delete with clear wording. Port from `TotalEmployees.jsx`, `Analytic.jsx`,
  `AdminTailorDashboard.jsx`, `TailorAnalyticsDashboard.jsx` (hardcoded targets 5/20/2000 → remove or configure later).
  *Done: `ShopOwner/Employees/` at `/shopOwner/employees`. Period filter; KPIs (team, items in production now, steps completed,
  monthly salaries); table (role, contact, salary, items with them now from the open-items queue, done in period, work value,
  last activity / sign-in). Detail (centered modal, not a drawer — same as the rest of the app): period figures per role,
  activity-per-day bar chart (Overall → last 90 days), items with them now (open the invoice), salary & advances from employee
  expenses. Edit details (email-change warning), Add staff (unassigned accounts), Remove from shop / Delete account with
  plain-language confirmations. Targets dropped.*

---

## 4. Expenses

| Requirement | Endpoint |
|---|---|
| Add shop expense | `POST /expenses` ✅ (Cash comes out of petty cash; refused if too low) |
| Add employee expense | `POST /employee-expenses` (+ `employeeUserId`) ✅ |
| List + filters | `GET /expenses?from&to&search&paymentMethod&type=shop\|employee&employeeUserId&expenseType&sort&pageNumber&pageSize` ✅ |
| Totals for the filtered list | `GET /expenses/summary?<same filters>` → count, total, shop vs employee, by category, by payment method ✅ |
| Edit | `PATCH /expenses/{id}` → amount, notes, category, date, payment method, employee ✅ (petty cash adjusted; low balance refused) |
| Delete with note | `DELETE /expenses/{id}?reason=` ✅ |
| Deleted tab | `GET /expenses/deleted?from&to&search&type&pageNumber&pageSize` → expense + `deletedAt`, `deletedByName`, `reason` ✅ (expenses deleted before this release are not in the archive) |
| Payment methods | `GET /reference/payment-methods` ✅ |
| Petty cash balance / top-up | `GET /reports/financials/summary` → `pettyCash.currentBalance`; `GET\|POST /shops/{shopId}/petty-cash` ✅ |

- [x] [FE] Expenses page: Active / Deleted tabs, filters (date, category, payment method, shop/employee, employee, search), totals
  bar from `/expenses/summary`, add modal (shop / employee toggle, category-specific line items), edit modal, delete with reason,
  petty-cash balance + top-up. Port from `ShopExpense.jsx`, `EmployeeExpense.jsx`, `RevenueTracking.jsx`, `PettyCashManagement.jsx`.
  *Done: `ShopOwner/Expenses/` at `/shopOwner/expenses`. Period filter (dates); KPIs (spent, shop, employees, petty cash
  balance); by-category and by-method bars; filter bar (search, For: All / Shop / Employees, employee, category — options from
  the categories actually used, so old spellings still filter — paid by, sort). Add / edit popup: shop or employee, category or
  "Something else…", date (not in the future), amount, paid by with the petty-cash balance and a "too low" warning for Cash,
  notes; Refreshment / Travel / Miscellaneous can list bill lines (amount = their sum; travel lines have from / to). Delete
  needs a reason (Cash goes back to petty cash). Deleted tab: who / when / reason. Petty cash popup: balance, add cash,
  last 10 top-ups. Dashboard "Add expense / petty cash" now opens this page.*
- [x] [FE] One category list in the frontend (today `Travel` and `Travel Expenses` both exist — pick one). *(`Travel` —
  `api/expenses.js` SHOP_CATEGORIES / EMPLOYEE_CATEGORIES. Old "Travel Expenses" rows still show and can be filtered.)*

---

## 5. Cross-cutting frontend

- [x] **[FE] App shell** for SHOP_OWNER: sidebar Dashboard · Invoices · Employees · Expenses (+ Deliveries); replaces the tab bar in
  `PaymentInvoice.jsx`; routes under `/shopOwner/...`.
  *Done: `ShopOwner/ShopOwnerLayout.jsx` (role guard, mobile drawer < 1024 px, log out). New branch declared before the
  legacy `/` branch in `App.jsx` (`/shopOwner` → `/shopOwner/dashboard`; dashboard, invoices, deliveries, employees, expenses);
  route matching checked with `matchRoutes`. Legacy screens stay reachable ("Old invoice screens" in the sidebar,
  "← New dashboard" tab in `PaymentInvoice.jsx`). Login sends SHOP_OWNER to `/shopOwner/dashboard`. Invoices / Employees /
  Expenses are all built; the classic screens and `ComingSoon.jsx` were deleted in the 2026-11-17 cleanup; Deliveries reuses the classic component.*
- [x] **[FE] Shared components** per §0; money (₹, en-IN, 2 dp) and date (IST) formatters. *(`ShopOwner/lib/format.js`,
  `lib/period.js`; more components come with each phase.)*
- [~] **[FE] API layer** per area (`api/invoices.js`, `api/expenses.js`, `api/reports.js`, `api/employees.js`, `api/production.js`,
  `api/reference.js`) on the existing axios instance. *Done: `api/reports.js`, `api/reference.js` (payment methods cached,
  local fallback). The rest come with their pages.*
- [ ] **[FE]** Replace every hardcoded payment-method list with `GET /reference/payment-methods`.
- [ ] **[FE]** Mobile / tablet layouts per `design.md` breakpoints.

## 6. Before / after release

- [ ] Restart the backend and smoke-test: `GET /reference/payment-methods`, `GET /invoices?sort=dueSoonest`,
  `GET /invoices?stage=READY`, `GET /reports/financials/monthly`, `GET /expenses/summary`, delete an expense with a reason → it
  appears in `GET /expenses/deleted`.
- [ ] Run `POST /admin/migrations/payment-methods?dryRun=true` (OWNER), review, then `dryRun=false`.
- [ ] Commit the backend work (everything since `5df218f` is uncommitted).
