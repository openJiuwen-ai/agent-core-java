/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

/**
 * UT-V: maxRetries validation on every configuration entry point: builder,
 * Jackson binding, and the explicit setter.
 *
 * @since 2026-10-20
 */
class ModelClientConfigRetryValidationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builder_negativeMaxRetries_rejected() {
        assertThatThrownBy(() -> ModelClientConfig.builder().maxRetries(-1).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setter_negativeMaxRetries_rejected() {
        ModelClientConfig config = ModelClientConfig.builder().build();
        assertThatThrownBy(() -> config.setMaxRetries(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jackson_negativeMaxRetries_rejectedAtBinding() {
        // The setter validates, so a negative configured value fails deserialization
        assertThatThrownBy(() -> objectMapper.readValue("{\"max_retries\":-2}", ModelClientConfig.class))
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jackson_maxRetries_boundThroughValidatingSetter() throws JsonProcessingException {
        ModelClientConfig config = objectMapper.readValue("{\"max_retries\":5}", ModelClientConfig.class);
        assertThat(config.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void jackson_maxRetries_serializedBackWithSnakeCaseKey() throws JsonProcessingException {
        ModelClientConfig config = ModelClientConfig.builder().maxRetries(4).build();
        String json = objectMapper.writeValueAsString(config);
        assertThat(json).contains("\"max_retries\":4");
    }

    @Test
    void jackson_retrySwitches_boundBySnakeCaseKeys() throws JsonProcessingException {
        ModelClientConfig config = objectMapper.readValue(
            "{\"max_retries\":3,\"retry_on_rate_limit\":false,\"retry_on_temporary\":false}",
            ModelClientConfig.class);
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.isRetryOnRateLimit()).isFalse();
        assertThat(config.isRetryOnTemporary()).isFalse();
    }
}
