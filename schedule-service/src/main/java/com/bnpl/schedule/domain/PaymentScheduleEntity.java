package com.bnpl.schedule.domain;

import com.bnpl.common.enums.ScheduleStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_schedules", schema = "schedule_schema")
public class PaymentScheduleEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "remaining_debt", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingDebt;

    @Column(name = "card_token", nullable = false, length = 128)
    private String cardToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ScheduleStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<InstallmentEntity> installments = new ArrayList<>();

    protected PaymentScheduleEntity() {
    }

    public PaymentScheduleEntity(UUID id,
                                 UUID orderId,
                                 UUID userId,
                                 BigDecimal totalAmount,
                                 BigDecimal remainingDebt,
                                 String cardToken,
                                 ScheduleStatus status,
                                 Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.totalAmount = Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        this.remainingDebt = Objects.requireNonNull(remainingDebt, "remainingDebt must not be null");
        this.cardToken = Objects.requireNonNull(cardToken, "cardToken must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = 0L;
    }

    public void addInstallment(InstallmentEntity installment) {
        installments.add(installment);
        installment.setSchedule(this);
    }

    public void reduceDebt(BigDecimal amount) {
        this.remainingDebt = this.remainingDebt.subtract(amount);
        if (this.remainingDebt.compareTo(BigDecimal.ZERO) <= 0) {
            this.remainingDebt = BigDecimal.ZERO;
            this.status = ScheduleStatus.COMPLETED;
        }
    }

    public void markOverdue() {
        if (this.status != ScheduleStatus.COMPLETED) {
            this.status = ScheduleStatus.OVERDUE;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getRemainingDebt() {
        return remainingDebt;
    }

    public String getCardToken() {
        return cardToken;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<InstallmentEntity> getInstallments() {
        return installments;
    }
}
