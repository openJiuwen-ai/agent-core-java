/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.FileNotFoundException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vision multimodal tool helpers.
 *
 * <p>Mirrors Python's OCR/VQA helpers and tool classes in
 * {@code openjiuwen/harness/tools/multimodal/vision.py}.</p>
 */
public final class VisionTools {
    private static final String SANDBOX_PATH_MARKER = "home/user";
    private static final String DEFAULT_OCR_PROMPT = """
            You are a meticulous OCR assistant.
            Extract all visible text from the image.
            Preserve structure, line breaks, numbers, symbols, and uncertain text when possible.
            If no text is visible, reply with 'No text found'.""";
    private static final String DEFAULT_VQA_PROMPT_TEMPLATE = """
            You are a careful visual analysis assistant.
            Use the image and the OCR result below to answer the user's question accurately.

            OCR result:
            %s

            Question:
            %s

            Provide a concise but complete answer. If something is uncertain, say so explicitly.""";

    private VisionTools() {
    }

    public static String guessMimeType(String filePath) {
        String guess = URLConnection.guessContentTypeFromName(filePath == null ? "" : Path.of(filePath).getFileName().toString());
        return guess == null ? "image/png" : guess;
    }

    public static List<Tool> createVisionTools(VisionInvoker invoker) {
        return createVisionTools("cn", null, invoker);
    }

    public static List<Tool> createVisionTools(String language,
                                               DeepAgentConfig.VisionModelConfig visionModelConfig,
                                               VisionInvoker invoker) {
        String resolvedLanguage = language == null || language.isBlank() ? "cn" : language;
        return List.of(
                new ImageOcrTool(resolvedLanguage, visionModelConfig, invoker),
                new VisualQuestionAnsweringTool(resolvedLanguage, visionModelConfig, invoker)
        );
    }

    public interface VisionInvoker {
        Map<String, Object> ocr(String imagePath, Map<String, Object> inputs) throws Exception;

        Map<String, Object> answer(String imagePath, String question, Map<String, Object> inputs) throws Exception;
    }

    /**
     * Mirrors Python's {@code ImageOCRTool} in {@code openjiuwen/harness/tools/multimodal/vision.py}.
     */
    public static class ImageOcrTool extends AbstractHarnessTool {
        private final DeepAgentConfig.VisionModelConfig visionModelConfig;
        private final VisionInvoker invoker;

        public ImageOcrTool() {
            this(null, null);
        }

        public ImageOcrTool(VisionInvoker invoker) {
            this(null, invoker);
        }

        public ImageOcrTool(DeepAgentConfig.VisionModelConfig visionModelConfig, VisionInvoker invoker) {
            this("cn", visionModelConfig, invoker);
        }

        public ImageOcrTool(String language,
                            DeepAgentConfig.VisionModelConfig visionModelConfig,
                            VisionInvoker invoker) {
            super(toolCard("image_ocr", "ImageOCRTool", "Extract visible text from an image."));
            String ignored = language;
            this.invoker = invoker;
            this.visionModelConfig = visionModelConfig;
        }

        public DeepAgentConfig.VisionModelConfig getVisionModelConfig() {
            return visionModelConfig;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            try {
                DeepAgentConfig.VisionModelConfig config = requireVisionModelConfig(visionModelConfig);
                String imagePathOrUrl = imagePathOrUrl(inputs);
                if (invoker == null) {
                    throw new IllegalStateException("Vision invoker is not configured.");
                }
                Map<String, Object> payload = withVisionPayload(
                        inputs,
                        inputs == null || inputs.get("prompt") == null ? DEFAULT_OCR_PROMPT : stringValueLocal(inputs.get("prompt")),
                        buildImageContent(imagePathOrUrl),
                        config
                );
                Map<String, Object> response = invoker.ocr(imagePathOrUrl, payload);
                Map<String, Object> data = linkedMap();
                data.put("text", response.get("text"));
                data.put("model", response.getOrDefault("model", config.getModel()));
                return ToolOutput.success(data);
            } catch (Exception exception) {
                return ToolOutput.failure(exception.getMessage());
            }
        }
    }

    /**
     * Mirrors Python's {@code VisualQuestionAnsweringTool} in
     * {@code openjiuwen/harness/tools/multimodal/vision.py}.
     */
    public static class VisualQuestionAnsweringTool extends AbstractHarnessTool {
        private final DeepAgentConfig.VisionModelConfig visionModelConfig;
        private final VisionInvoker invoker;

        public VisualQuestionAnsweringTool() {
            this(null, null);
        }

        public VisualQuestionAnsweringTool(VisionInvoker invoker) {
            this(null, invoker);
        }

        public VisualQuestionAnsweringTool(DeepAgentConfig.VisionModelConfig visionModelConfig,
                                           VisionInvoker invoker) {
            this("cn", visionModelConfig, invoker);
        }

        public VisualQuestionAnsweringTool(String language,
                                           DeepAgentConfig.VisionModelConfig visionModelConfig,
                                           VisionInvoker invoker) {
            super(toolCard("visual_question_answering", "VisualQuestionAnsweringTool",
                    "Answer a question about an image."));
            String ignored = language;
            this.invoker = invoker;
            this.visionModelConfig = visionModelConfig;
        }

