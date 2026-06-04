/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness.tools;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.schema.config.AudioModelConfig;
import com.openjiuwen.harness.tools.AudioQuestionAnsweringTool;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for audio tool registration and invocation.
 *
 * <p>Mirrors Python's {@code test_audio_tool.py} in
 * {@code tests.system_tests.harness.tools}.</p>
 */
@Tag("system-test")
public class TestAudioTool {

    private static void writeTestWav(Path path, int durationSeconds) throws Exception {
        float sampleRate = 16000f;
        int numFrames = (int) sampleRate * durationSeconds;
        byte[] pcmData = new byte[numFrames * 2];
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        ByteArrayInputStream bytes = new ByteArrayInputStream(pcmData);
        AudioInputStream audioInputStream = new AudioInputStream(bytes, format, numFrames);
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, path.toFile());
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
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                assertEquals(audioPath.toString(), inputs.get("audio_path_or_url"));
                assertEquals("What happens in the audio?", inputs.get("question"));
                return new ToolOutput(true, Map.of(
                        "answer", "A person is speaking.",
                        "duration_seconds", 1.0,
                        "model", audioModelConfig.getQuestionAnsweringModel()
                ), null);
            }
        };

        Runner.start();
        try {
            Runner.resourceMgr().addTool(qaTool, null);
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
