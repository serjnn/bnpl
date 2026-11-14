package com.bnpl.checkout.service;

import com.bnpl.checkout.client.PaymentServiceClient;
import com.bnpl.checkout.domain.BnplOrderEntity;
import com.bnpl.checkout.domain.OutboxMessageEntity;
import com.bnpl.checkout.dto.CheckoutRequest;
import com.bnpl.checkout.repository.BnplOrderRepository;
import com.bnpl.checkout.repository.OutboxMessageRepository;
import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.enums.OrderStatus;
import com.bnpl.common.enums.OutboxStatus;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.common.events.OrderAuthorizedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CheckoutTransactionService {

    private final BnplOrderRepository orderRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public CheckoutTransactionService(BnplOrderRepository orderRepository,
                                    OutboxMessageRepository outboxMessageRepository,
                                    ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the initial order in PAYMENT_PROCESSING state within a short database transaction.
     */
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

    /**
     * Finalizes the order and writes the transactional outbox message in a single atomic database transaction.
     */
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
}
