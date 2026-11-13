package com.saritha.fashions.modules.invoicemanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One tailoring item embedded in an invoice. Same stored field names as
 * com.saritha.fashions.payload.items.Items, so the tailor / shop assistant flows that
 * update items later keep working.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "InvoiceItem", description = "A tailoring item on an invoice")
public class InvoiceItem {

    @Schema(description = "Unique id of the item: {itemName}_{timestamp}_{index}", example = "Blouse_1762935000000_0")
    private String itemUniqueId;

    @Schema(description = "Item name / category", example = "Blouse")
    private String itemName;

    @Schema(description = "Quantity", example = "2")
    private int itemQuantity;

    @Schema(description = "Unit price", example = "850.0")
    private double itemPrice;

    @Schema(description = "Public URL of the reference image, if one was uploaded")
    private String imageUrl;

    @Schema(description = "Production status", example = "ORDER_ACCEPTED")
    private String itemOrderStatus;

    @Schema(description = "Tailor assignment status (set later by tailor assignment)")
    private String assignmentStatus;

    @Schema(description = "Assigned tailor id (set later)")
    private String assignedTo;

    @Schema(description = "Assigned shop assistant id (set later)")
    private String assignedToWhichShopAssistant;

    @Schema(description = "Measurements as name → value", example = "{\"Chest\": \"36\", \"Length\": \"14\"}")
    private Map<String, String> measurements;

    @Schema(description = "Structured measurements")
    private List<ItemMeasurement> measurementList;

    @Builder.Default
    @Schema(description = "Tailor assignment history (filled later)")
    private List<InvoiceAssignmentEntry> assignmentHistory = new ArrayList<>();
}
