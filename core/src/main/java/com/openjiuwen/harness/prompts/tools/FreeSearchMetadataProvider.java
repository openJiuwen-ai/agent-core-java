/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code FreeSearchMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/web_tools.py}.
 */
public class FreeSearchMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn",
            "如果 paid_search 可用或已配置 API，优先使用 paid_search；free_search 仅作为兜底或用户明确要求免费搜索时使用。"
                    + "免费搜索，返回结果 URL 和摘要。如果前几条结果看起来相关但还不足以直接回答任务，"
                    + "应先抓取前 1-3 条中的至少 2 条；如果第一条抓取失败、是动态壳页或内容仍然不完整，"
                    + "就继续抓下一条，而不是立刻继续改写搜索词。"
                    + "当用户询问最新、当前、今年、实时、近期等信息时，query 必须使用系统提示中的当前年份或日期；",
            "en",
            "Free search. If paid_search is available/configured, call paid_search first; "
                    + "use free_search only as fallback or when the user explicitly asks for free search. "
                    + "Input a query and return result URLs with snippets. "
                    + "If the top results look relevant but do not directly answer the task, "
                    + "you must fetch at least 2 of the top 1-3 results first. "
                    + "If the first fetch fails, is a dynamic shell page, or is still incomplete, "
                    + "continue with the next result instead of searching again immediately. "
                    + "For latest/current/this-year/recent information, the query must use the current year "
                    + "or date from the system prompt."
    );

    @Override
    public String getName() {
        return "free_search";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getFreeSearchInputParams(language);
    }

    public static Map<String, Object> getFreeSearchInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", property("string", description(
                language,
                "搜索查询文本。查询最新、当前、今年、实时、近期信息时，必须使用系统提示中的当前年份或日期。",
                "Free search query text. For latest/current/this-year/recent information, use the current year or date from the system prompt."
        )));
        properties.put("max_results", property("integer", description(language, "最大结果数（1-20）。", "Maximum number of results (1-20)."), 8));
        properties.put("timeout_seconds", property("integer", description(language, "请求超时时间（秒，5-60）。", "Request timeout in seconds (5-60)."), 20));

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
