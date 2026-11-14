package com.bnpl.checkout.domain;

import com.bnpl.common.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bnpl_orders", schema = "checkout_schema")
public class BnplOrderEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_order_id", nullable = false, unique = true, length = 128)
    private String merchantOrderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BnplOrderEntity() {
    }

    public BnplOrderEntity(UUID id,
                           String merchantOrderId,
                           UUID userId,
                           BigDecimal totalAmount,
                           OrderStatus status,
                           Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.merchantOrderId = Objects.requireNonNull(merchantOrderId, "merchantOrderId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = 0L;
    }

    public void markAuthorized() {
        this.status = OrderStatus.AUTHORIZED;
    }

    public void markRejected() {
        this.status = OrderStatus.REJECTED;
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantOrderId() {
        return merchantOrderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
