package com.openjiuwen.core.memory.config;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryEngineConfigTest {

    @Test
    void builderExposesForbiddenVariablesAndCryptoKeyDefaults() {
        MemoryEngineConfig config = MemoryEngineConfig.builder().build();

        assertEquals("", config.getForbiddenVariables());
        assertArrayEquals(new byte[0], config.getCryptoKey());
    }

    @Test
    void validateCryptoKeyRejectsNonAesLength() {
        MemoryEngineConfig config = MemoryEngineConfig.builder()
                .build();

        BaseError error = assertThrows(BaseError.class, () -> config.setCryptoKey("short-key".getBytes()));
        assertEquals(StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR, error.getStatus());
    }

    @Test
    void builderRejectsNonAesLengthCryptoKeyDuringConstruction() {
        BaseError error = assertThrows(BaseError.class, () -> MemoryEngineConfig.builder()
                .cryptoKey("short-key".getBytes())
                .build());

        assertEquals(StatusCode.MEMORY_SET_CONFIG_EXECUTION_ERROR, error.getStatus());
    }
}
