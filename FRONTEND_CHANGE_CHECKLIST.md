# Frontend Change Checklist: old React app → new modular backend

Scope: `SarithaFashion_Frontend/src`. Every live API call in the old app is listed below with
its file and line and the endpoint that replaces it. Paths are relative to the axios
`baseURL` (`${VITE_API_BASE_URL}/v1/api`). Request and response bodies are documented in
`FRONTEND_MIGRATION_GUIDE.md` and `MODULES_API_DOCUMENTATION.md` (and Swagger at `/swagger-ui.html`).

Recommended order: **0 → 1 → 2 → 3 (shop-owner core) → 4 (production) → 5 (owner) → 6 (public) → 7 (cleanup)**.
After each section, sign in as that role and click through the screens.

**Legend:** `[x]` done and verified against the backend · `[ ] ⚠️` changed, but still wrong (fix noted) · `[ ]` not started.

---

## Review log

### Review 1: 2026-11-17, frontend commit `3066df8` "updated some endpoints" (branch `origin/endpoints/update`)

Note: the local frontend checkout is still on `main` (`b3c8146`); these changes exist only on `origin/endpoints/update`.
Run `git checkout endpoints/update` in `SarithaFashion_Frontend` before you continue.

The review compared the code with the backend controllers and payload classes. Nothing was run against a live backend.

| Result | Count | Where |
|---|---|---|
| Correct, ticked `[x]` | 7 rows | AddShop, AddPettyCash (POST + body), PettyCash history, TotalEmployees list + remove, ForgotPassword, TM profile (`/auth/me`) |
| Changed but still wrong, `[ ] ⚠️` | 14 rows | the 11 must-fix items below (some span two rows) + Signup public route |
| Not started | rest | sections 0, 3a, 3c–3h, most of 4, 5, 6, 7 |

**Must fix before testing (these break at runtime):**
1. ✅ *(fixed in section 2)* `/shops/name` → **`/shops/names`** (AddEmployees, AddPettyCash): 404 now.
2. ✅ *(fixed in section 2)* `TotalEmployees.jsx` search: `/employee/unassigned` is the wrong path (singular) **and** the wrong endpoint. It must be
   `GET /shops/${shopId}/employees` with `params: { search: term }` (the checklist row was wrong before; now corrected).
3. ✅ *(fixed in section 2)* `EmployeeExpense.jsx:356`: `ShopId` is never declared → `ReferenceError`. Use `localStorage.getItem('shopId')`.
4. ✅ *(fixed in step 2)* `ResetPassword.jsx` sends `FormData` (multipart) but the backend takes JSON (`@RequestBody`) → 415.
   Send `{ email, code, newPassword, confirmPassword }` as a plain object.
5. ✅ *(fixed in section 3b)* `InvoiceGenerator.jsx` payment: still `axiosInstance.put(url, null, { params })` → 405. Must be
   `axiosInstance.post(`/invoices/${id}/payments`, { amount, paymentMethod })`, plus a separate
   `post(`/invoices/${id}/discount`, { amount: calculatedDiscount, reason })` when a discount is applied.
6. ✅ *(fixed in section 3b)* `InvoiceGenerator.jsx:75`: `/account` has no `customer`, `items` or `expectedDeliveryDate`, but the page renders
   `invoice.customer.name` → crash. Use `GET /invoices/${id}` here (it has `balanceDue`, `paymentStatus`, `items`,
   `customer`), and use `/account` only for the payment list.
7. ✅ *(fixed in section 2)* `EditEmployeeModal.jsx`: sends `oldOwnerUserId`/`newOwnerName`/`newOwnerEmail`/`newOwnerPhone`/`newOwnerSalary`/
   `newOwnerPassword`, but the backend expects **`ownerUserId`, `name`, `email`, `phone`, `salary`, `password`, `reason`** →
   400 "no field provided". Rename the keys in `requestData`.
8. ✅ *(fixed in section 2)* `AddEmployees.jsx`: the payload sends `assignedShops` (and `role`), but the backend needs **`shopId`** → 400 "shop is
   required" for staff roles. Use `{ name, email, password, phone, salary, address, shopId: formData.assignedShops, roles: [formData.role] }`.
9. ✅ *(fixed in section 2)* `Shop.jsx`: `/shops/summaries` returns `shopId`, but the tabs use `shop.id` → every tab has `undefined` as its key/id.
   Use `shop.shopId`.
10. ✅ *(fixed in step 1)* `Login.jsx`: `res.data.id` → **`res.data.userId`**; `res.data.username` → **`res.data.name`**. Right now `userId` and
    `username` are saved as the string `"undefined"`, and the Profile menu shows "Undefined".
11. `TailorMasterDashboard.jsx:167`, `MyOrdersTM.jsx:63` → `/items/queue`: the URL is right, but the screens still treat each
    row as an *invoice with `items[]`*. The queue returns **one row per item** (`invoiceId`, `itemUniqueId`, `itemName`,
    `status`, `tailor`, `shopAssistant`, `customerName`, `expectedDeliveryDate`, `availableActions`). `sortOrder`, `startDate`
    and `endDate` are ignored; the filter is `status` (repeatable). These tables need reworking.

**Other things found in the commit:**
- ✅ *(fixed in step 1)* `src/components/NavBar.jsx` imports `./Profile/Profile.jsx`, but the file is in **`src/Components/Profile/`** (capital C).
  It works on Windows but **fails on a Linux build** (GitHub Actions / Firebase deploy). Move `Profile/` into
  `src/components/` (or fix the import), and ideally merge the two `components`/`Components` folders.
- ✅ *(fixed in step 1)* `utils/authUtils.jsx` `getUserName()` crashes if `username` is missing from localStorage (`null.split`). Add a
  fallback: `(localStorage.getItem('username') || '').split(' ')...`.
- ✅ *(fixed in step 2)* `ResetPassword.jsx`: the error state key was renamed to `newPassword`, but validation still writes `newErrors.password` /
  `errors.password`. Rename them consistently, or the password error message won't show.
- ✅ *(section 2)* `AddPettyCash.jsx`: the response field is `amountAdded` (not `amount`). Harmless, because the page reloads.
- ✅ *(section 2)* `PettyCashManagement.jsx`: `response.data.pettyCash` is now an object `{ added, currentBalance }`. Use
  `response.data.pettyCash.currentBalance`, or the card renders `[object Object]` / `NaN`.
