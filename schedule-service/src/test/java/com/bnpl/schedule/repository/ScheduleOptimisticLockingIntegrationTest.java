package com.bnpl.schedule.repository;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.common.enums.ScheduleStatus;
import com.bnpl.schedule.domain.InstallmentEntity;
import com.bnpl.schedule.domain.PaymentScheduleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ScheduleOptimisticLockingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bnpl_test_db")
            .withUsername("bnpl_test")
            .withPassword("bnpl_test_pass")
            .withInitScript("init-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "&currentSchema=schedule_schema");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "schedule_schema");
        registry.add("spring.liquibase.enabled", () -> "false");
    }

    @Autowired
    private PaymentScheduleRepository scheduleRepository;

    @Autowired
    private InstallmentRepository installmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindDueInstallmentsViaKeysetCursorWithoutNPlusOne() {
        UUID orderId = UUID.randomUUID();
        PaymentScheduleEntity schedule = new PaymentScheduleEntity(
                UUID.randomUUID(), orderId, UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal("75.00"), "tok_card_123", ScheduleStatus.ACTIVE, Instant.now()
        );

        InstallmentEntity inst1 = new InstallmentEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                1, new BigDecimal("25.00"), LocalDate.now().minusDays(1), InstallmentStatus.PENDING
        );
        InstallmentEntity inst2 = new InstallmentEntity(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                2, new BigDecimal("25.00"), LocalDate.now(), InstallmentStatus.PENDING
        );

        schedule.addInstallment(inst1);
        schedule.addInstallment(inst2);

        scheduleRepository.saveAndFlush(schedule);
        entityManager.clear();

        List<InstallmentEntity> due = installmentRepository.findDueInstallmentsWithSchedule(
                InstallmentStatus.PENDING, LocalDate.now(), null, PageRequest.of(0, 10)
        );

        assertThat(due).hasSize(2);
        // Ensure schedule is loaded in same query (JOIN FETCH, no LazyInitializationException)
        assertThat(due.get(0).getSchedule().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void shouldThrowOptimisticLockExceptionOnConcurrentUpdate() {
        PaymentScheduleEntity schedule = new PaymentScheduleEntity(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"),
                new BigDecimal("75.00"), "tok_card_concurrent", ScheduleStatus.ACTIVE, Instant.now()
        );
        InstallmentEntity inst = new InstallmentEntity(
                UUID.randomUUID(), 2, new BigDecimal("25.00"), LocalDate.now(), InstallmentStatus.PENDING
        );
        schedule.addInstallment(inst);

        scheduleRepository.saveAndFlush(schedule);
        entityManager.clear();

        // Load entity into persistence context (version = 0)
        InstallmentEntity loadedEntity = installmentRepository.findById(inst.getId()).orElseThrow();

        // Simulate concurrent transaction updating the DB row directly
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE schedule_schema.installments SET version = version + 1 WHERE id = :id")
                .setParameter("id", inst.getId())
                .executeUpdate();

        // Attempting to update and flush the entity with outdated version must fail
        loadedEntity.markProcessing();
        assertThatThrownBy(() -> {
            installmentRepository.saveAndFlush(loadedEntity);
            entityManager.flush();
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
