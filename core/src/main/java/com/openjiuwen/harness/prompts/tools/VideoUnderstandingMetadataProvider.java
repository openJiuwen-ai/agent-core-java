/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code VideoUnderstandingMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/video_understanding.py}.
 */
public class VideoUnderstandingMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn", "理解视频内容并回答用户问题，支持远程视频 URL 或本地视频文件路径。",
            "en", "Understand video content and answer user queries. Supports remote video URLs or local video file paths."
    );

    private static final Map<String, Map<String, String>> VIDEO_UNDERSTANDING_PARAMS = createInputParamDescriptions();

    @Override
    public String getName() {
        return "video_understanding";
    }

    public static String getStaticDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public String getDescription(String language) {
        return getStaticDescription(language);
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getVideoUnderstandingInputParams(language);
    }

    public static Map<String, Object> getVideoUnderstandingInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", propertySchema("query", language, "string"));
        properties.put("video_path", propertySchema("video_path", language, "string"));
        properties.put("model", propertySchema("model", language, "string"));
        properties.put("max_tokens", propertySchema("max_tokens", language, "integer"));
        properties.put("temperature", propertySchema("temperature", language, "number"));
        properties.put("timeout_seconds", propertySchema("timeout_seconds", language, "integer"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query", "video_path"));
        return schema;
    }

    private static Map<String, Map<String, String>> createInputParamDescriptions() {
        Map<String, Map<String, String>> descriptions = new LinkedHashMap<>();
        descriptions.put("query", Map.of(
                "cn", "用户关于视频内容的问题",
                "en", "User query about the video content"
        ));
        descriptions.put("video_path", Map.of(
                "cn", "本地视频路径或远程视频 URL",
                "en", "Local video path or remote video URL"
        ));
        descriptions.put("model", Map.of(
                "cn", "可选，指定模型名称",
                "en", "Optional model name"
        ));
        descriptions.put("max_tokens", Map.of(
                "cn", "可选，最大输出 token 数",
                "en", "Optional maximum output tokens"
        ));
        descriptions.put("temperature", Map.of(
                "cn", "可选，采样温度",
                "en", "Optional sampling temperature"
        ));
        descriptions.put("timeout_seconds", Map.of(
                "cn", "可选，请求超时时间（秒）",
                "en", "Optional timeout in seconds"
        ));
        return descriptions;
    }

    private static Map<String, Object> propertySchema(String key, String language, String type) {
        Map<String, String> descriptions = VIDEO_UNDERSTANDING_PARAMS.get(key);
        String description = descriptions.getOrDefault(language, descriptions.get("cn"));
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }
}
