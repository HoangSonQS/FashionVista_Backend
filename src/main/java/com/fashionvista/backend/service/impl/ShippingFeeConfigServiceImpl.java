package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.ShippingFeeConfigCreateRequest;
import com.fashionvista.backend.dto.ShippingFeeConfigResponse;
import com.fashionvista.backend.dto.ShippingFeeConfigUpdateRequest;
import com.fashionvista.backend.entity.ShippingFeeConfig;
import com.fashionvista.backend.repository.ShippingFeeConfigRepository;
import com.fashionvista.backend.service.ShippingFeeConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingFeeConfigServiceImpl implements ShippingFeeConfigService {

    private final ShippingFeeConfigRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<ShippingFeeConfigResponse> getAll() {
        return repository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShippingFeeConfigResponse getByMethod(String method) {
        ShippingFeeConfig config = repository.findByMethod(method)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình phí vận chuyển cho phương thức: " + method));
        return toResponse(config);
    }

    @Override
    @Transactional
    public ShippingFeeConfigResponse create(ShippingFeeConfigCreateRequest request) {
        if (repository.findByMethod(request.getMethod()).isPresent()) {
            throw new IllegalArgumentException("Phương thức vận chuyển này đã tồn tại: " + request.getMethod());
        }

        ShippingFeeConfig config = ShippingFeeConfig.builder()
            .method(request.getMethod())
            .baseFee(request.getBaseFee())
            .freeShippingThreshold(request.getFreeShippingThreshold())
            .build();

        ShippingFeeConfig saved = repository.save(config);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ShippingFeeConfigResponse update(Long id, ShippingFeeConfigUpdateRequest request) {
        ShippingFeeConfig config = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cấu hình phí vận chuyển với ID: " + id));

        config.setBaseFee(request.getBaseFee());
        config.setFreeShippingThreshold(request.getFreeShippingThreshold());

        ShippingFeeConfig saved = repository.save(config);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy cấu hình phí vận chuyển với ID: " + id);
        }
        repository.deleteById(id);
    }

    private ShippingFeeConfigResponse toResponse(ShippingFeeConfig config) {
        return ShippingFeeConfigResponse.builder()
            .id(config.getId())
            .method(config.getMethod())
            .baseFee(config.getBaseFee())
            .freeShippingThreshold(config.getFreeShippingThreshold())
            .build();
    }
}



