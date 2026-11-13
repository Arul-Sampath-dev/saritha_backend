package com.saritha.fashions.modules.employeemanagement.payload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * An employee and the shop they work in (shopId / shopName are null when unassigned).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "Employee", description = "An employee account")
public class EmployeeResponse {

    @Schema(example = "665f0a1b2c3d4e5f6a7b8c9d")
    private String userId;
    @Schema(example = "Lakshmi")
    private String name;
    @Schema(example = "lakshmi@example.com")
    private String email;
    @Schema(example = "9876543210")
    private String phone;
    @Schema(example = "18000.0")
    private double salary;
    @Schema(example = "12, 2nd Avenue, Anna Nagar, Chennai")
    private String address;

    @Builder.Default
    @Schema(example = "[\"TAILOR\"]")
    private List<String> roles = new ArrayList<>();

    @Schema(description = "Shop the employee works in; null when unassigned", example = "665f1c2e9b1e8a3d4c7f0a12")
    private String shopId;
    @Schema(example = "Saritha Fashions - Anna Nagar")
    private String shopName;

    private Date accountCreationDate;
    private Date lastLoginDate;
}
