package com.fashionvista.backend.dto;

import lombok.Value;

@Value
public class ResetPasswordRequest {

    Boolean sendEmail; // true: gửi email, false: trả về password tạm
}

