package com.saritha.fashions.payload.invoice;

import com.saritha.fashions.payload.items.Items;
import lombok.Data;

import java.util.List;

@Data
public class UpdateItemsRequest {
    private String invoiceId;
    private List<Items> items;
}