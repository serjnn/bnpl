package com.bnpl.checkout.service;

import com.bnpl.checkout.client.PaymentServiceClient;
import com.bnpl.checkout.domain.BnplOrderEntity;
import com.bnpl.checkout.domain.OutboxMessageEntity;
import com.bnpl.checkout.dto.CheckoutRequest;
import com.bnpl.checkout.dto.CheckoutResponse;
import com.bnpl.checkout.repository.BnplOrderRepository;
import com.bnpl.checkout.repository.OutboxMessageRepository;
import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.enums.OrderStatus;
import com.bnpl.common.enums.OutboxStatus;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.common.events.OrderAuthorizedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final BnplOrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final ObjectMapper objectMapper;

    public CheckoutService(BnplOrderRepository orderRepository,
                           OutboxMessageRepository outboxMessageRepository,
                           PaymentServiceClient paymentServiceClient,
                           ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.paymentServiceClient = paymentServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the checkout flow with strict transaction isolation:
     * 1. Short Tx: Persist initial order (status = PAYMENT_PROCESSING).
     * 2. STRICTLY OUTSIDE Tx: Call payment-service synchronously for 1st share (25%).
     * 3. Short Tx: Finalize order (AUTHORIZED / REJECTED) and write outbox event.
     */
    public CheckoutResponse checkout(CheckoutRequest request) {
        Optional<BnplOrderEntity> existing = orderRepository.findByMerchantOrderId(request.merchantOrderId());
        if (existing.isPresent()) {
            BnplOrderEntity entity = existing.get();
            log.info("Order with merchantOrderId {} already exists with status {}", request.merchantOrderId(), entity.getStatus());
            BigDecimal firstShare = calculateFirstShare(entity.getTotalAmount());
            return new CheckoutResponse(
                    entity.getId(),
                    entity.getMerchantOrderId(),
                    entity.getUserId(),
                    entity.getTotalAmount(),
                    firstShare,
                    entity.getStatus(),
                    null,
                    null,
                    entity.getCreatedAt()
            );
        }

        UUID orderId = UUID.randomUUID();
        BigDecimal firstShare = calculateFirstShare(request.totalAmount());

        // STEP 1 (Short Transaction): Persist order in PAYMENT_PROCESSING state
        createInitialOrder(orderId, request);

        // STEP 2 (STRICTLY OUTSIDE TRANSACTION): Synchronous call to payment-service
        String idempotencyKey = "checkout-" + orderId;
        PaymentServiceClient.PaymentResult paymentResult = paymentServiceClient.chargeFirstShare(
                orderId, firstShare, request.pan(), idempotencyKey
        );

        // STEP 3 (Short Transaction): Update order status and write outbox if authorized
        BnplOrderEntity finalizedOrder = finalizeOrder(orderId, paymentResult);

        return new CheckoutResponse(
                finalizedOrder.getId(),
                finalizedOrder.getMerchantOrderId(),
                finalizedOrder.getUserId(),
                finalizedOrder.getTotalAmount(),
                firstShare,
                finalizedOrder.getStatus(),
                paymentResult.externalTxId(),
                paymentResult.cardToken(),
                finalizedOrder.getCreatedAt()
        );
    }

    @Transactional
    public BnplOrderEntity createInitialOrder(UUID orderId, CheckoutRequest request) {
        BnplOrderEntity entity = new BnplOrderEntity(
                orderId,
                request.merchantOrderId(),
                request.userId(),
                request.totalAmount(),
                OrderStatus.PAYMENT_PROCESSING,
                Instant.now()
        );
        return orderRepository.save(entity);
    }

    @Transactional
    public BnplOrderEntity finalizeOrder(UUID orderId, PaymentServiceClient.PaymentResult paymentResult) {
        BnplOrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        if (paymentResult != null && paymentResult.status() == PaymentStatus.SUCCEEDED) {
            order.markAuthorized();

            // Transactional Outbox: ORDER_AUTHORIZED event
            OrderAuthorizedEvent event = new OrderAuthorizedEvent(
                    order.getId(),
                    order.getUserId(),
                    order.getMerchantOrderId(),
                    order.getTotalAmount(),
                    paymentResult.cardToken(),
                    Instant.now()
            );

            try {
                String payload = objectMapper.writeValueAsString(event);
                OutboxMessageEntity outbox = new OutboxMessageEntity(
                        UUID.randomUUID(),
                        "BnplOrder",
                        order.getId(),
                        KafkaTopics.ORDERS_EVENTS,
                        payload,
                        OutboxStatus.PENDING,
                        Instant.now()
                );
                outboxMessageRepository.save(outbox);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize OrderAuthorizedEvent", e);
            }
        } else {
            order.markRejected();
        }

        return orderRepository.save(order);
    }

    private BigDecimal calculateFirstShare(BigDecimal totalAmount) {
        return totalAmount.multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
    }
}
