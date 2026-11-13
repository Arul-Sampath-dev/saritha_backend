# Legacy functionality without a module equivalent

Checked against the current controllers under `src/main/java/com/saritha/fashions/modules/` and the legacy controllers. This list excludes features that now have an equivalent in the modules, including invoice listing/search/edit/delete, expense read/filter operations, reporting, employee search, shop names, and petty-cash history.

These legacy routes remain in the application source. "Missing" here means no equivalent is currently exposed by the new modules; it does not mean the legacy routes have been removed.

## Still missing

### Platform owner shop-owner account maintenance and backups

- Update/replace the shop owner's details, including password, with the legacy backup behavior: `PUT /v1/api/owner/shop/update-owner-details`.
- Read user backup history: `GET /v1/api/owner/shop/backup-history/user/{userId}`.
- Read shop backup history: `GET /v1/api/owner/shop/backup-history/shop/{shopId}`.

Employee management supports ordinary employee detail updates, but does not implement this owner-specific account replacement/backup workflow.

### Employees across all shops

- One request returning employee details across shops: `GET /v1/api/shops/all-employees`.

The new employee module lists employees for a specified shop and has a separate unassigned-staff list; it does not provide this combined cross-shop listing.

### Legacy `employees` collection operations

The old `/employees/**` controller operates on the `employees` collection, using the separate `Employee` model. The new employee module operates on login accounts in the `users` collection. No module currently provides the legacy collection's operations:

- `POST /employees/add`
- `GET /employees/{id}`
- `GET /employees/all`
- `GET /employees/shop/{shopId}`
- `POST /employees/assign/{employeeId}/{shopId}`
- `DELETE /employees/remove/{employeeId}/{shopId}`
- `DELETE /employees/delete/{id}`

Confirm whether this legacy collection is still used before deciding whether these operations need migration or can be retired.

### Shop deletion

- `DELETE /v1/api/shops/{id}` has no module equivalent. The shop-management module intentionally does not expose shop deletion.

### Manual receipt status override

- `PATCH /v1/api/receipt/{id}/status?status=...` has no direct equivalent. The new receipt flow derives invoice payment status from recorded payments and discounts instead of allowing a manual status override.

## Legacy operations replaced by a different module workflow

These do not have a one-call or exact route equivalent, but the underlying document operations are available through the modules:

- Legacy receipt generation/download and invoice download routes (`POST /v1/api/receipt/generate`, `/download/receipt`, `/download/invoice`) are split across invoice creation, receipt creation, and PDF retrieval. Receipt creation in the new flow requires the invoice to be fully paid and all items to be complete.
- `POST /v1/api/financials/record` is not ported. Reporting computes summaries from invoice payments, expenses, petty-cash entries, receipts, and deleted invoices instead of storing manually entered daily totals.

## Not gaps after the latest module additions

- Invoice read/list/search, special-category filtering, edit, and archive/delete: `/v1/api/invoice-management/invoices`.
- Expense list, search, payment-method/type filters, and detail: `/v1/api/expense-management/expenses`.
- Financial, dashboard, activity, and deleted-invoice reports: `/v1/api/reports/**`.
- Employee search within a shop: `GET /v1/api/employee-management/shops/{shopId}/employees?search=...`.
- Shop name lookup and petty-cash history/search: `/v1/api/owner/shop-management/names` and `/v1/api/owner/shop-management/{shopId}/petty-cash`.
- Owner pending/delivered invoice lists and search are available through invoice list filters (`status`, `search`, and `shopId`).

