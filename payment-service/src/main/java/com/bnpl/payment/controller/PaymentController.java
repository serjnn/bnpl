package com.bnpl.payment.controller;

import com.bnpl.payment.dto.ChargeRequest;
import com.bnpl.payment.dto.ChargeResponse;
import com.bnpl.payment.repository.PaymentIntentRepository;
import com.bnpl.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentIntentRepository paymentIntentRepository;

    public PaymentController(PaymentService paymentService, PaymentIntentRepository paymentIntentRepository) {
        this.paymentService = paymentService;
        this.paymentIntentRepository = paymentIntentRepository;
    }

    @PostMapping("/charge")
    public ResponseEntity<ChargeResponse> charge(@Valid @RequestBody ChargeRequest request) {
        ChargeResponse response = paymentService.processCharge(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChargeResponse> getPaymentIntent(@PathVariable UUID id) {
        return paymentIntentRepository.findById(id)
                .map(entity -> new ChargeResponse(
                        entity.getId(),
                        entity.getOrderId(),
                        entity.getInstallmentId(),
                        entity.getAmount(),
                        entity.getStatus(),
                        entity.getExternalTxId(),
                        entity.getCardMask(),
                        entity.getCardToken(),
                        entity.getCreatedAt()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
