package com.saritha.fashions.payload.invoice;

import com.saritha.fashions.payload.items.Items;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class UpdateItemsRequest {
    private String invoiceId;
    private List<Items> items;
    private List<MultipartFile> imageFiles;
    private List<String> imageFileNames;
}