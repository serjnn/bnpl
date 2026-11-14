package com.bnpl.payment.service;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.crypto.CardUtils;
import com.bnpl.common.enums.OutboxStatus;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.common.events.PaymentResultEvent;
import com.bnpl.common.events.PaymentSettledEvent;
import com.bnpl.payment.client.AcquiringBankClient;
import com.bnpl.payment.config.PaymentProperties;
import com.bnpl.payment.domain.OutboxMessageEntity;
import com.bnpl.payment.domain.PaymentIntentEntity;
import com.bnpl.payment.dto.ChargeRequest;
import com.bnpl.payment.dto.ChargeResponse;
import com.bnpl.payment.dto.MockBankChargeRequest;
import com.bnpl.payment.dto.MockBankChargeResponse;
import com.bnpl.payment.repository.OutboxMessageRepository;
import com.bnpl.payment.repository.PaymentIntentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentIntentRepository paymentIntentRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final AcquiringBankClient acquiringBankClient;
    private final IdempotencyService idempotencyService;
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentIntentRepository paymentIntentRepository,
                          OutboxMessageRepository outboxMessageRepository,
                          AcquiringBankClient acquiringBankClient,
                          IdempotencyService idempotencyService,
                          PaymentProperties paymentProperties,
                          ObjectMapper objectMapper) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.acquiringBankClient = acquiringBankClient;
        this.idempotencyService = idempotencyService;
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the charge flow with strict transaction isolation:
     * 1. Check idempotency.
     * 2. DB Tx 1: Save initial PaymentIntent (status = PROCESSING).
     * 3. Network Call OUTSIDE Tx: Call acquiring bank.
     * 4. DB Tx 2: Finalize PaymentIntent and write Outbox event.
     */
    public ChargeResponse processCharge(ChargeRequest request) {
        String idempotencyKey = request.idempotencyKey();

        // 1. Check Redis cache first
        Optional<ChargeResponse> cachedResponse = idempotencyService.getCachedResponse(idempotencyKey);
        if (cachedResponse.isPresent()) {
            log.info("Returning cached response for idempotency key: {}", idempotencyKey);
            return cachedResponse.get();
        }

        // 2. Check DB for previously completed payment intent
        Optional<PaymentIntentEntity> existingIntent = paymentIntentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingIntent.isPresent()) {
            PaymentIntentEntity intent = existingIntent.get();
            ChargeResponse response = toChargeResponse(intent);
            idempotencyService.saveResponse(idempotencyKey, response);
            return response;
        }

        // 3. Acquire Redis lock
        boolean lockAcquired = idempotencyService.acquireLock(idempotencyKey);
        if (!lockAcquired) {
            throw new IllegalStateException("Payment already in progress for idempotency key: " + idempotencyKey);
        }

        try {
            // PCI DSS SAQ-A compliance: derive mask, token, and blind hash; never store raw PAN
            String cardMask;
            String cardToken;
            String cardBlindHash;

            if (request.pan() != null && !request.pan().isBlank()) {
                cardMask = CardUtils.maskCard(request.pan());
                cardToken = (request.cardToken() != null && !request.cardToken().isBlank())
                        ? request.cardToken()
                        : CardUtils.generateCardToken();
                cardBlindHash = CardUtils.calculateBlindHash(request.pan(), paymentProperties.blindIndexPepper());
            } else {
                cardToken = request.cardToken();
                cardMask = "tok_" + cardToken.substring(Math.max(0, cardToken.length() - 4));
                cardBlindHash = "token_" + cardToken;
            }

            UUID intentId = UUID.randomUUID();

            // STEP 1 (Short Transaction): Persist intent
            createInitialPaymentIntent(intentId, request, cardMask, cardToken, cardBlindHash);

            // STEP 2 (STRICTLY OUTSIDE TRANSACTION): Call Acquiring Bank
            MockBankChargeRequest bankRequest = new MockBankChargeRequest(cardMask, request.amount(), intentId.toString());
            MockBankChargeResponse bankResponse = acquiringBankClient.charge(bankRequest);

            // STEP 3 (Short Transaction): Finalize intent & write outbox event
            PaymentIntentEntity finalizedEntity = finalizePaymentIntent(intentId, bankResponse);

            ChargeResponse chargeResponse = toChargeResponse(finalizedEntity);
            idempotencyService.saveResponse(idempotencyKey, chargeResponse);
            return chargeResponse;

        } catch (Exception e) {
            idempotencyService.releaseLock(idempotencyKey);
            throw e;
        }
    }

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

    private ChargeResponse toChargeResponse(PaymentIntentEntity entity) {
        return new ChargeResponse(
                entity.getId(),
                entity.getOrderId(),
                entity.getInstallmentId(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getExternalTxId(),
                entity.getCardMask(),
                entity.getCardToken(),
                entity.getCreatedAt()
        );
    }
}
