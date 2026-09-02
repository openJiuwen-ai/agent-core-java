/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Public class AudioTranscriptionTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class AudioTranscriptionTool {
    /**
     * audioModelConfig.
     * 
     * @since 0.1.7
     */
    public final AudioModelConfig audioModelConfig;

    private final TranscriptionInvoker invoker;

    /**
     * Public interface TranscriptionInvoker used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface TranscriptionInvoker {
        /**
         * invoke.
         * 
         * @param config config
         * @param audioPath audioPath
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        String invoke(AudioModelConfig config, String audioPath) throws Exception;
    }

    /**
     * AudioTranscriptionTool.
     * 
     * @param audioModelConfig audioModelConfig
     * @since 0.1.7
     */
    public AudioTranscriptionTool(AudioModelConfig audioModelConfig) {
        this(audioModelConfig, (config, audioPath) -> "");
    }

    /**
     * AudioTranscriptionTool.
     * 
     * @param audioModelConfig audioModelConfig
     * @param invoker invoker
     * @since 0.1.7
     */
    public AudioTranscriptionTool(AudioModelConfig audioModelConfig, TranscriptionInvoker invoker) {
        this.audioModelConfig = audioModelConfig;
        this.invoker = invoker;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(Map<String, Object> inputs) {
        if (audioModelConfig == null) {
            return ToolOutput.builder().success(false).error("Audio model config is not set.").build();
        }
        try {
            String audioPath = String.valueOf(inputs.get("audio_path_or_url"));
            String text = invoker.invoke(audioModelConfig, audioPath);
            return ToolOutput.builder().success(true)
                    .data(Map.of("text", text, "model", audioModelConfig.getTranscriptionModel())).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
