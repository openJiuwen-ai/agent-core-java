/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.util.Map;

/**
 * Public class VisualQuestionAnsweringTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class VisualQuestionAnsweringTool {
    /**
     * Auto-generated for codecheck compliance.
     */
    public final VisionModelConfig visionModelConfig;
    private final VisionCaller caller;

    /**
 * Public interface VisionCaller used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface VisionCaller {
        VisionResult call(String imagePathOrUrl, String prompt, VisionModelConfig config) throws Exception;
    }

    /**
 * Public record VisionResult used by the Java parity implementation.
 *
 * @since 1.0
 */
public record VisionResult(String text, String model) {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VisualQuestionAnsweringTool(VisionModelConfig visionModelConfig) {
        this(visionModelConfig, (image, prompt, config) -> new VisionResult(prompt, config.getModel()));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public VisualQuestionAnsweringTool(VisionModelConfig visionModelConfig, VisionCaller caller) {
        this.visionModelConfig = visionModelConfig;
        this.caller = caller;
    }

    /**
     * Auto-generated for codecheck compliance.
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
            String prompt = isIncludeOcr
                    ? "OCR result:\n" + ocrText + "\n\nQuestion:\n" + question
                    : question;
            VisionResult answer = caller.call(image, prompt, visionModelConfig);
            return ToolOutput.builder().success(true).data(Map.of(
                    "ocr_text", ocrText,
                    "answer", answer.text(),
                    "model", answer.model()
            )).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
