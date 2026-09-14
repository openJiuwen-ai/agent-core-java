/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code PaidSearchMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/web_tools.py}.
 */
public class PaidSearchMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn",
            "配置 API 时这是首选联网搜索工具；对搜索、最新、当前信息任务应先调用 paid_search，再考虑 free_search 兜底。"
                    + "付费搜索，支持 provider=auto|bocha|perplexity|serper|jina。"
                    + "当用户询问最新、当前、今年、实时、近期等信息时，query 必须使用系统提示中的当前年份或日期；",
            "en",
            "Paid search via Bocha/Perplexity/SERPER/JINA. Support provider=auto|bocha|perplexity|serper|jina. "
                    + "When available, this is the preferred web search tool; call it before free_search for search, latest, "
                    + "current, or recent-information tasks. "
                    + "For latest/current/this-year/recent information, the query must use the current year "
                    + "or date from the system prompt. "
    );

    @Override
    public String getName() {
        return "paid_search";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getPaidSearchInputParams(language);
    }

    public static Map<String, Object> getPaidSearchInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", property("string", description(language, "付费搜索查询文本。", "Paid search query text.")));
        properties.put("provider", property(
                "string",
                description(language, "Provider: auto|bocha|perplexity|serper|jina。", "Provider: auto|bocha|perplexity|serper|jina."),
                "auto"
        ));
        properties.put("max_results", property("integer", description(language, "最大 URL 数（1-20）。", "Maximum number of URLs (1-20)."), 8));
        Map<String, Object> timeout = property(
                "integer",
                description(language, "请求超时时间（秒，30-300）。", "Request timeout in seconds (30-300)."),
                180
        );
        timeout.put("minimum", 30);
        timeout.put("maximum", 300);
        properties.put("timeout_seconds", timeout);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    private static String description(String language, String chinese, String english) {
        return "en".equals(language) ? english : chinese;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> property(String type, String description, Object defaultValue) {
        Map<String, Object> property = property(type, description);
        property.put("default", defaultValue);
        return property;
    }
}
