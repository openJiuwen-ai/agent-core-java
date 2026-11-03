/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CodingMemoryPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void codingMemoryEditMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new CodingMemoryPromptToolProviders.CodingMemoryEditMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("coding_memory_edit");
        assertThat(provider.getDescription("cn")).isEqualTo("在 coding_memory/ 下的记忆文件中做精确字符串替换（old_text → new_text）。");
        assertThat(provider.getDescription("en")).isEqualTo("Perform an exact string replacement inside a coding memory file (old_text → new_text).");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(java.util.List.of("path", "old_text", "new_text"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("path", "old_text", "new_text"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void codingMemoryReadMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new CodingMemoryPromptToolProviders.CodingMemoryReadMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("coding_memory_read");
        assertThat(provider.getDescription("cn")).isEqualTo("按 offset/limit 读取 coding_memory/ 下记忆文件的部分内容（用于分页阅读）。");
        assertThat(provider.getDescription("en")).isEqualTo("Read a portion of a memory file under coding_memory/ using offset/limit (for paging).");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(java.util.List.of("path", "offset", "limit"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("path"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void codingMemoryWriteMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new CodingMemoryPromptToolProviders.CodingMemoryWriteMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("coding_memory_write");
        assertThat(provider.getDescription("cn")).isEqualTo("写入记忆内容到 coding_memory/ 下的 Markdown 文件（要求 frontmatter）。");
        assertThat(provider.getDescription("en")).isEqualTo("Write memory content to a markdown file under coding_memory/ (frontmatter required).");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(java.util.List.of("path", "content"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("path", "content"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

}
