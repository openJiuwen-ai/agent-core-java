package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.AudioModelConfig;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code AudioMetadataTool} in {@code openjiuwen.harness.tools.multimodal.audio}.
 */
public class AudioMetadataTool extends AbstractHarnessTool {

    public final AudioModelConfig audioModelConfig;

    public AudioMetadataTool(AudioModelConfig audioModelConfig) {
        super(toolCard("audio_metadata", "audio_metadata", "Inspect audio metadata."), null);
        this.audioModelConfig = audioModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String audioPathOrUrl = String.valueOf(inputs.getOrDefault("audio_path_or_url", ""));
        double durationSeconds = extractDurationSeconds(audioPathOrUrl);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("duration_seconds", durationSeconds);
        data.put("identified", false);
        data.put("note", "ACR credentials not configured.");
        return new ToolOutput(true, data, null);
    }

    protected double extractDurationSeconds(String audioPath) throws Exception {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(new File(audioPath))) {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(new File(audioPath));
            long frames = stream.getFrameLength();
            float rate = format.getFormat().getFrameRate();
            return rate > 0 ? frames / rate : 0.0;
        }
    }
}
