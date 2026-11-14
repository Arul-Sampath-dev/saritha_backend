package com.saritha.fashions.payload.items;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Items {
    private String itemUniqueId;
    private String itemName;
    private double itemQuantity;
    private double itemPrice;
    private String imageUrl;
    private String assignmentStatus;
    private String itemOrderStatus;
    private String assignedTo;
    private String assignedToWhichShopAssistant;
    private String ShopAssistantStatus;
    private Map<String, String> measurements;
    private List<AssignmentHistory> assignmentHistory = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AssignmentHistory {
        private String tailorId;
        private String action;
        private Date timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Measurement {
        private String name;
        private String value;
        private String unit;
    }
    private List<Measurement> measurementList;
}
