package com.bnpl.payment.controller;

import com.bnpl.payment.dto.MockBankChargeRequest;
import com.bnpl.payment.dto.MockBankChargeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockBankControllerTest {

    private final MockBankController mockBankController = new MockBankController();

    @Test
    void shouldSucceedForNormalCard() {
        MockBankChargeRequest request = new MockBankChargeRequest(
                "411122******4444",
                new BigDecimal("25.00"),
                "ref_123"
        );

        ResponseEntity<MockBankChargeResponse> response = mockBankController.charge(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().transactionId()).startsWith("tx_bank_");
        assertThat(response.getBody().errorCode()).isNull();
    }

    @Test
    void shouldDeclineWithInsufficientFundsWhenEndingIn0000() {
        MockBankChargeRequest request = new MockBankChargeRequest(
                "411122******0000",
                new BigDecimal("50.00"),
                "ref_456"
        );

        ResponseEntity<MockBankChargeResponse> response = mockBankController.charge(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
    }
}
