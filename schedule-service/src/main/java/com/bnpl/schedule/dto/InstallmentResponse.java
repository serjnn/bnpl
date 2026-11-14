package com.bnpl.schedule.dto;

import com.bnpl.common.enums.InstallmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InstallmentResponse(
        UUID id,
        int installmentNumber,
        BigDecimal amount,
        LocalDate dueDate,
        InstallmentStatus status,
        Long version
) {}
