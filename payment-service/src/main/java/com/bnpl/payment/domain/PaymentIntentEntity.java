package com.bnpl.payment.domain;

import com.bnpl.common.enums.PaymentStatus;
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
@Table(name = "payment_intents", schema = "payment_schema")
public class PaymentIntentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "installment_id")
    private UUID installmentId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "card_mask", nullable = false, length = 32)
    private String cardMask;

    @Column(name = "card_token", nullable = false, length = 128)
    private String cardToken;

    @Column(name = "card_blind_hash", nullable = false, length = 128)
    private String cardBlindHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "external_tx_id", length = 128)
    private String externalTxId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PaymentIntentEntity() {
    }

    public PaymentIntentEntity(UUID id,
                               String idempotencyKey,
                               UUID orderId,
                               UUID installmentId,
                               BigDecimal amount,
                               String cardMask,
                               String cardToken,
                               String cardBlindHash,
                               PaymentStatus status,
                               Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        this.installmentId = installmentId;
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.cardMask = Objects.requireNonNull(cardMask, "cardMask must not be null");
        this.cardToken = Objects.requireNonNull(cardToken, "cardToken must not be null");
        this.cardBlindHash = Objects.requireNonNull(cardBlindHash, "cardBlindHash must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.version = 0L;
    }

    public void markSucceeded(String externalTxId) {
        this.status = PaymentStatus.SUCCEEDED;
        this.externalTxId = externalTxId;
    }

    public void markFailed(String externalTxId) {
        this.status = PaymentStatus.FAILED;
        this.externalTxId = externalTxId;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getInstallmentId() {
        return installmentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCardMask() {
        return cardMask;
    }

    public String getCardToken() {
        return cardToken;
    }

    public String getCardBlindHash() {
        return cardBlindHash;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getExternalTxId() {
        return externalTxId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
