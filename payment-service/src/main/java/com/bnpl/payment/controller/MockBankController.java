package com.bnpl.payment.controller;

import com.bnpl.payment.dto.MockBankChargeRequest;
import com.bnpl.payment.dto.MockBankChargeResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/mock-bank/v1")
public class MockBankController {

    private static final Logger log = LoggerFactory.getLogger(MockBankController.class);

    @PostMapping("/charge")
    public ResponseEntity<MockBankChargeResponse> charge(@Valid @RequestBody MockBankChargeRequest request) {
        log.info("Mock bank received charge request for mask: {}, amount: {}", request.cardMask(), request.amount());

        // Deterministic failure triggers per spec:
        // 1. Ending in 9999 triggers 3s timeout
        if (request.cardMask().endsWith("9999")) {
            try {
                log.info("Simulating 3s network timeout for card mask ending in 9999");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 2. Ending in 0000 triggers INSUFFICIENT_FUNDS
        if (request.cardMask().endsWith("0000")) {
            log.warn("Declining charge due to INSUFFICIENT_FUNDS for mask: {}", request.cardMask());
            return ResponseEntity.ok(new MockBankChargeResponse(false, null, "INSUFFICIENT_FUNDS"));
        }

        // Standard success
        String txId = "tx_bank_" + UUID.randomUUID();
        log.info("Charge approved by mock bank, txId: {}", txId);
        return ResponseEntity.ok(new MockBankChargeResponse(true, txId, null));
    }
}
