package com.bnpl.schedule.service;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.enums.ScheduleStatus;
import com.bnpl.common.events.OrderAuthorizedEvent;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.repository.PaymentScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScheduleCreationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCreationService.class);

    private final PaymentScheduleRepository scheduleRepository;

    public ScheduleCreationService(PaymentScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Creates a 4-share repayment schedule with penny-balancing on the 4th share.
     * Invariant: share4 = totalAmount - (share1 + share2 + share3)
     */
    @Transactional
    public PaymentScheduleEntity createSchedule(OrderAuthorizedEvent event) {
        Optional<PaymentScheduleEntity> existing = scheduleRepository.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            log.info("Payment schedule for order {} already exists", event.orderId());
            return existing.get();
        }

        BigDecimal total = event.totalAmount();
        BigDecimal standardShare = total.divide(new BigDecimal("4"), 2, RoundingMode.HALF_UP);

        BigDecimal share1 = standardShare;
        BigDecimal share2 = standardShare;
        BigDecimal share3 = standardShare;
        // Invariant: Penny-balancing on the 4th share
        BigDecimal share4 = total.subtract(share1.add(share2).add(share3));

        BigDecimal remainingDebt = total.subtract(share1);
        LocalDate today = LocalDate.now();

        PaymentScheduleEntity schedule = new PaymentScheduleEntity(
                UUID.randomUUID(),
                event.orderId(),
                event.userId(),
                total,
                remainingDebt,
                event.cardToken(),
                ScheduleStatus.ACTIVE,
                Instant.now()
        );

        // Installment 1: charged synchronously during checkout
        InstallmentEntity inst1 = new InstallmentEntity(
                UUID.randomUUID(), 1, share1, today, InstallmentStatus.PAID
        );
        // Installment 2: due in 14 days
        InstallmentEntity inst2 = new InstallmentEntity(
                UUID.randomUUID(), 2, share2, today.plusDays(14), InstallmentStatus.PENDING
        );
        // Installment 3: due in 28 days
        InstallmentEntity inst3 = new InstallmentEntity(
                UUID.randomUUID(), 3, share3, today.plusDays(28), InstallmentStatus.PENDING
        );
        // Installment 4: due in 42 days (penny-balanced)
        InstallmentEntity inst4 = new InstallmentEntity(
                UUID.randomUUID(), 4, share4, today.plusDays(42), InstallmentStatus.PENDING
        );

        schedule.addInstallment(inst1);
        schedule.addInstallment(inst2);
        schedule.addInstallment(inst3);
        schedule.addInstallment(inst4);

        PaymentScheduleEntity saved = scheduleRepository.save(schedule);
        log.info("Created 4-share schedule {} for order {}: [{}, {}, {}, {}]",
                saved.getId(), event.orderId(), share1, share2, share3, share4);

        return saved;
    }
}
