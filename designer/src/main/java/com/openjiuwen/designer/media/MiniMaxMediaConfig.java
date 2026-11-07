/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

/**
 * MiniMax connection settings.
 *
 * @param apiBase API base, normally {@code https://api.minimax.cn/v1}
 * @param apiKey API key
 * @param imageModel image model, normally {@code image-01}
 * @param videoModel video model, normally {@code MiniMax-H3}
 * @param timeoutSeconds request timeout
 * @param provider core model-client provider, normally {@code OpenAI}
 * @param verifySsl whether to verify the endpoint TLS certificate
 * @since 2026-11-06
 */
public record MiniMaxMediaConfig(
        String apiBase,
        String apiKey,
        String imageModel,
        String videoModel,
        int timeoutSeconds,
        String provider,
        boolean verifySsl) {

    /** Convenience constructor: OpenAI-compatible provider with TLS verification enabled. */
    public MiniMaxMediaConfig(String apiBase, String apiKey, String imageModel, String videoModel,
            int timeoutSeconds) {
        this(apiBase, apiKey, imageModel, videoModel, timeoutSeconds, "OpenAI", true);
    }

    public MiniMaxMediaConfig {
        apiBase = apiBase == null ? "" : apiBase.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
        imageModel = imageModel == null || imageModel.isBlank() ? "image-01" : imageModel.strip();
        videoModel = videoModel == null || videoModel.isBlank() ? "MiniMax-H3" : videoModel.strip();
        timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
        provider = provider == null || provider.isBlank() ? "OpenAI" : provider.strip();
    }

    /**
     * Whether the slot is configured.
     *
     * @return true when base and key are present
     */
    public boolean isConfigured() {
        return !apiBase.isBlank() && !apiKey.isBlank();
    }
}
