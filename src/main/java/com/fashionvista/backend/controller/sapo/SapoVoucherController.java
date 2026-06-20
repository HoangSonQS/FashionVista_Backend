package com.fashionvista.backend.controller.sapo;

import com.fashionvista.backend.dto.sapo.SapoPageResponse;
import com.fashionvista.backend.dto.sapo.SapoResponse;
import com.fashionvista.backend.dto.sapo.SapoVoucherDto;
import com.fashionvista.backend.dto.sapo.SapoVoucherRequest;
import com.fashionvista.backend.dto.sapo.SapoVoucherUseRequest;
import com.fashionvista.backend.entity.Voucher;
import com.fashionvista.backend.entity.VoucherType;
import com.fashionvista.backend.exception.SapoDuplicateException;
import com.fashionvista.backend.exception.SapoNotFoundException;
import com.fashionvista.backend.repository.VoucherRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sapo/v1/vouchers")
@RequiredArgsConstructor
@Validated
public class SapoVoucherController {

    private final VoucherRepository voucherRepository;

    @GetMapping
    public SapoPageResponse<SapoVoucherDto> getVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") @Max(200) int size,
            @RequestParam(defaultValue = "true") boolean active) {

        Specification<Voucher> spec = (root, query, cb) ->
            cb.equal(root.get("active"), active);

        Page<SapoVoucherDto> result = voucherRepository
            .findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(this::toDto);
        return SapoPageResponse.of(result);
    }

    @GetMapping("/{code}")
    public SapoResponse<SapoVoucherDto> getVoucher(@PathVariable String code) {
        Voucher v = voucherRepository.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new SapoNotFoundException("Voucher not found: " + code));
        return SapoResponse.ok(toDto(v));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SapoResponse<SapoVoucherDto> createVoucher(
            @RequestBody @Valid SapoVoucherRequest req) {

        if (voucherRepository.findByCodeIgnoreCase(req.getCode()).isPresent()) {
            throw new SapoDuplicateException("Voucher code already exists: " + req.getCode());
        }

        Voucher voucher = Voucher.builder()
            .code(req.getCode().toUpperCase().trim())
            .type(VoucherType.valueOf(req.getType()))
            .value(req.getValue())
            .freeShipping(req.isFreeShipping())
            .minOrderTotal(req.getMinOrderTotal())
            .usageLimit(req.getUsageLimit())
            .usedCount(0)
            .active(true)
            .startsAt(req.getStartsAt())
            .expiresAt(req.getExpiresAt())
            .build();

        return SapoResponse.ok(toDto(voucherRepository.save(voucher)));
    }

    @PutMapping("/{code}/use")
    public SapoResponse<SapoVoucherDto> useVoucher(
            @PathVariable String code,
            @RequestBody(required = false) SapoVoucherUseRequest request) {

        Voucher v = voucherRepository.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new SapoNotFoundException("Voucher not found: " + code));

        if (!v.isActive() || v.isExpired() || v.isNotStartedYet()) {
            throw new SapoDuplicateException("Voucher is not active: " + code);
        }
        if (v.isUsageExceeded()) {
            throw new SapoDuplicateException("Voucher usage limit exceeded: " + code);
        }

        v.setUsedCount(v.getUsedCount() + 1);
        return SapoResponse.ok(toDto(voucherRepository.save(v)));
    }

    private SapoVoucherDto toDto(Voucher v) {
        String unavailableReason = null;
        boolean available = true;

        if (!v.isActive()) {
            available = false;
            unavailableReason = "Voucher is inactive";
        } else if (v.isNotStartedYet()) {
            available = false;
            unavailableReason = "Voucher has not started yet";
        } else if (v.isExpired()) {
            available = false;
            unavailableReason = "Voucher has expired";
        } else if (v.isUsageExceeded()) {
            available = false;
            unavailableReason = "Voucher usage limit exceeded";
        }

        return SapoVoucherDto.builder()
            .id(v.getId())
            .code(v.getCode())
            .type(v.getType().name())
            .value(v.getValue())
            .freeShipping(v.isFreeShipping())
            .minOrderTotal(v.getMinOrderTotal())
            .usageLimit(v.getUsageLimit())
            .usedCount(v.getUsedCount())
            .active(v.isActive())
            .startsAt(v.getStartsAt())
            .expiresAt(v.getExpiresAt())
            .available(available)
            .unavailableReason(unavailableReason)
            .build();
    }
}
