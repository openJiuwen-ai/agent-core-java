package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.schema.config.VisionModelConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code ImageOCRTool} in {@code openjiuwen.harness.tools.multimodal.vision}.
 */
public class ImageOCRTool extends AbstractHarnessTool {

    private final VisionModelConfig visionModelConfig;

    public ImageOCRTool(VisionModelConfig visionModelConfig) {
        super(toolCard("image_ocr", "image_ocr", "Extract text from an image."), null);
        this.visionModelConfig = visionModelConfig;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String imagePathOrUrl = String.valueOf(inputs.getOrDefault("image_path_or_url", ""));
        Map<String, Object> imageContent = buildImageContent(imagePathOrUrl);
        String text = callVisionModel(imagePathOrUrl, "ocr", visionModelConfig);
        return new ToolOutput(true, Map.of("text", text, "image_content", imageContent), null);
    }

    protected String callVisionModel(String imagePathOrUrl, String prompt, VisionModelConfig configuredModel) {
        return "";
    }

    protected Map<String, Object> buildImageContent(String imagePathOrUrl) throws Exception {
        if (imagePathOrUrl.startsWith("http://") || imagePathOrUrl.startsWith("https://")) {
            return Map.of("type", "image_url", "image_url", Map.of("url", imagePathOrUrl));
        }
        byte[] bytes = Files.readAllBytes(Path.of(imagePathOrUrl));
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + base64));
    }
}
