package com.bnpl.schedule.domain;

import com.bnpl.common.enums.InstallmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "installments", schema = "schedule_schema")
public class InstallmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private PaymentScheduleEntity schedule;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InstallmentStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected InstallmentEntity() {
    }

    public InstallmentEntity(UUID id,
                             int installmentNumber,
                             BigDecimal amount,
                             LocalDate dueDate,
                             InstallmentStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.installmentNumber = installmentNumber;
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.version = 0L;
    }

    public void setSchedule(PaymentScheduleEntity schedule) {
        this.schedule = schedule;
    }

    public void markProcessing() {
        this.status = InstallmentStatus.PROCESSING;
    }

    public void markPaid() {
        this.status = InstallmentStatus.PAID;
    }

    public void markOverdue() {
        this.status = InstallmentStatus.OVERDUE;
    }

    public void markGracePeriod() {
        this.status = InstallmentStatus.GRACE_PERIOD;
    }

    public UUID getId() {
        return id;
    }

    public PaymentScheduleEntity getSchedule() {
        return schedule;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public InstallmentStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}
