package com.fashionvista.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashionvista.backend.dto.address.AddressOptionDto;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AddressService {

    private final List<ProvinceData> provinces;
    private final Map<String, ProvinceData> provinceByCode = new HashMap<>();
    private final Map<String, DistrictData> districtByCode = new HashMap<>();

    public AddressService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.provinces = loadAddressData(resourceLoader, objectMapper);
        indexData();
    }

    private List<ProvinceData> loadAddressData(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        Resource resource = resourceLoader.getResource("classpath:static/data/full_json_generated_data_vn_units.json");
        try (InputStream inputStream = resource.getInputStream()) {
            ProvinceData[] data = objectMapper.readValue(inputStream, ProvinceData[].class);
            return List.of(data);
        } catch (IOException e) {
            log.error("Không thể tải dữ liệu địa chỉ Việt Nam", e);
            throw new IllegalStateException("Không thể tải dữ liệu địa chỉ Việt Nam", e);
        }
    }

    private void indexData() {
        for (ProvinceData province : provinces) {
            provinceByCode.put(province.getCode(), province);
            if (province.getDistricts() == null) {
                continue;
            }
            for (DistrictData district : province.getDistricts()) {
                district.setProvince(province);
                districtByCode.put(district.getCode(), district);
            }
        }
    }

    public List<AddressOptionDto> getProvinces() {
        return provinces.stream()
            .map(AddressService::toOption)
            .toList();
    }

    public List<AddressOptionDto> getDistrictsByProvince(String provinceCode) {
        ProvinceData province = provinceByCode.get(provinceCode);
        if (province == null) {
            throw new IllegalArgumentException("Không tìm thấy tỉnh/thành");
        }
        if (province.getDistricts() == null) {
            return Collections.emptyList();
        }
        return province.getDistricts().stream()
            .map(AddressService::toOption)
            .toList();
    }

    public List<AddressOptionDto> getWardsByDistrict(String districtCode) {
        DistrictData district = districtByCode.get(districtCode);
        if (district == null) {
            throw new IllegalArgumentException("Không tìm thấy quận/huyện");
        }
        if (district.getWards() == null) {
            return Collections.emptyList();
        }
        return district.getWards().stream()
            .map(AddressService::toOption)
            .toList();
    }

    private static AddressOptionDto toOption(BaseAdministrativeUnit unit) {
        return AddressOptionDto.builder()
            .code(unit.getCode())
            .name(unit.getName())
            .fullName(unit.getFullName())
            .codeName(unit.getCodeName())
            .build();
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static abstract class BaseAdministrativeUnit {
        @JsonProperty("Code")
        private String code;

        @JsonProperty("Name")
        private String name;

        @JsonProperty("FullName")
        private String fullName;

        @JsonProperty("CodeName")
        private String codeName;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ProvinceData extends BaseAdministrativeUnit {

        @JsonProperty("District")
        private List<DistrictData> districts = new ArrayList<>();
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DistrictData extends BaseAdministrativeUnit {

        @JsonProperty("Ward")
        private List<WardData> wards = new ArrayList<>();

        private ProvinceData province;

        public void setProvince(ProvinceData province) {
            this.province = province;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WardData extends BaseAdministrativeUnit { }
}


