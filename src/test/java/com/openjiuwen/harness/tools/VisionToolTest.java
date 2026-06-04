package com.openjiuwen.harness.tools;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.harness.schema.config.VisionModelConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's test_vision_tool.py.
 * Tests vision tool registration and invocation.
 */
@Tag("system-test")
class VisionToolTest {

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
        VisionModelConfig visionModelConfig = new VisionModelConfig();
        visionModelConfig.setApiKey("test-key");
        visionModelConfig.setBaseUrl("https://example.com/v1");
        visionModelConfig.setModel("mock-model");

        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(visionModelConfig) {
            private int count = 0;

            @Override
            protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
                count++;
                assertEquals(visionModelConfig, configuredModel);
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
        } finally {
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
    @DisabledIf("visionSystemTestsEnabled")
    void testVisionToolOutputStructure() throws Exception {
        VisionModelConfig config = new VisionModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setModel("mock-model");

        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(config) {
            @Override
            protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
                return "test response";
            }
        };

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of(
                        "image_path_or_url", "https://example.com/image.png",
                        "question", "What is this?",
                        "include_ocr", false
                ),
                Map.of()
        );

        assertTrue(result.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertTrue(data.containsKey("answer"));
        assertTrue(data.containsKey("ocr_text"));
        assertEquals("test response", data.get("answer"));
        assertNull(data.get("ocr_text"));
    }

    @Test
    @EnabledIf("visionSystemTestsEnabled")
    void testCreateVisionToolsWithRealApiFromEnv(@TempDir Path tempDir) throws Exception {
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
            assertFalse(String.valueOf(data.get("answer")).isEmpty());
        } finally {
            Runner.stop();
        }
    }
}
