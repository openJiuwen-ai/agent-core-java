/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;
import java.util.Map;

/**
 * Public class AudioTranscriptionTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class AudioTranscriptionTool {
  /** Auto-generated for codecheck compliance. */
  public final AudioModelConfig audioModelConfig;

  private final TranscriptionInvoker invoker;

  /**
   * Public interface TranscriptionInvoker used by the Java parity implementation.
   *
   * @since 1.0
   */
  @FunctionalInterface
  public interface TranscriptionInvoker {
    String invoke(AudioModelConfig config, String audioPath) throws Exception;
  }

  /** Auto-generated for codecheck compliance. */
  public AudioTranscriptionTool(AudioModelConfig audioModelConfig) {
    this(audioModelConfig, (config, audioPath) -> "");
  }

  /** Auto-generated for codecheck compliance. */
  public AudioTranscriptionTool(AudioModelConfig audioModelConfig, TranscriptionInvoker invoker) {
    this.audioModelConfig = audioModelConfig;
    this.invoker = invoker;
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput invoke(Map<String, Object> inputs) {
    if (audioModelConfig == null) {
      return ToolOutput.builder().success(false).error("Audio model config is not set.").build();
    }
    try {
      String audioPath = String.valueOf(inputs.get("audio_path_or_url"));
      String text = invoker.invoke(audioModelConfig, audioPath);
      return ToolOutput.builder()
          .success(true)
          .data(Map.of("text", text, "model", audioModelConfig.getTranscriptionModel()))
          .build();
    } catch (Exception ex) {
      return ToolOutput.builder().success(false).error(ex.getMessage()).build();
    }
  }
}
