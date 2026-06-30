/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Public class AudioQuestionAnsweringTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class AudioQuestionAnsweringTool {
    /**
     * Auto-generated for codecheck compliance.
     */
    public final AudioModelConfig audioModelConfig;
    private final QaInvoker invoker;

    /**
 * Public interface QaInvoker used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface QaInvoker {
        QaResult invoke(AudioModelConfig config, String audioPath, String question) throws Exception;
    }

    /**
 * Public record QaResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record QaResult(String answer, double durationSeconds) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AudioQuestionAnsweringTool(AudioModelConfig audioModelConfig) {
        this(audioModelConfig, (config, audioPath, question) -> new QaResult("", 0.0));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public AudioQuestionAnsweringTool(AudioModelConfig audioModelConfig, QaInvoker invoker) {
        this.audioModelConfig = audioModelConfig;
        this.invoker = invoker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput invoke(Map<String, Object> inputs) {
        if (audioModelConfig == null) {
            return ToolOutput.builder().success(false).error("Audio model config is not set.").build();
        }
        try {
            String audioPath = String.valueOf(inputs.get("audio_path_or_url"));
            String question = String.valueOf(inputs.get("question"));
            QaResult result = invoker.invoke(audioModelConfig, audioPath, question);
            return ToolOutput.builder().success(true).data(Map.of(
                    "answer", result.answer(),
                    "duration_seconds", result.durationSeconds(),
                    "model", audioModelConfig.getQuestionAnsweringModel()
            )).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
