package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.AdminVoucherResponse;
import com.fashionvista.backend.dto.VoucherCreateRequest;
import com.fashionvista.backend.dto.VoucherUpdateRequest;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.repository.VoucherRepository;
import com.fashionvista.backend.service.AdminVoucherService;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminVoucherServiceImpl implements AdminVoucherService {

    private final VoucherRepository voucherRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminVoucherResponse> getAllVouchers(String search, Boolean active, Pageable pageable) {
        Specification<Voucher> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("code")), searchPattern)
            );
        }

        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }

        Page<Voucher> vouchers = voucherRepository.findAll(spec, pageable);
        return vouchers.map(this::toAdminVoucherResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminVoucherResponse getVoucherById(Long id) {
        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy voucher với ID: " + id));
        return toAdminVoucherResponse(voucher);
    }

    @Override
    @Transactional
    public AdminVoucherResponse createVoucher(VoucherCreateRequest request) {
        // Kiểm tra code trùng
        if (voucherRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new IllegalArgumentException("Mã voucher đã tồn tại: " + request.getCode());
        }

        // Validation
        if (request.getType() == VoucherType.PERCENT || request.getType() == VoucherType.FIXED_AMOUNT) {
            if (request.getValue() == null || request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Voucher loại " + request.getType() + " cần có giá trị > 0");
            }
            if (request.getType() == VoucherType.PERCENT && request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Voucher loại PERCENT không được vượt quá 100%");
            }
        }

        Voucher voucher = Voucher.builder()
            .code(request.getCode())
            .type(request.getType())
            .value(request.getValue())
            .freeShipping(request.getFreeShipping() != null ? request.getFreeShipping() : false)
            .minOrderTotal(request.getMinOrderTotal())
            .usageLimit(request.getUsageLimit())
            .active(request.getActive() != null ? request.getActive() : true)
            .startsAt(request.getStartsAt())
            .expiresAt(request.getExpiresAt())
            .build();

        voucher = voucherRepository.save(voucher);
        return toAdminVoucherResponse(voucher);
    }

    @Override
    @Transactional
    public AdminVoucherResponse updateVoucher(Long id, VoucherUpdateRequest request) {
        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy voucher với ID: " + id));

        // Update fields
        if (request.getCode() != null && !request.getCode().equals(voucher.getCode())) {
            // Kiểm tra code trùng (trừ chính nó)
            voucherRepository.findByCodeIgnoreCase(request.getCode())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("Mã voucher đã tồn tại: " + request.getCode());
                    }
                });
            voucher.setCode(request.getCode());
        }
        if (request.getType() != null) {
            voucher.setType(request.getType());
        }
        if (request.getValue() != null) {
            voucher.setValue(request.getValue());
        }
        if (request.getFreeShipping() != null) {
            voucher.setFreeShipping(request.getFreeShipping());
        }
        if (request.getMinOrderTotal() != null) {
            voucher.setMinOrderTotal(request.getMinOrderTotal());
        }
        if (request.getUsageLimit() != null) {
            voucher.setUsageLimit(request.getUsageLimit());
        }
        if (request.getActive() != null) {
            voucher.setActive(request.getActive());
        }
        if (request.getStartsAt() != null) {
            voucher.setStartsAt(request.getStartsAt());
        }
        if (request.getExpiresAt() != null) {
            voucher.setExpiresAt(request.getExpiresAt());
        }

        // Validation
        if (voucher.getType() == VoucherType.PERCENT || voucher.getType() == VoucherType.FIXED_AMOUNT) {
            if (voucher.getValue() == null || voucher.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Voucher loại " + voucher.getType() + " cần có giá trị > 0");
            }
            if (voucher.getType() == VoucherType.PERCENT && voucher.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("Voucher loại PERCENT không được vượt quá 100%");
            }
        }

        voucher = voucherRepository.save(voucher);
        return toAdminVoucherResponse(voucher);
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = voucherRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy voucher với ID: " + id));
        voucherRepository.delete(voucher);
    }

    private AdminVoucherResponse toAdminVoucherResponse(Voucher voucher) {
        return AdminVoucherResponse.builder()
            .id(voucher.getId())
            .code(voucher.getCode())
            .type(voucher.getType())
            .value(voucher.getValue())
            .freeShipping(voucher.isFreeShipping())
            .minOrderTotal(voucher.getMinOrderTotal())
            .usageLimit(voucher.getUsageLimit())
            .usedCount(voucher.getUsedCount())
            .active(voucher.isActive())
            .startsAt(voucher.getStartsAt())
            .expiresAt(voucher.getExpiresAt())
            .createdAt(voucher.getCreatedAt())
            .updatedAt(voucher.getUpdatedAt())
            .build();
    }
}

