# Saritha Fashions Backend — Project Status

**As of:** 2026-11-14 · **Branch:** `opt/shop-management` · **Last commit:** `5df218f` "deleted old api endpoints"

**In short:** the backend has been rebuilt as self-contained modules and the old code has been removed. What's left:
- ~~two regressions against the client's closure matrix~~ — **fixed 2026-11-14** (see 3.1);
- **live testing** with real logins;
- the **frontend switch** to the new API;
- the **client's open change requests**.

---

## Contents

- [1. Completed](#1-completed)
- [2. Verification status](#2-verification-status)
- [3. To do](#3-to-do)
- [4. Suggested order](#4-suggested-order)
- [5. Document index](#5-document-index)

---

## 1. Completed

### 1.1 New module architecture

All business code now lives in `src/main/java/com/saritha/fashions/modules/` (187 files), with 49 REST paths:

| Module | Base path(s) | What it does |
|---|---|---|
| `authentication` | `/v1/api/auth` | Sign-in, token refresh, sign-out on all devices, `/me`, create users (owner), password reset by emailed code |
| `shop` | `/v1/api/shops` | Shops (create with owner account, list, summaries, names, update), **shop owner details** (`/{shopId}/owner`), petty cash top-up and history |
| `employee` | `/v1/api/shops/{shopId}/employees`, `/v1/api/employees` | Staff per shop, unassigned staff, assign / move / remove, edit, delete (releases in-progress items) |
| `invoice` | `/v1/api/invoices` | Create (with images, GST, notifications), list / search / filters, detail, edit, delete (archive), PDF |
| `receipt` | `/v1/api/invoices/{id}/account|payments|discount|receipt`, `/v1/api/receipts` | Payments, one-time discount + credit note, receipt = delivery, receipt list / detail / PDF |
| `production` | `/v1/api/items/...`, `/v1/api/invoices/{id}/items/...` | Work queue per role, status steps, assign / unassign tailor and shop assistant |
| `expense` | `/v1/api/expenses`, `/v1/api/employee-expenses` | Shop and employee expenses (cash from petty cash), edit amount / notes, delete, list / search |
| `report` | `/v1/api/reports` | Shops overview, financial summary and daily series, activity log / per employee / per day, my activity, deleted invoices |
| `core` | `/v1/api/public/documents`, `/v1/api/pdf-service/warmup` | Shared services: WhatsApp, Brevo mail, document tokens + public downloads, PDF generation, invoice / receipt numbering, activity log, background notifications |

### 1.2 Legacy code removed

- **111 legacy files deleted:** all 24 old controllers, every old service, unused repositories, payloads and models. 48 shared platform files remain outside `modules/` (security, errors, config, utilities, `User` / `Role`, a few model types).
- **Ported before deletion:** shop-owner details update and the PDF warm-up.
- **Dropped on purpose:**
  - backup-history read endpoints (backups are still written);
  - manual financial records;
  - manual payment-status endpoints;
  - shop delete;
  - a demo endpoint.
- **Data:** MongoDB collections and document shapes are unchanged, so all existing data works with the new code.

### 1.3 Naming

- Module folders and classes no longer contain "management": `shop`, `invoice`, `receipt`, `production`, `expense`, `employee`, `report`.
- URLs follow a nested REST style, e.g. `/v1/api/invoices/{id}/payments`, `/v1/api/invoices/{id}/items/{itemId}/status`.

### 1.4 Behaviour fixed or improved (selection)

| Area | Fix |
|---|---|
| Security | Every route protected by role; a shop owner can only see their own shop. Old endpoints accepted any `shopId` from any logged-in user. |
| Security findings (`SECURITY_FINDINGS_OPEN.md`) | All 5 resolved by the new authentication module: required password, `SecureRandom` reset codes stored as hashes and consumed atomically, 5-try lock, same response whether or not an email exists, enforced password policy. |
| Concurrency | Payments, edits, assignments and status changes are atomic and return `409` instead of silently overwriting each other. |
| Money | Server calculates totals and GST. Over-payment is blocked, one discount per invoice, `Paid` includes GST. |
| Petty cash | A cash expense can never take petty cash below zero. |
| Production | Strict one-step workflow. **Only the responsible person moves a status**: tailor master cuts, the assigned tailor stitches, the assigned shop assistant hems, irons and completes. The shop owner assigns and unassigns (see `PRODUCTION_WORKFLOW_REVIEW.md`). |
| Reports | Computed live from real data. The old dashboard showed expenses as 0 and read manually entered "financials". |
| PDFs | Same layout (verified identical Word XML). A shop without a GST number no longer crashes PDF generation, and `null` no longer appears on bills. The converter call now has timeouts. |
| Customer downloads | New public endpoints return the PDF (old: base64 text) and a 400 for a bad link (old: 500). |
| Numbering | Invoice and receipt counters are race-safe and safe across the year change. Deleting the latest invoice frees its number (bill numbers stay in sequence, as the client requires). |

### 1.5 Documentation written

`MODULES_API_DOCUMENTATION.md`, `FRONTEND_MIGRATION_GUIDE.md`, `FRONTEND_BUILD_GUIDE.md`, `LEGACY_MODULE_DEPENDENCY_AUDIT.md` (removal record), `PRODUCTION_WORKFLOW_REVIEW.md` (with resolution), and Swagger descriptions on every endpoint.

---

## 2. Verification status

| Check | Status |
|---|---|
| Clean build (`mvnw clean compile`) | ✅ passes |
| App starts after legacy removal and rename | ✅ started, Swagger lists all 49 paths |
| Every protected route without a token → `401`; public routes reachable | ✅ checked live |
| PDF output identical to the legacy code (invoice CGST/SGST, invoice IGST, receipt) | ✅ Word XML compared |
| Activity documents identical to the legacy format | ✅ 39 checks |
| Old and new download-link tokens interchangeable | ✅ 16 checks |
| Production permission matrix | ✅ 37 checks |
| R2 number release (latest / middle / gaps / race / years) + R1 validation | ✅ 26 checks (in-memory fake; not yet on real data) |
| Delivery dashboard classification + date parsing | ✅ 19 checks (fake repository; not yet on real data) |
| Decimal quantities: parsing, validation, exact amounts / GST, PDF text | ✅ 24 checks |
| Payment receipts: flow, numbering, 409 rollback, old invoices, document content | ✅ 25 checks (fakes; PDF conversion and notifications not yet run live) |
| **Calls with real logins against real data** (one per role and module) | ❌ **not done** — no tokens available to the developer tooling |
| **Wrong role gets `403`** (security rules were rewritten for the new URLs) | ❌ **not done** |
| **Reports aggregations on real data** | ❌ only `financials/summary` was tried once (crashed; fixed, not re-tried) |
| Automated tests in the repository | ❌ only the default context-load test exists |

---

## 3. To do

### 3.1 ✅ Regressions against the client closure matrix — fixed 2026-11-14

The closure matrix (`Saritha_Proposal_vs_Client_Requirements_Closure_Matrix (1) (1).xlsx`) marks these as **Completed**; the new backend had changed them, and both are now restored:

| ID | Matrix row | Problem | Fix |
|---|---|---|---|
| **R1** | 24 — Customer name correction after bill ("Use Edit Order") | The new invoice edit (`PATCH /v1/api/invoices/{id}`) does not allow changing the customer. The old Edit Order updated name, phone, email, alternate phone and address, also in the customer record. | ✅ **Fixed:** `PATCH /v1/api/invoices/{id}` accepts `customer: { name, phone, email, alternatePhone, address }` and updates the customer record (all of the customer's bills show it). A customer-only correction is allowed even after the receipt. A phone of another customer is refused. |
| **R2** | 25 — Bill number remains in sequence | The old delete moved the counter back when the **latest** bill was deleted (no gap). The new code never reuses a deleted number, so gaps appear. | ✅ **Fixed:** deleting the latest invoice moves the counter back to the highest number still in use, so the next invoice re-uses the number (guarded against invoices created at the same moment). Deleting an older invoice leaves a gap, as before. |

### 3.2 🟠 Housekeeping

- [ ] **Commit** the uncommitted work: the production workflow change (`ItemWorkflow`, `ProductionController`), doc updates, `PRODUCTION_WORKFLOW_REVIEW.md`, and this file.
- [ ] Mark `SECURITY_FINDINGS_OPEN.md` findings 1–5 as resolved (see 1.4).
- [ ] Update `MISSING_MODULE_APIS.md`, which is outdated: almost everything it lists now exists.
- [ ] `API_DOCUMENTATION.md` is obsolete (already marked); delete it once nobody needs it.
- [ ] Fix garbled `â†’` characters in a few code comments (authentication, employee, production).

### 3.3 🟠 Live testing (needs real logins)

Follow the scenarios in `FRONTEND_BUILD_GUIDE.md` §I.2. At minimum:

- [ ] Sign-in per role; `/me`; refresh; sign-out on all devices; password reset.
- [ ] Shop owner on `/v1/api/shops/{own id}/employees` and `/petty-cash` → allowed; on `/v1/api/shops` → `403`.
- [ ] Invoice create (with images), list filters, edit, delete, PDF; the numbers continue from the existing ones.
- [ ] Production: tailor master cuts and assigns → tailor stitches and assigns a shop assistant → assistant hems, irons, completes. The shop owner sees only assign / unassign buttons.
- [ ] Payment, discount, receipt (delivery), receipt PDF, customer download link.
- [ ] Expenses: a cash expense above petty cash is refused; top up, then retry.
- [ ] Every report endpoint, with the OWNER (all shops and one shop) and with a SHOP_OWNER.
- [ ] Shop-owner details update (`PUT /v1/api/shops/{id}/owner`).
- [ ] **R1:** correct a customer's name on a delivered invoice; check the receipt PDF shows the new name.
- [ ] **R2:** delete the latest invoice, create a new one, and check it gets the same number.
- [ ] **Payment receipts:** record a part-payment → check the payment receipt in the response, the PDF, and that the customer got the WhatsApp / email link (`/download-payment-receipt/...` page must exist in the frontend).
- [ ] **Delivery dashboard:** `GET /v1/api/reports/deliveries` as shop owner and owner; check how many open orders land in `withoutDeliveryDateCount` (old free-text dates) and fix those dates.

### 3.4 🟠 Frontend

- [ ] Switch every screen to the new API (`FRONTEND_MIGRATION_GUIDE.md`). **The old routes no longer exist**, so the current frontend is broken until this is done.
- [ ] Watch the look-alike URLs (migration guide §1.11), e.g. `GET /v1/api/invoices/{id}` now takes the invoice **number**.
- [ ] Customer download pages: call `/v1/api/public/documents/...` and handle a PDF instead of base64.
- [ ] Production screens: show only the buttons in `availableActions`; the shop owner no longer changes statuses.

### 3.5 🟡 Client open requirements (closure matrix)

Each needs the exact rule confirmed by the client before it's built (change requests):

| Row | Requirement | Matrix status | Backend today | What to confirm |
|---|---|---|---|---|
| 14 | Today's delivery dashboard | Completely new | ✅ **Done 2026-11-14:** `GET /v1/api/reports/deliveries?date=` — due today (ready / in production), overdue, delivered today, balance to collect. Delivery dates are now validated and stored as `yyyy-MM-dd`; old text dates in common formats are read. | Confirm with the client in the demo. |
| 15 | Lining / fabric amount in tailor tracking | (blank) | ◐ assignments store quantity × price; reports show counts only | Which value is the "worth of work": item price, or a separate material cost? |
| 19 | Decimal quantity (lining material) | New requirement | ✅ **Done 2026-11-14:** every item accepts a whole or decimal quantity (> 0, ≤ 1000, max 2 decimals); price is per 1 unit; amounts and GST use exact decimal math; PDFs print `2` / `2.5`. Before, decimals were silently cut (2.5 → 2). | Confirm 2 decimals is enough (e.g. 2.25 m). Later: an item catalog could restrict decimals to material items. |
| 20 | ₹1 round-off difference | Change request | ❌ GST rounded per component to paise; no rupee round-off | Exact rule: round the final total to the nearest rupee? Show a "round-off" line? |
| 21 | Partial-payment receipt | New requirement | ✅ **Done 2026-11-14:** every recorded payment creates a numbered **payment receipt** (`{SHOP}-PR-{dd-MM-yyyy}-{00001}`) showing amount received, total paid and **balance due**; PDF, WhatsApp / email to the customer, list per invoice. The final receipt is still issued on delivery. | Confirm the layout and whether the advance at invoice creation also needs one (today it is on the invoice). |
| 22 | Monthly receipt report (month, cash type, bank details) | New requirement | ◐ financial summary gives collections per method for any period | Which columns? "Bank details" means capturing bank / account per payment. |
| 27 | Zero-value issue | Change request | ⚠ Likely cause found: the old code cut decimal quantities, so 0.5 m became quantity **0** and a ₹0 line. The new code keeps decimals and rejects quantity 0. Item **price** 0 is still allowed. | Confirm with the client that this was the issue (old invoices with `itemQuantity: 0` would show it); decide whether price 0 should be blocked. |
| 28 | Bills in "Saritha Mam's" name | Change request | ❌ the PDF uses shop details | The exact name / wording, and whether it's per shop or global. |
| 17 | Complete accounting system | Completely new | — | Out of the original scope; separate project if wanted. |

### 3.6 🟢 Technical debt / optional

- [ ] **Automated tests:** turn the verification harnesses (PDF parity, activity parity, tokens, workflow matrix) into JUnit tests under `src/test`.
- [ ] Move the shared platform code into a `common` package (import changes only).
- [ ] Remove the unused `WebSocketConfig` and the security rules for routes with no controller (`/v1/api/payments/handle-payment-callback`, `/v1/api/user/support-ticket/contact-us`, `/api/test/**`).
- [ ] Rotate and move the Brevo / Twilio credentials out of the committed properties files. *Deferred by decision; noted for production readiness.*
- [ ] Consider small additions:
  - a backup-history read endpoint for owner-detail changes;
  - letting shop owners create staff accounts (today only the platform owner can);
  - screens for the `ACCOUNTANT` role.

---

## 4. Suggested order

1. Commit the current work.
2. ~~Fix R1 and R2~~ — done; include them in the live tests below.
3. Live testing with real logins (3.3), and fix what it finds.
4. Frontend migration (3.4).
5. Agree the client change requests (3.5) and build them one by one.
6. Tests and clean-up (3.6).

---

## 5. Document index

| File | Purpose |
|---|---|
| `MODULES_API_DOCUMENTATION.md` | Full API reference (every endpoint, field rule, error) |
| `FRONTEND_MIGRATION_GUIDE.md` | Moving the existing frontend from the old to the new API |
| `FRONTEND_BUILD_GUIDE.md` | Building a frontend from scratch on the new API |
| `LEGACY_MODULE_DEPENDENCY_AUDIT.md` | Record of the legacy-code removal and what was kept |
| `PRODUCTION_WORKFLOW_REVIEW.md` | Production workflow review and its resolution |
| `INVOICE_CREATION_FLOW.md`, `TAILOR_MASTER_INVOICE_CONDITIONS.md` | Earlier flow descriptions |
| `SECURITY_FINDINGS_OPEN.md` | Legacy security findings (all resolved, see 1.4) |
| `MISSING_MODULE_APIS.md` | Gap list from before the rebuild (outdated) |
| `API_DOCUMENTATION.md` | Old API (obsolete) |
| Closure matrix `.xlsx` | Proposal vs client requirements (source of 3.1 and 3.5) |
| `PROJECT_STATUS.md` | This file |