        public DeepAgentConfig.VisionModelConfig getVisionModelConfig() {
            return visionModelConfig;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            try {
                DeepAgentConfig.VisionModelConfig config = requireVisionModelConfig(visionModelConfig);
                String imagePathOrUrl = imagePathOrUrl(inputs);
                String question = requiredString(inputs, "question");
                boolean includeOcr = boolValue(inputs == null ? null : inputs.get("include_ocr"), true);
                if (invoker == null) {
                    throw new IllegalStateException("Vision invoker is not configured.");
                }
                String ocrText = "";
                String model = "";
                Map<String, Object> imageContent = buildImageContent(imagePathOrUrl);
                if (includeOcr) {
                    String ocrPrompt = inputs == null || inputs.get("ocr_prompt") == null
                            ? DEFAULT_OCR_PROMPT
                            : stringValueLocal(inputs.get("ocr_prompt"));
                    Map<String, Object> ocrResponse = invoker.ocr(
                            imagePathOrUrl,
                            withVisionPayload(inputs, ocrPrompt, imageContent, config)
                    );
                    ocrText = stringValueLocal(ocrResponse.get("text"));
                    model = stringValueLocal(ocrResponse.get("model"));
                }
                String prompt = includeOcr
                        ? DEFAULT_VQA_PROMPT_TEMPLATE.formatted(ocrText.isBlank() ? "No OCR used" : ocrText, question)
                        : question;
                Map<String, Object> answerResponse = invoker.answer(
                        imagePathOrUrl,
                        prompt,
                        withVisionPayload(inputs, prompt, imageContent, config)
                );
                String answerModel = stringValueLocal(answerResponse.get("model"));
                if (!answerModel.isBlank()) {
                    model = answerModel;
                }
                if (model.isBlank()) {
                    model = config.getModel();
                }
                Map<String, Object> data = linkedMap();
                data.put("answer", answerResponse.get("answer"));
                data.put("ocr_text", includeOcr ? ocrText : null);
                data.put("model", model);
                return ToolOutput.success(data);
            } catch (Exception exception) {
                return ToolOutput.failure(exception.getMessage());
            }
        }
    }

    private static DeepAgentConfig.VisionModelConfig requireVisionModelConfig(
            DeepAgentConfig.VisionModelConfig visionModelConfig
    ) {
        if (visionModelConfig == null) {
            throw new IllegalArgumentException(
                    "Vision model config is not set. Pass DeepAgentConfig.vision_model_config "
                            + "or construct the tool with VisionModelConfig."
            );
        }
        if (visionModelConfig.getApiKey() == null || visionModelConfig.getApiKey().isBlank()) {
            throw new IllegalArgumentException("Vision model config missing api_key.");
        }
        if (visionModelConfig.getBaseUrl() == null || visionModelConfig.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Vision model config missing base_url.");
        }
        if (visionModelConfig.getModel() == null || visionModelConfig.getModel().isBlank()) {
            throw new IllegalArgumentException("Vision model config missing model.");
        }
        return visionModelConfig;
    }

    private static String imagePathOrUrl(Map<String, Object> inputs) {
        String value = stringValueLocal(inputs == null ? null : inputs.get("image_path_or_url")).trim();
        if (value.isEmpty()) {
            value = stringValueLocal(inputs == null ? null : inputs.get("image_path")).trim();
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("image_path_or_url is required");
        }
        return value;
    }

    private static Map<String, Object> buildImageContent(String imagePathOrUrl) throws Exception {
        if (imagePathOrUrl.contains(SANDBOX_PATH_MARKER)) {
            throw new IllegalArgumentException(
                    "Vision tools cannot access sandbox-only paths. Use a local path outside the sandbox or an https URL."
            );
        }
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        if (isHttpUrl(imagePathOrUrl)) {
            imageUrl.put("url", imagePathOrUrl);
        } else {
            Path imagePath = Path.of(imagePathOrUrl).toAbsolutePath().normalize();
            if (!Files.exists(imagePath) || !Files.isRegularFile(imagePath)) {
                throw new FileNotFoundException("Image path does not exist or is not a file: " + imagePathOrUrl);
            }
            String encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            imageUrl.put("url", "data:" + guessMimeType(imagePath.toString()) + ";base64," + encoded);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "image_url");
        result.put("image_url", imageUrl);
        return result;
    }

    private static boolean isHttpUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private static Map<String, Object> withVisionPayload(Map<String, Object> inputs,
                                                         String prompt,
                                                         Map<String, Object> imageContent,
                                                         DeepAgentConfig.VisionModelConfig config) {
        Map<String, Object> payload = new LinkedHashMap<>(inputs == null ? Map.of() : inputs);
        payload.put("prompt", prompt);
        payload.put("image_content", imageContent);
        payload.put("vision_model_config", config);
        return payload;
    }

    private static String stringValueLocal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
