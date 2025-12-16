package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.config.GhnConfig;
import com.fashionvista.backend.dto.GhnFeeRequest;
import com.fashionvista.backend.dto.GhnFeeResponse;
import com.fashionvista.backend.dto.ShippingFeeResponse;
import com.fashionvista.backend.entity.Address;
import com.fashionvista.backend.repository.AddressRepository;
import com.fashionvista.backend.service.ShippingService;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ShippingServiceImpl implements ShippingService {

    private final GhnConfig ghnConfig;
    private final AddressRepository addressRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ShippingFeeResponse calculateFee(Long addressId, String service) {
        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ giao hàng."));

        if (ghnConfig.getToken() == null || ghnConfig.getToken().isBlank()
            || ghnConfig.getShopId() == null || ghnConfig.getShopId().isBlank()) {
            // Fallback: nếu chưa cấu hình GHN, trả phí cố định
            return ShippingFeeResponse.builder()
                .fee(BigDecimal.valueOf(30000))
                .currency("VND")
                .provider("GHN")
                .service(service)
                .note("GHN token/shopId chưa được cấu hình, dùng phí tạm.")
                .build();
        }

        // GHN cần mã quận huyện/ward code; giả định address.ward lưu code, district lưu code
        GhnFeeRequest request = GhnFeeRequest.builder()
            .service_id(resolveServiceId(service))
            .to_district_id(address.getDistrict())
            .to_ward_code(address.getWard())
            .weight(500) // giả định 0.5kg
            .length(20)
            .width(15)
            .height(5)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Token", ghnConfig.getToken());
        headers.set("ShopId", ghnConfig.getShopId());

        HttpEntity<GhnFeeRequest> entity = new HttpEntity<>(request, headers);

        String url = ghnConfig.getBaseUrl() + "/shiip/public-api/v2/shipping-order/fee";
        GhnFeeResponse response = restTemplate.exchange(url, HttpMethod.POST, entity, GhnFeeResponse.class).getBody();

        if (response == null || response.getData() == null || response.getData().getTotal() == null) {
            throw new IllegalArgumentException("Không tính được phí vận chuyển từ GHN.");
        }

        return ShippingFeeResponse.builder()
            .fee(BigDecimal.valueOf(response.getData().getTotal()))
            .currency("VND")
            .provider("GHN")
            .service(service)
            .note("Fee from GHN API")
            .build();
    }

    private Integer resolveServiceId(String service) {
        if (service == null) return null;
        String key = service.toUpperCase();
        Map<String, Integer> map = Map.of(
            "STANDARD", 53321, // placeholder service_id
            "FAST", 53321,
            "EXPRESS", 53321
        );
        return map.getOrDefault(key, 53321);
    }
}

