package com.saritha.fashions.service.ShopOwnerServices;

import com.saritha.fashions.model.UserBackupHistory;
import com.saritha.fashions.payload.owner.ShopOwnerUpdateRequest;
import java.util.List;

public interface ShopOwnerService {
    String updateShopOwner(ShopOwnerUpdateRequest request, String performedByUsername);
    List<UserBackupHistory> getUserBackupHistory(String userId);
    List<UserBackupHistory> getShopBackupHistory(String shopId);
    List<UserBackupHistory> getBackupsByAssignedShop(String shopId);
}