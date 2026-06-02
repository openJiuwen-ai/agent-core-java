package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.List;

/**
 * Factory for audio harness tools.
 *
 * <p>Mirrors Python's {@code create_audio_tools} in
 * {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public final class AudioTools {

    private AudioTools() {
    }

    public static List<Tool> createAudioTools(AudioModelConfig audioModelConfig) {
        return createAudioTools("cn", audioModelConfig, null);
    }

    public static List<Tool> createAudioTools(String language, AudioModelConfig audioModelConfig) {
        return createAudioTools(language, audioModelConfig, null);
    }

    public static List<Tool> createAudioTools(String language, AudioModelConfig audioModelConfig, String agentId) {
        return List.of(
                new AudioTranscriptionTool(audioModelConfig),
                new AudioQuestionAnsweringTool(audioModelConfig),
                new AudioMetadataTool(audioModelConfig)
        );
    }
}
