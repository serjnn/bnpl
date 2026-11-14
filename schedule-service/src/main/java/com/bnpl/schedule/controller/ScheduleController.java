package com.bnpl.schedule.controller;

import com.bnpl.schedule.domain.PaymentScheduleEntity;
import com.bnpl.schedule.dto.InstallmentResponse;
import com.bnpl.schedule.dto.ScheduleResponse;
import com.bnpl.schedule.repository.PaymentScheduleRepository;
import com.bnpl.schedule.service.NightlyBillingScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/schedules")
public class ScheduleController {

    private final PaymentScheduleRepository scheduleRepository;
    private final NightlyBillingScheduler nightlyBillingScheduler;

    public ScheduleController(PaymentScheduleRepository scheduleRepository,
                              NightlyBillingScheduler nightlyBillingScheduler) {
        this.scheduleRepository = scheduleRepository;
        this.nightlyBillingScheduler = nightlyBillingScheduler;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ScheduleResponse> getScheduleByOrderId(@PathVariable UUID orderId) {
        return scheduleRepository.findByOrderIdWithInstallments(orderId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/billing/run")
    public ResponseEntity<Map<String, Object>> triggerBilling() {
        int processedCount = nightlyBillingScheduler.executeBillingJob();
        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "processedInstallments", processedCount
        ));
    }

    private ScheduleResponse toResponse(PaymentScheduleEntity schedule) {
        List<InstallmentResponse> installmentResponses = schedule.getInstallments().stream()
                .sorted(Comparator.comparingInt(i -> i.getInstallmentNumber()))
                .map(i -> new InstallmentResponse(
                        i.getId(),
                        i.getInstallmentNumber(),
                        i.getAmount(),
                        i.getDueDate(),
                        i.getStatus(),
                        i.getVersion()
                ))
                .toList();

        return new ScheduleResponse(
                schedule.getId(),
                schedule.getOrderId(),
                schedule.getUserId(),
                schedule.getTotalAmount(),
                schedule.getRemainingDebt(),
                schedule.getCardToken(),
                schedule.getStatus(),
                schedule.getVersion(),
                schedule.getCreatedAt(),
                installmentResponses
        );
    }
}
