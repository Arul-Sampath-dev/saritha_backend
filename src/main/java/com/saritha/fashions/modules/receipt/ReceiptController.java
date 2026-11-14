package com.saritha.fashions.modules.receipt;

import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.receipt.payload.ApplyDiscountRequest;
import com.saritha.fashions.modules.receipt.payload.InvoiceAccountResponse;
import com.saritha.fashions.modules.receipt.payload.PaymentReceiptResponse;
import com.saritha.fashions.modules.receipt.payload.ReceiptResponse;
import com.saritha.fashions.modules.receipt.payload.RecordPaymentRequest;
import com.saritha.fashions.modules.receipt.service.InvoicePaymentService;
import com.saritha.fashions.modules.receipt.service.ReceiptReadService;
import com.saritha.fashions.modules.receipt.service.ReceiptReadService.ReceiptPdf;
import com.saritha.fashions.modules.receipt.service.ReceiptWriteService;
import com.saritha.fashions.payload.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payments, discounts and receipts.
 *
 *  GET  /invoices/{invoiceId}/account     totals, payments and balance      (shop owner)
 *  POST /invoices/{invoiceId}/payments    record a payment                  (shop owner)
 *  POST /invoices/{invoiceId}/discount    one-time discount + credit note   (shop owner)
 *  POST /invoices/{invoiceId}/receipt     generate receipt = deliver order  (shop owner)
 *  GET  /receipts                         list (own shop / all for OWNER)
 *  GET  /receipts/{receiptNo}             one receipt
 *  GET  /receipts/{receiptNo}/pdf         receipt PDF
 *
 * Replaces /v1/api/receipt/** and PUT /v1/api/invoices/{id}/update-payment.
 *
 * Access: WebSecurityConfig allows OWNER and SHOP_OWNER to read receipts (GET /receipts/**)
 * and only SHOP_OWNER for everything else; the services also check the shop.
 */
@RestController
@RequestMapping("/v1/api")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Payments & Receipts",
        description = "Collect payments and discounts on an invoice, then generate the receipt â€” the final, fully paid "
                + "bill, which also marks the order delivered. Shop owners work on their own shop; the platform "
                + "owner can read receipts of all shops.")
public class ReceiptController {

    private static final String AUTH = "Bearer token of a SHOP_OWNER";
    private static final String CONFLICT_EXAMPLE = "{\"message\": \"The invoice was changed by someone else in the "
            + "meantime. Please reload it and try again.\", \"status\": false}";

    private final InvoicePaymentService paymentService;
    private final ReceiptWriteService writeService;
    private final ReceiptReadService readService;

    public ReceiptController(InvoicePaymentService paymentService,
                                       ReceiptWriteService writeService,
                                       ReceiptReadService readService) {
        this.paymentService = paymentService;
        this.writeService = writeService;
        this.readService = readService;
    }

    // ------------------------------------------------------------------ invoice account

    @Operation(summary = "Invoice account (totals, payments, balance)",
            description = "Everything needed on the payment / receipt page: totals with GST, discount, every payment, "
                    + "amount paid and balance due (always total with GST âˆ’ amount paid).",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "The account",
                            content = @Content(schema = @Schema(implementation = InvoiceAccountResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invoice not found in your shop",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner")
            })
    @GetMapping("/invoices/{invoiceId}/account")
    public ResponseEntity<InvoiceAccountResponse> getAccount(@PathVariable String invoiceId) {
        return ResponseEntity.ok(paymentService.getAccount(invoiceId));
    }

