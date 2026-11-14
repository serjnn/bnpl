package com.bnpl.schedule.service;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.repository.InstallmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InstallmentStateService {

    private final InstallmentRepository installmentRepository;

    public InstallmentStateService(InstallmentRepository installmentRepository) {
        this.installmentRepository = installmentRepository;
    }

    /**
     * Updates an installment's status to PROCESSING in a dedicated, short database transaction.
     * Enforces optimistic locking via @Version on InstallmentEntity.
     * If a concurrent update occurred, ObjectOptimisticLockingFailureException is thrown upon saveAndFlush.
     */
    @Transactional
    public InstallmentEntity markAsProcessing(UUID installmentId) {
        InstallmentEntity installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new IllegalStateException("Installment not found: " + installmentId));

        if (installment.getStatus() != InstallmentStatus.PENDING) {
            return null;
        }

        installment.markProcessing();
        return installmentRepository.saveAndFlush(installment);
    }
}
