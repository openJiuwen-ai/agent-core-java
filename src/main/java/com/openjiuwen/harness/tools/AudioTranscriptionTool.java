package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Mirrors Python's {@code AudioTranscriptionTool} in {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public class AudioTranscriptionTool extends AbstractHarnessTool {

    public final AudioModelConfig audioModelConfig;

    public AudioTranscriptionTool() {
        this(null);
    }

    public AudioTranscriptionTool(AudioModelConfig audioModelConfig) {
        super(toolCard("audio_transcription", "audio_transcription", "Transcribe audio content."), null);
        this.audioModelConfig = audioModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        AudioSupport.ResolvedAudioPath resolved = null;
        try {
            AudioModelConfig config = AudioSupport.requireAudioModelConfig(audioModelConfig);
            String audioPathOrUrl = String.valueOf(inputs.getOrDefault("audio_path_or_url", ""));
            resolved = AudioSupport.resolveAudioPath(audioPathOrUrl, config);
            String resolvedPath = resolved.path().toString();
            String text = AudioSupport.callWithRetries(config,
                    () -> invokeAudioTranscription(config, resolvedPath));
            return new ToolOutput(true, Map.of("text", text, "model", config.getTranscriptionModel()), null);
        } catch (Exception exc) {
            return new ToolOutput(false, null, exc.getMessage());
        } finally {
            AudioSupport.deleteIfTemporary(resolved);
        }
    }

    protected String invokeAudioTranscription(AudioModelConfig config, String audioPathArg) throws Exception {
        return AudioSupport.invokeAudioTranscription(config, audioPathArg);
    }
}
