package com.saritha.fashions.modules.core.documentlink;

import com.saritha.fashions.exception.APIException;
import com.saritha.fashions.exception.APIResponse;
import com.saritha.fashions.modules.core.pdf.SalesDocumentPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public download of an invoice / receipt PDF from the link sent to the customer (no login; the
 * token itself is the authorisation). Replaces the legacy PublicController download methods:
 *
 *   legacy GET /v1/api/public/download-invoice/{token}  → base64 text, 500 on any error
 *   legacy GET /v1/api/public/download-receipt/{token}  → PDF, 500 on any error
 *   new    GET /v1/api/public/documents/invoices/{token} → PDF
 *   new    GET /v1/api/public/documents/receipts/{token} → PDF
 *
 * An invalid / altered / wrong-type token is a 400 with a generic message, checked before any
 * database or converter work (so random tokens cannot trigger PDF generation). Tokens from links
 * already sent to customers work here too (same token format).
 *
 * Public by the existing "/v1/api/public/**" permitAll rule in WebSecurityConfig.
 */
@RestController
@RequestMapping("/v1/api/public/documents")
@Tag(name = "Customer documents (public)",
        description = "Download an invoice or receipt PDF from the link sent to the customer. No login: the "
                + "encrypted token in the link is the authorisation.")
public class CustomerDocumentController {

    private static final String INVALID_LINK = "This download link is not valid";
    private static final String INVALID_EXAMPLE = "{\"message\": \"This download link is not valid\", \"status\": false}";

    private final DocumentTokenService tokenService;
    private final SalesDocumentPdfService pdfService;

    public CustomerDocumentController(DocumentTokenService tokenService, SalesDocumentPdfService pdfService) {
        this.tokenService = tokenService;
        this.pdfService = pdfService;
    }

    @Operation(
            summary = "Download an invoice PDF (customer link)",
            description = "Returns the invoice PDF for the token in the customer's link (the part after "
                    + "/download-invoice/). Replaces /v1/api/public/download-invoice/{token}, which returned "
                    + "base64 text and a 500 on errors.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The PDF",
                            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "400", description = "Invalid link, invoice no longer exists, or PDF generation failed",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = INVALID_EXAMPLE)))
            })
    @GetMapping(value = "/invoices/{token}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoice(
            @Parameter(description = "Token from the customer's link") @PathVariable String token) {
        String invoiceId = read(DocumentKind.INVOICE, token);
        return pdf(pdfService.invoicePdf(invoiceId, null), invoiceId);
    }

    @Operation(
            summary = "Download a receipt PDF (customer link)",
            description = "Returns the receipt PDF for the token in the customer's link (the part after "
                    + "/download-receipt/). Replaces /v1/api/public/download-receipt/{token}.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The PDF",
                            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE,
                                    schema = @Schema(type = "string", format = "binary"))),
                    @ApiResponse(responseCode = "400", description = "Invalid link, receipt no longer exists, or PDF generation failed",
                            content = @Content(schema = @Schema(implementation = APIResponse.class),
                                    examples = @ExampleObject(value = INVALID_EXAMPLE)))
            })
    @GetMapping(value = "/receipts/{token}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReceipt(
            @Parameter(description = "Token from the customer's link") @PathVariable String token) {
        String receiptNo = read(DocumentKind.RECEIPT, token);
        return pdf(pdfService.receiptPdf(receiptNo, null), receiptNo);
    }

    private String read(DocumentKind kind, String token) {
        try {
            return tokenService.readToken(kind, token);
        } catch (InvalidDocumentTokenException exception) {
            throw new APIException(INVALID_LINK);
        }
    }

    private static ResponseEntity<byte[]> pdf(byte[] content, String number) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(number + ".pdf").build());
        headers.setContentLength(content.length);
        // Customer documents: do not let shared caches / proxies keep a copy.
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
