package com.saritha.fashions.modules.invoice;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.invoice.payload.CreateInvoiceRequest;
import com.saritha.fashions.modules.invoice.payload.DeleteInvoiceResponse;
import com.saritha.fashions.modules.invoice.payload.EditInvoiceRequest;
import com.saritha.fashions.modules.invoice.payload.InvoiceResponse;
import com.saritha.fashions.modules.invoice.payload.InvoiceSummaryResponse;
import com.saritha.fashions.payload.PaginatedResponse;
import com.saritha.fashions.modules.invoice.service.InvoiceReadService;
import com.saritha.fashions.modules.invoice.service.InvoiceReadService.InvoicePdf;
import com.saritha.fashions.modules.invoice.service.InvoiceWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for invoice management.
 *
 * - GET    /invoices                   list / search the shop's invoices (paginated)
 * - GET    /invoices/{invoiceId}       one invoice in full
 * - GET    /invoices/{invoiceId}/pdf   download the invoice PDF
 * - POST   /invoices                   create an invoice (multipart: invoiceJson + images, or JSON)
 * - PATCH  /invoices/{invoiceId}       edit an invoice without receipt (multipart: editJson + images, or JSON)
 * - DELETE /invoices/{invoiceId}       delete (archive) an invoice without receipt
 *
 * Access (WebSecurityConfig + services): GET is for the platform OWNER (any shop, with shopId)
 * and the SHOP_OWNER (own shop); create / edit / delete are SHOP_OWNER only. Invoices of
 * other shops return "Invoice not found".
 */
@RestController
@RequestMapping("/v1/api")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Invoices",
        description = "Shop owners create, list, edit and delete customer invoices (with GST, item images "
                + "and customer notifications) and download invoice PDFs. Reads also work for the platform "
                + "OWNER with shopId; writes need a SHOP_OWNER token.")
public class InvoiceController {

    private static final String AUTH_HEADER_DESCRIPTION =
            "Bearer token of a SHOP_OWNER; the invoice belongs to the owner's shop";

    private static final String FORBIDDEN_EXAMPLE =
            "{\"message\": \"You are not authorized to access this resource\", \"status\": false}";

    private static final String SHOP_ID_DESCRIPTION =
            "Platform OWNER only (required for them): the shop to read. Ignored for a SHOP_OWNER, who always "
                    + "reads their own shop.";

    private static final String CONFLICT_EXAMPLE = "{\"message\": \"The invoice was changed by someone else "
            + "(payment, receipt or production update) while you were editing it. Reload it and try again.\", "
            + "\"status\": false}";

    private static final String EDIT_JSON_EXAMPLE = "{\n"
            + "  \"expectedDeliveryDate\": \"2026-11-28\",\n"
            + "  \"notes\": \"Customer will collect on Saturday\",\n"
            + "  \"discount\": 150,\n"
            + "  \"items\": [\n"
            + "    { \"itemUniqueId\": \"Blouse_1762935000000_0\", \"itemName\": \"Blouse\", \"itemQuantity\": 3,\n"
            + "      \"itemPrice\": 850, \"measurements\": { \"Chest\": \"36\", \"Length\": \"15\" } },\n"
            + "    { \"itemName\": \"Saree Pico\", \"itemQuantity\": 1, \"itemPrice\": 120 }\n"
            + "  ],\n"
            + "  \"customer\": { \"name\": \"Priya Krishnan\" }\n"
            + "}";

    private static final String EDIT_DESCRIPTION = "Edits an invoice of the shop owner's shop. Every field is optional; "
            + "fields left out are not changed. Invoice fields (items, discount, delivery date, notes) can only be "
            + "changed while the invoice has **no receipt** and is not delivered; a **customer correction** is "
            + "allowed at any time.\n\n"
            + "- `customer`: corrects the customer's `name`, `phone`, `email`, `alternatePhone`, `address` "
            + "(`\"\"` removes email / alternatePhone / address). The **customer record** is changed, so all of "
            + "the customer's invoices, receipts and PDFs show the correction. A phone that belongs to another "
            + "customer is refused.\n"
            + "- `expectedDeliveryDate`, `notes`: replaced (empty notes reset to the default text; an empty date clears it).\n"
            + "- `discount`: replaced; totals, GST, balance and payment status are recalculated.\n"
            + "- `items`: the **complete** list after the edit. Existing items keep their `itemUniqueId`; items "
            + "without one are added (they start in ORDER_ACCEPTED, or CUTTING for special items); existing items "
            + "missing from the list are removed. Items already in production (assigned, or past their start "
            + "status) may stay unchanged but cannot be changed or removed. Totals are recalculated; the amount "
            + "already paid must still fit in the new total.\n\n"
            + "Not editable here: customer state, invoice date, payments (payment endpoints) and production "
            + "statuses (item endpoints).\n\n"
            + "**Concurrency:** if the invoice changes while you edit (payment, receipt, production update), "
            + "nothing is saved and **409** is returned; reload and retry.";

