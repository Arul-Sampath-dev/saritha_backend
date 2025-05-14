package com.saritha.fashions.payload.items;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Items {
    private String itemUniqueId;
    private String itemName;
    private int itemQuantity;
    private double itemPrice;
    private String imageUrl;
    private String itemOrderStatus;
    private Map<String, String> measurements;

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
