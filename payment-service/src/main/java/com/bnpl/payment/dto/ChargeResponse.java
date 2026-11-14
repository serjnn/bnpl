package com.bnpl.payment.dto;

import com.bnpl.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargeResponse(
        UUID paymentIntentId,
        UUID orderId,
        UUID installmentId,
        BigDecimal amount,
        PaymentStatus status,
        String externalTxId,
        String cardMask,
        String cardToken,
        Instant createdAt
) {}
