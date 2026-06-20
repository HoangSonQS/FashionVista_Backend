package com.fashionvista.backend.controller.sapo;

import com.fashionvista.backend.dto.sapo.SapoOrderCustomerDto;
import com.fashionvista.backend.dto.sapo.SapoOrderDto;
import com.fashionvista.backend.dto.sapo.SapoOrderItemDto;
import com.fashionvista.backend.dto.sapo.SapoOrderRequest;
import com.fashionvista.backend.dto.sapo.SapoOrderStatusRequest;
import com.fashionvista.backend.dto.sapo.SapoPageResponse;
import com.fashionvista.backend.dto.sapo.SapoResponse;
import com.fashionvista.backend.entity.Order;
import com.fashionvista.backend.entity.OrderItem;
import com.fashionvista.backend.entity.OrderStatus;
import com.fashionvista.backend.entity.PaymentMethod;
import com.fashionvista.backend.entity.PaymentStatus;
import com.fashionvista.backend.entity.ProductVariant;
import com.fashionvista.backend.entity.User;
import com.fashionvista.backend.exception.SapoNotFoundException;
import com.fashionvista.backend.repository.OrderRepository;
import com.fashionvista.backend.repository.ProductVariantRepository;
import com.fashionvista.backend.repository.UserRepository;
import com.fashionvista.backend.service.VoucherService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/sapo/v1/orders")
@RequiredArgsConstructor
@Validated
public class SapoOrderController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VoucherService voucherService;

    @GetMapping
    public SapoPageResponse<SapoOrderDto> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdAfter,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdBefore) {

        Specification<Order> spec = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"),
                    OrderStatus.valueOf(status.toUpperCase())));
            }
            if (createdAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            }
            if (createdBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SapoOrderDto> result = orderRepository
            .findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(this::toDto);
        return SapoPageResponse.of(result);
    }

    @GetMapping("/{orderNumber}")
    public SapoResponse<SapoOrderDto> getOrder(@PathVariable String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new SapoNotFoundException("Order not found: " + orderNumber));
        return SapoResponse.ok(toDto(order));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SapoResponse<SapoOrderDto> createOrder(@RequestBody @Valid SapoOrderRequest req) {
        User customer = userRepository.findById(req.getCustomerId())
            .orElseThrow(() -> new SapoNotFoundException(
                "Customer not found: " + req.getCustomerId()));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (var itemReq : req.getItems()) {
            ProductVariant variant = productVariantRepository.findBySku(itemReq.getSku())
                .orElseThrow(() -> new SapoNotFoundException("SKU not found: " + itemReq.getSku()));

            int updated = productVariantRepository.decreaseStockIfEnough(
                variant.getId(), itemReq.getQuantity());
            if (updated == 0) {
                throw new IllegalArgumentException(
                    "Insufficient stock for SKU: " + itemReq.getSku());
            }

            BigDecimal lineTotal = itemReq.getUnitPrice()
                .multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            orderItems.add(OrderItem.builder()
                .product(variant.getProduct())
                .variant(variant)
                .productName(variant.getProduct().getName())
                .price(itemReq.getUnitPrice())
                .quantity(itemReq.getQuantity())
                .subtotal(lineTotal)
                .build());
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (req.getVoucherCode() != null && !req.getVoucherCode().isBlank()) {
            var discountResult = voucherService.validateAndCalculateDiscount(
                req.getVoucherCode(), subtotal);
            discount = discountResult.getDiscount();
            voucherService.applyVoucher(req.getVoucherCode());
        }

        BigDecimal shippingFee = req.getShippingFee() != null
            ? req.getShippingFee() : BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discount).add(shippingFee);

        String orderNumber = "ORD-"
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = Order.builder()
            .orderNumber(orderNumber)
            .user(customer)
            .status(OrderStatus.CONFIRMED)
            .paymentMethod(PaymentMethod.valueOf(req.getPaymentMethod()))
            .paymentStatus(PaymentStatus.PAID)
            .shippingMethod("POS")
            .shippingAddress("{\"type\":\"POS\",\"note\":\"Bán tại quầy Sapo\"}")
            .subtotal(subtotal)
            .shippingFee(shippingFee)
            .discount(discount)
            .voucherCode(req.getVoucherCode())
            .total(total)
            .source("SAPO_POS")
            .notes(req.getNote())
            .items(orderItems)
            .build();

        orderItems.forEach(item -> item.setOrder(order));

        Order saved = orderRepository.save(order);
        return SapoResponse.ok(toDto(saved));
    }

    @PutMapping("/{orderNumber}/status")
    @Transactional
    public SapoResponse<SapoOrderDto> updateStatus(
            @PathVariable String orderNumber,
            @RequestBody @Valid SapoOrderStatusRequest req) {

        Order order = orderRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new SapoNotFoundException("Order not found: " + orderNumber));

        order.setStatus(OrderStatus.valueOf(req.getStatus().toUpperCase()));
        if (req.getNote() != null && !req.getNote().isBlank()) {
            order.setNotes(req.getNote());
        }

        return SapoResponse.ok(toDto(orderRepository.save(order)));
    }

    private SapoOrderDto toDto(Order o) {
        List<SapoOrderItemDto> items = o.getItems().stream()
            .map(item -> SapoOrderItemDto.builder()
                .sku(item.getVariant() != null ? item.getVariant().getSku() : null)
                .productName(item.getProductName())
                .variantLabel(item.getVariant() != null
                    ? item.getVariant().getSize() + " / " + item.getVariant().getColor()
                    : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getPrice())
                .subtotal(item.getSubtotal())
                .build())
            .toList();

        User u = o.getUser();
        SapoOrderCustomerDto customer = SapoOrderCustomerDto.builder()
            .id(u.getId())
            .fullName(u.getFullName())
            .email(u.getEmail())
            .phoneNumber(u.getPhoneNumber())
            .build();

        return SapoOrderDto.builder()
            .orderNumber(o.getOrderNumber())
            .status(o.getStatus().name())
            .paymentMethod(o.getPaymentMethod().name())
            .paymentStatus(o.getPaymentStatus().name())
            .transactionId(o.getPayment() != null ? o.getPayment().getTransactionId() : null)
            .customer(customer)
            .shippingAddress(o.getShippingAddress())
            .items(items)
            .subtotal(o.getSubtotal())
            .shippingFee(o.getShippingFee())
            .discount(o.getDiscount())
            .voucherCode(o.getVoucherCode())
            .total(o.getTotal())
            .trackingNumber(o.getTrackingNumber())
            .source(o.getSource())
            .createdAt(o.getCreatedAt())
            .updatedAt(o.getUpdatedAt())
            .build();
    }
}
