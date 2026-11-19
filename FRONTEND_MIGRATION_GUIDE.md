# Frontend Migration Guide — Old API → New Module API

**For:** developers of the existing Saritha Fashions frontend  
**Backend branch:** `opt/shop-management` · **Date:** 2026-11-13  
> **Status: the old endpoints have been REMOVED from the backend** (2026-11-13). Every old call in the frontend now fails until it is switched to the new endpoint below. Only `GET /v1/api/pdf-service/warmup` kept its exact behaviour. **Careful:** some new URLs look like old ones but mean something else — see [1.11](#111-new-urls-that-look-like-old-ones).

**Full API reference:** [`MODULES_API_DOCUMENTATION.md`](MODULES_API_DOCUMENTATION.md) · **Swagger UI:** `{API_BASE}/swagger-ui.html`

This guide tells you, screen by screen, which old endpoint to replace, what to call instead, and **exactly what changes in your request and response handling**. Read [Part 1](#part-1--global-changes-read-first) first: it covers changes that affect every call (errors, paging, dates, auth). Part 2 is the endpoint-by-endpoint map with before/after code.

---

## Contents

- [How to migrate safely](#how-to-migrate-safely)
- [Part 1 — Global changes (read first)](#part-1--global-changes-read-first)
  - [1.1 Base paths](#11-base-paths)
  - [1.2 Authentication header and token lifetime](#12-authentication-header-and-token-lifetime)
  - [1.3 Error responses](#13-error-responses)
  - [1.4 Pagination, sorting and date filters](#14-pagination-sorting-and-date-filters)
  - [1.5 Shop scope (`shopId`)](#15-shop-scope-shopid)
  - [1.6 Values that changed spelling](#16-values-that-changed-spelling)
  - [1.7 Dates and times](#17-dates-and-times)
  - [1.8 PDF downloads](#18-pdf-downloads)
  - [1.9 HTTP methods and CORS](#19-http-methods-and-cors)
  - [1.10 Concurrency (`409 Conflict`)](#110-concurrency-409-conflict)
  - [1.11 New URLs that look like old ones](#111-new-urls-that-look-like-old-ones)
- [Part 2 — Endpoint map with before/after](#part-2--endpoint-map-with-beforeafter)
  - [2.1 Sign-in, session, password reset](#21-sign-in-session-password-reset)
  - [2.2 Shops and petty cash](#22-shops-and-petty-cash)
  - [2.3 Invoices](#23-invoices)
  - [2.4 Payments, discount, receipt (delivery)](#24-payments-discount-receipt-delivery)
  - [2.5 Production workflow (tailor master / tailor / shop assistant)](#25-production-workflow-tailor-master--tailor--shop-assistant)
  - [2.6 Expenses](#26-expenses)
  - [2.7 Employees and user accounts](#27-employees-and-user-accounts)
  - [2.8 Dashboards, financials, activity, deleted invoices](#28-dashboards-financials-activity-deleted-invoices)
  - [2.9 Customer download pages (public)](#29-customer-download-pages-public)
- [Part 3 — Old endpoints without a new equivalent](#part-3--old-endpoints-without-a-new-equivalent)
- [Part 4 — Behaviour changes users will notice](#part-4--behaviour-changes-users-will-notice)
- [Part 5 — Migration checklist](#part-5--migration-checklist)

---

## How to migrate safely

1. **The old endpoints no longer exist on the backend.** If you must migrate gradually, run the frontend against a backend build from before the removal (commit `71b1a5f`) for the screens you have not switched yet; both use the same database, so data written by one is read correctly by the other.
2. **Migrate in this order** (each step only depends on the ones before it):
   1. Error handling + API client changes ([Part 1](#part-1--global-changes-read-first)) — do this first, once, in your HTTP layer.
   2. Sign-in / session / password reset.
   3. Read-only screens (invoice lists, expenses lists, reports) — lowest risk.
   4. Invoice create / edit / delete, payments, receipts.
   5. Production workflow screens (tailor master, tailor, shop assistant).
   6. Employees, shops, expenses writes.
   7. Customer download pages — **last**, because links already sent to customers point at them (old links keep working with the new endpoint too, see [2.9](#29-customer-download-pages-public)).
3. **Do not mix old and new calls within one screen's write flow** (only relevant if you temporarily run an old backend build): the old endpoints don't have the new safety checks.
4. Search the frontend for every old path (see the [checklist](#part-5--migration-checklist)) — none of them may remain.

---

## Part 1 — Global changes (read first)

### 1.1 Base paths

| Area | Old base path(s) | New base path |
|---|---|---|
| Sign-in / tokens / password | `/v1/api/auth`, `/v1/api/forgotPassword` | `/v1/api/auth` |
| Shops, petty cash | `/v1/api/shops`, `/v1/api/owner/shop`, `/v1/api/owner/petty-cash` | `/v1/api/shops` |
| Invoices | `/v1/api/invoices`, `/v1/api/shopOwner/invoices`, `/v1/api/owner/invoice`, `/v1/api/owner/order` | `/v1/api/invoices` |
| Payments, receipts | `/v1/api/receipt`, `/v1/api/owner/receipt`, `PUT /v1/api/invoices/{id}/update-payment` | `/v1/api/invoices/{invoiceId}/account`, `/payments`, `/discount`, `/receipt`; `/v1/api/receipts` |
| Production workflow | `/v1/api/tailor-master`, `/v1/api/tailor`, `/v1/api/shop-assistant` | `/v1/api/items/...`, `/v1/api/invoices/{invoiceId}/items/{itemUniqueId}/...` |
| Expenses | `/v1/api/expenses`, `/v1/api/shops/view-expenses` | `/v1/api/expenses`, `/v1/api/employee-expenses` |
| Employees | `/employees`, `/v1/api/owner/employee`, `/v1/api/shops/all-employees` | `/v1/api/shops/{shopId}/employees`, `/v1/api/employees/...` |
| Dashboards / reports | `/v1/api/dashboard`, `/v1/api/financials`, `/v1/api/employee/activities`, `/v1/api/deleted/invoices` | `/v1/api/reports` |
| Customer downloads | `/v1/api/public/download-invoice|receipt/{token}` | `/v1/api/public/documents/{invoices|receipts}/{token}` |

### 1.2 Authentication header and token lifetime

- Same header as before: `Authorization: Bearer <token>`.
- **New:** the sign-in response body now contains the token (`accessToken`) and its lifetime in seconds (`expiresIn`). You no longer have to read the `Authorization` response header (it is still sent).
- **New:** `POST /v1/api/auth/sign-out` revokes **all** of the user's tokens (every device). Resetting the password does the same. After that, old tokens get `401`.
- **New:** a token whose user was removed from a shop, deleted, or whose email changed is revoked → `401`. Treat **every `401` as "session ended"**: clear the stored token and go to the sign-in page.
- Refresh (`POST /v1/api/auth/token/refresh`) only works with a **still valid** token. Refresh shortly before `expiresIn` runs out; an expired token cannot be refreshed — the user must sign in again.

### 1.3 Error responses

There are now four error shapes. Handle them centrally in your HTTP client.

| Status | Body | Meaning / what to show |
|---|---|---|
| `400` | `{"message": "Invoice not found", "status": false}` | Business rule. Show `message` as-is (they are written for users). |
| `400` | `{"customer.phone": "customer.phone must be a 10-digit mobile number", "items[0].itemQuantity": "..."}` | **Field validation.** Keys are field paths; map them to form fields. There is **no** `message` key. |
| `401` | `{"error": "unauthorized", "message": "..."}` or `{"status": 401, "error": "Unauthorized", "message": "Invalid or expired JWT", ...}` | Not signed in / token invalid, expired or revoked → sign-in page. |
| `403` | `{"message": "You are not authorized to access this resource", "status": false}` | Signed in, but this role / shop may not do it. The message is always this generic text. |
| `409` | `{"message": "... Reload it and try again.", "status": false}` | **New.** Someone changed the record at the same time. Reload and let the user retry ([1.10](#110-concurrency-409-conflict)). |
| `429` | `{"message": "...", "status": false}` | **New.** Too many attempts (sign-in rate limit, wrong reset codes). |

**Changed from old behaviour:**

| Old | New |
|---|---|
| Sign-in unknown email → `404 "Email not found"`; wrong password → `401 "Invalid password"` | Both → `401 "Invalid email or password"` (don't reveal which accounts exist) |
| Many "not found" cases → `404` with **empty body** | `400 {"message": "... not found", "status": false}` |
| Many errors → `500` with plain text `"Error: ..."` | Proper `400` / `409` JSON |
| Wrong role on some old endpoints → `200` with other shops' data | `403`, or "not found" for another shop's records |

Suggested normaliser (TypeScript):

```ts
export type ApiError =
  | { kind: 'fields'; status: 400; fields: Record<string, string> }
  | { kind: 'message'; status: number; message: string }
  | { kind: 'auth'; status: 401 }
  | { kind: 'network'; message: string };

export async function toApiError(res: Response): Promise<ApiError> {
  if (res.status === 401) return { kind: 'auth', status: 401 };
  let body: any = null;
  try { body = await res.json(); } catch { /* empty or non-JSON body */ }
  if (res.status === 400 && body && typeof body === 'object' && !('message' in body)) {
    return { kind: 'fields', status: 400, fields: body };          // field validation map
  }
  return { kind: 'message', status: res.status, message: body?.message ?? `Request failed (${res.status})` };
}
```

### 1.4 Pagination, sorting and date filters

Paged responses keep the **same shape** as before:

```json
{ "content": [], "pageNumber": 0, "pageSize": 20, "totalElements": 57, "totalPages": 3, "lastPage": false }
```

What changed in the **query parameters**:

| Old | New |
|---|---|
| `pageSize` default `10`, no limit | default **`20`**, must be **1–100** (else `400`) |
| `sortBy=<field>&sortOrder=asc|desc` | `sort=newest|oldest` (default `newest`) — no free field sorting |
| `startDate`, `endDate` (sometimes `fromDate` / `toDate`) | **`from`**, **`to`** — `yyyy-MM-dd`, **both inclusive**, Indian time |
| `term`, `searchTerm`, `invoiceId`, `customerName` (one per screen) | one **`search`** parameter per list (searches several fields, literal text — `(` or `*` no longer break it) |
| Empty page sometimes `400`/`404` | Always `200` with `content: []` |

### 1.5 Shop scope (`shopId`)

The old API often took `shopId` in the **path** (e.g. `/owner/invoice/{shopId}/all-invoice`). The new rule is the same everywhere:

- **`SHOP_OWNER`** — never send a shop id; the backend always uses the shop owner's own shop (a `shopId` they send is ignored).
- **Platform `OWNER`** — send `?shopId=...` as a **query parameter** on reads. Missing → `400 "shopId is required"` (on reports it means "all shops" instead, see [2.8](#28-dashboards-financials-activity-deleted-invoices)).

Exceptions that keep `shopId` in the path: shops (`/v1/api/shops/{shopId}/...`, incl. petty cash and the owner account) and the shop's employees (`/v1/api/shops/{shopId}/employees/...`).

Records of another shop are reported as **not found** (`400 "Invoice not found"`), not `403`.

### 1.6 Values that changed spelling

| Field | Old values you may compare against | New values |
|---|---|---|
| `paymentStatus` | `PAID`, `PARTIAL`, sometimes `Pending` | **`Paid`**, **`Partial`**, **`Dues`** (exact casing) |
| User id field | `id` (sign-in), `username` = display name | **`userId`**, **`name`** |
| Roles when creating users | short names (`tailor`, `shopOwner`) | `TAILOR`, `SHOP_OWNER`, ... (short names still accepted) |

Item statuses (`ORDER_ACCEPTED`, `CUTTING`, `TAILOR_ASSIGNED`, `STITCHING`, `SHOP_ASSISTANT_ASSIGNED`, `HEMMING`, `IRONING`, `ORDER_COMPLETED`) and invoice `orderStatus` (`ORDER_ACCEPTED` / `ORDER_DELIVERED`) are unchanged.

### 1.7 Dates and times

- Timestamps (e.g. `invoiceDate`, `paidAt`, `deletedAt`) are ISO strings in **UTC**, e.g. `2026-11-12T05:00:00.000+00:00`. Display them in **Asia/Kolkata**.
- Day filters (`from`, `to`) are interpreted as **Indian** days.
- `expectedDeliveryDate` is free text as entered (`"2026-11-25"`), not a timestamp — as before.

### 1.8 PDF downloads

| Old | New |
|---|---|
| Invoice creation returned the **PDF** (or a wrapped byte array) | Creation returns **JSON** with `pdfUrl`; fetch the PDF separately |
| `POST .../invoice-download` with the whole invoice in the body | `GET` with the number in the path, e.g. `GET /v1/api/invoices/{invoiceId}/pdf` |
| Public invoice download returned **base64 text** | Returns the **PDF bytes** (`application/pdf`) |

PDF endpoints are authenticated (except the public customer ones), so you **cannot** use a plain `<a href>`. Fetch with the token and open a blob:

```ts
export async function downloadPdf(path: string, fileName: string) {
  const res = await fetch(API_BASE + path, { headers: { Authorization: `Bearer ${getToken()}` } });
  if (!res.ok) throw await toApiError(res);
  const url = URL.createObjectURL(await res.blob());
  const a = Object.assign(document.createElement('a'), { href: url, download: fileName });
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
// downloadPdf(invoice.pdfUrl, `${invoice.invoiceId}.pdf`)
```

> The browser **cannot read `Content-Disposition`** (CORS only exposes `Authorization` and `Content-Type`), so name the file yourself (`{invoiceId}.pdf`, `{receiptNo}.pdf`).

PDF generation calls an external converter and can take **several seconds** — show a spinner and disable the button while it runs.

### 1.9 HTTP methods and CORS

New endpoints use `PATCH` (edits) and `DELETE`; CORS now allows `GET, POST, PUT, PATCH, DELETE, OPTIONS`. Allowed request headers are still only `Authorization` and `Content-Type` — don't send custom headers.

### 1.10 Concurrency (`409 Conflict`)

Payments, edits, assignments and status changes are protected against two people changing the same record at the same moment. The loser gets **`409`** and **nothing is saved**. Recommended UX: show the message, reload the record, keep the user's input where possible, let them retry.

```ts
if (err.kind === 'message' && err.status === 409) {
  toast.warning(err.message);
  await reloadCurrentRecord();
}
```


### 1.11 New URLs that look like old ones

The new API reuses some old prefixes (`/v1/api/invoices`, `/v1/api/shops`, `/v1/api/expenses`, `/v1/api/auth`). A few old calls therefore still hit **a** route, but not the one you expect:

| Old call | What it hits now | What to do |
|---|---|---|
| `GET /v1/api/invoices/{id}` with the **database id** | New `GET /v1/api/invoices/{invoiceId}` expects the **invoice number** (`SARI-12-11-2026-00042`) → `400 "Invoice not found"` | Pass `invoiceId` (the number), not `id` |
| `GET /v1/api/invoices/all-invoice`, `/shop/{shopId}`, `/special-category/...` | Treated as an invoice number → `400 "Invoice not found"` (not 404) | Use `GET /v1/api/invoices?...` filters |
| `GET /v1/api/shops` (shop owner) | Now the platform owner's full shop list → `403` for a shop owner | Shop owner: use `/v1/api/reports/financials/summary` etc. |
| `GET /v1/api/shops/{id}`, `PUT /v1/api/shops/{id}` | Same meaning, but **OWNER only** now | — |
| `DELETE /v1/api/expenses/{id}` | Same meaning; answers `204` (no body) instead of the text `"Expense Entry Deleted"` | Don't parse a body |
| `POST /v1/api/auth/...` (old `signin`, `signup`, `refresh-token`) | `404` — the new names are `sign-in`, `users`, `token/refresh` | Switch per [2.1](#21-sign-in-session-password-reset) |
| `DELETE /v1/api/shops/{id}` | `405` / `403` — shops are not deleted any more | Remove the action |

---

## Part 2 — Endpoint map with before/after

Legend: **Access** = roles allowed. Responses are summarised; full schemas are in [`MODULES_API_DOCUMENTATION.md`](MODULES_API_DOCUMENTATION.md).

### 2.1 Sign-in, session, password reset

| Old | New | Access |
|---|---|---|
| `POST /v1/api/auth/signin` | `POST /v1/api/auth/sign-in` | public |
| `POST /v1/api/auth/refresh-token` | `POST /v1/api/auth/token/refresh` | valid token |
| — (just deleted the token locally) | `POST /v1/api/auth/sign-out` | valid token |
| `GET /v1/api/user/username`, `GET /v1/api/tailor-master/profile`, `/tailor/profile`, `/shop-assistant/profile` | `GET /v1/api/auth/me` | valid token |
| `POST /v1/api/auth/signup` | `POST /v1/api/auth/users` | `OWNER` |
| `POST /v1/api/forgotPassword/verifyMail/{email}` | `POST /v1/api/auth/password-reset/code` | public |
| `POST /v1/api/forgotPassword/verifyOtp` + `POST /v1/api/forgotPassword/changePassword` | `POST /v1/api/auth/password-reset` (**one** call) | public |

#### Sign-in — before / after

```ts
// BEFORE
const res = await fetch('/v1/api/auth/signin', { method: 'POST', headers: json, body: JSON.stringify({ email, password }) });
const token = res.headers.get('Authorization')?.replace('Bearer ', '');
const u = await res.json();          // { id, username, message, shopName, shopId, roles }
saveSession({ token, userId: u.id, name: u.username, roles: u.roles, shopId: u.shopId });

// AFTER
const res = await fetch('/v1/api/auth/sign-in', { method: 'POST', headers: json, body: JSON.stringify({ email, password }) });
if (!res.ok) throw await toApiError(res);   // 401 "Invalid email or password", 429 rate limit, 400 field map
const u = await res.json();
// { userId, name, email, roles, shopId?, shopName?, accessToken, tokenType: "Bearer", expiresIn }
saveSession({ token: u.accessToken, userId: u.userId, name: u.name, roles: u.roles, shopId: u.shopId,
              expiresAt: Date.now() + u.expiresIn * 1000 });
```

- `shopId` / `shopName` are **absent** for the platform owner.
- Rate limit: 20 sign-in requests per minute per IP → `429`.

#### Refresh

| | Old | New |
|---|---|---|
| Body of `200` | plain text `"Token refreshed successfully"` | JSON — same shape as sign-in (new `accessToken`, current `roles`) |
| Errors | `400 "No token provided"`, `401 "Token is expired"` | `401` for missing / expired / invalid / revoked |

#### Password reset (3 steps → 2 steps)

```text
BEFORE  1. POST /forgotPassword/verifyMail/{email}
        2. POST /forgotPassword/verifyOtp      { email, otp }
        3. POST /forgotPassword/changePassword { email, otp, password, repeatPassword }

AFTER   1. POST /authentication/password-reset/code  { "email": "..." }              → 202 (always the same message)
        2. POST /authentication/password-reset       { "email", "code", "newPassword", "confirmPassword" } → 200
```

- Step 1 **always** answers `202` with the same message, even for unknown emails — don't show "email not found".
- The code is **6 digits**, valid **10 minutes**, usable once; a new code may be requested once per minute.
- New password rule: **8–100 characters, at least one letter and one digit**.
- 5 wrong codes → `429 "Too many wrong codes..."`.
- Validation keys: `code`, `newPassword`, `passwordConfirmed` (mismatch).
- After success the user is signed out everywhere → send them to sign-in.

#### Create user (owner)

| | Old `POST /auth/signup` | New `POST /authentication/users` |
|---|---|---|
| Shop field | `assignedShops` | **`shopId`** (required for every role except `OWNER`) |
| Password | optional (silent default!) | **required**, 8–100, letter + digit |
| Phone | optional (silent `0000000000`) | optional, 10 digits if sent |
| Roles | short names | `SHOP_OWNER`, `TAILOR`, ... (old short names like `tailor`, `shopOwner` still accepted) |
| Success | `200 {"message":"User created successfully"}` | **`201`** `{ userId, name, email, roles, shopId }` |
| Duplicate email | `400 "Error: Email is already in use!"` | **`409`** `"An account with email ... already exists"` |

### 2.2 Shops and petty cash

| Old | New | Access | Notes |
|---|---|---|---|
| `GET /v1/api/owner/shop`, `GET /v1/api/shops` (dashboard rows) | `GET /v1/api/reports/shops/overview` | `OWNER` | Real expense totals (old rows always showed `0`) — see [2.8](#28-dashboards-financials-activity-deleted-invoices) |
| `GET /v1/api/shops` (plain list) | `GET /v1/api/shops/summaries` | `OWNER` | id, name, contact, `pettyCash`, `totalExpense` |
| `GET /v1/api/shops/shop-name` | `GET /v1/api/shops/names` | `OWNER` | `[{ shopId, shopName }]` |
| `GET /v1/api/shops/{id}` | `GET /v1/api/shops/{shopId}` | `OWNER` | |
| `POST /v1/api/owner/shop` | `POST /v1/api/shops` | `OWNER` | see below |
| `PUT /v1/api/shops/{id}` | `PUT /v1/api/shops/{shopId}` | `OWNER` | partial update — send only changed fields |
| `DELETE /v1/api/shops/{id}` | — | — | shops are not deleted any more |
| `GET /v1/api/owner/shop/add-petty-cash/{amount}/{shopId}` | `POST /v1/api/shops/{shopId}/petty-cash` body `{ "amount": 5000 }` | `OWNER`, `SHOP_OWNER` (own shop) | **GET → POST**; response has the new balance |
| `GET /v1/api/owner/petty-cash/{shopId}/all-pettyCash`, `/search-by-amount/{term}` | `GET /v1/api/shops/{shopId}/petty-cash?from=&to=&amount=` | `OWNER`, `SHOP_OWNER` (own shop) | newest first, paged |
| `GET /v1/api/shops/petty-cash` (shop owner's balance) | `GET /v1/api/reports/financials/summary` → `pettyCash.currentBalance` | `SHOP_OWNER` | also returned by every expense write as `pettyCashBalance` |

**Create shop — request changes**

```json
{
  "shopName": "Saritha Fashions - Anna Nagar",
  "ownerName": "Saritha R",
  "ownerEmailId": "owner@example.com",
  "shopAddress": "12, 2nd Avenue, Anna Nagar, Chennai",
  "ownerNumber": "9876543210",
  "gstNumber": "33ABCDE1234F1Z5",
  "password": "StrongPass2026",
  "salary": 25000
}
```

- Same field names as before, but now **validated first**: `shopName`, `ownerName`, `ownerEmailId`, `password` required; password 8–100 with a letter and a digit; `ownerNumber` 10 digits. Field errors come back as a field map.
- Success is **`201`** (was `200`); the shop's `employeeIds` already contains the new owner.
- Taken email → `400 {"message": "Email is already in use: owner@example.com"}` (old text was `"Error: Email is already in use!"` — don't match on the exact text).

### 2.3 Invoices

| Old | New | Access |
|---|---|---|
| `POST /v1/api/invoices/generate-invoice` (multipart) | `POST /v1/api/invoices` (multipart **or** JSON) | `SHOP_OWNER` |
| `POST /v1/api/invoices/generate`, `POST /v1/api/receipt/download/invoice` | same new endpoint, then `GET .../invoices/{invoiceId}/pdf` | `SHOP_OWNER` |
| `GET /v1/api/invoices/{id}`, `GET /v1/api/shopOwner/invoices/search-invoice?invoiceId=` | `GET /v1/api/invoices/{invoiceId}` | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/invoices/all-invoice` | `GET /v1/api/invoices` | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/invoices/all-invoice-with-all-details` | `GET .../invoices?details=true` | |
| `GET /v1/api/invoices/special-category/all-invoice` | `GET .../invoices?special=true` | |
| `GET /v1/api/invoices/shop/{shopId}`, `GET /v1/api/owner/invoice/{shopId}/all-invoice` | `GET .../invoices?shopId=...` (owner) | |
| `GET /v1/api/owner/order/{shopId}/all-pendingOrders`, `/search` | `GET .../invoices?status=pending&search=...` | |
| `GET /v1/api/owner/order/{shopId}/all-completedOrders`, `/completed-order/search` | `GET .../invoices?status=delivered&search=...` | |
| `GET /v1/api/owner/invoice/{shopId}/download/{invoiceId}` + `POST /v1/api/owner/invoice/invoice-download` | `GET /v1/api/invoices/{invoiceId}/pdf?shopId=...` | `OWNER`, `SHOP_OWNER` |
| `PUT /v1/api/invoices/update-items` (multipart `request` + `images` + `imageFileNames`) | `PATCH /v1/api/invoices/{invoiceId}` (multipart `editJson` + `images`, or JSON) | `SHOP_OWNER` |
| `DELETE /v1/api/shopOwner/invoices{shopId}/delete-invoice/{invoiceId}` | `DELETE /v1/api/invoices/{invoiceId}?reason=...` | `SHOP_OWNER` |
| `GET /v1/api/dashboard/all-invoices` | `GET .../invoices` (paged) | |

#### Create invoice — before / after

```ts
// BEFORE: response was a PDF (byte array) or "Error: ..." text with 500
const form = new FormData();
form.append('invoiceJson', JSON.stringify(invoice));
images.forEach(f => form.append('images', f));
const pdfBytes = await (await fetch('/v1/api/invoices/generate-invoice', { method: 'POST', body: form, headers: auth })).blob();

// AFTER
const form = new FormData();
form.append('invoiceJson', JSON.stringify(invoice));
// IMPORTANT: each image file NAME must be item_{index}_{anything}.{ext}, index = position in invoice.items
itemImages.forEach(({ index, file }) => form.append('images', file, `item_${index}_${file.name}`));
const res = await fetch('/v1/api/invoices', { method: 'POST', body: form, headers: auth });
if (!res.ok) throw await toApiError(res);
const created = await res.json();      // 201: the invoice object (see below)
await downloadPdf(created.pdfUrl, `${created.invoiceId}.pdf`);   // only if the user wants the PDF now
```

- **No images?** You may send plain JSON instead: `Content-Type: application/json`, body = the invoice JSON.
- Do **not** set `Content-Type` yourself for `FormData` (the browser adds the boundary).
- The invoice JSON keeps the **old field names** (`customer`, `customerState`, `items`, `discount`, `advancePaid`, `paymentMethod`, `transactionId`, `expectedDeliveryDate`, `orderDate`, `notes`, `isBackDate`, `backDate`). Extra old fields are ignored — the server computes ids, statuses, GST and totals.
- **Stricter validation (new 400s):**
  - `customer.phone` must be 10 digits; `customer.alternatePhone` too if sent.
  - `totalAmount`, if you send it, must equal Σ price × quantity, or you get `{"totalAmount": "totalAmount 1800.0 does not match the items total 1850.0"}`. Simplest: don't send it.
  - `discount` ≤ total; `advancePaid` ≤ total **with GST**.
  - Image names must follow `item_{index}_...`, be images, ≤ 10 MB, one per item. Old code silently ignored bad files — now it's a `400` naming the file.
  - `backDate` required (`yyyy-MM-dd`, not in the future) when `isBackDate: true`.
  - **`itemQuantity` may be decimal** (e.g. `2.5`, max 2 decimals, > 0, ≤ 1000); `itemPrice` is the price of one unit. The old API silently cut decimals (2.5 → 2, 0.5 → 0). Display quantities as numbers, not integers.
  - **`expectedDeliveryDate` must be a real date** (`yyyy-MM-dd`; `25-11-2026` / `25/11/2026` also accepted) and is stored as `yyyy-MM-dd` — old code accepted any text. Use a date picker. It powers the new delivery dashboard (`GET /v1/api/reports/deliveries`).
- `paymentStatus` in the response is `Dues` / `Partial` / `Paid`.
- The customer gets WhatsApp / email in the background; `notifications: { whatsApp: "QUEUED"|"SKIPPED", email: ... }` tells you what was queued. A notification failure never fails the request.

Response (abridged):

```json
{
  "id": "6672b1c0d2e3f4a5b6c7d8e9",
  "invoiceId": "SARI-12-11-2026-00042",
  "customer": { "id": "...", "customerId": "CUS-8F3K2Q1ZXA", "name": "Priya K", "phone": "9876543210", "email": null, "newCustomer": true },
  "items": [ { "itemUniqueId": "Blouse_1762935000000_0", "itemName": "Blouse", "itemQuantity": 2, "itemPrice": 850.0,
               "itemOrderStatus": "ORDER_ACCEPTED", "imageUrl": null, "measurements": { "Chest": "36" } } ],
  "totalAmount": 1850.0, "discount": 100.0, "taxableAmount": 1750.0,
  "cgstAmount": 43.75, "sgstAmount": 43.75, "igstAmount": 0.0, "totalAmountWithGST": 1837.5,
  "advancePaid": 1000.0, "balanceDue": 837.5, "paymentStatus": "Partial", "orderStatus": "ORDER_ACCEPTED",
  "receiptId": null,
  "pdfUrl": "/v1/api/invoices/SARI-12-11-2026-00042/pdf",
  "notifications": { "whatsApp": "QUEUED", "email": "SKIPPED" }
}
```

#### Invoice lists — before / after

```text
BEFORE  GET /v1/api/invoices/all-invoice?pageNumber=0&pageSize=10&sortBy=invoiceDate&sortOrder=desc&startDate=2026-11-01&endDate=2026-11-30
        GET /v1/api/owner/order/{shopId}/all-pendingOrders?term=priya
        GET /v1/api/invoices/special-category/all-invoice

AFTER   GET /v1/api/invoices?pageNumber=0&pageSize=20&sort=newest&from=2026-11-01&to=2026-11-30
        GET /v1/api/invoices?status=pending&search=priya            (+ &shopId=... for the owner)
        GET /v1/api/invoices?special=true
```

All filters combine: `status` (`pending|delivered|all`), `from`, `to`, `search` (invoice number, customer name or phone), `paymentStatus` (`Dues|Partial|Paid`), `special`, `details`, `sort`, `pageNumber`, `pageSize`.

Rows are **summary rows** by default (no items array):

```json
{ "id": "...", "invoiceId": "SARI-12-11-2026-00042", "invoiceDate": "...", "expectedDeliveryDate": "2026-11-25",
  "customerId": "CUS-8F3K2Q1ZXA", "customerName": "Priya K", "customerPhone": "9876543210",
  "itemCount": 3, "itemNames": ["Blouse", "Chudidhar", "Saree Fall"], "completedItemCount": 1,
  "totalAmountWithGST": 3150.0, "advancePaid": 1500.0, "balanceDue": 1650.0,
  "paymentStatus": "Partial", "orderStatus": "ORDER_ACCEPTED", "pdfUrl": "..." }
```

Add `details=true` if a screen really needs the full invoice per row (heavier).

#### Edit invoice (replaces `update-items`)

`PATCH /v1/api/invoices/{invoiceId}` — multipart (`editJson` part + optional `images`) or JSON. Every field optional:

```json
{
  "expectedDeliveryDate": "2026-11-28",
  "notes": "Customer will collect on Saturday",
  "discount": 150,
  "items": [
    { "itemUniqueId": "Blouse_1762935000000_0", "itemName": "Blouse", "itemQuantity": 3, "itemPrice": 850 },
    { "itemName": "Saree Pico", "itemQuantity": 1, "itemPrice": 120 }
  ]
}
```

Key differences from `update-items`:
- `items` is the **complete list after the edit**: existing items keep their `itemUniqueId`; items without it are **added**; existing items you leave out are **removed**.
- Images: same `item_{index}_...` naming (index into the `items` you send); no `imageFileNames` part any more. `removeImage: true` on an item removes its picture.
- Only allowed while the invoice has **no receipt** and is not delivered (check `receiptId === null` to show/hide the Edit button).
- Items already in production (assigned, or past their start status) **cannot be changed or removed** → `400` naming the item. Show production items read-only in the edit form.
- **Customer details** (name, phone, email, alternate phone, address) are corrected with a `customer` object in the same PATCH, like the old Edit Order. A customer-only correction also works after the receipt. Customer state, invoice date and payments are **not** editable here.
- `409` if a payment / production update happened meanwhile — reload and retry.

#### Delete invoice

```text
BEFORE  DELETE /v1/api/shopOwner/invoices{shopId}/delete-invoice/{invoiceId}   → 200 plain text (customer name)
AFTER   DELETE /v1/api/invoices/{invoiceId}?reason=Duplicate%20invoice
        → 200 { "invoiceId", "archiveId", "reason", "deletedAt", "message": "Invoice deleted" }
```

- Only while there is **no receipt** and the invoice is not delivered (old: no check).
- Ask the user for a reason (optional, ≤ 300 chars) — it appears in the deleted-invoices report.
- Bill numbers stay in sequence as before: deleting the **latest** invoice gives its number to the next invoice.

### 2.4 Payments, discount, receipt (delivery)

| Old | New | Access |
|---|---|---|
| `PUT /v1/api/invoices/{id}/update-payment?paymentAmount=&paymentMethod=&discount=` | **two** calls: `POST /v1/api/invoices/{invoiceId}/payments` and (optional) `POST .../discount` | `SHOP_OWNER` |
| — | `GET /v1/api/invoices/{invoiceId}/account` (totals + payment list) | `SHOP_OWNER` |
| `PATCH /v1/api/invoices/{id}/status?status=`, `PATCH /v1/api/receipt/{id}/status?status=` | — (payment status is calculated by the server; no manual status) | |
| `POST /v1/api/receipt/generate`, `POST /v1/api/receipt/download/receipt` | `POST /v1/api/invoices/{invoiceId}/receipt` (no body), then `GET .../receipts/{receiptNo}/pdf` | `SHOP_OWNER` |
| `GET /v1/api/receipt/{id}` | `GET /v1/api/receipts/{receiptNo}` | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/receipt/shop/{shopId}`, `/all-receipt`, `GET /v1/api/owner/receipt/{shopId}/all-receipt` | `GET /v1/api/receipts?shopId=&from=&to=&search=` (shopId: owner only; old `startDate`/`endDate`/`term` → `from`/`to`/`search`; rows have `amountPaid`, not `advancePaid`/`balanceDue`) | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/owner/receipt/{shopId}/download/{receiptId}` + `POST /v1/api/owner/receipt/receipt-download` | `GET /v1/api/receipts/{receiptNo}/pdf` | `OWNER`, `SHOP_OWNER` |

#### Collecting a payment — before / after

```ts
// BEFORE: one call did payment + discount, values in the query string
await fetch(`/v1/api/invoices/${id}/update-payment?paymentAmount=650&paymentMethod=UPI&discount=0`, { method: 'PUT', headers: auth });

// AFTER
await post(`/v1/api/invoices/${invoiceId}/payments`,
           { amount: 650, paymentMethod: 'UPI', transactionId: 'UPI-4589123' });       // 201 → invoice account
// discount is a separate, one-time action:
await post(`/v1/api/invoices/${invoiceId}/discount`,
           { amount: 100, reason: 'Delay in delivery' });                               // 200 → account + creditNoteId
```

Rules the UI should reflect:
- Payment `amount` must be > 0 and **≤ balance due** (no over-payment). Fully paid → `400 "This invoice is already fully paid"`.
- **One discount per invoice**, ≤ the pre-GST `totalAmount`. GST is recalculated; a credit note is created. If the customer already paid more than the new total → `400` (refunds not supported).
- Both are blocked once the receipt exists.
- **New: payment receipts.** Every payment now creates a numbered payment receipt (`paymentReceipt` in the payment response; list `GET /v1/api/invoices/{id}/payment-receipts`; PDF `GET /v1/api/payment-receipts/{no}/pdf`). The customer gets a link to a **new public page** `/download-payment-receipt/:token`, which must call `GET /v1/api/public/documents/payment-receipts/{token}` (PDF).

Account object (returned by `GET .../account`, payments and discount):

```json
{ "invoiceId": "SARI-12-11-2026-00042", "totalAmount": 3050.0, "discount": 50.0, "taxableAmount": 3000.0,
  "cgstAmount": 75.0, "sgstAmount": 75.0, "igstAmount": 0.0, "totalAmountWithGST": 3150.0,
  "advancePaid": 2500.0, "balanceDue": 650.0, "paymentStatus": "Partial",
  "payments": [ { "amount": 1000.0, "paidVia": "UPI", "paidAt": "2026-11-13T05:30:00.000+00:00", "transactionId": "UPI-4589123" } ] }
```

#### Generating the receipt (= delivering the order)

```text
POST /v1/api/invoices/{invoiceId}/receipt      (no body)
```

- Requires **balance 0** and **every item `ORDER_COMPLETED`**; otherwise `400` with a clear message ("Collect the remaining balance of 650.0 ..." / "All items must be ORDER_COMPLETED ... Not finished: Blouse (IRONING)"). Disable the button until both are true.
- `201` = created; `200` with `created: false` = the receipt already existed (safe to retry).
- The response contains `receiptNo` and `pdfUrl`; the customer is notified automatically.
- After this, the invoice is `ORDER_DELIVERED`, `receiptId` is set, and the invoice can no longer be edited, paid, discounted or deleted.

### 2.5 Production workflow (tailor master / tailor / shop assistant)

All three old role-specific APIs are replaced by **one** item API that tells the UI what the current user may do.

| Old | New |
|---|---|
| `GET /v1/api/tailor-master/shop/all-invoice`, `/shop/order-accepted-status/all-invoice`, `GET /v1/api/tailor/orders`, `GET /v1/api/shop-assistant/orders`, `GET /v1/api/tailor-master/tailor/` | `GET /v1/api/items/queue` (role-aware; optional `status` filter) |
| `GET /v1/api/{tailor-master|tailor|shop-assistant}/view-order/{invoiceId}` | `GET /v1/api/invoices/{invoiceId}/items/{itemUniqueId}` |
| `PUT /v1/api/tailor-master/my-order/update-status`, `PUT /v1/api/tailor/orders/update-status`, `PUT /v1/api/shop-assistant/orders/update-status` | `PATCH /v1/api/invoices/{invoiceId}/items/{itemUniqueId}/status` body `{ "status": "STITCHING" }` |
| `POST /v1/api/tailor-master/assign-tailor` | `PUT .../items/{itemUniqueId}/tailor` body `{ "tailorId": "..." }` |
| `POST /v1/api/tailor/assign-shopAssistant` | `PUT .../items/{itemUniqueId}/shop-assistant` body `{ "shopAssistantId": "..." }` |
| `POST /v1/api/tailor-master/unassign-tailor/{invoiceId}` | `DELETE .../items/{itemUniqueId}/tailor` (**shop owner only**) |
| — | `DELETE .../items/{itemUniqueId}/shop-assistant` (shop owner only) |
| `GET /v1/api/tailor-master/all-tailor` | `GET /v1/api/items/assignees/tailors` |
| `GET /v1/api/tailor/all-shopAssistant` | `GET /v1/api/items/assignees/shop-assistants` |

**Big conceptual change:** the old screens listed **invoices** and you dug into items. The new queue lists **work items** (one row per item), already filtered to what the current role works on, oldest invoice first:

```json
{
  "invoiceId": "SARI-12-11-2026-00042",
  "itemUniqueId": "Blouse_1762935000000_0",
  "itemName": "Blouse", "itemQuantity": 2, "imageUrl": null,
  "status": "TAILOR_ASSIGNED",
  "tailor": { "userId": "665f...", "name": "Lakshmi" },
  "shopAssistant": null,
  "measurements": { "Chest": "36" }, "measurementList": [],
  "customerName": "Priya K", "expectedDeliveryDate": "2026-11-25", "invoiceDate": "...",
  "availableActions": ["STITCHING"],
  "history": [ { "userId": "...", "action": "TAILOR_ASSIGNED", "timestamp": "..." } ]
}
```

**Render buttons from `availableActions`** — don't re-implement the rules in the frontend:

| `availableActions` value | Button | Call |
|---|---|---|
| `CUTTING` / `STITCHING` / `HEMMING` / `IRONING` / `ORDER_COMPLETED` | "Start cutting", "Start stitching", ... | `PATCH .../status` `{ "status": <value> }` |
| `ASSIGN_TAILOR` | "Assign tailor" (dropdown from `/assignees/tailors`) | `PUT .../tailor` |
| `ASSIGN_SHOP_ASSISTANT` | "Assign shop assistant" (dropdown from `/assignees/shop-assistants`) | `PUT .../shop-assistant` |
| `UNASSIGN_TAILOR` | "Remove tailor" | `DELETE .../tailor` |
| `UNASSIGN_SHOP_ASSISTANT` | "Remove shop assistant" | `DELETE .../shop-assistant` |

Every action returns the updated work item — replace the row with it.

**Rule changes users will notice:**
- Statuses move **one step at a time** (no skipping, no going back) — old update endpoints accepted any status.
- **Who assigns the shop assistant changed:** the item's **tailor** or the shop owner (no longer the tailor master).
- Unassigning is **shop owner only**, and a tailor can't be removed once a shop assistant is assigned.
- **Only the responsible person moves a status:** tailor master → cutting; the assigned tailor → stitching; the assigned shop assistant → hemming / ironing / completed. The shop owner and the tailor master can no longer change statuses on someone else's behalf (the old screens let them). Hide those buttons; the owner re-assigns instead.
- Items of delivered invoices are locked.
- `409` when two people act on the same item at once.

### 2.6 Expenses

| Old | New | Access |
|---|---|---|
| `POST /v1/api/expenses/add` | `POST /v1/api/expenses` | `SHOP_OWNER` |
| `POST /v1/api/expenses/add-employee-expense` | `POST /v1/api/employee-expenses` | `SHOP_OWNER` |
| `DELETE /v1/api/expenses/{id}` | `DELETE /v1/api/expenses/{expenseId}` | `SHOP_OWNER` |
| — | `PATCH /v1/api/expenses/{expenseId}` (amount and/or notes) | `SHOP_OWNER` |
| `GET /v1/api/expenses/all`, `GET /v1/api/shops/view-expenses`, `/view-expenses/{shopId}`, `/view-expenses/byPaymentMethod`, `GET /v1/api/expenses/shop/{shopId}` | `GET /v1/api/expenses` | `OWNER` (with `shopId`), `SHOP_OWNER` |
| — | `GET /v1/api/expenses/{expenseId}` | same |

Changes:
- Employee expense: the employee goes in **`employeeUserId`** (must belong to the shop).
- `expenseDate` optional (now if missing); accepts `yyyy-MM-dd` or an ISO date-time.
- Create returns **`201`** with the expense **plus** `pettyCashBalance` and `shopTotalExpense` — update the petty-cash display from it, no extra call needed.
- Cash expenses larger than the petty-cash balance are **rejected** (`400 "You do not have sufficient petty cash..."`). Show the balance next to the amount field.
- Delete returns **`204`** (no body; old: text `"Expense Entry Deleted"`) and puts cash expenses back into petty cash.
- List filters: `from`, `to`, `search` (type or notes), `paymentMethod` (old `byPaymentMethod` endpoint, default was `BANK`), `type=shop|employee`, `employeeUserId`, `sort`.
- In list rows, `null` fields are **omitted** (e.g. shop expenses have no `userId` / `employeeName`) — use optional chaining.

### 2.7 Employees and user accounts

| Old | New | Access |
|---|---|---|
| `GET /v1/api/owner/employee/{shopId}/all-employee`, `GET /v1/api/shops/all-employees`, `GET /employees/shop/{shopId}` | `GET /v1/api/shops/{shopId}/employees` | `OWNER`, `SHOP_OWNER` (own shop) |
| `GET /v1/api/owner/employee/{shopId}/find-user?term=` | same with `?search=` | |
| `DELETE /v1/api/owner/employee/{shopId}/delete-employee/{userId}`, `DELETE /employees/delete/{id}` | `DELETE /v1/api/shops/{shopId}/employees/{userId}` (deletes the account, `204`) | |
| `DELETE /employees/remove/{employeeId}/{shopId}` | `DELETE /v1/api/shops/{shopId}/employees/{userId}/assignment` (keeps the account) | |
| `POST /employees/assign/{employeeId}/{shopId}` | `PUT /v1/api/employees/{userId}/assignment` body `{ "shopId": "..." }` | |
| — | `GET /v1/api/employees/unassigned` | |
| — | `PATCH /v1/api/shops/{shopId}/employees/{userId}` (name, email, phone, salary, address) | |
| `POST /employees/add`, `GET /employees/{id}`, `GET /employees/all` | create accounts with `POST /v1/api/auth/users` (owner); the old `employees` collection is not used | |

Important:
- The old `/employees/*` routes used a **different, unused collection** — their data is not the real staff list. Switch any screen using them.
- Only the **platform owner** can create accounts. A shop owner can assign **unassigned** staff to their shop, edit and remove their staff.
- One shop per user; a shop owner can't take staff from another shop (only the platform owner can move them).
- Removing / deleting an employee releases their in-progress items (they go back one step in production) and signs them out.

### 2.8 Dashboards, financials, activity, deleted invoices

| Old | New | Access |
|---|---|---|
| `GET /v1/api/owner/shop` / `GET /v1/api/shops` (dashboard cards per shop) | `GET /v1/api/reports/shops/overview?from=&to=` | `OWNER` |
| `GET /v1/api/dashboard/financial-summary/shop/{shopId}`, `/financial-summary/all`, `GET /v1/api/financials/shop/{shopId}`, `/profit/shop/{shopId}`, `/profit/all` | `GET /v1/api/reports/financials/summary?shopId=&from=&to=` | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/financials/date/{date}` | `GET /v1/api/reports/financials/daily?from=&to=` (or summary with `from=to=date`) | |
| `POST /v1/api/financials/record` | — **remove from the UI**: figures are now computed from real data | |
| `GET /v1/api/employee/activities/summary` | `GET /v1/api/reports/activities/by-employee`, `GET /v1/api/reports/activities?employeeId=` | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/employee/activities/employee/{id}/analytics` | `GET /v1/api/reports/activities/employee-analytics?employeeId=&role=&from=&to=` (same figures; `startDate`/`endDate` → `from`/`to`; for role `SHOP_OWNER` the status counts are in `itemStatusCounts`) | `OWNER`, `SHOP_OWNER` |
| `GET /v1/api/employee/activities/employee/{id}/daily` | `GET /v1/api/reports/activities/daily?employeeId=` | |
| (role analytics shown to tailors / assistants) | `GET /v1/api/reports/my-activity` | `SHOP_OWNER`, `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT` |
| `GET /v1/api/deleted/invoices/all-deleted-invoices` | `GET /v1/api/reports/deleted-invoices?search=&from=&to=` | `OWNER`, `SHOP_OWNER` |

**The numbers will differ from the old dashboard — that is intended:**
- Old financials read manually entered `financial_records`; old shop cards showed expenses as `0` and counted paid invoices wrongly. New figures are calculated live from invoices, payments, expenses and petty cash.
- `financials/summary` returns structured groups instead of four flat numbers:

| Old flat field | New field |
|---|---|
| `totalIncome` | `collections.total` (money received in the period) — or `sales.invoicedAmount` if you meant "billed" |
| `totalExpenses` | `expenses.total` |
| `totalPettyCash` | `pettyCash.currentBalance` (and `pettyCash.added` in the period) |
| `netProfit` | `netCashFlow` (= collections − expenses) |

- **Reports scope differs from other modules:** the platform owner may **omit** `shopId` to get all shops combined.
- Daily endpoints default to the last 30 days and allow at most 366 days per request.
- Deleted invoices: `from`/`to` filter the **deletion** date (old filtered the invoice date); `search` covers invoice number, customer name and phone (old customer-name filter never matched).
- Activity `type` filter values: `INVOICE_CREATED`, `INVOICE_DELETED`, `ASSIGNED_TO_TAILOR`, `ASSIGNED_TO_ASSISTANT`, `TAILOR_MASTER_CUTTING`, `TAILOR_STITCHING`, `ASSISTANT_HEMMING`, `ASSISTANT_IRONING`, `ORDER_COMPLETED`, `ORDER_DELIVERED`, `USER_DELETED`, ...

### 2.9 Customer download pages (public)

The links sent to customers on WhatsApp/email look like `{website}/download-invoice/{token}` and `{website}/download-receipt/{token}`. **The link format does not change** — only the API your page calls.

| Old call from the page | New call | Response change |
|---|---|---|
| `GET /v1/api/public/download-invoice/{token}` | `GET /v1/api/public/documents/invoices/{token}` | **base64 text → PDF bytes** |
| `GET /v1/api/public/download-receipt/{token}` | `GET /v1/api/public/documents/receipts/{token}` | PDF bytes (same) |

```ts
// BEFORE (invoice page)
const base64 = await (await fetch(`/v1/api/public/download-invoice/${token}`)).text();
const blob = new Blob([Uint8Array.from(atob(base64), c => c.charCodeAt(0))], { type: 'application/pdf' });

// AFTER (both pages)
const res = await fetch(`/v1/api/public/documents/invoices/${token}`);   // no Authorization header
if (!res.ok) { showError((await res.json()).message); return; }         // 400 "This download link is not valid"
const blob = await res.blob();                                           // application/pdf
viewer.src = URL.createObjectURL(blob);
```

- Tokens from links **already sent** work with the new endpoints.
- Errors are now `400` JSON (`"This download link is not valid"`, `"Invoice not found"`, `"Could not generate the invoice PDF, please try again"`) instead of `500` text.
- Responses carry `Cache-Control: no-store`.

---

## Part 3 — Old endpoints without a new equivalent

These old endpoints were removed together with the rest; this is what to do instead:

| Old endpoint | Status |
|---|---|
| `PUT /v1/api/owner/shop/update-owner-details` | Use `PUT /v1/api/shops/{shopId}/owner` (OWNER). Body: `ownerUserId` (optional; was `oldOwnerUserId`), `name`, `phone`, `email`, `password`, `salary`, `reason` (was `newOwnerName`, `newOwnerPhone`, `newOwnerEmail`, `newOwnerPassword`, `newOwnerSalary`, `backupReason`); `shopId` moves to the path. Response `{ shopId, ownerUserId, name, email, phone, salary, changedFields, signedOut, message }` (old: `{ success, message, shopId }`). A new email or password signs the owner out. |
| `GET /v1/api/owner/shop/backup-history/user/{userId}`, `/shop/{shopId}` | **Removed** — remove the screen. (Backups are still stored in the database.) |
| `GET /v1/api/pdf-service/warmup` | **Unchanged** — same path and response. Optional: call it on app start to wake the PDF converter (throttled to once a minute). |
| `POST /v1/api/financials/record` | Not needed any more — remove the screen. |
| `PATCH /v1/api/invoices/{id}/status`, `PATCH /v1/api/receipt/{id}/status` | Removed concept — payment status is calculated. |
| `GET /v1/api/invoices/send-message` | Demo endpoint — remove. |
| `DELETE /v1/api/shops/{id}` | Shops are not deleted. |
| `/v1/api/orders/**`, `/v1/api/notifications/**`, `GET /v1/api/dashboard/all-orders` | **Already removed** on this branch (the separate "orders" feature is gone; production is tracked per invoice item). |

---

## Part 4 — Behaviour changes users will notice

| Area | Change |
|---|---|
| Sign-in | One generic error for wrong email or password; rate-limited. |
| Sign-out | Signs out on **all devices**. |
| Password | New rule: 8+ characters with a letter and a digit (reset, new users, new shops). |
| Invoice numbers | Format and sequence unchanged (`SARI-12-11-2026-00042`); deleting the latest invoice still frees its number. |
| Payment status | Shown as `Paid` / `Partial` / `Dues`; "Paid" now means including GST (old could show Paid with GST still due). |
| Invoice edit / delete | Blocked after the receipt / delivery; production items locked. |
| Production | Strict next-step workflow; the tailor (not tailor master) assigns the shop assistant. |
| Over-payment | Rejected; payments can't exceed the balance. |
| Discount | Once per invoice, with a credit note. |
| Cash expenses | Rejected when petty cash is too low. |
| Dashboards | Real numbers (expenses no longer 0; collections based on actual payments). |
| PDFs | Same layout. Shops without GST number no longer fail; missing values print `-` instead of `null`. |

---

## Part 5 — Migration checklist

**HTTP layer**
- [ ] Central error normaliser (field map / message / 401 / 409 / 429).
- [ ] Any `401` → clear session → sign-in page.
- [ ] `409` → toast + reload record.
- [ ] Paging defaults `pageSize=20`, max 100; replace `sortBy/sortOrder` with `sort`; `startDate/endDate` → `from/to`.
- [ ] Blob-based PDF download helper; name files client-side.
- [ ] Compare `paymentStatus` against `Paid`/`Partial`/`Dues`.

**Auth**
- [ ] Sign-in uses `accessToken`, `userId`, `name`, `expiresIn`.
- [ ] Session restore via `GET /authentication/me`.
- [ ] Refresh before expiry; sign-out calls the API.
- [ ] Two-step password reset with the new password rule.

**Shop owner**
- [ ] Invoice create (image file names `item_{index}_...`), list (one endpoint + filters), detail, edit (full item list), delete (reason), PDF.
- [ ] Account screen: payments, one-time discount, generate receipt (enabled only when paid + all items completed).
- [ ] Production board from `/items/queue` with buttons from `availableActions`.
- [ ] Expenses: create/employee/edit/delete/list; use returned `pettyCashBalance`.
- [ ] Petty cash history; employees screens.
- [ ] Dashboard from `/reports/financials/summary` + `/financials/daily`.

**Platform owner**
- [ ] Shops: overview cards, create (new validation), edit, petty cash add/history.
- [ ] Users: create via `/authentication/users`; employees per shop; unassigned staff; move between shops.
- [ ] Read screens pass `?shopId=`; reports may omit it for all shops.

**Staff (tailor master / tailor / shop assistant)**
- [ ] Single queue screen; buttons from `availableActions`; assignee dropdowns.
- [ ] "My activity" from `/reports/my-activity`.

**Public**
- [ ] Download pages call `/v1/api/public/documents/...` and handle a PDF blob.

**Done**
- [ ] No request goes to an old path. Search the code for these old-only paths: `/auth/signin`, `/auth/signup`, `/auth/refresh-token`, `/forgotPassword`, `/generate-invoice`, `/all-invoice`, `/special-category`, `/update-payment`, `/update-items`, `/receipt/` (singular), `/tailor-master`, `/tailor/`, `/shop-assistant`, `/expenses/add`, `/view-expenses`, `/shops/shop-name`, `/all-employees`, `/owner/`, `/shopOwner/`, `/dashboard`, `/financials`, `/employee/activities`, `/deleted/`, `/employees/add` and other `/employees/` calls without `/v1/api`, `/public/download-`.
- [ ] Re-check every call whose path also exists in the new API with a different meaning ([1.11](#111-new-urls-that-look-like-old-ones)).
- [ ] Test every screen against the current backend (the old controllers are already gone).
