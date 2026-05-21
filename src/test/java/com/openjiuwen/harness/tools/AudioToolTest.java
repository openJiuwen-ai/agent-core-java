package com.openjiuwen.harness.tools;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.schema.config.AudioModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's test_audio_tool.py.
 * Tests audio tool registration and invocation.
 */
@Tag("system-test")
class AudioToolTest {

    private static void writeTestWav(Path path, int durationSeconds) throws Exception {
        float sampleRate = 16000f;
        int numFrames = (int) sampleRate * durationSeconds;
        byte[] pcmData = new byte[numFrames * 2];
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        AudioInputStream ais = new AudioInputStream(bais, format, numFrames);
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, path.toFile());
    }

    @Test
    void testCreateAudioToolsRegisterAndInvoke(@TempDir Path tempDir) throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 1);

        AudioModelConfig audioModelConfig = new AudioModelConfig();
        audioModelConfig.setApiKey("test-key");
        audioModelConfig.setBaseUrl("https://example.com/v1");
        audioModelConfig.setQuestionAnsweringModel("mock-audio-qa");

        AudioQuestionAnsweringTool qaTool = new AudioQuestionAnsweringTool(audioModelConfig) {
            @Override
            protected AudioQaResult invokeAudioQuestionAnswering(AudioModelConfig config, String audioPathArg, String question) {
                assertEquals(audioModelConfig, config);
                assertEquals(audioPath.toString(), audioPathArg);
                assertEquals("What happens in the audio?", question);
                return new AudioQaResult("A person is speaking.", 1.0);
            }
        };

        Runner.start();
        try {
            ToolOutput result = (ToolOutput) qaTool.invoke(
                    Map.of(
                            "audio_path_or_url", audioPath.toString(),
                            "question", "What happens in the audio?"
                    ),
                    Map.of()
            );

            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertEquals("A person is speaking.", data.get("answer"));
            assertEquals(1.0, data.get("duration_seconds"));
            assertEquals("mock-audio-qa", data.get("model"));
        } finally {
            Runner.stop();
        }
    }
}
