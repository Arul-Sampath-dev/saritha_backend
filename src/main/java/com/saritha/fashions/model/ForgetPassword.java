package com.saritha.fashions.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document
@Builder
public class ForgetPassword {

    @Id
    private String fpid;

    @NonNull
    private Integer otp;

    @NonNull
    private Date expirationTime;
    private String user;
}
