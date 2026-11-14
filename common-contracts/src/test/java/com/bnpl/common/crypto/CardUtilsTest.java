package com.bnpl.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardUtilsTest {

    private static final String PEPPER = "pep_1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p";

    @Test
    void shouldMaskStandard16DigitCard() {
        String pan = "4111 2222 3333 4444";
        String masked = CardUtils.maskCard(pan);

        assertThat(masked).isEqualTo("411122******4444");
    }

    @Test
    void shouldMaskHyphenatedCard() {
        String pan = "5500-0000-0000-0004";
        String masked = CardUtils.maskCard(pan);

        assertThat(masked).isEqualTo("550000******0004");
    }

    @Test
    void shouldCalculateConsistentBlindHash() {
        String pan1 = "4111 2222 3333 4444";
        String pan2 = "4111222233334444";

        String hash1 = CardUtils.calculateBlindHash(pan1, PEPPER);
        String hash2 = CardUtils.calculateBlindHash(pan2, PEPPER);

        assertThat(hash1).isNotBlank();
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldProduceDifferentHashForDifferentCard() {
        String hash1 = CardUtils.calculateBlindHash("4111222233334444", PEPPER);
        String hash2 = CardUtils.calculateBlindHash("4111222233335555", PEPPER);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldGenerateCardTokenWithPrefix() {
        String token = CardUtils.generateCardToken();

        assertThat(token).startsWith("tok_");
        assertThat(token.length()).isGreaterThan(10);
    }

    @Test
    void shouldHandleNullInputs() {
        assertThat(CardUtils.maskCard(null)).isNull();
        assertThatThrownBy(() -> CardUtils.calculateBlindHash(null, PEPPER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
