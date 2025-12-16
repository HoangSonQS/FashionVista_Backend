package com.fashionvista.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GhnFeeRequest {
    private Integer service_id;
    private Integer service_type_id;
    private String from_district_id;
    private String to_district_id;
    private String to_ward_code;
    private Integer height;
    private Integer length;
    private Integer weight;
    private Integer width;
    private Integer insurance_value;
    private Integer coupon;
}

