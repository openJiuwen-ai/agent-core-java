/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.multimodal.AudioTools.AudioInvoker;
import com.openjiuwen.harness.tools.multimodal.AudioTools.AudioMetadataTool;
import com.openjiuwen.harness.tools.multimodal.AudioTools.AudioQuestionAnsweringTool;
import com.openjiuwen.harness.tools.multimodal.AudioTools.AudioTranscriptionTool;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_audio_tools} in
 * {@code tests/unit_tests/harness/tools/test_audio_tools.py}.
 */
class AudioToolsMissingTest {

    @TempDir
    Path tempDir;

    @Test
    void audioTranscriptionToolTranscribesLocalAudio() throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 1);
        DeepAgentConfig.AudioModelConfig audioModelConfig = audioModelConfig();
        RecordingAudioInvoker invoker = new RecordingAudioInvoker();
        AudioTranscriptionTool tool = new AudioTranscriptionTool(audioModelConfig, invoker);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("audio_path_or_url", audioPath.toString()));

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("hello from audio", data.get("text"));
        assertEquals("mock-transcribe", data.get("model"));
        assertEquals(audioPath.toAbsolutePath().normalize().toString(), invoker.transcriptionPath);
    }

    @Test
    void audioQuestionAnsweringToolReturnsAnswerAndDuration() throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 1);
        DeepAgentConfig.AudioModelConfig audioModelConfig = audioModelConfig();
        RecordingAudioInvoker invoker = new RecordingAudioInvoker();
        AudioQuestionAnsweringTool tool = new AudioQuestionAnsweringTool(audioModelConfig, invoker);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "audio_path_or_url", audioPath.toString(),
                "question", "What is being said?"
        ));

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("A person says hello.", data.get("answer"));
        assertEquals(1.0, ((Number) data.get("duration_seconds")).doubleValue(), 0.01);
        assertEquals("mock-audio-qa", data.get("model"));
        assertEquals(audioPath.toAbsolutePath().normalize().toString(), invoker.questionPath);
        assertEquals("What is being said?", invoker.question);
    }

    @Test
    void audioMetadataToolReturnsDurationWhenAcrMissing() throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 2);
        DeepAgentConfig.AudioModelConfig audioModelConfig = audioModelConfig();
        audioModelConfig.setAcrAccessKey("");
        audioModelConfig.setAcrAccessSecret("");
        AudioMetadataTool tool = new AudioMetadataTool(audioModelConfig, null);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("audio_path_or_url", audioPath.toString()));

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(2.0, ((Number) data.get("duration_seconds")).doubleValue(), 0.01);
        assertEquals(false, data.get("identified"));
        assertTrue(String.valueOf(data.get("note")).contains("ACR credentials"));
    }

    @Test
    void createAudioToolsSupportsLanguage() {
        DeepAgentConfig.AudioModelConfig audioModelConfig = audioModelConfig();
        RecordingAudioInvoker invoker = new RecordingAudioInvoker();

        List<Tool> tools = AudioTools.createAudioTools("en", audioModelConfig, invoker);

        assertEquals(3, tools.size());
        assertSame(audioModelConfig, ((AudioTranscriptionTool) tools.get(0)).getAudioModelConfig());
        assertSame(audioModelConfig, ((AudioQuestionAnsweringTool) tools.get(1)).getAudioModelConfig());
        assertSame(audioModelConfig, ((AudioMetadataTool) tools.get(2)).getAudioModelConfig());
    }

    @Test
    void audioTranscriptionToolReturnsClearErrorWithoutConfig() throws Exception {
        AudioTranscriptionTool tool = new AudioTranscriptionTool();

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("audio_path_or_url", "https://example.com/audio.wav")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Audio model config is not set"));
    }

    @Test
    void audioModelConfigFromEnv() {
        DeepAgentConfig.AudioModelConfig config = DeepAgentConfig.AudioModelConfig.fromEnvironment(Map.of(
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

    private static DeepAgentConfig.AudioModelConfig audioModelConfig() {
        DeepAgentConfig.AudioModelConfig config = new DeepAgentConfig.AudioModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setTranscriptionModel("mock-transcribe");
        config.setQuestionAnsweringModel("mock-audio-qa");
        return config;
    }

    private static void writeTestWav(Path path, int durationSeconds) throws Exception {
        int sampleRate = 16_000;
        byte[] audioData = new byte[sampleRate * durationSeconds * 2];
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                format,
                sampleRate * durationSeconds
        )) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }

    /**
     * Mirrors Python's monkeypatched audio invocation functions in
     * {@code tests/unit_tests/harness/tools/test_audio_tools.py}.
     */
    private static final class RecordingAudioInvoker implements AudioInvoker {
        private String transcriptionPath;
        private String questionPath;
        private String question;

        @Override
        public Map<String, Object> transcribe(String audioPath, Map<String, Object> inputs) {
            this.transcriptionPath = audioPath;
            return Map.of("text", "hello from audio");
        }

        @Override
        public Map<String, Object> questionAnswer(String audioPath, String question, Map<String, Object> inputs) {
            this.questionPath = audioPath;
            this.question = question;
            return Map.of(
                    "answer", "A person says hello.",
                    "duration_seconds", 1.0
            );
        }

        @Override
        public Map<String, Object> metadata(String audioPath, Map<String, Object> inputs) {
            return Map.of();
        }
    }
}
