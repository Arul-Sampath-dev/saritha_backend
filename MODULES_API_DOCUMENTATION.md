# Saritha Fashions — Module APIs

API reference for the new modules under `src/main/java/com/saritha/fashions/modules/`.
Each module replaces older controllers. **The legacy controllers and services were removed on 2026-11-13** — only these module endpoints exist now (see [Old → new endpoint map](#old--new-endpoint-map) to find the replacement of an old call).

| Module | Base path | Package |
|---|---|---|
| [Authentication](#1-authentication) | `/v1/api/auth` | `modules.authentication` |
| [Shops](#2-shops) | `/v1/api/shops` | `modules.shop` |
| [Invoices](#3-invoices) | `/v1/api/invoices` | `modules.invoice` |
| [Production (items)](#4-production-items) | `/v1/api/items`, `/v1/api/invoices/{invoiceId}/items` | `modules.production` |
| [Expenses](#5-expenses) | `/v1/api/expenses`, `/v1/api/employee-expenses` | `modules.expense` |
| [Payments & receipts](#6-payments--receipts) | `/v1/api/invoices/{invoiceId}/(account|payments|discount|receipt)`, `/v1/api/receipts` | `modules.receipt` |
| [Employees](#7-employees) | `/v1/api/shops/{shopId}/employees`, `/v1/api/employees` | `modules.employee` |
| [Reports](#8-reports) | `/v1/api/reports` | `modules.report` |
| [Core](#core-module-shared-services--customer-downloads) (shared services + public customer downloads) | `/v1/api/public/documents` | `modules.core` |

Interactive docs: **Swagger UI** at `/swagger-ui.html` (spec at `/v3/api-docs`).

---

## Contents

- [Common conventions](#common-conventions)
- [1. Authentication](#1-authentication)
- [2. Shops](#2-shops)
- [3. Invoices](#3-invoices)
- [4. Production (items)](#4-production-items)
- [5. Expenses](#5-expenses)
- [6. Payments & receipts](#6-payments--receipts)
- [7. Employees](#7-employees)
- [8. Reports](#8-reports)
- [Core module (shared services + customer downloads)](#core-module-shared-services--customer-downloads)
- [Configuration](#configuration)
- [Old → new endpoint map](#old--new-endpoint-map)

---

## Common conventions

### Authentication header

Every endpoint except sign-in and password reset needs a JWT:

```
Authorization: Bearer <access token>
```

The token comes from `POST /v1/api/auth/sign-in` (body `accessToken` and the `Authorization` response header).

> **Access rules.** `WebSecurityConfig` restricts every module route by role (see the table below), and the services additionally check the user's shop, assignments and ownership. Requests without a valid token get `401`; a valid token with the wrong role gets `403`.
>
> | Module | Roles |
> |---|---|
> | Authentication | `sign-in`, `password-reset/**`: public · `POST /users`: `OWNER` · everything else: any signed-in user |
> | Shops | `OWNER` · `GET`/`POST /{shopId}/petty-cash`: `OWNER`, `SHOP_OWNER` (own shop) |
> | Employees | `OWNER`, `SHOP_OWNER` |
> | Invoices | `GET` (list, detail, PDF): `OWNER` (with `shopId`), `SHOP_OWNER` · create / edit / delete: `SHOP_OWNER` |
> | Production (items) | `SHOP_OWNER`, `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT` |
> | Expenses | `GET`: `OWNER` (with `shopId`), `SHOP_OWNER` · writes: `SHOP_OWNER` |
> | Payments & receipts | `GET /receipts/**`: `OWNER`, `SHOP_OWNER` · everything else: `SHOP_OWNER` |
> | Reports (GET only) | `/shops/**`: `OWNER` · `/my-activity`: `SHOP_OWNER`, `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT` · everything else: `OWNER` (one or all shops), `SHOP_OWNER` (own shop) |

### Error response formats

| Situation | Status | Body |
|---|---|---|
| Field validation failed | `400` | Map of field → message, e.g. `{"email": "email must be a valid email address", "password": "password is required"}` |
| Business rule broken (`APIException`) | `400` | `{"message": "Invoice not found", "status": false}` |
| No / invalid token on a route that requires login | `401` | `{"error": "unauthorized", "message": "Full authentication is required to access this resource"}` |
| Expired / malformed / revoked token | `401` | `{"timestamp": "...", "status": 401, "error": "Unauthorized", "message": "Invalid or expired JWT", "path": "..."}` |
| Logged in but wrong role / not your item | `403` | `{"message": "You are not authorized to access this resource", "status": false}` |
| Concurrent change (someone else changed it first) | `409` | `{"message": "... Reload it and try again.", "status": false}` |
| Too many attempts | `429` | `{"message": "...", "status": false}` |

Notes:
- **403 messages are always generic.** The global handler replaces the specific reason (e.g. "Only the shop owner can…") with "You are not authorized to access this resource".
- **"Not found" is `400`**, not 404, matching the rest of the application.
- **Records of another shop** return the same error as a missing one (e.g. "Invoice not found"), so ids of other shops cannot be probed.

### Paginated responses

List endpoints return:

```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 57,
  "totalPages": 3,
  "lastPage": false
}
```

`pageNumber` is 0-based; `pageSize` is 1–100 (default 20).

### Shop scope on read endpoints

Read endpoints that take an optional `shopId` query parameter work the same way everywhere:

- a **`SHOP_OWNER`** always reads their own shop; any `shopId` they send is ignored;
- the platform **`OWNER`** must send `shopId` (`400` `"shopId is required"`; unknown shop → `400` `"Shop Not Found"`).

Date filters `from` / `to` are `yyyy-MM-dd`, both inclusive, in Indian time (IST).

### CORS

Browsers may call `GET`, `POST`, `PUT`, `PATCH`, `DELETE` and `OPTIONS` (`PATCH` was added for the module edit endpoints).

### Concurrency

All writes that change balances, statuses or assignments are **atomic single-document updates guarded by the values just read** (compare-and-set). If two requests change the same record at the same moment, one succeeds and the other gets `409` (or a clear `400`), instead of silently overwriting each other.

### Roles

| Role | Meaning |
|---|---|
| `OWNER` | Platform owner (all shops; belongs to no shop) |
| `SHOP_OWNER` | Owner of one shop |
| `TAILOR_MASTER` | Cuts and assigns tailors |
| `TAILOR` | Stitches assigned items |
| `SHOP_ASSISTANT` | Finishing (hemming, ironing) of assigned items |
| `ACCOUNTANT` | (no module endpoints yet) |

A user's shop is the shop whose `employeeIds` contains the user id.

---

## 1. Authentication

Base path: `/v1/api/auth` · Replaces the legacy `/v1/api/auth/signin`, `/signup`, `/refresh-token` and `/v1/api/forgotPassword/**`

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/sign-in` | public | Email + password → user + access token |
| POST | `/token/refresh` | valid token | New access token |
| POST | `/sign-out` | valid token | Revoke **all** of the user's tokens |
| GET | `/me` | valid token | Current user |
| POST | `/users` | `OWNER` | Create a user account |
| POST | `/password-reset/code` | public | Email a reset code |
| POST | `/password-reset` | public | Set a new password with the code |

Tokens are issued by the existing `JwtUtils` with the user's `tokenVersion`; signing out or resetting the password increments `tokenVersion`, which invalidates every older token.

### POST `/sign-in`

Request:

```json
{ "email": "asha@example.com", "password": "StrongPass123" }
```

| Field | Rules |
|---|---|
| `email` | required, valid email, ≤ 150 chars (case-insensitive) |
| `password` | required, ≤ 100 chars |

Response `200` — also sets header `Authorization: Bearer <token>`:

```json
{
  "userId": "665f0a1b2c3d4e5f6a7b8c9d",
  "name": "Asha",
  "email": "asha@example.com",
  "roles": ["SHOP_OWNER"],
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "shopName": "Saritha Fashions - Anna Nagar",
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 86400
}
```

`shopId` / `shopName` are omitted for users without a shop (e.g. `OWNER`). `expiresIn` is in seconds.

Errors:

| Status | Body |
|---|---|
| 400 | `{"email": "email must be a valid email address", "password": "password is required"}` |
| 401 | `{"message": "Invalid email or password", "status": false}` — same for unknown email and wrong password |
| 429 | Per-IP rate limit (20 requests/minute) |

### POST `/token/refresh`

No body. Send the current (valid, not revoked) token in `Authorization`.

Response `200`: same shape as sign-in, with a new `accessToken` and the user's current roles; header `Authorization: Bearer <new token>`.

Errors: `401` (missing, expired, invalid or revoked token).

### POST `/sign-out`

No body. Revokes every token of the user on every device.

Response `204` (no body). Errors: `401`.

### GET `/me`

Response `200`:

```json
{
  "userId": "665f0a1b2c3d4e5f6a7b8c9d",
  "name": "Asha",
  "email": "asha@example.com",
  "roles": ["SHOP_OWNER"],
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "shopName": "Saritha Fashions - Anna Nagar"
}
```

Errors: `401`.

### POST `/users` (platform owner only)

Request:

```json
{
  "name": "Lakshmi",
  "email": "lakshmi@example.com",
  "password": "Welcome2026",
  "roles": ["TAILOR"],
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "phone": "9876543210",
  "salary": 18000,
  "address": "12, 2nd Avenue, Anna Nagar, Chennai"
}
```

| Field | Rules |
|---|---|
| `name` | required, ≤ 100 |
| `email` | required, valid, ≤ 150; stored lower-case; must be unique |
| `password` | required, 8–100 chars, at least one letter and one digit |
| `roles` | required, ≥ 1. `OWNER`, `SHOP_OWNER`, `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT`, `ACCOUNTANT` — or the old short names `owner`, `shopOwner`, `tailorMaster`, `tailor`, `shopAssistant`, `accountant` (case-insensitive). Unknown names are rejected. |
| `shopId` | required for every role except `OWNER`; must exist |
| `phone` | optional, 10 digits |
| `salary` | optional, 0 – 10,000,000 |
| `address` | optional, ≤ 300 |

Response `201`:

```json
{
  "userId": "6672c3d4e5f6a7b8c9d0e1f2",
  "name": "Lakshmi",
  "email": "lakshmi@example.com",
  "roles": ["TAILOR"],
  "shopId": "665f1c2e9b1e8a3d4c7f0a12"
}
```

The user is added to the shop's `employeeIds` / `employees`.

Errors:

| Status | Example message |
|---|---|
| 400 | `"Unknown role: superadmin. Use one of OWNER, SHOP_OWNER, TAILOR_MASTER, TAILOR, SHOP_ASSISTANT, ACCOUNTANT"` |
| 400 | `"shopId is required for role(s) [TAILOR]"` · `"Shop not found: <id>"` |
| 400 | `{"password": "password must contain at least one letter and one digit"}` |
| 401 | not signed in |
| 403 | not the platform `OWNER` |
| 409 | `"An account with email lakshmi@example.com already exists"` |

### POST `/password-reset/code`

Request:

```json
{ "email": "asha@example.com" }
```

Response `202` — **always the same**, whether or not the email has an account:

```json
{ "message": "If an account exists for this email, a reset code has been sent. It is valid for 10 minutes." }
```

Behaviour: a 6-digit code (valid 10 minutes) is emailed using Brevo template `brevo.template.password-reset` (params `USER_NAME`, `OTP_CODE`, `EMAIL`). Only a hash of the code is stored. A new code can be requested at most once per minute; a new code cancels the previous one.

Errors: `400` invalid email format.

### POST `/password-reset`

Request:

```json
{
  "email": "asha@example.com",
  "code": "482913",
  "newPassword": "NewStrong2026",
  "confirmPassword": "NewStrong2026"
}
```

| Field | Rules |
|---|---|
| `code` | exactly 6 digits |
| `newPassword` | 8–100 chars, at least one letter and one digit |
| `confirmPassword` | must equal `newPassword` (error key `passwordConfirmed`) |

Response `200`:

```json
{ "message": "Password has been changed. Please sign in again." }
```

The code works once, and the user is signed out on every device.

Errors:

| Status | Message |
|---|---|
| 400 | `"Invalid or expired code. Please check the code or request a new one."` |
| 400 | `{"code": "code must be the 6-digit code from the email", "passwordConfirmed": "confirmPassword must match newPassword"}` |
| 429 | `"Too many wrong codes. Please wait a minute and request a new code."` — after 5 wrong codes |

---

## 2. Shops

Base path: `/v1/api/shops` · Replaces shop CRUD in `ShopController` / `OwnerShopController`

| Method | Path | Purpose |
|---|---|---|
| POST | `/` | Create a shop and its owner account |
| GET | `/` | All shops (full documents) |
| GET | `/summaries` | All shops — light summary with petty cash and total expense |
| GET | `/names` | All shops — id and name only (for dropdowns) |
| GET | `/{shopId}` | One shop |
| PUT | `/{shopId}` | Partial update |
| PUT | `/{shopId}/owner` | Update the shop owner's account (name, phone, email, password, salary) |
| POST | `/{shopId}/petty-cash` | Add petty cash |
| GET | `/{shopId}/petty-cash` | Petty cash top-up history (paginated) |

Delete is intentionally not exposed (shops are not deleted).

### POST `/` — create a shop

Needs an **OWNER** token: the owner account is registered through the OWNER-protected signup.

Request:

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

| Field | Rule |
|---|---|
| `shopName`, `ownerName` | required, max 100 |
| `ownerEmailId` | required, valid email, max 150; must not belong to an existing account |
| `password` | required, 8–100 characters, at least one letter and one digit |
| `ownerNumber` | optional, 10 digits; also saved as the shop contact number |
| `shopAddress` | optional, max 300; also saved as the owner's address |
| `gstNumber` | optional, max 30 |
| `salary` | ≥ 0 |

The owner account (role `SHOP_OWNER`, linked to the shop) is created through the [authentication module's registration](#post-users-platform-owner-only) — the same rules and checks as `POST /v1/api/auth/users`. Invalid fields and a taken email are rejected **before** anything is saved; if the account still cannot be created, the shop is removed again.

Response `201`: the created shop (see [Shop object](#shop-object)), with the owner in `employeeIds`.

Errors: `400` field map, e.g. `{"password": "password must contain at least one letter and one digit"}` · `400` `{"message": "Email is already in use: owner@example.com", "status": false}` · `401` · `403` (not the platform owner).

### GET `/` — all shops

Response `200`: array of [Shop objects](#shop-object).

### GET `/summaries`

Response `200`, sorted by shop name:

```json
[
  {
    "shopId": "665f1c2e9b1e8a3d4c7f0a12",
    "shopName": "Saritha Fashions - Anna Nagar",
    "shopType": "Boutique",
    "gstNumber": "33ABCDE1234F1Z5",
    "ownerName": "Saritha R",
    "shopContactNumber": "9876543210",
    "shopAddress": "12, 2nd Avenue, Anna Nagar, Chennai",
    "shopState": "Tamil Nadu",
    "pettyCash": 4500.0,
    "totalExpense": 12800.0
  }
]
```

`pettyCash` = current balance (top-ups − cash expenses). `totalExpense` = all expenses, every payment method.

### GET `/names`

`OWNER` only. Reads just two fields per shop (not the whole shop documents). Response `200`, sorted by shop name:

```json
[
  { "shopId": "665f1c2e9b1e8a3d4c7f0a12", "shopName": "Saritha Fashions - Anna Nagar" },
  { "shopId": "665f1c2e9b1e8a3d4c7f0a13", "shopName": "Saritha Fashions - T Nagar" }
]
```

### GET `/{shopId}`

Response `200`: [Shop object](#shop-object). Errors: `400` `"No Shop Found"`.

### PUT `/{shopId}` — partial update

Only the fields sent are changed; missing or `null` fields keep their value.

```json
{ "shopName": "Saritha Fashions - T Nagar", "shopContactNumber": "9876543210" }
```

Updatable fields: `shopName`, `ownerName`, `shopAddress`, `shopContactNumber`, `gstNumber`, `shopType`, `allInvoice`, `allReceipts` (leave the last two out — sending them replaces those lists).

Response `200`: the updated [Shop object](#shop-object). Errors: `400` `"No Shop Found"`.

### PUT `/{shopId}/owner` — update the shop owner's details

Platform `OWNER` only. Replaces `PUT /v1/api/owner/shop/update-owner-details`.

```json
{
  "ownerUserId": "665f0a1b2c3d4e5f6a7b8c9d",
  "name": "Saritha R",
  "phone": "9876500000",
  "email": "owner.new@example.com",
  "password": "NewStrong2026",
  "salary": 27000,
  "reason": "Owner changed phone number"
}
```

| Field | Rule |
|---|---|
| `ownerUserId` | optional — without it the shop's `SHOP_OWNER` is used (error if the shop has none or several); if sent, it must be a `SHOP_OWNER` of this shop |
| `name` | ≤ 100; also saved as the shop's `ownerName` |
| `phone` | 10 digits; also saved as the shop's `shopContactNumber` |
| `email` | valid, ≤ 150, stored lower-case, must not belong to another account |
| `password` | 8–100 characters, at least one letter and one digit |
| `salary` | 0 – 10,000,000 |
| `reason` | optional, ≤ 200; stored with the backup (default "Shop Owner Details Update") |

Send at least one of `name`, `phone`, `email`, `password`, `salary`; blank fields are ignored.

What happens: the owner's current details are backed up in `user_backup_history` (password redacted; same document shape as before) → only the values that actually differ are updated → the owner is kept in the shop's `employeeIds`. **A new email or password signs the owner out on every device.** If nothing differs, nothing is written.

Response `200`:

```json
{
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "ownerUserId": "665f0a1b2c3d4e5f6a7b8c9d",
  "name": "Saritha R",
  "email": "owner.new@example.com",
  "phone": "9876500000",
  "salary": 27000.0,
  "changedFields": ["phone", "email", "salary"],
  "signedOut": true,
  "message": "Shop owner details updated. The previous details were backed up. The owner was signed out and must sign in again."
}
```

Errors: `400` field map (`phone`, `email`, `password`, `salary`, `anyFieldProvided`) · `400` `"Shop Not Found"` · `"User ... is not the shop owner of this shop"` · `"This shop has no shop owner account"` · `"This shop has more than one shop owner; send ownerUserId"` · `"Email is already in use: ..."` · `401` · `403`.

The legacy backup-history read endpoints were not ported; backups are still written and can be read from the `user_backup_history` collection.

### POST `/{shopId}/petty-cash`

Allowed for the platform `OWNER` or a user assigned to that shop. Adds to the balance and records who added it, atomically.

Request:

```json
{ "amount": 5000 }
```

`amount`: required, 0.01 – 10,000,000.

Response `201`:

```json
{
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "shopName": "Saritha Fashions - Anna Nagar",
  "amountAdded": 5000.0,
  "pettyCashBalance": 9500.0,
  "addedBy": "665f0a1b2c3d4e5f6a7b8c9d",
  "addedAt": "2026-11-12T05:30:00.000+00:00"
}
```

Errors: `400` `{"amount": "amount must be greater than 0"}` · `400` `"Shop Not Found"` · `401` (no token) · `403` (not the platform owner or this shop's owner).

### GET `/{shopId}/petty-cash` — top-up history

The platform `OWNER` (any shop) or the `SHOP_OWNER` of that shop. Newest first by default, paginated ([format](#paginated-responses)).

| Query | Meaning |
|---|---|
| `from`, `to` | Date range of the top-up (yyyy-MM-dd, inclusive, IST) |
| `amount` | Only top-ups of exactly this amount |
| `sort` | `newest` (default), `oldest`, `highest` or `lowest` (amount; newest first on ties) |
| `pageNumber`, `pageSize` | Paging (default 0 / 20, max 100) |

`content` entries:

```json
{
  "amount": 5000.0,
  "addedAt": "2026-11-12T05:30:00.000+00:00",
  "addedBy": "665f0a1b2c3d4e5f6a7b8c9d",
  "addedByName": "Saritha R"
}
```

`addedByName` is `null` if that account no longer exists.

Errors: `400` `"Shop Not Found"` · `400` `"to must not be before from"` · `400` paging errors · `401` · `403` (another shop's owner, or another role).

### Shop object

```json
{
  "id": "665f1c2e9b1e8a3d4c7f0a12",
  "shopName": "Saritha Fashions - Anna Nagar",
  "pettyCash": 9500.0,
  "ownerName": "Saritha R",
  "shopAddress": "12, 2nd Avenue, Anna Nagar, Chennai",
  "shopState": "Tamil Nadu",
  "shopContactNumber": "9876543210",
  "gstNumber": "33ABCDE1234F1Z5",
  "shopType": "Boutique",
  "totalExpense": 12800.0,
  "allPettyCashDetails": [
    { "addingDate": "2026-11-12T05:30:00.000+00:00", "amount": 5000.0, "addedBy": "665f0a1b..." }
  ],
  "ownerDetails": null,
  "allExpense": [],
  "employeeIds": ["665f0a1b..."],
  "allReceipts": [],
  "allInvoice": []
}
```

---

## 3. Invoices

Paths below are relative to `/v1/api` · Replaces invoice creation, the invoice lists / search / special-category screens, invoice download and invoice delete

| Method | Path | Purpose |
|---|---|---|
| GET | `/invoices` | List / search invoices (paginated) |
| GET | `/invoices/{invoiceId}` | One invoice in full |
| GET | `/invoices/{invoiceId}/pdf` | Download the invoice PDF |
| POST | `/invoices` (multipart **or** JSON) | Create an invoice |
| PATCH | `/invoices/{invoiceId}` (multipart **or** JSON) | Edit an invoice that has no receipt yet |
| DELETE | `/invoices/{invoiceId}` | Delete (archive) an invoice that has no receipt yet |

Access: reads (`GET`) — `SHOP_OWNER` (own shop) or platform `OWNER` with `?shopId=` ([shop scope](#shop-scope-on-read-endpoints)). Create / edit / delete — `SHOP_OWNER` only, own shop. Another shop's invoice returns `"Invoice not found"`.

### POST `/invoices` — create

Two request formats on the same URL:

| Content-Type | Use when | Parts |
|---|---|---|
| `multipart/form-data` | item images attached | `invoiceJson` (text, the JSON below) + `images` (files, optional, repeatable) |
| `application/json` | no images | body = the JSON below |

Invoice JSON (same field names as the old endpoint; extra fields are ignored):

```json
{
  "customer": {
    "name": "Priya K",
    "phone": "9876543210",
    "email": "priya@example.com",
    "alternatePhone": "9123456780",
    "address": "12, 2nd Avenue, Anna Nagar, Chennai"
  },
  "customerState": "Tamil Nadu",
  "items": [
    {
      "itemName": "Blouse",
      "itemQuantity": 2,
      "itemPrice": 850,
      "measurements": { "Chest": "36", "Length": "14" },
      "measurementList": [ { "name": "Sleeve", "value": "10", "unit": "inch" } ]
    },
    { "itemName": "Saree Fall", "itemQuantity": 1, "itemPrice": 150 }
  ],
  "totalAmount": 1850,
  "discount": 100,
  "advancePaid": 1000,
  "paymentMethod": "UPI",
  "transactionId": "UPI-TEST-0001",
  "expectedDeliveryDate": "2026-11-25",
  "orderDate": null,
  "notes": "Urgent - wedding on 27th",
  "isBackDate": false,
  "backDate": null
}
```

| Field | Rules |
|---|---|
| `customer.name` | required, ≤ 100 |
| `customer.phone` | required, 10 digits — the customer is found by phone, or created |
| `customer.email` | optional, valid email — if present the invoice link is also emailed |
| `customer.alternatePhone` | optional, 10 digits |
| `customer.address` | optional, ≤ 300 |
| `customerState` | required — same state as the shop → CGST 2.5% + SGST 2.5%, otherwise IGST 5% (case/space-insensitive) |
| `items` | 1–50 items |
| `items[].itemName` | required, ≤ 100 |
| `items[].itemQuantity` | required, **whole or decimal**, greater than 0, ≤ 1000, at most 2 decimal places (e.g. `2`, `2.5`, `0.75` metres). Line amount = `itemPrice` (per **1** unit) × `itemQuantity`, calculated exactly and rounded to paise. Whole quantities print on the PDF as before (`2`), decimals as `2.5`. |
| `items[].itemPrice` | required, 0 – 10,000,000 — price of **one** unit, before GST |
| `totalAmount` | optional; calculated by the server as Σ price × quantity — if sent it must match |
| `discount` | optional, ≥ 0, ≤ total |
| `advancePaid` | optional, ≥ 0, ≤ total with GST |
| `paymentMethod` | optional, ≤ 30; defaults to `Cash` when an advance is paid |
| `expectedDeliveryDate` | optional date: `yyyy-MM-dd` (also `25-11-2026`, `25/11/2026`, `25.11.2026`, day first); stored as `yyyy-MM-dd`. Anything else → `400 {"expectedDeliveryDate": "expectedDeliveryDate must be a date, e.g. 2026-11-25"}`. Used by the [delivery dashboard](#get-deliveries--delivery-dashboard). |
| `notes` | optional, ≤ 500; defaults to "Thank you for your order" |
| `isBackDate` / `backDate` | `backDate` (`yyyy-MM-dd`, not in the future) is required when `isBackDate` is `true` |

Images (`multipart` only): file name `item_{index}_{anything}.{ext}`, where `index` is the item's 0-based position in `items`; image types only, ≤ 10 MB, one per item.

What the server does: finds or creates the customer → calculates totals and GST → assigns the invoice number `{SHOP}-{dd-MM-yyyy}-{00001}` (first 4 letters of the shop name) → assigns item ids `{itemName}_{timestamp}_{index}` → sets item status (`Embroidery`, `Lining Material`, `Saree Fall`, `Saree Pico`, `Designing` start in `CUTTING`, others in `ORDER_ACCEPTED`) → uploads images → saves → links the customer → logs the activity → sends WhatsApp / email in the background.

Response `201` (header `Location: <pdfUrl>`):

```json
{
  "id": "6672b1c0d2e3f4a5b6c7d8e9",
  "invoiceId": "SARI-12-11-2026-00042",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "customer": {
    "id": "6672b0a1c2d3e4f5a6b7c8d9",
    "customerId": "CUS-8F3K2Q1ZXA",
    "name": "Priya K",
    "phone": "9876543210",
    "email": "priya@example.com",
    "newCustomer": true
  },
  "customerState": "Tamil Nadu",
  "items": [
    {
      "itemUniqueId": "Blouse_1762935000000_0",
      "itemName": "Blouse",
      "itemQuantity": 2,
      "itemPrice": 850.0,
      "imageUrl": null,
      "itemOrderStatus": "ORDER_ACCEPTED",
      "assignmentStatus": null,
      "assignedTo": null,
      "assignedToWhichShopAssistant": null,
      "measurements": { "Chest": "36", "Length": "14" },
      "measurementList": [ { "name": "Sleeve", "value": "10", "unit": "inch" } ],
      "assignmentHistory": []
    }
  ],
  "totalAmount": 1850.0,
  "discount": 100.0,
  "taxableAmount": 1750.0,
  "cgstRate": 2.5, "sgstRate": 2.5, "igstRate": 0.0,
  "cgstAmount": 43.75, "sgstAmount": 43.75, "igstAmount": 0.0,
  "totalAmountWithGST": 1837.5,
  "advancePaid": 1000.0,
  "balanceDue": 837.5,
  "gstin": "33ABCDE1234F1Z5",
  "sacCode": "9988",
  "hsnCode": "998821",
  "paymentMethod": "UPI",
  "paymentStatus": "Partial",
  "orderStatus": "ORDER_ACCEPTED",
  "paymentHistories": [ { "amount": 1000.0, "paidAt": "2026-11-12T10:30:00Z", "paidVia": "UPI" } ],
  "invoiceDate": "2026-11-12T05:00:00.000+00:00",
  "orderDate": "2026-11-12T05:00:00.000+00:00",
  "expectedDeliveryDate": "2026-11-25",
  "notes": "Urgent - wedding on 27th",
  "receiptId": null,
  "pdfUrl": "/v1/api/invoices/SARI-12-11-2026-00042/pdf",
  "notifications": { "whatsApp": "QUEUED", "email": "QUEUED" }
}
```

This is the **invoice object** used by every invoice endpoint. `receiptId` is `null` until the receipt is generated; after that the invoice can no longer be edited or deleted. `notifications` is only filled on creation (`null` elsewhere).

Amount rules:
- `taxableAmount` = `totalAmount` − `discount`
- `totalAmountWithGST` = `taxableAmount` + GST
- `balanceDue` = `totalAmountWithGST` − `advancePaid`
- `paymentStatus` = `Dues` (nothing paid) · `Partial` (balance > 0) · `Paid`

`notifications`: `QUEUED` or `SKIPPED` per channel (email is `SKIPPED` without an email address). A notification failure never affects the invoice.

Errors (`400`, field → message):

```json
{
  "customer.phone": "customer.phone must be a 10-digit mobile number",
  "items[0].itemQuantity": "itemQuantity must be at least 1"
}
```

| Key | Example |
|---|---|
| `invoiceJson` | `"invoiceJson part is required"` · `"invoiceJson is not valid JSON for an invoice: ..."` |
| `totalAmount` | `"totalAmount 1800.0 does not match the items total 1850.0"` |
| `discount` | `"discount must not exceed the total amount 1850.0"` |
| `advancePaid` | `"advancePaid must not exceed the total with GST 1837.5"` |
| `backDate` | `"backDate must not be in the future"` · `backDateProvidedWhenRequired`: `"backDate is required when isBackDate is true"` |
| `images[0]` | `"file name 'photo.jpg' must look like item_{index}_{name}.{ext}"` · `"... refers to item 5 but the invoice has only 2 item(s)"` · `"... is not an image"` · `"... is larger than 10 MB"` |

Other: `400` `{"message": "User is not assigned to any shop", "status": false}` · `401` (no token) · `403` (not a shop owner).

### GET `/invoices/{invoiceId}/pdf`

Query: `shopId` (platform `OWNER` only, required for them).

Response `200`, `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="SARI-12-11-2026-00042.pdf"` — same template as before.

Errors: `400` `"Invoice not found"` (also for another shop's invoice) · `400` `"Could not generate the invoice PDF, please try again"` · `400` `"shopId is required"` (OWNER) · `401` · `403`.

### GET `/invoices` — list / search

One page of the shop's invoices, newest first. One endpoint replaces the old all-invoice, all-invoice-with-details, special-category, pending-orders and search lists.

| Query | Meaning |
|---|---|
| `shopId` | Platform `OWNER` only (required for them) |
| `status` | `pending` (not delivered yet) · `delivered` · `all` (default) |
| `from`, `to` | Invoice date range (yyyy-MM-dd, inclusive, IST) |
| `search` | Text in the invoice number, customer name or customer phone — literal, case-insensitive, max 100 characters |
| `paymentStatus` | `Dues` · `Partial` · `Paid` (case-insensitive) |
| `special` | `true` = only invoices with Embroidery, Lining Material, Saree Fall, Saree Pico or Designing (default `false`) |
| `details` | `false` (default) = summary rows below · `true` = full [invoice objects](#post-invoices--create) |
| `stage` | Production stage of the invoice: `NEW` (nothing started) · `IN_PRODUCTION` · `READY` (every item `ORDER_COMPLETED`, not delivered yet) · `DELIVERED` |
| `itemStatus` | Only invoices with at least one item in this status (`ORDER_ACCEPTED`, `CUTTING`, `TAILOR_ASSIGNED`, `STITCHING`, `SHOP_ASSISTANT_ASSIGNED`, `HEMMING`, `IRONING`, `ORDER_COMPLETED`) |
| `sort` | `newest` (default) · `oldest` (invoice date) · `dueSoonest` · `dueLatest` (expected delivery date; invoices without a readable delivery date come last; old `dd-MM-yyyy` / `dd/MM/yyyy` dates are understood) |
| `pageNumber`, `pageSize` | Paging (default 0 / 20, max 100) |

Response `200`: [paginated](#paginated-responses); with `details=false` each `content` row is:

```json
{
  "id": "6672b1c0d2e3f4a5b6c7d8e9",
  "invoiceId": "SARI-12-11-2026-00042",
  "invoiceDate": "2026-11-12T05:00:00.000+00:00",
  "expectedDeliveryDate": "2026-11-25",
  "customerId": "CUS-8F3K2Q1ZXA",
  "customerName": "Priya K",
  "customerPhone": "9876543210",
  "itemCount": 3,
  "itemNames": ["Blouse", "Chudidhar", "Saree Fall"],
  "completedItemCount": 1,
  "totalAmountWithGST": 3150.0,
  "advancePaid": 1500.0,
  "balanceDue": 1650.0,
  "paymentStatus": "Partial",
  "orderStatus": "ORDER_ACCEPTED",
  "pdfUrl": "/v1/api/invoices/SARI-12-11-2026-00042/pdf",
  "stage": "IN_PRODUCTION",
  "itemStatusCounts": { "ORDER_ACCEPTED": 0, "CUTTING": 1, "TAILOR_ASSIGNED": 0, "STITCHING": 1,
                        "SHOP_ASSISTANT_ASSIGNED": 0, "HEMMING": 0, "IRONING": 0, "ORDER_COMPLETED": 1 },
  "receiptNo": null,
  "deliveredAt": null
}
```

`stage` is derived from the item statuses: `NEW` = no item has moved past `ORDER_ACCEPTED`; `IN_PRODUCTION` = something started,
not everything completed; `READY` = every item `ORDER_COMPLETED` but not delivered; `DELIVERED` = receipt generated. The `stage`
filter uses exactly the same rule. `itemStatusCounts` lists every workflow status (0 when none). `receiptNo` is the receipt
**number** once delivered (use it with `GET /receipts/{receiptNo}/pdf`); `deliveredAt` is when that receipt was generated
(= when the order was delivered), `null` before delivery (and for an old receipt that has no date).

`completedItemCount` = items in `ORDER_COMPLETED`. Customer fields are `null` if the customer record no longer exists. Customers of a page are loaded in one query (the old lists loaded them one invoice at a time).

Errors (`400`): `"status must be pending, delivered or all"` · `"paymentStatus must be Dues, Partial or Paid"` · `"sort must be newest, oldest, dueSoonest or dueLatest"` · `"stage must be NEW, IN_PRODUCTION, READY or DELIVERED"` · `"itemStatus must be one of [...]"` · `"to must not be before from"` · `"search must be at most 100 characters"` · paging errors · `"shopId is required"` / `"Shop Not Found"` (OWNER) · `"User is not assigned to any shop"`. Also `401` · `403`.

### GET `/invoices/{invoiceId}`

Query: `shopId` (platform `OWNER` only). Response `200`: the [invoice object](#post-invoices--create) (`notifications` is `null`),
plus `stage`, `itemStatusCounts`, `receiptNo` and `deliveredAt` as in the list rows. Note: `receiptId` is the receipt's **database id**; use
`receiptNo` for the receipt PDF. Each item's `assignmentHistory` is its timeline: `[{ "tailorId": "<user who acted>", "action":
"CUTTING", "timestamp": ... }]` — the field is called `tailorId` for every actor (legacy name); a stage starts at its entry and
ends at the next one; `ORDER_ACCEPTED` starts at `invoiceDate`.

Errors: `400` `"Invoice not found"` (also another shop's) · `400` `"shopId is required"` (OWNER) · `401` · `403`.

### PATCH `/invoices/{invoiceId}` — edit

Shop owner only. Allowed while the invoice has **no receipt** and is **not delivered**.

| Content-Type | Use when | Parts |
|---|---|---|
| `multipart/form-data` | item images attached | `editJson` (text, the JSON below) + `images` (files, optional) |
| `application/json` | no images | body = the JSON below |

Every field is optional; a field left out (or `null`) is not changed:

```json
{
  "expectedDeliveryDate": "2026-11-28",
  "notes": "Customer will collect on Saturday",
  "discount": 150,
  "items": [
    { "itemUniqueId": "Blouse_1762935000000_0", "itemName": "Blouse", "itemQuantity": 3,
      "itemPrice": 850, "measurements": { "Chest": "36", "Length": "15" } },
    { "itemName": "Saree Pico", "itemQuantity": 1, "itemPrice": 120 }
  ]
}
```

| Field | Effect |
|---|---|
| `expectedDeliveryDate` | Replaced (same date formats and normalisation as on create); `""` clears it |
| `notes` | Replaced (max 500); `""` resets to "Thank you for your order" |
| `discount` | Replaced (≥ 0, ≤ total); amounts are recalculated |
| `items` | The **complete** list after the edit (1–50 items) — see below; amounts are recalculated. `itemQuantity` follows the same rule as on create (whole or decimal, max 2 decimals). |

How `items` works:
- **Keep / change an existing item:** include it with its `itemUniqueId`. `itemName`, `itemQuantity`, `itemPrice` are required; `measurements` / `measurementList` replace the old ones when sent (`null` keeps them); `removeImage: true` removes its image.
- **Add an item:** leave out `itemUniqueId`. It gets a new id and starts in `ORDER_ACCEPTED` (or `CUTTING` for the special items, as on creation).
- **Remove an item:** leave it out of the list. Its image is deleted after the edit succeeds.
- **Items already in production** — assigned to a tailor or shop assistant, or moved past their start status — must be sent **unchanged** and cannot be removed.
- Renaming an item that has not started also updates its start status (e.g. to a special item → `CUTTING`).
- **Images:** files named `item_{index}_{name}.{ext}`, where `index` is the position in `items` (0-based). A new image replaces the item's old one (the old file is deleted after success). Images need `items` in the request. Same type / size rules as creation.

Recalculation uses the invoice's customer state and the advance already paid. Old invoices without a customer state keep their tax type (IGST stays IGST). If only `expectedDeliveryDate` / `notes` change, stored amounts are left untouched.

Not editable here: customer state, invoice date, payments (use [payments & receipts](#6-payments--receipts)) and production statuses (use [production](#4-production-items)).

**Customer correction** — the optional `customer` object corrects the invoice's customer, e.g. a misspelled name after the bill was made (client requirement; replaces the legacy "Edit Order" customer change):

```json
{ "customer": { "name": "Priya Krishnan", "phone": "9876543210", "email": "", "alternatePhone": "9123456780", "address": "Anna Nagar, Chennai" } }
```

| Field | Rule |
|---|---|
| `name` | ≤ 100, not blank (cannot be cleared) |
| `phone` | 10 digits (cannot be cleared); must not belong to **another** customer → `400 {"customer.phone": "another customer already uses the phone number ..."}` |
| `email` | valid email; `""` removes it |
| `alternatePhone` | 10 digits; `""` removes it |
| `address` | ≤ 300; `""` removes it |

- Fields left out are unchanged. A request with **only** `customer` is allowed **at any time — also after the receipt** (it changes no amounts); any other field still needs an invoice without receipt.
- The change is made on the **customer record**, so every invoice, receipt and PDF of that customer shows the corrected details (same as before).
- Errors: `400` field map with keys `customer.name`, `customer.phone`, ... · `400` `"This invoice has no customer record to correct"`.


Response `200`: the updated [invoice object](#post-invoices--create).

Errors:

| Status | Example |
|---|---|
| 400 | `{"items[0]": "item Blouse_1762935000000_0 is already in production (STITCHING) and cannot be changed"}` |
| 400 | `{"items": "item Kurti_1762935000000_2 is already in production (TAILOR_ASSIGNED) and cannot be removed"}` |
| 400 | `{"items[1].itemUniqueId": "this invoice has no item X"}` · `"item X is listed more than once"` |
| 400 | `{"items": "the new total with GST would be less than the 1500.0 already paid"}` |
| 400 | `{"discount": "discount must not exceed the total amount 1850.0"}` · `{"images": "images can only be sent together with items"}` · image errors as on creation |
| 400 | `"Invoice SARI-12-11-2026-00042 already has receipt SARI-13-11-2026-00007 and can no longer be edited"` · `"... is already delivered and can no longer be edited"` |
| 400 | `"Nothing to change: send expectedDeliveryDate, notes, discount or items"` · `"Invoice not found"` · `"User is not assigned to any shop"` |
| 401 / 403 | no token / not a shop owner |
| 409 | `"The invoice was changed by someone else (payment, receipt or production update) while you were editing it. Reload it and try again."` |

**409 / concurrency:** the update is saved only if the stored items (every field: statuses, assignments, measurements, images), the advance paid and the discount are exactly as they were read. Otherwise nothing is saved, newly uploaded images are deleted and `409` is returned. Kept items are copied from the stored document, so production fields (e.g. `ShopAssistantStatus`, assignment history) are preserved.

No activity entry is recorded for an edit (there is no `INVOICE_UPDATED` activity type yet).

### DELETE `/invoices/{invoiceId}`

Shop owner only. Allowed while the invoice has **no receipt** and is **not delivered** (delete the receipt first otherwise).

Query: `reason` (optional, max 300 characters; default `"Deleted by the shop owner"`).

What happens:
1. The stored invoice is copied to `deleted_invoices` as `{ invoice, deletedBy, deletedAt, reason }` (same shape as before).
2. The invoice is deleted only if it still has no receipt, is not delivered and its items / advance are unchanged; otherwise the archive copy is removed again and `409` is returned.
3. Then (failures are logged, not returned): the invoice number is removed from the customer's `allInvoiceIds`, an `INVOICE_DELETED` activity is recorded with the reason, and the item images are deleted.

Items already in production can be deleted with the invoice (same as the old delete).

**Bill numbers stay in sequence** (client requirement, same as before): if the deleted invoice was the shop's **latest** one, its number is released and the **next invoice gets the same number**; the response message then says *"Invoice deleted. Its number will be used for the next invoice."* Deleting an older invoice leaves a gap (its number is not re-used). The release is guarded: if a new invoice was created in the meantime, nothing is moved back.

Response `200`:

```json
{
  "invoiceId": "SARI-12-11-2026-00042",
  "archiveId": "6673c2d1e2f3a4b5c6d7e8f9",
  "reason": "Duplicate invoice",
  "deletedAt": "2026-11-13T06:10:00.000+00:00",
  "message": "Invoice deleted"
}
```

Errors: `400` `"Invoice ... already has receipt ... and can no longer be deleted"` · `400` `"... is already delivered ..."` · `400` `{"reason": "reason must be at most 300 characters"}` · `400` `"Invoice not found"` · `401` · `403` · `409` (changed meanwhile, nothing deleted).

---

## 4. Production (items)

Paths below are relative to `/v1/api` · Replaces the status / assignment endpoints under `/v1/api/tailor-master`, `/v1/api/tailor`, `/v1/api/shop-assistant`

### Workflow

```
ORDER_ACCEPTED ─► CUTTING ─► TAILOR_ASSIGNED ─► STITCHING ─► SHOP_ASSISTANT_ASSIGNED ─► HEMMING ─► IRONING ─► ORDER_COMPLETED
                                                                                                  (receipt → invoice ORDER_DELIVERED)
```

| Move | Endpoint | Who |
|---|---|---|
| `ORDER_ACCEPTED` → `CUTTING` | `PATCH .../status` | **Tailor master** |
| → `TAILOR_ASSIGNED` (from `CUTTING`; re-assign from `TAILOR_ASSIGNED` / `STITCHING`) | `PUT .../tailor` | Tailor master, shop owner |
| `TAILOR_ASSIGNED` → `STITCHING` | `PATCH .../status` | **The assigned tailor** only |
| → `SHOP_ASSISTANT_ASSIGNED` (from `STITCHING`; re-assign from `SHOP_ASSISTANT_ASSIGNED` / `HEMMING` / `IRONING`) | `PUT .../shop-assistant` | **The item's tailor** only from `STITCHING` (hands over once, no re-assign); shop owner from any of them (**not** the tailor master) |
| `SHOP_ASSISTANT_ASSIGNED` → `HEMMING` → `IRONING` → `ORDER_COMPLETED` | `PATCH .../status` | **The assigned shop assistant** only |
| Unassign tailor (`TAILOR_ASSIGNED` / `STITCHING` → `CUTTING`) | `DELETE .../tailor` | Shop owner |
| Unassign shop assistant (`SHOP_ASSISTANT_ASSIGNED` / `HEMMING` / `IRONING` → `STITCHING`) | `DELETE .../shop-assistant` | Shop owner |

**Status changes are made only by the person responsible for the step** — the shop owner and the tailor master do not move statuses for others, so the activity log always credits the person who did the work. The shop owner steers production by assigning, re-assigning and unassigning (e.g. when someone is absent); their queue shows every item, but their `availableActions` only contain assignment actions.

Rules: only the next step is allowed (no skipping, no going back); workers can only act on items assigned to them; items of delivered invoices are locked; a tailor cannot be removed once a shop assistant is assigned (stitching is complete). All rules live in `modules/production/model/ItemWorkflow.java`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/items/queue` | My work queue (+ `search`, `sort`, `due`) |
| GET | `/items/queue/summary` | Counts of my queue (per status, orders, late, due today / week) |
| GET | `/items/queue/orders` | My queue grouped by invoice |
| GET | `/invoices/{invoiceId}/items/{itemUniqueId}` | One item + my available actions |
| PATCH | `/invoices/{invoiceId}/items/{itemUniqueId}/status` | Next status |
| PUT | `/invoices/{invoiceId}/items/{itemUniqueId}/tailor` | Assign / re-assign tailor |
| PUT | `/invoices/{invoiceId}/items/{itemUniqueId}/shop-assistant` | Assign / re-assign shop assistant |
| DELETE | `/invoices/{invoiceId}/items/{itemUniqueId}/tailor` | Unassign tailor |
| DELETE | `/invoices/{invoiceId}/items/{itemUniqueId}/shop-assistant` | Unassign shop assistant |
| POST | `/invoices/{invoiceId}/items/{itemUniqueId}/complete` | Shop owner: mark one item completed (skips the remaining steps) |
| POST | `/invoices/{invoiceId}/items/complete` | Shop owner: mark every not-completed item of the invoice completed |
| GET | `/items/assignees/tailors` | Tailors of my shop (dropdown; `withLoad=true` adds current load) |
| GET | `/items/assignees/shop-assistants` | Shop assistants of my shop (dropdown; `withLoad=true` adds current load) |

### Work item object

Returned by every item endpoint:

```json
{
  "invoiceId": "SARI-12-11-2026-00042",
  "itemUniqueId": "Blouse_1762935000000_0",
  "itemName": "Blouse",
  "itemQuantity": 2,
  "imageUrl": null,
  "status": "TAILOR_ASSIGNED",
  "tailor": { "userId": "665f0a1b2c3d4e5f6a7b8c9d", "name": "Lakshmi" },
  "shopAssistant": null,
  "measurements": { "Chest": "36", "Length": "14" },
  "measurementList": [ { "name": "Sleeve", "value": "10", "unit": "inch" } ],
  "customerName": "Priya K",
  "expectedDeliveryDate": "2026-11-25",
  "invoiceDate": "2026-11-12T05:00:00.000+00:00",
  "dueDate": "2026-11-25",
  "daysOverdue": 0,
  "availableActions": ["STITCHING"],
  "history": [
    { "userId": "665f0a1b...", "action": "CUTTING", "timestamp": "2026-11-12T06:00:00.000+00:00" },
    { "userId": "665f0a1b...", "action": "TAILOR_ASSIGNED", "timestamp": "2026-11-12T06:10:00.000+00:00" }
  ]
}
```

`availableActions` — what the **current user** may do, for showing buttons:

| Value | Call |
|---|---|
| `CUTTING`, `STITCHING`, `HEMMING`, `IRONING`, `ORDER_COMPLETED` | `PATCH .../status` with that status |
| `ASSIGN_TAILOR` | `PUT .../tailor` |
| `ASSIGN_SHOP_ASSISTANT` | `PUT .../shop-assistant` |
| `UNASSIGN_TAILOR` | `DELETE .../tailor` |
| `UNASSIGN_SHOP_ASSISTANT` | `DELETE .../shop-assistant` |

`history[].action` also contains `TAILOR_UNASSIGNED` / `SHOP_ASSISTANT_UNASSIGNED` (with the removed person's id).

### GET `/items/queue`

Query: `status` (optional, repeatable, e.g. `?status=CUTTING&status=TAILOR_ASSIGNED`), `pageNumber` (default 0), `pageSize` (1–100, default 20).

| Role | Items |
|---|---|
| `SHOP_OWNER` | every item not yet completed (`status=ORDER_COMPLETED` shows items ready to deliver) |
| `TAILOR_MASTER` | `ORDER_ACCEPTED`, `CUTTING`, `TAILOR_ASSIGNED`, `STITCHING` |
| `TAILOR` | `TAILOR_ASSIGNED`, `STITCHING` — assigned to them |
| `SHOP_ASSISTANT` | `SHOP_ASSISTANT_ASSIGNED`, `HEMMING`, `IRONING` — assigned to them |

Oldest invoice first; delivered invoices never appear. Response `200`: paginated [work items](#work-item-object).

Optional filters (all roles, combinable with `status`):

| Param | Values | Meaning |
|---|---|---|
| `search` | text (≤ 100) | invoice no., customer name or item name contains it (case-insensitive) |
| `sort` | `oldest` (default) · `newest` · `dueSoonest` | invoice date ↑ / ↓; or delivery date ↑ with no date last |
| `due` | `today` · `overdue` · `week` · `none` | delivery date = today / before today / today…today+6 / missing or unreadable (IST) |

Without these the queue is paged in the database as before; with any of them the whole queue (max 5000 items) is
read, filtered and sorted in Java (the delivery date is a free-text field), then paged.

Errors: `400` `"Unknown status: STICHING"` · `"Status HEMMING is not part of your work queue; allowed: [TAILOR_ASSIGNED, STITCHING]"` · `"pageSize must be between 1 and 100"` · `"sort must be oldest, newest or dueSoonest"` · `"due must be today, overdue, week or none"` · `"search must be at most 100 characters"` · `403`.

### GET `/items/queue/summary`

Counts of the whole queue (same items as `/items/queue` without filters). Response `200`:

```json
{
  "today": "2026-11-18",
  "countsByStatus": { "ORDER_ACCEPTED": 4, "CUTTING": 2, "TAILOR_ASSIGNED": 3, "STITCHING": 5 },
  "totalItems": 14, "orders": 6, "late": 2, "dueToday": 1, "dueThisWeek": 5, "withoutDueDate": 0
}
```

`countsByStatus` has every status of the caller's queue (0 when none), in workflow order. `late` = delivery date before
today; `dueThisWeek` = today … today + 6 (includes `dueToday`). Errors: `403`.

### GET `/items/queue/orders`

The queue **grouped by invoice**, same query parameters as `/items/queue` (`status`, `search`, `sort`, `due`,
`pageNumber`, `pageSize` 1–100) — paging is per **order**. Each order holds only its items that are in the caller's
queue (e.g. for the tailor master, not the items already with the shop assistant). Orders are placed by their first
item under the chosen sort (`dueSoonest` → soonest delivery first).

```json
{
  "content": [
    {
      "invoiceId": "SARI-18-11-2026-00042", "customerName": "Priya R",
      "invoiceDate": "2026-11-18T05:00:00.000+00:00", "expectedDeliveryDate": "2026-11-22",
      "dueDate": "2026-11-22", "daysOverdue": 0,
      "countsByStatus": { "ORDER_ACCEPTED": 1, "CUTTING": 1, "TAILOR_ASSIGNED": 0, "STITCHING": 1 },
      "itemCount": 3,
      "items": [ { "...": "work item" } ]
    }
  ],
  "pageNumber": 0, "pageSize": 20, "totalElements": 6, "totalPages": 1, "lastPage": true
}
```

Errors: as `/items/queue`.

### GET `/invoices/{invoiceId}/items/{itemUniqueId}`

Response `200`: [work item](#work-item-object). Errors: `400` `"Item not found"` · `403`.

### PATCH `/invoices/{invoiceId}/items/{itemUniqueId}/status`

Who may move a step: the worker responsible for it (tailor master: cutting; the assigned tailor: stitching; the assigned shop
assistant: hemming, ironing, completed) **or the shop owner** (any step, one step at a time, no skipping). A change by the shop
owner is recorded under the shop owner in the item history and the activity log (note "… (by shop owner)"), so it is clear who
pressed the button. `availableActions` on the work item shows the next step for the shop owner too.

```json
{ "status": "STITCHING" }
```

Response `200`: the updated [work item](#work-item-object).

| Status | Example message |
|---|---|
| 400 | `"Unknown status: STICHING"` |
| 400 | `"Cannot move the item from TAILOR_ASSIGNED to ORDER_COMPLETED. Next step: STITCHING."` |
| 400 | `"Use PUT .../tailor to assign a tailor"` · `"Use PUT .../shop-assistant to assign a shop assistant"` |
| 400 | `"The invoice has already been delivered; its items can no longer change"` · `"Item not found"` |
| 403 | not your item / not your role |
| 409 | `"This item was changed by someone else in the meantime. Please reload it and try again."` |

### PUT `.../tailor` and PUT `.../shop-assistant`

```json
{ "tailorId": "665f0a1b2c3d4e5f6a7b8c9d" }
```

```json
{ "shopAssistantId": "665f0a1b2c3d4e5f6a7b8c9e" }
```

The person must work in the same shop and have the role `TAILOR` / `SHOP_ASSISTANT`.

Response `200`: the updated [work item](#work-item-object).

| Status | Example message |
|---|---|
| 400 | `"A tailor can be assigned only when the item is CUTTING / TAILOR_ASSIGNED / STITCHING; it is HEMMING. ..."` |
| 400 | `"This tailor is already assigned to the item"` |
| 400 | `"User 665f... is not a tailor of your shop"` · `"... is not a shop assistant of your shop"` |
| 403 | not allowed (e.g. tailor master assigning a shop assistant, or another tailor's item) |
| 409 | concurrent change |

### DELETE `.../tailor` and DELETE `.../shop-assistant` (shop owner)

No body. Response `200`: the updated [work item](#work-item-object) (tailor → item back to `CUTTING`; shop assistant → item back to `STITCHING`).

| Status | Example message |
|---|---|
| 400 | `"The tailor can be removed only while the item is TAILOR_ASSIGNED or STITCHING. Once a shop assistant is assigned, stitching is complete. It is HEMMING."` |
| 400 | `"The shop assistant can be removed only while the item is SHOP_ASSISTANT_ASSIGNED, HEMMING or IRONING. It is ORDER_COMPLETED."` |
| 400 | `"No tailor is assigned to this item"` |
| 403 | not the shop owner |
| 409 | concurrent change |

### POST `.../items/{itemUniqueId}/complete` and POST `/invoices/{invoiceId}/items/complete` (shop owner)

For work that was finished but not recorded in the app. The item goes **straight to `ORDER_COMPLETED` from any step**
(including `ORDER_ACCEPTED` / `CUTTING` with nobody assigned); its tailor / shop assistant stay as they are. Not allowed
after delivery. Only the SHOP_OWNER (own shop). The item's `availableActions` contain `COMPLETE` when it is allowed.

Body (optional): `{ "note": "Ravi finished it on Monday" }` (max 200 characters).

Recorded:
- item `assignmentHistory`: `{ "tailorId": "<shop owner id>", "action": "ORDER_COMPLETED", "timestamp": …, "markedByShopOwner": true, "skippedFrom": "STITCHING", "note": "…" }`
- activity log: type **`ITEM_MARKED_COMPLETED`**, note `"Marked completed by shop owner (was STITCHING): <note>"`. Workers get
  **no** credit in employee analytics (a tailor's count is `ASSIGNED_TO_ASSISTANT`, an assistant's `ORDER_COMPLETED`).

One item — response `200`: the updated [work item](#work-item-object).

All items — response `200`:
```json
{
  "invoiceId": "SARI-12-11-2026-00042",
  "completed": ["Blouse_1762935000000_0", "Top_1762935000000_2"],
  "alreadyCompleted": 1,
  "failed": [{ "itemUniqueId": "Salwar_1762935000000_3", "itemName": "Salwar", "reason": "This item was changed by someone else in the meantime. Please reload it and try again." }]
}
```
Items are completed one at a time: an item changed by someone else meanwhile is listed in `failed`, the others are still
completed. When every item is completed the invoice `stage` becomes `READY` and the receipt (delivery) is allowed once it
is fully paid.

| Status | Example message |
|---|---|
| 400 | `"The item is already completed"` (one item) |
| 400 | `"The invoice has already been delivered; its items can no longer change"` |
| 400 | `"Item not found"` / `"Invoice not found"` / `"note must be at most 200 characters"` |
| 403 | not the shop owner |
| 409 | concurrent change (one item) |

### GET `/items/assignees/tailors` · GET `/items/assignees/shop-assistants`

Response `200`, sorted by name:

```json
[ { "userId": "665f0a1b2c3d4e5f6a7b8c9d", "name": "Lakshmi" } ]
```

Tailors: shop owner, tailor master. Shop assistants: shop owner, tailor. Otherwise `403`.

**Tailors with load:** `GET /items/assignees/tailors?withLoad=true` adds each tailor's current work (all undelivered
invoices of the shop): `assigned` = items TAILOR_ASSIGNED (not started), `inProgress` = STITCHING, `late` = of those,
delivery date passed (IST). Without `withLoad` the three fields are left out.

**Shop assistants with load:** `GET /items/assignees/shop-assistants?withLoad=true` — same fields: `assigned` = items
SHOP_ASSISTANT_ASSIGNED (not started), `inProgress` = HEMMING + IRONING, `late` = of those, delivery date passed.

```json
[ { "userId": "665f0a1b2c3d4e5f6a7b8c9d", "name": "Lakshmi", "assigned": 3, "inProgress": 2, "late": 1 } ]
```

---

## 5. Expenses

Paths below are relative to `/v1/api` · Replaces `/v1/api/expenses/add`, `/add-employee-expense`, delete and the expense lists (`/v1/api/shops/view-expenses...`, `/v1/api/expenses/shop/{shopId}`, `/v1/api/expenses/all`)

Access: reads (`GET`) — `SHOP_OWNER` (own shop) or platform `OWNER` with `?shopId=` ([shop scope](#shop-scope-on-read-endpoints)). Writes — `SHOP_OWNER` only; expenses belong to the owner's shop.

| Method | Path | Purpose |
|---|---|---|
| GET | `/expenses` | List / search expenses (paginated) |
| GET | `/expenses/summary` | Totals of the filtered expenses (count, amount, per category, per payment method, shop vs employee) |
| GET | `/expenses/deleted` | Deleted expenses with the deletion note |
| GET | `/expenses/{expenseId}` | One expense |
| POST | `/expenses` | Shop expense (stored **without** `userId`) |
| POST | `/employee-expenses` | Expense for an employee (`userId` = the employee) |
| PATCH | `/expenses/{expenseId}` | Change amount, notes, category, date, payment method, or the employee |
| DELETE | `/expenses/{expenseId}?reason=` | Delete with an optional note (kept in the deleted-expense archive; reverses its effect on the shop) |

Petty cash: **Cash** expenses are deducted from the shop's petty cash and are rejected when the balance is too low; other payment methods do not touch petty cash. Every expense is added to the shop's `totalExpense`. The check and the deduction are one atomic update, so petty cash can never go negative.

### POST `/expenses` — shop expense

```json
{
  "expenseType": "Electricity",
  "expenseAmount": 1850,
  "paymentMethod": "Cash",
  "notes": "EB bill for October",
  "expenseDate": "2026-11-12T10:30:00.000+05:30",
  "refreshmentDetails": [ { "amount": 120, "description": "Tea and snacks" } ],
  "miscellaneousDetails": [],
  "travelDetails": [
    { "date": "2026-11-12", "source": "Anna Nagar", "destination": "T Nagar", "amount": 250, "description": "Fabric pickup" }
  ]
}
```

| Field | Rules |
|---|---|
| `expenseType` | required, ≤ 100 |
| `expenseAmount` | required, 0.01 – 10,000,000 (the amount applied to the shop totals) |
| `paymentMethod` | optional, ≤ 30; default `Cash` (any casing of "cash" counts as cash) |
| `notes` | optional, ≤ 500 |
| `expenseDate` | optional; missing / `null` → now (IST). Accepts `yyyy-MM-dd`, ISO date-time with offset, or without offset (read as IST) |
| `refreshmentDetails[]`, `miscellaneousDetails[]` | optional; `amount` ≥ 0.01, `description` ≤ 200 |
| `travelDetails[]` | optional; `date` `yyyy-MM-dd`, `source` / `destination` ≤ 100, `amount` ≥ 0.01, `description` ≤ 200 |

The line items are stored for reference; they are not checked against `expenseAmount`.

Response `201`:

```json
{
  "id": "6672a9f0c1d2e3f4a5b6c7d8",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "userId": null,
  "expenseType": "Electricity",
  "expenseAmount": 1850.0,
  "paymentMethod": "Cash",
  "notes": "EB bill for October",
  "expenseDate": "2026-11-12T05:00:00.000+00:00",
  "refreshmentDetails": [ { "amount": 120.0, "description": "Tea and snacks" } ],
  "miscellaneousDetails": [],
  "travelDetails": [ { "date": "2026-11-12", "source": "Anna Nagar", "destination": "T Nagar", "amount": 250.0, "description": "Fabric pickup" } ],
  "pettyCashBalance": 2650.0,
  "shopTotalExpense": 14650.0
}
```

### POST `/employee-expenses`

Same as a shop expense plus one required field:

```json
{
  "employeeUserId": "665f0a1b2c3d4e5f6a7b8c9d",
  "expenseType": "Travel & Refreshment",
  "expenseAmount": 370,
  "paymentMethod": "Cash",
  "notes": "Supplier visit"
}
```

The employee must belong to the owner's shop. Response `201`: same shape, `userId` = the employee.

### PATCH `/expenses/{expenseId}` — edit

Any of `expenseAmount`, `notes`, `expenseType`, `expenseDate`, `paymentMethod`, `employeeUserId` (employee expenses only; must be
an employee of the shop); send at least one. Leaving a field out (or `null`) keeps it; `"notes": ""` clears the notes. The shop and
the kind (shop vs employee expense) cannot change; line items are not edited here.

```json
{ "expenseAmount": 2100, "paymentMethod": "UPI", "notes": "EB bill for October (revised)" }
```

Money effect: `totalExpense` changes by (new amount − old amount). Petty cash changes by the **cash part** before vs after
(cash part = the amount when the method is Cash, else 0):

| Change | `pettyCash` |
|---|---|
| Cash 500 → Cash 800 | − 300 (**rejected if the balance does not cover it**) |
| Cash 500 → UPI 500 | + 500 (back to petty cash) |
| UPI 500 → Cash 500 | − 500 (rejected if too low) |
| Cash 500 → Card 800 | + 500 |
| non-cash amount change | unchanged |

The shop is updated first, then the expense only if its amount and payment method are still the ones read; otherwise the shop
change is undone and `400 "This expense was changed or deleted by another request, please reload and try again"` is returned.
Other errors: `"This is a shop expense; it has no employee to change"` · `"Employee not found in your shop"` ·
`"paymentMethod must be one of [Cash, UPI, Card, Bank Transfer, Other]"`.

Response `200`: the updated expense (same shape as create, with the new balances).

### DELETE `/expenses/{expenseId}`

Query: `reason` (optional, max 300 characters) — the deletion note shown in the Deleted tab.

The expense is first copied to the `deleted_expenses` archive (with who deleted it, when and the reason), then removed; then it is
subtracted from `totalExpense` (never below 0) and, for Cash, the amount goes back to petty cash. Deleting the same expense twice at
once refunds only once (the second archive copy is removed again); if the shop update fails, the expense is restored and its archive
copy removed.

Response `204` (no body). Errors: `400 "Expense not found"` · `400 "reason must be at most 300 characters"`.

### GET `/expenses/deleted`

Query: `shopId` (platform `OWNER`), `from`, `to` (**deletion** date, IST), `search` (category, notes or reason), `type`
(`shop` / `employee`), `pageNumber`, `pageSize`. Newest deletion first. Each row:

```json
{ "archiveId": "6674d3e2f3a4b5c6d7e8f9a0", "expenseId": "6674d3e2f3a4b5c6d7e8f9a1", "shopId": "665f…",
  "userId": null, "employeeName": null, "expenseType": "Electricity Bill", "expenseAmount": 1850.0,
  "paymentMethod": "Cash", "notes": "EB bill for October", "expenseDate": "...",
  "deletedAt": "...", "deletedBy": "665f…", "deletedByName": "Saritha R", "reason": "Entered twice" }
```

Expenses deleted before this archive existed are not listed (they were removed permanently).

### GET `/expenses/summary`

The same filters as `GET /expenses` (without paging/sort). Response:

```json
{ "count": 42, "totalAmount": 38450.0, "shopExpenses": 21500.0, "employeeExpenses": 16950.0,
  "byType": [ { "name": "Salary", "amount": 15000.0, "count": 2 }, { "name": "Rent", "amount": 12000.0, "count": 1 } ],
  "byPaymentMethod": [ { "name": "Cash", "amount": 20450.0, "count": 30 }, { "name": "UPI", "amount": 18000.0, "count": 12 } ] }
```

### GET `/expenses` — list / search

| Query | Meaning |
|---|---|
| `shopId` | Platform `OWNER` only (required for them) |
| `from`, `to` | Expense date range (yyyy-MM-dd, inclusive, IST) |
| `search` | Text in the expense type or notes — literal, case-insensitive |
| `paymentMethod` | A payment method (any spelling); matches every stored spelling of it, e.g. `Card` also finds old `Debit/Credit Cards` |
| `expenseType` | Exact category, case-insensitive (e.g. `Rent`) |
| `type` | `shop` (no `userId`) · `employee` (has `userId`) · omitted = both |
| `employeeUserId` | Only this employee's expenses |
| `sort` | `newest` (default) · `oldest` (by expense date) |
| `pageNumber`, `pageSize` | Paging (default 0 / 20, max 100) |

Response `200`: [paginated](#paginated-responses) expenses, each like:

```json
{
  "id": "6671a0b9c1d2e3f4a5b6c7d8",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "userId": "665f0a1b2c3d4e5f6a7b8c9e",
  "employeeName": "Lakshmi T",
  "expenseType": "Travel",
  "expenseAmount": 350.0,
  "paymentMethod": "Cash",
  "notes": "Supplier visit",
  "expenseDate": "2026-11-10T05:30:00.000+00:00",
  "travelDetails": [ { "date": "2026-11-10", "source": "Anna Nagar", "destination": "T Nagar", "amount": 350.0, "description": "Supplier visit" } ]
}
```

Fields that are `null` are left out: shop expenses have no `userId` / `employeeName`, and `pettyCashBalance` / `shopTotalExpense` appear only in write responses. `employeeName` is resolved in one query per page and is missing if the account no longer exists.

Fixes vs the old lists: the search text is matched literally (the old search treated it as a regex, so `(` or `*` broke it) and also searches notes; an empty page is an empty list, not an error.

Errors (`400`): `"type must be shop or employee"` · `"sort must be newest or oldest"` · `"to must not be before from"` · paging errors · `"shopId is required"` / `"Shop Not Found"` (OWNER) · `"User is not assigned to any shop"`. Also `401` · `403`.

### GET `/expenses/{expenseId}`

Query: `shopId` (platform `OWNER` only). Response `200`: one expense (shape above). Errors: `400` `"Expense not found"` (also another shop's) · `401` · `403`.

### Expense errors

| Status | Example |
|---|---|
| 400 | `{"expenseAmount": "expenseAmount must be greater than 0"}` · `{"travelDetails[0].date": "date must be in yyyy-MM-dd format"}` · `{"employeeUserId": "employeeUserId is required"}` |
| 400 | `{"anyFieldProvided": "Provide expenseAmount and/or notes to update"}` |
| 400 | `"You do not have sufficient petty cash to spend, please contact owner to provide petty cash"` |
| 400 | `"User is not assigned to any shop"` · `"Employee does not belong to your shop"` · `"Expense not found"` (also another shop's) |
| 400 | `"This expense was changed or deleted by another request, please reload and try again"` (concurrent edit) |
| 401 | no token |
| 403 | not a shop owner |

---

## 6. Payments & receipts

Paths below are relative to `/v1/api` · Replaces `/v1/api/receipt/**` and `PUT /v1/api/invoices/{id}/update-payment`

A **receipt** is the final, fully paid bill; generating it marks the order **delivered**.

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/invoices/{invoiceId}/account` | Shop owner | Totals, payments, balance |
| POST | `/invoices/{invoiceId}/payments` | Shop owner | Record a payment (creates a **payment receipt**) |
| POST | `/invoices/{invoiceId}/discount` | Shop owner | One-time discount + credit note |
| POST | `/invoices/{invoiceId}/receipt` | Shop owner | Generate receipt = deliver |
| GET | `/receipts` | Shop owner (own shop) / `OWNER` (all) | List receipts |
| GET | `/receipts/{receiptNo}` | same | One receipt |
| GET | `/receipts/{receiptNo}/pdf` | same | Receipt PDF |
| GET | `/invoices/{invoiceId}/payment-receipts` | Shop owner (own shop) / `OWNER` | Payment receipts of an invoice |
| GET | `/payment-receipts/{paymentReceiptNo}` | same | One payment receipt |
| GET | `/payment-receipts/{paymentReceiptNo}/pdf` | same | Payment receipt PDF |

### Payment methods (one list)

`GET /v1/api/reference/payment-methods` → `["Cash", "UPI", "Card", "Bank Transfer", "Other"]` (any signed-in user). Invoices
(advance), payments and expenses store these names. Inputs also accept old spellings and store the canonical name
(`cash` → Cash, `Debit/Credit Cards` → Card, `BANK` → Bank Transfer, `GPay`/`PhonePe` → UPI, `NEFT`/`IMPS` → Bank Transfer,
`other` → Other); anything else is `400 "paymentMethod must be one of [...]"`. Reports and the expense filter map old stored
spellings too, so totals are correct before any data is changed.

`POST /v1/api/admin/migrations/payment-methods?dryRun=true` (platform `OWNER`): lists (dry run, default) or rewrites
(`dryRun=false`) old spellings in invoices (`paymentMethod`, `paymentHistories.paidVia`), receipts, payment receipts and expenses.
Unknown spellings are reported, never changed. Idempotent.

### Invoice account object

Returned by account, payment and discount endpoints:

```json
{
  "invoiceId": "SARI-12-11-2026-00042",
  "totalAmount": 3050.0,
  "discount": 50.0,
  "taxableAmount": 3000.0,
  "cgstAmount": 75.0,
  "sgstAmount": 75.0,
  "igstAmount": 0.0,
  "totalAmountWithGST": 3150.0,
  "advancePaid": 2500.0,
  "balanceDue": 650.0,
  "paymentStatus": "Partial",
  "creditNoteId": "6673e5f6a7b8c9d0e1f2a3b4",
  "payments": [
    { "amount": 1000.0, "paidVia": "UPI", "paidAt": "2026-11-13T05:30:00.000+00:00", "transactionId": "UPI-4589123" }
  ]
}
```

`balanceDue` is always `totalAmountWithGST` − `advancePaid`. `paymentStatus`: `Dues` / `Partial` / `Paid`. `creditNoteId` only appears right after a discount.

### GET `/invoices/{invoiceId}/account`

Response `200`: [invoice account](#invoice-account-object). Errors: `400` `"Invoice not found"` · `403`.

### POST `/invoices/{invoiceId}/payments`

```json
{ "amount": 650, "paymentMethod": "UPI", "transactionId": "UPI-4589123" }
```

| Field | Rules |
|---|---|
| `amount` | required, > 0, ≤ balance due |
| `paymentMethod` | required, ≤ 30 |
| `transactionId` | optional, ≤ 100 |

Response `201`: updated [invoice account](#invoice-account-object), plus `paymentReceipt` — the [payment receipt](#payment-receipts) created for this payment. When the balance reaches 0, status becomes `Paid` and `paymentCompletedDate` is set.

Every recorded payment gets a numbered **payment receipt** (client requirement "partial payment receipt"), and the customer receives it by WhatsApp / email with a download link. See [Payment receipts](#payment-receipts).

| Status | Message |
|---|---|
| 400 | `{"amount": "amount must be greater than 0", "paymentMethod": "paymentMethod is required"}` |
| 400 | `"Payment 2000.0 exceeds the balance due 1650.0"` · `"This invoice is already fully paid"` |
| 400 | `"The receipt for this invoice has already been generated; it can no longer change"` |
| 409 | `"The invoice was changed by someone else in the meantime. Please reload it and try again."` |

### POST `/invoices/{invoiceId}/discount`

```json
{ "amount": 100, "reason": "Delay in delivery" }
```

| Field | Rules |
|---|---|
| `amount` | required, > 0, ≤ invoice `totalAmount` (pre-GST) |
| `reason` | optional, ≤ 200; default "Owner discount" |

One discount per invoice. GST is recalculated on the reduced amount (same GST type as the invoice), and a credit note is stored in `credit_notes`.

Response `200`: updated [invoice account](#invoice-account-object) with `creditNoteId`.

| Status | Message |
|---|---|
| 400 | `"A discount of 50.0 is already applied to this invoice; only one discount is allowed"` |
| 400 | `"Discount 4000.00 exceeds the invoice amount 3050.00"` |
| 400 | `"The customer has already paid 3000.0, more than the discounted total 2940.00. Refunds are not supported; reduce the discount."` |
| 409 | concurrent change |

### POST `/invoices/{invoiceId}/receipt` — generate receipt (deliver)

No body. Requirements: **fully paid** and **every item `ORDER_COMPLETED`**.

What happens: receipt number `{SHOP}-{dd-MM-yyyy}-{00001}` → receipt saved (copy of invoice items and amounts) → invoice gets `receiptId`, `orderStatus: ORDER_DELIVERED`, `paymentStatus: Paid` → customer linked → activity `ORDER_DELIVERED` → customer gets WhatsApp / email with the receipt link.

Response `201` (new) or `200` (already existed, `created: false`):

```json
{
  "id": "6673d4e5f6a7b8c9d0e1f2a3",
  "receiptNo": "SARI-13-11-2026-00007",
  "invoiceId": "SARI-12-11-2026-00042",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "customerName": "Priya K",
  "customerPhone": "9876543210",
  "receiptDate": "2026-11-13T06:00:00.000+00:00",
  "items": [
    { "itemUniqueId": "Blouse_1762935000000_0", "itemName": "Blouse", "itemQuantity": 2, "itemPrice": 850.0, "itemOrderStatus": "ORDER_COMPLETED" }
  ],
  "totalAmount": 3050.0,
  "discount": 50.0,
  "taxableAmount": 3000.0,
  "cgstAmount": 75.0,
  "sgstAmount": 75.0,
  "igstAmount": 0.0,
  "totalAmountWithGST": 3150.0,
  "amountPaid": 3150.0,
  "paymentStatus": "Paid",
  "paymentMethod": "Cash",
  "orderStatus": "ORDER_DELIVERED",
  "gstin": "33ABCDE1234F1Z5",
  "pdfUrl": "/v1/api/receipts/SARI-13-11-2026-00007/pdf",
  "notifications": { "whatsApp": "QUEUED", "email": "SKIPPED" },
  "created": true
}
```

| Status | Message |
|---|---|
| 400 | `"Collect the remaining balance of 650.0 before generating the receipt (POST .../payments)"` |
| 400 | `"All items must be ORDER_COMPLETED before delivery. Not finished: Blouse (IRONING)"` |
| 400 | `"Invoice not found"` |
| 409 | concurrent change |

### GET `/receipts`

Query: `shopId` (platform owner only — filter by shop; ignored for shop owners), `from` / `to` (receipt date,
inclusive IST days, `yyyy-MM-dd`, either optional), `search` (receipt number, invoice number, or customer name / phone;
contains, any case), `pageNumber`, `pageSize` (1–100).

Response `200`: paginated receipts, newest first (same object as above, without `notifications` / `created`).
`400`: `to` before `from`, bad paging.

### GET `/receipts/{receiptNo}`

Response `200`: the receipt. Errors: `400` `"Receipt not found"` (also another shop's) · `403`.

### GET `/receipts/{receiptNo}/pdf`

Response `200`, `application/pdf`, `attachment; filename="<receiptNo>.pdf"`. Errors: `400` `"Receipt not found"` · `"Could not generate the receipt PDF, please try again"` · `403`.

### Payment receipts

A **payment receipt** is created automatically for every payment recorded with `POST /invoices/{invoiceId}/payments` — the customer's proof of an advance / part / final payment, showing what was paid and **what is still due**. (The final [receipt](#post-invoicesinvoiceidreceipt--generate-receipt-deliver) is still issued on delivery.)

- **Number:** `{SHOP}-PR-{dd-MM-yyyy}-{00001}`, e.g. `SARI-PR-14-11-2026-00003` — its own running number per shop and year, so it is never confused with invoice or receipt numbers.
- **Stored** in `payment_receipts`; the invoice's payment entry (`payments[].paymentReceiptNo` in the [account](#invoice-account-object)) links to it.
- **Customer:** WhatsApp (same document template as invoices: name, number, amount received, link) and email (amount received + balance due) with a secure link `{website}/download-payment-receipt/{token}`.
- **PDF:** one page — shop header (name, address, phone, GSTIN), receipt no., date / time, customer, invoice no., amount received, payment method, transaction id; then invoice total (incl. GST), total paid so far, balance due, payment status. Amounts with 2 decimals, `Rs.` prefix.
- Created **before** the invoice is updated: if the payment then fails (`409`), the payment receipt is removed and its number released, so every recorded payment has exactly one payment receipt.
- Not created for: the advance taken when the invoice is created (it is printed on the invoice as "Advance Paid"), and payments recorded before this feature existed.

Payment receipt object (in `paymentReceipt` of the payment response, and from the endpoints below):

```json
{
  "paymentReceiptNo": "SARI-PR-14-11-2026-00003",
  "invoiceId": "SARI-12-11-2026-00042",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "customerName": "Priya K",
  "customerPhone": "9876543210",
  "paidAt": "2026-11-14T06:30:00.000+00:00",
  "amount": 650.0,
  "paidVia": "UPI",
  "transactionId": "UPI-4589123",
  "invoiceTotalWithGST": 3150.0,
  "totalPaid": 2150.0,
  "balanceDue": 1000.0,
  "paymentStatus": "Partial",
  "receivedBy": "Saritha R",
  "pdfUrl": "/v1/api/payment-receipts/SARI-PR-14-11-2026-00003/pdf",
  "notifications": { "whatsApp": "QUEUED", "email": "QUEUED" }
}
```

`totalPaid` / `balanceDue` / `paymentStatus` are the invoice's position **right after** that payment. `notifications` only appears in the payment response. Null fields are left out.

| Endpoint | Returns |
|---|---|
| `GET /invoices/{invoiceId}/payment-receipts` | All payment receipts of the invoice, oldest first (`[]` if none) |
| `GET /payment-receipts/{paymentReceiptNo}` | One payment receipt · `400 "Payment receipt not found"` (also another shop's) |
| `GET /payment-receipts/{paymentReceiptNo}/pdf` | The PDF (`application/pdf`, file `{paymentReceiptNo}.pdf`) |

Access: shop owner (own shop) and platform `OWNER` (any shop; optional `?shopId=` filter). Customers use the public link — `GET /v1/api/public/documents/payment-receipts/{token}`.

---

## 7. Employees

Paths below are relative to `/v1/api` · Manages login users in the shared `users` collection (not the unused legacy `employees` collection). New accounts are created with [`POST /v1/api/auth/users`](#post-users-platform-owner-only).

| Method | Path | Purpose |
|---|---|---|
| GET | `/shops/{shopId}/employees` | Employees of a shop |
| GET | `/employees/unassigned` | Staff who work in no shop (can be assigned) |
| PUT | `/employees/{userId}/assignment` | Assign to a shop (platform owner: also move) |
| DELETE | `/shops/{shopId}/employees/{userId}/assignment` | Remove from the shop; account kept |
| PATCH | `/shops/{shopId}/employees/{userId}` | Update name, email, phone, salary, address |
| DELETE | `/shops/{shopId}/employees/{userId}` | Delete the account |

Rules:
- **One shop per user.** Every module finds "the user's shop" through `shops.employeeIds`, so a user is never in two shops.
- **Platform `OWNER`:** manages every shop, and can move a user from one shop to another.
- **`SHOP_OWNER`:** manages only their own shop and only **staff** accounts (`TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT`, `ACCOUNTANT`), plus their own details. They can assign a staff user only when that user works in **no** shop, so they cannot pull employees away from another shop.
- **Protected accounts:** shop owner and platform owner accounts cannot be removed or deleted here, and nobody can delete their own account.
- **When someone leaves a shop** (removed, moved or deleted), their in-progress items go back one step, with the same rules as the item workflow: tailor of a `TAILOR_ASSIGNED` / `STITCHING` item → `CUTTING`; shop assistant of a `SHOP_ASSISTANT_ASSIGNED` / `HEMMING` / `IRONING` item → `STITCHING`. Finished stages keep the record of who did the work, and the item history gets `UNASSIGNED_DUE_TO_EMPLOYEE_REMOVAL` / `UNASSIGNED_DUE_TO_EMPLOYEE_DELETION`.
- **Sign-out:** removing an employee, deleting them, or changing their email revokes their tokens.

### Employee object

```json
{
  "userId": "665f0a1b2c3d4e5f6a7b8c9d",
  "name": "Lakshmi",
  "email": "lakshmi@example.com",
  "phone": "9876543210",
  "salary": 18000.0,
  "address": "12, 2nd Avenue, Anna Nagar, Chennai",
  "roles": ["TAILOR"],
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "shopName": "Saritha Fashions - Anna Nagar",
  "accountCreationDate": "2026-11-01T05:30:00.000+00:00",
  "lastLoginDate": "2026-11-13T04:10:00.000+00:00"
}
```

`shopId` / `shopName` are `null` for unassigned users.

### GET `/shops/{shopId}/employees`

Query: `search` (optional) — keeps only employees whose name, email, phone or user id contains the text (case-insensitive), e.g. `?search=laks`.

Response `200`: array of [employees](#employee-object), sorted by name (includes the shop owner).

Errors: `400` `"Shop Not Found"` · `401` (no token) · `403` (wrong role, or not your shop).

### GET `/employees/unassigned`

Response `200`: array of [employees](#employee-object) with a staff role who work in no shop (e.g. after being removed from one). Errors: `403`.

### PUT `/employees/{userId}/assignment`

```json
{ "shopId": "665f1c2e9b1e8a3d4c7f0a12" }
```

Response `200`: the [employee](#employee-object) with the new shop. Assigning to the shop they already work in changes nothing.

| Status | Message |
|---|---|
| 400 | `{"shopId": "shopId is required"}` |
| 400 | `"No user found"` · `"Shop Not Found"` · `"The platform owner cannot be assigned to a shop"` |
| 400 | `"This user already works in another shop. Only the platform owner can move them."` |
| 403 | not your shop, or a shop owner assigning a non-staff account |

### DELETE `/shops/{shopId}/employees/{userId}/assignment`

No body. Releases their in-progress items, unlinks them from the shop and signs them out; the account is kept.

Response `200`: the [employee](#employee-object), now with `shopId: null`.

| Status | Message |
|---|---|
| 400 | `"User is not an employee of this shop"` · `"The shop owner cannot be removed from their shop"` |
| 401 | no token |
| 403 | wrong role, or not your shop |

### PATCH `/shops/{shopId}/employees/{userId}`

Only the fields sent change; send at least one. Roles, password and shop are not changed here.

```json
{ "name": "Lakshmi R", "email": "lakshmi.r@example.com", "phone": "9876543210", "salary": 19000, "address": "Anna Nagar, Chennai" }
```

| Field | Rules |
|---|---|
| `name` | ≤ 100, not blank |
| `email` | valid, ≤ 150; stored lower-case; must be unique. Changing it signs the employee out (they sign in with the new email) |
| `phone` | 10 digits, or `""` to clear |
| `salary` | 0 – 10,000,000 |
| `address` | ≤ 300, or `""` to clear |

Response `200`: the updated [employee](#employee-object).

| Status | Message |
|---|---|
| 400 | `{"anyFieldProvided": "Provide at least one of name, email, phone, salary, address"}` |
| 400 | `{"phone": "phone must be a 10-digit number", "name": "name must not be blank", "salary": "salary must not be negative"}` |
| 400 | `"User is not an employee of this shop"` |
| 403 | not your shop, or a shop owner editing another shop owner |
| 409 | `"An account with email lakshmi@example.com already exists"` |

### DELETE `/shops/{shopId}/employees/{userId}`

No body. Releases their in-progress items, removes them from every shop, deletes the account, and logs `USER_DELETED` in the activity log. To keep the account, use `DELETE .../assignment` instead.

Response `204` (no body).

| Status | Message |
|---|---|
| 400 | `"Shop owner and platform owner accounts cannot be deleted here"` · `"You cannot delete your own account"` · `"User is not an employee of this shop"` |
| 401 | no token |
| 403 | wrong role, or not your shop |

## 8. Reports

Base path: `/v1/api/reports` · Replaces `/v1/api/financials/**`, `/v1/api/dashboard/**`, `/v1/api/employee/activities/**`, `/v1/api/deleted/invoices/**` and the legacy shop dashboard rows

| Method | Path | Purpose |
|---|---|---|
| GET | `/shops/overview` | One row per shop (platform owner's dashboard) |
| GET | `/financials/summary` | Sales, collections, expenses, petty cash, outstanding for a period |
| GET | `/financials/daily` | The same per day |
| GET | `/activities` | Employee activity log (paginated) |
| GET | `/activities/by-employee` | Activity counts per employee |
| GET | `/activities/daily` | Activity counts per day |
| GET | `/financials/monthly` | Sales, collections, expenses per month (all time by default) |
| GET | `/deleted-invoices/{archiveId}` | One deleted invoice with all its details |
| GET | `/branch-report` | One branch's report for a period as Excel or PDF (download) |
| GET | `/activities/employee-analytics` | One employee's performance figures (assigned, quantity, value, reassignments) or, for role SHOP_OWNER, the shop's item figures |
| GET | `/my-activity` | The logged-in employee's own activity |
| GET | `/deliveries` | Delivery dashboard: due today (ready / in production), overdue, delivered today |
| GET | `/production` | Production health: open items per stage (overdue, days in stage) + turnaround of orders delivered in the period, per branch + total |
| GET | `/customers` | Customers: active / new / returning, repeat rate, months, top 20, customers with dues |
| GET | `/deleted-invoices` | Archived invoices (paginated) |

All endpoints are read-only (`GET`).

**Shop scope** (differs from the other modules): a `SHOP_OWNER` always gets their own shop; the platform `OWNER` passes `shopId` for one shop, or **leaves it out for all shops**. `/shops/overview` is `OWNER` only; `/my-activity` is for the caller themself.

**Periods:** `from` / `to` are inclusive IST days (`yyyy-MM-dd`). Without them, summaries cover all time. Day-by-day endpoints (`/financials/daily`, `/activities/daily`, `/my-activity`) default to the last 30 days (`to` = today) and allow at most 366 days.

**Where the numbers come from** — computed at request time from the real data, never stored:

| Figure | Source |
|---|---|
| Sales (`invoiceCount`, `invoicedAmount`, taxable, GST, discount) | Invoices whose `invoiceDate` is in the period |
| Collections | Payment entries (`paymentHistories`) whose `paidAt` is in the period, amount > 0 — the advance at creation plus later payments. Payment methods are grouped ignoring case. |
| Expenses | Expenses whose `expenseDate` is in the period; shop expenses have no `userId`, employee expenses do |
| `netCashFlow` | collections − expenses |
| Petty cash `added` / `currentBalance` | Top-ups in the period / the shop's current balance |
| Outstanding, pending orders | Invoices not yet delivered **now** (not limited to the period) and their balance due |
| Receipts / deleted invoices | Receipts by `receiptDate` / archives by `deletedAt` in the period |

Fixes vs the legacy reports:
- The legacy financials and profit read `financial_records`, which only the manual `POST /v1/api/financials/record` filled. That endpoint is **not** ported; nothing needs to be recorded any more.
- The legacy shop dashboard's expense totals were always `0` (its expense lookup dropped the amount and payment method), and it counted paid invoices as `"PAID"` while invoices store `"Paid"`.
- The legacy activity and financial endpoints accepted any `shopId` from any logged-in user; now they are limited by role and shop.
- The legacy deleted-invoice search by customer name never matched (the archive stores the customer as a reference); it now searches the customer's name and phone.

### GET `/shops/overview`

Query: `from`, `to` (optional). Response `200`, one row per shop sorted by name:

```json
[
  {
    "shopId": "665f1c2e9b1e8a3d4c7f0a12",
    "shopName": "Saritha Fashions - Anna Nagar",
    "shopType": "Boutique",
    "ownerName": "Saritha R",
    "employeeCount": 6,
    "pettyCash": 4500.0,
    "invoiceCount": 42,
    "invoicedAmount": 98400.0,
    "paidInvoiceCount": 30,
    "deliveredInvoiceCount": 28,
    "collected": 62750.0,
    "expenses": 21500.0,
    "cashExpenses": 9800.0,
    "netCashFlow": 41250.0,
    "pendingOrders": 12,
    "outstanding": 18600.0,
    "receiptCount": 38
  }
]
```

`invoiceCount`, `paidInvoiceCount`, `deliveredInvoiceCount`, `invoicedAmount` cover invoices dated in the period; `pendingOrders` and `outstanding` are current. `employeeCount` includes the shop owner.

Errors: `400` `"to must not be before from"` · `401` · `403` (not the platform owner).

### GET `/financials/summary`

Query: `shopId` (OWNER, optional), `from`, `to`. Response `200`:

```json
{
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "shopName": "Saritha Fashions - Anna Nagar",
  "from": "2026-11-01",
  "to": "2026-11-30",
  "sales": { "invoiceCount": 42, "invoicedAmount": 98400.0, "taxableAmount": 93714.29, "gstAmount": 4685.71, "discount": 1200.0 },
  "collections": {
    "total": 62750.0,
    "byPaymentMethod": [
      { "paymentMethod": "Cash", "amount": 30500.0, "count": 21 },
      { "paymentMethod": "UPI", "amount": 32250.0, "count": 25 }
    ]
  },
  "expenses": {
    "total": 21500.0, "count": 17, "shopExpenses": 18000.0, "employeeExpenses": 3500.0,
    "byPaymentMethod": [ { "paymentMethod": "Cash", "amount": 9800.0, "count": 12 } ],
    "byType": [ { "expenseType": "Rent", "amount": 12000.0, "count": 1 } ]
  },
  "pettyCash": { "added": 15000.0, "currentBalance": 4500.0 },
  "netCashFlow": 41250.0,
  "outstanding": { "openInvoiceCount": 12, "balanceDue": 18600.0 },
  "receiptCount": 38,
  "deletedInvoiceCount": 2
}
```

For all shops (OWNER without `shopId`), `shopId` and `shopName` are `null` and every figure is the total over all shops. `byPaymentMethod` is sorted by amount, largest first, and uses the canonical payment-method names (old spellings are mapped, see [Payment methods](#payment-methods-one-list)); `expenses.byType` = per category (case-insensitive), largest first; `from` / `to` echo the request (`null` = open).

Errors: `400` `"to must not be before from"` · `"Shop Not Found"` · `"User is not assigned to any shop"` · `401` · `403`.

### GET `/financials/daily`

Query: `shopId` (OWNER, optional), `from`, `to` (default the last 30 days, max 366). Response `200`, one row per day, oldest first, zeros included:

```json
[
  { "date": "2026-11-12", "invoiceCount": 3, "invoicedAmount": 6200.0, "collected": 4500.0, "expenses": 850.0, "netCashFlow": 3650.0 }
]
```

Errors: as above, plus `400` `"A daily report can cover at most 366 days"`.

### GET `/financials/monthly`

Query: `shopId` (platform `OWNER`), `from`, `to`. One row per IST month, oldest first:
`{ "month": "2026-11", "invoiceCount": 84, "invoicedAmount": 196800.0, "collected": 125500.0, "expenses": 43000.0, "netCashFlow": 82500.0 }`.
Without `from`: from the first month with any sale, payment or expense (the dashboard's "Overall" chart); without `to`: up to the
current month. Months without data have zeros; no data at all and no `from` → `[]`.

### GET `/activities`

Query: `shopId` (OWNER, optional), `employeeId`, `type`, `invoiceId` (invoice number — one invoice's activities, for its timeline), `from`, `to`, `sort` (`newest` default or `oldest`), `pageNumber`, `pageSize`. [Paginated](#paginated-responses); each entry:

```json
{
  "id": "6674d3e2f3a4b5c6d7e8f9a0",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "employeeId": "665f0a1b2c3d4e5f6a7b8c9d",
  "employeeName": "Saritha R",
  "type": "ASSIGNED_TO_TAILOR",
  "invoiceId": "SARI-12-11-2026-00042",
  "assignedTo": "665f0a1b2c3d4e5f6a7b8c9e",
  "notes": "Assigned Blouse to tailor",
  "timestamp": "2026-11-12T06:15:00.000+00:00"
}
```

`type` (case-insensitive) is one of: `ASSIGNED_TO_TAILOR_MASTER`, `TAILOR_MASTER_CUTTING`, `ASSIGNED_TO_TAILOR`, `TAILOR_STARTED`, `TAILOR_COMPLETED`, `ASSIGNED_TO_ASSISTANT`, `ASSISTANT_IRONING`, `TAILOR_STITCHING`, `ASSISTANT_HEMMING`, `ASSISTANT_COMPLETED`, `INVOICE_CREATED`, `INVOICE_DELETED`, `RECEIPT_DELETED`, `ORDER_COMPLETED`, `ORDER_DELIVERED`, `USER_DELETED`.

Errors: `400` `"type must be one of [...]"` · paging errors · `"to must not be before from"` · `401` · `403`.

### GET `/activities/by-employee`

Query: `shopId` (OWNER, optional), `from`, `to`. Response `200`, most active first:

```json
[
  {
    "employeeId": "665f0a1b2c3d4e5f6a7b8c9e",
    "employeeName": "Lakshmi T",
    "total": 57,
    "countsByType": { "ASSIGNED_TO_ASSISTANT": 20, "TAILOR_STITCHING": 21, "TAILOR_COMPLETED": 16 },
    "lastActivityAt": "2026-11-12T11:40:00.000+00:00"
  }
]
```

`employeeName` is the account's current name (or the name stored with the activity if the account is gone).

### GET `/activities/employee-analytics`

Performance figures of one employee in a period: the same figures as the legacy
`GET /v1/api/employee/activities/employee/{employeeId}/analytics`, computed from `employee_activities` the same way
(verified against the legacy aggregations).

Query: `employeeId` (not needed for role `SHOP_OWNER`), `role` (required: `TAILOR_MASTER`, `TAILOR`, `SHOP_ASSISTANT`,
`SHOP_OWNER`; case-insensitive), `from`, `to` (inclusive IST days), `shopId` (platform owner only).

| Role | Figures |
|---|---|
| `TAILOR_MASTER` | The master's own activities: `assigned` = items given to a tailor (first assignments); `totalQuantity` / `totalRevenue` = quantity / value (price × quantity, before GST) of all first-time activities; `totalReassignments`, `reassignmentQuantity`, `reassignmentRevenue` |
| `TAILOR` | Activities done by or assigned to the tailor: `totalAssigned` = items assigned to the tailor; `assigned` = `totalCompleted` = items handed to a shop assistant (stitched), with quantity / value; reassignments |
| `SHOP_ASSISTANT` | Activities done by or assigned to the assistant: `totalAssigned` = items assigned to them; `assigned` / `totalCompleted` = items completed, with quantity / value; reassignments |
| `SHOP_OWNER` | The shop's invoices dated in the period: `itemStatusCounts` (every workflow status, 0 when none), `totalRevenue` (Σ price × quantity), `totalItems` (Σ quantity), `invoiceCount`, `averageRevenuePerInvoice`, `receiptCount`, `deletedInvoiceCount` |

```json
{ "employeeId": "665f0a1b2c3d4e5f6a7b8c9e", "role": "TAILOR", "from": "2026-11-01", "to": "2026-11-15",
  "assigned": 12, "totalQuantity": 18.5, "totalRevenue": 15400.0, "totalReassignments": 1,
  "reassignmentQuantity": 2.0, "reassignmentRevenue": 1700.0, "totalAssigned": 14, "totalCompleted": 12 }
```

Fields that do not apply to the role are left out. Money and quantities are rounded to 2 decimals.
`400`: unknown role, `employeeId` missing, `to` before `from`, shop not found.

### GET `/activities/daily`

Query: `shopId` (OWNER, optional), `employeeId` (optional — one employee), `from`, `to` (default the last 30 days, max 366). Response `200`: `[ { "date": "2026-11-12", "count": 7 } ]`, one row per day, zeros included.

### GET `/my-activity`

For a shop owner, tailor master, tailor or shop assistant — their own activities (all shops). Query: `from`, `to` (default the last 30 days, max 366). Response `200`:

```json
{
  "employeeId": "665f0a1b2c3d4e5f6a7b8c9e",
  "from": "2026-10-15",
  "to": "2026-11-13",
  "total": 57,
  "countsByType": { "ASSIGNED_TO_ASSISTANT": 20, "TAILOR_STITCHING": 21, "TAILOR_COMPLETED": 16 },
  "daily": [ { "date": "2026-10-15", "count": 0 }, { "date": "2026-10-16", "count": 3 } ]
}
```

Errors: `400` range errors · `401` · `403` (platform owner or other roles).

### GET `/branch-report` — branch report (Excel / PDF download)

Everything about **one branch** for a period, as a file. The platform `OWNER` passes `shopId`; a `SHOP_OWNER` always
gets their own branch (`shopId` ignored).

| Query | Meaning |
|---|---|
| `shopId` | Branch (platform OWNER; required for them) |
| `from`, `to` | Required, inclusive IST days (`yyyy-MM-dd`); one day = the same date twice; at most **366 days** |
| `format` | `xlsx` (default) or `pdf` |

Response: the file itself (`Content-Type` xlsx / `application/pdf`,
`Content-Disposition: attachment; filename="SarithaFashions_<Branch>_<from>_<to>.<ext>"`, one day: `…_<date>.<ext>`;
the branch part is the name after " - ", letters / digits / dashes only).

Contents (both formats, the same numbers):
- header: branch, address, phone, GSTIN, shop owner, period, generated on / by
- summary vs the previous period of the same length (sales, taxable, GST, discount, collected, expenses, net,
  delivered, petty cash added, deleted invoices) + outstanding / open invoices / petty cash balance now
- GST split: intra-state (CGST + SGST) and inter-state (IGST) — a business summary, not a GST return
- collected by payment method, expenses by category, day by day
- orders (invoices dated in the period), payments received (each payment entry, with its payment receipt when there is
  one), deliveries (receipts), expenses + deleted expenses, petty cash top-ups
- production now + turnaround and time per stage, staff work (counts per activity type), customers (+ top 10),
  deleted invoices, sales by item (pieces, amount before GST)

Format notes:
- **xlsx**: one sheet per section (Summary, Daily, Orders, Payments, Deliveries, Expenses, Petty cash, Production,
  Staff, Customers, Deleted invoices, Items); numbers are numbers (₹ Indian format), dates are dates, frozen header,
  auto-filter, `SUM` totals.
- **pdf**: A4, built as a Word document and converted by the PDF service; lists capped at 500 rows
  ("+ N more — see the Excel report"); periods over 62 days show the day-by-day table per week.

Errors: 400 (dates missing / reversed / over 366 days, unknown `format`, shop not found, `shopId` missing for the
OWNER) · 403 (not OWNER / SHOP_OWNER) · **502** `{ "message": "The PDF service is not available right now…" }` when the
PDF conversion fails (the Excel still works).

### GET `/customers` — customer analytics

`shopId` (OWNER only; leave out for all shops) · `from` / `to` (period; open = all time). Built from the live invoices;
a customer is **new** in the period (or month) of their first invoice in scope, **returning** otherwise.

- `totalCustomers` (all time), `activeCustomers` / `newCustomers` / `returningCustomers` (period), `repeatRate`
  (% with 2+ invoices, all time), `avgSpendPerCustomer` (invoiced incl. GST in the period / active customers).
- `months[]` `{month: "yyyy-MM", newCustomers, returningCustomers}`: the period's months (at most 12); a single-month or
  open period shows the last 12.
- `top[]` (20, by amount in the period) `{customerId, name, phone, invoices, spent, lifetimeInvoices, lastVisit, branches}`.
- `withDues[]` (50, largest first) `{customerId, name, phone, openOrders, balanceDue, oldestDue}` — undelivered orders
  with a balance; `customersWithDues` and `totalDue` count all of them.

### GET `/production` — production health

`shopId` (OWNER only; leave out for all shops) · `from` / `to` (turnaround period; open = all time).

- **Now** (every undelivered invoice): per branch `openItems`, `overdueItems` (order past its delivery date) and
  `stages[]` in workflow order: `{status, items, overdue, avgDaysInStage, maxDaysInStage}`. Days in stage = since the
  item's latest history entry with that status (ORDER_ACCEPTED without one: since the invoice date). `NoUse` items are
  left out.
- **Turnaround** (receipts generated in the period): `deliveredOrders`, `avgDaysToDeliver` (invoice → receipt),
  `onTimePercent` (receipt day ≤ expected delivery date, of the orders with a readable date; `withDueDate` = how many),
  `stageTimes[]` `{status, avgHours, samples}` from the item history (each entry lasts until the next; `READY` =
  ORDER_COMPLETED until delivery).
- `branches[]` (by name) + `total` (all of them; `shopName` "All branches" when no `shopId`).

```json
{ "today": "2026-11-19", "from": "2026-11-01", "to": "2026-11-30",
  "branches": [ { "shopId": "665f…", "shopName": "Navalur", "openItems": 42, "overdueItems": 6,
    "stages": [ { "status": "STITCHING", "items": 12, "overdue": 3, "avgDaysInStage": 2.4, "maxDaysInStage": 9.0 } ],
    "turnaround": { "deliveredOrders": 38, "avgDaysToDeliver": 11.6, "onTimePercent": 84.2, "withDueDate": 38,
      "stageTimes": [ { "status": "STITCHING", "avgHours": 52.5, "samples": 57 } ] } } ],
  "total": { "shopId": null, "shopName": "All branches", "…": "same fields" } }
```

### GET `/deliveries` — delivery dashboard

The orders to hand over on a day (client requirement "Today's delivery dashboard"). Query: `shopId` (OWNER, optional — all shops without it), `date` (`yyyy-MM-dd`, default **today**, IST).

Response `200`:

```json
{
  "date": "2026-11-14",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "summary": {
    "dueCount": 6, "dueReadyCount": 4, "dueInProductionCount": 2,
    "overdueCount": 3, "deliveredCount": 5, "withoutDeliveryDateCount": 1,
    "balanceToCollect": 8450.0
  },
  "due": [
    {
      "invoiceId": "SARI-10-11-2026-00031", "shopId": "665f1c2e9b1e8a3d4c7f0a12",
      "customerName": "Priya K", "customerPhone": "9876543210",
      "expectedDeliveryDate": "2026-11-14", "daysOverdue": 0,
      "ready": true, "itemCount": 2, "completedItemCount": 2,
      "items": [ { "itemName": "Blouse", "itemQuantity": 2, "status": "ORDER_COMPLETED" },
                 { "itemName": "Saree Fall", "itemQuantity": 1, "status": "ORDER_COMPLETED" } ],
      "totalAmountWithGST": 3150.0, "balanceDue": 1650.0, "paymentStatus": "Partial",
      "invoiceDate": "2026-11-10T05:00:00.000+00:00"
    }
  ],
  "overdue": [ { "...": "same shape, daysOverdue > 0" } ],
  "//": "delivered[].invoiceId is the invoice NUMBER (the receipt stores the invoice's database id; it is translated)",
  "delivered": [
    { "receiptNo": "SARI-14-11-2026-00007", "invoiceId": "SARI-09-11-2026-00027", "shopId": "665f...",
      "customerName": "Anu S", "customerPhone": "9123456780",
      "deliveredAt": "2026-11-14T06:20:00.000+00:00", "totalAmountWithGST": 2100.0,
      "invoiceDate": "2026-11-09T05:00:00.000+00:00", "expectedDeliveryDate": "2026-11-14", "itemCount": 2,
      "items": [ { "itemName": "Blouse", "itemQuantity": 1.0, "status": "ORDER_COMPLETED" } ] }
  ]
}
```

| List | Contains | Order |
|---|---|---|
| `due` | Not delivered, expected delivery = `date`. `ready` = every item `ORDER_COMPLETED` (hand over after collecting `balanceDue` → `POST /invoices/{id}/receipt`); otherwise the item statuses show what is still in production. | Ready first, then oldest invoice |
| `overdue` | Not delivered, expected delivery before `date`; `daysOverdue` = days late | Most overdue first |
| `delivered` | Receipts generated on `date` | Time order |

- `balanceToCollect` = balance due of all `due` + `overdue` orders.
- Delivery dates are read from the stored text in the formats `2026-11-25`, `25-11-2026`, `25/11/2026`, `25.11.2026` (day first). Open orders without a readable date are only counted in `withoutDeliveryDateCount` — edit them with a proper date to make them appear. New and edited invoices always store `yyyy-MM-dd` (see invoice create).
- Future dates work too (`?date=2026-11-15` = tomorrow's deliveries); for a past date, `due` shows orders that were due then and are still not delivered.

Errors: `400` `"Shop Not Found"` · `"User is not assigned to any shop"` · `401` · `403`.

### GET `/deleted-invoices`

Query: `shopId` (OWNER, optional), `search` (invoice number, customer name or phone; literal, case-insensitive, max 100), `from` / `to` (**deletion** date), `pageNumber`, `pageSize`. Most recently deleted first, [paginated](#paginated-responses); each entry:

```json
{
  "archiveId": "6673c2d1e2f3a4b5c6d7e8f9",
  "shopId": "665f1c2e9b1e8a3d4c7f0a12",
  "invoiceId": "SARI-12-11-2026-00042",
  "invoiceDate": "2026-11-12T05:00:00.000+00:00",
  "customerName": "Priya K",
  "customerPhone": "9876543210",
  "itemCount": 3,
  "totalAmountWithGST": 3150.0,
  "advancePaid": 1500.0,
  "balanceDue": 1650.0,
  "paymentStatus": "Partial",
  "deletedAt": "2026-11-13T06:10:00.000+00:00",
  "deletedBy": "665f0a1b2c3d4e5f6a7b8c9d",
  "deletedByName": "Saritha R",
  "reason": "Duplicate invoice"
}
```

Customer and `deletedByName` are `null` when those records no longer exist. Errors: `400` paging / `"search must be at most 100 characters"` / `"to must not be before from"` · `401` · `403`.

### GET `/deleted-invoices/{archiveId}`

Query: `shopId` (platform `OWNER`, optional). Everything the invoice had when it was deleted, plus who deleted it, when and why:

```json
{ "archiveId": "6674d3e2f3a4b5c6d7e8f9a0", "deletedAt": "...", "deletedBy": "665f…", "deletedByName": "Saritha R",
  "reason": "Duplicate invoice",
  "invoice": { "id": "6672b1c0…", "invoiceId": "SARI-12-11-2026-00042", "shopId": "665f…",
               "customer": { "id": "…", "customerId": "CUS-8F3K2Q1ZXA", "name": "Priya K", "phone": "9876543210", "email": null },
               "items": [ { "itemName": "Blouse", "itemQuantity": 2.5, "itemPrice": 850.0, "itemOrderStatus": "CUTTING",
                            "measurements": { "Chest": "36" }, "assignmentHistory": [ … ] } ],
               "totalAmountWithGST": 3150.0, "advancePaid": 1500.0, "paymentHistories": [ … ], "invoiceDate": "...", "notes": "…" } }
```

`invoice` is the stored invoice with ids as plain strings. A SHOP_OWNER only sees their shop's entries. Errors: `400 "Deleted invoice not found"` · `401` · `403`.

---

## Core module (shared services + customer downloads)

`modules/core` holds services the other modules share, plus the public customer-download endpoints.

| Package | Class | Purpose |
|---|---|---|
| `core.whatsapp` | `WhatsAppMessagingService` | Twilio WhatsApp "document ready" template (`{{1}}` name, `{{2}}` document no., `{{3}}` amount, `{{4}}` link). 10-digit numbers get `+91`. Throws `WhatsAppDeliveryException` with Twilio's error code. |
| `core.mailing` | `BrevoMailService` | Brevo email: `send(MailMessage)` (inline HTML) and `sendTemplate(...)` (Brevo template). Throws `MailDeliveryException` with Brevo's HTTP code and response body. |
| `core.documentlink` | `CustomerDocumentLinkService`, `DocumentTokenService` | Secure customer links `{twillo.website.link}/download-invoice/{token}` and `/download-receipt/{token}`. The token is AES-256-GCM encrypted and tamper-proof, keyed by `customer.document-link.secret`; the format is unchanged, so links already sent keep working. |
| `core.documentlink` | `CustomerDocumentController` | Public PDF download endpoints for those links (below). |
| `core.pdf` | `SalesDocumentPdfService` | Invoice / receipt PDFs: fills the Word templates (`Receipt_SF.docx` for invoices, `Invoice_SF.docx` for receipts — the legacy names are swapped) and converts them with the Cloud Run service. Output is identical to the legacy code (verified on the generated Word XML). |
| `core.numbering` | `DocumentNumberService` | Invoice / receipt numbers `{PREFIX}-{dd-MM-yyyy}-{00001}`, per shop and year, in the legacy `invoice_sequence` / `receipt_sequence` counters. Numbers of deleted invoices are never re-issued. |
| `core.activity` | `ActivityLogService` | Employee activity records in `employee_activities` (same document shape as the legacy service). |
| `core.async` | `NotificationDispatcher` | Small background thread pool. Notifications run after the data is saved; failures are logged and never fail the request. |

### Customer downloads (public, no login)

The token in the customer's link is the authorisation. Invalid tokens are rejected before any database or converter work.

| Method | Path | Returns |
|---|---|---|
| GET | `/v1/api/public/documents/invoices/{token}` | The invoice PDF (`application/pdf`, `Cache-Control: no-store`) |
| GET | `/v1/api/public/documents/receipts/{token}` | The receipt PDF |
| GET | `/v1/api/public/documents/payment-receipts/{token}` | A payment receipt PDF (link `{website}/download-payment-receipt/{token}`, sent after each payment) |

`{token}` is the part of the link after `/download-invoice/` or `/download-receipt/`.

Errors (`400`): `{"message": "This download link is not valid", "status": false}` (malformed, altered, or wrong document type) · `"Invoice not found"` / `"Receipt not found"` (deleted since) · `"Could not generate the invoice PDF, please try again"` (converter failure).

These replace the legacy `/v1/api/public/download-invoice/{token}` (returned **base64 text**) and `/download-receipt/{token}`, which were removed. The frontend's download pages must call the new paths and handle a binary PDF instead of base64 text.

### PDF converter warm-up

`GET /v1/api/pdf-service/warmup` (any signed-in user) → `200` text `Warmup triggered!` — same path and response as the legacy endpoint. Wakes the Cloud Run converter in the background so the next PDF is fast; calls within a minute of the previous one are ignored. Call it when the app starts or the invoice screen opens.

PDF changes vs the legacy code (layout, rows and number formatting are unchanged):
- A shop without a GST number (or a customer without a phone) no longer crashes PDF generation; the field is left blank.
- A missing payment method, payment status, delivery date or HSN code prints `-` instead of `null`.
- The converter call has timeouts, and a failed conversion is an error instead of an empty "PDF".
- No temp `.docx` file is left on disk per PDF.

Customer messages:

| Event | WhatsApp | Email |
|---|---|---|
| Invoice created | always (phone required) | if the customer has an email |
| Receipt generated | always | if the customer has an email |
| Password reset | — | Brevo template with the 6-digit code |

---

## Configuration

New optional properties (defaults in brackets):

| Property | Used for |
|---|---|
| `twilio.template.document-sid` [`HXe6f4ff67cb16b5e9de82d3f5c0a5296e`] | WhatsApp template for invoice / receipt links |
| `brevo.sender.email` [`fp@sarithasdesignerstudio.com`] | Sender of all module emails (must be verified in Brevo) |
| `brevo.sender.name` [`sarithasdesignerstudio`] | Sender name |
| `brevo.template.password-reset` [`1`] | Brevo template id of the password-reset email |
| `pdf.converter.url` [`https://docs-to-pdf-new-156538435086.asia-south1.run.app/convert`] | docx → PDF converter (the URL the legacy code hard-codes) |
| `pdf.converter.connect-timeout-ms` [`10000`] · `pdf.converter.read-timeout-ms` [`90000`] | Converter timeouts |
| `pdf.converter.warmup-url` [`https://docs-to-pdf-new-156538435086.asia-south1.run.app/warmup`] | Converter warm-up URL |

Existing properties used: `twilio.accountSid`, `twilio.authToken`, `twilio.whatsappFrom`, `brevo.api.key`, `twillo.website.link`, `customer.document-link.secret`, `spring.app.jwtSecret`, `spring.app.jwtExpirationMs`.

New collection: `password_reset_otps` (indexes `userId` unique and a TTL on `expiresAt`, created at startup).

---

## Old → new endpoint map

| Old endpoint | New endpoint |
|---|---|
| `POST /v1/api/auth/signin` | `POST /v1/api/auth/sign-in` |
| `POST /v1/api/auth/refresh-token` | `POST /v1/api/auth/token/refresh` |
| `POST /v1/api/auth/signup` | `POST /v1/api/auth/users` |
| `POST /v1/api/auth/request-reset/{email}` | `POST /v1/api/auth/password-reset/code` |
| `POST /v1/api/auth/reset` | `POST /v1/api/auth/password-reset` |
| — | `POST /v1/api/auth/sign-out`, `GET /v1/api/auth/me` |
| `POST /v1/api/owner/shop` | `POST /v1/api/shops` |
| `GET /v1/api/shops/{id}` · `PUT /v1/api/shops/{id}` | `GET` · `PUT /v1/api/shops/{shopId}` |
| `GET /v1/api/owner/shop/add-petty-cash/{amount}/{shopId}` | `POST /v1/api/shops/{shopId}/petty-cash` |
| `GET /v1/api/owner/petty-cash/{shopId}/all-pettyCash` · `/{shopId}/search-by-amount/{term}` · `GET /v1/api/shops/petty-cash` | `GET /v1/api/shops/{shopId}/petty-cash` (`?from=&to=&amount=`) |
| shop-name lists built from `GET /v1/api/owner/shop` | `GET /v1/api/shops/names` |
| `POST /v1/api/invoices/generate-invoice` | `POST /v1/api/invoices` (returns JSON; PDF via `pdfUrl`) |
| `GET /v1/api/invoices/all-invoice` · `/shop/{shopId}` · `/all-invoice-with-all-details` · `/special-category/all-invoice` · `GET /v1/api/shopOwner/invoices/search-invoice` · `GET /v1/api/owner/invoice/{shopId}/all-invoice` · `GET /v1/api/owner/order/{shopId}/all-pendingOrders` | `GET /v1/api/invoices` (`?details=true`, `?special=true`, `?search=`, `?status=pending`, `?shopId=`) |
| `GET /v1/api/owner/invoice/{shopId}/download/{invoiceId}` · `POST /v1/api/owner/invoice/invoice-download` | `GET /v1/api/invoices/{invoiceId}/pdf?shopId=` |
| — | `GET /v1/api/invoices/{invoiceId}`, `PATCH /v1/api/invoices/{invoiceId}` |
| `DELETE /v1/api/shopOwner/invoices/{shopId}/delete-invoice/{invoiceId}` | `DELETE /v1/api/invoices/{invoiceId}?reason=` (no receipt allowed; the latest invoice's number is re-used, as before) |
| `PUT /v1/api/tailor-master/my-order/update-status` · `PUT /v1/api/tailor/orders/update-status` · `PUT /v1/api/shop-assistant/orders/update-status` | `PATCH /v1/api/invoices/{invoiceId}/items/{itemUniqueId}/status` |
| `POST /v1/api/tailor-master/assign-tailor` | `PUT .../items/{itemUniqueId}/tailor` |
| `POST /v1/api/tailor/assign-shopAssistant` | `PUT .../items/{itemUniqueId}/shop-assistant` |
| `GET /v1/api/tailor-master/all-tailor` · `GET /v1/api/tailor/all-shopAssistant` | `GET /v1/api/items/assignees/tailors` · `/shop-assistants` |
| role-specific `orders` lists | `GET /v1/api/items/queue` |
| `POST /v1/api/expenses/add` | `POST /v1/api/expenses` |
| `POST /v1/api/expenses/add-employee-expense` | `POST /v1/api/employee-expenses` |
| expense delete | `DELETE /v1/api/expenses/{expenseId}` |
| — | `PATCH /v1/api/expenses/{expenseId}` |
| `GET /v1/api/shops/view-expenses` · `/view-expenses/{shopId}` · `/view-expenses/byPaymentMethod` · `GET /v1/api/expenses/shop/{shopId}` · `/all` | `GET /v1/api/expenses` (`?shopId=&from=&to=&search=&paymentMethod=&type=&employeeUserId=`), `GET .../expenses/{expenseId}` |
| `PUT /v1/api/invoices/{id}/update-payment` | `POST /v1/api/invoices/{invoiceId}/payments` and `/discount` |
| `POST /v1/api/receipt/download/receipt` | `POST /v1/api/invoices/{invoiceId}/receipt` |
| `GET /v1/api/receipt/{id}` · `/shop/{shopId}` · `/all-receipt` | `GET /v1/api/receipts/{receiptNo}` · `/receipts` |
| legacy shop dashboard rows (`findAllShopSummaries`, used by `GET /v1/api/owner/shop` and `/v1/api/shops`) | `GET /v1/api/reports/shops/overview` |
| `GET /v1/api/financials/shop/{shopId}` · `/date/{date}` · `/profit/shop/{shopId}` · `/profit/all` · `GET /v1/api/dashboard/financial-summary/shop/{shopId}` · `/financial-summary/all` | `GET /v1/api/reports/financials/summary` (`?shopId=&from=&to=`), `GET /v1/api/reports/financials/daily` |
| `POST /v1/api/financials/record` | — (not needed: figures are computed from the data) |
| `GET /v1/api/dashboard/all-invoices` | `GET /v1/api/invoices?shopId=` |
| `GET /v1/api/employee/activities/summary` | `GET /v1/api/reports/activities/by-employee`, `GET /v1/api/reports/activities?employeeId=` |
| `GET /v1/api/employee/activities/employee/{employeeId}/analytics` | `GET /v1/api/reports/activities/employee-analytics?employeeId=&role=&from=&to=` |
| `GET /v1/api/employee/activities/employee/{employeeId}/daily` | `GET /v1/api/reports/activities/daily?employeeId=` |
| role analytics for the logged-in employee | `GET /v1/api/reports/my-activity` |
| `GET /v1/api/deleted/invoices/all-deleted-invoices` | `GET /v1/api/reports/deleted-invoices` |
| `GET /v1/api/public/download-invoice/{token}` (base64 text) | `GET /v1/api/public/documents/invoices/{token}` (PDF) |
| `GET /v1/api/public/download-receipt/{token}` | `GET /v1/api/public/documents/receipts/{token}` |
| `PUT /v1/api/owner/shop/update-owner-details` (`shopId`, `oldOwnerUserId`, `newOwnerName`, ...) | `PUT /v1/api/shops/{shopId}/owner` (`ownerUserId`, `name`, `phone`, `email`, `password`, `salary`, `reason`) |
| `GET /v1/api/owner/shop/backup-history/user/{userId}` · `/shop/{shopId}` | — (removed; backups are still written to `user_backup_history`) |
| `GET /v1/api/pdf-service/warmup` | same path (now in `modules/core/pdf`) |

Response shapes changed in a few places (e.g. sign-in returns `userId` / `name` instead of `id` / `username`; invoice creation returns JSON instead of a wrapped PDF; payment status is `Paid` / `Partial` instead of `PAID` / `PARTIAL`). The old endpoints have been removed from the backend.
