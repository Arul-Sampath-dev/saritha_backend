package com.saritha.fashions.payload.owner;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class ShopOwnerUpdateRequest {
    @NotNull(message = "Shop ID is required")
    private String shopId;

    private String backupReason = "Shop Owner Details Update";

    private String oldOwnerUserId;

    private String newOwnerName;
    private String newOwnerPhone;
    private String newOwnerEmail;
    private String newOwnerPassword;
    private Double newOwnerSalary;
}