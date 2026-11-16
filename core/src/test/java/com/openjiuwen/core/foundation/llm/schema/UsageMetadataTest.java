/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

/**
 * Verifies token usage field presence and wire representation.
 */
class UsageMetadataTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void unsetTokenFields_remainMissingInsteadOfZero() throws Exception {
        UsageMetadata usage = UsageMetadata.builder().build();

        assertThat(usage.getInputTokens()).isZero();
        assertThat(usage.getOutputTokens()).isZero();
        assertThat(usage.getTotalTokens()).isZero();
        assertThat(usage.getCacheTokens()).isZero();
        assertThat(usage.getInputTokensOrNull()).isNull();
        assertThat(usage.getOutputTokensOrNull()).isNull();
        assertThat(usage.getTotalTokensOrNull()).isNull();
        assertThat(usage.getCacheTokensOrNull()).isNull();
        assertThat(usage.modelDump()).doesNotContainKeys(
                "input_tokens", "output_tokens", "total_tokens", "cache_tokens");
        assertThat(MAPPER.writeValueAsString(usage)).doesNotContain(
                "input_tokens", "output_tokens", "total_tokens", "cache_tokens");

        UsageMetadata restored = MAPPER.readValue("{}", UsageMetadata.class);
        assertThat(restored.modelDump()).doesNotContainKeys(
                "input_tokens", "output_tokens", "total_tokens", "cache_tokens");
    }

    @Test
    void explicitZeroTokenFields_remainPresentThroughRoundTrip() throws Exception {
        UsageMetadata usage = UsageMetadata.builder()
                .inputTokens(0)
                .outputTokens(0)
                .totalTokens(0)
                .cacheTokens(0)
                .build();

        assertThat(usage.modelDump()).containsEntry("input_tokens", 0)
                .containsEntry("output_tokens", 0)
                .containsEntry("total_tokens", 0)
                .containsEntry("cache_tokens", 0);
        assertThat(usage.getInputTokensOrNull()).isZero();
        assertThat(usage.getOutputTokensOrNull()).isZero();
        assertThat(usage.getTotalTokensOrNull()).isZero();
        assertThat(usage.getCacheTokensOrNull()).isZero();

        UsageMetadata restored = MAPPER.readValue(MAPPER.writeValueAsString(usage), UsageMetadata.class);
        assertThat(restored.modelDump()).containsEntry("input_tokens", 0)
                .containsEntry("output_tokens", 0)
                .containsEntry("total_tokens", 0)
                .containsEntry("cache_tokens", 0);
        assertThat(restored.getInputTokensOrNull()).isZero();
        assertThat(restored.getOutputTokensOrNull()).isZero();
        assertThat(restored.getTotalTokensOrNull()).isZero();
        assertThat(restored.getCacheTokensOrNull()).isZero();
    }

    @Test
    void settersRetainMissingAndExplicitZeroTokenFields() throws Exception {
        UsageMetadata usage = new UsageMetadata();
        usage.setInputTokens(null);
        usage.setOutputTokens(0);

        assertThat(usage.getInputTokensOrNull()).isNull();
        assertThat(usage.getOutputTokensOrNull()).isZero();

        UsageMetadata restored = MAPPER.readValue(MAPPER.writeValueAsString(usage), UsageMetadata.class);
        assertThat(restored.getInputTokensOrNull()).isNull();
        assertThat(restored.getOutputTokensOrNull()).isZero();
        assertThat(restored.modelDump()).doesNotContainKey("input_tokens")
                .containsEntry("output_tokens", 0);
    }
}
