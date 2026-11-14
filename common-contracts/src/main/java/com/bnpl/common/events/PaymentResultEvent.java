package com.bnpl.common.events;

import com.bnpl.common.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record PaymentResultEvent(
        @NotNull UUID paymentIntentId,
        @NotNull UUID orderId,
        UUID installmentId,
        @NotNull PaymentStatus status,
        String externalTxId,
        String failureReason,
        @NotNull Instant timestamp
) {}
