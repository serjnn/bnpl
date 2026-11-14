package com.bnpl.schedule.service;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.enums.ScheduleStatus;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.repository.InstallmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NightlyBillingSchedulerTest {

    @Mock
    private InstallmentRepository installmentRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private InstallmentStateService installmentStateService;
    private NightlyBillingScheduler scheduler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        installmentStateService = new InstallmentStateService(installmentRepository);
        scheduler = new NightlyBillingScheduler(installmentRepository, installmentStateService, kafkaTemplate, objectMapper, 2);
    }

    @Test
    void shouldIterateUsingKeysetCursorAndDispatchCommands() {
        UUID orderId = UUID.randomUUID();
        PaymentScheduleEntity schedule = new PaymentScheduleEntity(
                UUID.randomUUID(), orderId, UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal("75.00"), "tok_card", ScheduleStatus.ACTIVE, Instant.now()
        );

        InstallmentEntity inst1 = new InstallmentEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                2, new BigDecimal("25.00"), LocalDate.now().minusDays(1), InstallmentStatus.PENDING
        );
        inst1.setSchedule(schedule);

        InstallmentEntity inst2 = new InstallmentEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                2, new BigDecimal("25.00"), LocalDate.now(), InstallmentStatus.PENDING
        );
        inst2.setSchedule(schedule);

        // Batch 1 returns inst1 and inst2
        when(installmentRepository.findDueInstallmentsWithSchedule(
                eq(InstallmentStatus.PENDING), any(LocalDate.class), eq(null), any(Pageable.class)))
                .thenReturn(List.of(inst1, inst2));

        // Batch 2 returns empty list (end of pagination)
        when(installmentRepository.findDueInstallmentsWithSchedule(
                eq(InstallmentStatus.PENDING), any(LocalDate.class), eq(inst2.getId()), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(installmentRepository.findById(inst1.getId())).thenReturn(Optional.of(inst1));
        when(installmentRepository.findById(inst2.getId())).thenReturn(Optional.of(inst2));
        when(installmentRepository.saveAndFlush(any(InstallmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        int processed = scheduler.executeBillingJob();

        assertThat(processed).isEqualTo(2);
        assertThat(inst1.getStatus()).isEqualTo(InstallmentStatus.PROCESSING);
        assertThat(inst2.getStatus()).isEqualTo(InstallmentStatus.PROCESSING);
        verify(installmentRepository).saveAndFlush(inst1);
        verify(installmentRepository).saveAndFlush(inst2);
    }
}
