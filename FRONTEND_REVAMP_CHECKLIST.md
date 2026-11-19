# Frontend Revamp Checklist: a professional shop app that pays for itself

The old frontend now talks to the new backend (see `FRONTEND_CHANGE_CHECKLIST.md`). This checklist covers the **next step**:
turning it into a consistent, fast app that gives the shop owner value every day. It is based on a review of the
current code (`SarithaFashion_Frontend/src`) and of every backend endpoint.

**Priorities:** **P0** = fixes wrong numbers or lost data, do first · **P1** = high owner value · **P2** = polish / nice to have.
**Effort:** S (≤1 day) · M (2–4 days) · L (1–2 weeks). **[BE]** = needs backend work too.

---

## 0. What we found (why this revamp)

| Area | Finding | Impact |
|---|---|---|
| Payment methods | Hardcoded per screen with different spellings: `Cash`, `UPI`, `Debit/Credit Cards`, `BANK` (labelled "Debit/Credit Cards" on one screen, "Bank" on another), `Other`, `other` | Reports group by the exact text, so the same money is split across several rows; the dashboard's Cash/Bank cards are wrong |
| Expense types | Hardcoded; `Travel` and `Travel Expenses` both exist | Expense breakdowns are split |
| Item catalog | 11 item names hardcoded in two screens (`ReceiptGeneration`, `EditOrderModel`); price typed by hand every time | No standard prices, typos, no "what sells" report |
| Measurements | Templates (Blouse / Top / Bottom / Salwar Set / Default) hardcoded in **two different `MeasurementModal.jsx` files that have already drifted apart**; measurements are saved per invoice item only | Repeat customers are measured and typed in again; create and edit screens show different fields |
| Customers | No customer endpoint at all; the customer is typed per invoice (matched by phone in the backend) | No customer history, no "regulars", no dues per customer |
| GST / states | 5 % and the state list hardcoded in `ReceiptGeneration.jsx` | A rate change needs a code change |
| Unused backend features | Edit shop, edit / delete employee, assign unassigned staff, unassign tailor / shop assistant, edit expense, payment history, activity log, staff "my activity" | The owner can't do these things although the API exists |
| UX | 248 `Swal.fire` pop-ups; 64 CSS files with no shared design system; two `components` folders; large commented-out copies of whole components; date filters default to "today", which hides older work | Slow to use, inconsistent look, hard to maintain |
| Tech | One 2.8 MB JS bundle (build warns); `chart.js` + `react-chartjs-2`, `jspdf`, `html2canvas`, `mammoth` and a stray `run` package are installed but **not imported anywhere**; both `lottie-react` **and** `react-lottie` are used (2 files each) | Slow first load on shop phones / tablets |

---

## 1. P0: data correctness (do first)

- [ ] **One payment-method list for the whole app** (S, **[BE]** recommended). Values: `Cash`, `UPI`, `Card`, `Bank Transfer`, `Other`.
  - Frontend: one constant (`src/constants/paymentMethods.js`) used by invoice create, payments, expenses and every filter.
  - Backend: validate against the same list (enum) and **migrate existing data** (`BANK`/`Debit/Credit Cards` → `Card` or `Bank Transfer`; `other` → `Other`).
  - Then fix the dashboard: Cash card = `Cash`; Bank card = everything that isn't cash (it already is), with "Other" shown separately.
- [ ] **One expense-category list** (S, **[BE]** recommended): merge `Travel` / `Travel Expenses`; store categories as fixed values; migrate old data.
- [ ] **One measurement-template source** (M): delete the duplicate `MeasurementModal.jsx`; one shared component + one template file
  (until the backend serves templates, §3). Today create and edit show different fields for the same item.
- [ ] **Item names from one list** (S): one constant used by create, edit and the "special category" logic (now 3 hardcoded copies).

---

## 2. P1: features that give the owner value

### 2.1 Owner home dashboard (platform OWNER) — L
Endpoints: `GET /reports/shops/overview`, `/reports/financials/summary`, `/reports/financials/daily`, `/reports/deliveries?shopId=`.
- [ ] **Period selector** (today / this week / this month / custom) shared by all cards.
- [ ] **KPI row per period:** sales (invoiced), collected, expenses, net cash flow, outstanding dues, open orders, deliveries due today.
- [ ] **Shop comparison table** (one row per shop from `shops/overview`), sortable; click → shop page.
- [ ] **Trend chart:** collected vs expenses per day/month (`financials/daily`), one chart library only.
- [ ] **Payment-method split** (from `collections.byPaymentMethod`) — only meaningful after §1.
- [ ] **Alerts strip:** overdue orders, low petty cash, invoices deleted this week (`summary.deletedInvoiceCount`).

