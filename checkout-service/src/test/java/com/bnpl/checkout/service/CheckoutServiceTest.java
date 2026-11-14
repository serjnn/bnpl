package com.bnpl.checkout.service;

import com.bnpl.checkout.client.PaymentServiceClient;
import com.bnpl.checkout.domain.BnplOrderEntity;
import com.bnpl.checkout.domain.OutboxMessageEntity;
import com.bnpl.checkout.dto.CheckoutRequest;
import com.bnpl.checkout.dto.CheckoutResponse;
import com.bnpl.checkout.repository.BnplOrderRepository;
import com.bnpl.checkout.repository.OutboxMessageRepository;
import com.bnpl.common.enums.OrderStatus;
import com.bnpl.common.enums.PaymentStatus;
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
class CheckoutServiceTest {

    @Mock
    private BnplOrderRepository orderRepository;

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private CheckoutService checkoutService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        checkoutService = new CheckoutService(orderRepository, outboxMessageRepository, paymentServiceClient, objectMapper);
    }

    @Test
    void shouldAuthorizeOrderAndSaveOutboxWhenPaymentSucceeds() {
        UUID userId = UUID.randomUUID();
        String merchantOrderId = "merchant-order-001";
        BigDecimal totalAmount = new BigDecimal("100.00");
        CheckoutRequest request = new CheckoutRequest(userId, merchantOrderId, totalAmount, "4111222233334444");

        when(orderRepository.findByMerchantOrderId(merchantOrderId)).thenReturn(Optional.empty());
        when(orderRepository.save(any(BnplOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentServiceClient.PaymentResult paymentResult = new PaymentServiceClient.PaymentResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.SUCCEEDED,
                "tx_bank_123",
                "tok_card_test",
                "411122******4444",
                Instant.now()
        );

        when(paymentServiceClient.chargeFirstShare(any(UUID.class), eq(new BigDecimal("25.00")), eq("4111222233334444"), any(String.class)))
                .thenReturn(paymentResult);

        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.of(new BnplOrderEntity(id, merchantOrderId, userId, totalAmount, OrderStatus.PAYMENT_PROCESSING, Instant.now()));
        });

        CheckoutResponse response = checkoutService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.AUTHORIZED);
        assertThat(response.firstShareAmount()).isEqualByComparingTo("25.00");
        assertThat(response.cardToken()).isEqualTo("tok_card_test");

        // Verify outbox message saved with ORDER_AUTHORIZED event
        ArgumentCaptor<OutboxMessageEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(outboxMessageRepository).save(outboxCaptor.capture());
        OutboxMessageEntity outbox = outboxCaptor.getValue();
        assertThat(outbox.getTopic()).isEqualTo("bnpl.orders.events");
        assertThat(outbox.getPayload()).contains(merchantOrderId);
    }

    @Test
    void shouldRejectOrderWhenPaymentFails() {
        UUID userId = UUID.randomUUID();
        String merchantOrderId = "merchant-order-declined";
        BigDecimal totalAmount = new BigDecimal("100.00");
        CheckoutRequest request = new CheckoutRequest(userId, merchantOrderId, totalAmount, "4111222233330000");

        when(orderRepository.findByMerchantOrderId(merchantOrderId)).thenReturn(Optional.empty());
        when(orderRepository.save(any(BnplOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentServiceClient.PaymentResult paymentResult = new PaymentServiceClient.PaymentResult(
                null,
                UUID.randomUUID(),
                PaymentStatus.FAILED,
                null,
                null,
                null,
                null
        );

        when(paymentServiceClient.chargeFirstShare(any(UUID.class), eq(new BigDecimal("25.00")), eq("4111222233330000"), any(String.class)))
                .thenReturn(paymentResult);

        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.of(new BnplOrderEntity(id, merchantOrderId, userId, totalAmount, OrderStatus.PAYMENT_PROCESSING, Instant.now()));
        });

        CheckoutResponse response = checkoutService.checkout(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        verify(outboxMessageRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingOrderWhenMerchantOrderIdDuplicate() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String merchantOrderId = "merchant-order-duplicate";
        BigDecimal totalAmount = new BigDecimal("200.00");
        BnplOrderEntity existingEntity = new BnplOrderEntity(
                orderId, merchantOrderId, userId, totalAmount, OrderStatus.AUTHORIZED, Instant.now()
        );

        when(orderRepository.findByMerchantOrderId(merchantOrderId)).thenReturn(Optional.of(existingEntity));

        CheckoutRequest request = new CheckoutRequest(userId, merchantOrderId, totalAmount, "4111222233334444");
        CheckoutResponse response = checkoutService.checkout(request);

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo(OrderStatus.AUTHORIZED);
        verify(paymentServiceClient, never()).chargeFirstShare(any(), any(), any(), any());
    }

    @Test
    void shouldCalculatePennyRoundingCorrectly() {
        UUID userId = UUID.randomUUID();
        String merchantOrderId = "merchant-order-rounding";
        BigDecimal totalAmount = new BigDecimal("100.03"); // 100.03 * 0.25 = 25.0075 -> 25.01
        CheckoutRequest request = new CheckoutRequest(userId, merchantOrderId, totalAmount, "4111222233334444");

        when(orderRepository.findByMerchantOrderId(merchantOrderId)).thenReturn(Optional.empty());
        when(orderRepository.save(any(BnplOrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentServiceClient.PaymentResult paymentResult = new PaymentServiceClient.PaymentResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                PaymentStatus.SUCCEEDED,
                "tx_bank_123",
                "tok_card_test",
                "411122******4444",
                Instant.now()
        );

        when(paymentServiceClient.chargeFirstShare(any(UUID.class), eq(new BigDecimal("25.01")), any(), any()))
                .thenReturn(paymentResult);

        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.of(new BnplOrderEntity(id, merchantOrderId, userId, totalAmount, OrderStatus.PAYMENT_PROCESSING, Instant.now()));
        });

        CheckoutResponse response = checkoutService.checkout(request);

        assertThat(response.firstShareAmount()).isEqualByComparingTo("25.01");
    }
}
