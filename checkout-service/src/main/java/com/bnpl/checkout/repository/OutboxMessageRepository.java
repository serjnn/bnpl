package com.bnpl.checkout.repository;

import com.bnpl.checkout.domain.OutboxMessageEntity;
import com.bnpl.common.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID> {
    List<OutboxMessageEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
