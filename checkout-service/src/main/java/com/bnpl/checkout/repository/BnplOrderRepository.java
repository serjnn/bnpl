package com.bnpl.checkout.repository;

import com.bnpl.checkout.domain.BnplOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BnplOrderRepository extends JpaRepository<BnplOrderEntity, UUID> {
    Optional<BnplOrderEntity> findByMerchantOrderId(String merchantOrderId);
}
