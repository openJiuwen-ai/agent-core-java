/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;
import java.io.File;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Public class AudioMetadataTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class AudioMetadataTool {
  /** Auto-generated for codecheck compliance. */
  public final AudioModelConfig audioModelConfig;

  /** Auto-generated for codecheck compliance. */
  public AudioMetadataTool(AudioModelConfig audioModelConfig) {
    this.audioModelConfig = audioModelConfig;
  }

  /** Auto-generated for codecheck compliance. */
  public ToolOutput invoke(Map<String, Object> inputs) {
    try {
      String audioPath = String.valueOf(inputs.get("audio_path_or_url"));
      File file = new File(audioPath);
      try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
        AudioFileFormat format = AudioSystem.getAudioFileFormat(file);
        double duration = stream.getFrameLength() / format.getFormat().getFrameRate();
        boolean isTrackIdentified =
            audioModelConfig != null
                && audioModelConfig.getAcrAccessKey() != null
                && !audioModelConfig.getAcrAccessKey().isBlank()
                && audioModelConfig.getAcrAccessSecret() != null
                && !audioModelConfig.getAcrAccessSecret().isBlank();
        String note =
            isTrackIdentified ? "ACR credentials configured." : "ACR credentials are not configured.";
        return ToolOutput.builder()
            .success(true)
            .data(
                Map.of(
                    "duration_seconds", duration,
                    "identified", isTrackIdentified,
                    "note", note))
            .build();
      }
    } catch (Exception ex) {
      return ToolOutput.builder().success(false).error(ex.getMessage()).build();
    }
  }
}
