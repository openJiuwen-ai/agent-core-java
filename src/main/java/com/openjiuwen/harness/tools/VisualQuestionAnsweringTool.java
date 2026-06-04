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
        this("cn", visionModelConfig);
    }

    public VisualQuestionAnsweringTool(String language, VisionModelConfig visionModelConfig) {
        super(toolCard("visual_question_answering", "visual_question_answering", description(language)), null);
        this.visionModelConfig = visionModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String imagePathOrUrl = stringInput(inputs, "image_path_or_url");
        String question = stringInput(inputs, "question");
        boolean includeOcr = !Boolean.FALSE.equals(inputs.get("include_ocr"));
        String ocrPrompt = stringInput(inputs, "ocr_prompt");
        if (ocrPrompt.isBlank()) {
            ocrPrompt = VisionTools.DEFAULT_OCR_PROMPT;
        }

        try {
            String ocrText = "";
            String model = "";
            if (includeOcr) {
                ocrText = callVisionModel(imagePathOrUrl, ocrPrompt, visionModelConfig);
                model = visionModelConfig != null ? visionModelConfig.getModel() : "";
            }

            String prompt = includeOcr
                    ? VisionTools.defaultVqaPrompt(ocrText.isBlank() ? "No OCR used" : ocrText, question)
                    : question;
            String answer = callVisionModel(imagePathOrUrl, prompt, visionModelConfig);
            if (visionModelConfig != null && !visionModelConfig.getModel().isBlank()) {
                model = visionModelConfig.getModel();
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("answer", answer);
            data.put("ocr_text", includeOcr ? ocrText : null);
            data.put("model", model);
            return new ToolOutput(true, data, null);
        } catch (Exception exc) {
            return new ToolOutput(false, null, exc.getMessage());
        }
    }

    public VisionModelConfig getVisionModelConfig() {
        return visionModelConfig;
    }

    protected String callVisionModel(
            String imagePathOrUrl,
            String prompt,
            VisionModelConfig configuredModel
    ) throws Exception {
        return VisionTools.callVisionModel(imagePathOrUrl, prompt, configuredModel);
    }

    private static String stringInput(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String description(String language) {
        return "Answer questions about an image.";
    }
}
