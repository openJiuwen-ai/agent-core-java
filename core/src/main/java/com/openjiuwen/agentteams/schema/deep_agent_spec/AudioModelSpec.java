/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable mirror of AudioModelConfig.
 * Mirrors Python AudioModelSpec.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioModelSpec {
    @Builder.Default
    private String apiKey = "";
    @Builder.Default
    private String baseUrl = "https://api.openai.com/v1";
    @Builder.Default
    private String transcriptionModel = "whisper-1";
    @Builder.Default
    private String questionAnsweringModel = "gpt-4o";
    @Builder.Default
    private int maxRetries = 3;
    @Builder.Default
    private int httpTimeout = 600;
    @Builder.Default
    private long maxAudioBytes = 25L * 1024 * 1024;
    @Builder.Default
    private String acrAccessKey = "";
    @Builder.Default
    private String acrAccessSecret = "";
    @Builder.Default
    private String acrBaseUrl = "";
}
