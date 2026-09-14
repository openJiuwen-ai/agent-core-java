/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.coding_memory} in
 * {@code openjiuwen/harness/prompts/tools/coding_memory.py}.
 */
public final class CodingMemoryPromptToolProviders {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private CodingMemoryPromptToolProviders() {
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

    private static final String CODINGMEMORYEDITMETADATAPROVIDER_DESCRIPTION_CN = "在 coding_memory/ 下的记忆文件中做精确字符串替换（old_text → new_text）。";
    private static final String CODINGMEMORYEDITMETADATAPROVIDER_DESCRIPTION_EN = "Perform an exact string replacement inside a coding memory file (old_text → new_text).";
    private static final String CODINGMEMORYEDITMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"coding_memory/ 下的目标文件路径（相对路径）\"},\"old_text\":{\"type\":\"string\",\"description\":\"要替换的原始文本\"},\"new_text\":{\"type\":\"string\",\"description\":\"替换后的新文本\"}},\"required\":[\"path\",\"old_text\",\"new_text\"]}";
    private static final String CODINGMEMORYEDITMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under coding_memory/ (relative path)\"},\"old_text\":{\"type\":\"string\",\"description\":\"Original text to replace\"},\"new_text\":{\"type\":\"string\",\"description\":\"New replacement text\"}},\"required\":[\"path\",\"old_text\",\"new_text\"]}";

    /**
     * Mirrors Python's {@code CodingMemoryEditMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/coding_memory.py}.
     */
    public static final class CodingMemoryEditMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "coding_memory_edit";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CODINGMEMORYEDITMETADATAPROVIDER_DESCRIPTION_CN, CODINGMEMORYEDITMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CODINGMEMORYEDITMETADATAPROVIDER_SCHEMA_CN, CODINGMEMORYEDITMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CODINGMEMORYREADMETADATAPROVIDER_DESCRIPTION_CN = "按 offset/limit 读取 coding_memory/ 下记忆文件的部分内容（用于分页阅读）。";
    private static final String CODINGMEMORYREADMETADATAPROVIDER_DESCRIPTION_EN = "Read a portion of a memory file under coding_memory/ using offset/limit (for paging).";
    private static final String CODINGMEMORYREADMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"coding_memory/ 下的目标文件路径（相对路径）\"},\"offset\":{\"type\":\"integer\",\"description\":\"从第几行开始读取（可选）\"},\"limit\":{\"type\":\"integer\",\"description\":\"最多读取多少行（可选）\"}},\"required\":[\"path\"]}";
    private static final String CODINGMEMORYREADMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under coding_memory/ (relative path)\"},\"offset\":{\"type\":\"integer\",\"description\":\"Line offset to start reading from (optional)\"},\"limit\":{\"type\":\"integer\",\"description\":\"Maximum number of lines to read (optional)\"}},\"required\":[\"path\"]}";

    /**
     * Mirrors Python's {@code CodingMemoryReadMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/coding_memory.py}.
     */
    public static final class CodingMemoryReadMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "coding_memory_read";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CODINGMEMORYREADMETADATAPROVIDER_DESCRIPTION_CN, CODINGMEMORYREADMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CODINGMEMORYREADMETADATAPROVIDER_SCHEMA_CN, CODINGMEMORYREADMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

    private static final String CODINGMEMORYWRITEMETADATAPROVIDER_DESCRIPTION_CN = "写入记忆内容到 coding_memory/ 下的 Markdown 文件（要求 frontmatter）。";
    private static final String CODINGMEMORYWRITEMETADATAPROVIDER_DESCRIPTION_EN = "Write memory content to a markdown file under coding_memory/ (frontmatter required).";
    private static final String CODINGMEMORYWRITEMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"coding_memory/ 下的目标文件路径（相对路径）\"},\"content\":{\"type\":\"string\",\"description\":\"要写入的内容（含 frontmatter）\"}},\"required\":[\"path\",\"content\"]}";
    private static final String CODINGMEMORYWRITEMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Target path under coding_memory/ (relative path)\"},\"content\":{\"type\":\"string\",\"description\":\"Content to write (with frontmatter)\"}},\"required\":[\"path\",\"content\"]}";

    /**
     * Mirrors Python's {@code CodingMemoryWriteMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/coding_memory.py}.
     */
    public static final class CodingMemoryWriteMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "coding_memory_write";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CODINGMEMORYWRITEMETADATAPROVIDER_DESCRIPTION_CN, CODINGMEMORYWRITEMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CODINGMEMORYWRITEMETADATAPROVIDER_SCHEMA_CN, CODINGMEMORYWRITEMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

}
