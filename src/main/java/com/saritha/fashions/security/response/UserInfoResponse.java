package com.saritha.fashions.security.response;

import com.saritha.fashions.model.Profile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.DBRef;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfoResponse {
    private String  id;
    private String username;
    private String message;
    private String shopName;
    private String shopId;
    private List<String> roles;

//    @DBRef(lazy = true)
//    private Profile profile;

    public UserInfoResponse(String id, List<String> roles, String loginSuccessful, String shopName, String username) {

        this.id = id;
        this.roles = roles;
        this.message = loginSuccessful;
        this.shopName = shopName;
        this.username = username;
    }

    public UserInfoResponse(String id, List<String> roles, String loginSuccessful, String shopName, String shopId, String username) {
        this.id = id;
        this.roles = roles;
        this.message = loginSuccessful;
        this.shopName = shopName;
        this.shopId=shopId;
        this.username = username;
    }
}
