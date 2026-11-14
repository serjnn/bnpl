package com.bnpl.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record MockBankChargeRequest(
        @NotBlank String cardMask,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String referenceId
) {}
