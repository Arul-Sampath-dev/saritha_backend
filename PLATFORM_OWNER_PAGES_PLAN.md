# Platform owner (OWNER) portal: what the owner should see

Written 2026-11-19 as input for the platform owner rebuild. **No code is changed yet.**

## 0. Decisions (2026-11-19)

| # | Question | Answer | Effect on the plan |
|---|---|---|---|
| 1 | One business or many? | **One owner for all branches** | No subscriptions / on-boarding of other businesses. |
| 2 | Act inside a branch? | **View only**, but **keep the admin CRUDs** | No payments, deliveries, invoice edits, status moves or discounts; every branch page is read-only (lists, PDFs, exports). The owner keeps: add / edit branch, change its shop owner, top up petty cash, create / assign / move / remove users. |
| 3 | Alerts and limits | **Pending (you are thinking about it)** | The Overview "Needs attention" list waits; the rest of the Overview can be built. |
| 4 | GST report for filing? | **Yes** | **All financial reports needed for tax filing**: GSTR-1 / 3B workings, registers, cash book, P&L, exports. See **section 6**. |
| 5 | Footer / numbers | **Same footer text for all branches; invoice numbers differ per shop** | Footer stays in the Word templates (one place, no setting). Numbers are already per shop (prefix from the shop name + own counter), so B9 is dropped; Settings only shows the prefix. |


The OWNER owns every branch (Gowrivakkam HO, Medavakkam, Perumbakkam, Madipakkam, Navalur, …). Each branch is run by
a SHOP_OWNER with its own tailor master, tailors and shop assistants. The owner doesn't record invoices or stitch.
They want to know four things:

1. How is the business doing?
2. Which branch needs attention?
3. Where is the money?
4. Is anything going wrong?

---

## Progress

- [x] **Step 1 (2026-11-19): owner app shell + Overview** (frontend only).
  - `OwnerLayout` (sidebar: Overview · Classic dashboard → the old `/dashboard` until steps 2–3 replace it), route
    `/owner` → `/owner/overview`, login home for OWNER `/owner/overview`.
  - `Owner/Overview/OverviewPage.jsx`: branch picker (All branches / one) + period filter in the URL; KPI tiles with ▲ / ▼
    vs the previous period (Sales, Collected, Expenses, Net cash flow · Outstanding, Deliveries due today + overdue,
    Petty cash, Delivered); cash-flow trend (full width, grouped like the shop owner dashboard; payment mix removed
    2026-11-20); sortable branch table with an
    "All branches" total row (a row narrows the page to that branch).
  - `TrendChart` moved to `shared/components/` (shop owner dashboard uses it from there); `getShopsOverview` in `api/reports.js`;
    `AppShell` takes a `brand` ("All branches").
  - Not yet: "Needs attention" alerts (waiting for the list), This year period (the shared filter has Overall).
