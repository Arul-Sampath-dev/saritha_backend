package com.saritha.fashions.modules.authentication.model;

import com.saritha.fashions.model.AppRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

/**
 * A role embedded in a user (same stored shape as com.saritha.fashions.model.Role:
 * { _id, roleName }). The _id comes from the "roles" collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRole {

    @Id
    private String roleId;

    private AppRole roleName;
}
