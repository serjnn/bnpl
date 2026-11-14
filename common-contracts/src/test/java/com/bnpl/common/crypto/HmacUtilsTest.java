package com.bnpl.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacUtilsTest {

    private static final String SECRET = "whsec_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c";
    private static final String PAYLOAD = "{\"payment_id\":\"123\",\"status\":\"SUCCEEDED\"}";

    @Test
    void shouldComputeConsistentHmac() {
        String signature1 = HmacUtils.computeHmacHex(PAYLOAD, SECRET);
        String signature2 = HmacUtils.computeHmacHex(PAYLOAD, SECRET);

        assertThat(signature1).isNotBlank();
        assertThat(signature1).isEqualTo(signature2);
    }

    @Test
    void shouldVerifyValidSignature() {
        String signature = HmacUtils.computeHmacHex(PAYLOAD, SECRET);
        boolean isValid = HmacUtils.verifyHmacHex(PAYLOAD, SECRET, signature);

        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectInvalidSignature() {
        String signature = HmacUtils.computeHmacHex(PAYLOAD, SECRET);
        boolean isValid = HmacUtils.verifyHmacHex(PAYLOAD + "_tampered", SECRET, signature);

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectWrongSecret() {
        String signature = HmacUtils.computeHmacHex(PAYLOAD, SECRET);
        boolean isValid = HmacUtils.verifyHmacHex(PAYLOAD, "wrong_secret", signature);

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldHandleNullInputsGracefully() {
        assertThatThrownBy(() -> HmacUtils.computeHmacHex(null, SECRET))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(HmacUtils.verifyHmacHex(null, SECRET, "abc")).isFalse();
        assertThat(HmacUtils.verifyHmacHex(PAYLOAD, null, "abc")).isFalse();
        assertThat(HmacUtils.verifyHmacHex(PAYLOAD, SECRET, null)).isFalse();
    }
}
