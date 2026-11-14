package com.bnpl.payment.service;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.enums.OutboxStatus;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.common.events.PaymentResultEvent;
import com.bnpl.common.events.PaymentSettledEvent;
import com.bnpl.payment.domain.OutboxMessageEntity;
import com.bnpl.payment.domain.PaymentIntentEntity;
import com.bnpl.payment.dto.ChargeRequest;
import com.bnpl.payment.dto.MockBankChargeResponse;
import com.bnpl.payment.repository.OutboxMessageRepository;
import com.bnpl.payment.repository.PaymentIntentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentTransactionService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public PaymentTransactionService(PaymentIntentRepository paymentIntentRepository,
                                   OutboxMessageRepository outboxMessageRepository,
                                   ObjectMapper objectMapper) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists initial payment intent in PROCESSING state within a short database transaction.
     */
    @Transactional
    public PaymentIntentEntity createInitialPaymentIntent(UUID intentId,
                                                          ChargeRequest request,
                                                          String cardMask,
                                                          String cardToken,
                                                          String cardBlindHash) {
        PaymentIntentEntity entity = new PaymentIntentEntity(
                intentId,
                request.idempotencyKey(),
                request.orderId(),
                request.installmentId(),
                request.amount(),
                cardMask,
                cardToken,
                cardBlindHash,
                PaymentStatus.PROCESSING,
                Instant.now()
        );
        return paymentIntentRepository.save(entity);
    }

    /**
     * Finalizes payment intent (SUCCEEDED or FAILED) and writes transactional outbox message
     * atomically in a single database transaction.
     */
    @Transactional
    public PaymentIntentEntity finalizePaymentIntent(UUID intentId, MockBankChargeResponse bankResponse) {
        PaymentIntentEntity intent = paymentIntentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalStateException("Payment intent not found: " + intentId));

        if (bankResponse.success()) {
            intent.markSucceeded(bankResponse.transactionId());

            // Write PaymentSettledEvent to transactional outbox
            PaymentSettledEvent event = new PaymentSettledEvent(
                    intent.getId(),
                    intent.getOrderId(),
                    intent.getInstallmentId(),
                    intent.getAmount(),
                    bankResponse.transactionId(),
                    Instant.now()
            );

            saveOutboxMessage("PaymentIntent", intent.getId(), KafkaTopics.PAYMENTS_RESULTS, event);
        } else {
            intent.markFailed(bankResponse.errorCode());

            // Write PaymentResultEvent (failed) to transactional outbox
            PaymentResultEvent failureEvent = new PaymentResultEvent(
                    intent.getId(),
                    intent.getOrderId(),
                    intent.getInstallmentId(),
                    PaymentStatus.FAILED,
                    null,
                    bankResponse.errorCode(),
                    Instant.now()
            );

            saveOutboxMessage("PaymentIntent", intent.getId(), KafkaTopics.PAYMENTS_RESULTS, failureEvent);
        }

        return paymentIntentRepository.save(intent);
    }

    private void saveOutboxMessage(String aggregateType, UUID aggregateId, String topic, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxMessageEntity outbox = new OutboxMessageEntity(
                    UUID.randomUUID(),
                    aggregateType,
                    aggregateId,
                    topic,
                    payloadJson,
                    OutboxStatus.PENDING,
                    Instant.now()
            );
            outboxMessageRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox message", e);
        }
    }
}
