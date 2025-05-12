package com.saritha.fashions.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePassword {

    private String password;
    private String repeatPassword;
    private String email;
    private Integer otp;

}
