package com.saritha.fashions.security.request;


import com.saritha.fashions.model.Shop;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignupRequest {

    private String name;
    private String phone;
    private String username;
    private String email;
    private String role;
    private Set<String> roles;
    private String password;
    private double salary;
    private String address;

    private String assignedShops;

}