### 2.2 Shop owner "Today" home (SHOP_OWNER) — M
- [ ] Make **Today's Deliveries** (already built, `/shopOwner/deliveries`) the landing page instead of invoice creation.
- [ ] Add a **"Collect dues" list**: open invoices with balance, sorted by oldest (`GET /invoices?status=pending&sort=oldest`, once with
  `paymentStatus=Partial` and once with `paymentStatus=Dues` — the filter takes one value; or **[BE]** allow several),
  each with a **WhatsApp click-to-chat** button (`https://wa.me/91<phone>?text=…`, pre-filled reminder with amount).
- [ ] **Quick actions:** New invoice · Record payment · Add expense · Add petty cash.

### 2.3 Customers — L, **[BE]**
- [ ] **[BE] Customer endpoints:** search by name/phone, customer profile (invoices, total spent, dues), saved measurements per customer.
- [ ] **Invoice create:** typing a phone number shows "Returning customer: Priya K — 6 orders, ₹2,400 due", fills the name and offers the
  **last measurements for each item type** (big time saver; fewer mistakes).
- [ ] **Customer page:** history, dues, measurements (editable), WhatsApp button.

### 2.4 Item catalog and price list — M, **[BE]** (open client requirement)
- [ ] **[BE] Catalog:** item name, category (stitching / material / service), default price, measurement template, special-category flag, active flag.
- [ ] **Owner screen** to manage it; invoice create uses it (price pre-filled, still editable).
- [ ] **Report:** sales per item (quantity, value) for the period.

### 2.5 Payments screen (`InvoiceGenerator.jsx`) — M
Endpoints exist: `GET /invoices/{id}/account`, `GET /invoices/{id}/payment-receipts`, `GET /payment-receipts/{no}/pdf`.
- [ ] Show the **payment history** (date, method, amount, payment receipt + PDF button) and the **GST breakdown** from `/account`.
- [ ] Discount as an **explicit field** ("discount ₹ / %" with a preview of the new total) instead of the hidden "pay less and tick the box" flow.
- [ ] Prefill from Payment Tracking / Deliveries **and load automatically** (today the invoice number is filled but Search must be pressed).

### 2.6 Production board for staff — L
Endpoints: `GET /items/queue`, `PATCH …/status`, `PUT/DELETE …/tailor`, `PUT/DELETE …/shop-assistant`, `availableActions`.
- [ ] Replace the three invoice-shaped tables (tailor master / tailor / shop assistant) with **one item board** (Kanban columns by status),
  item cards with photo, measurements, customer, due date, and **buttons generated from `availableActions`** (no hardcoded per-role dropdowns,
  no "Update" that the backend then rejects).
