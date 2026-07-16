/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;
import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.util.List;

/**
 * MultimodalToolFactory.
 * 
 * @since 0.1.7
 */
public final class MultimodalToolFactory {
    /**
     * MultimodalToolFactory.
     * 
     * @since 0.1.7
     */
    private MultimodalToolFactory() {
    }

    /**
     * createVisionTools.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> createVisionTools(VisionModelConfig config) {
        return List.of(new ImageOCRTool(config), new VisualQuestionAnsweringTool(config));
    }

    /**
     * createAudioTools.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> createAudioTools(AudioModelConfig config) {
        return List.of(new AudioTranscriptionTool(config), new AudioQuestionAnsweringTool(config),
                new AudioMetadataTool(config));
    }
}
