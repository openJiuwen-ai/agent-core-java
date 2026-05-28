/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness.tools;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.schema.config.VisionModelConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.VisualQuestionAnsweringTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_vision_tool.py} in {@code tests.system_tests.harness.tools}.
 * 
 * Tests for vision tool registration and invocation.
 */
@Tag("system-test")
class TestVisionTool {

    static boolean visionSystemTestsEnabled() {
        String value = System.getenv("RUN_VISION_SYSTEM_TESTS");
        if (value == null) {
            value = System.getProperty("RUN_VISION_SYSTEM_TESTS");
        }
        return value != null && value.strip().toLowerCase().matches("1|true|yes|on");
    }

    private static void writeRedPng(Path path) throws Exception {
        byte[] pngData = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n',
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00,
                (byte) 0x90, 'w', 'S', (byte) 0xDE,
                0x00, 0x00, 0x00, 0x0C, 'I', 'D', 'A', 'T',
                'x', (byte) 0x9C, 'c', (byte) 0xF8, (byte) 0xCF, (byte) 0xC0,
                0x00, 0x00, 0x03, 0x01, 0x01, 0x00,
                (byte) 0xC9, (byte) 0xFE, (byte) 0x92, (byte) 0xEF,
                0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D',
                (byte) 0xAE, 'B', 0x60, (byte) 0x82
        };
        Files.write(path, pngData);
    }

    @Test
    void testCreateVisionToolsRegisterAndInvoke() throws Exception {
        List<String> prompts = new ArrayList<>();
        VisionModelConfig visionModelConfig = new VisionModelConfig();
        visionModelConfig.setApiKey("test-key");
        visionModelConfig.setBaseUrl("https://example.com/v1");
        visionModelConfig.setModel("mock-model");

        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(visionModelConfig) {
            private int count = 0;

            @Override
            protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
                prompts.add(prompt);
                assertEquals(visionModelConfig, configuredModel);
                count++;
                if (count == 1) {
                    return "HELLO WORLD";
                }
                assertTrue(prompt.contains("HELLO WORLD"));
                assertTrue(prompt.contains("What text is shown?"));
                return "The image text says HELLO WORLD.";
            }
        };

        Runner.start();
        try {
            Runner.resourceMgr().addTool(tool, null);
            
            ToolOutput result = (ToolOutput) tool.invoke(
                    Map.of(
                            "image_path_or_url", "https://example.com/image.png",
                            "question", "What text is shown?"
                    ),
                    Map.of()
            );

            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertEquals("HELLO WORLD", data.get("ocr_text"));
            assertEquals("The image text says HELLO WORLD.", data.get("answer"));
            assertEquals(2, prompts.size());
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
            Runner.stop();
        }
    }

    @Test
    void testRunnerStopClearsRegisteredVisionTools() {
        Runner.start();
        try {
            VisionModelConfig config = new VisionModelConfig();
            VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(config);
            Runner.resourceMgr().addTool(tool, null);
        } finally {
            Runner.stop();
        }

        Object leakedTool = Runner.resourceMgr().getTool("visual_question_answering");
        assertNull(leakedTool);
    }

    @Test
    @EnabledIf("visionSystemTestsEnabled")
    void testCreateVisionToolsWithRealApiFromEnv(@TempDir Path tempDir) throws Exception {
        // Load API configuration from environment
        String apiKey = System.getenv("VISION_API_KEY");
        if (apiKey == null) {
            apiKey = System.getenv("OPENROUTER_API_KEY");
        }
        if (apiKey == null) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        VisionModelConfig visionModelConfig = new VisionModelConfig();
        visionModelConfig.setApiKey(apiKey);

        String baseUrl = System.getenv("VISION_BASE_URL");
        if (baseUrl == null) {
            baseUrl = System.getenv("OPENROUTER_BASE_URL");
        }
        if (baseUrl == null) {
            baseUrl = System.getenv("OPENAI_BASE_URL");
        }
        if (baseUrl != null) {
            visionModelConfig.setBaseUrl(baseUrl);
        }

        String model = System.getenv("VISION_MODEL");
        if (model != null) {
            visionModelConfig.setModel(model);
        }

        Path imagePath = tempDir.resolve("red.png");
        writeRedPng(imagePath);

        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(visionModelConfig);
        Runner.start();
        try {
            ToolOutput result = (ToolOutput) tool.invoke(
                    Map.of(
                            "image_path_or_url", imagePath.toString(),
                            "question", "What is the dominant color in this image?",
                            "include_ocr", false
                    ),
                    Map.of()
            );
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.getData();
            assertNotNull(data.get("model"));
            assertNotNull(data.get("answer"));
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
            Runner.stop();
        }
    }
}
