package com.bnpl.schedule.listener;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.common.events.PaymentResultEvent;
import com.bnpl.common.events.PaymentSettledEvent;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.repository.InstallmentRepository;
import com.bnpl.schedule.repository.PaymentScheduleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentSettledListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettledListener.class);

    private final InstallmentRepository installmentRepository;
    private final PaymentScheduleRepository scheduleRepository;
    private final ObjectMapper objectMapper;

    public PaymentSettledListener(InstallmentRepository installmentRepository,
                                  PaymentScheduleRepository scheduleRepository,
                                  ObjectMapper objectMapper) {
        this.installmentRepository = installmentRepository;
        this.scheduleRepository = scheduleRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENTS_RESULTS, groupId = "schedule-service-payments-group")
    public void onPaymentResult(String messagePayload) {
        log.info("Received payment result message: {}", messagePayload);
        try {
            JsonNode root = objectMapper.readTree(messagePayload);
            if (root.has("settledAt") || (root.has("externalTxId") && !root.get("externalTxId").isNull() && !root.has("failureReason"))) {
                PaymentSettledEvent settledEvent = objectMapper.readValue(messagePayload, PaymentSettledEvent.class);
                handlePaymentSettled(settledEvent);
            } else {
                PaymentResultEvent resultEvent = objectMapper.readValue(messagePayload, PaymentResultEvent.class);
                handlePaymentFailed(resultEvent);
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict when processing payment result: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process payment result payload: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void handlePaymentSettled(PaymentSettledEvent event) {
        if (event.installmentId() == null) {
            log.info("Payment settled for initial checkout order {}, no installment to update", event.orderId());
            return;
        }

        InstallmentEntity installment = installmentRepository.findById(event.installmentId())
                .orElse(null);

        if (installment == null) {
            log.warn("Installment {} not found for settled event", event.installmentId());
            return;
        }

        if (installment.getStatus() == InstallmentStatus.PAID) {
            log.info("Installment {} already marked as PAID", installment.getId());
            return;
        }

        installment.markPaid();
        installmentRepository.save(installment);

        PaymentScheduleEntity schedule = installment.getSchedule();
        schedule.reduceDebt(event.amount());
        scheduleRepository.save(schedule);

        log.info("Installment {} marked PAID, remaining debt for order {} is {}",
                installment.getId(), schedule.getOrderId(), schedule.getRemainingDebt());
    }

    @Transactional
    public void handlePaymentFailed(PaymentResultEvent event) {
        if (event.installmentId() == null) {
            return;
        }

        InstallmentEntity installment = installmentRepository.findById(event.installmentId())
                .orElse(null);

        if (installment != null) {
            installment.markOverdue();
            installmentRepository.save(installment);

            PaymentScheduleEntity schedule = installment.getSchedule();
            schedule.markOverdue();
            scheduleRepository.save(schedule);

            log.warn("Installment {} marked OVERDUE due to failure: {}", installment.getId(), event.failureReason());
        }
    }
}