- [x] **Step 2 (2026-11-19): Branches** (frontend only).
  - `/owner/branches` (`Owner/Branches/BranchesPage.jsx`): a card per branch (name, shop owner, phone, address;
    today's sales, collected, open orders, outstanding; petty cash; staff), from `/shops/summaries` + today's
    `/reports/shops/overview`.
  - `/owner/branches/:shopId` (`BranchPage.jsx`), view only: header (owner, phone, address, GST, "Figures" → Overview
    for the branch) + tabs (`?tab=`): **Orders** (pending / delivered / all, search, PDF) · **Receipts** (search, PDF) ·
    **Deliveries** (overdue / due today / delivered today, balance to collect) · **Expenses & petty cash** (expenses +
    top-up history, balance) · **People** (staff by role, phone, email, joined, last login) · **Activity** (log).
    20 per page, shop-owner table style.
  - `api/owner.js` (branch-scoped reads + PDFs with the branch); sidebar: Overview · Branches · Classic dashboard;
    Overview gets a "Branch page" button when one branch is picked.
- [x] **Step 3 (2026-11-19): admin CRUDs** (frontend only; endpoints existed).
  - Branches: **Add branch** (branch + shop owner login: name, address, GST, owner name / phone / email / password / salary).
  - Branch page: **Edit branch** (name, address, phone, GST; only changed fields sent) · **Shop owner** (name, phone,
    new email / password (signs them out), salary, reason) · **Add petty cash**.
  - People tab: **New staff** (tailor master / tailor / shop assistant login in this branch) · **Add existing staff**
    (unassigned accounts) · per row **Edit**, **Move** (to another branch), **Remove** from branch, **Delete** account
    (confirm popups). The shop owner row is edited with "Shop owner".
  - `Owner/components/AdminModals.jsx` (`FormModal` driven by a field list, client checks for email / 10-digit phone /
    password / amounts, backend errors shown in the popup; `AddExistingModal`); admin calls in `api/owner.js`.
  - Note: `PUT /shops/{id}` loads and re-saves the whole shop document (ModelMapper, nulls skipped), so a petty cash
    top-up at the very same moment could be overwritten. Rare; can be made an atomic `$set` later.
- [x] **Step 4 (2026-11-19): Compare + Expenses** (frontend only).
  - `/owner/compare`: pick 2 or more branches, no limit (first visit: the 3 biggest by sales; `?shops=` in the URL) + period;
    side-by-side table (sales, invoices, average bill, collected, expenses, net, delivered, open orders, outstanding,
    petty cash, staff; best value bold); month-by-month line chart (last 12 months, Sales / Collected / Net) with a
    legend, tooltip and table view. Branch colours: first 5 categorical slots of the validated palette
    (`validate_palette.js`: all checks pass; 3 slots < 3:1 on white → legend + table view always present).
  - `/owner/expenses`: all branches or one + period; totals (vs previous period), cash share, shop vs employee
    expenses; by category, by payment method, by branch (cash, petty cash left; click = that branch); with one branch:
    every entry + the deleted ones (the expense list needs a branch for the owner).
  - `Owner/lib/owner.js`: period-in-URL helpers, `useSection`, `BRANCH_COLORS` (Overview uses them too).
- [x] **Step 5 (2026-11-19): Production health** (B2 + B3 done).
  - Backend: `GET /reports/production?shopId=&from=&to=` (OWNER: one / all branches; SHOP_OWNER: own) →
    per branch + total: open items per stage now (overdue, avg / longest days in stage) and turnaround of orders
    delivered in the period (avg days invoice → delivery, on-time %, avg hours per stage from the item history,
    READY = completed → delivered). `ProductionHealth` (calculator, `ProductionHealthTest` 3/3), repository
    `findOpenInvoicesWithHistory` / `findInvoicesWithHistory`; documented in MODULES_API_DOCUMENTATION §8.
  - `/owner/production`: branch + period; tiles (in production, overdue, delivered, order → delivery days, on-time %);
    stage table (items, overdue, avg / longest days); "Where the time goes" bars with the **slowest** stage marked;
    all-branches table (items per stage with late counts, delivered, days, on time, slowest stage; row = that branch).
- [x] **Step 6 (2026-11-19): People + Audit** (frontend + one backend fix).
  - `/owner/people`: everyone in every branch + unassigned accounts; branch, role pills (with counts), period, search;
    per role the key work of the period (shop owner: invoices / delivered · tailor master: cut / assigned · tailor:
    started / handed over · shop assistant: completed / hemming), all actions, last active, last login; ★ = most work
    in the role (no ties); a row opens the branch's People tab.
  - `/owner/audit` tabs: Activity log (type filter) · Deleted invoices (search; who / when / reason) · Deleted expenses
    (pick a branch) · Skipped steps (ITEM_MARKED_COMPLETED). Branch + period + tab in the URL; 20 per page.
  - Backend fix (round-3 leftover, part 2): `ReportRepository.findUserNames` queried `userId`, but `User.userId` is the
    `@Id` (stored as `_id`) → "deleted by" and activity-per-employee names were empty. Now queries `_id`
    (ObjectId or string).
  - Not yet: discount / credit-note list (B6, needs an endpoint) — goes with the tax reports.
- [x] **Step 7 (2026-11-19): Customers** (B7 done).
  - Backend: `GET /reports/customers?shopId=&from=&to=` (from the live invoices; new = first invoice in scope falls in
    the period / month): totals (all time, active / new / returning, repeat rate, average spend), months (new vs
    returning, ≤ 12), top 20 by amount in the period, customers with dues on undelivered orders (50 + count + total).
    `CustomerStats` (calculator, `CustomerStatsTest`), repository `findInvoicesForCustomers`; documented in §8.
    Note: reads every invoice in scope (small projection) — fine now; an aggregation can replace it if volumes grow.
  - `/owner/customers`: branch + period; 5 tiles; new vs returning per month (stacked bars, legend, tooltip, table view);
    top customers (# , phone, branches, invoices / lifetime, spent, last visit); customers with dues (orders, balance,
    earliest due with overdue badge).
- [x] **Step 8 (2026-11-19): old owner screens deleted** (OK'd): `Pages/Dashboard/*` (29 files) and
  `Pages/OwnerDashboardCharts/*` (4 files); their imports and the `/home` + NavBar `/dashboard…` routes removed from
  `App.jsx`; "Classic dashboard" sidebar link removed; `Home.jsx` sends OWNER to `/owner` (and SHOP_ASSISTANT to
  `/shopAssistant`). `Components/ProtectedRoute.jsx` and `RoleProtectedRoute.jsx` are no longer used anywhere
  (delete with OK).
- Remaining: alerts ("Needs attention", waiting for your list) and the financial & tax reports (section 6).

## 1. What exists today

### Screens (old style, NavBar)

| Route | What it shows |
|---|---|
| `/dashboard` (MenuBar → `Shop.jsx`) | Shop cards. Per shop (`AllShopData.jsx`): shop details, Total cash expense, Receipts, Invoices, Employees, Petty cash left, Bank expense, Pending orders, Completed orders. Each tile opens a popup list. |
| `/dashboard/invoices`, `receipts`, `employees`, `pendingOrders`, `completedOrders`, `pettyCash` `/:shopId/:shopName` | One list per tile (search, dates, PDF download) |
| MenuBar buttons | Add shop, Add employees, Add petty cash |
| `/home` (`FinancialDashboard`) | 3 charts: Income / Expenses / Petty cash / Net profit; a pie chart; monthly profit (last 12 months) |

### Backend the owner can already use (all take `shopId`, or all shops when it is left out)

| Area | Endpoints |
|---|---|
| Branches | `GET /reports/shops/overview` (per shop: employees, petty cash, invoices, invoiced, paid, delivered, collected, expenses, cash expenses, net cash flow, pending orders, outstanding, receipts) |
| Money | `GET /reports/financials/summary` (sales incl. taxable + GST, collections **by payment method**, expenses by method and category, petty cash, outstanding, receipts, deleted invoices) · `/financials/daily` · `/financials/monthly` |
| Orders | `GET /invoices?shopId=&status=pending\|delivered\|all&from&to&search` · `GET /receipts?…` · PDFs |
| Deliveries | `GET /reports/deliveries?shopId=&date=` (due, overdue, delivered, balance to collect) |
| Expenses | `GET /expenses?shopId=…` · `/expenses/summary` · `/expenses/deleted` |
| Petty cash | `POST /shops/{id}/petty-cash` · `GET /shops/{id}/petty-cash` (history) |
| Branch admin | `POST /shops` (shop + owner account) · `PUT /shops/{id}` · `PUT /shops/{id}/owner` · `GET /shops/summaries` |
| People | `GET /shops/{id}/employees` · `/employees/unassigned` · assign / unassign / update / remove · `POST /auth/users` |
| Activity / audit | `GET /reports/activities` · `/activities/by-employee` · `/activities/daily` · `/activities/employee-analytics` · `/reports/deleted-invoices(/{id})` |

**So most of the numbers already exist.** The old screens just show them as separate tiles and popups.

---

## 2. Suggested portal: same app shell and design as the shop owner (sidebar, KPI tiles, shop-owner tables)

Every page has the same **period filter** (Today · This week · This month · This year · Custom, instant pills) and a
**branch filter** (All branches / one branch). Numbers compare with the previous period (▲ / ▼ %).

### 2.1 Overview (home): "how is the business doing today?"

- **KPI tiles (all branches):**
  - Sales (invoiced, incl. GST)
  - Collected
  - Expenses
  - Net cash flow
  - Outstanding (balance due on open orders)
  - Orders in progress
  - Deliveries due today / overdue
- **Trend chart:** collections vs expenses per day / week / month (same period rules as My progress).
- **Branch league table:** one row per branch with sales, collected, expenses, net, pending orders, overdue deliveries
  and outstanding. Sortable; a row opens the branch.
- **Needs attention** (alert list, each opens the right page):
  - overdue deliveries per branch;
  - orders with no progress for N days;
  - low petty cash;
  - invoices deleted this period;
  - big discounts given;
  - expenses above a set amount.
- **Payment mix:** Cash · UPI · Card · Bank transfer (amount and %).

### 2.2 Branches: list + one branch

- **List:** branch cards with name, shop owner, phone, today's sales and collections, pending orders, overdue deliveries,
  petty cash.
- **Branch page (tabs):**
  - **Overview:** the KPI tiles and trend for this branch.
  - **Orders:** pending / delivered invoices (shop owner table style, read-only), PDF.
  - **Receipts:** full receipts and payment receipts.
  - **Deliveries:** today, overdue and delivered (the shop owner's Deliveries view, read-only).
  - **Production:** items per stage (to cut, with tailors, stitching, hemming / ironing, ready), overdue items.
  - **People:** employees with role and this period's work counts.
  - **Expenses & petty cash:** expenses by category, cash vs bank, petty cash balance + top-up history, **Add petty cash**.
  - **Activity:** activity log of the branch.
- **View only** (decision 2): no actions on orders. Admin actions kept (to confirm): edit branch details (address, phone, GSTIN), change the shop owner, top up petty cash.

### 2.3 Compare branches

- Pick 2 or more branches (no limit) and a period. Side by side: sales, collected, average bill, orders, delivery on-time %, expenses,
  net, outstanding.
- Month-by-month chart per branch (one line each, legend + direct labels).

### 2.4 Money

- **Collections:** per day / branch / payment method; daily cash book (cash in, cash expenses, petty cash).
- **Dues:** open orders with balance due, **aged** 0–7 / 8–30 / 30+ days, per branch; customer phone to follow up.
- **Tax & registers (decision 4):** the full set in **section 6** (GSTR-1 / GSTR-3B workings per GSTIN, sales /
  collections / expense registers, cash and bank books, credit notes, receivables, P&L), FY-aware, Excel + PDF.
- **Discounts & deleted invoices:** who gave which discount (credit notes), deleted invoices with reason / who.

### 2.5 Expenses

- All branches: by category (rent, salary, electricity, material, …), by branch, cash vs bank, trend.
- Deleted expenses (who / when).
- Petty cash: balance per branch, top-ups, cash spent from it.

### 2.6 Production health

- **Work in progress per stage per branch:**
  - to cut / waiting for a tailor / stitching / with shop assistant / ready to deliver;
  - overdue items at each stage.
- **Turnaround:** average days from order to delivery, and per stage (cutting, stitching, finishing), per branch.
- **Bottlenecks:** the stage where items wait longest.

### 2.7 People

- Everyone across branches: role, branch, phone, active.
  - Create a user, assign / move to a branch, unassign, remove.
  - "Unassigned users" list.
- **Performance** (period): per employee, their own counts.
  - Tailor master: cut, assigned.
  - Tailor: started, handed over.
  - Shop assistant: completed.
  - Shop owner: invoices, payments, deliveries.
  - Top performers per role.

### 2.8 Customers

- New vs repeat customers per month, top customers by spend, customers with dues, birthdays / reminders (later).

### 2.9 Audit

- Activity log across branches, filter by branch / person / type.
- Deleted invoices and expenses.
- Discounts.
- Items marked completed by a shop owner (skipped steps).

### 2.10 Settings

- Branch profile (view), invoice number prefix (view: numbers are already per shop), payment methods, GST rate / HSN code
  defaults. The invoice / receipt footer text is the same for all branches and stays in the Word templates (decision 5).
- **Not** the Brevo / Twilio keys (they stay as they are).

---

## 3. Backend gaps

| # | Gap | Needed for | Size |
|---|---|---|---|
| B1 | Period comparison (previous period figures) | ▲ / ▼ on tiles | none: frontend calls the same endpoint twice (already done on the shop owner dashboard) |
| B2 | **Production per branch for the owner**: stage counts + overdue per shop (`/items/queue/summary` only works for a user with a shop) | Overview, Branch → Production, Production health | small–medium |
| B3 | **Turnaround times**: order → delivery and per stage, per branch (from item history timestamps) | Production health, Compare | medium |
| B4 | **Dues aging**: open invoices grouped 0–7 / 8–30 / 30+ days, per branch | Money → Dues | small |
| B5 | **GST report (for filing)**: per month and branch GSTIN: taxable, CGST, SGST, IGST, invoice count; Excel. B2B / B2C + HSN summary to confirm | Money → GST | small–medium |
| B6 | **Discount report**: credit notes list (who, amount, reason, invoice) | Money → Discounts, Audit | small |
| B7 | **Customer analytics**: new vs repeat, top customers, customers with dues | Customers | medium |
| B8 | **Alerts**: "needs attention" items. **Waiting for your list and limits (decision 3)** | Overview | small (or none) |
| ~~B9~~ | ~~Settings: footer text / number prefix per shop~~: **dropped** (decision 5: one footer in the templates; numbers already per shop) | | |
| B10 | Branch active / inactive flag (hide a closed branch) | Branches | small |
| B11 | Employee performance for all roles in one call (by-employee exists; check role-specific counts) | People | small |

Everything else (overview, financials, collections by method, expenses, petty cash, deliveries, invoices / receipts,
deleted invoices, employees, activity) already exists.

---

## 4. Suggested order

1. **Overview + Branches** (list + branch page with Orders / Receipts / Deliveries / Expenses & petty cash / People):
   frontend only on today's endpoints. Replaces the old `/dashboard` tiles and popups.
2. **Money & tax** (section 6): first the decisions T1 (numbers) and 6.5, then registers + GST workings on today's
   data (T4, T5, T6, T7, B4, B6), then T2 (B2B) / T3 (purchases + ITC) if you need them. Plus **Expenses**.
3. **Production health** + **Compare**: B2, B3.
4. **People** performance, **Audit**, **Customers** (B7), **Settings** (view) + B10. Alerts (B8) once you send the list.
5. Delete the old owner screens (`Pages/Dashboard/*`, `Pages/OwnerDashboardCharts/*`), with your OK.

## 5. Still open

1. **Alerts:** which ones and at what limits (decision 3). Ideas: overdue deliveries (by N days), orders with no
   progress for N days, petty cash below ₹X, expenses above ₹X, discount above X %, invoices deleted, and the
   income-tax cash limits from section 6 (cash receipt ≥ ₹2 lakh, cash expense > ₹10,000).
2. **Tax questions** in section 6.5 (scheme, GSTIN, B2B, ITC, invoice numbers, SAC / rate, filing frequency).

## 6. Financial & tax reports (for filing)

Decision (2026-11-19): **all financial reports needed for tax filing** go into the owner portal (view + export).
Below is the full set an accountant / CA would ask for, for an Indian tailoring business with several branches.
**Have your CA confirm the GST treatment** (rate, SAC code, scheme) before we build the exports.

### 6.1 Things to know first (they change the reports)

| Point | Why it matters | What we do |
|---|---|---|
| **GSTIN is per state, not per branch** | One PAN gets one GSTIN per state. Branches in Tamil Nadu are usually *additional places of business* under the **same GSTIN**. GST returns are filed per GSTIN. | GST reports are grouped **by GSTIN** (from each shop's `gstNumber`), with a per-branch breakdown inside. If every shop has its own GSTIN, it works the same way. |
| **Financial year = April–March** | GST returns, the annual return (GSTR-9) and income tax all use Apr–Mar. | Report periods: Month · Quarter (Apr–Jun, Jul–Sep, Oct–Dec, Jan–Mar) · **Financial year** · Custom. |
| **Invoice numbers must never be reused** | GST: invoice numbers must be unique and consecutive in a financial year; a cancelled invoice must stay recorded as cancelled. **Today, deleting the latest invoice releases its number and the next invoice gets it again** (client requirement, `DocumentType.INVOICE`). That is a compliance risk. | **Decision needed (T1):** stop reusing numbers. Deleted invoices stay archived (they already are) and are reported as *cancelled* in the document summary. |
| **Discounts after billing are credit notes** | A discount given after the invoice reduces taxable value and GST. It must be reported as a credit note. | Credit notes already store the full GST split (`CreditNoteRecord`). Report them (GSTR-1 CDN / B2CS adjustment). |
| **Time of supply** | For a service, GST is due on the invoice date (invoice raised when the order is taken). Advances are on the invoice, so no separate "advance received" entry (GSTR-1 table 11) is needed. | Confirm with the CA. If orders are ever booked without an invoice, table 11 is needed. |
| **B2B customers** | A customer with a GSTIN (boutique, company) needs their GSTIN and legal name on the invoice and an invoice-wise GSTR-1 line. **Today invoices don't capture the customer's GSTIN.** | **Gap T2:** optional *customer GSTIN + legal name* on the invoice. Invoices without it are B2C. |
| **Input tax credit (ITC)** | GST paid on purchases (fabric, lining, machines, rent with GST) can be set off only with a purchase register (vendor GSTIN, bill no., taxable, GST). **Expenses today store only amount + type.** | **Gap T3:** optional GST fields on expenses (vendor, vendor GSTIN, bill no., taxable, CGST / SGST / IGST). Gives a purchase register and the ITC for GSTR-3B. Not needed if you never claim ITC. |
| **Composition scheme?** | Under composition there is no GST on bills ("Bill of supply"), quarterly CMP-08 and an annual GSTR-4 instead. | **Question:** regular or composition? This plan assumes **regular**. |
| **Cash limits (income tax)** | Sec 269ST: no cash receipt of ₹2,00,000 or more from one person for one transaction or one day. Sec 40A(3): a cash expense over ₹10,000 to one payee in a day is disallowed. | Alerts (they fit decision 3): cash receipt ≥ ₹2 lakh per customer per day; cash expense > ₹10,000 per payee per day. |
| **Keep records 6+ years** | GST books must be kept 72 months from the annual return due date. | Nothing is hard-deleted today (invoices / expenses are archived). Keep it that way. |

### 6.2 GST reports (per GSTIN, period = month / quarter / FY)

| Report | Contents | Data today |
|---|---|---|
| **GSTR-1 working** | **B2B** invoice-wise (customer GSTIN, invoice no / date / value, place of supply, rate, taxable, IGST / CGST / SGST). **B2C Large**: inter-state invoices above ₹1,00,000, invoice-wise. **B2C Small**: totals by place of supply (state) and rate. **Credit notes**: CDNR (registered) / B2CS adjustment. **HSN / SAC summary (table 12)**: SAC, description, quantity, taxable, IGST / CGST / SGST, split B2B / B2C. **Document summary (table 13)**: invoice and credit-note series from–to, total, **cancelled** (deleted). | B2C, rates, place of supply (`customerState`), SAC (`hsnCode`), credit notes, deleted invoices: **yes**. B2B: **no (T2)**. |
| **GSTR-3B summary** | 3.1(a) outward taxable supplies (taxable, IGST, CGST, SGST). 3.2 inter-state supplies to unregistered persons by state. 4 eligible ITC (from the purchase register). 5.1 tax payable vs ITC. | Outward: **yes**. ITC: **no (T3)**. |
| **GST rate-wise sales** | Per month: taxable and tax per rate (5 %, 12 %, 18 % …), net of credit notes. | Yes |
| **Annual (GSTR-9 working)** | FY totals of all the above, month-wise. | Yes, after T1 / T2 / T3 |
| **Exports** | Excel per report and table, columns in the GST offline-tool order, so the CA can upload or copy. Optional later: GSTR-1 JSON. | New (frontend + small backend) |

### 6.3 Books and registers (income tax, accountant)

| Report | Contents |
|---|---|
| **Sales register** | Every invoice: no., date, branch, customer, taxable, GST split, total, paid, balance, status (cancelled shown). |
| **Collections register (day book)** | Every payment: date / time, invoice, customer, amount, method, transaction id, payment receipt no. |
| **Cash book** | Per branch per day: opening cash, cash collected, cash expenses, petty cash top-ups, closing cash. |
| **Bank / UPI / card book** | Non-cash collections per day and method, to match with bank settlements. |
| **Expense register** | Every expense: date, branch, category, amount, method, notes (+ vendor / GST when T3). Deleted expenses shown. |
| **Petty cash book** | Top-ups and spends per branch, running balance. |
| **Credit note register** | No., date, invoice, reason, taxable reduction, GST reversed, who. |
| **Receivables (dues aging)** | Open invoices with balance due, 0–7 / 8–30 / 31–90 / 90+ days, per branch (B4). |
| **Profit & loss (cash basis)** | Per month / branch / FY: sales (excl. GST), discounts, expenses by category, net. Marked "management P&L" (no depreciation / stock); the CA prepares the statutory one. |
| **Salary / staff payments** | Salary and employee expenses per person and month (from employee expenses). |
| **TDS hints** (optional) | Rent, contractor and professional payments above the TDS limits (sec 194-I / 194C / 194J), so the accountant can check TDS. Needs the expense category + payee (T3). |

All registers: filters (period, branch, GSTIN), totals row, **Excel + PDF export**, view only.

### 6.4 New backend items for this section

| # | Item | Size |
|---|---|---|
| T1 | **Stop reusing deleted invoice numbers** (business decision; changes `DocumentType.INVOICE` behaviour) + report deleted ones as cancelled | small (decision first) |
| T2 | Optional **customer GSTIN + legal name** on the invoice (invoice form, PDF "Bill to" block, B2B report) | medium |
| T3 | Optional **purchase details on expenses** (vendor, vendor GSTIN, bill no., taxable, GST split) → purchase register + ITC | medium |
| T4 | **FY-aware periods** (Apr–Mar, quarters) in the report endpoints | small |
| T5 | **GST report endpoints**: GSTR-1 tables (B2B, B2CL, B2CS, CDN, HSN, document summary), GSTR-3B summary, rate-wise, per GSTIN | medium |
| T6 | **Registers**: sales, collections, cash book, bank book, credit notes, P&L (most data exists; new read endpoints + paging) | medium |
| T7 | **Excel exports** in GST offline-tool column order | small–medium |
| T8 | Cash-limit alerts (269ST receipts, 40A(3) expenses) | small |

### 6.5 Questions for you / your CA

1. Regular GST or **composition** scheme?
2. Do all branches share **one GSTIN** (Tamil Nadu), or does each have its own?
3. Do you ever bill **businesses with a GSTIN** (B2B)? If yes → T2.
4. Do you claim **input tax credit** on purchases? If yes → T3.
5. OK to **stop reusing deleted invoice numbers** (T1)? It is the compliant way.
6. SAC code and GST rate used for tailoring (the template sample shows 998821); one rate for everything, or per item?
7. Monthly GSTR-1 or quarterly (QRMP)? Which accounting software does the CA use (Tally? Excel?), so exports match it.

- 2026-11-20: Branches list redesigned after design.md: search + flat box cards, 3 per row (details, today's four figures,
  petty cash / staff, Open); no colour accents or summary band.
- 2026-11-20: Branch page — role pills and delivery badges match the shop owner screens; deliveries paged (20 per page);
  order, receipt and delivery rows open a read-only invoice page (`/owner/branches/:shopId/invoices/:invoiceId`, uses
  `GET /invoices/{invoiceId}?shopId=`; payments come from the invoice's payment history, no backend change).
- 2026-11-20: Compare has no branch limit (8 colours, then dashed lines); Select all / Clear.
- 2026-11-20: Owner Expenses: by-category and by-payment-method cards removed (by branch stays).
- 2026-11-20: Owner Expenses entries: search, for / employee, category, paid by, sort.
- 2026-11-20: Customers: customers-with-dues card removed; top customers shows the top 5.
- 2026-11-20: People: no "All" role across all branches (default Shop owner); "All" only within one branch.
- 2026-11-20: Branch report download (Excel / PDF, any period up to 366 days) on the branch page and the shop
  owner dashboard — see BRANCH_REPORT_PLAN.md / BRANCH_REPORT_CHECKLIST.md.
