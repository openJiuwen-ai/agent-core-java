/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.util.Map;

/**
 * Public class VisualQuestionAnsweringTool used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class VisualQuestionAnsweringTool {
    /**
     * visionModelConfig.
     * 
     * @since 0.1.7
     */
    public final VisionModelConfig visionModelConfig;
    private final VisionCaller caller;

    /**
     * Public interface VisionCaller used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface VisionCaller {
        /**
         * call.
         * 
         * @param imagePathOrUrl imagePathOrUrl
         * @param prompt prompt
         * @param config config
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        VisionResult call(String imagePathOrUrl, String prompt, VisionModelConfig config) throws Exception;
    }

    /**
     * Public record VisionResult used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record VisionResult(String text, String model) {
    }

    /**
     * VisualQuestionAnsweringTool.
     * 
     * @param visionModelConfig visionModelConfig
     * @since 0.1.7
     */
    public VisualQuestionAnsweringTool(VisionModelConfig visionModelConfig) {
        this(visionModelConfig, (image, prompt, config) -> new VisionResult(prompt, config.getModel()));
    }

    /**
     * VisualQuestionAnsweringTool.
     * 
     * @param visionModelConfig visionModelConfig
     * @param caller caller
     * @since 0.1.7
     */
    public VisualQuestionAnsweringTool(VisionModelConfig visionModelConfig, VisionCaller caller) {
        this.visionModelConfig = visionModelConfig;
        this.caller = caller;
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
            String image = String.valueOf(inputs.get("image_path_or_url"));
            String question = String.valueOf(inputs.get("question"));
            boolean isIncludeOcr = !Boolean.FALSE.equals(inputs.get("include_ocr"));
            String ocrText = null;
            if (isIncludeOcr) {
                ocrText = caller.call(image, "ocr", visionModelConfig).text();
            }
            String prompt = isIncludeOcr ? "OCR result:\n" + ocrText + "\n\nQuestion:\n" + question : question;
            VisionResult answer = caller.call(image, prompt, visionModelConfig);
            return ToolOutput.builder().success(true)
                    .data(Map.of("ocr_text", ocrText, "answer", answer.text(), "model", answer.model())).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
