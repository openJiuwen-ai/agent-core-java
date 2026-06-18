/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.tools;

import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.prompts.tools.HarnessPromptToolsPackage;
import com.openjiuwen.harness.prompts.tools.ToolMetadataProvider;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Read-only tool for searching stored auto-harness experiences.
 *
 * <p>Mirrors Python's {@code ExperienceSearchTool} in
 * {@code openjiuwen/auto_harness/tools/experience_search_tool.py}.</p>
 */
public class ExperienceSearchTool extends Tool {

    public static final String TOOL_NAME = "experience_search";
    private static final String TOOL_ID_PREFIX = "ExperienceSearchTool";
    private static final int DEFAULT_LIMIT = 5;
    private static final ToolMetadataProvider METADATA_PROVIDER = new ExperienceSearchMetadataProvider();

    static {
        METADATA_PROVIDER.validate();
        HarnessPromptToolsPackage.registerToolProvider(METADATA_PROVIDER);
    }

    private final String experienceDir;

    public ExperienceSearchTool(String experienceDir) {
        this(experienceDir, null, "cn");
    }

    public ExperienceSearchTool(String experienceDir, String agentId, String language) {
        super(buildToolCard(agentId, language));
        this.experienceDir = experienceDir;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        Object rawQuery = safeInputs.getOrDefault("query", "");
        if (isFalsey(rawQuery)) {
            return ToolOutput.failure("query 参数不能为空");
        }

        try {
            int limit = resolveLimit(safeInputs.getOrDefault("limit", DEFAULT_LIMIT));
            List<Experience> results = new ExperienceStore(experienceDir)
                    .search(String.valueOf(rawQuery), limit)
                    .join();
            return ToolOutput.success(toSearchRows(results));
        } catch (RuntimeException exception) {
            return ToolOutput.failure(truncatedMessage(exception));
        }
    }

    @Override
    protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.of(invokeInternal(inputs, kwargs)).iterator();
    }

    private static ToolCard buildToolCard(String agentId, String language) {
        String suffix = agentId == null || agentId.isEmpty()
                ? UUID.randomUUID().toString().replace("-", "")
                : agentId;
        return new ToolCard(
                TOOL_ID_PREFIX + "_" + suffix,
                TOOL_NAME,
                HarnessPromptToolsPackage.getToolDescription(TOOL_NAME, language),
                HarnessPromptToolsPackage.getToolInputParams(TOOL_NAME, language)
        );
    }

    private static boolean isFalsey(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean bool) {
            return !bool;
        }
        if (value instanceof CharSequence text) {
            return text.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() == 0.0;
        }
        if (value instanceof Iterable<?> iterable) {
            return !iterable.iterator().hasNext();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private static int resolveLimit(Object value) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("limit must be numeric");
    }

    private static List<Map<String, Object>> toSearchRows(List<Experience> experiences) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Experience experience : experiences) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", experience.getType() == null ? null : experience.getType().value());
            row.put("topic", experience.getTopic());
            row.put("summary", experience.getSummary());
            row.put("outcome", experience.getOutcome());
            rows.add(row);
        }
        return rows;
    }

    private static String truncatedMessage(RuntimeException exception) {
        Throwable cause = exception instanceof CompletionException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        String message = cause.getMessage();
        if (message == null || message.isEmpty()) {
            message = cause.getClass().getName();
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

    /**
     * Metadata provider for the experience-search tool.
     *
     * <p>Mirrors Python's {@code ExperienceSearchMetadataProvider} in
     * {@code openjiuwen/auto_harness/tools/experience_search_tool.py}.</p>
     */
    public static final class ExperienceSearchMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return TOOL_NAME;
        }

        @Override
        public String getDescription(String language) {
            if ("en".equals(language)) {
                return "Search historical experiences by keyword and return relevant success/failure/insight entries.";
            }
            return "搜索历史经验记录。输入关键词，返回相关的成功/失败/洞察经验，帮助避免重复错误。复用已验证的方案。";
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            boolean english = "en".equals(language);
            Map<String, Object> query = property("string", english
                    ? "Search keywords or topic description"
                    : "搜索关键词或主题描述");
            Map<String, Object> limit = property("integer", english
                    ? "Maximum number of returned results, default 5"
                    : "最大返回条数，默认 5");
            limit.put("default", DEFAULT_LIMIT);

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("query", query);
            properties.put("limit", limit);

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            schema.put("required", List.of("query"));
            return schema;
        }

        private static Map<String, Object> property(String type, String description) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", type);
            property.put("description", description);
            return property;
        }
    }
}
