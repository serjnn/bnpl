package com.bnpl.checkout.controller;

import com.bnpl.checkout.domain.BnplOrderEntity;
import com.bnpl.checkout.dto.CheckoutRequest;
import com.bnpl.checkout.dto.CheckoutResponse;
import com.bnpl.checkout.repository.BnplOrderRepository;
import com.bnpl.checkout.service.CheckoutService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final BnplOrderRepository orderRepository;

    public CheckoutController(CheckoutService checkoutService, BnplOrderRepository orderRepository) {
        this.checkoutService = checkoutService;
        this.orderRepository = orderRepository;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = checkoutService.checkout(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CheckoutResponse> getOrder(@PathVariable UUID id) {
        return orderRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private CheckoutResponse toResponse(BnplOrderEntity entity) {
        BigDecimal firstShare = entity.getTotalAmount().multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
        return new CheckoutResponse(
                entity.getId(),
                entity.getMerchantOrderId(),
                entity.getUserId(),
                entity.getTotalAmount(),
                firstShare,
                entity.getStatus(),
                null,
                null,
                entity.getCreatedAt()
        );
    }
}
