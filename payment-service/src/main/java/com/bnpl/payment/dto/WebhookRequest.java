package com.bnpl.payment.dto;

import com.bnpl.common.enums.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record WebhookRequest(
        @NotNull UUID paymentIntentId,
        @NotBlank String externalTxId,
        @NotNull PaymentStatus status,
        @NotNull Instant timestamp
) {}
