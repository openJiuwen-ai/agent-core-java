package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.util.Map;

/**
 * Mirrors Python's {@code ImageOCRTool} in {@code openjiuwen.harness.tools.multimodal.vision}.
 */
public class ImageOCRTool extends AbstractHarnessTool {

    private final VisionModelConfig visionModelConfig;

    public ImageOCRTool(VisionModelConfig visionModelConfig) {
        this("cn", visionModelConfig);
    }

    public ImageOCRTool(String language, VisionModelConfig visionModelConfig) {
        super(toolCard("image_ocr", "image_ocr", description(language)), null);
        this.visionModelConfig = visionModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String imagePathOrUrl = stringInput(inputs, "image_path_or_url");
        String prompt = stringInput(inputs, "prompt");
        if (prompt.isBlank()) {
            prompt = VisionTools.DEFAULT_OCR_PROMPT;
        }

        try {
            Map<String, Object> imageContent = buildImageContent(imagePathOrUrl);
            String text = callVisionModel(imagePathOrUrl, prompt, visionModelConfig);
            return new ToolOutput(
                    true,
                    Map.of(
                            "text", text,
                            "model", visionModelConfig != null ? visionModelConfig.getModel() : "",
                            "image_content", imageContent
                    ),
                    null
            );
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

    protected Map<String, Object> buildImageContent(String imagePathOrUrl) throws Exception {
        return VisionTools.buildImageContent(imagePathOrUrl);
    }

    private static String stringInput(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String description(String language) {
        return "Extract text from an image.";
    }
}
