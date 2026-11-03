package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashUtilTest {

    @Test
    void generateKeyMatchesSortedConcatenationBehavior() {
        String explicit = HashUtil.generateKey("api", "https://base", "openai");
        String swapped = HashUtil.generateKey("https://base", "api", "openai");

        assertThat(HashUtil.generateKey("api", "https://base")).isEqualTo(explicit);
        assertThat(swapped).isEqualTo(explicit);
        assertThat(explicit).hasSize(64);
        assertThat(explicit).isEqualTo("52137c2e3c345840ab1848f0242b4bc2f5b40b7eb21d26e462ec81e5750451d7");
    }
}
