package com.fashionvista.backend.service.impl;

import com.fashionvista.backend.dto.VoucherDiscountResult;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.repository.VoucherRepository;
import com.fashionvista.backend.service.VoucherService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;

    @Override
    @Transactional
    public VoucherDiscountResult validateAndCalculateDiscount(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return VoucherDiscountResult.builder()
                .discount(BigDecimal.ZERO)
                .freeShipping(false)
                .build();
        }
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tổng tiền đơn hàng không hợp lệ để áp dụng voucher.");
        }

        Voucher voucher = voucherRepository.findByCodeIgnoreCase(code.trim())
            .orElseThrow(() -> new IllegalArgumentException("Mã voucher không tồn tại."));

        if (!voucher.isActive()) {
            throw new IllegalArgumentException("Mã voucher đã bị vô hiệu hóa.");
        }
        if (voucher.isNotStartedYet()) {
            throw new IllegalArgumentException("Mã voucher chưa bắt đầu hiệu lực.");
        }
        if (voucher.isExpired()) {
            throw new IllegalArgumentException("Mã voucher đã hết hạn.");
        }
        if (voucher.isUsageExceeded()) {
            throw new IllegalArgumentException("Mã voucher đã được sử dụng hết số lần cho phép.");
        }
        if (voucher.getMinOrderTotal() != null
            && subtotal.compareTo(voucher.getMinOrderTotal()) < 0) {
            throw new IllegalArgumentException("Đơn hàng chưa đạt giá trị tối thiểu để áp dụng voucher.");
        }

        BigDecimal discount = calculateDiscountAmount(voucher, subtotal);
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        // KHÔNG tăng usedCount ở đây - chỉ validate và tính discount
        // usedCount sẽ được tăng khi đơn hàng được tạo thành công

        return VoucherDiscountResult.builder()
            .discount(discount)
            .freeShipping(voucher.isFreeShipping())
            .build();
    }

    @Override
    @Transactional
    public void applyVoucher(String code) {
        if (code == null || code.isBlank()) {
            return;
        }

        Voucher voucher = voucherRepository.findByCodeIgnoreCase(code.trim())
            .orElseThrow(() -> new IllegalArgumentException("Mã voucher không tồn tại."));

        // Tăng usedCount khi đơn hàng được tạo thành công
        voucher.setUsedCount(voucher.getUsedCount() + 1);
        voucherRepository.save(voucher);
    }

    private BigDecimal calculateDiscountAmount(Voucher voucher, BigDecimal subtotal) {
        VoucherType type = voucher.getType();
        if (type == VoucherType.FREESHIP) {
            // Phần freeship sẽ được xử lý ở tầng khác (tính phí ship),
            // ở đây chỉ trả về 0 cho phần giảm trên subtotal.
            return BigDecimal.ZERO;
        }

        BigDecimal value = voucher.getValue() != null ? voucher.getValue() : BigDecimal.ZERO;
        return switch (type) {
            case PERCENT -> {
                if (value.compareTo(BigDecimal.ZERO) <= 0) {
                    yield BigDecimal.ZERO;
                }
                BigDecimal percent = value.min(BigDecimal.valueOf(100));
                yield subtotal.multiply(percent)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            }
            case FIXED_AMOUNT -> value.max(BigDecimal.ZERO);
            default -> BigDecimal.ZERO;
        };
    }
}