    private static final String INVOICE_JSON_EXAMPLE = "{\n"
            + "  \"customer\": {\n"
            + "    \"name\": \"Priya K\",\n"
            + "    \"phone\": \"9876543210\",\n"
            + "    \"email\": \"priya@example.com\",\n"
            + "    \"address\": \"12, 2nd Avenue, Anna Nagar, Chennai\"\n"
            + "  },\n"
            + "  \"customerState\": \"Tamil Nadu\",\n"
            + "  \"items\": [\n"
            + "    { \"itemName\": \"Blouse\", \"itemQuantity\": 2, \"itemPrice\": 850,\n"
            + "      \"measurements\": { \"Chest\": \"36\", \"Length\": \"14\" } },\n"
            + "    { \"itemName\": \"Lining Material\", \"itemQuantity\": 2.5, \"itemPrice\": 60 }\n"
            + "  ],\n"
            + "  \"discount\": 100,\n"
            + "  \"advancePaid\": 1000,\n"
            + "  \"paymentMethod\": \"UPI\",\n"
            + "  \"expectedDeliveryDate\": \"2026-11-25\",\n"
            + "  \"notes\": \"Urgent - wedding on 27th\",\n"
            + "  \"isBackDate\": false\n"
            + "}";

    private final InvoiceWriteService invoiceWriteService;
    private final InvoiceReadService invoiceReadService;

    public InvoiceController(InvoiceWriteService invoiceWriteService,
                                       InvoiceReadService invoiceReadService) {
        this.invoiceWriteService = invoiceWriteService;
        this.invoiceReadService = invoiceReadService;
    }

