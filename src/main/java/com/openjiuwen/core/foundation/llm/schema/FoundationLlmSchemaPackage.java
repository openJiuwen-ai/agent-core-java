/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

/**
 * Package bridge for the LLM schema exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/foundation/llm/schema/__init__.py}.
 */
public final class FoundationLlmSchemaPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/llm/schema/__init__.py";
    public static final Class<ImageGenerationResponse> IMAGE_GENERATION_RESPONSE = ImageGenerationResponse.class;
    public static final Class<AudioGenerationResponse> AUDIO_GENERATION_RESPONSE = AudioGenerationResponse.class;
    public static final Class<VideoGenerationResponse> VIDEO_GENERATION_RESPONSE = VideoGenerationResponse.class;

    private FoundationLlmSchemaPackage() {
    }
}
