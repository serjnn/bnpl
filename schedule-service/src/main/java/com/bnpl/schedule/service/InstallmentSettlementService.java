package com.bnpl.schedule.service;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.events.PaymentResultEvent;
import com.bnpl.common.events.PaymentSettledEvent;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.repository.InstallmentRepository;
import com.bnpl.schedule.repository.PaymentScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstallmentSettlementService {

    private static final Logger log = LoggerFactory.getLogger(InstallmentSettlementService.class);

    private final InstallmentRepository installmentRepository;
    private final PaymentScheduleRepository scheduleRepository;

    public InstallmentSettlementService(InstallmentRepository installmentRepository,
                                       PaymentScheduleRepository scheduleRepository) {
        this.installmentRepository = installmentRepository;
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Atomically marks an installment as PAID and updates remaining debt on the schedule.
     */
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

    /**
     * Atomically marks an installment and schedule as OVERDUE upon payment failure.
     */
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
