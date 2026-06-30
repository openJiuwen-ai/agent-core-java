/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;
import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.util.List;

/**
 * Auto-generated for codecheck compliance.
 */
public final class MultimodalToolFactory {
    private MultimodalToolFactory() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> createVisionTools(VisionModelConfig config) {
        return List.of(new ImageOCRTool(config), new VisualQuestionAnsweringTool(config));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Object> createAudioTools(AudioModelConfig config) {
        return List.of(
                new AudioTranscriptionTool(config),
                new AudioQuestionAnsweringTool(config),
                new AudioMetadataTool(config)
        );
    }
}
