/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Memory tool metadata providers.
 *
 * @since 0.1.7
 */
final class MemoryMetadataProviders {
    /**
     * MemoryMetadataProviders.
     * 
     * @since 0.1.7
     */
    private MemoryMetadataProviders() {
    }

    static final class MemorySearchMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "memory_search";
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
            return text(language, "在长期记忆中检索过往信息（决策、偏好、人物、日期、TODO 等），返回相关片段与引用线索。",
                    "Search long-term memory and return relevant snippets and references.");
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
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[]{"query",
                            ToolSchemaSupport.property("string", text(language, "检索关键词或问题", "Search query string")),
                            "max_results",
                            ToolSchemaSupport.property(
                                    "integer", text(language, "最多返回条数（可选）", "Maximum number of results (optional)")),
                            "min_score",
                            ToolSchemaSupport.property(
                                    "number",
                                    text(language, "最小相关度阈值（可选）", "Minimum relevance score threshold (optional)")),
                            "session_key",
                            ToolSchemaSupport
                                    .property("string",
                                            text(language, "会话键（可选，用于上下文隔离/过滤）",
                                                    "Session key (optional, for scoping/filtering)"))}),
                    List.of("query"));
        }
    }

    static final class MemoryGetMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "memory_get";
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
            return text(language, "按行号切片读取 memory/ 下的记忆 Markdown 文件内容（from_line + lines）。",
                    "Read a slice of a memory markdown file under memory/ (from_line + lines).");
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
            return pathWindowSchema(language, "from_line", "lines");
        }
    }

    static final class WriteMemoryMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "write_memory";
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
            return text(language, "写入记忆内容到 memory/ 下的 Markdown 文件；支持覆盖写或追加写（append）。",
                    "Write memory content to a markdown file under memory/; supports overwrite or append.");
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
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[]{"path", pathProperty(language), "content",
                            ToolSchemaSupport.property("string", text(language, "要写入的内容", "Content to write")),
                            "append",
                            ToolSchemaSupport.property("boolean",
                                    text(language, "是否追加写入（默认 false）",
                                            "Append to file instead of overwrite (default false)"))}),
                    List.of("path", "content"));
        }
    }

    static final class EditMemoryMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "edit_memory";
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
            return text(language, "在 memory/ 下的记忆文件中做精确字符串替换（old_text → new_text）。",
                    "Perform an exact string replacement inside a memory file (old_text -> new_text).");
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
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[]{"path", pathProperty(language), "old_text",
                            ToolSchemaSupport.property("string",
                                    text(language, "要替换的原始文本", "Original text to isReplace")),
                            "new_text",
                            ToolSchemaSupport.property("string", text(language, "替换后的新文本", "New replacement text"))}),
                    List.of("path", "old_text", "new_text"));
        }
    }

    static final class ReadMemoryMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "read_memory";
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
            return text(language, "按 offset/limit 读取 memory/ 下记忆文件的部分内容（用于分页阅读）。",
                    "Read a portion of a memory file under memory/ using offset/limit.");
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
            return pathWindowSchema(language, "offset", "limit");
        }
    }

    static final class CodingMemoryReadMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "coding_memory_read";
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
            return text(language, "按 offset/limit 读取 coding_memory/ 下的编码记忆 Markdown 文件。",
                    "Read a coding memory markdown file under coding_memory/ using offset/limit.");
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
            return pathWindowSchema(language, "offset", "limit");
        }
    }

    static final class CodingMemoryWriteMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "coding_memory_write";
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
            return text(language, "写入编码记忆到 coding_memory/ 下的 Markdown 文件；内容必须包含 frontmatter。",
                    "Write coding memory to a markdown file under coding_memory/; content must include frontmatter.");
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
                            ToolSchemaSupport.properties(new Object[]{"path", pathProperty(language), "content",
                                    ToolSchemaSupport.property("string",
                                            text(language, "要写入的内容", "Content to write"))}),
                            List.of("path", "content"));
        }
    }

    static final class CodingMemoryEditMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "coding_memory_edit";
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
            return text(language, "在 coding_memory/ 下的编码记忆文件中做精确字符串替换（old_text → new_text）。",
                    "Perform an exact string replacement inside a coding memory file (old_text -> new_text).");
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
            return ToolSchemaSupport.objectSchema(
                    ToolSchemaSupport.properties(new Object[]{"path", pathProperty(language), "old_text",
                            ToolSchemaSupport.property("string",
                                    text(language, "要替换的原始文本", "Original text to isReplace")),
                            "new_text",
                            ToolSchemaSupport.property("string", text(language, "替换后的新文本", "New replacement text"))}),
                    List.of("path", "old_text", "new_text"));
        }
    }

    /**
     * pathWindowSchema.
     * 
     * @param language language
     * @param startField startField
     * @param countField countField
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> pathWindowSchema(String language, String startField, String countField) {
        return ToolSchemaSupport
                .objectSchema(
                        ToolSchemaSupport.properties(new Object[]{"path", pathProperty(language), startField,
                                ToolSchemaSupport.property(
                                        "integer", text(language, "起始行号（可选）", "Starting line number (optional)")),
                                countField,
                                ToolSchemaSupport.property("integer",
                                        text(language, "读取行数（可选）", "Number of lines to read (optional)"))}),
                        List.of("path"));
    }

    /**
     * pathProperty.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> pathProperty(String language) {
        return ToolSchemaSupport.property("string",
                text(language, "memory/ 下的目标文件路径（相对路径）", "Target path under memory/ (relative path)"));
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
    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
