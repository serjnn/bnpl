package com.bnpl.checkout.dto;

import com.bnpl.common.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        String merchantOrderId,
        UUID userId,
        BigDecimal totalAmount,
        BigDecimal firstShareAmount,
        OrderStatus status,
        String paymentExternalTxId,
        String cardToken,
        Instant createdAt
) {}
