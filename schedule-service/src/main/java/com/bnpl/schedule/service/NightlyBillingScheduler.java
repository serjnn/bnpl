package com.bnpl.schedule.service;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.events.ProcessPaymentCommand;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.repository.InstallmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class NightlyBillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(NightlyBillingScheduler.class);

    private final InstallmentRepository installmentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public NightlyBillingScheduler(InstallmentRepository installmentRepository,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${bnpl.billing.batch-size:50}") int batchSize) {
        this.installmentRepository = installmentRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    /**
     * Nightly distributed billing job with ShedLock.
     * Uses strictly Keyset/Cursor pagination to prevent OFFSET/LIMIT table scans.
     */
    @Scheduled(cron = "${bnpl.billing.cron:0 0 1 * * ?}")
    @SchedulerLock(name = "nightlyBillingLock", lockAtMostFor = "10m", lockAtLeastFor = "10s")
    public int executeBillingJob() {
        log.info("Starting nightly billing job using keyset/cursor pagination...");
        LocalDate today = LocalDate.now();
        UUID lastSeenId = null;
        int totalProcessed = 0;

        while (true) {
            List<InstallmentEntity> batch = installmentRepository.findDueInstallmentsWithSchedule(
                    InstallmentStatus.PENDING,
                    today,
                    lastSeenId,
                    PageRequest.of(0, batchSize)
            );

            if (batch.isEmpty()) {
                break;
            }

            for (InstallmentEntity installment : batch) {
                lastSeenId = installment.getId();
                try {
                    dispatchPaymentCommand(installment);
                    totalProcessed++;
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("Optimistic lock conflict processing installment {}: concurrent update detected", installment.getId());
                } catch (Exception e) {
                    log.error("Failed to process due installment {}: {}", installment.getId(), e.getMessage(), e);
                }
            }
        }

        log.info("Nightly billing job completed. Total installments processed: {}", totalProcessed);
        return totalProcessed;
    }

    /**
     * Updates installment status to PROCESSING within a transaction enforcing @Version optimistic lock,
     * and dispatches payment command to Kafka.
     */
    @Transactional
    public void dispatchPaymentCommand(InstallmentEntity installment) {
        installment.markProcessing();
        installmentRepository.save(installment);

        UUID paymentIntentId = UUID.randomUUID();
        String idempotencyKey = "billing-" + installment.getId() + "-v" + installment.getVersion();

        ProcessPaymentCommand command = new ProcessPaymentCommand(
                paymentIntentId,
                installment.getSchedule().getOrderId(),
                installment.getId(),
                installment.getAmount(),
                installment.getSchedule().getCardToken(),
                idempotencyKey
        );

        try {
            String payload = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(KafkaTopics.PAYMENTS_COMMANDS, installment.getSchedule().getOrderId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish payment command for installment {}: {}", installment.getId(), ex.getMessage());
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ProcessPaymentCommand", e);
        }
    }
}
