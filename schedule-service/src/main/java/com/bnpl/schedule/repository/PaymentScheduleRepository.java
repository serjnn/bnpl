package com.bnpl.schedule.repository;

import com.bnpl.schedule.domain.PaymentScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentScheduleRepository extends JpaRepository<PaymentScheduleEntity, UUID> {

    Optional<PaymentScheduleEntity> findByOrderId(UUID orderId);

    @Query("SELECT s FROM PaymentScheduleEntity s LEFT JOIN FETCH s.installments WHERE s.orderId = :orderId")
    Optional<PaymentScheduleEntity> findByOrderIdWithInstallments(@Param("orderId") UUID orderId);
}
