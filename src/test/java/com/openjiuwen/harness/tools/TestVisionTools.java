/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.config.VisionModelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for vision tools.
 *
 * <p>Mirrors Python's {@code test_vision_tools.py} in
 * {@code tests.unit_tests.harness.tools.test_vision_tools}.
 */
class TestVisionTools {

    private VisionModelConfig visionConfig;

    @BeforeEach
    void setUp() {
        visionConfig = new VisionModelConfig();
        visionConfig.setApiKey("test-key");
        visionConfig.setBaseUrl("https://example.com/v1");
        visionConfig.setModel("mock-model");
    }

    @Test
    void testImageOcrToolEncodesLocalImage(@TempDir Path tempDir) throws Exception {
        Path imagePath = tempDir.resolve("sample.png");
        writeTestPng(imagePath);
        RecordingImageOCRTool tool = new RecordingImageOCRTool(visionConfig, "detected text");

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("image_path_or_url", imagePath.toString()), Map.of());

        assertTrue(result.isSuccess());
        Map<String, Object> data = data(result);
        assertEquals("detected text", data.get("text"));
        assertEquals("mock-model", data.get("model"));
        assertSame(visionConfig, tool.configs.get(0));
        assertEquals(imagePath.toString(), tool.imagePaths.get(0));
        assertTrue(tool.prompts.get(0).contains("meticulous OCR assistant"));
        assertTrue(imageUrl(data).startsWith("data:image/png;base64,"));
    }

    @Test
    void testVisualQuestionAnsweringToolUsesOcrContext() {
        RecordingVisualQuestionAnsweringTool tool = new RecordingVisualQuestionAnsweringTool(
                visionConfig,
                List.of("SALE 50% OFF", "The sign says SALE 50% OFF.")
        );

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of(
                        "image_path_or_url", "https://example.com/image.png",
                        "question", "What does the sign say?"
                ),
                Map.of()
        );

        assertTrue(result.isSuccess());
        Map<String, Object> data = data(result);
        assertEquals("SALE 50% OFF", data.get("ocr_text"));
        assertEquals("The sign says SALE 50% OFF.", data.get("answer"));
        assertEquals("mock-model", data.get("model"));
        assertEquals(2, tool.prompts.size());
        assertSame(visionConfig, tool.configs.get(0));
        assertSame(visionConfig, tool.configs.get(1));
        assertTrue(tool.prompts.get(1).contains("SALE 50% OFF"));
        assertTrue(tool.prompts.get(1).contains("What does the sign say?"));
    }

    @Test
    void testVisualQuestionAnsweringToolCanSkipOcr() {
        RecordingVisualQuestionAnsweringTool tool = new RecordingVisualQuestionAnsweringTool(
                visionConfig,
                List.of("A black cat is sitting on a chair.")
        );

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of(
                        "image_path_or_url", "https://example.com/image.png",
                        "question", "Describe the image.",
                        "include_ocr", false
                ),
                Map.of()
        );

        assertTrue(result.isSuccess());
        Map<String, Object> data = data(result);
        assertNull(data.get("ocr_text"));
        assertEquals("A black cat is sitting on a chair.", data.get("answer"));
        assertEquals(List.of("Describe the image."), tool.prompts);
    }

    @Test
    void testCreateVisionToolsSupportsLanguage() {
        List<Tool> tools = VisionTools.createVisionTools("en", visionConfig);

        assertEquals(2, tools.size());
        ImageOCRTool ocrTool = assertInstanceOf(ImageOCRTool.class, tools.get(0));
        VisualQuestionAnsweringTool vqaTool = assertInstanceOf(VisualQuestionAnsweringTool.class, tools.get(1));
        assertSame(visionConfig, ocrTool.getVisionModelConfig());
        assertSame(visionConfig, vqaTool.getVisionModelConfig());
        assertEquals("image_ocr", tools.get(0).getCard().getName());
        assertEquals("visual_question_answering", tools.get(1).getCard().getName());
    }

    @Test
    void testImageOcrToolReturnsClearErrorWithoutVisionConfig() throws Exception {
        ImageOCRTool tool = new ImageOCRTool(null);

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("image_path_or_url", "https://example.com/image.png"),
                Map.of()
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Vision model config is not set"));
    }

    @Test
    void testVisionModelConfigFromEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("VISION_API_KEY", "vision-key");
        env.put("VISION_BASE_URL", "https://openrouter.ai/api/v1");

        VisionModelConfig config = VisionTools.visionModelConfigFromEnv(env);

        assertEquals("vision-key", config.getApiKey());
        assertEquals("https://openrouter.ai/api/v1", config.getBaseUrl());
        assertEquals("google/gemini-2.5-pro", config.getModel());
    }

    private static void writeTestPng(Path path) throws Exception {
        byte[] pngData = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00,
                (byte) 0x90, 0x77, 0x53, (byte) 0xDE,
                0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54,
                0x78, (byte) 0x9C, 0x63, 0x60, 0x00, 0x00, 0x00, 0x02,
                0x00, 0x01, (byte) 0xE2, 0x21, (byte) 0xBC, 0x33,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
        Files.write(path, pngData);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    @SuppressWarnings("unchecked")
    private static String imageUrl(Map<String, Object> data) {
        Map<String, Object> imageContent = (Map<String, Object>) data.get("image_content");
        Map<String, Object> imageUrl = (Map<String, Object>) imageContent.get("image_url");
        return String.valueOf(imageUrl.get("url"));
    }

    private static class RecordingImageOCRTool extends ImageOCRTool {
        private final String response;
        private final List<String> imagePaths = new ArrayList<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<VisionModelConfig> configs = new ArrayList<>();

        RecordingImageOCRTool(VisionModelConfig visionModelConfig, String response) {
            super(visionModelConfig);
            this.response = response;
        }

        @Override
        protected String callVisionModel(
                String imagePathOrUrl,
                String prompt,
                VisionModelConfig configuredModel
        ) {
            imagePaths.add(imagePathOrUrl);
            prompts.add(prompt);
            configs.add(configuredModel);
            return response;
        }
    }

    private static class RecordingVisualQuestionAnsweringTool extends VisualQuestionAnsweringTool {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();
        private final List<VisionModelConfig> configs = new ArrayList<>();

        RecordingVisualQuestionAnsweringTool(VisionModelConfig visionModelConfig, List<String> responses) {
            super(visionModelConfig);
            this.responses = responses;
        }

        @Override
        protected String callVisionModel(
                String imagePathOrUrl,
                String prompt,
                VisionModelConfig configuredModel
        ) {
            prompts.add(prompt);
            configs.add(configuredModel);
            return responses.get(prompts.size() - 1);
        }
    }
}
