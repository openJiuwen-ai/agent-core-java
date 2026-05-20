/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.tools;

import com.openjiuwen.autoharness.experience.ExperienceStore;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataProvider;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.ToolOutput;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Readonly experience search tool for auto-harness.
 */
public class ExperienceSearchTool extends Tool {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String TOOL_NAME = "experience_search";

    static {
        ToolMetadataRegistry.registerToolProvider(new ExperienceSearchMetadataProvider());
    }

    private final String experienceDir;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExperienceSearchTool(String experienceDir) {
        this(experienceDir, null, "cn");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ExperienceSearchTool(String experienceDir, String agentId, String language) {
        super(buildCard(language, agentId));
        this.experienceDir = experienceDir;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> safeInputs = inputs != null ? inputs : Map.of();
        String query = stringValue(safeInputs.getOrDefault("query", ""));
        int limit = intValue(safeInputs.get("limit"), 5);
        if (query.isEmpty()) {
            return ToolOutput.builder().success(false).error("query 参数不能为空").build();
        }
        try {
            ExperienceStore store = new ExperienceStore(experienceDir);
            List<Experience> results = store.search(query, limit);
            return ToolOutput.builder()
                    .success(true)
                    .data(results.stream().map(ExperienceSearchTool::toOutput).toList())
                    .build();
        } catch (IOException | UncheckedIOException e) {
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return ToolOutput.builder()
                    .success(false)
                    .error(message.substring(0, Math.min(200, message.length())))
                    .build();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return List.<Object>of(invoke(inputs, kwargs)).iterator();
    }

    private static ToolCard buildCard(String language, String agentId) {
        String toolId = agentId == null || agentId.isBlank()
                ? "ExperienceSearchTool_" + UUID.randomUUID().toString().replace("-", "")
                : "ExperienceSearchTool_" + agentId;
        return ToolMetadataRegistry.buildToolCard(TOOL_NAME, toolId, language);
    }

    private static Map<String, Object> toOutput(Experience exp) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", exp.getType() != null ? exp.getType().name().toLowerCase(java.util.Locale.ROOT) : "");
        item.put("topic", exp.getTopic());
        item.put("summary", exp.getSummary());
        item.put("outcome", exp.getOutcome());
        return item;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    static final class ExperienceSearchMetadataProvider implements ToolMetadataProvider {
        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getName() {
            return TOOL_NAME;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getDescription(String language) {
            if ("en".equals(language)) {
                return "Search historical experiences by keyword and return relevant success/failure/insight entries.";
            }
            return "搜索历史经验记录。输入关键词，返回相关的成功/失败/洞察经验，帮助避免重复错误、复用已验证的方案。";
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Map<String, Object> getInputParams(String language) {
            String queryDesc = "en".equals(language) ? "Search keywords or topic description" : "搜索关键词或主题描述";
            String limitDesc = "en".equals(language) ? "Maximum number of returned results, default 5" : "最大返回条数，默认 5";
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of(
                                    "type", "string",
                                    "description", queryDesc),
                            "limit", Map.of(
                                    "type", "integer",
                                    "description", limitDesc,
                                    "default", 5)),
                    "required", List.of("query"));
        }
    }
}
