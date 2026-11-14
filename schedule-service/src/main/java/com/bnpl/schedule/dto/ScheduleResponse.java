package com.bnpl.schedule.dto;

import com.bnpl.common.enums.ScheduleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScheduleResponse(
        UUID scheduleId,
        UUID orderId,
        UUID userId,
        BigDecimal totalAmount,
        BigDecimal remainingDebt,
        String cardToken,
        ScheduleStatus status,
        Long version,
        Instant createdAt,
        List<InstallmentResponse> installments
) {}
