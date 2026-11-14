package com.bnpl.common.events;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderAuthorizedEvent(
        @NotNull UUID orderId,
        @NotNull UUID userId,
        @NotNull String merchantOrderId,
        @NotNull @Positive BigDecimal totalAmount,
        @NotNull String cardToken,
        @NotNull Instant createdAt
) {}
