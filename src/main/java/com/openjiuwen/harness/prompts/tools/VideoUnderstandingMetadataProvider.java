/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Bilingual tool description and input params for VideoUnderstanding tool.
 * <p>
 * Mirrors Python's {@code VideoUnderstandingMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.video_understanding}.
 */
public class VideoUnderstandingMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn", "理解视频内容并回答用户问题，支持远程视频 URL 或本地视频文件路径。");
        DESCRIPTIONS.put("en", "Understand video content and answer user queries. "
                + "Supports remote video URLs or local video file paths.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("query", Map.of("type", "string", "description", "用户关于视频内容的问题"));
        cnProps.put("video_path", Map.of("type", "string", "description", "本地视频路径或远程视频 URL"));
        cnProps.put("model", Map.of("type", "string", "description", "可选，指定模型名称"));
        cnProps.put("max_tokens", Map.of("type", "integer", "description", "可选，最大输出 token 数"));
        cnProps.put("temperature", Map.of("type", "number", "description", "可选，采样温度"));
        cnProps.put("timeout_seconds", Map.of("type", "integer", "description", "可选，请求超时时间（秒）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Collections.singletonList("query"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("query", Map.of("type", "string", "description", "User query about the video content"));
        enProps.put("video_path", Map.of("type", "string", "description", "Local video path or remote video URL"));
        enProps.put("model", Map.of("type", "string", "description", "Optional model name"));
        enProps.put("max_tokens", Map.of("type", "integer", "description", "Optional maximum output tokens"));
        enProps.put("temperature", Map.of("type", "number", "description", "Optional sampling temperature"));
        enProps.put("timeout_seconds", Map.of("type", "integer", "description", "Optional timeout in seconds"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Collections.singletonList("query"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "video_understanding";
    }

    /**
     * Get description for the specified language.
     * Static method for convenience in static contexts.
     *
     * @param language language code (cn/en)
     * @return description string
     */
    public static String getStaticDescription(String language) {
        return DESCRIPTIONS.getOrDefault(language, DESCRIPTIONS.get("cn"));
    }

    @Override
    public String getDescription(String language) {
        return getStaticDescription(language);
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return INPUT_PARAMS.getOrDefault(language, INPUT_PARAMS.get("cn"));
    }
}