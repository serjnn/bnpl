package com.bnpl.payment.client;

import com.bnpl.payment.config.AcquiringProperties;
import com.bnpl.payment.dto.MockBankChargeRequest;
import com.bnpl.payment.dto.MockBankChargeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class AcquiringBankClient {

    private static final Logger log = LoggerFactory.getLogger(AcquiringBankClient.class);

    private final RestClient restClient;
    private final AcquiringProperties acquiringProperties;

    public AcquiringBankClient(RestClient restClient, AcquiringProperties acquiringProperties) {
        this.restClient = restClient;
        this.acquiringProperties = acquiringProperties;
    }

    public MockBankChargeResponse charge(MockBankChargeRequest request) {
        log.info("Sending charge request to acquiring gateway: {}", acquiringProperties.gatewayUrl());
        try {
            return restClient.post()
                    .uri(acquiringProperties.gatewayUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MockBankChargeResponse.class);
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error calling acquiring bank: {}", e.getMessage());
            return new MockBankChargeResponse(false, null, "GATEWAY_TIMEOUT");
        } catch (Exception e) {
            log.error("Unexpected error calling acquiring bank: {}", e.getMessage());
            return new MockBankChargeResponse(false, null, "GATEWAY_ERROR: " + e.getMessage());
        }
    }
}
