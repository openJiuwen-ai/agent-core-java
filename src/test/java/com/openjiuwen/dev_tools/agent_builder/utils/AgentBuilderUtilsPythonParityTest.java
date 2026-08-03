/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/dev_tools/agent_builder/utils/test_utils.py}.
 */
class AgentBuilderUtilsPythonParityTest {

    @Test
    void extractFromJsonCodeBlock() {
        String text = "```json\n{\"key\": \"value\"}\n```";

        assertThat(AgentBuilderUtils.extractJsonFromText(text)).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractFromPlainCodeBlock() {
        String text = "```\n{\"key\": \"value\"}\n```";

        assertThat(AgentBuilderUtils.extractJsonFromText(text)).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractFromTextWithoutCodeBlock() {
        assertThat(AgentBuilderUtils.extractJsonFromText("{\"key\": \"value\"}")).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    void extractFromEmptyText() {
        assertThat(AgentBuilderUtils.extractJsonFromText("")).isEmpty();
    }

    @Test
    void extractFromNullText() {
        assertThat(AgentBuilderUtils.extractJsonFromText(null)).isNull();
    }

    @Test
    void extractJsonArray() {
        String text = "```json\n[1, 2, 3]\n```";

        assertThat(AgentBuilderUtils.extractJsonFromText(text)).isEqualTo("[1, 2, 3]");
    }

    @Test
    void extractMultilineJson() {
        String text = "```json\n{\"key1\": \"value1\",\n\"key2\": \"value2\"}\n```";

        String result = AgentBuilderUtils.extractJsonFromText(text);

        assertThat(result).contains("key1", "key2");
    }

    @Test
    void formatSingleMessage() {
        List<Map<String, ?>> history = List.of(Map.of("role", "user", "content", "Hello"));

        assertThat(AgentBuilderUtils.formatDialogHistory(history)).isEqualTo("user: Hello");
    }

    @Test
    void formatMultipleMessages() {
        List<Map<String, ?>> history = List.of(
                Map.of("role", "user", "content", "Hello"),
                Map.of("role", "assistant", "content", "Hi there!")
        );

        assertThat(AgentBuilderUtils.formatDialogHistory(history)).isEqualTo("user: Hello\nassistant: Hi there!");
    }

    @Test
    void formatEmptyHistory() {
        assertThat(AgentBuilderUtils.formatDialogHistory(List.of())).isEmpty();
    }

    @Test
    void formatWithCustomSeparator() {
        List<Map<String, ?>> history = List.of(
                Map.of("role", "user", "content", "Hello"),
                Map.of("role", "assistant", "content", "Hi!")
        );

        assertThat(AgentBuilderUtils.formatDialogHistory(history, " | "))
                .isEqualTo("user: Hello | assistant: Hi!");
    }

    @Test
    void formatWithMissingKeys() {
        Map<String, Object> onlyRole = new LinkedHashMap<>();
        onlyRole.put("role", "user");
        Map<String, Object> onlyContent = new LinkedHashMap<>();
        onlyContent.put("content", "Missing role");

        String result = AgentBuilderUtils.formatDialogHistory(List.of(onlyRole, onlyContent));

        assertThat(result).contains("user: ", "unknown: Missing role");
    }

    @Test
    void safeJsonLoadsValidJson() {
        assertThat(AgentBuilderUtils.safeJsonLoads("{\"key\": \"value\"}")).isEqualTo(Map.of("key", "value"));
    }

    @Test
    void safeJsonLoadsInvalidJsonReturnsDefault() {
        assertThat(AgentBuilderUtils.safeJsonLoads("invalid json", Map.of())).isEqualTo(Map.of());
    }

    @Test
    void safeJsonLoadsEmptyStringReturnsDefault() {
        assertThat(AgentBuilderUtils.safeJsonLoads("", null)).isNull();
    }

    @Test
    void safeJsonLoadsNullReturnsDefault() {
        assertThat(AgentBuilderUtils.safeJsonLoads(null, List.of())).isEqualTo(List.of());
    }

    @Test
    void safeJsonLoadsJsonArray() {
        assertThat(AgentBuilderUtils.safeJsonLoads("[1, 2, 3]")).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void validateSessionIdWithAlphanumeric() {
        assertThat(AgentBuilderUtils.validateSessionId("session123")).isTrue();
    }

    @Test
    void validateSessionIdWithUnderscore() {
        assertThat(AgentBuilderUtils.validateSessionId("session_123")).isTrue();
    }

    @Test
    void validateSessionIdWithHyphen() {
        assertThat(AgentBuilderUtils.validateSessionId("session-123")).isTrue();
    }

    @Test
    void validateSessionIdCombined() {
        assertThat(AgentBuilderUtils.validateSessionId("session_123-abc")).isTrue();
    }

    @Test
    void validateSessionIdRejectsSpecialChars() {
        assertThat(AgentBuilderUtils.validateSessionId("session@123")).isFalse();
    }

    @Test
    void validateSessionIdRejectsSpace() {
        assertThat(AgentBuilderUtils.validateSessionId("session 123")).isFalse();
    }

    @Test
    void validateSessionIdRejectsEmpty() {
        assertThat(AgentBuilderUtils.validateSessionId("")).isFalse();
    }

    @Test
    void validateSessionIdRejectsNull() {
        assertThat(AgentBuilderUtils.validateSessionId(null)).isFalse();
    }

    @Test
    void mergeWithUniqueKeys() {
        List<Map<String, Object>> existing = List.of(mapOf("id", "1", "name", "A"));
        List<Map<String, Object>> newItems = List.of(mapOf("id", "2", "name", "B"));

        List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(existing, newItems, "id");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("id", "1");
        assertThat(result.get(1)).containsEntry("id", "2");
    }

    @Test
    void mergeWithDuplicateKeys() {
        List<Map<String, Object>> existing = List.of(mapOf("id", "1", "name", "A"));
        List<Map<String, Object>> newItems = List.of(mapOf("id", "1", "name", "B"));

        List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(existing, newItems, "id");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "A");
    }

    @Test
    void mergeEmptyNewItems() {
        List<Map<String, Object>> existing = List.of(mapOf("id", "1", "name", "A"));

        assertThat(AgentBuilderUtils.mergeDictLists(existing, List.of(), "id")).hasSize(1);
    }

    @Test
    void mergeEmptyExisting() {
        List<Map<String, Object>> newItems = List.of(mapOf("id", "1", "name", "A"));

        assertThat(AgentBuilderUtils.mergeDictLists(List.of(), newItems, "id")).hasSize(1);
    }

    @Test
    void mergeBothEmpty() {
        assertThat(AgentBuilderUtils.mergeDictLists(List.of(), List.of(), "id")).isEmpty();
    }

    @Test
    void mergeWithMissingUniqueKey() {
        List<Map<String, Object>> existing = List.of(mapOf("id", "1", "name", "A"));
        List<Map<String, Object>> newItems = List.of(mapOf("name", "B"));

        assertThat(AgentBuilderUtils.mergeDictLists(existing, newItems, "id")).hasSize(1);
    }

    @Test
    void deepMergeSimpleDicts() {
        assertThat(AgentBuilderUtils.deepMergeDict(mapOf("a", 1), mapOf("b", 2)))
                .isEqualTo(mapOf("a", 1, "b", 2));
    }

    @Test
    void deepMergeNestedDicts() {
        Map<String, Object> base = mapOf("a", mapOf("b", 1, "c", 2));
        Map<String, Object> update = mapOf("a", mapOf("b", 3, "d", 4));

        assertThat(AgentBuilderUtils.deepMergeDict(base, update))
                .isEqualTo(mapOf("a", mapOf("b", 3, "c", 2, "d", 4)));
    }

    @Test
    void deepMergeOverwritesNonDictValues() {
        assertThat(AgentBuilderUtils.deepMergeDict(mapOf("a", 1), mapOf("a", 2)))
                .isEqualTo(mapOf("a", 2));
    }

    @Test
    void deepMergeDoesNotModifyOriginal() {
        Map<String, Object> base = mapOf("a", 1);
        Map<String, Object> update = mapOf("b", 2);

        Map<String, Object> result = AgentBuilderUtils.deepMergeDict(base, update);

        assertThat(base).doesNotContainKey("b");
        assertThat(result).containsKey("b");
    }

    @Test
    void deepMergeEmptyDicts() {
        assertThat(AgentBuilderUtils.deepMergeDict(Map.of(), Map.of())).isEmpty();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
