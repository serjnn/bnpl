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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisherService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxMessageRepository outboxMessageRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisherService(OutboxMessageRepository outboxMessageRepository,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
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
                kafkaTemplate.send(message.getTopic(), message.getAggregateId().toString(), message.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("Failed to send outbox message {} to topic {}: {}",
                                        message.getId(), message.getTopic(), ex.getMessage());
                            }
                        });
                message.markSent();
                outboxMessageRepository.save(message);
            } catch (Exception e) {
                log.error("Error dispatching checkout outbox message {}: {}", message.getId(), e.getMessage());
            }
        }
    }
}
