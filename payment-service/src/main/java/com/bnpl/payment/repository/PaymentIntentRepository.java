package com.bnpl.payment.repository;

import com.bnpl.payment.domain.PaymentIntentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID> {
    Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);
}
