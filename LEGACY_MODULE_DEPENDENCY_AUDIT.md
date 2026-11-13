# Legacy Code Dependency Audit

**Checked:** 2026-11-13  
**Scope:** `src/main/java/com/saritha/fashions/modules`

## Current status

The modules have fewer legacy runtime dependencies than in the previous audit. Shop creation now calls the authentication module, and activity logging, document numbering, and customer document-token services have module-owned implementations. However, the modules are **still not safe to run without the legacy code** because invoice and receipt PDF generation directly uses legacy repositories, models, and a legacy PDF service.

## Remaining direct runtime blockers

| Module | Legacy dependency | Usage | Required migration |
|---|---|---|---|
| Invoice management | `com.saritha.fashions.repository.InvoiceRepository`, legacy `Invoice` model, `com.saritha.fashions.service.Interface.ReceiptService` | `InvoiceManagementReadServiceImpl.getInvoicePdf()` loads an invoice with the old repository and calls `getInvoicePdfMethod(...)`. | Move the PDF pipeline behind a module-owned service and load the invoice using `InvoiceRecordRepository`/`InvoiceRecord`, preserving output and access checks. |
| Receipt management | `com.saritha.fashions.repository.ReceiptRepository`, legacy `Receipt` model, legacy `ReceiptService` | `ReceiptManagementReadServiceImpl.getReceiptPdf()` loads a receipt from the old repository and calls `getReceiptPdfMethod(...)`. | Move the PDF pipeline behind a module-owned service and load the receipt with `ReceiptRecordRepository`/`ReceiptRecord`. |

These are real Spring dependency-injection and compile-time references. Deleting the corresponding legacy classes now would prevent a successful build/application startup, and the PDF endpoints would not work.

## Dependencies already moved into module-owned code

The following previous findings appear resolved in the current sources:

- Shop management creates the shop owner through authentication module `UserRegistrationService`; it no longer calls `AuthController`.
- Employee, item, invoice, and receipt modules use `modules/core/activity/ActivityLogService`.
- Invoice and receipt number generation uses `modules/core/numbering/DocumentNumberService`.
- Customer-facing invoice/receipt links use `modules/core/documentlink/DocumentTokenService` through `CustomerDocumentLinkService`.

The activity service still writes to the shared MongoDB collection `employee_activities`; this preserves data compatibility and is not itself a dependency on a legacy Java service.

## Shared code outside `modules/`

Many modules still import `com.saritha.fashions.*` classes from the old package layout. This includes:

- Domain/security types: `User`, `AppRole`, and in places `Invoice`, `Receipt`, and `Expense`.
- Security helpers: `AuthUtil`, `UserDetailsImpl`, `JwtUtils`.
- API support: `APIException`, `APIResponse`, `PaginatedResponse`.
- Common utilities and integrations: `DateTimeUtils`, `AmountUtils`, `TaxUtils`, `GcsImageUploader`, mail/WhatsApp services, and related configuration.
- Configuration such as `OrderStatus`.

These imports do not all represent legacy business logic. Several are shared platform or integration components that may intentionally remain outside the feature modules. But if the plan is to delete **all** non-module packages, these classes must first be moved to retained `core`/platform packages or replaced. `ManagedShop` also still uses legacy `User`, `Expense`, `Invoice`, and `Receipt` types for its Mongo document fields/DBRefs, so any type migration must preserve persisted shapes and references.

## Suggested next steps

1. Port invoice and receipt PDF generation, including its template and external conversion dependencies, into module-owned services.
2. Verify module-owned PDF endpoints against existing documents and output behavior.
3. Decide which shared types/utilities are retained platform infrastructure; move only those that must no longer live in the legacy package.
4. Run a final source reference scan and build after migrating the PDF code before deleting legacy classes.

## Removal readiness

**Not ready for complete legacy-code deletion.** Invoice and receipt PDF endpoints are direct blockers. If only legacy business controllers/services are being removed while shared infrastructure remains, the required migration scope can be narrower—but the two PDF dependencies still need to be addressed if their legacy service/repository classes are included in the deletion.

This is a source-level dependency audit, not a full proof of zero runtime references. Repeat repository-wide reference and build checks immediately before removing old packages.
