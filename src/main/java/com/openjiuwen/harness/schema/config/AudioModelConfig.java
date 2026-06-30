/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class AudioModelConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class AudioModelConfig {
    @Builder.Default
    private String apiKey = "";
    @Builder.Default
    private String baseUrl = "";
    @Builder.Default
    private String transcriptionModel = "";
    @Builder.Default
    private String questionAnsweringModel = "";
    @Builder.Default
    private int maxRetries = 3;
    @Builder.Default
    private String acrAccessKey = "";
    @Builder.Default
    private String acrAccessSecret = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    public static AudioModelConfig fromEnv() {
        return AudioModelConfig.builder()
                .apiKey(System.getenv().getOrDefault("AUDIO_API_KEY", ""))
                .baseUrl(System.getenv().getOrDefault("AUDIO_BASE_URL", ""))
                .transcriptionModel(System.getenv().getOrDefault("AUDIO_TRANSCRIPTION_MODEL", ""))
                .questionAnsweringModel(System.getenv().getOrDefault("AUDIO_QUESTION_ANSWERING_MODEL", ""))
                .maxRetries(parseInt(System.getenv("AUDIO_MAX_RETRIES"), 3))
                .acrAccessKey(System.getenv().getOrDefault("ACR_ACCESS_KEY", ""))
                .acrAccessSecret(System.getenv().getOrDefault("ACR_ACCESS_SECRET", ""))
                .build();
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return raw != null ? Integer.parseInt(raw) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
