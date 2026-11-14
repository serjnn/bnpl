package com.bnpl.checkout.service;

import com.bnpl.checkout.domain.OutboxMessageEntity;
import com.bnpl.checkout.repository.OutboxMessageRepository;
import com.bnpl.common.enums.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxTransactionService outboxTransactionService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(OutboxMessageRepository outboxMessageRepository,
                                  OutboxTransactionService outboxTransactionService,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.outboxTransactionService = outboxTransactionService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Outbox publisher scheduled poller.
     * STRICT TRANSACTION BOUNDARY:
     * 1. Reads pending messages outside any long transaction.
     * 2. Publishes to Kafka over network with timeout STRICTLY OUTSIDE ANY DB TRANSACTION.
     * 3. Upon verified Kafka ACK, marks message as SENT in a dedicated, short DB transaction.
     */
    @Scheduled(fixedDelay = 1000)
    public void publishPendingMessages() {
        List<OutboxMessageEntity> pending = outboxMessageRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50)
        );

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox messages in checkout-service", pending.size());

        for (OutboxMessageEntity message : pending) {
            try {
                // Network I/O outside DB transaction
                kafkaTemplate.send(message.getTopic(), message.getAggregateId().toString(), message.getPayload())
                        .get(5, TimeUnit.SECONDS);

                // Short, dedicated DB transaction to mark as SENT
                outboxTransactionService.markSent(message.getId());
            } catch (Exception e) {
                log.error("Failed to publish checkout outbox message {} to topic {}: {}",
                        message.getId(), message.getTopic(), e.getMessage());
                // Leave status as PENDING to retry on next poll without holding any DB connection
            }
        }
    }
}
