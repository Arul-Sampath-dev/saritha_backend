package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Document(collection = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndex(name = "email_index", def = "{'email': 1}", unique = true)
public class User {
    @Id
    private String  userId;

    @NonNull
    @Indexed
    private String username;
    private String name;

    @Indexed
    private String email;
    private String password;
    private String role;
    private String phone;

    private double salary;
    private String address;

    private Date accountCreationDate;
    private Date lastLoginDate;
    private Date lastOnlineDate;

    @DBRef
    private List<Expense> allExpenses = new ArrayList<Expense>();

    @Getter
    @Setter
    private Set<Role> roles = new HashSet<>();

    @DBRef(lazy = true)
    private String authToken;

    @DBRef(lazy = true)
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