    /**
     * Creates an invoice.
     * POST /v1/api/invoices  (multipart/form-data)
     */
    @Operation(
            summary = "Create an invoice",
            description = "Creates an invoice for the shop owner's shop and returns it as JSON (201). "
                    + "Download the PDF from **pdfUrl** in the response.\n\n"
                    + "**Request (multipart/form-data):**\n"
                    + "- `invoiceJson` (required): the invoice as JSON text (see CreateInvoiceRequest). "
                    + "Same field names as the old /v1/api/invoices/generate-invoice; extra fields are ignored.\n"
                    + "- `images` (optional, repeatable): item reference images. Each file name must be "
                    + "`item_{index}_{anything}.{ext}`, where index is the item's 0-based position in `items`. "
                    + "Images only, max 10 MB each, at most one per item.\n\n"
                    + "**What the server does:**\n"
                    + "- finds the customer by phone, or creates them (existing details are not changed)\n"
                    + "- calculates totalAmount from the items (a sent totalAmount must match), "
                    + "GST (same state as the shop: CGST 2.5% + SGST 2.5%; other state: IGST 5%), "
                    + "total with GST, balance due and payment status (Dues / Partial / Paid)\n"
                    + "- assigns the invoice number `{SHOP}-{dd-MM-yyyy}-{00001}` and item ids; items "
                    + "Embroidery, Lining Material, Saree Fall, Saree Pico, Designing start in CUTTING, "
                    + "others in ORDER_ACCEPTED\n"
                    + "- uploads images; if saving fails, uploaded images are deleted\n"
                    + "- after saving, sends the customer a WhatsApp message and (if they have an email) an "
                    + "email with a secure download link, in the background. A delivery failure never "
                    + "affects the invoice; see `notifications` in the response.",
            parameters = @Parameter(hidden = true),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = CreateInvoiceMultipartForm.class),
                            encoding = @Encoding(name = "invoiceJson", contentType = MediaType.APPLICATION_JSON_VALUE))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Invoice created",
                            content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Invalid JSON / fields / images / amounts, or the shop owner is not assigned to a shop",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Field errors", value = "{\n"
                                            + "  \"customer.phone\": \"customer.phone must be a 10-digit mobile number\",\n"
                                            + "  \"items[0].itemQuantity\": \"itemQuantity can have at most 2 decimal places\"\n}"),
                                    @ExampleObject(name = "Amount mismatch", value =
                                            "{\"totalAmount\": \"totalAmount 1800.0 does not match the items total 1850.0\"}"),
                                    @ExampleObject(name = "Advance too high", value =
                                            "{\"advancePaid\": \"advancePaid must not exceed the total with GST 1680.0\"}"),
                                    @ExampleObject(name = "Bad image", value =
                                            "{\"images[0]\": \"file 'photo.jpg' must look like item_{index}_{name}.{ext}\"}"),
                                    @ExampleObject(name = "Not assigned to a shop", value =
                                            "{\"message\": \"User is not assigned to any shop\", \"status\": false}")
                            })),
                    @ApiResponse(responseCode = "403", description = "No JWT sent, or the user is not a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @PostMapping(value = "/invoices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponse> createInvoice(
            @RequestPart("invoiceJson") String invoiceJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        InvoiceResponse invoice = invoiceWriteService.createInvoice(invoiceJson, images);
        return ResponseEntity.created(URI.create(invoice.getPdfUrl())).body(invoice);
    }

    /**
     * Creates an invoice without images, from a plain JSON body.
     * POST /v1/api/invoices  (application/json)
     *
     * Same URL and same processing as the multipart version; Spring picks this method
     * when Content-Type is application/json.
     */
    // NOTE: no "Authorization" @Parameter here on purpose. Springdoc merges this method and the
    // multipart createInvoice (same path + POST) into ONE documented operation; declaring the header
    // on both makes the merge fail with "Duplicate key Parameter" and /v3/api-docs returns 500.
    @Operation(
            summary = "Create an invoice (JSON, no images)",
            description = "Same as the multipart version, but the request body is the invoice JSON itself "
                    + "(Content-Type: application/json). Use this when the invoice has no item images.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreateInvoiceRequest.class),
                            examples = @ExampleObject(value = INVOICE_JSON_EXAMPLE))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Invoice created",
                            content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid JSON / fields / amounts"),
                    @ApiResponse(responseCode = "403", description = "No JWT sent, or the user is not a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @PostMapping(value = "/invoices", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InvoiceResponse> createInvoiceFromJson(@RequestBody String invoiceJson) {
        InvoiceResponse invoice = invoiceWriteService.createInvoice(invoiceJson, null);
        return ResponseEntity.created(URI.create(invoice.getPdfUrl())).body(invoice);
    }

    /**
     * Downloads the invoice PDF.
     * GET /v1/api/invoices/{invoiceId}/pdf
     */
    @Operation(
            summary = "Download an invoice PDF",
            description = "Generates the invoice PDF (same template as before). A SHOP_OWNER gets invoices of "
                    + "their own shop; the platform OWNER passes shopId. Invoices of other shops return "
                    + "\"Invoice not found\".",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true,
                            description = "Invoice number", example = "SARI-12-11-2026-00042"),
                    @Parameter(name = "shopId", in = ParameterIn.QUERY, description = SHOP_ID_DESCRIPTION)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "The PDF file",
                            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "400", description = "Invoice not found in the owner's shop, or PDF generation failed",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Invoice not found\", \"status\": false}"))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "The user is not the platform OWNER or a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @GetMapping(value = "/invoices/{invoiceId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable String invoiceId,
                                                     @RequestParam(name = "shopId", required = false) String shopId) {
        InvoicePdf pdf = invoiceReadService.getInvoicePdf(invoiceId, shopId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(pdf.fileName()).build());
        headers.setContentLength(pdf.content().length);
        return new ResponseEntity<>(pdf.content(), headers, HttpStatus.OK);
    }

    /**
     * Lists the shop's invoices.
     * GET /v1/api/invoices
     */
    @Operation(
            summary = "List invoices",
            description = "One page of the shop's invoices, newest first. Replaces the old invoice list, search, "
                    + "pending / delivered and special-category screens.\n\n"
                    + "- `status`: `pending` (not delivered yet), `delivered`, or `all` (default)\n"
                    + "- `from` / `to`: invoice date range (yyyy-MM-dd, both inclusive, IST)\n"
                    + "- `search`: text in the invoice number, customer name or customer phone (literal, case-insensitive)\n"
                    + "- `paymentStatus`: Dues, Partial or Paid\n"
                    + "- `special=true`: only invoices with Embroidery, Lining Material, Saree Fall, Saree Pico or Designing\n"
                    + "- `details=true`: full invoices (InvoiceResponse) instead of summary rows (InvoiceSummary)\n\n"
                    + "A SHOP_OWNER sees their own shop; the platform OWNER must pass shopId.",
            responses = {
                    @ApiResponse(responseCode = "200",
                            description = "PaginatedResponse whose content holds InvoiceSummary rows (or InvoiceResponse with details=true)",
                            content = @Content(schema = @Schema(implementation = InvoiceSummaryPage.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid filter / paging, shopId missing for the OWNER, shop not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"status must be pending, delivered or all\", \"status\": false}"))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "The user is not the platform OWNER or a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @GetMapping("/invoices")
    public ResponseEntity<PaginatedResponse<?>> listInvoices(
            @Parameter(description = SHOP_ID_DESCRIPTION) @RequestParam(name = "shopId", required = false) String shopId,
            @Parameter(example = "2026-11-01") @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(example = "2026-11-30") @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "pending, delivered or all", example = "pending")
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Dues, Partial or Paid") @RequestParam(name = "paymentStatus", required = false) String paymentStatus,
            @Parameter(description = "Invoice number, customer name or phone", example = "Priya")
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "special", defaultValue = "false") boolean special,
            @RequestParam(name = "details", defaultValue = "false") boolean details,
            @Parameter(description = "newest (default) or oldest") @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @Parameter(description = "1 to 100") @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(invoiceReadService.listInvoices(shopId, from, to, status, paymentStatus, search,
                special, details, sort, pageNumber, pageSize));
    }

    /**
     * One invoice.
     * GET /v1/api/invoices/{invoiceId}
     */
    @Operation(
            summary = "Get an invoice",
            description = "The full invoice with items, amounts, payments and receiptId (null until the receipt "
                    + "is generated). A SHOP_OWNER reads their own shop; the platform OWNER passes shopId.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The invoice",
                            content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invoice not found in the shop, or shopId missing for the OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Invoice not found\", \"status\": false}"))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "The user is not the platform OWNER or a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)))
            })
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @Parameter(example = "SARI-12-11-2026-00042") @PathVariable String invoiceId,
            @Parameter(description = SHOP_ID_DESCRIPTION) @RequestParam(name = "shopId", required = false) String shopId) {
        return ResponseEntity.ok(invoiceReadService.getInvoice(invoiceId, shopId));
    }

    /**
     * Edits an invoice (multipart, with item images).
     * PATCH /v1/api/invoices/{invoiceId}
     */
    @Operation(
            summary = "Edit an invoice",
            description = EDIT_DESCRIPTION + "\n\n**Request (multipart/form-data):** `editJson` (required, the "
                    + "EditInvoiceRequest as JSON) and optional `images` named `item_{index}_{name}.{ext}`, where "
                    + "index is the item's position in `items` (a new image replaces the item's old one). Images "
                    + "need `items` in editJson.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = EditInvoiceMultipartForm.class),
                            encoding = @Encoding(name = "editJson", contentType = MediaType.APPLICATION_JSON_VALUE))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The updated invoice",
                            content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
                    @ApiResponse(responseCode = "400",
                            description = "Invalid fields / images, item in production changed or removed, receipt already "
                                    + "generated, new total below the amount paid, invoice not found",
                            content = @Content(examples = {
                                    @ExampleObject(name = "Item in production", value =
                                            "{\"items[0]\": \"item Blouse_1762935000000_0 is already in production (STITCHING) and cannot be changed\"}"),
                                    @ExampleObject(name = "Receipt exists", value =
                                            "{\"message\": \"Invoice SARI-12-11-2026-00042 already has receipt SARI-R-00007 and can no longer be edited\", \"status\": false}"),
                                    @ExampleObject(name = "Below amount paid", value =
                                            "{\"items\": \"the new total with GST would be less than the 1500.0 already paid\"}")
                            })),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "The user is not a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
                    @ApiResponse(responseCode = "409", description = "The invoice changed during the edit; nothing was saved",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PatchMapping(value = "/invoices/{invoiceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceResponse> editInvoice(
            @Parameter(example = "SARI-12-11-2026-00042") @PathVariable String invoiceId,
            @RequestPart("editJson") String editJson,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ResponseEntity.ok(invoiceWriteService.editInvoice(invoiceId, editJson, images));
    }

    /**
     * Edits an invoice (plain JSON, no images).
     * PATCH /v1/api/invoices/{invoiceId}
     */
    @Operation(
            summary = "Edit an invoice (JSON, no images)",
            description = EDIT_DESCRIPTION,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EditInvoiceRequest.class),
                            examples = @ExampleObject(value = EDIT_JSON_EXAMPLE))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The updated invoice",
                            content = @Content(schema = @Schema(implementation = InvoiceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid fields, item in production changed, receipt exists"),
                    @ApiResponse(responseCode = "409", description = "The invoice changed during the edit; nothing was saved",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PatchMapping(value = "/invoices/{invoiceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InvoiceResponse> editInvoiceFromJson(@PathVariable String invoiceId,
                                                               @RequestBody String editJson) {
        return ResponseEntity.ok(invoiceWriteService.editInvoice(invoiceId, editJson, null));
    }

    /**
     * Deletes (archives) an invoice.
     * DELETE /v1/api/invoices/{invoiceId}?reason=...
     */
    @Operation(
            summary = "Delete an invoice",
            description = "Deletes an invoice of the shop owner's shop that has **no receipt yet** and is not "
                    + "delivered (delete the receipt first otherwise). The invoice is archived in deleted_invoices "
                    + "with the reason, removed from the customer, its item images are deleted and an "
                    + "INVOICE_DELETED activity is recorded. If it was the shop's **latest** invoice, its number is "
                    + "released and the next invoice gets it, so bill numbers stay in sequence (deleting an older "
                    + "invoice leaves a gap).\n\n"
                    + "If the invoice changes meanwhile (payment, receipt, production update) nothing is deleted and "
                    + "409 is returned.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Invoice deleted",
                            content = @Content(schema = @Schema(implementation = DeleteInvoiceResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invoice not found, receipt exists, delivered, reason too long",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Invoice SARI-12-11-2026-00042 already has receipt "
                                            + "SARI-R-00007 and can no longer be deleted\", \"status\": false}"))),
                    @ApiResponse(responseCode = "401", description = "No or invalid JWT"),
                    @ApiResponse(responseCode = "403", description = "The user is not a SHOP_OWNER",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
                    @ApiResponse(responseCode = "409", description = "The invoice changed meanwhile; nothing was deleted",
                            content = @Content(schema = @Schema(implementation = APIResponse.class)))
            })
    @DeleteMapping("/invoices/{invoiceId}")
    public ResponseEntity<DeleteInvoiceResponse> deleteInvoice(
            @Parameter(example = "SARI-12-11-2026-00042") @PathVariable String invoiceId,
            @Parameter(description = "Optional reason (max 300 characters), stored with the archived invoice",
                    example = "Duplicate invoice")
            @RequestParam(name = "reason", required = false) String reason) {
        return ResponseEntity.ok(invoiceWriteService.deleteInvoice(invoiceId, reason));
    }

    /**
     * Swagger-only description of the edit multipart form (not used at runtime).
     */
    @Schema(name = "EditInvoiceMultipartForm")
    static class EditInvoiceMultipartForm {

        @Schema(description = "The edit as JSON text", requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = EditInvoiceRequest.class, example = EDIT_JSON_EXAMPLE)
        public EditInvoiceRequest editJson;

        @ArraySchema(arraySchema = @Schema(description = "Optional item images named item_{index}_{name}.{ext}"),
                schema = @Schema(type = "string", format = "binary"))
        public List<MultipartFile> images;
    }

    /** Swagger-only: the list response with summary rows. */
    @Schema(name = "InvoiceSummaryPage")
    static class InvoiceSummaryPage {
        @ArraySchema(schema = @Schema(implementation = InvoiceSummaryResponse.class))
        public List<InvoiceSummaryResponse> content;
        @Schema(example = "0")
        public Integer pageNumber;
        @Schema(example = "20")
        public Integer pageSize;
        @Schema(example = "57")
        public Long totalElements;
        @Schema(example = "3")
        public Integer totalPages;
        @Schema(example = "false")
        public boolean lastPage;
    }

    /**
     * Swagger-only description of the multipart form (not used at runtime).
     */
    @Schema(name = "CreateInvoiceMultipartForm")
    static class CreateInvoiceMultipartForm {

        @Schema(description = "The invoice as JSON text", requiredMode = Schema.RequiredMode.REQUIRED,
                implementation = CreateInvoiceRequest.class, example = INVOICE_JSON_EXAMPLE)
        public CreateInvoiceRequest invoiceJson;

        @ArraySchema(arraySchema = @Schema(description = "Optional item images named item_{index}_{name}.{ext}"),
                schema = @Schema(type = "string", format = "binary"))
        public List<MultipartFile> images;
    }
}
