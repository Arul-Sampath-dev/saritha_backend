package com.saritha.fashions.modules.authentication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * User account as seen by the Authentication module.
 *
 * Maps to the same "users" collection as com.saritha.fashions.model.User with the same
 * field names, so accounts created here work with the JWT filter (UserDetailsServiceImpl)
 * and the rest of the app. Only the fields authentication needs are declared; the shop
 * links (assignedShops DBRefs) are written by AuthUserRepositoryCustomImpl.
 */
@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class AuthUser {

    @Id
    private String userId;

    /** Internal unique username (random UUID, as before); people sign in with their email. */
    private String username;
    private String name;

    /** Always stored lower-case. Unique. */
    private String email;

    /** BCrypt hash. */
    private String password;

    /**
     * Incremented on password reset / sign-out. Every JWT carries the version it was issued
     * with, and the JWT filter rejects tokens with an older version.
     */
    private int tokenVersion;

    private String phone;
    private double salary;
    private String address;

    @Builder.Default
    private List<AuthRole> roles = new ArrayList<>();

    private Date accountCreationDate;
    private Date lastLoginDate;
    private Date lastOnlineDate;
}
