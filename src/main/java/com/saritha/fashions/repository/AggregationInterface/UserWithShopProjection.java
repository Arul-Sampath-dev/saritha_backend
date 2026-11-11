package com.saritha.fashions.repository.AggregationInterface;

import com.saritha.fashions.model.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/** Concrete result type for the sign-in aggregation, including embedded role documents. */
@Data
@NoArgsConstructor
public class UserWithShopProjection {
    private String userId;
    private String password;
    private String name;
    private String email;
    private Set<Role> roles = new HashSet<>();
    private int tokenVersion;
    private String shopName;
    private String shopId;
}
