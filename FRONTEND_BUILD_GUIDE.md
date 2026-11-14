# Frontend Build Guide — Saritha Fashions (New Module API)

**For:** a frontend team building the Saritha Fashions web app **from scratch** on the new module API  
**Backend:** Spring Boot, branch `opt/shop-management` · **Date:** 2026-11-13  
**Companion docs:** [`MODULES_API_DOCUMENTATION.md`](MODULES_API_DOCUMENTATION.md) (every endpoint, field rule and error) · Swagger UI at `{API_BASE}/swagger-ui.html`

This guide explains the business, the roles, the app structure we recommend, every screen with the exact API calls it makes, TypeScript types for every payload, and the rules the UI must respect. Code samples use TypeScript + `fetch` and are framework-neutral (React, Vue, Angular all work).

---

## Contents

- [Part A — The business in 5 minutes](#part-a--the-business-in-5-minutes)
- [Part B — App architecture](#part-b--app-architecture)
- [Part C — Authentication and session](#part-c--authentication-and-session)
- [Part D — Screens by role](#part-d--screens-by-role)
  - [D1 Platform owner](#d1-platform-owner-owner)
  - [D2 Shop owner](#d2-shop-owner-shop_owner)
  - [D3 Tailor master, tailor, shop assistant](#d3-tailor-master-tailor-shop-assistant)
  - [D4 Public customer pages](#d4-public-customer-pages)
- [Part E — TypeScript types (all payloads)](#part-e--typescript-types-all-payloads)
- [Part F — Rules and enums reference](#part-f--rules-and-enums-reference)
- [Part G — Client-side validation (mirror of the server)](#part-g--client-side-validation-mirror-of-the-server)
- [Part H — Error handling catalogue](#part-h--error-handling-catalogue)
- [Part I — Local setup, testing and limits](#part-i--local-setup-testing-and-limits)

---

## Part A — The business in 5 minutes

Saritha Fashions runs **tailoring shops**. A customer orders garments; the shop bills them (invoice), the garments go through production (cutting → stitching → finishing), the customer pays (advance + balance), and on collection the shop issues a **receipt**.

### A.1 Roles

| Role | Who | Belongs to a shop? | Main jobs in the app |
|---|---|---|---|
| `OWNER` | Platform owner (the business owner) | No — sees **all** shops | Create shops and user accounts, move staff, top up petty cash, view everything and all reports |
| `SHOP_OWNER` | Manager of one shop | Yes (one) | Create/edit/delete invoices, collect payments, discounts, deliver (receipt), run production, expenses, staff of the shop, reports for the shop |
| `TAILOR_MASTER` | Head tailor | Yes | Cut garments, assign them to tailors |
| `TAILOR` | Tailor | Yes | Stitch assigned garments, hand over to a shop assistant |
| `SHOP_ASSISTANT` | Finisher | Yes | Hemming, ironing, mark the garment completed |
| `ACCOUNTANT` | (reserved) | Yes | No screens yet |

A user has one shop at most. The shop is returned at sign-in (`shopId`, `shopName`).

### A.2 Life of an order

```
 SHOP_OWNER                         PRODUCTION (per item)                                   SHOP_OWNER
 ───────────                         ─────────────────────                                   ───────────
 Create invoice ──► items start in   ORDER_ACCEPTED ─► CUTTING ─► TAILOR_ASSIGNED ─► STITCHING
 (+ advance)        ORDER_ACCEPTED   ─► SHOP_ASSISTANT_ASSIGNED ─► HEMMING ─► IRONING ─► ORDER_COMPLETED
                    (special items                                                                 │
                     start in CUTTING)                                                             ▼
 Collect payments / one-time discount ───────────────────────────────────────────────►  Generate receipt
                                                                       (needs: fully paid + all items completed)
                                                                       → invoice becomes ORDER_DELIVERED (final)
```

- An **invoice** has 1–50 **items** (garments). Each item moves through production **independently**.
- "Special" items (`Embroidery`, `Lining Material`, `Saree Fall`, `Saree Pico`, `Designing`) skip `ORDER_ACCEPTED` and start in `CUTTING`.
- Money is per invoice: total → discount → GST → total with GST → advance paid → balance due.
- The **receipt** is the final bill. Creating it delivers the order; afterwards nothing on the invoice can change.

### A.3 Money rules (show these numbers, never compute your own totals for saving)

| Value | Formula |
|---|---|
| `totalAmount` | Σ `itemPrice × itemQuantity` (before GST) |
| `taxableAmount` | `totalAmount − discount` |
| GST | customer in the **same state** as the shop → CGST 2.5% + SGST 2.5%; other state → IGST 5% |
| `totalAmountWithGST` | `taxableAmount + GST` |
| `balanceDue` | `totalAmountWithGST − advancePaid` |
| `paymentStatus` | `Dues` (nothing paid) · `Partial` · `Paid` |

The server calculates all of these. The UI may **preview** them while the user types (same formulas, round to 2 decimals), but always display the server's numbers after saving.

### A.4 Petty cash

Each shop has a petty-cash balance. The platform owner (or shop owner) **tops it up**; **cash** expenses are paid from it and are **rejected** if the balance is too low. Non-cash expenses (UPI, bank, card) don't touch it.

### A.5 Customer notifications

When an invoice or receipt is created, the customer automatically gets a WhatsApp message (and an email if they have one) with a secure download link `{website}/download-invoice/{token}` or `/download-receipt/{token}`. Your app must provide those two **public pages** ([D4](#d4-public-customer-pages)).

---

## Part B — App architecture

### B.1 Configuration

```ts
// config.ts
export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:5000';
export const TIME_ZONE = 'Asia/Kolkata';
```

Allowed browser origins are configured on the backend (CORS): `http://localhost:5173`, `http://localhost:5174`, `http://192.168.1.16:5173`, `https://sarithasdesignerstudio.com`, `https://testing.sarithasdesignerstudio.com`. Ask the backend team to add any other origin.

### B.2 Suggested folder structure

```
src/
  api/
    client.ts          # fetch wrapper: base URL, token, error normalising
    auth.ts            # sign-in, refresh, me, sign-out, password reset
    invoices.ts        # /v1/api/invoices
    accounts.ts        # payments, discount, receipt creation, /v1/api/receipts
    items.ts           # production: /v1/api/items, /v1/api/invoices/{id}/items
    expenses.ts
    shops.ts           # /v1/api/shops + petty cash + owner account
    employees.ts
    reports.ts
    publicDocs.ts      # customer download pages
  types/api.ts         # Part E types
  session/             # token storage, current user, refresh timer
  routing/             # route table + role guards
  features/            # one folder per screen group (Part D)
  lib/format.ts        # money, dates (IST), statuses
  lib/files.ts         # PDF download, image naming
```

### B.3 API client

```ts
// api/client.ts
import { API_BASE } from '../config';
import { session } from '../session';

export type ApiError =
  | { kind: 'fields'; status: 400; fields: Record<string, string> }   // field validation map
  | { kind: 'message'; status: number; message: string }               // 400 / 403 / 409 / 429 / 5xx
  | { kind: 'auth'; status: 401 }                                       // not signed in / revoked
  | { kind: 'network'; message: string };

export async function toApiError(res: Response): Promise<ApiError> {
  if (res.status === 401) return { kind: 'auth', status: 401 };
  let body: any = null;
  try { body = await res.json(); } catch { /* no JSON body */ }
  if (res.status === 400 && body && typeof body === 'object' && !('message' in body)) {
    return { kind: 'fields', status: 400, fields: body };
  }
  return { kind: 'message', status: res.status, message: body?.message ?? `Request failed (${res.status})` };
}

type Query = Record<string, string | number | boolean | string[] | undefined | null>;

function url(path: string, query?: Query) {
  const u = new URL(API_BASE + path);
  Object.entries(query ?? {}).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    (Array.isArray(v) ? v : [v]).forEach(x => u.searchParams.append(k, String(x)));
  });
  return u.toString();
}

export async function api<T>(method: string, path: string,
                             opts: { query?: Query; json?: unknown; form?: FormData; auth?: boolean } = {}): Promise<T> {
  const headers: Record<string, string> = {};
  if (opts.auth !== false && session.token) headers.Authorization = `Bearer ${session.token}`;
  let body: BodyInit | undefined;
  if (opts.form) body = opts.form;                       // never set Content-Type for FormData
  else if (opts.json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(opts.json); }

  let res: Response;
  try { res = await fetch(url(path, opts.query), { method, headers, body }); }
  catch (e: any) { throw { kind: 'network', message: e?.message ?? 'Network error' } as ApiError; }

  if (!res.ok) {
    const err = await toApiError(res);
    if (err.kind === 'auth') session.end();               // global: back to sign-in
    throw err;
  }
  if (res.status === 204) return undefined as T;
  const type = res.headers.get('Content-Type') ?? '';
  return (type.includes('application/json') ? res.json() : res.blob()) as Promise<T>;
}

export const get  = <T>(p: string, query?: Query) => api<T>('GET', p, { query });
export const post = <T>(p: string, json?: unknown, query?: Query) => api<T>('POST', p, { json, query });
export const put  = <T>(p: string, json?: unknown) => api<T>('PUT', p, { json });
export const patch = <T>(p: string, json?: unknown) => api<T>('PATCH', p, { json });
export const del  = <T>(p: string, query?: Query) => api<T>('DELETE', p, { query });
```

### B.4 Handling errors in screens

```ts
try {
  await save();
} catch (e) {
  const err = e as ApiError;
  if (err.kind === 'fields') form.setErrors(err.fields);          // keys like "customer.phone", "items[0].itemPrice"
  else if (err.kind === 'message' && err.status === 409) { toast.warning(err.message); await reload(); }
  else if (err.kind === 'message') toast.error(err.message);      // server messages are user-readable
  else if (err.kind === 'network') toast.error('No connection to the server');
  // 'auth' already redirected to sign-in by the client
}
```

Field keys use dot / bracket paths: `customer.phone`, `items[2].itemQuantity`, `travelDetails[0].date`, `images[1]`. Map `items[i].x` to the i-th row of your items editor.

### B.5 Paged lists

Every list endpoint returns:

```ts
export interface Page<T> {
  content: T[]; pageNumber: number; pageSize: number;
  totalElements: number; totalPages: number; lastPage: boolean;
}
```

- `pageNumber` is 0-based; `pageSize` 1–100 (default 20).
- Filters are query parameters; dates are `from` / `to` = `yyyy-MM-dd`, inclusive, Indian days.
- Re-fetch from page 0 when a filter changes; debounce search boxes (~300 ms).

### B.6 Formatting

```ts
// lib/format.ts
export const money = (n: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 }).format(n);

export const dateTime = (iso: string) =>
  new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));

export const day = (iso: string) =>
  new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium' }).format(new Date(iso));

/** yyyy-MM-dd of "today" in India, for default date filters */
export const todayIST = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
```

Timestamps from the API are UTC ISO strings (`2026-11-12T05:00:00.000+00:00`). `expectedDeliveryDate` is plain text as typed (usually `yyyy-MM-dd`) — display it as-is or parse carefully.

### B.7 Files: PDFs and images

```ts
// lib/files.ts
import { API_BASE } from '../config';
import { session } from '../session';
import { toApiError } from '../api/client';

/** Authenticated PDF download (invoice / receipt). The browser cannot read Content-Disposition, so pass the name. */
export async function downloadPdf(path: string, fileName: string, open = false) {
  const res = await fetch(API_BASE + path, { headers: { Authorization: `Bearer ${session.token}` } });
  if (!res.ok) throw await toApiError(res);
  const blobUrl = URL.createObjectURL(await res.blob());
  if (open) window.open(blobUrl, '_blank');
  else Object.assign(document.createElement('a'), { href: blobUrl, download: fileName }).click();
  setTimeout(() => URL.revokeObjectURL(blobUrl), 30_000);
}

/** Item image upload naming rule: item_{index}_{anything}.{ext}; index = 0-based position in items[] */
export const itemImageName = (index: number, file: File) => `item_${index}_${file.name.replace(/\s+/g, '_')}`;
```

- PDFs are generated on demand by an external converter: expect **2–10 s**. Show a spinner, disable the button, don't auto-retry.
- Images: **image types only, ≤ 10 MB, one per item**. Validate before upload; compress large photos client-side if you can.

### B.8 Route table and guards

```ts
type Role = 'OWNER' | 'SHOP_OWNER' | 'TAILOR_MASTER' | 'TAILOR' | 'SHOP_ASSISTANT' | 'ACCOUNTANT';

export const routes = [
  { path: '/sign-in', public: true },
  { path: '/forgot-password', public: true },
  { path: '/download-invoice/:token', public: true },      // customer link — keep this exact path
  { path: '/download-receipt/:token', public: true },      // customer link — keep this exact path

  // OWNER
  { path: '/owner', roles: ['OWNER'] },                      // dashboard
  { path: '/owner/shops', roles: ['OWNER'] },
  { path: '/owner/shops/:shopId', roles: ['OWNER'] },
  { path: '/owner/users/new', roles: ['OWNER'] },
  { path: '/owner/staff', roles: ['OWNER'] },

  // SHOP_OWNER
  { path: '/shop', roles: ['SHOP_OWNER'] },                  // dashboard
  { path: '/shop/invoices', roles: ['SHOP_OWNER'] },
  { path: '/shop/invoices/new', roles: ['SHOP_OWNER'] },
  { path: '/shop/invoices/:invoiceId', roles: ['SHOP_OWNER'] },
  { path: '/shop/invoices/:invoiceId/edit', roles: ['SHOP_OWNER'] },
  { path: '/shop/production', roles: ['SHOP_OWNER'] },
  { path: '/shop/receipts', roles: ['SHOP_OWNER'] },
  { path: '/shop/expenses', roles: ['SHOP_OWNER'] },
  { path: '/shop/petty-cash', roles: ['SHOP_OWNER'] },
  { path: '/shop/staff', roles: ['SHOP_OWNER'] },
  { path: '/shop/reports', roles: ['SHOP_OWNER'] },

  // Production staff (one shared screen)
  { path: '/work', roles: ['SHOP_OWNER', 'TAILOR_MASTER', 'TAILOR', 'SHOP_ASSISTANT'] },
  { path: '/work/:invoiceId/:itemUniqueId', roles: ['SHOP_OWNER', 'TAILOR_MASTER', 'TAILOR', 'SHOP_ASSISTANT'] },
  { path: '/my-activity', roles: ['SHOP_OWNER', 'TAILOR_MASTER', 'TAILOR', 'SHOP_ASSISTANT'] },
];

export const homeFor = (roles: Role[]) =>
  roles.includes('OWNER') ? '/owner' : roles.includes('SHOP_OWNER') ? '/shop' : '/work';
```

Route guards are for **navigation only**; the server enforces every permission (`403`). Never hide a security problem behind a hidden button.

---

## Part C — Authentication and session

Base path `/v1/api/auth`.

### C.1 Sign-in screen

| | |
|---|---|
| Call | `POST /v1/api/auth/sign-in` `{ email, password }` |
| Success | `200` → `AuthenticatedUser` (token in `accessToken`) |
| Errors | `400` field map (`email`, `password`) · `401 "Invalid email or password"` · `429` too many attempts (20/min per IP) |

```ts
export async function signIn(email: string, password: string) {
  const u = await api<AuthenticatedUser>('POST', '/v1/api/auth/sign-in', { json: { email, password }, auth: false });
  session.start(u);                       // store token, user, expiry; schedule refresh
  router.navigate(homeFor(u.roles));
}
```

UX: one error message for wrong email **or** password (the server doesn't say which). Disable the button while signing in.

### C.2 Session storage and restore

```ts
// session/index.ts
export const session = {
  token: localStorage.getItem('token') as string | null,
  user: null as Me | null,
  expiresAt: Number(localStorage.getItem('expiresAt') ?? 0),

  start(u: AuthenticatedUser) {
    this.token = u.accessToken; this.user = u;
    this.expiresAt = Date.now() + (u.expiresIn ?? 0) * 1000;
    localStorage.setItem('token', u.accessToken);
    localStorage.setItem('expiresAt', String(this.expiresAt));
    scheduleRefresh();
  },
  end() {
    this.token = null; this.user = null;
    localStorage.removeItem('token'); localStorage.removeItem('expiresAt');
    router.navigate('/sign-in');
  },
};

// on app start:
export async function restoreSession() {
  if (!session.token) return;
  try { session.user = await get<Me>('/v1/api/auth/me'); scheduleRefresh(); }
  catch { session.end(); }                 // revoked / expired
}
```

`GET /me` returns `{ userId, name, email, roles, shopId?, shopName? }`. Use it to restore the user after a page reload — never trust a cached user without it.

### C.3 Token refresh

`POST /v1/api/auth/token/refresh` (no body) with the **current, still-valid** token returns a new `AuthenticatedUser`. An expired token cannot be refreshed.

```ts
let timer: number | undefined;
function scheduleRefresh() {
  clearTimeout(timer);
  const lead = 5 * 60_000;                                   // refresh 5 minutes before expiry
  const wait = Math.max(10_000, session.expiresAt - Date.now() - lead);
  timer = window.setTimeout(async () => {
    try { session.start(await post<AuthenticatedUser>('/v1/api/auth/token/refresh')); }
    catch { session.end(); }
  }, wait);
}
```

Also refresh when the tab becomes visible again if the expiry is near. Roles in the refreshed response are the user's **current** roles — update the UI from them.

### C.4 Sign-out

`POST /v1/api/auth/sign-out` → `204`. This revokes **all** the user's tokens on every device. Call it, then `session.end()` (even if the call fails).

A user is also signed out automatically (next request → `401`) when: they reset their password, the shop removes them, their account is deleted, or their email is changed.

### C.5 Forgot password (two screens)

1. **Request code** — `POST /v1/api/auth/password-reset/code` `{ email }` → `202 { message }`. The response is **always the same**, whether the email exists or not; show it and move to step 2. Allow "resend" after 60 s (server limit: one code per minute; a new code cancels the old one).
2. **Set new password** — `POST /v1/api/auth/password-reset` `{ email, code, newPassword, confirmPassword }` → `200 { message: "Password has been changed. Please sign in again." }` → go to sign-in.

| Field | Rule |
|---|---|
| `code` | exactly 6 digits, valid 10 minutes, single use |
| `newPassword` | 8–100 chars, ≥ 1 letter and ≥ 1 digit |
| `confirmPassword` | equals `newPassword` (error key: `passwordConfirmed`) |

Errors: `400 "Invalid or expired code. Please check the code or request a new one."` · `429` after 5 wrong codes ("wait a minute and request a new code").

---

## Part D — Screens by role

Each screen lists: **purpose**, **API calls**, **fields / columns**, **actions**, **states and edge cases**.

### D1 Platform owner (`OWNER`)

The platform owner has no shop. Every shop-specific read needs `?shopId=` — keep a **shop picker** in the header (from `GET /v1/api/shops/names`) and pass the selected id. Reports also accept **no** `shopId` = all shops.

#### D1.1 Owner dashboard

| Call | Use |
|---|---|
| `GET /v1/api/reports/shops/overview?from&to` | One card / table row per shop |
| `GET /v1/api/reports/financials/summary?from&to` (no `shopId`) | Totals across all shops |
| `GET /v1/api/reports/financials/daily?from&to` | Line chart (≤ 366 days, default last 30) |

Shop row columns (`ShopOverview`): name, owner, employees, petty cash, invoices (count, amount), paid count, delivered count, collected, expenses (cash), net cash flow, pending orders, outstanding, receipts. Period picker (this month / last month / custom); `pendingOrders` and `outstanding` are **current** regardless of period — label them "now".

#### D1.2 Shops list and create

| Call | Use |
|---|---|
| `GET /v1/api/shops/summaries` | List (name, contact, GST, petty cash, total expense) |
| `POST /v1/api/shops` | Create shop + its owner account |

Create form (`CreateShopRequest`): shop name*, owner name*, owner email*, owner password*, owner phone (10 digits; also the shop contact), shop address, GST number, owner salary. On `201` navigate to the shop detail. Errors: field map; `"Email is already in use: ..."` → put it on the email field.

#### D1.3 Shop detail

Tabs:

| Tab | Calls |
|---|---|
| Overview / edit | `GET /v1/api/shops/{shopId}` · `PUT /v1/api/shops/{shopId}` (send only changed fields: `shopName`, `ownerName`, `shopAddress`, `shopContactNumber`, `gstNumber`, `shopType`) — **never send `allInvoice` / `allReceipts`** |
| Owner account | `PUT /v1/api/shops/{shopId}/owner` `{ name?, phone?, email?, password?, salary?, reason?, ownerUserId? }` → `ShopOwnerUpdate`. Previous details are backed up; a new email or password signs the owner out (`signedOut: true`) — tell the platform owner to share the new login. A new name / phone also updates the shop's owner name / contact number. |
| Petty cash | `GET .../{shopId}/petty-cash?from&to&amount&pageNumber&pageSize` (history, newest first) · `POST .../{shopId}/petty-cash` `{ amount }` → shows new balance |
| Staff | see D1.4 with this `shopId` |
| Invoices | `GET /v1/api/invoices?shopId=...` + detail / PDF with `?shopId=` (read-only for the owner) |
| Expenses | `GET /v1/api/expenses?shopId=...` (read-only) |
| Receipts | `GET /v1/api/receipts?shopId=...` · PDF `GET .../receipts/{receiptNo}/pdf` |
| Reports | D2.9 calls with `shopId` |

There is no delete for shops.

#### D1.4 Staff and user accounts

| Action | Call |
|---|---|
| List a shop's staff (+ search) | `GET /v1/api/shops/{shopId}/employees?search=` |
| Create an account | `POST /v1/api/auth/users` (roles + `shopId`; `shopId` required for every role except `OWNER`) |
| Staff without a shop | `GET /v1/api/employees/unassigned` |
| Assign / **move** to a shop | `PUT /v1/api/employees/{userId}/assignment` `{ shopId }` |
| Remove from shop (keep account) | `DELETE /v1/api/shops/{shopId}/employees/{userId}/assignment` |
| Edit details | `PATCH /v1/api/shops/{shopId}/employees/{userId}` `{ name?, email?, phone?, salary?, address? }` |
| Delete account | `DELETE /v1/api/shops/{shopId}/employees/{userId}` → `204` |

Explain in confirm dialogs: removing or deleting someone **releases their in-progress items** (they go back one production step) and signs them out. Shop-owner and platform-owner accounts can't be removed/deleted here; nobody can delete themselves. Changing an email signs that person out.

#### D1.5 Reports (owner)

Same as [D2.9](#d29-reports) with an optional shop filter ("All shops" = no `shopId`).

---

### D2 Shop owner (`SHOP_OWNER`)

Never send `shopId` on invoice / expense / receipt / item / report calls — the server uses the owner's shop. Where a path needs it (petty cash, staff), use `session.user.shopId`.

#### D2.1 Shop dashboard

| Widget | Call |
|---|---|
| Today / this month: invoices, invoiced, collected, expenses, net, petty cash balance, outstanding | `GET /v1/api/reports/financials/summary?from&to` |
| Trend chart | `GET /v1/api/reports/financials/daily?from&to` |
| Production counts | `GET /v1/api/items/queue?pageSize=1&status=<S>` → use `totalElements` per status (or one call without status and group the page) |
| Ready to deliver | `GET /v1/api/items/queue?status=ORDER_COMPLETED` |
| Pending invoices | `GET /v1/api/invoices?status=pending&pageSize=5` |

#### D2.2 Invoice list

`GET /v1/api/invoices`

| Filter (query) | UI control |
|---|---|
| `status` = `pending` / `delivered` / `all` | Tabs |
| `from`, `to` | Date range (invoice date) |
| `search` | Search box — invoice number, customer name or phone |
| `paymentStatus` = `Dues` / `Partial` / `Paid` | Chips |
| `special=true` | Toggle "Special items only" |
| `sort` = `newest` / `oldest` | Sort menu |
| `pageNumber`, `pageSize` | Pager |

Columns from `InvoiceSummary`: invoice number, date, customer (name, phone), items (`itemNames`, `completedItemCount / itemCount` progress), total with GST, paid, balance, payment status badge, order status badge, actions (open, PDF).

#### D2.3 Create invoice

`POST /v1/api/invoices`

**Form sections**

1. **Customer** — phone* (10 digits; the server finds the existing customer by phone or creates a new one — existing customer details are **not** changed), name*, email (if set, the customer also gets an email), alternate phone, address.
2. **Customer state*** — dropdown of Indian states; same as the shop's state → CGST+SGST, otherwise IGST. Default to the shop's state.
3. **Items** (1–50 rows) — item name* (free text or a catalogue dropdown; note the 5 special names), quantity* (1–1000), unit price* (0–10,000,000), measurements (key/value pairs → `measurements` object, or structured rows → `measurementList`), one optional photo per item.
4. **Amounts** — discount (≤ total), advance paid (≤ total with GST), payment method (default `Cash` if an advance is paid), transaction id. Show a live preview using [A.3](#a3-money-rules-show-these-numbers-never-compute-your-own-totals-for-saving).
5. **Delivery & notes** — expected delivery date (text, e.g. `2026-11-25`), notes (≤ 500).
6. **Back-dated invoice** (optional toggle) — `isBackDate: true` + `backDate` (`yyyy-MM-dd`, not in the future).

**Submitting**

```ts
export async function createInvoice(data: CreateInvoiceRequest, photos: Map<number, File>) {
  if (photos.size === 0) {
    return post<Invoice>('/v1/api/invoices', data);              // JSON variant
  }
  const form = new FormData();
  form.append('invoiceJson', JSON.stringify(data));
  photos.forEach((file, index) => form.append('images', file, itemImageName(index, file)));
  return api<Invoice>('POST', '/v1/api/invoices', { form });   // multipart variant
}
```

- **Don't send** `totalAmount` (or send exactly Σ price × qty — a mismatch is rejected).
- Remember the photo index is the item's **position in `items`** — if the user deletes a row, re-index photos.
- On `201`: show the invoice number, the notification status (`notifications.whatsApp` / `.email` = `QUEUED` or `SKIPPED`), and buttons "Download PDF" / "Open invoice".
- Errors: field map with keys like `customer.phone`, `items[1].itemPrice`, `totalAmount`, `discount`, `advancePaid`, `backDate`, `images[0]`; message `"User is not assigned to any shop"`.

#### D2.4 Invoice detail

`GET /v1/api/invoices/{invoiceId}` → `Invoice`

Show: header (number, dates, customer, state), items table (name, qty, price, status badge, assignees, photo thumbnail `imageUrl`, measurements), amounts block, payments list (`paymentHistories`), notes, `receiptId`.

Actions (enable/disable from data):

| Action | Enabled when | Call |
|---|---|---|
| Download PDF | always | `GET .../invoices/{invoiceId}/pdf` → `{invoiceId}.pdf` |
| Edit | `receiptId === null && orderStatus !== 'ORDER_DELIVERED'` | D2.5 |
| Delete | same as edit | `DELETE .../invoices/{invoiceId}?reason=...` (ask for a reason, ≤ 300 chars) → `DeleteInvoiceResponse` |
| Payments / discount / deliver | `receiptId === null` | D2.6 |
| Production view | items not completed | D2.7 |
| Open receipt | `receiptId !== null` | `GET /v1/api/receipts/{receiptId}` |

Delete confirm text: "The invoice will be archived and removed. Its number will not be reused. Items in production are removed too."

#### D2.5 Edit invoice

`PATCH /v1/api/invoices/{invoiceId}` — JSON, or multipart with `editJson` + `images`.

- Prefill from the invoice. Send only what changed: `expectedDeliveryDate`, `notes` (`""` → default text), `discount`, and/or `items`.
- If you send `items`, send the **complete list after the edit**:
  - existing item → include `itemUniqueId` (+ `itemName`, `itemQuantity`, `itemPrice`; `measurements` / `measurementList` only if changed; `removeImage: true` to drop its photo);
  - new item → no `itemUniqueId`;
  - removed item → leave it out.
- **Lock items already in production** (assigned, or status past its start status): show them read-only and always send them unchanged. Rule of thumb: an item is editable if `itemOrderStatus` is `ORDER_ACCEPTED` (or `CUTTING` for special items) **and** `assignedTo` and `assignedToWhichShopAssistant` are empty.
- New photos: `item_{index}_...` where index = position in the `items` you send.
- Customer, customer state, invoice date and payments are not editable.
- `409` → "Someone updated this invoice (payment or production) while you were editing. Reload and try again." Reload, re-apply the user's changes if possible.
- Error `{"items": "the new total with GST would be less than the 1500.0 already paid"}` → explain the total can't go below what was paid.

#### D2.6 Payments, discount and delivery (account panel)

Use a side panel / tab on the invoice detail.

| Step | Call |
|---|---|
| Load | `GET /v1/api/invoices/{invoiceId}/account` → `InvoiceAccount` |
| Record payment | `POST .../invoices/{invoiceId}/payments` `{ amount, paymentMethod, transactionId? }` → `201 InvoiceAccount` |
| One-time discount | `POST .../invoices/{invoiceId}/discount` `{ amount, reason? }` → `200 InvoiceAccount` (+ `creditNoteId`) |
| Deliver (receipt) | `POST .../invoices/{invoiceId}/receipt` (no body) → `201` new / `200` existing `Receipt` |

UI rules:
- Payment amount field: default = `balanceDue`, max = `balanceDue`, > 0. Payment method required (Cash, UPI, Card, Bank...). Hide the form when `paymentStatus === 'Paid'`.
- Discount button only if `discount === 0` on the invoice (one per invoice); amount ≤ `totalAmount` (pre-GST). Warn that GST is recalculated.
- **Deliver** button enabled only if `balanceDue === 0` **and** every item is `ORDER_COMPLETED` (from the invoice). If disabled, say why ("Collect ₹650.00", "2 items still in production"). After success: show `receiptNo`, "Download receipt" (`GET .../receipts/{receiptNo}/pdf`), and note the customer was notified.
- All three are blocked after the receipt exists → `400` messages are user-readable.
- `409` → reload the account and retry.

#### D2.7 Production board (shop owner view)

See [D3](#d3-tailor-master-tailor-shop-assistant) — the same screen and calls. The shop owner sees **every** not-completed item of the shop and gets extra actions (assign anyone, unassign). Filter chips by status (`?status=` repeatable). A Kanban layout by status works well.

#### D2.8 Expenses and petty cash

**Expenses list** — `GET /v1/api/expenses` with `from`, `to`, `search` (type or notes), `paymentMethod`, `type=shop|employee`, `employeeUserId`, `sort`, paging. Columns: date, type, amount, method, employee (if any), notes, actions.

**Create** — two forms (or one with a toggle):

| Form | Call | Extra field |
|---|---|---|
| Shop expense | `POST /v1/api/expenses` | — |
| Employee expense | `POST /v1/api/employee-expenses` | `employeeUserId*` (staff dropdown from D2.10) |

Fields: expense type* (Electricity, Rent, Travel, Refreshment, Materials, ...), amount*, payment method (default Cash), date (default now; `yyyy-MM-dd` ok), notes, optional line items (`refreshmentDetails`, `miscellaneousDetails`: amount + description; `travelDetails`: date, source, destination, amount, description) — line items are stored for reference only.

- Show the **petty cash balance** next to the amount when the method is Cash; cash > balance is rejected with `"You do not have sufficient petty cash to spend, please contact owner to provide petty cash"`.
- The `201` response includes `pettyCashBalance` and `shopTotalExpense` → update the header balance from it.

**Edit** — `PATCH .../expenses/{expenseId}` `{ expenseAmount?, notes? }` (at least one; `notes: ""` clears). Anything else → delete and re-create.  
**Delete** — `DELETE .../expenses/{expenseId}` → `204`; cash goes back to petty cash.

**Petty cash page** — balance from `GET /v1/api/reports/financials/summary` (`pettyCash.currentBalance`), history from `GET /v1/api/shops/{shopId}/petty-cash`, and "Add cash" via `POST /v1/api/shops/{shopId}/petty-cash` `{ amount }` (allowed for the shop's own shop owner).

#### D2.9 Reports

| Report | Call | Display |
|---|---|---|
| Financial summary | `GET /v1/api/reports/financials/summary?from&to` | Cards: invoiced, collected (by method), expenses (shop vs employee, by method), net cash flow, petty cash added / balance, outstanding (now), receipts, deleted invoices |
| Daily | `GET /v1/api/reports/financials/daily?from&to` | Chart: invoiced vs collected vs expenses per day (every day present, zeros included) |
| Staff activity | `GET /v1/api/reports/activities/by-employee?from&to` | Table: employee, total, counts per type, last activity |
| Activity per day | `GET /v1/api/reports/activities/daily?employeeId&from&to` | Bar chart (shop or one employee) |
| Activity log | `GET /v1/api/reports/activities?employeeId&type&from&to&pageNumber&pageSize` | Timeline, newest first |
| Deleted invoices | `GET /v1/api/reports/deleted-invoices?search&from&to&pageNumber&pageSize` | Table: invoice, customer, amount, deleted at/by, reason (`from`/`to` = deletion date) |

Daily endpoints: default last 30 days, max 366 days per request.

#### D2.10 Staff (shop owner)

| Action | Call | Notes |
|---|---|---|
| List / search | `GET /v1/api/shops/{myShopId}/employees?search=` | includes the shop owner |
| Available staff | `GET /v1/api/employees/unassigned` | staff working in no shop |
| Add to my shop | `PUT /v1/api/employees/{userId}/assignment` `{ shopId: myShopId }` | only unassigned staff |
| Edit | `PATCH /v1/api/shops/{myShopId}/employees/{userId}` | staff and own details |
| Remove (keep account) | `DELETE .../shops/{myShopId}/employees/{userId}/assignment` | |
| Delete account | `DELETE .../shops/{myShopId}/employees/{userId}` | |

A shop owner **cannot create accounts** (platform owner only) and cannot take staff from another shop. Hide "Remove/Delete" on the shop-owner row.

#### D2.11 Receipts

`GET /v1/api/receipts?pageNumber&pageSize` (newest first) → table: receipt number, date, invoice, customer, total, paid; row action "PDF" (`GET .../receipts/{receiptNo}/pdf` → `{receiptNo}.pdf`); detail `GET .../receipts/{receiptNo}`.

---

### D3 Tailor master, tailor, shop assistant

One screen — **"My work"** — for all production roles (and the shop owner's production board).

#### D3.1 Work queue

`GET /v1/api/items/queue?status=...&pageNumber&pageSize` → `Page<WorkItem>`, **oldest invoice first**.

| Role | What the queue contains | Optional `status` filter values |
|---|---|---|
| `SHOP_OWNER` | every item not yet completed (add `status=ORDER_COMPLETED` for "ready to deliver") | any status |
| `TAILOR_MASTER` | `ORDER_ACCEPTED`, `CUTTING`, `TAILOR_ASSIGNED`, `STITCHING` | those four |
| `TAILOR` | items **assigned to me** in `TAILOR_ASSIGNED`, `STITCHING` | those two |
| `SHOP_ASSISTANT` | items **assigned to me** in `SHOP_ASSISTANT_ASSIGNED`, `HEMMING`, `IRONING` | those three |

A status outside your role's set → `400 "Status X is not part of your work queue; allowed: [...]"`.

Card content (`WorkItem`): item name × quantity, photo, customer name, invoice number, expected delivery (highlight overdue/today), status badge, current tailor / shop assistant, measurements (expand), buttons from `availableActions`.

Refresh the queue when the screen gains focus and every ~60 s (there are no push notifications).

#### D3.2 Actions — render from `availableActions`

The server computes what **this user** may do with **this item right now**. Render exactly these buttons; do not duplicate the rules.

| Value | Button label | Call | Body |
|---|---|---|---|
| `CUTTING` | Start cutting | `PATCH /v1/api/invoices/{invoiceId}/items/{itemUniqueId}/status` | `{ "status": "CUTTING" }` |
| `STITCHING` | Start stitching | same | `{ "status": "STITCHING" }` |
| `HEMMING` | Start hemming | same | `{ "status": "HEMMING" }` |
| `IRONING` | Start ironing | same | `{ "status": "IRONING" }` |
| `ORDER_COMPLETED` | Mark completed | same | `{ "status": "ORDER_COMPLETED" }` |
| `ASSIGN_TAILOR` | Assign tailor | `PUT .../items/{itemUniqueId}/tailor` | `{ "tailorId": "<userId>" }` |
| `ASSIGN_SHOP_ASSISTANT` | Assign shop assistant | `PUT .../items/{itemUniqueId}/shop-assistant` | `{ "shopAssistantId": "<userId>" }` |
| `UNASSIGN_TAILOR` | Remove tailor | `DELETE .../items/{itemUniqueId}/tailor` | — |
| `UNASSIGN_SHOP_ASSISTANT` | Remove shop assistant | `DELETE .../items/{itemUniqueId}/shop-assistant` | — |

Assignee dropdowns:
- Tailors: `GET /v1/api/items/assignees/tailors` (shop owner, tailor master)
- Shop assistants: `GET /v1/api/items/assignees/shop-assistants` (shop owner, tailor)

Each action returns the updated `WorkItem` → replace the card (it may leave the user's queue, e.g. after a tailor hands over to a shop assistant). On `409`: "This item was just updated by someone else" → reload the item.

Who does what (for help texts):

| Step | Done by |
|---|---|
| `ORDER_ACCEPTED → CUTTING` | tailor master, shop owner |
| assign tailor (→ `TAILOR_ASSIGNED`) | tailor master, shop owner |
| `TAILOR_ASSIGNED → STITCHING` | the assigned tailor, tailor master, shop owner |
| assign shop assistant (→ `SHOP_ASSISTANT_ASSIGNED`) | **the item's tailor**, shop owner (not the tailor master) |
| `→ HEMMING → IRONING → ORDER_COMPLETED` | the assigned shop assistant, shop owner |
| remove tailor / shop assistant | shop owner only (tailor can't be removed once a shop assistant is assigned) |

#### D3.3 Item detail

`GET /v1/api/invoices/{invoiceId}/items/{itemUniqueId}` → `WorkItem` with full `history` (who did what, when; also `TAILOR_UNASSIGNED` / `SHOP_ASSISTANT_UNASSIGNED` entries). Show a timeline and the same action buttons.

#### D3.4 My activity

`GET /v1/api/reports/my-activity?from&to` → `{ total, countsByType, daily[] }` (default last 30 days). Show a total, a breakdown (e.g. "Stitching started: 21") and a daily bar chart. Translate types with [F.6](#f6-activity-types).

---

### D4 Public customer pages

Customers open links like `https://<website>/download-invoice/<token>` from WhatsApp / email. **Keep these two routes exactly** — the backend generates links with these paths.

| Page | Call (no Authorization header) |
|---|---|
| `/download-invoice/:token` | `GET /v1/api/public/documents/invoices/{token}` |
| `/download-receipt/:token` | `GET /v1/api/public/documents/receipts/{token}` |

```ts
export async function loadCustomerPdf(kind: 'invoices' | 'receipts', token: string): Promise<Blob> {
  const res = await fetch(`${API_BASE}/v1/api/public/documents/${kind}/${encodeURIComponent(token)}`);
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? 'This document is not available right now.');
  }
  return res.blob();                      // application/pdf
}
```

Page design: shop branding, a PDF viewer (`<iframe src={blobUrl}>` or pdf.js), a **Download** button, and a friendly error state:

| Error message | Show |
|---|---|
| `This download link is not valid` | "This link is not valid. Please ask the shop to send it again." |
| `Invoice not found` / `Receipt not found` | "This document is no longer available. Please contact the shop." |
| `Could not generate the ... PDF, please try again` | "Couldn't prepare the document. Please try again in a minute." + Retry |

These pages must work on mobile (most customers open them from WhatsApp).

---

## Part E — TypeScript types (all payloads)

```ts
// ===== common =====
export type Role = 'OWNER' | 'SHOP_OWNER' | 'TAILOR_MASTER' | 'TAILOR' | 'SHOP_ASSISTANT' | 'ACCOUNTANT';
export type IsoDateTime = string;          // "2026-11-12T05:00:00.000+00:00" (UTC)
export type Day = string;                  // "2026-11-12"
export interface Page<T> { content: T[]; pageNumber: number; pageSize: number; totalElements: number; totalPages: number; lastPage: boolean; }
export interface ApiMessage { message: string; status: false; }

// ===== authentication =====
export interface Me { userId: string; name: string; email: string; roles: Role[]; shopId?: string; shopName?: string; }
export interface AuthenticatedUser extends Me { accessToken: string; tokenType: 'Bearer'; expiresIn: number; }   // seconds
export interface RegisterUserRequest {
  name: string; email: string; password: string; roles: Role[]; shopId?: string;
  phone?: string; salary?: number; address?: string;
}
export interface RegisteredUser { userId: string; name: string; email: string; roles: Role[]; shopId?: string; }
export interface ResetPasswordRequest { email: string; code: string; newPassword: string; confirmPassword: string; }

// ===== shops =====
export interface CreateShopRequest {
  shopName: string; ownerName: string; ownerEmailId: string; password: string;
  ownerNumber?: string; shopAddress?: string; gstNumber?: string; salary?: number;
}
export interface UpdateShopRequest {
  shopName?: string; ownerName?: string; shopAddress?: string; shopContactNumber?: string; gstNumber?: string; shopType?: string;
}
export interface Shop {
  id: string; shopName: string; pettyCash: number; ownerName?: string; shopAddress?: string; shopState?: string;
  shopContactNumber?: string; gstNumber?: string; shopType?: string; totalExpense: number;
  allPettyCashDetails: { addingDate: IsoDateTime; amount: number; addedBy: string }[];
  employeeIds: string[];
  // also: ownerDetails, allExpense, allReceipts, allInvoice — legacy fields, do not rely on them
}
export interface ShopSummary {
  shopId: string; shopName: string; shopType?: string; gstNumber?: string; ownerName?: string;
  shopContactNumber?: string; shopAddress?: string; shopState?: string; pettyCash: number; totalExpense: number;
}
export interface ShopName { shopId: string; shopName: string; }
export interface UpdateShopOwnerRequest {
  ownerUserId?: string; name?: string; phone?: string; email?: string; password?: string; salary?: number; reason?: string;
}
export interface ShopOwnerUpdate {
  shopId: string; ownerUserId: string; name: string; email: string; phone: string | null; salary: number | null;
  changedFields: string[]; signedOut: boolean; message: string;
}
export interface PettyCashTopUp { shopId: string; shopName: string; amountAdded: number; pettyCashBalance: number; addedBy: string; addedAt: IsoDateTime; }
export interface PettyCashEntry { amount: number; addedAt: IsoDateTime; addedBy: string; addedByName: string | null; }

// ===== invoices =====
export type ItemStatus = 'ORDER_ACCEPTED' | 'CUTTING' | 'TAILOR_ASSIGNED' | 'STITCHING'
  | 'SHOP_ASSISTANT_ASSIGNED' | 'HEMMING' | 'IRONING' | 'ORDER_COMPLETED';
export type OrderStatus = 'ORDER_ACCEPTED' | 'ORDER_DELIVERED';
export type PaymentStatus = 'Dues' | 'Partial' | 'Paid';
export interface Measurement { name: string; value: string; unit?: string; }

export interface CreateInvoiceRequest {
  customer: { name: string; phone: string; email?: string; alternatePhone?: string; address?: string };
  customerState: string;
  items: { itemName: string; itemQuantity: number; itemPrice: number;
           measurements?: Record<string, string>; measurementList?: Measurement[] }[];
  discount?: number; advancePaid?: number; paymentMethod?: string; transactionId?: string;
  expectedDeliveryDate?: string; orderDate?: IsoDateTime | null; notes?: string;
  isBackDate?: boolean; backDate?: Day | null;
  totalAmount?: number;   // optional; must equal Σ price×qty if sent — prefer leaving it out
}
export interface InvoiceItem {
  itemUniqueId: string; itemName: string; itemQuantity: number; itemPrice: number; imageUrl: string | null;
  itemOrderStatus: ItemStatus; assignmentStatus: string | null;
  assignedTo: string | null; assignedToWhichShopAssistant: string | null;
  measurements: Record<string, string> | null; measurementList: Measurement[] | null;
  assignmentHistory: unknown[];
}
export interface Invoice {
  id: string; invoiceId: string; shopId: string;
  customer: { id: string; customerId: string; name: string; phone: string; email: string | null; newCustomer: boolean } | null;
  customerState: string; items: InvoiceItem[];
  totalAmount: number; discount: number; taxableAmount: number;
  cgstRate: number; sgstRate: number; igstRate: number; cgstAmount: number; sgstAmount: number; igstAmount: number;
  totalAmountWithGST: number; advancePaid: number; balanceDue: number;
  gstin: string | null; sacCode: string; hsnCode: string;
  paymentMethod: string | null; paymentStatus: PaymentStatus; orderStatus: OrderStatus;
  paymentHistories: { amount: number; paidAt: IsoDateTime; paidVia: string }[];
  invoiceDate: IsoDateTime; orderDate: IsoDateTime; expectedDeliveryDate: string | null; notes: string | null;
  receiptId: string | null;            // set once delivered → read-only from then on
  pdfUrl: string;                      // relative path, needs the token
  notifications: { whatsApp: 'QUEUED' | 'SKIPPED'; email: 'QUEUED' | 'SKIPPED' } | null;   // only on create
}
export interface InvoiceSummary {
  id: string; invoiceId: string; invoiceDate: IsoDateTime; expectedDeliveryDate: string | null;
  customerId: string | null; customerName: string | null; customerPhone: string | null;
  itemCount: number; itemNames: string[]; completedItemCount: number;
  totalAmountWithGST: number; advancePaid: number; balanceDue: number;
  paymentStatus: PaymentStatus; orderStatus: OrderStatus; pdfUrl: string;
}
export interface InvoiceListQuery {
  shopId?: string; status?: 'pending' | 'delivered' | 'all'; from?: Day; to?: Day; search?: string;
  paymentStatus?: PaymentStatus; special?: boolean; details?: boolean; sort?: 'newest' | 'oldest';
  pageNumber?: number; pageSize?: number;
}
export interface EditInvoiceRequest {
  expectedDeliveryDate?: string; notes?: string; discount?: number;
  items?: { itemUniqueId?: string; itemName: string; itemQuantity: number; itemPrice: number;
            measurements?: Record<string, string>; measurementList?: Measurement[]; removeImage?: boolean }[];
}
export interface DeleteInvoiceResponse { invoiceId: string; archiveId: string; reason: string; deletedAt: IsoDateTime; message: string; }

// ===== payments / receipts =====
export interface InvoiceAccount {
  invoiceId: string; totalAmount: number; discount: number; taxableAmount: number;
  cgstAmount: number; sgstAmount: number; igstAmount: number; totalAmountWithGST: number;
  advancePaid: number; balanceDue: number; paymentStatus: PaymentStatus; creditNoteId?: string;
  payments: { amount: number; paidVia: string; paidAt: IsoDateTime; transactionId?: string }[];
}
export interface PaymentRequest { amount: number; paymentMethod: string; transactionId?: string; }
export interface DiscountRequest { amount: number; reason?: string; }
export interface Receipt {
  id: string; receiptNo: string; invoiceId: string; shopId: string;
  customerName: string | null; customerPhone: string | null; receiptDate: IsoDateTime;
  items: { itemUniqueId: string; itemName: string; itemQuantity: number; itemPrice: number; itemOrderStatus: ItemStatus }[];
  totalAmount: number; discount: number; taxableAmount: number; cgstAmount: number; sgstAmount: number; igstAmount: number;
  totalAmountWithGST: number; amountPaid: number; paymentStatus: PaymentStatus; paymentMethod: string | null;
  orderStatus: OrderStatus; gstin: string | null; pdfUrl: string;
  notifications?: { whatsApp: 'QUEUED' | 'SKIPPED'; email: 'QUEUED' | 'SKIPPED' }; created?: boolean;   // only from POST .../receipt
}

// ===== production =====
export type ItemAction = 'CUTTING' | 'STITCHING' | 'HEMMING' | 'IRONING' | 'ORDER_COMPLETED'
  | 'ASSIGN_TAILOR' | 'ASSIGN_SHOP_ASSISTANT' | 'UNASSIGN_TAILOR' | 'UNASSIGN_SHOP_ASSISTANT';
export interface Person { userId: string; name: string; }
export interface WorkItem {
  invoiceId: string; itemUniqueId: string; itemName: string; itemQuantity: number; imageUrl: string | null;
  status: ItemStatus; tailor: Person | null; shopAssistant: Person | null;
  measurements: Record<string, string> | null; measurementList: Measurement[] | null;
  customerName: string | null; expectedDeliveryDate: string | null; invoiceDate: IsoDateTime;
  availableActions: ItemAction[];
  history: { userId: string; action: string; timestamp: IsoDateTime }[];
}

// ===== expenses =====
export interface ExpenseLine { amount: number; description?: string; }
export interface TravelLine { date: Day; source?: string; destination?: string; amount: number; description?: string; }
export interface CreateExpenseRequest {
  expenseType: string; expenseAmount: number; paymentMethod?: string; notes?: string;
  expenseDate?: string;                         // "yyyy-MM-dd" or ISO date-time; omitted = now
  refreshmentDetails?: ExpenseLine[]; miscellaneousDetails?: ExpenseLine[]; travelDetails?: TravelLine[];
}
export interface CreateEmployeeExpenseRequest extends CreateExpenseRequest { employeeUserId: string; }
export interface Expense {
  id: string; shopId: string; userId?: string; employeeName?: string;   // absent for shop expenses
  expenseType: string; expenseAmount: number; paymentMethod: string; notes?: string; expenseDate: IsoDateTime;
  refreshmentDetails?: ExpenseLine[]; miscellaneousDetails?: ExpenseLine[]; travelDetails?: TravelLine[];
  pettyCashBalance?: number; shopTotalExpense?: number;                 // only in write responses
}

// ===== employees =====
export interface Employee {
  userId: string; name: string; email: string; phone: string | null; salary: number; address: string | null;
  roles: Role[]; shopId: string | null; shopName: string | null;
  accountCreationDate: IsoDateTime | null; lastLoginDate: IsoDateTime | null;
}
export interface UpdateEmployeeRequest { name?: string; email?: string; phone?: string; salary?: number; address?: string; }

// ===== reports =====
export interface MethodTotal { paymentMethod: string; amount: number; count: number; }
export interface FinancialSummary {
  shopId: string | null; shopName: string | null; from: Day | null; to: Day | null;
  sales: { invoiceCount: number; invoicedAmount: number; taxableAmount: number; gstAmount: number; discount: number };
  collections: { total: number; byPaymentMethod: MethodTotal[] };
  expenses: { total: number; count: number; shopExpenses: number; employeeExpenses: number; byPaymentMethod: MethodTotal[] };
  pettyCash: { added: number; currentBalance: number };
  netCashFlow: number;
  outstanding: { openInvoiceCount: number; balanceDue: number };     // current, not period-limited
  receiptCount: number; deletedInvoiceCount: number;
}
export interface DailyFinancials { date: Day; invoiceCount: number; invoicedAmount: number; collected: number; expenses: number; netCashFlow: number; }
export interface ShopOverview {
  shopId: string; shopName: string; shopType: string | null; ownerName: string | null; employeeCount: number; pettyCash: number;
  invoiceCount: number; invoicedAmount: number; paidInvoiceCount: number; deliveredInvoiceCount: number;
  collected: number; expenses: number; cashExpenses: number; netCashFlow: number;
  pendingOrders: number; outstanding: number; receiptCount: number;
}
export interface Activity {
  id: string; shopId: string; employeeId: string; employeeName: string | null; type: string;
  invoiceId: string | null; assignedTo: string | null; notes: string | null; timestamp: IsoDateTime;
}
export interface EmployeeActivitySummary { employeeId: string | null; employeeName: string | null; total: number; countsByType: Record<string, number>; lastActivityAt: IsoDateTime | null; }
export interface ActivityDay { date: Day; count: number; }
export interface MyActivity { employeeId: string; from: Day; to: Day; total: number; countsByType: Record<string, number>; daily: ActivityDay[]; }
export interface DeletedInvoice {
  archiveId: string; shopId: string; invoiceId: string; invoiceDate: IsoDateTime | null;
  customerName: string | null; customerPhone: string | null; itemCount: number;
  totalAmountWithGST: number; advancePaid: number; balanceDue: number; paymentStatus: string | null;
  deletedAt: IsoDateTime; deletedBy: string | null; deletedByName: string | null; reason: string | null;
}
```

### E.1 Endpoint quick list (typed)

```ts
// auth
POST  /v1/api/auth/sign-in                     (SignIn)            → AuthenticatedUser
POST  /v1/api/auth/token/refresh                                   → AuthenticatedUser
POST  /v1/api/auth/sign-out                                        → 204
GET   /v1/api/auth/me                                              → Me
POST  /v1/api/auth/users                       RegisterUserRequest → 201 RegisteredUser      (OWNER)
POST  /v1/api/auth/password-reset/code         { email }           → 202 ApiMessage-like { message }
POST  /v1/api/auth/password-reset              ResetPasswordRequest → { message }
// shops                                                                        (OWNER unless noted)
POST  /v1/api/shops                      CreateShopRequest   → 201 Shop
GET   /v1/api/shops                                          → Shop[]
GET   /v1/api/shops/summaries                                → ShopSummary[]
GET   /v1/api/shops/names                                    → ShopName[]
GET   /v1/api/shops/{shopId}                                 → Shop
PUT   /v1/api/shops/{shopId}             UpdateShopRequest   → Shop
PUT   /v1/api/shops/{shopId}/owner       UpdateShopOwnerRequest → ShopOwnerUpdate
POST  /v1/api/shops/{shopId}/petty-cash  { amount }          → 201 PettyCashTopUp       (OWNER, SHOP_OWNER own)
GET   /v1/api/shops/{shopId}/petty-cash  ?from&to&amount&page → Page<PettyCashEntry>    (OWNER, SHOP_OWNER own)
// invoices
GET   /v1/api/invoices                InvoiceListQuery    → Page<InvoiceSummary | Invoice>
GET   /v1/api/invoices/{invoiceId}    ?shopId             → Invoice
GET   /v1/api/invoices/{invoiceId}/pdf ?shopId            → PDF blob
POST  /v1/api/invoices                JSON | multipart    → 201 Invoice              (SHOP_OWNER)
PATCH /v1/api/invoices/{invoiceId}    JSON | multipart    → Invoice                  (SHOP_OWNER)
DELETE /v1/api/invoices/{invoiceId}   ?reason             → DeleteInvoiceResponse     (SHOP_OWNER)
// payments / receipts                                                          (SHOP_OWNER; receipts GET also OWNER)
GET   /v1/api/invoices/{invoiceId}/account                → InvoiceAccount
POST  /v1/api/invoices/{invoiceId}/payments PaymentRequest → 201 InvoiceAccount
POST  /v1/api/invoices/{invoiceId}/discount DiscountRequest → InvoiceAccount
POST  /v1/api/invoices/{invoiceId}/receipt                → 201|200 Receipt
GET   /v1/api/receipts                ?shopId&page        → Page<Receipt>
GET   /v1/api/receipts/{receiptNo}                        → Receipt
GET   /v1/api/receipts/{receiptNo}/pdf                    → PDF blob
// production
GET   /v1/api/items/queue                ?status[]&page      → Page<WorkItem>
GET   /v1/api/invoices/{invoiceId}/items/{itemUniqueId}     → WorkItem
PATCH .../items/{itemUniqueId}/status                    { status }          → WorkItem
PUT   .../items/{itemUniqueId}/tailor                    { tailorId }        → WorkItem
PUT   .../items/{itemUniqueId}/shop-assistant            { shopAssistantId } → WorkItem
DELETE .../items/{itemUniqueId}/tailor                                       → WorkItem                 (SHOP_OWNER)
DELETE .../items/{itemUniqueId}/shop-assistant                               → WorkItem                 (SHOP_OWNER)
GET   /v1/api/items/assignees/tailors                              → Person[]
GET   /v1/api/items/assignees/shop-assistants                      → Person[]
// expenses                                                                     (writes SHOP_OWNER; reads also OWNER)
GET   /v1/api/expenses                ?shopId&from&to&search&paymentMethod&type&employeeUserId&sort&page → Page<Expense>
GET   /v1/api/expenses/{expenseId}    ?shopId             → Expense
POST  /v1/api/expenses                CreateExpenseRequest → 201 Expense
POST  /v1/api/employee-expenses       CreateEmployeeExpenseRequest → 201 Expense
PATCH /v1/api/expenses/{expenseId}    { expenseAmount?, notes? } → Expense
DELETE /v1/api/expenses/{expenseId}                       → 204
// employees                                                                    (OWNER, SHOP_OWNER own shop)
GET   /v1/api/shops/{shopId}/employees ?search            → Employee[]
GET   /v1/api/employees/unassigned                       → Employee[]
PUT   /v1/api/employees/{userId}/assignment { shopId }   → Employee
DELETE /v1/api/shops/{shopId}/employees/{userId}/assignment → Employee
PATCH /v1/api/shops/{shopId}/employees/{userId} UpdateEmployeeRequest → Employee
DELETE /v1/api/shops/{shopId}/employees/{userId}         → 204
// reports
GET   /v1/api/reports/shops/overview                     ?from&to            → ShopOverview[]          (OWNER)
GET   /v1/api/reports/financials/summary                 ?shopId&from&to     → FinancialSummary
GET   /v1/api/reports/financials/daily                   ?shopId&from&to     → DailyFinancials[]
GET   /v1/api/reports/activities                         ?shopId&employeeId&type&from&to&page → Page<Activity>
GET   /v1/api/reports/activities/by-employee             ?shopId&from&to     → EmployeeActivitySummary[]
GET   /v1/api/reports/activities/daily                   ?shopId&employeeId&from&to → ActivityDay[]
GET   /v1/api/reports/my-activity                        ?from&to            → MyActivity               (staff, SHOP_OWNER)
GET   /v1/api/reports/deleted-invoices                   ?shopId&search&from&to&page → Page<DeletedInvoice>
// pdf
GET   /v1/api/pdf-service/warmup                                             → "Warmup triggered!"   (any signed-in user)
// public
GET   /v1/api/public/documents/invoices/{token}                              → PDF blob (no auth)
GET   /v1/api/public/documents/receipts/{token}                              → PDF blob (no auth)
```

---

## Part F — Rules and enums reference

### F.1 Access matrix

| Area | OWNER | SHOP_OWNER | TAILOR_MASTER | TAILOR | SHOP_ASSISTANT |
|---|---|---|---|---|---|
| Shops (create, list, edit, owner account) | ✅ | — | — | — | — |
| Petty cash add / history | ✅ any shop | ✅ own shop | — | — | — |
| Create user accounts | ✅ | — | — | — | — |
| Staff list / assign / edit / remove | ✅ any shop | ✅ own shop, staff only | — | — | — |
| Invoices read + PDF | ✅ (`shopId`) | ✅ | — | — | — |
| Invoices create / edit / delete | — | ✅ | — | — | — |
| Payments, discount, receipt | — | ✅ | — | — | — |
| Receipts read + PDF | ✅ | ✅ | — | — | — |
| Production queue / actions | — | ✅ all items | ✅ cutting stage | ✅ own items | ✅ own items |
| Expenses read | ✅ (`shopId`) | ✅ | — | — | — |
| Expenses write | — | ✅ | — | — | — |
| Reports (financial, activity, deleted) | ✅ one or all shops | ✅ own shop | — | — | — |
| Shops overview report | ✅ | — | — | — | — |
| My activity | — | ✅ | ✅ | ✅ | ✅ |

### F.2 Item statuses (display labels)

| Value | Label | Badge colour idea |
|---|---|---|
| `ORDER_ACCEPTED` | Order accepted | grey |
| `CUTTING` | Cutting | amber |
| `TAILOR_ASSIGNED` | With tailor | blue |
| `STITCHING` | Stitching | blue |
| `SHOP_ASSISTANT_ASSIGNED` | With shop assistant | purple |
| `HEMMING` | Hemming | purple |
| `IRONING` | Ironing | purple |
| `ORDER_COMPLETED` | Ready | green |

Invoice `orderStatus`: `ORDER_ACCEPTED` → "Open", `ORDER_DELIVERED` → "Delivered".

### F.3 Payment status

`Dues` (nothing paid, red) · `Partial` (amber) · `Paid` (green). Exact casing.

### F.4 Special items

`Embroidery`, `Lining Material`, `Saree Fall`, `Saree Pico`, `Designing` — start in `CUTTING`; `special=true` filters invoices containing them. Match names exactly (case-sensitive) when offering a catalogue.

### F.5 Number formats

- Invoice: `{PREFIX}-{dd-MM-yyyy}-{00001}`, e.g. `SARI-12-11-2026-00042` (prefix = first 4 letters/digits of the shop name; running number per shop per year). Gaps are normal (deleted invoices' numbers are never reused).
- Receipt: same pattern, own counter.
- Item: `{itemName}_{timestamp}_{index}`.
- Customer: `CUS-XXXXXXXXXX`.

### F.6 Activity types

| Value | Label |
|---|---|
| `INVOICE_CREATED` | Invoice created |
| `INVOICE_DELETED` | Invoice deleted |
| `TAILOR_MASTER_CUTTING` | Moved to cutting |
| `ASSIGNED_TO_TAILOR` | Assigned to tailor |
| `TAILOR_STITCHING` | Stitching started |
| `ASSIGNED_TO_ASSISTANT` | Assigned to shop assistant |
| `ASSISTANT_HEMMING` | Hemming started |
| `ASSISTANT_IRONING` | Ironing started |
| `ORDER_COMPLETED` | Item completed |
| `ORDER_DELIVERED` | Order delivered (receipt) |
| `USER_DELETED` | Employee deleted |
| `ASSIGNED_TO_TAILOR_MASTER`, `TAILOR_STARTED`, `TAILOR_COMPLETED`, `ASSISTANT_COMPLETED`, `RECEIPT_DELETED` | Older entries (legacy app) |

### F.7 Things that become read-only

| When | Locked |
|---|---|
| Receipt generated (`receiptId` set, `ORDER_DELIVERED`) | invoice edit / delete, payments, discount, all item actions |
| Item in production | that item's name / qty / price / measurements / photo, and removing it |
| Discount applied once | further discounts |
| Shop assistant assigned | removing the tailor |

---

## Part G — Client-side validation (mirror of the server)

Validate in the form for a fast UX; the server validates again and returns a field map.

| Form | Field | Rule |
|---|---|---|
| Sign-in | email | required, valid, ≤ 150 |
| | password | required, ≤ 100 |
| Shop owner update | name ≤ 100 · phone 10 digits · email valid ≤ 150 · password policy · salary 0–10,000,000 · reason ≤ 200 · at least one of name/phone/email/password/salary | |
| Password (new user, reset, new shop owner) | password | 8–100, ≥ 1 letter, ≥ 1 digit — `/^(?=.*[A-Za-z])(?=.*\d).{8,100}$/` |
| Reset | code | `/^\d{6}$/` |
| Phone (any) | phone | `/^\d{10}$/` (Indian 10-digit, no +91) |
| New user | name ≤ 100 · email ≤ 150 · salary 0–10,000,000 · address ≤ 300 · roles ≥ 1 · shopId required unless OWNER | |
| New shop | shopName*, ownerName* ≤ 100 · ownerEmailId* · password* · ownerNumber 10 digits · shopAddress ≤ 300 · gstNumber ≤ 30 · salary ≥ 0 | |
| Invoice | customer.name* ≤ 100 · customer.phone* 10 digits · customer.email valid · alternatePhone 10 digits · address ≤ 300 | |
| | customerState* ≤ 50 | |
| | items 1–50 · itemName* ≤ 100 · itemQuantity* 1–1000 · itemPrice* 0–10,000,000 | |
| | discount ≥ 0 and ≤ total · advancePaid ≥ 0 and ≤ total with GST | |
| | paymentMethod ≤ 30 · transactionId ≤ 100 · expectedDeliveryDate ≤ 30 · notes ≤ 500 | |
| | backDate `yyyy-MM-dd`, not future, required if isBackDate | |
| | photos: image/*, ≤ 10 MB, ≤ 1 per item | |
| Invoice delete | reason ≤ 300 | |
| Payment | amount > 0 and ≤ balanceDue · paymentMethod* ≤ 30 · transactionId ≤ 100 | |
| Discount | amount > 0 and ≤ totalAmount (pre-GST) · reason ≤ 200 | |
| Expense | expenseType* ≤ 100 · expenseAmount* 0.01–10,000,000 · paymentMethod ≤ 30 · notes ≤ 500 | |
| | line items: amount ≥ 0.01, description ≤ 200; travel date `yyyy-MM-dd`, source/destination ≤ 100 | |
| Petty cash | amount 0.01–10,000,000 | |
| Employee edit | name not blank ≤ 100 · email valid ≤ 150 · phone 10 digits or "" · salary 0–10,000,000 · address ≤ 300 or "" · at least one field | |
| Lists | pageSize 1–100 · search ≤ 100 · to ≥ from · daily range ≤ 366 days | |

---

## Part H — Error handling catalogue

| Status | Typical message | Recommended UI |
|---|---|---|
| 400 (field map) | `{"customer.phone": "..."}` | Inline under the field; scroll to the first error |
| 400 | `"Invoice not found"`, `"Receipt not found"`, `"Expense not found"`, `"Item not found"` | Toast + navigate back to the list |
| 400 | `"User is not assigned to any shop"` | Full-page message: "Your account is not linked to a shop. Contact the owner." |
| 400 | `"shopId is required"` | (Owner) prompt to pick a shop |
| 400 | `"You do not have sufficient petty cash to spend, ..."` | Inline on amount + link to petty cash |
| 400 | `"Collect the remaining balance of 650.0 before generating the receipt ..."` | Keep Deliver disabled; show balance |
| 400 | `"All items must be ORDER_COMPLETED before delivery. Not finished: ..."` | List the unfinished items |
| 400 | `"Cannot move the item from X to Y. Next step: Z."` | Reload the item (buttons were stale) |
| 400 | `"Invoice ... already has receipt ... and can no longer be edited"` | Reload invoice; hide edit |
| 400 | `"Could not generate the invoice PDF, please try again"` | Retry button |
| 401 | — | Session ended → sign-in (client does this) |
| 403 | `"You are not authorized to access this resource"` | "You don't have permission for this." (likely a stale screen or wrong role) |
| 409 | `"... changed by someone else ... Reload it and try again."` | Toast + reload record; keep user input if possible |
| 409 | `"An account with email ... already exists"` | Inline on email |
| 429 | rate limit / too many wrong codes | Message + disable the button ~60 s |
| network | — | "No connection" banner with retry |

Server messages are written for end users — show them as-is unless you have a better translation.

---

## Part I — Local setup, testing and limits

### I.1 Running against a local backend

1. Backend runs on `http://localhost:5000` (Swagger: `http://localhost:5000/swagger-ui.html`).
2. Frontend dev server on `http://localhost:5173` (already allowed by CORS).
3. In Swagger, use **Authorize** with the raw `accessToken` from sign-in to try any call.
4. You need accounts per role: the platform owner creates shops (`POST /v1/api/shops`, which also creates the shop owner) and staff (`POST /v1/api/auth/users`).

### I.2 End-to-end test scenarios

1. **Sign-in per role** lands on the right home; reload keeps the session (`/me`); sign-out on one tab ends the other.
2. **Password reset**: request code → wrong code ×5 → `429`; correct code → sign in with the new password.
3. **Invoice with photos**: 3 items (one special), 2 photos (index 0 and 2), advance → check numbers, statuses (`CUTTING` for the special item), photo thumbnails, PDF download.
4. **Validation**: phone with 9 digits, quantity 0, advance > total → field errors at the right fields.
5. **Edit**: change quantity of an untouched item, add an item, remove an item → totals updated; try changing an item in production → blocked.
6. **Production**: tailor master cuts and assigns tailor → tailor starts stitching and assigns shop assistant → assistant hems, irons, completes. Check each user only sees their buttons.
7. **Concurrency**: open the same item on two devices, act on both → second gets `409`, reload works.
8. **Payment & delivery**: pay part → `Partial`; try deliver → blocked; pay rest → `Paid`; complete all items → deliver → receipt PDF; invoice now read-only.
9. **Discount**: apply once → second attempt blocked; GST recalculated.
10. **Expenses**: cash expense larger than petty cash → blocked; top up → succeeds; delete → balance restored.
11. **Staff**: owner creates a tailor in shop A; shop owner B can't add them; owner moves them to B → their in-progress items in A go back a step.
12. **Reports**: totals match the invoices/payments/expenses just created; owner "all shops" vs single shop.
13. **Customer link**: open the WhatsApp link on a phone → PDF shows; tamper one character → friendly invalid-link page.

### I.3 Known limits (plan the UI around them)

- **No push / real-time updates** — poll the work queue (~60 s) and refresh on focus.
- **PDF generation is slow-ish** (external converter, a few seconds).
- **No receipt cancellation / refunds**; one discount per invoice; payments can't exceed the balance.
- **Invoice customer can't be changed** after creation; neither can customer state or invoice date.
- **Shop owners can't create accounts** — only the platform owner can.
- **Shops can't be deleted.**
- **No backup-history screen:** owner-detail changes are backed up in the database, but there is no endpoint to list the backups.
- **PDF warm-up:** call `GET /v1/api/pdf-service/warmup` (any signed-in user, returns text `Warmup triggered!`) once after sign-in or when the invoice screen opens, so the first PDF is fast. Throttled server-side to once a minute.
- `ACCOUNTANT` role has no screens yet.

### I.4 Where to get details

- Every endpoint, every field rule and every error message: [`MODULES_API_DOCUMENTATION.md`](MODULES_API_DOCUMENTATION.md).
- Try calls live: Swagger UI.
- Migrating an existing frontend instead of starting fresh: [`FRONTEND_MIGRATION_GUIDE.md`](FRONTEND_MIGRATION_GUIDE.md).
