package com.bnpl.schedule.listener;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.events.PaymentResultEvent;
import com.bnpl.common.events.PaymentSettledEvent;
import com.bnpl.schedule.service.InstallmentSettlementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class PaymentSettledListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettledListener.class);

    private final InstallmentSettlementService settlementService;
    private final ObjectMapper objectMapper;

    public PaymentSettledListener(InstallmentSettlementService settlementService,
                                  ObjectMapper objectMapper) {
        this.settlementService = settlementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENTS_RESULTS, groupId = "schedule-service-payments-group")
    public void onPaymentResult(String messagePayload) {
        log.info("Received payment result message: {}", messagePayload);
        try {
            JsonNode root = objectMapper.readTree(messagePayload);
            if (root.has("settledAt") || (root.has("externalTxId") && !root.get("externalTxId").isNull() && !root.has("failureReason"))) {
                PaymentSettledEvent settledEvent = objectMapper.readValue(messagePayload, PaymentSettledEvent.class);
                settlementService.handlePaymentSettled(settledEvent);
            } else {
                PaymentResultEvent resultEvent = objectMapper.readValue(messagePayload, PaymentResultEvent.class);
                settlementService.handlePaymentFailed(resultEvent);
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict when processing payment result: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process payment result payload: {}", e.getMessage(), e);
        }
    }
}
