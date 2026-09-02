/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * Public class ImageOCRTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ImageOCRTool {
    private static final String DEFAULT_PROMPT = "Extract all visible text from the image.";

    /**
     * visionModelConfig.
     * 
     * @since 0.1.7
     */
    public final VisionModelConfig visionModelConfig;

    private final OcrInvoker invoker;

    /**
     * Public interface OcrInvoker used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface OcrInvoker {
        /**
         * invoke.
         * 
         * @param config config
         * @param prompt prompt
         * @param imageContent imageContent
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        String invoke(VisionModelConfig config, String prompt, Map<String, Object> imageContent) throws Exception;
    }

    /**
     * ImageOCRTool.
     * 
     * @param visionModelConfig visionModelConfig
     * @since 0.1.7
     */
    public ImageOCRTool(VisionModelConfig visionModelConfig) {
        this(visionModelConfig, (config, prompt, imageContent) -> "ocr not configured");
    }

    /**
     * ImageOCRTool.
     * 
     * @param visionModelConfig visionModelConfig
     * @param invoker invoker
     * @since 0.1.7
     */
    public ImageOCRTool(VisionModelConfig visionModelConfig, OcrInvoker invoker) {
        this.visionModelConfig = visionModelConfig;
        this.invoker = invoker;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput invoke(Map<String, Object> inputs) {
        if (visionModelConfig == null) {
            return ToolOutput.builder().success(false).error("Vision model config is not set.").build();
        }
        try {
            String path = String.valueOf(inputs.get("image_path_or_url"));
            String prompt = String.valueOf(inputs.getOrDefault("prompt", DEFAULT_PROMPT));
            Map<String, Object> imageContent = buildImageContent(path);
            String text = invoker.invoke(visionModelConfig, prompt, imageContent);
            return ToolOutput.builder().success(true).data(Map.of("text", text, "model", visionModelConfig.getModel()))
                    .build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    /**
     * buildImageContent.
     * 
     * @param imagePathOrUrl imagePathOrUrl
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private static Map<String, Object> buildImageContent(String imagePathOrUrl) throws Exception {
        if (imagePathOrUrl.startsWith("http://") || imagePathOrUrl.startsWith("https://")) {
            return Map.of("type", "image_url", "image_url", Map.of("url", imagePathOrUrl));
        }
        Path path = Path.of(imagePathOrUrl);
        String mime = Files.probeContentType(path);
        String url = "data:" + (mime != null ? mime : "image/png") + ";base64,"
                + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        return Map.of("type", "image_url", "image_url", Map.of("url", url));
    }
}
