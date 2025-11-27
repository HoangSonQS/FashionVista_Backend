package com.fashionvista.backend.controller;

import com.fashionvista.backend.dto.address.AddressOptionDto;
import com.fashionvista.backend.service.AddressService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/provinces")
    public List<AddressOptionDto> getProvinces() {
        return addressService.getProvinces();
    }

    @GetMapping("/provinces/{code}/districts")
    public List<AddressOptionDto> getDistricts(@PathVariable String code) {
        return addressService.getDistrictsByProvince(code);
    }

    @GetMapping("/districts/{code}/wards")
    public List<AddressOptionDto> getWards(@PathVariable String code) {
        return addressService.getWardsByDistrict(code);
    }
}


