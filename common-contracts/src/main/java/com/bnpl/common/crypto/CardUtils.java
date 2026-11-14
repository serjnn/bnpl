package com.bnpl.common.crypto;

import java.util.UUID;

public final class CardUtils {

    private CardUtils() {}

    /**
     * Masks PAN keeping first 6 and last 4 digits (e.g. 411111******1111).
     */
    public static String maskCard(String pan) {
        if (pan == null) {
            return null;
        }
        String cleanPan = pan.replaceAll("[\\s-]", "");
        int length = cleanPan.length();
        if (length < 10) {
            if (length <= 4) {
                return "*".repeat(length);
            }
            return "*".repeat(length - 4) + cleanPan.substring(length - 4);
        }

        String prefix = cleanPan.substring(0, 6);
        String suffix = cleanPan.substring(length - 4);
        String masked = "*".repeat(length - 10);
        return prefix + masked + suffix;
    }

    /**
     * Generates an HMAC-SHA256 blind hash of the clean PAN using the provided pepper.
     */
    public static String calculateBlindHash(String pan, String pepper) {
        if (pan == null || pepper == null) {
            throw new IllegalArgumentException("PAN and pepper must not be null");
        }
        String cleanPan = pan.replaceAll("[\\s-]", "");
        return HmacUtils.computeHmacHex(cleanPan, pepper);
    }

    /**
     * Generates a unique opaque card token for safe persistence.
     */
    public static String generateCardToken() {
        return "tok_" + UUID.randomUUID().toString().replace("-", "");
    }
}
