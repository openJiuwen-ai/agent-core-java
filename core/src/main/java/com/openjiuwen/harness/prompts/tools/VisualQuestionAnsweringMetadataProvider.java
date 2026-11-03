/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code VisualQuestionAnsweringMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/vision.py}.
 */
public class VisualQuestionAnsweringMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn", "理解图片内容并回答问题，可选先做 OCR 再结合识别到的文字回答。",
            "en", "Understand an image and answer questions, optionally grounding the answer with OCR first."
    );

    private static final Map<String, Map<String, String>> VISUAL_QUESTION_ANSWERING_PARAMS = createInputParamDescriptions();

    @Override
    public String getName() {
        return "visual_question_answering";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getVisualQuestionAnsweringInputParams(language);
    }

    public static Map<String, Object> getVisualQuestionAnsweringInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("image_path_or_url", propertySchema("image_path_or_url", language, "string"));
        properties.put("question", propertySchema("question", language, "string"));
        properties.put("include_ocr", propertySchema("include_ocr", language, "boolean"));
        properties.put("ocr_prompt", propertySchema("ocr_prompt", language, "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("image_path_or_url", "question"));
        return schema;
    }

    private static Map<String, Map<String, String>> createInputParamDescriptions() {
        Map<String, Map<String, String>> descriptions = new LinkedHashMap<>();
        descriptions.put("image_path_or_url", Map.of(
                "cn", "本地图片路径或公网 http(s) 图片 URL",
                "en", "Local image path or public http(s) image URL"
        ));
        descriptions.put("question", Map.of(
                "cn", "要询问图片的问题",
                "en", "Question to ask about the image"
        ));
        descriptions.put("include_ocr", Map.of(
                "cn", "是否先执行 OCR 并把结果拼接进问答提示词，默认 true",
                "en", "Whether to run OCR first and inject the result into the VQA prompt, default true"
        ));
        descriptions.put("ocr_prompt", Map.of(
                "cn", "可选，自定义 OCR 提示词，仅在 include_ocr 为 true 时使用",
                "en", "Optional custom OCR prompt used only when include_ocr is true"
        ));
        return descriptions;
    }

    private static Map<String, Object> propertySchema(String key, String language, String type) {
        Map<String, String> descriptions = VISUAL_QUESTION_ANSWERING_PARAMS.get(key);
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", descriptions.getOrDefault(language, descriptions.get("cn")));
        return property;
    }
}
