package com.bnpl.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record ChargeRequest(
        @NotNull UUID orderId,
        UUID installmentId,
        @NotNull @Positive BigDecimal amount,
        String pan,
        String cardToken,
        @NotBlank String idempotencyKey
) {
    public ChargeRequest {
        if ((pan == null || pan.isBlank()) && (cardToken == null || cardToken.isBlank())) {
            throw new IllegalArgumentException("Either 'pan' or 'cardToken' must be provided");
        }
    }
}
