package com.bnpl.schedule.service;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.enums.ScheduleStatus;
import com.bnpl.common.events.OrderAuthorizedEvent;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.repository.PaymentScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleCreationServiceTest {

    @Mock
    private PaymentScheduleRepository scheduleRepository;

    private ScheduleCreationService creationService;

    @BeforeEach
    void setUp() {
        creationService = new ScheduleCreationService(scheduleRepository);
    }

    @Test
    void shouldCreateScheduleWithPennyBalancingInvariantFor100_03() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal total = new BigDecimal("100.03");
        OrderAuthorizedEvent event = new OrderAuthorizedEvent(
                orderId, userId, "merchant_123", total, "tok_card_xyz", Instant.now()
        );

        when(scheduleRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any(PaymentScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentScheduleEntity schedule = creationService.createSchedule(event);

        assertThat(schedule).isNotNull();
        assertThat(schedule.getTotalAmount()).isEqualByComparingTo("100.03");
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.ACTIVE);

        List<InstallmentEntity> installments = schedule.getInstallments();
        assertThat(installments).hasSize(4);

        InstallmentEntity inst1 = installments.get(0);
        InstallmentEntity inst2 = installments.get(1);
        InstallmentEntity inst3 = installments.get(2);
        InstallmentEntity inst4 = installments.get(3);

        // 100.03 / 4 = 25.0075 -> 25.01
        assertThat(inst1.getAmount()).isEqualByComparingTo("25.01");
        assertThat(inst1.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(inst1.getDueDate()).isEqualTo(LocalDate.now());

        assertThat(inst2.getAmount()).isEqualByComparingTo("25.01");
        assertThat(inst2.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(inst2.getDueDate()).isEqualTo(LocalDate.now().plusDays(14));

        assertThat(inst3.getAmount()).isEqualByComparingTo("25.01");
        assertThat(inst3.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(inst3.getDueDate()).isEqualTo(LocalDate.now().plusDays(28));

        // 4th share must balance: 100.03 - (25.01 + 25.01 + 25.01) = 25.00
        assertThat(inst4.getAmount()).isEqualByComparingTo("25.00");
        assertThat(inst4.getStatus()).isEqualTo(InstallmentStatus.PENDING);
        assertThat(inst4.getDueDate()).isEqualTo(LocalDate.now().plusDays(42));

        BigDecimal sum = inst1.getAmount().add(inst2.getAmount()).add(inst3.getAmount()).add(inst4.getAmount());
        assertThat(sum).isEqualByComparingTo(total);

        // Remaining debt = total - share1 = 100.03 - 25.01 = 75.02
        assertThat(schedule.getRemainingDebt()).isEqualByComparingTo("75.02");
    }

    @Test
    void shouldCreateScheduleWithPennyBalancingInvariantFor100_01() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        BigDecimal total = new BigDecimal("100.01");
        OrderAuthorizedEvent event = new OrderAuthorizedEvent(
                orderId, userId, "merchant_123", total, "tok_card_xyz", Instant.now()
        );

        when(scheduleRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(scheduleRepository.save(any(PaymentScheduleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentScheduleEntity schedule = creationService.createSchedule(event);

        List<InstallmentEntity> installments = schedule.getInstallments();
        // 100.01 / 4 = 25.0025 -> 25.00
        // share4 = 100.01 - 75.00 = 25.01
        assertThat(installments.get(0).getAmount()).isEqualByComparingTo("25.00");
        assertThat(installments.get(1).getAmount()).isEqualByComparingTo("25.00");
        assertThat(installments.get(2).getAmount()).isEqualByComparingTo("25.00");
        assertThat(installments.get(3).getAmount()).isEqualByComparingTo("25.01");

        BigDecimal sum = installments.stream().map(InstallmentEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(total);
    }
}
