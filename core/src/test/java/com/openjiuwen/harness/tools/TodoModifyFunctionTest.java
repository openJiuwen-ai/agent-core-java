/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class TodoModifyFunctionTest {
    private static final String SESSION_ID = "todo-modify-contract";

    @TempDir
    Path tempDir;

    private TodoTool todoTool;
    private TodoModifyFunction function;

    @BeforeEach
    void prepareTool() throws IOException {
        todoTool = new TodoTool(tempDir.toString());
        todoTool.save(SESSION_ID, List.of(
                TodoItem.builder().id("task-1").content("Analyze workbook").status(TodoStatus.PENDING).build(),
                TodoItem.builder().id("task-2").content("Check result").status(TodoStatus.PENDING).build()));
        ToolCard card = ToolMetadataRegistry.buildToolCard("todo_modify", "todo_modify", "cn");
        function = new TodoModifyFunction(card, inputs -> todoTool.modify(SESSION_ID, inputs));
    }

    @Test
    void canonicalUpdateShouldPersistEverySupportedField() throws Exception {
        Map<String, Object> update = Map.of("id", "task-1", "status", "completed", "content", "Analyzed",
                "activeForm", "Analyzing", "description", "Read sheet1", "priority", "high",
                "selected_model_id", "model-1", "depends_on", List.of("task-2"), "result_summary", "done",
                "meta_data", Map.of("rows", 10));

        Object result = function.invoke(Map.of("action", "update", "todos", List.of(update)), Map.of());

        assertSuccess(result);
        TodoItem saved = todoTool.load(SESSION_ID).get(0);
        assertThat(saved.getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(saved.getContent()).isEqualTo("Analyzed");
        assertThat(saved.getActiveForm()).isEqualTo("Analyzing");
        assertThat(saved.getDescription()).isEqualTo("Read sheet1");
        assertThat(saved.getPriority()).isEqualTo("high");
        assertThat(saved.getSelectedModelId()).isEqualTo("model-1");
        assertThat(saved.getDependsOn()).containsExactly("task-2");
        assertThat(saved.getResultSummary()).isEqualTo("done");
        assertThat(saved.getMetaData()).containsEntry("rows", 10);
    }

    @Test
    void legacyUpdatesShouldNormalizeBeforeSchemaValidationWithoutChangingInput() throws Exception {
        Map<String, Object> inputs = Map.of("updates", List.of(
                Map.of("task_id", "task-1", "status", "COMPLETED", "result_summary", "legacy done")));

        assertSuccess(function.invoke(inputs, Map.of()));

        assertThat(inputs).containsKey("updates").doesNotContainKeys("action", "todos");
        TodoItem saved = todoTool.load(SESSION_ID).get(0);
        assertThat(saved.getStatus()).isEqualTo(TodoStatus.COMPLETED);
        assertThat(saved.getResultSummary()).isEqualTo("legacy done");
    }

    @Test
    void streamShouldNormalizeLegacyUpdatesAndPersistResult() throws Exception {
        Iterator<Object> chunks = function.stream(Map.of("action", "update", "updates",
                List.of(Map.of("task_id", "task-1", "status", "completed"))), Map.of());

        assertSuccess(chunks.next());
        assertThat(chunks.hasNext()).isFalse();
        assertThat(todoTool.load(SESSION_ID).get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
    }

    @Test
    void canonicalTodosShouldKeepPrecedenceOverLegacyUpdates() throws Exception {
        assertSuccess(function.invoke(Map.of("action", "update",
                "todos", List.of(Map.of("id", "task-1", "status", "completed")),
                "updates", List.of(Map.of("task_id", "task-1", "status", "cancelled"))), Map.of()));

        assertThat(todoTool.load(SESSION_ID).get(0).getStatus()).isEqualTo(TodoStatus.COMPLETED);
    }

    @Test
    void historicalMalformedCallsShouldFailWithoutChangingStoredTodos() throws IOException {
        List<Map<String, Object>> malformedCalls = List.of(
                Map.of("action", "update", "status", "completed", "todos", List.of(Map.of("id", "task-1"))),
                Map.of("action", "update", "status", "completed", "task_id", "task-1"),
                Map.of("todos", List.of(Map.of("id", "task-1", "status", "completed"))),
                Map.of("action", "update", "ids", List.of("task-1"), "status", "completed"));
        for (Map<String, Object> inputs : malformedCalls) {
            assertThatThrownBy(() -> function.invoke(inputs, Map.of())).isInstanceOf(Exception.class);
            assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                    .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
        }
    }

    @Test
    void topLevelStatusShouldStillBeRejectedBySchemaValidation() throws IOException {
        Map<String, Object> inputs = Map.of("action", "update", "status", "completed",
                "todos", List.of(Map.of("id", "task-1", "result_summary", "done")));

        assertThatThrownBy(() -> function.invoke(inputs, Map.of())).hasMessageContaining("status");
        assertThat(todoTool.load(SESSION_ID).get(0).getStatus()).isEqualTo(TodoStatus.PENDING);
    }

    @Test
    void emptyOrIneffectiveUpdatesShouldFailInBothInvocationModes() throws IOException {
        List<Map<String, Object>> invalidCalls = List.of(Map.of("action", "update"),
                Map.of("action", "update", "todos", List.of()),
                Map.of("action", "update", "todos", List.of(Map.of("id", "task-1"))),
                Map.of("action", "update", "todos", List.of(Map.of("task_id", "task-1", "status", "completed"))));
        for (Map<String, Object> inputs : invalidCalls) {
            assertThatThrownBy(() -> function.invoke(inputs, Map.of())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> function.stream(inputs, Map.of())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
    }

    @Test
    void otherActionsShouldKeepExistingBehavior() throws Exception {
        Map<String, Object> item = Map.of("id", "task-3", "content", "Export", "activeForm", "Exporting",
                "description", "Export workbook", "status", "pending");
        assertSuccess(function.invoke(Map.of("action", "append", "todos", List.of(item)), Map.of()));
        Map<String, Object> inserted = Map.of("id", "task-4", "content", "Review", "activeForm", "Reviewing",
                "description", "Review workbook", "status", "pending");
        assertSuccess(function.invoke(Map.of("action", "insert_before", "todo_data",
                Map.of("target_id", "task-3", "items", List.of(inserted))), Map.of()));
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getId)
                .containsExactly("task-1", "task-2", "task-4", "task-3");
        assertSuccess(function.invoke(Map.of("action", "cancel", "ids", List.of("task-4")), Map.of()));
        assertThat(todoTool.load(SESSION_ID).get(2).getStatus()).isEqualTo(TodoStatus.CANCELLED);
        assertSuccess(function.invoke(Map.of("action", "delete", "ids", List.of("task-3")), Map.of()));
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getId)
                .containsExactly("task-1", "task-2", "task-4");
    }

    @Test
    void modelSchemaShouldExposeOnlyCanonicalUpdateShapeInBothLanguages() {
        for (String language : List.of("cn", "en")) {
            ToolCard card = ToolMetadataRegistry.buildToolCard("todo_modify", "todo_modify", language);
            assertThat(card.getInputParams()).containsEntry("required", List.of("action"))
                    .containsEntry("additionalProperties", false);
            assertThat(card.getInputParams().get("properties")).isInstanceOfSatisfying(Map.class, properties ->
                    assertThat(properties).containsKeys("todos", "ids", "todo_data").doesNotContainKey("updates"));
            assertThat(card.getDescription()).contains("result_summary").contains("task-1");
        }
    }

    private static void assertSuccess(Object result) {
        assertThat(result).isInstanceOfSatisfying(ToolOutput.class,
                output -> assertThat(output.isSuccess()).isTrue());
    }
}
