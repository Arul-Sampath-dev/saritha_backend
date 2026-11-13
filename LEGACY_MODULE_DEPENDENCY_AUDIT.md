# Legacy Code Dependency Audit

**Checked:** 2026-11-13  
**Scope:** `src/main/java/com/saritha/fashions/modules`

## Current status

The previously identified direct business-service dependencies have been removed from the current module code:

- Invoice and receipt PDF endpoints use module-owned `core/pdf/SalesDocumentPdfService`.
- Shop creation uses authentication module `UserRegistrationService`.
- Activity writes use `core/activity/ActivityLogService`.
- Invoice and receipt number generation use `core/numbering/DocumentNumberService`.
- Customer document links use module-owned `core/documentlink/DocumentTokenService`.

The source scan did not find module imports of legacy `AuthController`, `EmployeeActivityService`, `InvoiceSequenceService`, `ReceiptSequenceService`, `CustomerDocumentTokenService`, old `InvoiceRepository`/`ReceiptRepository`, or old `ReceiptService`.

## Remaining references outside `modules/`

The modules still rely on classes in the old top-level package layout. These references are real compile-time dependencies; whether they are “legacy” depends on the planned deletion scope.

### Shared domain and security

- `model.User`, `model.AppRole` (used throughout module auth and access checks).
- `security.services.UserDetailsImpl`, `security.jwt.JwtUtils` (authentication module).
- `util.AuthUtil` (principal lookup in several modules).
- `config.OrderStatus` (invoice workflow).
- `model.Invoice`, `model.Receipt`, `model.Expense` (still used by `shopmanagement` persistence/payload types, including `ManagedShop` DBRefs/lists).

### Shared application support

- `exception.APIException`, `exception.APIResponse`.
- `payload.PaginatedResponse`.
- `util.DateTimeUtils`, `AmountUtils`, `TaxUtils`, and `GcsImageUploader`.
- Notification/integration beans such as mail and WhatsApp services.

These may be retained as platform/core infrastructure. If all non-module packages are to be deleted, move or replace them first. When replacing persistence model types, preserve collection fields, DBRef targets, and stored document shapes.

## Shared data collections are not Java dependencies

Module code still needs to preserve compatibility with existing Mongo collections such as `employee_activities`, `users`, `shops`, `invoice`, and `receipt`. Reading/writing those collections does not itself require keeping the legacy Java classes when module-owned models and repositories are used. The activity collection remains shared data and is now accessed by module-owned core code/reporting.

## Removal readiness

**No direct legacy business-service blocker was found in the searched module source for the specific services listed above.** However, the modules are not independent of the top-level legacy package layout because they still import shared domain, security, exception, payload, utility, configuration, and integration classes.

Before deleting old packages:

1. Decide which top-level classes are retained platform infrastructure versus code to remove.
2. Move/replace every required shared type that is scheduled for deletion, including the model types referenced by `ManagedShop`.
3. Run a repository-wide reference scan (including tests and configuration), then build and exercise module endpoints after the moves.

This is a source scan, not a successful-build verification. Repeat the scan immediately before deletion because dependencies can change.
