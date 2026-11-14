package com.bnpl.schedule.listener;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.enums.PaymentStatus;
import com.bnpl.common.enums.ScheduleStatus;
import com.bnpl.common.events.PaymentResultEvent;
import com.bnpl.common.events.PaymentSettledEvent;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.repository.InstallmentRepository;
import com.bnpl.schedule.repository.PaymentScheduleRepository;
import com.bnpl.schedule.service.InstallmentSettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSettledListenerTest {

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private PaymentScheduleRepository scheduleRepository;

    private InstallmentSettlementService settlementService;
    private PaymentSettledListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        settlementService = new InstallmentSettlementService(installmentRepository, scheduleRepository);
        listener = new PaymentSettledListener(settlementService, objectMapper);
    }

    @Test
    void shouldMarkInstallmentPaidAndReduceDebt() throws Exception {
        UUID installmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentScheduleEntity schedule = new PaymentScheduleEntity(
                UUID.randomUUID(), orderId, UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal("25.00"), "tok_card", ScheduleStatus.ACTIVE, Instant.now()
        );

        InstallmentEntity installment = new InstallmentEntity(
                installmentId, 4, new BigDecimal("25.00"), LocalDate.now(), InstallmentStatus.PROCESSING
        );
        installment.setSchedule(schedule);

        when(installmentRepository.findById(installmentId)).thenReturn(Optional.of(installment));

        PaymentSettledEvent event = new PaymentSettledEvent(
                UUID.randomUUID(), orderId, installmentId, new BigDecimal("25.00"), "tx_123", Instant.now()
        );

        String jsonPayload = objectMapper.writeValueAsString(event);
        listener.onPaymentResult(jsonPayload);

        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PAID);
        assertThat(schedule.getRemainingDebt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.COMPLETED);

        verify(installmentRepository).save(installment);
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void shouldMarkInstallmentAndScheduleOverdueOnFailure() throws Exception {
        UUID installmentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentScheduleEntity schedule = new PaymentScheduleEntity(
                UUID.randomUUID(), orderId, UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal("50.00"), "tok_card", ScheduleStatus.ACTIVE, Instant.now()
        );

        InstallmentEntity installment = new InstallmentEntity(
                installmentId, 3, new BigDecimal("25.00"), LocalDate.now(), InstallmentStatus.PROCESSING
        );
        installment.setSchedule(schedule);

        when(installmentRepository.findById(installmentId)).thenReturn(Optional.of(installment));

        PaymentResultEvent event = new PaymentResultEvent(
                UUID.randomUUID(), orderId, installmentId, PaymentStatus.FAILED, null, "INSUFFICIENT_FUNDS", Instant.now()
        );

        String jsonPayload = objectMapper.writeValueAsString(event);
        listener.onPaymentResult(jsonPayload);

        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.OVERDUE);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.OVERDUE);
        verify(installmentRepository).save(installment);
        verify(scheduleRepository).save(schedule);
    }
}
