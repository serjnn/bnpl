package com.bnpl.payment.service;

import com.bnpl.payment.dto.ChargeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final String RESPONSE_PREFIX = "idempotency:response:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Attempts to acquire an atomic idempotency lock in Redis.
     * Returns true if lock was acquired, false if another request is in progress.
     */
    public boolean acquireLock(String idempotencyKey) {
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_PREFIX + idempotencyKey, "PROCESSING", LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Redis unavailable for lock acquisition on key {}: {}", idempotencyKey, e.getMessage());
            // Fallback: If Redis is unavailable in local testing without Redis, return true
            return true;
        }
    }

    /**
     * Caches the completed charge response in Redis for subsequent duplicate calls.
     */
    public void saveResponse(String idempotencyKey, ChargeResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(RESPONSE_PREFIX + idempotencyKey, json, RESPONSE_TTL);
            // Release the in-flight lock
            redisTemplate.delete(LOCK_PREFIX + idempotencyKey);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ChargeResponse for key {}", idempotencyKey, e);
        } catch (Exception e) {
            log.warn("Redis unavailable for saving response on key {}: {}", idempotencyKey, e.getMessage());
        }
    }

    /**
     * Retrieves the cached charge response if previously processed.
     */
    public Optional<ChargeResponse> getCachedResponse(String idempotencyKey) {
        try {
            String json = redisTemplate.opsForValue().get(RESPONSE_PREFIX + idempotencyKey);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, ChargeResponse.class));
            }
        } catch (Exception e) {
            log.warn("Redis unavailable or failed to deserialize cached response for key {}: {}", idempotencyKey, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Releases the lock in case of failure before saving response.
     */
    public void releaseLock(String idempotencyKey) {
        try {
            redisTemplate.delete(LOCK_PREFIX + idempotencyKey);
        } catch (Exception e) {
            log.warn("Failed to release Redis lock for key {}: {}", idempotencyKey, e.getMessage());
        }
    }
}
