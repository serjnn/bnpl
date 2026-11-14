package com.bnpl.checkout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID userId,
        @NotBlank String merchantOrderId,
        @NotNull @Positive BigDecimal totalAmount,
        @NotBlank String pan
) {}
