/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.multimodal.AudioTools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.system_tests.harness.tools.test_audio_tool}
 * in {@code tests/system_tests/harness/tools/test_audio_tool.py}.
 */
class AudioToolSystemMissingTest {

    @TempDir
    Path tempDir;

    @Test
    void createAudioToolsRegisterAndInvoke() throws Exception {
        Path audioPath = tempDir.resolve("sample.wav");
        writeTestWav(audioPath, 1);
        DeepAgentConfig.AudioModelConfig audioModelConfig = audioModelConfig();
        RecordingAudioInvoker invoker = new RecordingAudioInvoker();
        List<Tool> tools = AudioTools.createAudioTools("cn", audioModelConfig, invoker);

        ToolOutput result;
        try {
            Runner.start().toCompletableFuture().join();
            assertTrue(Runner.resourceMgr().addTools(tools, null, true).stream().allMatch(add -> add.isOk()));
            Tool registeredTool = Runner.resourceMgr().getTool(toolIdByName(tools, "AudioQuestionAnsweringTool"));
            assertNotNull(registeredTool);
            result = (ToolOutput) registeredTool.invoke(Map.of(
                    "audio_path_or_url", audioPath.toString(),
                    "question", "What happens in the audio?"
            ));
        } finally {
            for (Tool tool : tools) {
                Runner.resourceMgr().removeTool(tool.getCard().getId());
            }
            Runner.stop().toCompletableFuture().join();
        }

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("A person is speaking.", data.get("answer"));
        assertEquals(1.0, ((Number) data.get("duration_seconds")).doubleValue(), 0.01);
        assertEquals("mock-audio-qa", data.get("model"));
        assertEquals(audioPath.toAbsolutePath().normalize().toString(), invoker.questionPath);
        assertEquals("What happens in the audio?", invoker.question);
        assertTrue(tools.stream().map(tool -> tool.getCard().getName()).toList()
                .contains("AudioQuestionAnsweringTool"));
    }

    private static DeepAgentConfig.AudioModelConfig audioModelConfig() {
        DeepAgentConfig.AudioModelConfig config = new DeepAgentConfig.AudioModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setQuestionAnsweringModel("mock-audio-qa");
        return config;
    }

    private static String toolIdByName(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> name.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow()
                .getCard()
                .getId();
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
     * Mirrors Python's monkeypatched {@code fake_invoke_audio_question_answering} in
     * {@code tests/system_tests/harness/tools/test_audio_tool.py}.
     */
    private static final class RecordingAudioInvoker implements AudioTools.AudioInvoker {
        private String questionPath;
        private String question;

        @Override
        public Map<String, Object> transcribe(String audioPath, Map<String, Object> inputs) {
            return Map.of("text", "");
        }

        @Override
        public Map<String, Object> questionAnswer(String audioPath, String question, Map<String, Object> inputs) {
            this.questionPath = audioPath;
            this.question = question;
            return Map.of(
                    "answer", "A person is speaking.",
                    "duration_seconds", 1.0
            );
        }

        @Override
        public Map<String, Object> metadata(String audioPath, Map<String, Object> inputs) {
            return Map.of();
        }
    }
}
