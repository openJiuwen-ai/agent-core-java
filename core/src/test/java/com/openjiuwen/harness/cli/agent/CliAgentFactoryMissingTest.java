/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import com.openjiuwen.harness.schema.DeepAgentConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/cli/unit/test_factory.py}.</p>
 */
class CliAgentFactoryMissingTest {

    @Test
    void testLoadVisionConfigFallbackToMainModel() {
        DeepAgentConfig.VisionModelConfig result = CliAgentFactory.loadVisionConfig(cliCfg(), Map.of());

        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).isEqualTo("sk-main-key");
        assertThat(result.getBaseUrl()).isEqualTo("https://main.example.com/v1");
    }

    @Test
    void testLoadVisionConfigUsesEnvWhenSet() {
        DeepAgentConfig.VisionModelConfig result = CliAgentFactory.loadVisionConfig(cliCfg(), Map.of(
                "VISION_API_KEY", "sk-vision-key",
                "VISION_BASE_URL", "https://vision.example.com/v1"
        ));

        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).isEqualTo("sk-vision-key");
        assertThat(result.getBaseUrl()).isEqualTo("https://vision.example.com/v1");
    }

    @Test
    void testLoadAudioConfigFallbackToMainModel() {
        DeepAgentConfig.AudioModelConfig result = CliAgentFactory.loadAudioConfig(cliCfg(), Map.of());

        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).isEqualTo("sk-main-key");
        assertThat(result.getBaseUrl()).isEqualTo("https://main.example.com/v1");
    }

    @Test
    void testLoadAudioConfigUsesEnvWhenSet() {
        DeepAgentConfig.AudioModelConfig result = CliAgentFactory.loadAudioConfig(cliCfg(), Map.of(
                "AUDIO_API_KEY", "sk-audio-key",
                "AUDIO_BASE_URL", "https://audio.example.com/v1"
        ));

        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).isEqualTo("sk-audio-key");
        assertThat(result.getBaseUrl()).isEqualTo("https://audio.example.com/v1");
    }

    private static Map<String, Object> cliCfg() {
        return Map.of(
                "api_key", "sk-main-key",
                "api_base", "https://main.example.com/v1"
        );
    }
}
