/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's unit coverage for
 * {@code openjiuwen/dev_tools/agent_builder/utils/utils.py}.
 */
class AgentBuilderUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void extractJsonFromMarkdownBlockAndFallbackText() {
        String codeBlock = """
                ```json
                {"key": "value"}
                ```
                """;

        assertThat(AgentBuilderUtils.extractJsonFromText(codeBlock)).isEqualTo("{\"key\": \"value\"}");
        assertThat(AgentBuilderUtils.extractJsonFromText("{\"plain\": true}")).isEqualTo("{\"plain\": true}");
        assertThat(AgentBuilderUtils.extractJsonFromText("")).isEmpty();
        assertThat(AgentBuilderUtils.extractJsonFromText(null)).isNull();
    }

    @Test
    void formatDialogHistoryUsesDefaultsAndCustomSeparator() {
        List<Map<String, ?>> history = List.of(
                Map.of("role", "user", "content", "Hello"),
                Map.of("content", "Missing role")
        );

        assertThat(AgentBuilderUtils.formatDialogHistory(history))
                .isEqualTo("user: Hello\nunknown: Missing role");
        assertThat(AgentBuilderUtils.formatDialogHistory(history, " | "))
                .isEqualTo("user: Hello | unknown: Missing role");
    }

    @Test
    void safeJsonLoadsAndValidateSessionIdMirrorPythonHelpers() {
        assertThat(AgentBuilderUtils.safeJsonLoads("{\"key\": \"value\"}", Map.of()))
                .isEqualTo(Map.of("key", "value"));
        assertThat(AgentBuilderUtils.safeJsonLoads("invalid", List.of()))
                .isEqualTo(List.of());
        assertThat(AgentBuilderUtils.safeJsonLoads("", null)).isNull();

        assertThat(AgentBuilderUtils.validateSessionId("session_123-abc")).isTrue();
        assertThat(AgentBuilderUtils.validateSessionId("session@123")).isFalse();
        assertThat(AgentBuilderUtils.validateSessionId("")).isFalse();
    }

    @Test
    void mergeDictListsKeepsFirstOccurrenceByUniqueKey() {
        List<Map<String, Object>> existing = List.of(
                Map.of("resource_id", "1", "name", "A"),
                Map.of("resource_id", "2", "name", "B")
        );
        List<Map<String, Object>> updates = List.of(
                Map.of("resource_id", "2", "name", "B2"),
                Map.of("resource_id", "3", "name", "C"),
                Map.of("name", "missing-key")
        );

        List<Map<String, Object>> merged = AgentBuilderUtils.mergeDictLists(existing, updates, "resource_id");

        assertThat(merged).containsExactly(
                Map.of("resource_id", "1", "name", "A"),
                Map.of("resource_id", "2", "name", "B"),
                Map.of("resource_id", "3", "name", "C")
        );
    }

    @Test
    void deepMergeDictRecursivelyMergesNestedMapsWithoutMutatingBase() {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("a", 1);
        base.put("nested", new LinkedHashMap<>(Map.of("b", 2, "c", 3)));

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("nested", Map.of("b", 4, "d", 5));
        update.put("e", 6);

        Map<String, Object> merged = AgentBuilderUtils.deepMergeDict(base, update);

        assertThat(merged).isEqualTo(Map.of(
                "a", 1,
                "nested", Map.of("b", 4, "c", 3, "d", 5),
                "e", 6
        ));
        assertThat(base).isEqualTo(Map.of(
                "a", 1,
                "nested", Map.of("b", 2, "c", 3)
        ));
    }

    @Test
    void loadJsonFileReadsObjectAndReturnsEmptyMapForBlankFile() throws Exception {
        Path jsonFile = tempDir.resolve("config.json");
        Path blankFile = tempDir.resolve("blank.json");
        Files.writeString(jsonFile, "{\"key\":\"value\",\"nested\":{\"flag\":true}}");
        Files.writeString(blankFile, "   ");

        Map<String, Object> loaded = AgentBuilderUtils.loadJsonFile(jsonFile.toString());
        Map<String, Object> blank = AgentBuilderUtils.loadJsonFile(blankFile.toString());

        assertThat(loaded).containsEntry("key", "value");
        assertThat(loaded.get("nested")).isInstanceOf(Map.class);
        assertThat(blank).isEmpty();
    }

    @Test
    void loadJsonFileRaisesFileNotFoundAndValidationErrorForInvalidContent() throws Exception {
        Path invalidJson = tempDir.resolve("invalid.json");
        Path invalidTopLevel = tempDir.resolve("list.json");
        Files.writeString(invalidJson, "{invalid json");
        Files.writeString(invalidTopLevel, "[1,2,3]");

        assertThatThrownBy(() -> AgentBuilderUtils.loadJsonFile(tempDir.resolve("missing.json").toString()))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("File not found:");
        assertThat(AgentBuilderUtils.loadJsonFile(invalidJson.toString())).isEmpty();
        assertThatThrownBy(() -> AgentBuilderUtils.loadJsonFile(invalidTopLevel.toString()))
                .isInstanceOf(ValidationError.class)
                .satisfies(error -> {
                    ValidationError validationError = (ValidationError) error;
                    assertThat(validationError.getStatus()).isEqualTo(StatusCode.CONTEXT_MESSAGE_INVALID);
                    assertThat(validationError.getMessage()).contains("JSON parse error:");
                });
    }
}
