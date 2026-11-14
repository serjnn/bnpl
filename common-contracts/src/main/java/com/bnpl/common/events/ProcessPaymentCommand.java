package com.bnpl.common.events;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentCommand(
        @NotNull UUID paymentIntentId,
        @NotNull UUID orderId,
        @NotNull UUID installmentId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String cardToken,
        @NotBlank String idempotencyKey
) {}
