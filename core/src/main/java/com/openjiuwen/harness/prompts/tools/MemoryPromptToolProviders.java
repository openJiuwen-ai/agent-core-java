/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.memory} in
 * {@code openjiuwen/harness/prompts/tools/memory.py}.
 */
public final class MemoryPromptToolProviders {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private MemoryPromptToolProviders() {
    }

    private static Map<String, Object> parseSchema(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse embedded prompt-tool schema JSON", ex);
        }
    }

    private static String resolve(String chinese, String english, String language) {
        return "en".equals(language) ? english : chinese;
    }

    private static final String MEMORY_SEARCH_DESCRIPTION_CN = "在长期记忆中检索过往信息（决策、偏好、人物、日期、TODO 等），返回相关片段与引用线索。";
    private static final String MEMORY_SEARCH_DESCRIPTION_EN = "Search long-term memory (prior decisions, preferences, people, dates, todos) and return relevant snippets and references.";
    private static final String MEMORY_SEARCH_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"检索关键词或问题\"},\"max_results\":{\"type\":\"integer\",\"description\":\"最多返回条数（可选）\"},\"min_score\":{\"type\":\"number\",\"description\":\"最小相关度阈值（可选）\"},\"session_key\":{\"type\":\"string\",\"description\":\"会话键（可选，用于上下文隔离/过滤）\"}},\"required\":[\"query\"]}";
    private static final String MEMORY_SEARCH_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"Search query string\"},\"max_results\":{\"type\":\"integer\",\"description\":\"Maximum number of results (optional)\"},\"min_score\":{\"type\":\"number\",\"description\":\"Minimum relevance score threshold (optional)\"},\"session_key\":{\"type\":\"string\",\"description\":\"Session key (optional, for scoping/filtering)\"}},\"required\":[\"query\"]}";

    /**
     * Mirrors Python's {@code MemorySearchMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/memory.py}.
     */
    public static final class MemorySearchMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "memory_search";
        }

        @Override
        public String getDescription(String language) {
            return resolve(MEMORY_SEARCH_DESCRIPTION_CN, MEMORY_SEARCH_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(MEMORY_SEARCH_SCHEMA_CN, MEMORY_SEARCH_SCHEMA_EN, language));
        }
    }

    private static final String MEMORY_GET_DESCRIPTION_CN = "按行号切片读取 memory/ 下的记忆 Markdown 文件内容（from_line + lines）。";
    private static final String MEMORY_GET_DESCRIPTION_EN = "Read a slice of a memory markdown file under memory/ (from_line + lines).";
    private static final String MEMORY_GET_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"memory/ 下的目标文件路径（相对路径）\"},\"from_line\":{\"type\":\"integer\",\"description\":\"起始行号（可选）\"},\"lines\":{\"type\":\"integer\",\"description\":\"读取行数（可选）\"}},\"required\":[\"path\"]}";
    private static final String MEMORY_GET_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under memory/ (relative path)\"},\"from_line\":{\"type\":\"integer\",\"description\":\"Starting line number (optional)\"},\"lines\":{\"type\":\"integer\",\"description\":\"Number of lines to read (optional)\"}},\"required\":[\"path\"]}";

    /**
     * Mirrors Python's {@code MemoryGetMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/memory.py}.
     */
    public static final class MemoryGetMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "memory_get";
        }

        @Override
        public String getDescription(String language) {
            return resolve(MEMORY_GET_DESCRIPTION_CN, MEMORY_GET_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(MEMORY_GET_SCHEMA_CN, MEMORY_GET_SCHEMA_EN, language));
        }
    }

    private static final String WRITE_MEMORY_DESCRIPTION_CN = "写入记忆内容到 memory/ 下的 Markdown 文件；支持覆盖写或追加写（append）。";
    private static final String WRITE_MEMORY_DESCRIPTION_EN = "Write memory content to a markdown file under memory/; supports overwrite or append.";
    private static final String WRITE_MEMORY_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"memory/ 下的目标文件路径（相对路径）\"},\"content\":{\"type\":\"string\",\"description\":\"要写入的内容\"},\"append\":{\"type\":\"boolean\",\"description\":\"是否追加写入（默认 false）\"}},\"required\":[\"path\",\"content\"]}";
    private static final String WRITE_MEMORY_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under memory/ (relative path)\"},\"content\":{\"type\":\"string\",\"description\":\"Content to write\"},\"append\":{\"type\":\"boolean\",\"description\":\"Append to file instead of overwrite (default false)\"}},\"required\":[\"path\",\"content\"]}";

    /**
     * Mirrors Python's {@code WriteMemoryMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/memory.py}.
     */
    public static final class WriteMemoryMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "write_memory";
        }

        @Override
        public String getDescription(String language) {
            return resolve(WRITE_MEMORY_DESCRIPTION_CN, WRITE_MEMORY_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(WRITE_MEMORY_SCHEMA_CN, WRITE_MEMORY_SCHEMA_EN, language));
        }
    }

    private static final String EDIT_MEMORY_DESCRIPTION_CN = "在 memory/ 下的记忆文件中做精确字符串替换（old_text → new_text）。";
    private static final String EDIT_MEMORY_DESCRIPTION_EN = "Perform an exact string replacement inside a memory file (old_text → new_text).";
    private static final String EDIT_MEMORY_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"memory/ 下的目标文件路径（相对路径）\"},\"old_text\":{\"type\":\"string\",\"description\":\"要替换的原始文本\"},\"new_text\":{\"type\":\"string\",\"description\":\"替换后的新文本\"}},\"required\":[\"path\",\"old_text\",\"new_text\"]}";
    private static final String EDIT_MEMORY_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under memory/ (relative path)\"},\"old_text\":{\"type\":\"string\",\"description\":\"Original text to replace\"},\"new_text\":{\"type\":\"string\",\"description\":\"New replacement text\"}},\"required\":[\"path\",\"old_text\",\"new_text\"]}";

    /**
     * Mirrors Python's {@code EditMemoryMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/memory.py}.
     */
    public static final class EditMemoryMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "edit_memory";
        }

        @Override
        public String getDescription(String language) {
            return resolve(EDIT_MEMORY_DESCRIPTION_CN, EDIT_MEMORY_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(EDIT_MEMORY_SCHEMA_CN, EDIT_MEMORY_SCHEMA_EN, language));
        }
    }

    private static final String READ_MEMORY_DESCRIPTION_CN = "按 offset/limit 读取 memory/ 下记忆文件的部分内容（用于分页阅读）。";
    private static final String READ_MEMORY_DESCRIPTION_EN = "Read a portion of a memory file under memory/ using offset/limit (for paging).";
    private static final String READ_MEMORY_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"memory/ 下的目标文件路径（相对路径）\"},\"offset\":{\"type\":\"integer\",\"description\":\"从第几行开始读取（可选）\"},\"limit\":{\"type\":\"integer\",\"description\":\"最多读取多少行（可选）\"}},\"required\":[\"path\"]}";
    private static final String READ_MEMORY_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under memory/ (relative path)\"},\"offset\":{\"type\":\"integer\",\"description\":\"Line offset to start reading from (optional)\"},\"limit\":{\"type\":\"integer\",\"description\":\"Maximum number of lines to read (optional)\"}},\"required\":[\"path\"]}";

    /**
     * Mirrors Python's {@code ReadMemoryMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/memory.py}.
     */
    public static final class ReadMemoryMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "read_memory";
        }

        @Override
        public String getDescription(String language) {
            return resolve(READ_MEMORY_DESCRIPTION_CN, READ_MEMORY_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(READ_MEMORY_SCHEMA_CN, READ_MEMORY_SCHEMA_EN, language));
        }
    }
}
