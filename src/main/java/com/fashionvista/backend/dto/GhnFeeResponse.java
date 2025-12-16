package com.fashionvista.backend.dto;

import lombok.Data;

@Data
public class GhnFeeResponse {
    private Integer code;
    private String message;
    private FeeData data;

    @Data
    public static class FeeData {
        private Integer total;
        private Integer service_fee;
        private Integer insurance_fee;
        private Integer pick_station_fee;
        private Integer coupon_value;
        private Integer r2s_fee;
        private Integer document_return;
        private Integer double_check;
        private Integer cod_fee;
        private Integer pick_remote_areas_fee;
        private Integer deliver_remote_areas_fee;
        private Integer station_do;
        private Integer city_rank;
    }
}

