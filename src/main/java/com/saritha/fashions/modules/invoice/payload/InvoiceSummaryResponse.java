package com.saritha.fashions.modules.invoice.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * One row of an invoice list (use details=true on the list for full invoices).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InvoiceSummary", description = "An invoice in a list")
public class InvoiceSummaryResponse {

    @Schema(example = "6672b1c0d2e3f4a5b6c7d8e9")
    private String id;
    @Schema(example = "SARI-12-11-2026-00042")
    private String invoiceId;
    private Date invoiceDate;
    @Schema(example = "2026-11-25")
    private String expectedDeliveryDate;

    @Schema(example = "CUS-8F3K2Q1ZXA")
    private String customerId;
    @Schema(example = "Priya K")
    private String customerName;
    @Schema(example = "9876543210")
    private String customerPhone;

    @Schema(example = "3")
    private int itemCount;
    @Schema(example = "[\"Blouse\", \"Chudidhar\", \"Saree Fall\"]")
    private List<String> itemNames;
    @Schema(description = "Items already ORDER_COMPLETED", example = "1")
    private int completedItemCount;

    @Schema(example = "3150.0")
    private double totalAmountWithGST;
    @Schema(example = "1500.0")
    private double advancePaid;
    @Schema(example = "1650.0")
    private double balanceDue;
    @Schema(example = "Partial")
    private String paymentStatus;
    @Schema(description = "ORDER_ACCEPTED until the receipt is generated, then ORDER_DELIVERED", example = "ORDER_ACCEPTED")
    private String orderStatus;

    @Schema(example = "/v1/api/invoices/SARI-12-11-2026-00042/pdf")
    private String pdfUrl;

    @Schema(description = "Production stage of the invoice: NEW (nothing started), IN_PRODUCTION, READY (all items "
            + "ORDER_COMPLETED, not yet delivered) or DELIVERED", example = "IN_PRODUCTION")
    private String stage;

    @Schema(description = "Number of items per status (every workflow status, 0 when none)",
            example = "{\"ORDER_ACCEPTED\": 0, \"CUTTING\": 1, \"TAILOR_ASSIGNED\": 0, \"STITCHING\": 1, "
                    + "\"SHOP_ASSISTANT_ASSIGNED\": 0, \"HEMMING\": 0, \"IRONING\": 0, \"ORDER_COMPLETED\": 1}")
    private Map<String, Integer> itemStatusCounts;

    @Schema(description = "Receipt number once delivered (use with GET /v1/api/receipts/{receiptNo}/pdf); null before that",
            example = "SARI-R-20-11-2026-00007")
    private String receiptNo;

    @Schema(description = "When the order was delivered (the delivery receipt's date); null before that",
            example = "2026-11-20T10:15:00.000+00:00")
    private Date deliveredAt;
}
