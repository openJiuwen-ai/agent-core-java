package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Mirrors Python's {@code AudioTranscriptionTool} in {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public class AudioTranscriptionTool extends AbstractHarnessTool {

    public final AudioModelConfig audioModelConfig;

    public AudioTranscriptionTool(AudioModelConfig audioModelConfig) {
        super(toolCard("audio_transcription", "audio_transcription", "Transcribe audio content."), null);
        this.audioModelConfig = audioModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String audioPathOrUrl = String.valueOf(inputs.getOrDefault("audio_path_or_url", ""));
        String text = invokeAudioTranscription(audioModelConfig, audioPathOrUrl);
        return new ToolOutput(true, Map.of("text", text, "model", audioModelConfig.getTranscriptionModel()), null);
    }

    protected String invokeAudioTranscription(AudioModelConfig config, String audioPathArg) {
        return "";
    }
}
