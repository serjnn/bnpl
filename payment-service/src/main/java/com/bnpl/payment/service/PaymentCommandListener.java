package com.bnpl.payment.service;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.events.ProcessPaymentCommand;
import com.bnpl.payment.dto.ChargeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentCommandListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandListener.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentCommandListener(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENTS_COMMANDS, groupId = "payment-service-group")
    public void handleProcessPaymentCommand(String messagePayload) {
        log.info("Received process payment command: {}", messagePayload);
        try {
            ProcessPaymentCommand command = objectMapper.readValue(messagePayload, ProcessPaymentCommand.class);
            ChargeRequest chargeRequest = new ChargeRequest(
                    command.orderId(),
                    command.installmentId(),
                    command.amount(),
                    null,
                    command.cardToken(),
                    command.idempotencyKey()
            );
            paymentService.processCharge(chargeRequest);
        } catch (Exception e) {
            log.error("Failed to process payment command: {}", e.getMessage(), e);
        }
    }
}
