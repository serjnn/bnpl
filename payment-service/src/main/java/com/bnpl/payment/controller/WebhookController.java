package com.bnpl.payment.controller;

import com.bnpl.common.crypto.HmacUtils;
import com.bnpl.payment.config.PaymentProperties;
import com.bnpl.payment.dto.MockBankChargeResponse;
import com.bnpl.payment.dto.WebhookRequest;
import com.bnpl.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    public WebhookController(PaymentService paymentService,
                             PaymentProperties paymentProperties,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "X-Signature-SHA256", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("Received acquiring webhook with signature: {}", signature);

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook rejected: missing X-Signature-SHA256 header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing signature");
        }

        boolean isValid = HmacUtils.verifyHmacHex(rawPayload, paymentProperties.webhookSecret(), signature);
        if (!isValid) {
            log.warn("Webhook rejected: HMAC-SHA256 signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        try {
            WebhookRequest webhook = objectMapper.readValue(rawPayload, WebhookRequest.class);
            boolean isSuccess = webhook.status() == com.bnpl.common.enums.PaymentStatus.SUCCEEDED;
            MockBankChargeResponse bankResponse = new MockBankChargeResponse(
                    isSuccess,
                    webhook.externalTxId(),
                    isSuccess ? null : "WEBHOOK_FAILED"
            );

            paymentService.finalizePaymentIntent(webhook.paymentIntentId(), bankResponse);
            return ResponseEntity.ok("Webhook processed");
        } catch (Exception e) {
            log.error("Failed to process webhook body: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
        }
    }
}
