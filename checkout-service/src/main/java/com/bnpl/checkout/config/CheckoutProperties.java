package com.bnpl.checkout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bnpl.checkout")
public record CheckoutProperties(
        String paymentServiceUrl,
        int timeoutMs
) {}
