/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.net.URLConnection;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Vision multimodal tool helpers.
 *
 * <p>Mirrors Python's OCR/VQA helpers and tool classes in
 * {@code openjiuwen/harness/tools/multimodal/vision.py}.</p>
 */
public final class VisionTools {

    private VisionTools() {
    }

    public static String guessMimeType(String filePath) {
        String guess = URLConnection.guessContentTypeFromName(filePath == null ? "" : Path.of(filePath).getFileName().toString());
        return guess == null ? "image/png" : guess;
    }

    public static List<Tool> createVisionTools(VisionInvoker invoker) {
        return List.of(new ImageOcrTool(invoker), new VisualQuestionAnsweringTool(invoker));
    }

    public interface VisionInvoker {
        Map<String, Object> ocr(String imagePath, Map<String, Object> inputs) throws Exception;

        Map<String, Object> answer(String imagePath, String question, Map<String, Object> inputs) throws Exception;
    }

    /**
     * Mirrors Python's {@code ImageOCRTool} in {@code openjiuwen/harness/tools/multimodal/vision.py}.
     */
    public static class ImageOcrTool extends AbstractHarnessTool {
        private final VisionInvoker invoker;

        public ImageOcrTool(VisionInvoker invoker) {
            super(toolCard("image_ocr", "ImageOCRTool", "Extract visible text from an image."));
            this.invoker = invoker;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String imagePath = requiredString(inputs, "image_path");
            if (invoker == null) {
                return ToolOutput.failure("vision model config is not configured");
            }
            return ToolOutput.success(invoker.ocr(imagePath, inputs == null ? Map.of() : inputs));
        }
    }

    /**
     * Mirrors Python's {@code VisualQuestionAnsweringTool} in
     * {@code openjiuwen/harness/tools/multimodal/vision.py}.
     */
    public static class VisualQuestionAnsweringTool extends AbstractHarnessTool {
        private final VisionInvoker invoker;

        public VisualQuestionAnsweringTool(VisionInvoker invoker) {
            super(toolCard("visual_question_answering", "VisualQuestionAnsweringTool",
                    "Answer a question about an image."));
            this.invoker = invoker;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            String imagePath = requiredString(inputs, "image_path");
            String question = requiredString(inputs, "question");
            if (invoker == null) {
                return ToolOutput.failure("vision model config is not configured");
            }
            return ToolOutput.success(invoker.answer(imagePath, question, inputs == null ? Map.of() : inputs));
        }
    }
}
