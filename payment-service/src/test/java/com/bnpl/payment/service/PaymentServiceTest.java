package com.bnpl.payment.service;

import com.bnpl.common.enums.PaymentStatus;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentIntentRepository paymentIntentRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private AcquiringBankClient acquiringBankClient;

    @Mock
    private IdempotencyService idempotencyService;

    private PaymentTransactionService transactionService;
    private PaymentService paymentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        PaymentProperties properties = new PaymentProperties("whsec_test", "pep_test_pepper");

        transactionService = new PaymentTransactionService(
                paymentIntentRepository,
                outboxMessageRepository,
                objectMapper
        );

        paymentService = new PaymentService(
                paymentIntentRepository,
                transactionService,
                acquiringBankClient,
                idempotencyService,
                properties
        );
    }

    @Test
    void shouldProcessChargeSuccessfullyAndWriteOutbox() {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "idemp_order_1";
        ChargeRequest request = new ChargeRequest(
                orderId,
                null,
                new BigDecimal("25.00"),
                "4111 2222 3333 4444",
                null,
                idempotencyKey
        );

        when(idempotencyService.getCachedResponse(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentIntentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(idempotencyKey)).thenReturn(true);

        when(paymentIntentRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(acquiringBankClient.charge(any(MockBankChargeRequest.class)))
                .thenReturn(new MockBankChargeResponse(true, "tx_12345", null));

        when(paymentIntentRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return Optional.of(new PaymentIntentEntity(
                            id,
                            idempotencyKey,
                            orderId,
                            null,
                            new BigDecimal("25.00"),
                            "411122******4444",
                            "tok_test",
                            "blind_hash_test",
                            PaymentStatus.PROCESSING,
                            Instant.now()
                    ));
                });

        ChargeResponse response = paymentService.processCharge(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.externalTxId()).isEqualTo("tx_12345");
        assertThat(response.cardMask()).isEqualTo("411122******4444");

        // Verify outbox message saved
        ArgumentCaptor<OutboxMessageEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());
        OutboxMessageEntity savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getTopic()).isEqualTo("bnpl.payments.results");
        assertThat(savedOutbox.getPayload()).contains("tx_12345");

        // Verify response cached
        verify(idempotencyService).saveResponse(eq(idempotencyKey), any(ChargeResponse.class));
    }

    @Test
    void shouldReturnCachedResponseWhenIdempotencyKeyExists() {
        String idempotencyKey = "idemp_cached";
        ChargeResponse cached = new ChargeResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                new BigDecimal("25.00"),
                PaymentStatus.SUCCEEDED,
                "tx_cached_123",
                "411122******4444",
                "tok_123",
                Instant.now()
        );

        when(idempotencyService.getCachedResponse(idempotencyKey)).thenReturn(Optional.of(cached));

        ChargeRequest request = new ChargeRequest(
                cached.orderId(),
                null,
                cached.amount(),
                "4111 2222 3333 4444",
                null,
                idempotencyKey
        );

        ChargeResponse result = paymentService.processCharge(request);

        assertThat(result).isEqualTo(cached);
        verify(acquiringBankClient, never()).charge(any());
        verify(paymentIntentRepository, never()).save(any());
    }

    @Test
    void shouldHandleBankDeclineGracefully() {
        UUID orderId = UUID.randomUUID();
        String idempotencyKey = "idemp_declined";
        ChargeRequest request = new ChargeRequest(
                orderId,
                null,
                new BigDecimal("25.00"),
                "4111 2222 3333 0000",
                null,
                idempotencyKey
        );

        when(idempotencyService.getCachedResponse(idempotencyKey)).thenReturn(Optional.empty());
        when(paymentIntentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(idempotencyService.acquireLock(idempotencyKey)).thenReturn(true);

        when(paymentIntentRepository.save(any(PaymentIntentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(acquiringBankClient.charge(any(MockBankChargeRequest.class)))
                .thenReturn(new MockBankChargeResponse(false, null, "INSUFFICIENT_FUNDS"));

        when(paymentIntentRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    return Optional.of(new PaymentIntentEntity(
                            id,
                            idempotencyKey,
                            orderId,
                            null,
                            new BigDecimal("25.00"),
                            "411122******0000",
                            "tok_test",
                            "blind_hash_test",
                            PaymentStatus.PROCESSING,
                            Instant.now()
                    ));
                });

        ChargeResponse response = paymentService.processCharge(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.externalTxId()).isEqualTo("INSUFFICIENT_FUNDS");

        // Verify failure outbox message saved
        verify(outboxMessageRepository).save(any(OutboxMessageEntity.class));
    }
}
