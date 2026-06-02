/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.config.AudioModelConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_audio_tools.py} from
 * {@code tests/unit_tests/harness/tools/test_audio_tools.py}.
 */
@DisplayName("AudioTools Tests")
class TestAudioTools {

    @Test
    void testAudioTranscriptionToolTranscribesLocalAudio(@TempDir Path tempDir) throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 1);
        AudioModelConfig audioModelConfig = audioConfig();

        AudioTranscriptionTool tool = new AudioTranscriptionTool(audioModelConfig) {
            @Override
            protected String invokeAudioTranscription(AudioModelConfig config, String audioPathArg) {
                assertSame(audioModelConfig, config);
                assertEquals(audioPath.toString(), audioPathArg);
                return "hello from audio";
            }
        };

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("audio_path_or_url", audioPath.toString()), Map.of());

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("hello from audio", data.get("text"));
        assertEquals("mock-transcribe", data.get("model"));
    }

    @Test
    void testAudioQuestionAnsweringToolReturnsAnswerAndDuration(@TempDir Path tempDir) throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 1);
        AudioModelConfig audioModelConfig = audioConfig();

        AudioQuestionAnsweringTool tool = new AudioQuestionAnsweringTool(audioModelConfig) {
            @Override
            protected AudioQaResult invokeAudioQuestionAnswering(AudioModelConfig config, String audioPathArg,
                    String question) {
                assertSame(audioModelConfig, config);
                assertEquals(audioPath.toString(), audioPathArg);
                assertEquals("What is being said?", question);
                return new AudioQaResult("A person says hello.", 1.0);
            }
        };

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "audio_path_or_url", audioPath.toString(),
                "question", "What is being said?"
        ), Map.of());

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals("A person says hello.", data.get("answer"));
        assertEquals(1.0, data.get("duration_seconds"));
        assertEquals("mock-audio-qa", data.get("model"));
    }

    @Test
    void testAudioMetadataToolReturnsDurationWhenAcrMissing(@TempDir Path tempDir) throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 2);
        AudioModelConfig audioModelConfig = audioConfig();
        audioModelConfig.setAcrAccessKey("");
        audioModelConfig.setAcrAccessSecret("");

        AudioMetadataTool tool = new AudioMetadataTool(audioModelConfig);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("audio_path_or_url", audioPath.toString()), Map.of());

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertEquals(2.0, data.get("duration_seconds"));
        assertEquals(false, data.get("identified"));
        assertTrue(String.valueOf(data.get("note")).contains("ACR credentials"));
    }

    @Test
    void testCreateAudioToolsSupportsLanguage() {
        AudioModelConfig audioModelConfig = audioConfig();

        List<Tool> tools = AudioTools.createAudioTools("en", audioModelConfig);

        assertEquals(3, tools.size());
        assertSame(audioModelConfig, ((AudioTranscriptionTool) tools.get(0)).audioModelConfig);
        assertSame(audioModelConfig, ((AudioQuestionAnsweringTool) tools.get(1)).audioModelConfig);
        assertSame(audioModelConfig, ((AudioMetadataTool) tools.get(2)).audioModelConfig);
    }

    @Test
    void testAudioTranscriptionToolReturnsClearErrorWithoutConfig() {
        AudioTranscriptionTool tool = new AudioTranscriptionTool();

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("audio_path_or_url", "https://example.com/audio.wav"),
                Map.of()
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Audio model config is not set"));
    }

    @Test
    void testAudioModelConfigFromEnv() {
        AudioModelConfig config = AudioModelConfig.fromEnv(Map.of(
                "AUDIO_API_KEY", "audio-key",
                "AUDIO_BASE_URL", "https://audio.example.com/v1",
                "AUDIO_TRANSCRIPTION_MODEL", "mock-transcribe",
                "AUDIO_QUESTION_ANSWERING_MODEL", "mock-qa",
                "AUDIO_MAX_RETRIES", "5",
                "ACR_ACCESS_KEY", "acr-key",
                "ACR_ACCESS_SECRET", "acr-secret"
        ));

        assertEquals("audio-key", config.getApiKey());
        assertEquals("https://audio.example.com/v1", config.getBaseUrl());
        assertEquals("mock-transcribe", config.getTranscriptionModel());
        assertEquals("mock-qa", config.getQuestionAnsweringModel());
        assertEquals(5, config.getMaxRetries());
        assertEquals("acr-key", config.getAcrAccessKey());
        assertEquals("acr-secret", config.getAcrAccessSecret());
    }

    private static AudioModelConfig audioConfig() {
        AudioModelConfig config = new AudioModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setTranscriptionModel("mock-transcribe");
        config.setQuestionAnsweringModel("mock-audio-qa");
        return config;
    }

    private static void writeTestWav(Path path, int durationSeconds) throws Exception {
        int sampleRate = 16000;
        int frames = sampleRate * durationSeconds;
        byte[] data = new byte[frames * 2];
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(data), format, frames)) {
            AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
}
