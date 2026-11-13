package com.saritha.fashions.modules.shopmanagement.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * One petty cash top-up, stored inside the shop document (shops.allPettyCashDetails).
 *
 * Field names match com.saritha.fashions.payload.shop.PettyCashDTO, so entries written
 * here and entries written by the old ShopServiceImpl are stored in the same format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "PettyCashEntry", description = "A single petty cash top-up recorded on a shop")
public class PettyCashEntry {

    @Schema(description = "When the cash was added (stored as UTC, created from IST now)",
            example = "2026-11-12T05:30:00.000+00:00")
    private Date addingDate;

    @Schema(description = "Amount added", example = "5000.0")
    private double amount;

    @Schema(description = "User id of the person who added the cash", example = "665f0a1b2c3d4e5f6a7b8c9d")
    private String addedBy;
}
