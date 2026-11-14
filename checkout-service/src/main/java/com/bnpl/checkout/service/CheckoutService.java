package com.bnpl.checkout.service;

import com.bnpl.checkout.client.PaymentServiceClient;
import com.bnpl.checkout.domain.BnplOrderEntity;
import com.bnpl.checkout.dto.CheckoutRequest;
import com.bnpl.checkout.dto.CheckoutResponse;
import com.bnpl.checkout.repository.BnplOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final BnplOrderRepository orderRepository;
    private final CheckoutTransactionService transactionService;
    private final PaymentServiceClient paymentServiceClient;

    public CheckoutService(BnplOrderRepository orderRepository,
                           CheckoutTransactionService transactionService,
                           PaymentServiceClient paymentServiceClient) {
        this.orderRepository = orderRepository;
        this.transactionService = transactionService;
        this.paymentServiceClient = paymentServiceClient;
    }

    /**
     * Executes the checkout flow with strict transaction isolation:
     * 1. Short Tx: Persist initial order (status = PAYMENT_PROCESSING) via CheckoutTransactionService.
     * 2. STRICTLY OUTSIDE Tx: Call payment-service synchronously for 1st share (25%). No DB connection held.
     * 3. Short Tx: Finalize order (AUTHORIZED / REJECTED) and write outbox event via CheckoutTransactionService.
     */
    public CheckoutResponse checkout(CheckoutRequest request) {
        Optional<BnplOrderEntity> existing = orderRepository.findByMerchantOrderId(request.merchantOrderId());
        if (existing.isPresent()) {
            BnplOrderEntity entity = existing.get();
            log.info("Order with merchantOrderId {} already exists with status {}", request.merchantOrderId(), entity.getStatus());
            BigDecimal firstShare = calculateFirstShare(entity.getTotalAmount());
            return new CheckoutResponse(
                    entity.getId(),
                    entity.getMerchantOrderId(),
                    entity.getUserId(),
                    entity.getTotalAmount(),
                    firstShare,
                    entity.getStatus(),
                    null,
                    null,
                    entity.getCreatedAt()
            );
        }

        UUID orderId = UUID.randomUUID();
        BigDecimal firstShare = calculateFirstShare(request.totalAmount());

        // STEP 1 (Short Transaction via dedicated service): Persist order in PAYMENT_PROCESSING state
        transactionService.createInitialOrder(orderId, request);

        // STEP 2 (STRICTLY OUTSIDE TRANSACTION): Synchronous call to payment-service (zero DB connection held)
        String idempotencyKey = "checkout-" + orderId;
        PaymentServiceClient.PaymentResult paymentResult = paymentServiceClient.chargeFirstShare(
                orderId, firstShare, request.pan(), idempotencyKey
        );

        // STEP 3 (Short Transaction via dedicated service): Update order status and atomically write outbox
        BnplOrderEntity finalizedOrder = transactionService.finalizeOrder(orderId, paymentResult);

        return new CheckoutResponse(
                finalizedOrder.getId(),
                finalizedOrder.getMerchantOrderId(),
                finalizedOrder.getUserId(),
                finalizedOrder.getTotalAmount(),
                firstShare,
                finalizedOrder.getStatus(),
                paymentResult.externalTxId(),
                paymentResult.cardToken(),
                finalizedOrder.getCreatedAt()
        );
    }

    public BnplOrderEntity createInitialOrder(UUID orderId, CheckoutRequest request) {
        return transactionService.createInitialOrder(orderId, request);
    }

    public BnplOrderEntity finalizeOrder(UUID orderId, PaymentServiceClient.PaymentResult paymentResult) {
        return transactionService.finalizeOrder(orderId, paymentResult);
    }

    private BigDecimal calculateFirstShare(BigDecimal totalAmount) {
        return totalAmount.multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
    }
}
