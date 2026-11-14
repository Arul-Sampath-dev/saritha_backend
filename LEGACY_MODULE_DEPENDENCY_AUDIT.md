# Legacy Code Removal — Record

**Done:** 2026-11-13 · **Branch:** `opt/shop-management` · **Rollback point:** commit `71b1a5f` (last commit with the legacy code)

## What was removed

111 source files outside `modules/` were deleted; 48 remain (the shared platform listed below).

| Removed | Files |
|---|---|
| All legacy controllers (`controller/**`, 24 classes) | `/v1/api/auth`, `/shops`, `/owner/shop`, `/owner/*`, `/invoices`, `/receipt`, `/expenses`, `/tailor-master`, `/tailor`, `/shop-assistant`, `/dashboard`, `/financials`, `/employee/activities`, `/deleted/invoices`, `/shopOwner/invoices`, `/public/download-*`, `/user`, `/pdf-service` (re-implemented, see below), `/employees` |
| All legacy services (`service/**`) | invoice, receipt, shop, owner, expense, employee, tailor, shop-assistant, financial, activity, sequence, token, WhatsApp, email services |
| Legacy repositories | all except `UserRepository`, `RoleRepository` (+ `UserWithShopProjection`) |
| Legacy payloads / DTOs | all except the ones listed below |
| Legacy models | `CreditNote`, `DeletedInvoice`, `DeletedReceipt`, `Employee`, `EmployeeActivity`, `FinancialRecord`, `ForgetPassword`, `InvoiceSequence`, `ReceiptSequence`, `Service`, `UserBackupHistory`, legacy `ActivityType` |
| Other | `query/**`, `security/request/**`, `security/response/**`, `util/ChangePassword` |

Unused imports of `InvoiceServiceImpl` were removed from `AuthUtil` and `ExecutionTimeInterceptor`, and `WebSecurityConfig` rules for the removed routes were deleted.

**Data is untouched:** all MongoDB collections stay as they are (the modules use the same collections and document shapes). Collections only the removed code used (`financial_records`, `forget_password`, legacy `employees`, `orders`) are simply no longer read or written.

## Ported before the removal

| Legacy | Now |
|---|---|
| `PUT /v1/api/owner/shop/update-owner-details` | `PUT /v1/api/shops/{shopId}/owner` (backup to `user_backup_history` kept) |
| `GET /v1/api/pdf-service/warmup` | Same path, `modules/core/pdf/PdfServiceController` (throttled to once a minute) |
| Everything else | See the old → new map in `MODULES_API_DOCUMENTATION.md` |

Dropped on purpose: backup-history read endpoints, `POST /v1/api/financials/record` (figures are computed), manual payment-status endpoints, the demo `send-message` endpoint, shop delete.

## Shared platform kept outside `modules/` (48 files)

| Package | Kept | Why |
|---|---|---|
| (root) | `SarithaFashionsApplication` | entry point, Swagger security scheme |
| `config` | `AppConfig`, `AppConstants`, `AsyncConfig`, `CacheConfig`, `InterceptorConfig`, `MongoConfig`, `MongoIndexInitializer`, `OrderStatus`, `WebSocketConfig` | beans, Mongo setup, **indexes the module queries rely on** (incl. unique `users.email`), item status constants |
| `exception` | `APIException`, `APIResponse`, `MyGlobalExceptionHandler`, `ExpectationFailedException(Response)`, `ResourceNotFoundException` | error responses for every module |
| `interceptor` | `ExecutionTimeInterceptor` | request timing log |
| `security` | `WebSecurityConfig`, `RateLimitingFilter`, `jwt/*`, `services/*` | authentication of every request |
| `util` | `AuthUtil`, `DateTimeUtils`, `AmountUtils`, `TaxUtils`, `GcsImageUploader` | used by the modules |
| `repository` | `UserRepository`, `RoleRepository`, `AggregationInterface/UserWithShopProjection` | used by security / `AuthUtil` |
| `payload` | `PaginatedResponse`, `items/Items`, `expense/RefreshmentDetails`, `expense/TravelDetails`, `shop/PettyCashDTO` | paging; field types of the kept models |
| `model` | `User`, `Role`, `AppRole`, `Shop`, `Invoice`, `Receipt`, `Expense`, `Customer`, `Profile` | security user model; field types of `ManagedShop` / `UpdateShopRequest` |

`WebSocketConfig` has no remaining users (the order-update topic was removed earlier); it is kept as harmless infrastructure and can be deleted later.

## Verification

- Clean build (`mvnw clean compile`) passes; no compiled class from a removed package remains.
- The same removal was first rehearsed in a throw-away copy of the project.
- Earlier parity checks (PDF Word XML, activity documents, download tokens) compared the module code against the legacy code before it was removed.
- **Still to do:** start the application and smoke-test it (sign-in, one call per module, Swagger), and switch the frontend to the module endpoints (`FRONTEND_MIGRATION_GUIDE.md`) — the old routes now return 404 / 401.

## Further optional clean-up

- Move the shared platform above into a `common` package (import changes only).
- Delete `WebSocketConfig` and the `/v1/api/payments/handle-payment-callback`, `/v1/api/user/support-ticket/contact-us`, `/api/test/**` security rules if those features are not coming back (no controller serves them).
