package com.bnpl.schedule.listener;

import com.bnpl.common.constants.KafkaTopics;
import com.bnpl.common.events.OrderAuthorizedEvent;
import com.bnpl.schedule.service.ScheduleCreationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderAuthorizedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAuthorizedListener.class);

    private final ScheduleCreationService scheduleCreationService;
    private final ObjectMapper objectMapper;

    public OrderAuthorizedListener(ScheduleCreationService scheduleCreationService, ObjectMapper objectMapper) {
        this.scheduleCreationService = scheduleCreationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDERS_EVENTS, groupId = "schedule-service-orders-group")
    public void onOrderAuthorized(String messagePayload) {
        log.info("Received order event: {}", messagePayload);
        try {
            OrderAuthorizedEvent event = objectMapper.readValue(messagePayload, OrderAuthorizedEvent.class);
            scheduleCreationService.createSchedule(event);
        } catch (Exception e) {
            log.error("Failed to process OrderAuthorizedEvent: {}", e.getMessage(), e);
        }
    }
}
