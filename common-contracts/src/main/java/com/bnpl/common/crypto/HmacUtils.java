package com.bnpl.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class HmacUtils {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacUtils() {}

    /**
     * Computes HMAC-SHA256 signature and returns it as a lowercase hex string.
     */
    public static String computeHmacHex(String data, String secret) {
        if (data == null || secret == null) {
            throw new IllegalArgumentException("Data and secret must not be null");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    /**
     * Verifies the signature in constant time to prevent timing attacks.
     */
    public static boolean verifyHmacHex(String data, String secret, String expectedHexSignature) {
        if (data == null || secret == null || expectedHexSignature == null) {
            return false;
        }
        try {
            String actualHexSignature = computeHmacHex(data, secret);
            byte[] actualBytes = actualHexSignature.getBytes(StandardCharsets.UTF_8);
            byte[] expectedBytes = expectedHexSignature.toLowerCase().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(actualBytes, expectedBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
