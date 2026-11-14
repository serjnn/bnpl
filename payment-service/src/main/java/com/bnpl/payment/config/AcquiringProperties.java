package com.bnpl.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bnpl.acquiring")
public record AcquiringProperties(
        String gatewayUrl,
        int timeoutMs
) {}
