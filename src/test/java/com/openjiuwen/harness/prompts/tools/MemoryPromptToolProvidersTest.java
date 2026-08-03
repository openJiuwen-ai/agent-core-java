/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MemoryPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void memorySearchMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new MemoryPromptToolProviders.MemorySearchMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("memory_search");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("query", "max_results", "min_score", "session_key"));
        assertThat(castList(schema.get("required"))).containsExactly("query");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void memoryGetMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new MemoryPromptToolProviders.MemoryGetMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("memory_get");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("path", "from_line", "lines"));
        assertThat(castList(schema.get("required"))).containsExactly("path");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void writeMemoryMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new MemoryPromptToolProviders.WriteMemoryMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("write_memory");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("path", "content", "append"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("path", "content"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void editMemoryMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new MemoryPromptToolProviders.EditMemoryMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("edit_memory");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("path", "old_text", "new_text"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(
                java.util.List.of("path", "old_text", "new_text"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void readMemoryMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new MemoryPromptToolProviders.ReadMemoryMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("read_memory");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("path", "offset", "limit"));
        assertThat(castList(schema.get("required"))).containsExactly("path");
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
