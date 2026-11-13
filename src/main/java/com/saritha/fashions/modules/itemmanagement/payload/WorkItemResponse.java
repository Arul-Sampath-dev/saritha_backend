package com.saritha.fashions.modules.itemmanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * One invoice item as a unit of work, with what the current user can do next.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WorkItem", description = "An invoice item in production")
public class WorkItemResponse {

    @Schema(description = "Invoice number", example = "SARI-12-11-2026-00042")
    private String invoiceId;

    @Schema(description = "Item id", example = "Blouse_1762935000000_0")
    private String itemUniqueId;

    @Schema(example = "Blouse")
    private String itemName;

    @Schema(example = "2")
    private int itemQuantity;

    @Schema(description = "Reference image URL, if uploaded")
    private String imageUrl;

    @Schema(description = "Current production status", example = "TAILOR_ASSIGNED")
    private String status;

    @Schema(description = "Assigned tailor")
    private Person tailor;

    @Schema(description = "Assigned shop assistant")
    private Person shopAssistant;

    private Map<String, String> measurements;
    private List<Map<String, Object>> measurementList;

    @Schema(description = "Customer name", example = "Priya K")
    private String customerName;

    @Schema(example = "2026-11-25")
    private String expectedDeliveryDate;

    private Date invoiceDate;

    @Schema(description = "What the CURRENT user may do next. Status values (e.g. \"STITCHING\") go to "
            + "PATCH .../status; ASSIGN_TAILOR / ASSIGN_SHOP_ASSISTANT go to the assignment endpoints. "
            + "Empty when the user cannot act on this item.",
            example = "[\"STITCHING\", \"ASSIGN_SHOP_ASSISTANT\"]")
    private List<String> availableActions;

    @Schema(description = "Status and assignment history (oldest first)")
    private List<HistoryEntry> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "WorkItemPerson")
    public static class Person {
        @Schema(example = "665f0a1b2c3d4e5f6a7b8c9d")
        private String userId;
        @Schema(example = "Lakshmi")
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "WorkItemHistoryEntry")
    public static class HistoryEntry {
        @Schema(description = "User the entry is about (assignee or the user who changed the status)")
        private String userId;
        @Schema(description = "Status set / action taken", example = "STITCHING")
        private String action;
        private Date timestamp;
    }
}
