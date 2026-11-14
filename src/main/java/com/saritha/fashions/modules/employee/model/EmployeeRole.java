package com.saritha.fashions.modules.employee.model;

import com.saritha.fashions.model.AppRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.EnumSet;
import java.util.Set;

/**
 * A role embedded in a user ({ _id, roleName }), read-only here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRole {

    /** Roles a shop owner may manage. SHOP_OWNER and OWNER accounts are managed by the platform owner. */
    public static final Set<AppRole> STAFF_ROLES =
            EnumSet.of(AppRole.TAILOR_MASTER, AppRole.TAILOR, AppRole.SHOP_ASSISTANT, AppRole.ACCOUNTANT);

    @Id
    private String roleId;

    private AppRole roleName;
}
