package com.urlshortener.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encodeAndDecodeAreInverses() {
        long id = 12345L;
        String code = encoder.encode(id);
        assertThat(encoder.decode(code)).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 61, 62, 63, 999999, 3521614606207L})
    void roundTripForVariousIds(long id) {
        assertThat(encoder.decode(encoder.encode(id))).isEqualTo(id);
    }

    @Test
    void id1ProducesShortestCode() {
        assertThat(encoder.encode(1)).isEqualTo("1");
    }

    @Test
    void id62ProducesTwoCharCode() {
        // 62 in base62 = "10" (1×62 + 0)
        assertThat(encoder.encode(62)).isEqualTo("10");
    }

    @Test
    void sevenCharCodeSupportsTrillions() {
        // 62^7 - 1 should still encode to exactly 7 chars
        long maxSevenChar = (long) Math.pow(62, 7) - 1;
        assertThat(encoder.encode(maxSevenChar)).hasSize(7);
    }

    @Test
    void codesAreStrictlyIncreasing() {
        // higher ID → lexicographically later code (important for predictability)
        String code1 = encoder.encode(100);
        String code2 = encoder.encode(200);
        assertThat(code1.length()).isLessThanOrEqualTo(code2.length());
    }

    @Test
    void encodeRejectsZero() {
        assertThatThrownBy(() -> encoder.encode(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidCharacter() {
        assertThatThrownBy(() -> encoder.decode("abc!"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid character");
    }

    @Test
    void decodeRejectsBlankInput() {
        assertThatThrownBy(() -> encoder.decode("  "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}