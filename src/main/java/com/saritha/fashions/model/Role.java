package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@NoArgsConstructor
@AllArgsConstructor
@Data
@Document(collection = "roles")
public class Role {
    @Id
    private String roleId;

    @ToString.Exclude
    private AppRole roleName;

    public Role(AppRole roleName) {
        this.roleName = roleName;
    }
}
