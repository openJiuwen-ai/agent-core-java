package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code VisualQuestionAnsweringTool} in {@code openjiuwen.harness.tools.multimodal.vision}.
 */
public class VisualQuestionAnsweringTool extends AbstractHarnessTool {

    private final VisionModelConfig visionModelConfig;

    public VisualQuestionAnsweringTool(VisionModelConfig visionModelConfig) {
        super(toolCard("visual_question_answering", "visual_question_answering", "Answer questions about an image."), null);
        this.visionModelConfig = visionModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String imagePathOrUrl = String.valueOf(inputs.getOrDefault("image_path_or_url", ""));
        String question = String.valueOf(inputs.getOrDefault("question", ""));
        boolean includeOcr = !Boolean.FALSE.equals(inputs.get("include_ocr"));
        String ocrText = includeOcr ? callVisionModel(imagePathOrUrl, "ocr", visionModelConfig) : "";
        String prompt = includeOcr
                ? "OCR result:\n" + ocrText + "\n\nQuestion:\n" + question
                : question;
        String answer = callVisionModel(imagePathOrUrl, prompt, visionModelConfig);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        data.put("ocr_text", ocrText);
        return new ToolOutput(true, data, null);
    }

    protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
        return "";
    }
}
