package com.bnpl.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bnpl.security")
public record PaymentProperties(
        String webhookSecret,
        String blindIndexPepper
) {}
