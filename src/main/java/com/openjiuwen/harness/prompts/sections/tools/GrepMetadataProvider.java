/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Grep tool metadata provider.
 * 
 * @since 0.1.7
 */
public final class GrepMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "grep";
    }

    /**
     * getDescription.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language, "在文件中搜索内容。支持正则表达式。",
                "Search file contents with regex, structured output modes, pagination, context lines, file-type "
                        + "filters, and glob filters.");
    }

    /**
     * getInputParams.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport
                .objectSchema(
                        ToolSchemaSupport.properties(new Object[]{"pattern",
                                ToolSchemaSupport.property(
                                        "string", text(language, "搜索模式（正则表达式）", "Search pattern (regular expression)")),
                                "path",
                                ToolSchemaSupport.property("string", text(language, "搜索路径（文件或目录），默认为当前工作目录",
                                        "Search path, file or directory. Defaults to the current working directory")),
                                "ignore_case",
                                ToolSchemaSupport.property("boolean",
                                        text(language, "忽略大小写（兼容旧字段）", "Ignore case (legacy compatibility alias)")),
                                "glob",
                                ToolSchemaSupport
                                        .property("string",
                                                text(language, "glob 过滤模式，例如 *.py 或 *.{ts,tsx}",
                                                        "Glob filter pattern such as *.py or *.{ts,tsx}")),
                                "output_mode",
                                ToolSchemaSupport.enumProperty(
                                        "string", List.of("content", "files_with_matches", "count"),
                                        text(language, "输出模式：content、files_with_matches 或 count，默认 content",
                                                "Output mode: content, files_with_ma"
                                                        + "tches, or count. Defaults to content")),
                                "-B",
                                ToolSchemaSupport.property("integer", text(
                                        language, "每个匹配前显示的上下文行数，仅在 content 模式生效",
                                        "Lines of leading context before each match; only used in content mode")),
                                "-A",
                                ToolSchemaSupport.property("integer", text(language, "每个匹配后显示的上下文行数，仅在 content 模式生效",
                                        "Lines of trailing context after each match; only used in content mode")),
                                "-C",
                                ToolSchemaSupport.property("integer", text(language, "每个匹配前后都显示的上下文行数，仅在 content 模式生效",
                                        "Lines of context before and after each match; only used in content mode")),
                                "context",
                                ToolSchemaSupport.property("integer",
                                        text(language, "-C 的别名，用于设置前后对称上下文行数",
                                                "Alias of -C for symmetric context lines")),
                                "-n",
                                ToolSchemaSupport.property(
                                        "boolean",
                                        text(language, "在 content 模式显示行号，默认 true",
                                                "Show line numbers in content mode. Defaults to true")),
                                "-i",
                                ToolSchemaSupport
                                        .property("boolean", text(language, "大小写不敏感搜索", "Case-insensitive search")),
                                "type",
                                ToolSchemaSupport.property(
                                        "string",
                                        text(language, "文件类型过滤，例如 py、js、ts，需要 rg",
                                                "File type filter such as py, js, or ts. Requires rg")),
                                "head_limit",
                                ToolSchemaSupport.property(
                                        "integer",
                                        text(language, "只返回前 N 条记录或行。0 表示不限制，默认 250",
                                                "Return only the first N entries or line"
                                                        + "s. Use 0 for unlimited. Defaults to 250")),
                                "offset",
                                ToolSchemaSupport.property("integer", text(language, "先跳过前 N 条记录或行，再应用 head_limit，默认 0",
                                        "Skip the first N entries or lines before applying head_limit. Defaults to 0")),
                                "multiline",
                                ToolSchemaSupport.property("boolean",
                                        text(language, "启用多行正则模式，需要 rg", "Enable multiline regex mode. Requires rg"))}),
                        List.of("pattern"));
    }

    /**
     * text.
     * 
     * @param language language
     * @param cn cn
     * @param en en
     * @return the result
     * @since 0.1.7
     */
    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
