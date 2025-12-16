package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.ShippingFeeResponse;
import com.fashionvista.backend.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class ShippingController {

    private final ShippingService shippingService;

    @GetMapping("/fee")
    public ShippingFeeResponse getFee(
        @RequestParam Long addressId,
        @RequestParam(defaultValue = "STANDARD") String service
    ) {
        return shippingService.calculateFee(addressId, service);
    }
}

