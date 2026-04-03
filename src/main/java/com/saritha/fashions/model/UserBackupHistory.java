package com.saritha.fashions.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;
import java.util.List;

@Document(collection = "user_backup_history")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBackupHistory {
    @Id
    private String id;
    private String originalUserId;
    private User userData;
    private String backupReason;
    private String performedBy;
    private Date backupDate;
    private String shopId; // The shop for which owner is being changed
    private List<String> assignedShopIds; // All shops assigned to user at backup time
    private List<ShopInfo> assignedShopsInfo; // Detailed shop information

    public UserBackupHistory(String originalUserId, User userData, String backupReason,
                             String performedBy, String shopId, List<String> assignedShopIds,
                             List<ShopInfo> assignedShopsInfo) {
        this.originalUserId = originalUserId;
        this.userData = userData;
        this.backupReason = backupReason;
        this.performedBy = performedBy;
        this.shopId = shopId;
        this.assignedShopIds = assignedShopIds;
        this.assignedShopsInfo = assignedShopsInfo;
        this.backupDate = new Date();
    }

    // Inner class to store basic shop information
    @Data
    public static class ShopInfo {
        private String shopId;
        private String shopName;
        private String shopType;
        private Date assignedDate;

        public ShopInfo(String shopId, String shopName, String shopType) {
            this.shopId = shopId;
            this.shopName = shopName;
            this.shopType = shopType;
            this.assignedDate = new Date();
        }

        public ShopInfo(Shop shop) {
            this.shopId = shop.getId();
            this.shopName = shop.getShopName();
            this.shopType = shop.getShopType();
            this.assignedDate = new Date();
        }
    }
}