    @Operation(summary = "Record a payment",
            description = "Adds a payment to the invoice. It must be greater than 0 and not more than the balance due. "
                    + "Payment status becomes Partial or Paid. Not allowed after the receipt is generated.",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = RecordPaymentRequest.class),
                            examples = @ExampleObject(value = "{ \"amount\": 650, \"paymentMethod\": \"UPI\", \"transactionId\": \"UPI-4589123\" }"))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Payment recorded; the updated account",
                            content = @Content(schema = @Schema(implementation = InvoiceAccountResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid amount, more than the balance due, already "
                            + "fully paid, receipt already generated, or invoice not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = "{\"message\": \"Payment 2000.0 exceeds the balance due 1650.0\", \"status\": false}"))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner"),
                    @ApiResponse(responseCode = "409", description = "Invoice changed meanwhile (e.g. two payments at once); reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PostMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<InvoiceAccountResponse> recordPayment(@PathVariable String invoiceId,
                                                                @Valid @RequestBody RecordPaymentRequest request) {
        return new ResponseEntity<>(paymentService.recordPayment(invoiceId, request), HttpStatus.CREATED);
    }

    @Operation(summary = "Give a discount (credit note)",
            description = "One discount per invoice, on the pre-GST total. GST is recalculated on the reduced amount (same "
                    + "GST type as the invoice), the balance due is updated, and a credit note is issued. Rejected if the "
                    + "customer has already paid more than the discounted total (refunds are not supported).",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(hidden = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = ApplyDiscountRequest.class),
                            examples = @ExampleObject(value = "{ \"amount\": 100, \"reason\": \"Delay in delivery\" }"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Discount applied; the updated account with creditNoteId",
                            content = @Content(schema = @Schema(implementation = InvoiceAccountResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Discount already given, larger than the invoice, would need a "
                            + "refund, receipt already generated, or invoice not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner"),
                    @ApiResponse(responseCode = "409", description = "Invoice changed meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PostMapping("/invoices/{invoiceId}/discount")
    public ResponseEntity<InvoiceAccountResponse> applyDiscount(@PathVariable String invoiceId,
                                                                @Valid @RequestBody ApplyDiscountRequest request) {
        return ResponseEntity.ok(paymentService.applyDiscount(invoiceId, request));
    }

    // ------------------------------------------------------------------ receipts

    @Operation(summary = "Generate the receipt (deliver the order)",
            description = "Creates the receipt for a **fully paid** invoice whose items are **all ORDER_COMPLETED**, marks the "
                    + "invoice delivered, and sends the customer the receipt link by WhatsApp (and email if known). "
                    + "Calling it again returns the existing receipt (`created: false`, 200).",
            parameters = {
                    @Parameter(name = "invoiceId", in = ParameterIn.PATH, required = true, example = "SARI-12-11-2026-00042"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "201", description = "Receipt created",
                            content = @Content(schema = @Schema(implementation = ReceiptResponse.class))),
                    @ApiResponse(responseCode = "200", description = "Receipt already existed (created: false)",
                            content = @Content(schema = @Schema(implementation = ReceiptResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Balance still due, items not completed, or invoice not found",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = {
                                            @ExampleObject(name = "Balance due", value = "{\"message\": \"Collect the remaining balance of "
                                                    + "650.0 before generating the receipt (POST .../payments)\", \"status\": false}"),
                                            @ExampleObject(name = "Items not finished", value = "{\"message\": \"All items must be "
                                                    + "ORDER_COMPLETED before delivery. Not finished: Blouse (IRONING)\", \"status\": false}")
                                    })),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner"),
                    @ApiResponse(responseCode = "409", description = "Invoice changed meanwhile; reload and retry",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = CONFLICT_EXAMPLE)))
            })
    @PostMapping("/invoices/{invoiceId}/receipt")
    public ResponseEntity<ReceiptResponse> generateReceipt(@PathVariable String invoiceId) {
        ReceiptResponse receipt = writeService.generateReceipt(invoiceId);
        return new ResponseEntity<>(receipt, Boolean.TRUE.equals(receipt.getCreated()) ? HttpStatus.CREATED : HttpStatus.OK);
    }

    @Operation(summary = "List receipts",
            description = "Newest first. A shop owner sees their own shop. The platform OWNER sees all shops, or one shop "
                    + "with `shopId`.",
            parameters = {
                    @Parameter(name = "shopId", in = ParameterIn.QUERY, description = "Platform owner only: filter by shop"),
                    @Parameter(name = "pageNumber", in = ParameterIn.QUERY, example = "0"),
                    @Parameter(name = "pageSize", in = ParameterIn.QUERY, description = "1â€“100", example = "20"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "A page of receipts (content = Receipt[])"),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner")
            })
    @GetMapping("/receipts")
    public ResponseEntity<PaginatedResponse<ReceiptResponse>> getReceipts(
            @RequestParam(name = "shopId", required = false) String shopId,
            @RequestParam(name = "pageNumber", defaultValue = "0") int pageNumber,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(readService.getReceipts(shopId, pageNumber, pageSize));
    }

    @Operation(summary = "Get a receipt",
            parameters = {
                    @Parameter(name = "receiptNo", in = ParameterIn.PATH, required = true, example = "SARI-13-11-2026-00007"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "The receipt",
                            content = @Content(schema = @Schema(implementation = ReceiptResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Receipt not found (or belongs to another shop)",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner")
            })
    @GetMapping("/receipts/{receiptNo}")
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable String receiptNo) {
        return ResponseEntity.ok(readService.getReceipt(receiptNo));
    }

    @Operation(summary = "Download a receipt PDF",
            description = "Same template as before. Customers use the public link sent by WhatsApp / email instead.",
            parameters = {
                    @Parameter(name = "receiptNo", in = ParameterIn.PATH, required = true, example = "SARI-13-11-2026-00007"),
                    @Parameter(hidden = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "The PDF file",
                            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "400", description = "Receipt not found, or PDF generation failed",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner")
            })
    @GetMapping(value = "/receipts/{receiptNo}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReceiptPdf(@PathVariable String receiptNo) {
        ReceiptPdf pdf = readService.getReceiptPdf(receiptNo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(pdf.fileName()).build());
        headers.setContentLength(pdf.content().length);
        return new ResponseEntity<>(pdf.content(), headers, HttpStatus.OK);
    }

    // ------------------------------------------------------------------ payment receipts

    @Operation(summary = "Payment receipts of an invoice",
            description = "One payment receipt per payment recorded with POST .../payments, oldest first: amount "
                    + "received, method, and the invoice's total paid and balance right after that payment. "
                    + "Shop owner: own shop; platform OWNER: any shop (optional shopId).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The payment receipts (empty if none)",
                            content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                                    schema = @Schema(implementation = PaymentReceiptResponse.class)))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner")
            })
    @GetMapping("/invoices/{invoiceId}/payment-receipts")
    public ResponseEntity<java.util.List<PaymentReceiptResponse>> listPaymentReceipts(
            @Parameter(example = "SARI-12-11-2026-00042") @PathVariable String invoiceId,
            @Parameter(description = "Platform OWNER only: limit to this shop")
            @RequestParam(name = "shopId", required = false) String shopId) {
        return ResponseEntity.ok(paymentService.listPaymentReceipts(invoiceId, shopId));
    }

    @Operation(summary = "One payment receipt",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The payment receipt",
                            content = @Content(schema = @Schema(implementation = PaymentReceiptResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Payment receipt not found (also another shop's)",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner")
            })
    @GetMapping("/payment-receipts/{paymentReceiptNo}")
    public ResponseEntity<PaymentReceiptResponse> getPaymentReceipt(
            @Parameter(example = "SARI-PR-14-11-2026-00003") @PathVariable String paymentReceiptNo,
            @Parameter(description = "Platform OWNER only: limit to this shop")
            @RequestParam(name = "shopId", required = false) String shopId) {
        return ResponseEntity.ok(paymentService.getPaymentReceipt(paymentReceiptNo, shopId));
    }

    @Operation(summary = "Download a payment receipt PDF",
            description = "Customers get it through the public link sent by WhatsApp / email after each payment.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The PDF file",
                            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "400", description = "Payment receipt not found, or PDF generation failed",
                            content = @Content(schema = @Schema(implementation = APIResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Not a shop owner or the platform owner")
            })
    @GetMapping(value = "/payment-receipts/{paymentReceiptNo}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPaymentReceiptPdf(
            @PathVariable String paymentReceiptNo,
            @Parameter(description = "Platform OWNER only: limit to this shop")
            @RequestParam(name = "shopId", required = false) String shopId) {
        byte[] pdf = paymentService.getPaymentReceiptPdf(paymentReceiptNo, shopId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(paymentReceiptNo + ".pdf").build());
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
