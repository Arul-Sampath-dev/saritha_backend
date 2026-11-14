package com.saritha.fashions.modules.receipt.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An item on a receipt (read view). The stored item is a full copy of the invoice item;
 * only the fields a receipt shows are mapped here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "ReceiptItem", description = "An item on the receipt")
public class ReceiptItem {

    @Schema(example = "Blouse_1762935000000_0")
    private String itemUniqueId;

    @Schema(example = "Blouse")
    private String itemName;

    @Schema(example = "2")
    private int itemQuantity;

    @Schema(example = "850.0")
    private double itemPrice;

    @Schema(example = "ORDER_COMPLETED")
    private String itemOrderStatus;
}
