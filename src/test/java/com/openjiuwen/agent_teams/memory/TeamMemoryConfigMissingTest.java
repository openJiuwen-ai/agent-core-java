/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.EmbeddingProviders;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's module-level tests in
 * {@code tests/unit_tests/core/memory/team/test_config.py}.
 */
class TeamMemoryConfigMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void teamMemoryConfigExcludedFieldsAbsentFromModelDump() {
        EmbeddingConfig embedding = EmbeddingConfig.builder()
                .modelName("m")
                .baseUrl("http://localhost")
                .build();
        TeamMemoryConfig config = TeamMemoryConfig.builder()
                .enabled(true)
                .embeddingConfig(embedding)
                .parentWorkspacePath("/parent/ws")
                .teamMemoryDir("/team/mem")
                .build();

        Map<String, Object> flat = OBJECT_MAPPER.convertValue(config, new TypeReference<>() {
        });
        String jsonFlat = writeJson(config);

        assertThat(flat).doesNotContainKeys("embeddingConfig", "parentWorkspacePath", "teamMemoryDir");
        assertThat(jsonFlat)
                .doesNotContain("embeddingConfig")
                .doesNotContain("parentWorkspacePath")
                .doesNotContain("teamMemoryDir");
    }

    @Test
    void resolveEmbeddingConfigPrefersConfigOverEnv() {
        EmbeddingConfig explicit = EmbeddingConfig.builder()
                .modelName("explicit")
                .baseUrl("http://explicit")
                .build();
        TeamMemoryConfig config = TeamMemoryConfig.builder()
                .embeddingConfig(explicit)
                .build();

        assertThat(TeamMemoryConfig.resolveEmbeddingConfig(config)).isSameAs(explicit);
    }

    @Test
    void resolveEmbeddingConfigFallsBackToEnvWhenNoEmbeddingInConfig() {
        EmbeddingConfig directEnv = EmbeddingProviders.resolveEmbeddingConfigFromEnv(null, null, null);

        assertThat(TeamMemoryConfig.resolveEmbeddingConfig(null)).isEqualTo(directEnv);
        assertThat(TeamMemoryConfig.resolveEmbeddingConfig(TeamMemoryConfig.builder().build())).isEqualTo(directEnv);
    }

    private static String writeJson(TeamMemoryConfig config) {
        try {
            return OBJECT_MAPPER.writeValueAsString(config);
        } catch (Exception exception) {
            throw new AssertionError("TeamMemoryConfig JSON serialization failed", exception);
        }
    }
}
