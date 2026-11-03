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
import com.openjiuwen.harness.tools.multimodal.VisionTools.ImageOcrTool;
import com.openjiuwen.harness.tools.multimodal.VisionTools.VisionInvoker;
import com.openjiuwen.harness.tools.multimodal.VisionTools.VisualQuestionAnsweringTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.tools.test_vision_tools} in
 * {@code tests/unit_tests/harness/tools/test_vision_tools.py}.
 */
class VisionToolsMissingTest {

    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNkAAIAAAIABFEi3lIAAAAASUVORK5CYII=";

    @TempDir
    Path tempDir;

    @Test
    void imageOcrToolEncodesLocalImage() throws Exception {
        Path imagePath = tempDir.resolve("sample.png");
        writeTestPng(imagePath);
        DeepAgentConfig.VisionModelConfig visionModelConfig = visionModelConfig();
        RecordingVisionInvoker invoker = new RecordingVisionInvoker();
        ImageOcrTool tool = new ImageOcrTool(visionModelConfig, invoker);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of("image_path_or_url", imagePath.toString()));

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("detected text", data.get("text"));
        assertTrue(invoker.firstImageUrl().startsWith("data:image/png;base64,"));
    }

    @Test
    void visualQuestionAnsweringToolUsesOcrContext() throws Exception {
        DeepAgentConfig.VisionModelConfig visionModelConfig = visionModelConfig();
        RecordingVisionInvoker invoker = new RecordingVisionInvoker();
        invoker.ocrText = "SALE 50% OFF";
        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(visionModelConfig, invoker);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "image_path_or_url", "https://example.com/image.png",
                "question", "What does the sign say?"
        ));

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals("SALE 50% OFF", data.get("ocr_text"));
        assertEquals("The sign says SALE 50% OFF.", data.get("answer"));
        assertEquals(2, invoker.prompts.size());
        assertTrue(invoker.prompts.get(1).contains("SALE 50% OFF"));
        assertTrue(invoker.prompts.get(1).contains("What does the sign say?"));
        assertSame(visionModelConfig, invoker.lastConfig);
    }

    @Test
    void visualQuestionAnsweringToolCanSkipOcr() throws Exception {
        DeepAgentConfig.VisionModelConfig visionModelConfig = visionModelConfig();
        RecordingVisionInvoker invoker = new RecordingVisionInvoker();
        invoker.answer = "A black cat is sitting on a chair.";
        VisualQuestionAnsweringTool tool = new VisualQuestionAnsweringTool(visionModelConfig, invoker);

        ToolOutput result = (ToolOutput) tool.invoke(Map.of(
                "image_path_or_url", "https://example.com/image.png",
                "question", "Describe the image.",
                "include_ocr", false
        ));

        assertTrue(result.isSuccess());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(null, data.get("ocr_text"));
        assertEquals("A black cat is sitting on a chair.", data.get("answer"));
        assertEquals(List.of("Describe the image."), invoker.prompts);
    }

    @Test
    void createVisionToolsSupportsLanguage() {
        DeepAgentConfig.VisionModelConfig visionModelConfig = visionModelConfig();
        RecordingVisionInvoker invoker = new RecordingVisionInvoker();

        List<Tool> tools = VisionTools.createVisionTools("en", visionModelConfig, invoker);

        assertEquals(2, tools.size());
        assertSame(visionModelConfig, ((ImageOcrTool) tools.get(0)).getVisionModelConfig());
        assertSame(visionModelConfig, ((VisualQuestionAnsweringTool) tools.get(1)).getVisionModelConfig());
    }

    @Test
    void imageOcrToolReturnsClearErrorWithoutVisionConfig() throws Exception {
        ImageOcrTool tool = new ImageOcrTool();

        ToolOutput result = (ToolOutput) tool.invoke(
                Map.of("image_path_or_url", "https://example.com/image.png")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Vision model config is not set"));
    }

    @Test
    void visionModelConfigFromEnv() {
        DeepAgentConfig.VisionModelConfig config = DeepAgentConfig.VisionModelConfig.fromEnvironment(Map.of(
                "VISION_API_KEY", "vision-key",
                "VISION_BASE_URL", "https://openrouter.ai/api/v1"
        ));

        assertEquals("vision-key", config.getApiKey());
        assertEquals("https://openrouter.ai/api/v1", config.getBaseUrl());
        assertEquals("google/gemini-2.5-pro", config.getModel());
    }

    private static DeepAgentConfig.VisionModelConfig visionModelConfig() {
        DeepAgentConfig.VisionModelConfig config = new DeepAgentConfig.VisionModelConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://example.com/v1");
        config.setModel("mock-model");
        return config;
    }

    private static void writeTestPng(Path path) throws Exception {
        Files.write(path, Base64.getDecoder().decode(ONE_PIXEL_PNG));
    }

    /**
     * Mirrors Python's monkeypatched vision invocation helpers in
     * {@code tests/unit_tests/harness/tools/test_vision_tools.py}.
     */
    private static final class RecordingVisionInvoker implements VisionInvoker {
        private final List<String> prompts = new ArrayList<>();
        private DeepAgentConfig.VisionModelConfig lastConfig;
        private Map<String, Object> firstImageContent;
        private String ocrText = "detected text";
        private String answer = "The sign says SALE 50% OFF.";

        @Override
        public Map<String, Object> ocr(String imagePath, Map<String, Object> inputs) {
            prompts.add(String.valueOf(inputs.get("prompt")));
            lastConfig = (DeepAgentConfig.VisionModelConfig) inputs.get("vision_model_config");
            if (firstImageContent == null) {
                firstImageContent = mapValue(inputs.get("image_content"));
            }
            return Map.of(
                    "text", ocrText,
                    "model", "mock-model"
            );
        }

        @Override
        public Map<String, Object> answer(String imagePath, String question, Map<String, Object> inputs) {
            prompts.add(question);
            lastConfig = (DeepAgentConfig.VisionModelConfig) inputs.get("vision_model_config");
            if (firstImageContent == null) {
                firstImageContent = mapValue(inputs.get("image_content"));
            }
            return Map.of(
                    "answer", answer,
                    "model", "mock-model"
            );
        }

        private String firstImageUrl() {
            Map<String, Object> imageUrl = mapValue(firstImageContent.get("image_url"));
            return String.valueOf(imageUrl.get("url"));
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> mapValue(Object value) {
            return (Map<String, Object>) value;
        }
    }
}
