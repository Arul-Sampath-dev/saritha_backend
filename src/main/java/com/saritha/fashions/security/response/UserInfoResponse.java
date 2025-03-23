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
    private List<String> roles;

    @DBRef(lazy = true)
    private Profile profile;

    public UserInfoResponse(String  id, String username, List<String> roles, String jwtToken, String message) {
        this.id = id;
        this.username = username;
        this.roles = roles;
        this.message = message;
    }

    public UserInfoResponse(String  id, String username, List<String> roles) {
        this.id = id;
        this.username = username;
        this.roles = roles;
    }

    public UserInfoResponse(String id, List<String> roles, String loginSuccessful) {

        this.id = id;
        this.roles = roles;
        this.message = loginSuccessful;

    }
}
