/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public class VisionModelConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionModelConfig {
    @Builder.Default
    private String apiKey = "";
    @Builder.Default
    private String baseUrl = "";
    @Builder.Default
    private String model = "google/gemini-2.5-pro";
    @Builder.Default
    private int maxRetries = 3;

    /**
     * fromEnv.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static VisionModelConfig fromEnv() {
        return VisionModelConfig.builder().apiKey(System.getenv().getOrDefault("VISION_API_KEY", ""))
                .baseUrl(System.getenv().getOrDefault("VISION_BASE_URL", ""))
                .model(System.getenv().getOrDefault("VISION_MODEL", "google/gemini-2.5-pro"))
                .maxRetries(parseInt(System.getenv("VISION_MAX_RETRIES"), 3)).build();
    }

    /**
     * parseInt.
     * 
     * @param raw raw
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static int parseInt(String raw, int fallback) {
        try {
            return raw != null ? Integer.parseInt(raw) : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
