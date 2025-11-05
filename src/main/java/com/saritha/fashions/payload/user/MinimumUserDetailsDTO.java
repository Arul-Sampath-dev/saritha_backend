package com.saritha.fashions.payload.user;

import com.saritha.fashions.model.User;
import com.saritha.fashions.payload.shop.ShopDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.services.ses.endpoints.internal.Value;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MinimumUserDetailsDTO {
    private String  userId;
    private String name;
    private Date accountCreationDate;
    private List<ShopDTO> assignedShops;

    public MinimumUserDetailsDTO(String id, String name) {
        this.userId = id;
        this.name = name;
    }

    public MinimumUserDetailsDTO(String userId, String name, Date accountCreationDate) {
        this.userId = userId;
        this.name = name;
        this.accountCreationDate = accountCreationDate;
    }
}
