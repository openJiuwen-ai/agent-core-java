/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code ImageOCRMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/vision.py}.
 */
public class ImageOCRMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn", "读取图片中的可见文本，适合 OCR、票据文本提取和截图文字识别。",
            "en", "Extract visible text from an image for OCR, screenshot text recognition, and document snippets."
    );

    private static final Map<String, Map<String, String>> IMAGE_OCR_PARAMS = createInputParamDescriptions();

    @Override
    public String getName() {
        return "image_ocr";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getImageOcrInputParams(language);
    }

    public static Map<String, Object> getImageOcrInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("image_path_or_url", propertySchema("image_path_or_url", language));
        properties.put("prompt", propertySchema("prompt", language));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("image_path_or_url"));
        return schema;
    }

    private static Map<String, Map<String, String>> createInputParamDescriptions() {
        Map<String, Map<String, String>> descriptions = new LinkedHashMap<>();
        descriptions.put("image_path_or_url", Map.of(
                "cn", "本地图片路径或公网 http(s) 图片 URL",
                "en", "Local image path or public http(s) image URL"
        ));
        descriptions.put("prompt", Map.of(
                "cn", "可选，自定义 OCR 提示词",
                "en", "Optional custom OCR prompt"
        ));
        return descriptions;
    }

    private static Map<String, Object> propertySchema(String key, String language) {
        Map<String, String> descriptions = IMAGE_OCR_PARAMS.get(key);
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", descriptions.getOrDefault(language, descriptions.get("cn")));
        return property;
    }
}
