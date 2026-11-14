package com.saritha.fashions.modules.employee.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Employee as seen by the Employee module.
 *
 * Maps to the shared "users" collection (same field names as com.saritha.fashions.model.User).
 * Only the fields employee management needs are declared; the password, tokens and shop
 * links are never read or written through this class.
 */
@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedEmployee {

    @Id
    private String userId;
    private String name;
    private String email;
    private String phone;
    private double salary;
    private String address;

    @Builder.Default
    private List<EmployeeRole> roles = new ArrayList<>();

    private Date accountCreationDate;
    private Date lastLoginDate;
}
