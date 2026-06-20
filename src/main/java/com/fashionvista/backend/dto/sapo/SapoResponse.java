package com.fashionvista.backend.dto.sapo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SapoResponse<T> {

    boolean success;
    T data;
    String message;

    public static <T> SapoResponse<T> ok(T data) {
        return SapoResponse.<T>builder().success(true).data(data).message(null).build();
    }

    public static <T> SapoResponse<T> error(String message) {
        return SapoResponse.<T>builder().success(false).data(null).message(message).build();
    }
}