- [ ] **Due-date colours** (overdue / due today / this week) and sort by due date (the queue's natural order is oldest invoice first).
- [ ] **Mobile / tablet first** (staff use phones at the cutting table): large buttons, one item per card.
- [ ] **Shop owner: re-assign / unassign** a tailor or shop assistant when someone is absent (`DELETE …/tailor`, `DELETE …/shop-assistant`;
  the workflow relies on this, but there's no UI).
- [ ] **Item history** (`history[]` in the work item) in the item detail: who cut / stitched / ironed and when.

### 2.7 Staff and shop management — M
Endpoints exist but have no UI: `PATCH /shops/{shopId}/employees/{userId}`, `DELETE /shops/{shopId}/employees/{userId}`,
`GET /employees/unassigned` + `PUT /employees/{userId}/assignment`, `PUT /shops/{shopId}`.
- [ ] **Edit employee** (name, phone, salary, address) and **delete account** (with a confirmation that explains the difference from
  "remove from shop").
- [ ] **Move staff between shops:** pick from unassigned staff → assign to a shop.
- [ ] **Edit shop details** (name, address, phone, GST number, type).
- [ ] Let the **shop owner create staff for their own shop** (today `POST /auth/users` is OWNER-only) — **[BE]** decision.

### 2.8 Staff performance and accountability — M
Endpoints: `GET /reports/my-activity`, `/reports/activities`, `/reports/activities/by-employee`, `/reports/activities/employee-analytics`.
- [ ] **"My work" page for every staff role** (`my-activity`): today / this month counts, daily chart. Motivating and transparent.
- [ ] **Activity log for owners** (`activities`, filter by employee / type / date): who created, edited, deleted, delivered.
- [ ] **Team table** (`by-employee`): one row per employee with counts; click → analytics.
- [ ] Analytics targets (today hardcoded 5 / 20 / 2000 in `TailorAnalyticsDashboard.jsx`) → **owner-configurable** **[BE]** or remove.

### 2.9 Expenses and petty cash — S/M
- [ ] **Edit expense** (`PATCH /expenses/{id}`: amount / notes) — no UI today.
- [ ] Expense **category breakdown** chart for the period; petty-cash **ledger** (top-ups and cash expenses with running balance).
- [ ] Warn **before** a cash expense exceeds the petty-cash balance (the backend refuses it; show the balance on the form).

### 2.10 Customer-facing (public) — M, partly **[BE]**
- [ ] **Branded download pages** (logo, shop name, contact, WhatsApp) for invoice / receipt / payment-receipt links.
- [ ] **[BE] Order tracking link:** `GET /public/orders/{token}` → status per item and expected delivery; send it with the invoice.
  Customers stop calling to ask "is it ready?".
- [ ] **Public landing page** at `/` (today it's the login shell): services, gallery, location (Google Maps), timings, WhatsApp, Instagram.
  SEO basics: title, description, Open Graph image, `sitemap.xml`.

---

## 3. Move frontend-only data to the backend (configuration)

| Data | Today | Target | |
|---|---|---|---|
| Payment methods | hardcoded per screen, inconsistent | backend enum + `GET /settings/payment-methods` | **[BE]** P0 |
| Expense categories | hardcoded, duplicates | backend list (owner-editable) | **[BE]** P0/P1 |
| Item catalog + prices | hardcoded names, typed prices | catalog API (§2.4) | **[BE]** P1 |
| Measurement templates | 2 diverged frontend copies | per catalog item, served by the backend | **[BE]** P1 |
| Customer measurements | per invoice item only | per customer + item type (§2.3) | **[BE]** P1 |
| GST rate, shop state | `0.05` and state list in the frontend (backend already computes the real GST) | show the backend's numbers; preview from `/settings` | **[BE]** P2 |
| States list | hardcoded | static list is fine; move to one constant | P2 |
| Analytics targets | hardcoded 5 / 20 / 2000 | owner setting | **[BE]** P2 |
| Roles shown in forms | hardcoded | fine (fixed workflow) | — |

---

## 4. UX and visual design standards (P1)

- [ ] **One design system:** pick one component library (e.g. MUI or Ant Design, or Tailwind + Headless UI) and one colour palette
  (today purple `#6E5AF0`, `#4f6bff`, `#007bff` and `#3a0ca3` all appear). Shared: Button, Input, Select, Table, Card, Modal, Badge, Toast.
- [ ] **One app layout per role:** sidebar (desktop) / bottom nav (mobile), page title, breadcrumb, the shop name in the header.
- [ ] **Toasts instead of modal pop-ups** for success messages (248 `Swal.fire` calls today); keep modals for confirmations only.
- [ ] **Tables:** shared component with server paging, sorting, column picker, sticky header, CSV/Excel export of the *filtered* list.
- [ ] **Forms:** one form library (React Hook Form + Zod), inline validation messages that match the backend rules
  (phone 10 digits, quantity 0.01–1000, password 8+ with letter and digit), and field errors from 400 responses shown next to the field.
- [ ] **Money and dates:** one `formatMoney` (₹, `en-IN`, 2 decimals) and one `formatDate` (IST, dd-MM-yyyy) everywhere.
- [ ] **Status badges** with fixed colours: payment `Dues` / `Partial` / `Paid`; item statuses; overdue.
- [ ] **Empty, loading and error states** on every list (skeletons, "nothing due today 🎉", retry button).
- [ ] **Sensible default filters:** "last 30 days" for history lists, "all open" for work lists (today several screens default to "today").
- [ ] **Mobile responsive** for every page; test at 360 px width.
- [ ] **Accessibility:** labels for inputs, keyboard navigation, contrast; optional **Tamil** labels (i18n) for staff screens.
- [ ] **Print views:** invoice / receipt print from the PDF; a printable job card per item for the tailor (photo + measurements).

---

## 5. Technical foundation (P1)

- [ ] **Remove dead code:** the commented-out copies of whole components (they made automated edits misfire twice), `LoginSignup/*`,
  `Dashboard/Apply`, `UploadResume.jsx`, unused `ShopOwnerDashboard.jsx`, duplicate `MeasurementModal.jsx`. Merge `Components`/`components` on disk.
- [ ] **API layer:** one module per backend area (`api/invoices.js`, `api/production.js`, …) built on the existing axios instance; no URLs in components.
- [ ] **Data fetching:** TanStack Query (React Query) for caching, background refresh, retries, and 409 handling ("changed by someone else → reload").
- [ ] **Routing:** lazy-load each role's area (`React.lazy`) to split the 2.8 MB bundle; one role guard based on `/auth/me`.
- [ ] **Dependencies:** remove the unused `chart.js`, `react-chartjs-2`, `jspdf`, `html2canvas`, `mammoth`, `run` (checked: no imports);
  keep one Lottie library (switch the 2 `react-lottie` files to `lottie-react`); check the one `docx` user (Word templates are now in the backend);
  keep `@stomp/stompjs` + `sockjs-client` (decision: keep WebSocket).
- [ ] **Live updates (optional):** use the WebSocket you kept for "item status changed" / "payment received" events — **[BE]** must publish them.
- [ ] **TypeScript** for new code (types from the Swagger spec via `openapi-typescript`) — catches field-name mismatches like the ones fixed during the migration.
- [ ] **Lint clean:** fix the existing ESLint errors (unused variables, `editData is not defined` in `TotalEmployees.jsx`, hook dependencies).
- [ ] **Tests:** Vitest + React Testing Library for forms and calculations; Playwright smoke test per role (sign in → main flow).
- [ ] **Environments:** `.env.production` must point to the new backend after deployment; show the app version in the footer.
- [ ] **Security:** token in `localStorage` is readable by any injected script — keep strict input escaping, consider an httpOnly
  refresh cookie later **[BE]**; set `server.error.include-stacktrace=never` **[BE]** (stack traces are returned today).
- [ ] **Monitoring:** front-end error reporting (e.g. Sentry) so failures at the shop are visible.

---

## 6. Backend additions this revamp needs ([BE] summary)

| # | Addition | For |
|---|---|---|
| B1 | Payment-method enum + data migration | §1, reports |
| B2 | Expense categories (fixed list or owner-editable) + migration | §1, §2.9 |
| B3 | Customer API: search, profile, history, dues, saved measurements | §2.3 |
| B4 | Item catalog API (names, prices, measurement templates, special flag) | §2.4, §3 |
| B5 | `/settings` (GST preview, analytics targets, shop info for public pages) | §3, §2.8 |
| B6 | Public order-tracking endpoint + link in the invoice message | §2.10 |
| B7 | Let SHOP_OWNER create staff for their shop (decision) | §2.7 |
| B8 | WebSocket events on status / payment changes (optional) | §5 |
| B9 | Hide stack traces in error responses | §5 |

---

## 7. Suggested order (roadmap)

| Phase | Content | Result for the owner |
|---|---|---|
| **1. Correct data** (1 week) | §1 P0 items + B1, B2 | Reports and Cash/Bank cards are right |
| **2. Foundation** (1–2 weeks) | §5 cleanup, API layer, React Query, design system shell, lazy loading | Faster app, consistent look, easier to change |
| **3. Daily value** (2 weeks) | §2.2 Today home + dues list, §2.5 payments, §2.7 staff mgmt, §2.9 expenses | Less time per task, dues collected faster |
| **4. Production board** (1–2 weeks) | §2.6 | Fewer missed deliveries, clear responsibility |
| **5. Customers + catalog** (2–3 weeks) | §2.3, §2.4, B3, B4 | Faster invoices, repeat-customer measurements, item sales report |
| **6. Owner insight** (1–2 weeks) | §2.1 dashboard, §2.8 staff performance | Decide with numbers across shops |
| **7. Customer-facing** (1–2 weeks) | §2.10, B6 | Professional image, fewer "is it ready?" calls |

Track progress here the same way as `FRONTEND_CHANGE_CHECKLIST.md`: `[x]` done and checked against the backend, `[ ] ⚠️` started but not finished.
