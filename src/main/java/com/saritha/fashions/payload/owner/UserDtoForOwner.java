package com.saritha.fashions.payload.owner;

import com.saritha.fashions.model.Role;
import com.saritha.fashions.payload.shop.ShopDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDtoForOwner {
    private String  userId;
    private String name;
    private String phone;
    private Date accountCreationDate;
    private List<ShopDTO> assignedShops;
    private Set<Role> roles;
}
