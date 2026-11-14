package com.bnpl.payment.controller;

import com.bnpl.common.crypto.HmacUtils;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.payment.config.PaymentProperties;
import com.bnpl.payment.dto.WebhookRequest;
import com.bnpl.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    private static final String SECRET = "whsec_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c";

    @Mock
    private PaymentService paymentService;

    private WebhookController webhookController;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        PaymentProperties properties = new PaymentProperties(SECRET, "pep_test");
        webhookController = new WebhookController(paymentService, properties, objectMapper);
    }

    @Test
    void shouldAcceptValidHmacSignature() throws Exception {
        UUID intentId = UUID.randomUUID();
        WebhookRequest webhook = new WebhookRequest(intentId, "tx_ext_123", PaymentStatus.SUCCEEDED, Instant.now());
        String payload = objectMapper.writeValueAsString(webhook);
        String signature = HmacUtils.computeHmacHex(payload, SECRET);

        ResponseEntity<String> response = webhookController.handleWebhook(signature, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(paymentService).finalizePaymentIntent(eq(intentId), any());
    }

    @Test
    void shouldRejectInvalidSignature() throws Exception {
        UUID intentId = UUID.randomUUID();
        WebhookRequest webhook = new WebhookRequest(intentId, "tx_ext_123", PaymentStatus.SUCCEEDED, Instant.now());
        String payload = objectMapper.writeValueAsString(webhook);
        String invalidSignature = "invalid_hex_signature";

        ResponseEntity<String> response = webhookController.handleWebhook(invalidSignature, payload);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldRejectMissingSignature() throws Exception {
        ResponseEntity<String> response = webhookController.handleWebhook(null, "{}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
