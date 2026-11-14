package com.bnpl.common.constants;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static final String ORDERS_EVENTS = "bnpl.orders.events";
    public static final String PAYMENTS_COMMANDS = "bnpl.payments.commands";
    public static final String PAYMENTS_RESULTS = "bnpl.payments.results";
    public static final String DLQ = "bnpl.dlq";
}
