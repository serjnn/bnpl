package com.bnpl.checkout.client;

import com.bnpl.checkout.config.CheckoutProperties;
import com.bnpl.common.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    private final RestClient restClient;
    private final CheckoutProperties properties;

    public record PaymentResult(
            UUID paymentIntentId,
            UUID orderId,
            PaymentStatus status,
            String externalTxId,
            String cardToken,
            String cardMask,
            Instant createdAt
    ) {}

    public PaymentServiceClient(RestClient restClient, CheckoutProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public PaymentResult chargeFirstShare(UUID orderId, BigDecimal amount, String pan, String idempotencyKey) {
        log.info("Calling payment-service to charge 1st share of {} for order {}", amount, orderId);

        Map<String, Object> requestBody = Map.of(
                "orderId", orderId.toString(),
                "amount", amount,
                "pan", pan,
                "idempotencyKey", idempotencyKey
        );

        try {
            return restClient.post()
                    .uri(properties.paymentServiceUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(PaymentResult.class);
        } catch (Exception e) {
            log.error("Failed to charge first share for order {}: {}", orderId, e.getMessage());
            return new PaymentResult(null, orderId, PaymentStatus.FAILED, null, null, null, null);
        }
    }
}
