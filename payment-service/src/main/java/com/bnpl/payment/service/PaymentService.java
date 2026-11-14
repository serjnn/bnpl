package com.bnpl.payment.service;

import com.bnpl.common.crypto.CardUtils;
import com.bnpl.payment.client.AcquiringBankClient;
import com.bnpl.payment.config.PaymentProperties;
import com.bnpl.payment.domain.PaymentIntentEntity;
import com.bnpl.payment.dto.ChargeRequest;
import com.bnpl.payment.dto.ChargeResponse;
import com.bnpl.payment.dto.MockBankChargeRequest;
import com.bnpl.payment.dto.MockBankChargeResponse;
import com.bnpl.payment.repository.PaymentIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentTransactionService transactionService;
    private final AcquiringBankClient acquiringBankClient;
    private final IdempotencyService idempotencyService;
    private final PaymentProperties paymentProperties;

    public PaymentService(PaymentIntentRepository paymentIntentRepository,
                          PaymentTransactionService transactionService,
                          AcquiringBankClient acquiringBankClient,
                          IdempotencyService idempotencyService,
                          PaymentProperties paymentProperties) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.transactionService = transactionService;
        this.acquiringBankClient = acquiringBankClient;
        this.idempotencyService = idempotencyService;
        this.paymentProperties = paymentProperties;
    }

    /**
     * Executes the charge flow with strict transaction isolation:
     * 1. Check idempotency (Redis + DB) outside transaction.
     * 2. DB Tx 1 via PaymentTransactionService: Save initial PaymentIntent (status = PROCESSING).
     * 3. Network Call STRICTLY OUTSIDE Tx: Call acquiring bank via HTTP (zero DB connection held).
     * 4. DB Tx 2 via PaymentTransactionService: Finalize PaymentIntent and write Outbox event.
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

            // STEP 1 (Short Transaction via dedicated service): Persist intent
            transactionService.createInitialPaymentIntent(intentId, request, cardMask, cardToken, cardBlindHash);

            // STEP 2 (STRICTLY OUTSIDE TRANSACTION): Call Acquiring Bank over HTTP (zero DB connection held)
            MockBankChargeRequest bankRequest = new MockBankChargeRequest(cardMask, request.amount(), intentId.toString());
            MockBankChargeResponse bankResponse = acquiringBankClient.charge(bankRequest);

            // STEP 3 (Short Transaction via dedicated service): Finalize intent & write outbox event atomically
            PaymentIntentEntity finalizedEntity = transactionService.finalizePaymentIntent(intentId, bankResponse);

            ChargeResponse chargeResponse = toChargeResponse(finalizedEntity);
            idempotencyService.saveResponse(idempotencyKey, chargeResponse);
            return chargeResponse;

        } catch (Exception e) {
            idempotencyService.releaseLock(idempotencyKey);
            throw e;
        }
    }

    public PaymentIntentEntity finalizePaymentIntent(UUID intentId, MockBankChargeResponse bankResponse) {
        return transactionService.finalizePaymentIntent(intentId, bankResponse);
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
