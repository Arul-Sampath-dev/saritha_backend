package com.saritha.fashions.service.Custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WhatsAppVariable {

    private String CustomerName;
    private String amount;
    private String documentId;
    private String downloadLink;

}
