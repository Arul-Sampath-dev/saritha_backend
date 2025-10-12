package com.saritha.fashions.repository.AggregationInterface;

import com.saritha.fashions.model.Role;

import java.util.List;
import java.util.Set;

public interface UserWithShopProjection {
    String getUserId();
    String getPassword();
    String getName();
    String getEmail();
    String getShopName();
}
