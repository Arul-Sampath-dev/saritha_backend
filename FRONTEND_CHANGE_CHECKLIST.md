# Frontend Change Checklist: old React app → new modular backend

Scope: `SarithaFashion_Frontend/src`. Every live API call in the old app is listed below with
its file and line and the endpoint that replaces it. Paths are relative to the axios
`baseURL` (`${VITE_API_BASE_URL}/v1/api`). Request and response bodies are documented in
`FRONTEND_MIGRATION_GUIDE.md` and `MODULES_API_DOCUMENTATION.md` (and Swagger at `/swagger-ui.html`).

Recommended order: **0 → 1 → 2 → 3 (shop-owner core) → 4 (production) → 5 (owner) → 6 (public) → 7 (cleanup)**.
After each section, sign in as that role and click through the screens.

---

## 0. Global / shared (do first)

- [ ] **`.env.development`**: `VITE_API_BASE_URL=http://localhost:8080` → `http://localhost:5000` (8080 is Jenkins now).
- [ ] **`.env.production`**: confirm the Cloud Run URL is still the deployed backend.
- [ ] **CORS**: the backend allows `http://localhost:5173/` and `http://localhost:5174/` *with a trailing slash*.
  Browsers send the origin without one, so check that dev requests are not blocked. If they are, fix this in the
  backend (`WebSecurityConfig.corsConfigurationSource`), not in the frontend.
- [ ] **`Pages/LoginPages/axios.js`** (the only axios instance in use):
  - [ ] `refreshTokenIfNeeded` posts to `${VITE_API_BASE_URL}/auth/refresh-token`. Change it to
        `POST /auth/token/refresh` on the instance (the token goes in the Authorization header; the new token is in
        `accessToken`). Either wire it in or delete it, because it is currently unused.
  - [ ] Response interceptor: the backend now returns **401** for missing/expired/revoked tokens. Log out on
        any 401 instead of only when the message contains "expired"/"JWT". Revoked tokens (sign-out, password
        reset) send a different message.
  - [ ] Add a **403** handler ("You don't have access") and a **409** handler. 409 means someone else changed the
        invoice first: reload it and retry.
  - [ ] Read error text from the new error body (`message`, plus per-field `errors` on 400 validation failures)
        instead of plain-string bodies.
  - [ ] Optional: add `POST /auth/sign-out` to `handleLogout` so the token is revoked on the server.
- [ ] **Stored session**: after login, store `userId`, `name`, `roles`, `shopId`, `shopName` (see section 1). Replace
      every `localStorage.getItem('userId' | 'shopId' | 'shopName')` reader if the key names change.
- [ ] **Shared helpers**: add a `downloadPdf(url, filename)` helper (axios `responseType: 'blob'` →
      object URL). Every PDF in the new backend comes from a `GET .../pdf` endpoint, not base64 or a POST.
- [ ] **Pagination**: list endpoints use `pageNumber` (0-based) and `pageSize` (default 20) and return
      `{ content, pageNumber, pageSize, totalElements, totalPages, lastPage }`. Remove client-side
      "fetch all, slice locally" code where the screen pages.
- [ ] **Dates**: filters are `from` / `to` (yyyy-MM-dd). `sortBy` + `sortOrder` are replaced by one `sort` param (`newest` / `oldest` on invoices; an unknown value returns 400).
- [ ] **Quantities** may now be decimals (e.g. `2.5`). Number inputs need `step="0.01"`, `min="0.01"`, max 1000.
      Don't `parseInt` them.
- [ ] **Amounts** are rounded to 2 decimals by the server. Show them with `toFixed(2)`/`toLocaleString`,
      and don't recompute GST on the client except as a preview.
- [ ] **`paymentStatus`** values are exactly `Dues`, `Partial`, `Paid`. Check any badge/colour mapping that uses
      `PENDING` / `Pending` for payments.
- [ ] **WebSocket** (`Pages/OrderStatusUpdates.jsx`): it hardcodes `http://localhost:8080/ws`, nothing imports it,
      and the backend never publishes to `/topic/order-updates`. **Delete it** (or keep it only if live updates are
      planned, and use `VITE_API_BASE_URL`).

---

## 1. Authentication

