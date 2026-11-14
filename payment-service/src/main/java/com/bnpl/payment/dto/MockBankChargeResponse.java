package com.bnpl.payment.dto;

public record MockBankChargeResponse(
        boolean success,
        String transactionId,
        String errorCode
) {}