- ✅ *(checked in step 2: there's no route)* `Signup.jsx` → `/auth/users` is right, but the `/signup` page is public and the endpoint is OWNER-only → 401 → logout.
  Remove the public route.
- New `ShopOwnerDashboard` + `Profile` UI: no API calls; nothing to verify against the backend.

---

## 0. Global / shared (do first)

- [x] **`.env.development`**: `VITE_API_BASE_URL=http://localhost:8080` → `http://localhost:5000` (8080 is Jenkins now). *(Done by you: now `https://fd.arulsampath.in`.)*
- [ ] ⚠️ **`.env.production`**: confirm the Cloud Run URL is still the deployed backend. **Checked: that URL still runs the OLD
      backend (sign-in there needs a token; the new one returns 400 for an empty body). Blocked until the new backend is deployed;
      then keep or change the URL.**
- [x] **CORS**: the backend allows `http://localhost:5173/` and `http://localhost:5174/` *with a trailing slash*.
  Browsers send the origin without one, so check that dev requests are not blocked. If they are, fix this in the
  backend (`WebSecurityConfig.corsConfigurationSource`), not in the frontend. **Checked with a real preflight (`Origin: http://localhost:5173`
  and `5174`, against both `localhost:5000` and `fd.arulsampath.in`): allowed, because Spring trims the trailing slash. No change needed.**
- [x] **`Pages/LoginPages/axios.js`** (the only axios instance in use): *(step 1)*
  - [x] `refreshTokenIfNeeded` posts to `${VITE_API_BASE_URL}/auth/refresh-token`. Change it to
        `POST /auth/token/refresh` on the instance (the token goes in the Authorization header; the new token is in
        `accessToken`). Either wire it in or delete it, because it is currently unused. **Done: a token that expires within 5 minutes is now refreshed (one shared call) instead of logging the user out; an already expired token logs out.**
  - [x] Response interceptor: the backend now returns **401** for missing/expired/revoked tokens. Log out on
        any 401 instead of only when the message contains "expired"/"JWT". Revoked tokens (sign-out, password
        reset) send a different message. **Done: logs out on any 401, except `/auth/sign-in`, `/auth/password-reset*` and `/public/*` (a wrong password is also a 401).**
  - [x] Add a **403** handler ("You don't have access") and a **409** handler. 409 means someone else changed the
        invoice first: reload it and retry. **Done globally: both reach the screen with a readable `error.response.data.message`. There's no global popup, to avoid double alerts; the reload on 409 is done per screen (steps 4–7).**
  - [x] Read error text from the new error body: `{message, status:false}`, or on a 400 validation failure a map
        `{field: message}`. **Done: `getErrorMessage(error)` is exported, and the interceptor fills `data.message` for validation
        maps, so the 37 existing `error.response?.data?.message` readers work unchanged.**
  - [x] Optional: add `POST /auth/sign-out` to `handleLogout` so the token is revoked on the server. **Done: new `signOut()` export, used by the Profile menu's logout. `handleLogout` (expiry) now clears all session keys.**
- [x] **`components` / `Components` casing**: all files are now tracked under `src/components/` (`git mv`, 6 files),
      and every relative import resolves with exact case. The Linux build is safe. *(The folder on disk is still named `Components`
      because Windows locked it; git doesn't care. Rename it after closing the dev server if you like.)*
- [x] **Stored session**: after login, store `userId`, `name`, `roles`, `shopId`, `shopName` (see section 1). Replace
      every `localStorage.getItem('userId' | 'shopId' | 'shopName')` reader if the key names change. **Done in `Login.jsx`:
      `token` (from `accessToken`, header as fallback), `userId`, `username` (= `name`), `shopId`/`shopName` (removed, not "null", for
      OWNER), `role` (highest role). Key names unchanged, so no reader needed changes. `getUserName()` no longer crashes without `username`.**
- [x] **Shared helpers**: add a `downloadPdf(url, filename)` helper (axios `responseType: 'blob'` →
      object URL). Every PDF in the new backend comes from a `GET .../pdf` endpoint, not base64 or a POST. **Done: `src/utils/downloadPdf.js`:
      `downloadPdf(url, filename, { params, open, client })`. Error bodies (Blob) are parsed back to `{message}`; pass
      `client: axios` for the public pages. Screens start using it in steps 4–9.**
- [ ] *(rule — ✅ 3c, 3d done)* **Pagination**: list endpoints use `pageNumber` (0-based) and `pageSize` (default 20) and return
      `{ content, pageNumber, pageSize, totalElements, totalPages, lastPage }`. Remove client-side
      "fetch all, slice locally" code where the screen pages.
- [ ] *(rule — ✅ 3c, 3d done)* **Dates**: filters are `from` / `to` (yyyy-MM-dd). `sortBy` + `sortOrder` are replaced by one `sort` param (`newest` / `oldest` on invoices; an unknown value returns 400).
- [x] *(rule — ✅ invoice create (3a) and edit (3d) done)* **Quantities** may now be decimals (e.g. `2.5`). Number inputs need `step="0.01"`, `min="0.01"`, max 1000.
      Don't `parseInt` them.
- [ ] *(rule — ✅ payments screen done in 3b: amounts rounded to 2 dp, decimal keypad)* **Amounts** are rounded to 2 decimals by the server. Show them with `toFixed(2)`/`toLocaleString`,
      and don't recompute GST on the client except as a preview.
- [x] **`paymentStatus`** values are exactly `Dues`, `Partial`, `Paid`. Check any badge/colour mapping that uses
      `PENDING` / `Pending` for payments. **Checked: no screen compares `paymentStatus`. The `'Pending'` checks in TailorAssign/
      TailorMasterDashboard are item status (steps 3g/7); the `"Dues"` strings are only export column names. No change needed.**
- [ ] **WebSocket** (`Pages/OrderStatusUpdates.jsx`): it hardcodes `http://localhost:8080/ws`, nothing imports it,
      and the backend never publishes to `/topic/order-updates`. **Delete it** (or keep it only if live updates are
      planned, and use `VITE_API_BASE_URL`). **Kept by decision (you asked to restore it): the file and both packages are back.
      Still open: it hardcodes `localhost:8080`, and the backend never publishes to `/topic/order-updates`. Wire it up if live
      updates are wanted.**

---

## 1. Authentication

| ✓ | File:line | Old call | New call | Notes |
|---|---|---|---|---|
| [x] | `LoginPages/Login.jsx:73` | `POST /auth/signin` | `POST /auth/sign-in` `{email, password}` | **Done (step 1): stores `userId`/`name`/token from the body; shared `ROLE_HOME` map (the auto-redirect was missing SHOP_ASSISTANT).** Response: `userId`, `name`, `email`, `roles[]`, `shopId`, `shopName`, `accessToken`, `expiresIn`. Read the token from `res.data.accessToken` (the header still works). `res.data.id` → `res.data.userId`. |
| [x] | `LoginPages/Login.jsx` (role redirect) | roles from JWT/`res.data.roles` | `res.data.roles` | Role names: `OWNER`, `SHOP_OWNER`, `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT`. Check the redirect switch and `App.jsx` route guards use exactly these. |
| [x] | `LoginPages/Signup.jsx:78` | `POST /auth/signup` | `POST /auth/users` | **OWNER only** now. Public sign-up is gone, so remove the public `/signup` route or put it behind the OWNER guard. **Checked (step 2): `Signup.jsx` has no route in `App.jsx` and nothing imports it, so there's no public page. The file is kept (delete it in cleanup if you want).** |
| [x] | `.../AddEmployees.jsx:125` | `POST /auth/signup` | `POST /auth/users` (OWNER) | **Done (section 2): explicit payload `{name, email, password, phone, salary (number/null), address, shopId, roles}`.** Role values `tailor`/`tailorMaster`/`shopAssistant` are accepted. |
| [x] | `LoginPages/ForgotPassword.jsx:37` | `GET /forgotPassword/verifyMail/{email}` | `POST /auth/password-reset/code` `{email}` | Always returns 200 (doesn't reveal whether the email exists). |
| [x] | `LoginPages/ResetPassword.jsx:69` | `POST /forgotPassword/changePassword` | `POST /auth/password-reset` `{email, code, newPassword, confirmPassword}` | **Fields renamed correctly, and the route is now `/resert-password/:email/:code` (the email template still passes `OTP_CODE` + `EMAIL`). **Done (step 2): sends JSON (tested against the local backend: JSON → 400 "Invalid or expired code", the old FormData → 415). Backend errors are now shown on the form; the error key is `password` throughout; the password rule matches the backend (8–100 chars, letter + digit, symbols allowed; it used to reject `@#!`).** |
| [x] | Profile pages (tailor / TM / SA) — TM ✓ (4a), tailor ✓ (4b), shop assistant ✓ (4c) | `/tailor/profile`, `/tailor-master/profile`, `/shop-assistant/profile` | `GET /auth/me` | One endpoint for every role. |

---

## 2. Shops, employees, petty cash (OWNER + SHOP_OWNER)

| ✓ | File:line | Old call | New call | Notes |
|---|---|---|---|---|
| [x] | `AddEmployees.jsx:40`, `AddPettyCash.jsx:26` | `GET /shops/shop-name` | `GET /shops/names` | OWNER only. **Done: fixed to `/shops/names`.** Response `{shopId, shopName}` matches the dropdown. |
| [x] | `AddPettyCash.jsx:39` | **GET** `/owner/shop/add-petty-cash/{amount}/{shopId}` | **POST** `/shops/{shopId}/petty-cash` `{amount}` | The method changes from GET to POST. Response field is `amountAdded`. **Section 2: sends `Number(amount)`; dropped the unused `response.data.amount` read; any error now shows a popup (before, only 401 did).** |
| [x] | `AddShop.jsx:113` | `POST /owner/shop` | `POST /shops` | |
| [x] | `Shop.jsx:24` | `GET /owner/shop` | `GET /shops/summaries` (cards) or `GET /reports/shops/overview` (with totals) | **Done: loads both, joined by `shopId` (summaries = phone/address/GST; overview = all-time counts), and keeps `id = shopId` for the modals. `AllShopData.jsx` cards now read `receiptCount`, `invoiceCount`, `employeeCount`, `pettyCash`, `cashExpenses`, `pendingOrders`, `deliveredInvoiceCount`. "Total Bank Expense" is now an amount (₹ expenses − cash expenses); before, it showed a count.** |
| [x] | `EditEmployeeModal.jsx:178` | `PUT /owner/shop/update-owner-details` | `PUT /shops/{shopId}/owner` `{ownerUserId, name, email, phone, salary, password, reason}` | **Done: form fields mapped to the new names at submit (an empty password is sent as `null` = unchanged). Also fixed: number `salary` crashed `.trim()`; password rule is now 8+ with a letter and a digit (was 6+); reason max 200 (was 500). The success popup shows the backend's message (e.g. "owner signed out").** |
| [x] | `TotalEmployees.jsx:48` | `GET /owner/employee/{shopId}/all-employee` | `GET /shops/{shopId}/employees` | Rows `{userId, name, email, phone, salary, roles[], accountCreationDate}` match the table. |
| [x] | `TotalEmployees.jsx:76` | `GET /owner/employee/{shopId}/find-user?term=` | `GET /shops/{shopId}/employees?search=` | *(Corrected: this is a search inside the shop, not the unassigned list.)* **Done: `/shops/{shopId}/employees?search=`. Roles are strings now: the Role column and the Excel export show `roles.join(', ')`, and the Edit button checks `roles.includes("SHOP_OWNER")` (before, it could never appear).** |
| [x] | `TotalEmployees.jsx:208` | `DELETE /owner/employee/delete-employee/{userId}` | `DELETE /shops/{shopId}/employees/{userId}/assignment` | Removes the user from the shop (does not delete the account). To delete the account use `DELETE /shops/{shopId}/employees/{userId}`. |
| [x] | `EmployeeExpense.jsx:356` | `GET /shops/all-employees` | `GET /shops/{myShopId}/employees` | `myShopId` comes from the stored session. **Done: uses `localStorage.getItem('shopId')`, with try/catch.** Dropdown `employee.userId`/`employee.name` ✓. |
| [x] | `PettyCash.jsx:52` | `GET /owner/petty-cash/{shopId}/all-pettyCash` | `GET /shops/{shopId}/petty-cash` | **Re-checked in section 2: filters renamed `startDate/endDate/searchTerm` → `from/to/amount`; rows use `addedAt` (was `addingDate` → empty Date column).** |
| [x] | `PettyCashManagement.jsx:26` | `GET /shops/petty-cash` | `GET /reports/financials/summary` (balance) + `GET /shops/{myShopId}/petty-cash` (history) | **Done: Total Assigned = `pettyCash.added`, Remaining = `pettyCash.currentBalance`, Total Used = the difference (`totalExpense` doesn't exist in the new response).** |

---

## 3. Shop owner: invoices, payments, receipts

### 3a. Create invoice: `ReceiptGeneration.jsx`
- [x] `:1134` `POST /receipt/download/invoice` (multipart, **returned a PDF**) →
      `POST /invoices` multipart with part **`invoiceJson`** (JSON blob) and item images named
      **`item_{index}_{anything}`**. It **returns JSON** (`invoiceId`, totals, items with `itemUniqueId`). **Done (section 3a): part renamed
      `invoice` → `invoiceJson` (sent as a JSON blob); images keep the `item_{index}_…` names; the fake `shopId: "SHOP123"` was dropped
      (the shop comes from the token); success popup shows the new invoice number.**
- [x] Then download the PDF with `GET /invoices/{invoiceId}/pdf` (blob). **Done via `downloadPdf()`, saved as `{invoiceId}.pdf`. If only the PDF
      fails, a warning says the invoice is saved and can be downloaded later (no false "failed" error).**
- [x] Item `itemQuantity` accepts decimals (0.01–1000, 2 dp); the price is per 1 unit. **Done: the quantity box was a text input that
      re-parsed every keystroke (`2.` → `2`), so decimals could never be typed. It's now `type="number" step="0.01" min="0.01" max="1000"` with
      a decimal keypad; validation adds the 1000 maximum; quantity and price are sent as numbers rounded to 2 dp.**
- [x] `expectedDeliveryDate`: send `yyyy-MM-dd`. **Checked: already sent as `yyyy-MM-dd`; `backDate` too (date inputs).**
- [x] Customer WhatsApp/email notifications are sent by the server; remove any client-side send step. **Checked: this screen has no client-side send.**
- [x] Warmup `:834`, `:1192` `GET /pdf-service/warmup`: **unchanged**. *(Checked.)*

### 3b. Payments / delivery: `InvoiceGenerator.jsx`
- [x] `:44`, `:327` warmup: unchanged. *(Checked.)*
- [x] **Done (section 3b): uses `GET /invoices/{invoiceId}` (it has customer, items, `balanceDue`), via one `loadInvoice()` that is also
      used after every payment.** `:75` `GET /invoices/{id}` → `GET /invoices/{invoiceId}/account` for the payment screen (totals, payments, receipt
      status), or `GET /invoices/{invoiceId}` for full details.
- [x] `:154` `PUT /invoices/{id}/update-payment` → split into: **Done (section 3b).**
  - [x] `POST /invoices/{invoiceId}/payments` `{amount, paymentMethod, transactionId?}`. The response includes the new
        **payment receipt** (`paymentReceipt.paymentReceiptNo`); show a "Download payment receipt" button →
        `GET /payment-receipts/{no}/pdf`. **Done: the success popup names the payment receipt, and a "Download Payment Receipt (no)"
        button appears.**
  - [x] `POST /invoices/{invoiceId}/discount` `{amount, reason}` when a discount is given. **Done, keeping the old meaning ("customer pays X,
        the rest is written off"): the discount is called first with the pre-GST amount `remainder ÷ 1.05`, then the payment is the
        balance the server reports (it can differ from the typed amount by ₹0.01 because CGST/SGST are rounded separately). Only one
        discount per invoice is allowed; the backend message is shown.**
- [x] `:290` `POST /receipt/download/receipt` → `POST /invoices/{invoiceId}/receipt` (creates the delivery receipt,
      only when fully paid) → then `GET /receipts/{receiptNo}/pdf`. **Done. The old "Yes, generate anyway" with dues was removed (the
      backend refuses it); it now asks to collect the due first. A receipt generated earlier is downloaded again (`created: false`).**
- [x] Handle **409** (the invoice changed in another tab): reload the account and let the user retry. **Done: 409 → "Invoice changed"
      message; every payment error reloads the invoice, so the screen shows the real balance.**
- [ ] New (optional): payment history list `GET /invoices/{invoiceId}/payment-receipts`. *(Not done: optional; say if you want it.)*

### 3c. Payment tracking: `PaymentTracking.jsx`
- [x] `:402` `GET /shopOwner/invoices/search-invoice` → `GET /invoices?search=...` **Done (section 3c): `?search=&details=true`; it returns a
      list now (invoice number, customer name or phone), shown in the table; an empty result shows "Invoice not found".**
- [x] `:529` `GET /invoices/all-invoice?sortOrder&startDate&endDate` →
      `GET /invoices?from=&to=&paymentStatus=&sort=&pageNumber=&pageSize=` **Done: `from`/`to` (already `yyyy-MM-dd`), `sort=newest`,
      `details=true` (full invoices: the table needs `totalAmount`, `paymentMethod`, `customer`, which summary rows lack). Page-size options cut to
      10/20/25/50/100; the backend rejects more than 100 (the old options went to 500).**
- [x] `:700` export (`"Dues": item.balanceDue`): check that the field names match `InvoiceSummaryResponse`. **Checked against the full
      invoice (details=true): `customer.customerId`, `customer.name`, `totalAmount`, `totalAmountWithGST`, `advancePaid` (= total paid so far),
      `balanceDue`, `paymentMethod`, `paymentStatus` all exist. The status badge is now safe if the status is missing.**

### 3d. View / edit orders: `ViewEditOrderTable.jsx`, `EditOrderModel.jsx`
- [x] `ViewEditOrderTable.jsx:370` `GET /invoices/all-invoice-with-all-details` → `GET /invoices?details=true&...` **Done (section 3d):
      `from`/`to`, `sort=newest`, paging; page-size options cut to ≤100 (the backend rejects more). Customer cell is safe if `customer` is missing.**
- [x] `ViewEditOrderTable.jsx:400` search → `GET /invoices?search=` **Done: `?search=&details=true`; results (can be several) go to the table.**
- [x] `ViewEditOrderTable.jsx:629` `DELETE /shopOwner/invoices/{shopId}/delete-invoice/{id}` →
      `DELETE /invoices/{invoiceId}?reason=...`. *(Correction: the reason is **optional**, max 300 characters.)* **Done: the confirm popup has an
      optional reason box, sent as `?reason=`; the text now says the invoice moves to Deleted Invoices.**
- [x] `EditOrderModel.jsx:1011` `PUT /invoices/update-items` → `PATCH /invoices/{invoiceId}` multipart part
      **`editJson`** with the **full item list** (existing items keep their `itemUniqueId`; new ones have none;
      omitted ones are removed). Optional `customer` object to edit the customer details. **Done: part `request` → `editJson`; images keep
      `item_{index}_…` (index = position in `items`); the extra `imageFileNames` part and the `deletedItems`/`transactionId`/`imageUrl` fields
      were dropped (removed items are simply left out). Fixed: quantity was saved with `parseInt` (2.5 → 2) and the box had the same
      keystroke bug as 3a, so it's now a decimal input (0.01–1000) and saved rounded to 2 dp. Measurements are sent as `null` (= keep) instead of
      `{}` (= wipe) when nothing is set.**
- [x] Editing is blocked after the delivery receipt, except for customer-only changes. Show the server's
      400 message. **Checked: the server message is shown. Note: the list still blocks opening the editor for `ORDER_DELIVERED`
      orders, so customer-only corrections after delivery aren't possible from the UI yet. Say if you want that allowed.**
- [x] `EditOrderModel.jsx:1036` `POST /invoices/send-invoice`: **this endpoint doesn't exist** (in either backend). Remove it,
      or replace it with "download PDF" (`GET /invoices/{id}/pdf`). **Done: the function now downloads the PDF; its button was already
      commented out, so nothing changes on screen.**

### 3e. Deleted invoices: `DeletedInvoice.jsx`
- [x] `:41` `GET /deleted/invoices/all-deleted-invoices` → `GET /reports/deleted-invoices?search=&from=&to=&pageNumber=` **Done (section 3e).
      The row shape changed, and the old table would have crashed (`totalAmount.toLocaleString()` on a field that no longer exists). Now: `id` =
      `archiveId`; amount = `totalAmountWithGST`; the "Payment Method" column (gone) became "Deleted On / Reason"; the export adds customer
      name/phone, deleted on/by and reason; page sizes ≤100. *(Correction: the live shop-owner menu is `PaymentInvoice.jsx`, and its "Deleted Invoice" tab is visible;
      `ShopOwnerDashboard.jsx` isn't used anywhere.)***

### 3f. Expenses (SHOP_OWNER)
- [x] `ShopExpense.jsx:73` `POST /expenses/add` → `POST /expenses` **Done (section 3f): fields already match `CreateExpenseRequest`; amount is
      sent as a number; the error popup no longer crashes when there's no response.**
- [x] `EmployeeExpense.jsx:120` `POST /expenses/add-employee-expense` → `POST /employee-expenses` **Done. Also fixed: the employee was sent as
      `userId`, but the backend requires `employeeUserId`, so every save would have been a 400. Refreshment/travel/misc line items already match.**
- [x] `RevenueTracking.jsx:59` `GET /expenses/all` → `GET /expenses?from=&to=&type=&paymentMethod=&sort=&pageNumber=` **Done:
      `startDate`/`endDate` → `from`/`to`; rows (`id`, `expenseType`, `expenseAmount`, `paymentMethod`, `notes`, `expenseDate`) match; page sizes were
      already ≤100.**
- [x] `RevenueTracking.jsx:26` `DELETE /expenses/{id}`: same path (`/expenses/{expenseId}`), unchanged. **Checked; the failure alert now shows
      the backend's message.**

### 3g. Special-category / TM view on the shop-owner side: `TailorAssign.jsx`
- [x] `:45` `GET /tailor-master/view-order` → `GET /invoices/{invoiceId}` (or the production queue item) **Done (section 3g): this was the
      search fallback, now `GET /invoices?special=true&details=true&search=` (invoice no., name or phone). Its "View" button pointed to the tailor
      master's route; it now opens `/shopOwner/tailorAssign/:id`.**
- [x] `:62` `GET /invoices/special-category/all-invoice` → `GET /invoices?special=true` **Done: `special=true&details=true`; the newest 100
      are loaded and paged in the browser (before, it fetched one server page and sliced it again, so page 2 was always empty). Local filter is
      case-insensitive and safe for missing customer fields.**
- [x] `:186`, `:226` `order.status === 'Pending'`: check against the new item workflow status names. **Done: neither `status` nor `dueDate`
      exists on an invoice (old or new), so the overdue highlight never worked. Now overdue = not `ORDER_DELIVERED` and `expectedDeliveryDate`
      before today (IST). Open for section 4: the "Assign Tailor" button reads an invoice-level `assignmentStatus` that invoices don't have
      (assignment is per item), and its popup is the tailor master's `AssignTailor.jsx`.**

### 3h. New screen (client requirement)
- [x] **Today's deliveries dashboard**: `GET /reports/deliveries?date=yyyy-MM-dd` (overdue / due today / upcoming). **Done (section 3h):
      new page `Pages/PaymentInvoice/Deliveries/` (Deliveries.jsx + .css), route `/shopOwner/deliveries`, tab "Today's Deliveries" in the
      shop-owner menu (`PaymentInvoice.jsx`). Shows: a date picker (default today, IST); summary cards (due with ready / in production,
      overdue, delivered, ₹ to collect); a note for open orders without a delivery date; Overdue and Due cards with item statuses,
      days late, balance and a button (Collect payment / Deliver / Open) that opens the payments screen with the invoice pre-filled; and a
      Delivered table with a receipt PDF download.**

---

## 4. Production staff (TAILOR_MASTER, TAILOR, SHOP_ASSISTANT)

> **2026-11-18 — TAILOR_MASTER rebuild in progress** (plan + progress: `TAILOR_MASTER_PAGES_PLAN.md`).
> - [x] Shared theme / components moved to `src/Pages/shared/` (`AppShell`, `components/*`, `lib/{format,period,invoice}`,
>   `theme.css`); `ShopOwnerLayout` uses `AppShell`.
> - [x] New `/tailorMaster` app shell (Dashboard · Workplace placeholders), login home `/tailorMaster/dashboard`;
>   old TM screens kept at `/tailorMaster/legacy`, `/:invoiceId`, `/my-orders…` until the new workplace lands.
> - [x] ~~Workplace / Dashboard (steps 3–4)~~ replaced on request by the **old TM functionality in the new UI**:
>   `/tailorMaster/desk` (master desk), `/tailorMaster/cutting` (cutting queue), `/tailorMaster/orders/:invoiceId`.
> - [x] 2026-11-19 improvements: desk count strip + stage filter + overdue first; cutting queue bulk Mark cut and
>   printable cutting sheets (also on Order items); new `/tailorMaster/assign` (Assign board), `/tailorMaster/follow-up`
>   (Follow-up), `/tailorMaster/my-work` (My work). Frontend only.
> - [x] 2026-11-19 review change 1: Master desk page + sidebar link removed; TM home = `/tailorMaster/cutting`.
> - [x] 2026-11-19 review change 2: Assign board simplified — tailor dropdown + Assign on each row.
> - [x] 2026-11-19 review change 2b: Assign board workload card + cards / table views + custom tailor dropdown.
> - [x] 2026-11-19 review change 3: instant filter pills (due date on Cutting queue + Assign board; period on My work).
> - [x] 2026-11-19 review change 4: Assign board workload as a small right column; classic tailor dropdown.
> - [x] 2026-11-19 review change 5: names-only tailor dropdown (design.md style) + one-line workload strip.
> - [x] 2026-11-19 review change 6: workload pills; shop-owner style tables + 20-per-page pager on TM lists; My work
>   chart grouped by period (7 days / weeks / months / years).
> - [x] 2026-11-19 review change 7: due pills All / Due today / Overdue; Follow-up = all tailors → one tailor's items.
> - [x] 2026-11-19 review change 8: design photo thumbnails on the Cutting queue and Assign board (cards + table) → design popup.
> - [x] 2026-11-19 review change 9: Cutting queue ticks per item (not per invoice); pager counts items like the pills.
> - [x] 2026-11-19 review change 10: step timings in the Track popup (stage-history list removed); time in step / with tailor on Follow-up.
> - [x] 2026-11-19 shared production pieces moved to `src/Pages/shared/production/` (DeskParts, Modals, WorkChart, desk.js,
>   AssignBoard, MyWork); tailor master pages use them.
>
> **2026-11-19 — TAILOR rebuild** (plan: `TAILOR_PAGES_PLAN.md`).
> - [x] Backend: shop assistants `withLoad`; the tailor hands over only from STITCHING (no re-assign).
> - [x] `/tailor/queue` Work queue (Start stitching), `/tailor/assign` Assign board (shop assistant), `/tailor/my-work`;
>   login home `/tailor/queue`; old `/tailor` NavBar routes removed.
> - [x] Tailor tabs renamed: New jobs · Stitching · My progress (URLs unchanged).
> - [x] Sidebar (shared AppShell, all roles) given more space: padding, gaps between links, divider under the shop name.
> - [x] Tailor master tabs renamed: Cutting queue · **Assign tailors** · **Tailors** · **My progress** (URLs unchanged).
> - [ ] Manual test with a tailor login; delete old `TailorDashboard/*` (with OK).
>
> **2026-11-19 — SHOP_ASSISTANT rebuild** (plan: `SHOP_ASSISTANT_PAGES_PLAN.md`), frontend only.
> - [x] `/shopAssistant/jobs` My jobs (Start hemming → Start ironing → Mark completed, bulk Next step), `/shopAssistant/my-work`
>   My progress; login home `/shopAssistant/jobs`; old `/ShopAssistance` redirects.
> - [ ] Manual test with a shop assistant login; delete old `ShopAssistance/*` (with OK).
> - [x] Empty state (shared `EmptyState`, every table and list): icon in a soft circle, title, message and action stacked
>   and centred with even spacing; inside a table a divider under the header and it stays in view when the table scrolls.
> - [x] Toasts + confirmation popups (all SweetAlert2 popups, every screen) themed to design.md: new global
>   `src/Pages/shared/popups.css` (imported in `main.jsx`) + rebuilt `utils/toast.js` (tinted line icons, close button,
>   status-coloured progress line, errors stay 6 s, `toast.confirm({danger})`). In-page `Modal`: soft shadow, pop-in, display title.
> - [x] Toast fixes: no dark strip behind toasts; toast holder sized so the toast is not cut off; toasts above popups (z 2000).
> - [x] Shop owner invoice Payment card: GST split into CGST (rate %) / SGST (rate %) / IGST (when used), plus "Taxable amount"
>   after a discount.
> - [ ] Manual test of the tailor master screens with a TM login.
> - [ ] Delete unused steps 3–4 files and the old TM screens (with OK).

The new model: each person gets **`GET /items/queue`**, a list of the items assigned to them, and every item
carries **`availableActions`**. Buttons should be driven by `availableActions`, not hardcoded per role.
Actions:
- `PATCH /invoices/{invoiceId}/items/{itemUniqueId}/status` `{status}`
- `.../tailor` `{tailorId}` — *(correction: **PUT**, not PATCH; DELETE unassigns, shop owner only)*
- `.../shop-assistant` `{shopAssistantId}` — *(**PUT**; DELETE unassigns)*

**Approach used (section 4):** new shared helper `src/utils/workQueue.js`. It loads the whole queue (all pages), **groups the items back into
invoice rows** with the old field names (`items[].itemOrderStatus`, `assignedTo`, `assignmentStatus`, `customer.name`, …) so the existing
screens keep their layout, and wraps the action calls (`updateItemStatus`, `assignTailor`, `assignShopAssistant`, assignee lists). Date
filters and paging now run in the browser on the invoice date (IST), because the queue has no date filter. Queue rows have no
`customer.customerId`, so that column is empty. Section 4 is done in four parts: **4a** tailor master, **4b** tailor, **4c** shop assistant,
**4d** analytics + assignee lists.

Status rules are strict: only the TAILOR_MASTER cuts; only the assigned tailor stitches; only the assigned shop
assistant does the finishing steps.

| ✓ | File:line | Old call | New call |
|---|---|---|---|
| [x] | `TailorMasterDashboard.jsx:116` | `/tailor-master/profile` | `GET /auth/me` |
| [x] | `TailorMasterDashboard.jsx:138`, `TailorOrderDetails.jsx:357`, `OrderDetails.jsx:70`, `MyOrdersTM.jsx:162` | `/tailor-master/view-order` | `GET /invoices/{invoiceId}/items/{itemUniqueId}` (or the item from the queue) — **4a done: TMD + MyOrdersTM search the whole queue (all dates) by invoice no. / customer; OrderDetails loads the order from the queue (the tailor master can't read invoices) and now shows the items when opened by URL (they used to stay empty). **4b done: `TailorOrderDetails.jsx` loads the order from the tailor's queue.** |
| [x] | `TailorMasterDashboard.jsx:167` | `/tailor-master/shop/all-invoice` | `GET /items/queue` — **Done (4a): queue grouped into invoice rows, filtered by invoice date and paged in the browser. Also fixed: shop name from `/auth/me` (`shopName`, not `assignedShops[0]`); the overdue highlight (used `status`/`dueDate`, which don't exist) now uses `expectedDeliveryDate` before today.** |
| [x] | `MyOrdersTM.jsx:63` | `/tailor-master/shop/order-accepted-status/all-invoice` | `GET /items/queue?status=...` — **Done (4a): `status=ORDER_ACCEPTED` (items waiting for cutting), grouped, date-filtered and paged in the browser.** |
| [x] | `MyOrdersTM.jsx:320`, `OrderStatusUpdater.jsx:23` | `PUT /tailor-master/my-order/update-status` | `PATCH /invoices/{id}/items/{itemId}/status` — **Done (4a): `{status: "CUTTING"}`; errors show the backend's message.** |
| [x] | `AssignTailor.jsx:25`, `AdminTailorDashboard.jsx:26` | `/tailor-master/all-tailor` | `GET /items/assignees/tailors` — **4a: `AssignTailor.jsx` done. 4d: `AdminTailorDashboard.jsx` (shop owner's Analytics tab) uses `GET /shops/{myShopId}/employees` filtered by role instead, because it searches by phone, which the assignee list doesn't have.** |
| [x] | `AssignTailor.jsx:93` | `POST /tailor-master/assign-tailor` | `PUT /invoices/{id}/items/{itemId}/tailor` — **Done (4a): returns the updated item, and OrderDetails refreshes that row (the button used to stay "Assign Tailor"). The Assign button is disabled, with a hint, when `availableActions` lacks `ASSIGN_TAILOR`. The shop owner's TailorAssign list (3g) now opens the order page to assign per item, instead of the popup (which crashed without an item).** |
| [x] | `TailorDashboard.jsx:1481` | `/tailor/orders` | `GET /items/queue` — **Done (4b): the tailor's own items (TAILOR_ASSIGNED, STITCHING), grouped, date-filtered (default this week) and paged in the browser; search covers the whole queue.** |
| [x] | `TailorDashboard.jsx:1531` | `/tailor/profile` | `GET /auth/me` — **Done (4b); shop name from `shopName`.** |
| [x] | `TailorDashboard.jsx:1548` | `/tailor/view-order` | `GET /invoices/{id}/items/{itemId}` — **Done (4b): this was the search; it now searches the queue.** |
| [x] | `TailorDashboard.jsx:1625` | `PUT /tailor/orders/update-status` | `PATCH .../status` — **Done (4b): `{status: "STITCHING"}`; the row is refreshed from the returned item; errors show the backend's message.** |
| [x] | `AssignShopAssistant.jsx:21`, `AdminTailorDashboard.jsx:27` | `/tailor/all-shopAssistant` | `GET /items/assignees/shop-assistants` — **4b: `AssignShopAssistant.jsx` done. 4d: `AdminTailorDashboard.jsx`, see the row above.** |
| [x] | `AssignShopAssistant.jsx:88` | `POST /tailor/assign-shopAssistant` | `PUT .../shop-assistant` — **Done (4b): `{shopAssistantId}`; only allowed once the item is in STITCHING (the backend message says so otherwise); the assigned item then leaves the tailor's queue. The popup shows the customer name (queue rows have no customer ID).** |
| [x] | `ShopAssistance.jsx:759` | `/shop-assistant/profile` | `GET /auth/me` — **Done (4c); shop name from `shopName`.** |
| [x] | `ShopAssistance.jsx:783` | `/shop-assistant/view-order` | `GET /invoices/{id}/items/{itemId}` — **Done (4c): this was the search; it now searches the whole queue.** |
| [x] | `ShopAssistance.jsx:819` | `/shop-assistant/orders` | `GET /items/queue` — **Done (4c): own items (SHOP_ASSISTANT_ASSIGNED, HEMMING, IRONING), grouped, date-filtered when a filter is applied, paged in the browser.** |
| [x] | `ShopAssistance.jsx:908` | `PUT /shop-assistant/orders/update-status` | `PATCH .../status` — **Done (4c): HEMMING → IRONING → ORDER_COMPLETED, one step at a time (the backend rejects skipping a step, and the message is shown); the row (and search result) refresh from the returned item; completed items leave the list on the next refresh.** |
| [x] | `TailorMasterDashboard.jsx:658` | `order.status === "Pending"` | Use the new status names from the queue response — **Done (4a), see `:167`.** |
| [x] | `Analytic.jsx:66`, `TailorAnalyticsDashboard.jsx:56` | `/employee/activities/employee/{userId}/analytics` | **New backend endpoint `GET /reports/activities/employee-analytics?employeeId=&role=&from=&to=` (4d, your choice: port it).** The old figures (assigned, quantity, value, reassignments; for SHOP_OWNER the item status counts, revenue and invoice/receipt/deleted counts) had no equivalent; they are now computed exactly as the legacy service did. **Verified:** the legacy aggregations (rebuilt from git `71b1a5f`) and the new ones give identical results on 5 random data sets incl. IST date edges (170 checks, 0 failures; two deliberate mutations are caught); service checks for scoping, roles, rounding and JSON (22, 0 failures). Frontend: `TailorAnalyticsDashboard.jsx` calls it (`employeeId`, `role`, `from`/`to`); `Analytic.jsx` too, with `shopId` (owner) and the role from the string `roles` (was `roles[0].roleName`; the card groups would never have shown), and `itemStatusCounts` flattened for its status cards. Docs: `MODULES_API_DOCUMENTATION.md`, migration and build guides. **The backend must be restarted to serve it.** |

- [x] Show the 403/400 message when an action isn't allowed (e.g. a tailor trying to cut). **Done in every action handler of 4a–4c (`error.response.data.message`).**
- [ ] *(→ section 7 cleanup; recommended: these commented copies of whole components caused two misplaced automated edits in section 4)* Remove the large commented-out blocks in `TailorMasterDashboard.jsx` (`:1418`, `:1857`, `:1895`, `:2300`) while you're there.

---

## 5. Platform OWNER dashboard

| ✓ | File:line | Old call | New call |
|---|---|---|---|
| [x] | `FinancialOverviewChart.jsx:10`, `RevenueExpensePieChart.jsx:12` | `/dashboard/financial-summary/all` | `GET /reports/financials/summary` (all shops when no `shopId`) — **Done (section 5): Income = `collections.total`, Expenses = `expenses.total`, Petty Cash = `pettyCash.currentBalance`, Net Profit = `netCashFlow`. (These charts are on the `/home` page.)** |
| [x] | `MonthlyProfitChart.jsx:10` | `/financials/shop/{shopId}` | `GET /reports/financials/daily?shopId=&from=&to=` (group by month on the client) — **Done: last 12 months (≤366 days, as the backend requires), all shops (it had a hardcoded `shopId = "1234566"`), summed per month; profit = `netCashFlow`.** |
| [x] | `CompletedOrders.jsx:51,71` | `/owner/order/{shopId}/all-completedOrders` | `GET /invoices?shopId=&status=delivered` — **Done: list + search (`search`), `from`/`to`, `sort=newest`, page sizes ≤100; summary rows have every field the table reads. The "no results" check used `res.data.length` (always undefined on a page); it now checks `content`.** |
| [x] | `PendingOrders.jsx:52,72` | `/owner/order/{shopId}/all-pendingOrders` | `GET /invoices?shopId=&status=pending` — **Done, same as Completed.** |
| [x] | `ExpensesEntries.jsx:36`, `OwnerExpenseModal.jsx:38` | `/shops/view-expenses/byPaymentMethod` | `GET /expenses?shopId=&paymentMethod=` — **Done: `paymentMethod=CASH` / `BANK` (the backend matches ignoring case, so the saved `Cash` matches), `from`/`to`, page sizes ≤100; ExpensesEntries' extra exact-case `BANK` filter removed. Note: expenses saved as `Other` appear in neither popup, but they are included in the dashboard's "Total Bank Expense" card (= all non-cash expenses, section 2).** |
| [x] | `TotalInvoices.jsx:56` | `/owner/invoice/{shopId}/all-invoice` | `GET /invoices?shopId=` — **Done (out of order, after a "No static resource" 404 report): `shopId`, `from`/`to`, `search`, `sort=newest`; page sizes ≤100** |
| [x] | `TotalInvoices.jsx:171` | `POST /owner/invoice/invoice-download` | `GET /invoices/{id}/pdf?shopId=` (blob) — **Done via `downloadPdf()`** |
| [x] | `TotalReceipts.jsx:57` | `/owner/receipt/{shopId}/all-receipt` | `GET /receipts?shopId=` — **Done. Backend extended: `GET /receipts` now also takes `from`, `to` (receipt date, IST) and `search` (receipt no., invoice no., customer name/phone), because the screen filters by date (default last 30 days) and searches; without them, filtering would break paging. The same request without these parameters behaves as before. Checked with a query-capturing harness (12 checks, 0 failures); documented. Frontend: `startDate`/`endDate`/`term` → `from`/`to`/`search`; rows use `amountPaid` (the table read `advancePaid`/`balanceDue`, which receipts don't have, and `.toLocaleString()` on them would have crashed). **Needs the backend restart.** |
| [x] | `TotalReceipts.jsx:172` | `POST /owner/receipt/receipt-download` | `GET /receipts/{receiptNo}/pdf?shopId=` (blob) — **Done via `downloadPdf()`.** |
| [x] | `CompletedOrders.jsx:151` | `orderStatus \|\| 'Pending'` | Use the invoice `status` field from the new response — **Done: rows carry `orderStatus` (`ORDER_DELIVERED` here); the fallback is now `ORDER_DELIVERED` instead of `Pending`.** |

The `status` filter takes `pending`, `delivered` or `all` (delivered = delivery receipt issued).

---

> **2026-11-19 — platform OWNER rebuild** (plan: `PLATFORM_OWNER_PAGES_PLAN.md`).
> - [x] Step 1: `/owner` app shell + `/owner/overview` (all branches / one branch, KPIs vs previous period, trend, payment mix,
>   sortable branch table); login home `/owner/overview`; old `/dashboard` kept as "Classic dashboard".
> - [x] Step 2: `/owner/branches` (branch cards) + `/owner/branches/:shopId` read-only tabs (Orders, Receipts, Deliveries,
>   Expenses & petty cash, People, Activity); `api/owner.js`.
> - [x] Step 3: admin CRUDs (add / edit branch, shop owner details, petty cash, staff new / add existing / edit / move /
>   remove / delete) in `/owner/branches…`.
> - [x] People rows: icon buttons (edit · move · remove · delete, tooltips) instead of text buttons, so rows stay one line.
> - [x] Step 4: `/owner/compare` (2–5 branches side by side + 12-month chart) and `/owner/expenses` (all / one branch).
> - [x] Step 5: `/owner/production` + backend `GET /reports/production` (stages now, turnaround in the period).
> - [x] Step 6: `/owner/people` (work per role, ★ top) + `/owner/audit` (log, deleted invoices / expenses, skipped steps);
>   backend `findUserNames` fix (deleted-by / employee names).
> - [x] Step 7: `/owner/customers` + backend `GET /reports/customers` (new vs returning, top customers, dues).
> - [x] Step 8: old owner screens deleted (`Pages/Dashboard/*`, `Pages/OwnerDashboardCharts/*`, `/dashboard…` + `/home` routes,
>   "Classic dashboard" link); Home redirect OWNER → `/owner`. Unused now: `Components/ProtectedRoute.jsx`, `RoleProtectedRoute.jsx`.

> - [x] 2026-11-20 Item status labels: ORDER_ACCEPTED shows "Item accepted", ORDER_COMPLETED "Item completed" everywhere
>   (`humanize` in `shared/lib/format.js`, track steps in `shared/production/desk.js`); status codes unchanged.
> - [x] 2026-11-20 Branch report download: shared `ReportDownloadModal` (period: today, yesterday, this / last week,
>   this / last month, pick a month, last 3 / 6 / 12 months, pick a day, custom up to 366 days; Excel or PDF) on the
>   owner branch page header and the shop owner dashboard header; `downloadFile` (server file name) +
>   `downloadBranchReport` (`GET /reports/branch-report`).
> - [x] 2026-11-23 Branch report download **hidden** (owner branch page + shop owner dashboard) behind
>   `FEATURES.branchReportExport = false` in `shared/lib/features.js`; code and backend endpoint kept, set `true` to show it.
> - [x] 2026-11-24 **Excel export on every filtered table** (shop owner + platform owner): shared
>   `shared/lib/exportXlsx.js` (SheetJS, loaded on demand; all pages of the current filters, title + filters + export
>   time on top, auto-filter, ₹ Indian money format, real dates, SUM totals) and `shared/components/ExportButton.jsx`.
>   Shop owner: Invoices (Pending / Completed / Deleted), Expenses (+ Deleted), Deliveries (current view), Employees.
>   Platform owner: Branch page (Orders, Receipts, Deliveries, Expenses, Petty cash, Activity), People, Expenses
>   (+ Deleted), Audit (4 tabs), Production (stages, branches), Customers (months, top 5), Overview (cash flow,
>   branches), Compare (side by side, month by month). Not exported (no filters / not a table): branch People tab,
>   Branches cards, Employee detail. Paged lists reuse the table's params (100 per request = backend max).
>   Open: the Deliveries page still also has its older header "Excel" button (whole day, no filters).
> - [x] 2026-11-23 Invoice wizard item photos are shrunk in the browser before upload (`shared/lib/compressImage.js`:
>   longest side 1600 px, JPEG 82%, phone rotation kept; files under 400 KB, GIF/SVG or photos the browser can't read
>   are sent as they are). The photo button shows "Preparing photo…" meanwhile.
> - [x] 2026-11-23 Invoice wizard, Customer step: "Mobile number" renamed **"WhatsApp number"** and moved first. After the
>   10th digit `lookupCustomer` (`GET /invoices/customer-lookup?phone=`) runs: an existing customer fills in name, email,
>   alternate number, address (editable: on create the edits update the customer record; an empty optional field keeps the stored value) and the state of their latest
>   invoice, with an "Existing customer · N invoices" note; a new number shows "New customer — enter their details".
>   Changing the number clears details that came from a lookup.
> - [x] 2026-11-20 Owner People page: with "All branches" there is no "All" role pill (one role at a time, Shop owner by
>   default) so the list stays short; with one branch picked "All" is back and is the default.
> - [x] 2026-11-20 Owner Customers page: "Customers with dues" removed; "Top 5 customers" (was top 20) full width.
> - [x] 2026-11-20 Owner Expenses page entries: filters (search, for shop / employees + employee, category, paid by) and
>   sort (newest / oldest), result count, Clear filters. Deleted entries: search + for only (what the API supports).
> - [x] 2026-11-20 Owner Expenses page: "By category" and "By payment method" cards removed; "By branch" (all branches)
>   now full width; with one branch picked the entries list follows the KPIs directly.
> - [x] 2026-11-20 Owner Compare: no 5-branch limit. Palette now 8 validated colours (added violet #8b62d9, cyan #16a3b8,
>   brown #a35a2a; validate_palette.js light: all pass); from the 9th branch the colours repeat with dashed / dotted lines
>   (`branchStyle`, `swatchBackground` in `Owner/lib/owner.js`). "x of y selected", Select all, Clear.
> - [x] 2026-11-20 Owner branch page filters + sorts: Expenses (search, period, for shop/employees + employee, category,
>   paid by; newest/oldest), Petty cash (period, exact amount; newest/oldest/highest/lowest; 20 per page), Activity (invoice
>   no., period, who, what; newest/oldest). Result count + "Clear filters". Backend: optional `sort` on
>   GET /shops/{id}/petty-cash and GET /reports/activities (default newest, old callers unchanged).
> - [x] 2026-11-20 Owner invoice Track: uses the item's assignmentHistory (step times were missing) and, for a delivered
>   order, adds Delivered (at deliveredAt) as the last, finished step. Track popup shows Delivered as done; ORDER_DELIVERED
>   labelled "Delivered" with a tick (`humanize`, `ItemStatusBadge`).
> - [x] 2026-11-20 Owner branch invoice page: payment receipts listed (GET /invoices/{id}/payment-receipts?shopId=) with
>   number, amount, method, date, received by and a PDF button each (?shopId on the PDF); falls back to the invoice's payment
>   history if the list cannot load.
> - [x] 2026-11-20 Owner branch page: People role pills = shop owner `RoleBadge` colours; Deliveries status badges with icons
>   (late / ready / in production, as on the shop owner side); Deliveries paged 20 per page in Overdue, Due today and
>   Delivered today; Orders, Receipts and Deliveries rows open the new read-only invoice page
>   `/owner/branches/:shopId/invoices/:invoiceId` (`BranchInvoicePage.jsx`: stage / payment badges, invoice + receipt PDF,
>   items with status + Track step timings, customer, payment card with GST split and payments; back returns to the tab).
> - [x] 2026-11-20 Owner branches page redesigned (design.md): search (branch, shop owner, phone, address; filters as you
>   type, "x of y" count, empty state with Clear) + flat box cards 3 per row (2 on tablets, 1 on phones): neutral initials
>   badge, name + shop owner, phone/address chips, four soft-stone figure tiles with chart-colour dots, petty cash and staff
>   pills, dark "Open" pill. No colour accents, no green summary band (`.owb-*`).
> - [x] 2026-11-20 Owner overview: cash-flow chart uses the same grouping as the shop owner dashboard (`shared/lib/trend.js`:
>   7 days / week per day / month per week / custom by length / overall per month or year), chart-table toggle, full width;
>   "Payment mix" card removed.
> - [x] 2026-11-20 Shop owner dashboard: KPI figures back to the default colour. "Items by production step" bars coloured
>   by phase (`BarList` row `color`): cutting coral, tailoring plum, finishing amber, item completed green.
> - [x] 2026-11-20 Tabs (every screen): no stray scrollbar. Cause: tabs pulled 1px down (margin -1px) inside an
>   overflow-x:auto bar made it scroll vertically. Bar line is now an inset shadow, overflow-y hidden, scrollbar hidden (swipe
>   still works on phones). Same for the invoice wizard item tabs (`.so-item-tabs`).
> - [x] 2026-11-20 Deliveries KPIs: Overdue number red, Ready green, Still in production amber (only when above 0;
>   `.so-kpi-danger/success/warning` on `.so-kpi-btn`).
> - [x] 2026-11-20 Employees role pills: one colour per role with a dot (`RoleBadge`, `.so-role-*`): shop owner near-black,
>   tailor master deep green, tailor plum, shop assistant coral (table and detail popup).
> - [x] 2026-11-20 Employees page: "Steps completed" KPI removed (3 KPIs left). "With them now" unchanged (tailors and
>   shop assistants only; accepted items are not with a master).
> - [x] 2026-11-20 Expenses page: removed the "By category" and "By payment method" breakdown cards (KPIs, tabs, filters
>   and the list stay).
> - [x] 2026-11-20 Expenses page: Petty cash balance KPI uses the new `KpiCard variant="feature"` (design.md dark feature
>   band: deep green #003c33, white text, "Right now" chip, coral "Empty" chip at ₹0 or below; `.so-kpi-feature`).
> - [x] 2026-11-20 All KPI amounts (shop owner dashboard, expenses, employees, deliveries; owner overview, expenses,
>   customers) use new `formatAmount`: rupee sign + Indian commas, paise only when present (e.g. ₹1,23,456.5). Counts keep
>   `formatNumber` commas. Dollar icons replaced with `MdCurrencyRupee`. `formatMoneyShort` left as the raw value on purpose.
> - [x] 2026-11-20 Shop owner dashboard layout: cash-flow chart left, "Items by production step" right (bar list, as before;
>   cards keep their own height); bottom row of 3: payment methods | expenses | today's deliveries.
> - [x] 2026-11-20 Shop owner dashboard "Items by production step" no longer follows the date filter: it shows items right now,
>   Order accepted … Order completed, only on invoices not delivered yet (`GET /reports/production`, total.stages).
> - [x] 2026-11-20 Shop owner dashboard "Today's deliveries" card shows only the counts (due, ready, overdue, delivered) and
>   the balance to collect; the order list was removed so the card never grows. "View all" opens the deliveries page.
> - [x] 2026-11-20 Shop owner dashboard cash-flow chart follows the filter (`shared/lib/trend.js`): Today = last 7 days per
>   day; This week = Mon–Sun per day; This month = per week (1–7, 8–14 …); Custom = per day (≤31 days), per week (≤92 days),
>   per month, per year (>24 months); Overall = per month, per year past 24 months. Weeks/years summed in the browser;
>   `TrendChart` shows the bucket label/title (Owner overview unchanged).
> - [x] 2026-11-19 Sign-in screens v4: login, forgot and reset are exactly the window height (`.lg` 100dvh, no page scroll;
>   only the card scrolls on very short windows; the drawing scales to the space left). One drawing per slide via `art`:
>   studio (`AuthIllustration`), due today (`AuthArtDeliveries`), workflow (`AuthArtWorkflow`), forgot (`AuthArtForgot`),
>   reset (`AuthArtReset`); the drawing cross-fades with the headline.
> - [x] 2026-11-19 Sign-in screens v3: shared `LoginPages/AuthLayout.jsx` (white card + green panel) with a drawn tailoring-studio
>   illustration (`AuthIllustration.jsx`, inline SVG: dress form, blouse + saree, tape, pattern sheet, scissors, spools) instead
>   of the app previews; Forgot password and Reset password redesigned on it (same API calls; forgot now shows send errors;
>   reset has a live rules checklist + "Request a new link"). Old `ForgotPassword.css` / `ResetPassword.css` unused.
> - [x] 2026-11-19 Login page v2 (reference layout): white sign-in card (icons in fields, green "Sign in") + deep-green panel with
>   app previews (sales + payment mix, today's deliveries, production) and a rotating headline; checked in Edge at 1440 / 1280 / 500 px.
> - [x] 2026-11-19 Login page redesigned to design.md (`LoginPages/Login.jsx` + new `Login.css`): white canvas, display
>   headline, thin-border inputs, near-black pill "Sign in", underlined "Forgot password?", deep-green image band (hidden
>   below 1024px). Sign-in logic unchanged. Now unused: `LoginPages/PosterPage.jsx`, `LoginPages/Signup.css` (delete with OK).

## 6. Public customer download pages (no login)

- [x] `InvoiceDownload.jsx:67` `GET /public/download-invoice/{token}` (**base64 JSON**) →
      `GET /public/documents/invoices/{token}` (**PDF blob**). Use `responseType: 'blob'` and remove the base64 decode. **Done (section 6)
      via the new `fetchPublicPdf(kind, token)` in `utils/downloadPdf.js`. The manual download file name was `invoice_<token>.pdf`; it's now `Invoice.pdf`.**
- [x] `ReceiptDownload.jsx:69` → `GET /public/documents/receipts/{token}` (blob). **Done.**
- [x] **New page + route** `/download-payment-receipt/:token` → `GET /public/documents/payment-receipts/{token}`.
      The server sends this link to customers after each payment. **Done: `ReceiptDownload` takes `kind` / `label` / `filePrefix` props, and the route
      renders it with `kind="payment-receipts"` (no duplicate page).**
- [x] These pages must not attach the Authorization header or trigger the logout interceptor on 401/404
      (use plain `axios` or skip the interceptors for `/public/`). **Done: `fetchPublicPdf` uses plain axios. An invalid / expired link returns 400
      `{message}` (live check: "This download link is not valid"), and the page now shows that message instead of a generic one.**
- [x] Route names in `App.jsx` must match the links the server sends: `/download-invoice/:token`,
      `/download-receipt/:token`, `/download-payment-receipt/:token`. **Checked against `CustomerDocumentLinkService` (its outdated comment about the
      legacy PublicController is fixed too). Links already sent keep working: the token format is unchanged (token parity verified during the backend
      refactoring).**

---

## 7. Cleanup (dead code)

- [x] Delete `Pages/LoginSignup/axios.js` (points to `localhost:8090`), `LoginSignup/Login.jsx`, `LoginSignup/Signup.jsx`.
- [x] Delete `Dashboard/Apply/Apply.jsx` and `Components/NavBar/UploadResume.jsx` (job-portal leftovers).
- [ ] ~~Delete `Pages/OrderStatusUpdates.jsx` (see section 0), then remove the `@stomp/stompjs` and `sockjs-client` deps.~~ **Keep (your decision).**
- [x] Remove unused routes from `App.jsx` after the deletions.
- [x] **2026-11-17 cleanup (approved list):** 83 files deleted, found with an import-graph reachability check from `main.jsx`:
      40 old SHOP_OWNER screens replaced by the new pages (`PaymentInvoice` tab bar, `ReceiptGeneration`, `InvoiceGenerator`,
      `PaymentTracking`, `ViewEditOrder` table + edit modal, `DeletedInvoice`, `Reports`, `TailorAssign`, all of
      `FinancialManagement/`) and 43 files nothing imported (job-portal / `LoginSignup` leftovers, duplicate `NavBar/`,
      unused dashboard pieces, three unused `MeasurementModal` copies, `PrivateRoute`, `RestrictedRoute`, `ErrorBoundary`,
      `ComingSoon`). Kept: `OrderStatusUpdates.jsx` (WebSocket), `Deliveries.jsx`, `OrderStatusModal`, all assets.
      Old `/shopOwner` route block removed from `App.jsx`; Deliveries "open invoice" → `/shopOwner/invoices/{id}`; old
      "Financial Management" button removed from `NavBar.jsx`; "Old invoice screens" sidebar link removed.
- [~] `npm run build` and `npm run lint` with no errors. *(Build passes. Lint: 150 → 89 problems; the rest are in older files
      not touched by this work — new `ShopOwner/` and `api/` code is lint-clean.)*
- [x] Grep check: no old paths left.
      `grep -rnE "/owner/|/shopOwner/|/tailor-master/|/tailor/|/shop-assistant/|signin|signup|forgotPassword|all-invoice|update-payment|/receipt/download|/deleted/|/dashboard/financial|/financials/shop|/employee/activities" src`

---

## 8. Backend-side decisions still open that affect the frontend

- [ ] **Payment receipt number format**: proposed `{invoiceId}-P{n}` (not yet implemented; the current format is
      `{PREFIX}-PR-{dd-MM-yyyy}-{00001}`). Display code should treat it as an opaque string.
- [ ] **Rounding**: if the client picks the "round-off line" option, the invoice screens get an extra
      `roundOff` row.
- [ ] Repo hygiene: `SarithaFashion_Frontend/` is inside the backend repo and not git-ignored. Decide whether
      it gets its own repo or is committed here (and git-ignore `node_modules`/`dist` either way).
