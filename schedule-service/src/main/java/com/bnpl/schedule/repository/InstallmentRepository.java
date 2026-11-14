package com.bnpl.schedule.repository;

import com.bnpl.common.enums.InstallmentStatus;
import com.bnpl.schedule.domain.InstallmentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentRepository extends JpaRepository<InstallmentEntity, UUID> {

    @Query("SELECT i FROM InstallmentEntity i JOIN FETCH i.schedule s " +
           "WHERE i.status = :status AND i.dueDate <= :today " +
           "AND (:lastSeenId IS NULL OR i.id > :lastSeenId) " +
           "ORDER BY i.id ASC")
    List<InstallmentEntity> findDueInstallmentsWithSchedule(
            @Param("status") InstallmentStatus status,
            @Param("today") LocalDate today,
            @Param("lastSeenId") UUID lastSeenId,
            Pageable pageable
    );
}
