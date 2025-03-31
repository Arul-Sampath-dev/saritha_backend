package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private String  userId;

    @NonNull
    private String username;
    private String name;
    private String email;
    private String password;
    private String role;
    private String phone;

    private double salary;
    private String address;

    private String accountCreationDate;
    private String lastLoginDate;
    private String lastOnlineDate;

    @Getter
    @Setter
    private Set<Role> roles = new HashSet<>();

    @DBRef(lazy = true)
    private String authToken;

    @DBRef
    @JsonIgnore
    private List<Shop> assignedShops;

    @CreatedDate
    private Date createdAt;

    @LastModifiedDate
    private Date updatedAt;

    public User(@NonNull String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    public User(@NonNull String username, String email, String encode, String name, String phone) {
        this.username = username;
        this.email = email;
        this.password = encode;
        this.name = name;
        this.phone = phone;
    }
}