| ✓ | File:line | Old call | New call | Notes |
|---|---|---|---|---|
| [ ] | `LoginPages/Login.jsx:73` | `POST /auth/signin` | `POST /auth/sign-in` `{email, password}` | Response: `userId`, `name`, `email`, `roles[]`, `shopId`, `shopName`, `accessToken`, `expiresIn`. Read the token from `res.data.accessToken` (the header still works). `res.data.id` → `res.data.userId`. |
| [ ] | `LoginPages/Login.jsx` (role redirect) | roles from JWT/`res.data.roles` | `res.data.roles` | Role names: `OWNER`, `SHOP_OWNER`, `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT`. Check the redirect switch and `App.jsx` route guards use exactly these. |
| [ ] | `LoginPages/Signup.jsx:78` | `POST /auth/signup` | `POST /auth/users` | **OWNER only** now. Public sign-up is gone, so remove the public `/signup` route or put it behind the OWNER guard. |
| [ ] | `.../AddEmployees.jsx:125` | `POST /auth/signup` | `POST /auth/users` (OWNER) | A SHOP_OWNER cannot create users. Hide the button for SHOP_OWNER or have the OWNER create staff. |
| [ ] | `LoginPages/ForgotPassword.jsx:37` | `GET /forgotPassword/verifyMail/{email}` | `POST /auth/password-reset/code` `{email}` | Always returns 200 (doesn't reveal whether the email exists). |
| [ ] | `LoginPages/ResetPassword.jsx:69` | `POST /forgotPassword/changePassword` | `POST /auth/password-reset` `{email, code, newPassword, confirmPassword}` | The form needs a code field and a confirm field. |
| [ ] | Profile pages (tailor / TM / SA) | `/tailor/profile`, `/tailor-master/profile`, `/shop-assistant/profile` | `GET /auth/me` | One endpoint for every role. |

---

## 2. Shops, employees, petty cash (OWNER + SHOP_OWNER)

| ✓ | File:line | Old call | New call | Notes |
|---|---|---|---|---|
| [ ] | `AddEmployees.jsx:40`, `AddPettyCash.jsx:26` | `GET /shops/shop-name` | `GET /shops/names` | OWNER only. |
| [ ] | `AddPettyCash.jsx:39` | **GET** `/owner/shop/add-petty-cash/{amount}/{shopId}` | **POST** `/shops/{shopId}/petty-cash` `{amount}` | The method changes from GET to POST. |
| [ ] | `AddShop.jsx:113` | `POST /owner/shop` | `POST /shops` | |
| [ ] | `Shop.jsx:24` | `GET /owner/shop` | `GET /shops/summaries` (cards) or `GET /reports/shops/overview` (with totals) | |
| [ ] | `EditEmployeeModal.jsx:178` | `PUT /owner/shop/update-owner-details` | `PUT /shops/{shopId}/owner` | |
| [ ] | `TotalEmployees.jsx:48` | `GET /owner/employee/{shopId}/all-employee` | `GET /shops/{shopId}/employees` | |
| [ ] | `TotalEmployees.jsx:76` | `GET /owner/employee/find-user` | `GET /employees/unassigned` | Lists staff not assigned to any shop. |
| [ ] | `TotalEmployees.jsx:208` | `DELETE /owner/employee/delete-employee/{userId}` | `DELETE /shops/{shopId}/employees/{userId}/assignment` | Removes the user from the shop (does not delete the account). |
| [ ] | `EmployeeExpense.jsx:356` | `GET /shops/all-employees` | `GET /shops/{myShopId}/employees` | `myShopId` comes from the stored session. |
| [ ] | `PettyCash.jsx:52` | `GET /owner/petty-cash/{shopId}/all-pettyCash` | `GET /shops/{shopId}/petty-cash` | |
| [ ] | `PettyCashManagement.jsx:26` | `GET /shops/petty-cash` | `GET /reports/financials/summary` (balance) + `GET /shops/{myShopId}/petty-cash` (history) | |

---

## 3. Shop owner: invoices, payments, receipts

### 3a. Create invoice: `ReceiptGeneration.jsx`
- [ ] `:1134` `POST /receipt/download/invoice` (multipart, **returned a PDF**) →
      `POST /invoices` multipart with part **`invoiceJson`** (JSON blob) and item images named
      **`item_{index}_{anything}`**. It **returns JSON** (`invoiceId`, totals, items with `itemUniqueId`).
- [ ] Then download the PDF with `GET /invoices/{invoiceId}/pdf` (blob).
- [ ] Item `itemQuantity` accepts decimals (0.01–1000, 2 dp); the price is per 1 unit.
- [ ] `expectedDeliveryDate`: send `yyyy-MM-dd`.
- [ ] Customer WhatsApp/email notifications are sent by the server; remove any client-side send step.
- [ ] Warmup `:834`, `:1192` `GET /pdf-service/warmup`: **unchanged**.

### 3b. Payments / delivery: `InvoiceGenerator.jsx`
- [ ] `:44`, `:327` warmup: unchanged.
- [ ] `:75` `GET /invoices/{id}` → `GET /invoices/{invoiceId}/account` for the payment screen (totals, payments, receipt
      status), or `GET /invoices/{invoiceId}` for full details.
- [ ] `:154` `PUT /invoices/{id}/update-payment` → split into:
  - [ ] `POST /invoices/{invoiceId}/payments` `{amount, paymentMethod, transactionId?}`. The response includes the new
        **payment receipt** (`paymentReceipt.paymentReceiptNo`); show a "Download payment receipt" button →
        `GET /payment-receipts/{no}/pdf`.
  - [ ] `POST /invoices/{invoiceId}/discount` `{amount, reason}` when a discount is given.
- [ ] `:290` `POST /receipt/download/receipt` → `POST /invoices/{invoiceId}/receipt` (creates the delivery receipt,
      only when fully paid) → then `GET /receipts/{receiptNo}/pdf`.
- [ ] Handle **409** (the invoice changed in another tab): reload the account and let the user retry.
- [ ] New (optional): payment history list `GET /invoices/{invoiceId}/payment-receipts`.

### 3c. Payment tracking: `PaymentTracking.jsx`
- [ ] `:402` `GET /shopOwner/invoices/search-invoice` → `GET /invoices?search=...`
- [ ] `:529` `GET /invoices/all-invoice?sortOrder&startDate&endDate` →
      `GET /invoices?from=&to=&paymentStatus=&sort=&pageNumber=&pageSize=`
- [ ] `:700` export (`"Dues": item.balanceDue`): check that the field names match `InvoiceSummaryResponse`.

### 3d. View / edit orders: `ViewEditOrderTable.jsx`, `EditOrderModel.jsx`
- [ ] `ViewEditOrderTable.jsx:370` `GET /invoices/all-invoice-with-all-details` → `GET /invoices?details=true&...`
- [ ] `ViewEditOrderTable.jsx:400` search → `GET /invoices?search=`
- [ ] `ViewEditOrderTable.jsx:629` `DELETE /shopOwner/invoices/{shopId}/delete-invoice/{id}` →
      `DELETE /invoices/{invoiceId}?reason=...`. **A reason is required**: add a reason prompt (sweetalert input).
- [ ] `EditOrderModel.jsx:1011` `PUT /invoices/update-items` → `PATCH /invoices/{invoiceId}` multipart part
      **`editJson`** with the **full item list** (existing items keep their `itemUniqueId`; new ones have none;
      omitted ones are removed). Optional `customer` object to edit the customer details.
- [ ] Editing is blocked after the delivery receipt, except for customer-only changes. Show the server's
      400 message.
- [ ] `EditOrderModel.jsx:1036` `POST /invoices/send-invoice`: **this endpoint doesn't exist** (in either backend). Remove it,
      or replace it with "download PDF" (`GET /invoices/{id}/pdf`).

### 3e. Deleted invoices: `DeletedInvoice.jsx`
- [ ] `:41` `GET /deleted/invoices/all-deleted-invoices` → `GET /reports/deleted-invoices?search=&from=&to=&pageNumber=`

### 3f. Expenses (SHOP_OWNER)
- [ ] `ShopExpense.jsx:73` `POST /expenses/add` → `POST /expenses`
- [ ] `EmployeeExpense.jsx:120` `POST /expenses/add-employee-expense` → `POST /employee-expenses`
- [ ] `RevenueTracking.jsx:59` `GET /expenses/all` → `GET /expenses?from=&to=&type=&paymentMethod=&sort=&pageNumber=`
- [ ] `RevenueTracking.jsx:26` `DELETE /expenses/{id}`: same path (`/expenses/{expenseId}`), unchanged.

### 3g. Special-category / TM view on the shop-owner side: `TailorAssign.jsx`
- [ ] `:45` `GET /tailor-master/view-order` → `GET /invoices/{invoiceId}` (or the production queue item)
- [ ] `:62` `GET /invoices/special-category/all-invoice` → `GET /invoices?special=true`
- [ ] `:186`, `:226` `order.status === 'Pending'`: check against the new item workflow status names.

### 3h. New screen (client requirement)
- [ ] **Today's deliveries dashboard**: `GET /reports/deliveries?date=yyyy-MM-dd` (overdue / due today / upcoming).

---

## 4. Production staff (TAILOR_MASTER, TAILOR, SHOP_ASSISTANT)

The new model: each person gets **`GET /items/queue`**, a list of the items assigned to them, and every item
carries **`availableActions`**. Buttons should be driven by `availableActions`, not hardcoded per role.
Actions:
- `PATCH /invoices/{invoiceId}/items/{itemUniqueId}/status` `{status}`
- `.../tailor` `{tailorId}`
- `.../shop-assistant` `{shopAssistantId}`

Status rules are strict: only the TAILOR_MASTER cuts; only the assigned tailor stitches; only the assigned shop
assistant does the finishing steps.

| ✓ | File:line | Old call | New call |
|---|---|---|---|
| [ ] | `TailorMasterDashboard.jsx:116` | `/tailor-master/profile` | `GET /auth/me` |
| [ ] | `TailorMasterDashboard.jsx:138`, `TailorOrderDetails.jsx:357`, `OrderDetails.jsx:70`, `MyOrdersTM.jsx:162` | `/tailor-master/view-order` | `GET /invoices/{invoiceId}/items/{itemUniqueId}` (or the item from the queue) |
| [ ] | `TailorMasterDashboard.jsx:167` | `/tailor-master/shop/all-invoice` | `GET /items/queue` |
| [ ] | `MyOrdersTM.jsx:63` | `/tailor-master/shop/order-accepted-status/all-invoice` | `GET /items/queue?status=...` |
| [ ] | `MyOrdersTM.jsx:320`, `OrderStatusUpdater.jsx:23` | `PUT /tailor-master/my-order/update-status` | `PATCH /invoices/{id}/items/{itemId}/status` |
| [ ] | `AssignTailor.jsx:25`, `AdminTailorDashboard.jsx:26` | `/tailor-master/all-tailor` | `GET /items/assignees/tailors` |
| [ ] | `AssignTailor.jsx:93` | `POST /tailor-master/assign-tailor` | `PATCH /invoices/{id}/items/{itemId}/tailor` |
| [ ] | `TailorDashboard.jsx:1481` | `/tailor/orders` | `GET /items/queue` |
| [ ] | `TailorDashboard.jsx:1531` | `/tailor/profile` | `GET /auth/me` |
| [ ] | `TailorDashboard.jsx:1548` | `/tailor/view-order` | `GET /invoices/{id}/items/{itemId}` |
| [ ] | `TailorDashboard.jsx:1625` | `PUT /tailor/orders/update-status` | `PATCH .../status` |
| [ ] | `AssignShopAssistant.jsx:21`, `AdminTailorDashboard.jsx:27` | `/tailor/all-shopAssistant` | `GET /items/assignees/shop-assistants` |
| [ ] | `AssignShopAssistant.jsx:88` | `POST /tailor/assign-shopAssistant` | `PATCH .../shop-assistant` |
| [ ] | `ShopAssistance.jsx:759` | `/shop-assistant/profile` | `GET /auth/me` |
| [ ] | `ShopAssistance.jsx:783` | `/shop-assistant/view-order` | `GET /invoices/{id}/items/{itemId}` |
| [ ] | `ShopAssistance.jsx:819` | `/shop-assistant/orders` | `GET /items/queue` |
| [ ] | `ShopAssistance.jsx:908` | `PUT /shop-assistant/orders/update-status` | `PATCH .../status` |
| [ ] | `TailorMasterDashboard.jsx:658` | `order.status === "Pending"` | Use the new status names from the queue response |
| [ ] | `Analytic.jsx:66`, `TailorAnalyticsDashboard.jsx:56` | `/employee/activities/employee/{userId}/analytics` | staff: `GET /reports/my-activity`; owner: `GET /reports/activities/by-employee?employeeId=` / `reports/activities/daily` |

- [ ] Show the 403/400 message when an action isn't allowed (e.g. a tailor trying to cut).
- [ ] Remove the large commented-out blocks in `TailorMasterDashboard.jsx` (`:1418`, `:1857`, `:1895`, `:2300`) while you're there.

---

## 5. Platform OWNER dashboard

| ✓ | File:line | Old call | New call |
|---|---|---|---|
| [ ] | `FinancialOverviewChart.jsx:10`, `RevenueExpensePieChart.jsx:12` | `/dashboard/financial-summary/all` | `GET /reports/financials/summary` (all shops when no `shopId`) |
| [ ] | `MonthlyProfitChart.jsx:10` | `/financials/shop/{shopId}` | `GET /reports/financials/daily?shopId=&from=&to=` (group by month on the client) |
| [ ] | `CompletedOrders.jsx:51,71` | `/owner/order/{shopId}/all-completedOrders` | `GET /invoices?shopId=&status=delivered` |
| [ ] | `PendingOrders.jsx:52,72` | `/owner/order/{shopId}/all-pendingOrders` | `GET /invoices?shopId=&status=pending` |
| [ ] | `ExpensesEntries.jsx:36`, `OwnerExpenseModal.jsx:38` | `/shops/view-expenses/byPaymentMethod` | `GET /expenses?shopId=&paymentMethod=` |
| [ ] | `TotalInvoices.jsx:56` | `/owner/invoice/{shopId}/all-invoice` | `GET /invoices?shopId=` |
| [ ] | `TotalInvoices.jsx:171` | `POST /owner/invoice/invoice-download` | `GET /invoices/{id}/pdf?shopId=` (blob) |
| [ ] | `TotalReceipts.jsx:57` | `/owner/receipt/{shopId}/all-receipt` | `GET /receipts?shopId=` |
| [ ] | `TotalReceipts.jsx:172` | `POST /owner/receipt/receipt-download` | `GET /receipts/{receiptNo}/pdf?shopId=` (blob) |
| [ ] | `CompletedOrders.jsx:151` | `orderStatus \|\| 'Pending'` | Use the invoice `status` field from the new response |

The `status` filter takes `pending`, `delivered` or `all` (delivered = delivery receipt issued).

---

## 6. Public customer download pages (no login)

- [ ] `InvoiceDownload.jsx:67` `GET /public/download-invoice/{token}` (**base64 JSON**) →
      `GET /public/documents/invoices/{token}` (**PDF blob**). Use `responseType: 'blob'` and remove the base64 decode.
- [ ] `ReceiptDownload.jsx:69` → `GET /public/documents/receipts/{token}` (blob).
- [ ] **New page + route** `/download-payment-receipt/:token` → `GET /public/documents/payment-receipts/{token}`.
      The server sends this link to customers after each payment.
- [ ] These pages must not attach the Authorization header or trigger the logout interceptor on 401/404
      (use plain `axios` or skip the interceptors for `/public/`).
- [ ] Route names in `App.jsx` must match the links the server sends: `/download-invoice/:token`,
      `/download-receipt/:token`, `/download-payment-receipt/:token`.

---

## 7. Cleanup (dead code)

- [ ] Delete `Pages/LoginSignup/axios.js` (points to `localhost:8090`), `LoginSignup/Login.jsx`, `LoginSignup/Signup.jsx`.
- [ ] Delete `Dashboard/Apply/Apply.jsx` and `Components/NavBar/UploadResume.jsx` (job-portal leftovers).
- [ ] Delete `Pages/OrderStatusUpdates.jsx` (see section 0), then remove the `@stomp/stompjs` and `sockjs-client` deps.
- [ ] Remove unused routes from `App.jsx` after the deletions.
- [ ] `npm run build` and `npm run lint` with no errors.
- [ ] Grep check: no old paths left.
      `grep -rnE "/owner/|/shopOwner/|/tailor-master/|/tailor/|/shop-assistant/|signin|signup|forgotPassword|all-invoice|update-payment|/receipt/download|/deleted/|/dashboard/financial|/financials/shop|/employee/activities" src`

---

## 8. Backend-side decisions still open that affect the frontend

- [ ] **Payment receipt number format**: proposed `{invoiceId}-P{n}` (not yet implemented; the current format is
      `{PREFIX}-PR-{dd-MM-yyyy}-{00001}`). Display code should treat it as an opaque string.
- [ ] **Rounding**: if the client picks the "round-off line" option, the invoice screens get an extra
      `roundOff` row.
- [ ] Repo hygiene: `SarithaFashion_Frontend/` is inside the backend repo and not git-ignored. Decide whether
      it gets its own repo or is committed here (and git-ignore `node_modules`/`dist` either way).
