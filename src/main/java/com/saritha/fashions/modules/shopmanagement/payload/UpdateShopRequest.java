package com.saritha.fashions.modules.shopmanagement.payload;

import com.saritha.fashions.model.Invoice;
import com.saritha.fashions.model.Receipt;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for updating a shop. Null fields are skipped (ModelMapper has
 * setSkipNullEnabled(true)), so only the fields sent are changed.
 * Copy of com.saritha.fashions.payload.shop.ShopUpdateDTO.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "UpdateShopRequest",
        description = "Partial update: send only the fields to change; missing or null fields are left as they are")
public class UpdateShopRequest {

    @Schema(description = "Display name of the shop", example = "Saritha Fashions - T Nagar")
    private String shopName;

    @Schema(description = "Owner name shown on the shop (does not change the owner's user account)",
            example = "Saritha R")
    private String ownerName;

    @Schema(description = "Shop address", example = "45, Usman Road, T Nagar, Chennai")
    private String shopAddress;

    @Schema(description = "Shop contact number", example = "9876543210")
    private String shopContactNumber;

    @Schema(description = "GST registration number", example = "33ABCDE1234F1Z5")
    private String gstNumber;

    @Schema(description = "Type / category of the shop", example = "Boutique")
    private String shopType;

    @Schema(description = "Replaces the shop's embedded invoice list. Normally leave this out.")
    private List<Invoice> allInvoice;

    @Schema(description = "Replaces the shop's receipt references. Normally leave this out.")
    private List<Receipt> allReceipts;
}
