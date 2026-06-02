package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import java.util.Map;

/**
 * Mirrors Python's {@code AudioMetadataTool} in {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public class AudioMetadataTool extends AbstractHarnessTool {

    public final AudioModelConfig audioModelConfig;

    public AudioMetadataTool() {
        this(null);
    }

    public AudioMetadataTool(AudioModelConfig audioModelConfig) {
        super(toolCard("audio_metadata", "audio_metadata", "Inspect audio metadata."), null);
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
            Map<String, Object> data = AudioSupport.callWithRetries(config,
                    () -> invokeAudioMetadata(config, resolvedPath));
            return new ToolOutput(true, data, null);
        } catch (Exception exc) {
            return new ToolOutput(false, null, exc.getMessage());
        } finally {
            AudioSupport.deleteIfTemporary(resolved);
        }
    }

    protected Map<String, Object> invokeAudioMetadata(AudioModelConfig config, String audioPath) throws Exception {
        return AudioSupport.invokeAudioMetadata(config, audioPath);
    }

    protected double extractDurationSeconds(String audioPath) {
        return AudioSupport.getAudioDuration(audioPath);
    }
}
