package com.bnpl.checkout.service;

import com.bnpl.checkout.domain.OutboxMessageEntity;
import com.bnpl.checkout.repository.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxTransactionService {

    private final OutboxMessageRepository outboxMessageRepository;

    public OutboxTransactionService(OutboxMessageRepository outboxMessageRepository) {
        this.outboxMessageRepository = outboxMessageRepository;
    }

    /**
     * Marks an outbox message as SENT in a dedicated, fast database transaction.
     */
    @Transactional
    public void markSent(UUID messageId) {
        outboxMessageRepository.findById(messageId).ifPresent(message -> {
            message.markSent();
            outboxMessageRepository.save(message);
        });
    }
}
