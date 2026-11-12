/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.ValidationError;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

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
                Map.of("action", "update", "status", "completed", "task_id", "task-1"),
                Map.of("action", "update", "ids", List.of("task-1"), "status", "completed"));
        for (Map<String, Object> inputs : malformedCalls) {
            assertThatThrownBy(() -> function.invoke(inputs, Map.of())).isInstanceOf(ValidationError.class)
                    .hasMessageContaining("Unexpected keyword argument");
            assertThatThrownBy(() -> function.stream(inputs, Map.of())).isInstanceOf(ValidationError.class)
                    .hasMessageContaining("Unexpected keyword argument");
            assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                    .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
        }
    }

    @Test
    void canonicalUpdateWithoutActionShouldFailSchemaValidation() throws IOException {
        Map<String, Object> inputs = Map.of("todos", List.of(Map.of("id", "task-1", "status", "completed")));

        assertThatThrownBy(() -> function.invoke(inputs, Map.of())).isInstanceOf(ValidationError.class)
                .hasMessageContaining("Missing required field: action");
        assertThatThrownBy(() -> function.stream(inputs, Map.of())).isInstanceOf(ValidationError.class)
                .hasMessageContaining("Missing required field: action");
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
    }

    @Test
    void topLevelUpdateFieldsShouldMoveIntoEveryTodoWithoutChangingInput() throws Exception {
        Map<String, Object> inputs = Map.of("action", "update", "status", "completed", "result_summary", "done",
                "todos", List.of(Map.of("id", "task-1"), Map.of("id", "task-2")));

        assertSuccess(function.invoke(inputs, Map.of()));

        assertThat(inputs).containsKeys("status", "result_summary");
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.COMPLETED, TodoStatus.COMPLETED);
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getResultSummary)
                .containsExactly("done", "done");
    }

    @Test
    void streamShouldMoveTopLevelStatusIntoTodo() throws Exception {
        Iterator<Object> chunks = function.stream(Map.of("action", "update", "status", "completed",
                "todos", List.of(Map.of("id", "task-1"))), Map.of());

        assertSuccess(chunks.next());
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.COMPLETED, TodoStatus.PENDING);
    }

    @Test
    void todoLevelFieldShouldTakePrecedenceOverTopLevelField() throws Exception {
        Map<String, Object> inputs = Map.of("action", "update", "status", "completed",
                "todos", List.of(Map.of("id", "task-1"), Map.of("id", "task-2", "status", "in_progress")));

        assertSuccess(function.invoke(inputs, Map.of()));

        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.COMPLETED, TodoStatus.IN_PROGRESS);
    }

    @Test
    void topLevelFieldsShouldOnlyMoveForUpdateWithTodoObjects() throws IOException {
        List<Map<String, Object>> rejectedCalls = List.of(
                Map.of("action", "cancel", "ids", List.of("task-1"), "status", "completed"),
                Map.of("action", "update", "status", "completed", "todos", List.of("task-1")),
                Map.of("action", "update", "owner", "someone",
                        "todos", List.of(Map.of("id", "task-1", "status", "completed"))));
        for (Map<String, Object> inputs : rejectedCalls) {
            assertThatThrownBy(() -> function.invoke(inputs, Map.of())).isInstanceOf(ValidationError.class)
                    .hasMessageContaining("Unexpected keyword argument");
        }
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
    }

    @Test
    void emptyUpdatesShouldKeepBusinessFailureInBothInvocationModes() throws Exception {
        List<Map<String, Object>> invalidCalls = List.of(Map.of("action", "update"),
                Map.of("action", "update", "todos", List.of()));
        for (Map<String, Object> inputs : invalidCalls) {
            assertFailure(function.invoke(inputs, Map.of()), "todo items must be a non-empty list");
            assertFailure(function.stream(inputs, Map.of()).next(), "todo items must be a non-empty list");
        }
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
    }

    @Test
    void idOnlyUpdateShouldKeepExistingNoOpBehavior() throws Exception {
        Map<String, Object> inputs = Map.of("action", "update", "todos", List.of(Map.of("id", "task-1")));

        assertSuccess(function.invoke(inputs, Map.of()));
        assertSuccess(function.stream(inputs, Map.of()).next());
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getContent)
                .containsExactly("Analyze workbook", "Check result");
    }

    @Test
    void missingOrUnknownIdShouldKeepBusinessFailure() throws Exception {
        List<Map<String, Object>> updates = List.of(Map.of("status", "completed"),
                Map.of("id", "", "status", "completed"), Map.of("id", "missing", "status", "completed"));
        for (Map<String, Object> update : updates) {
            Map<String, Object> inputs = Map.of("action", "update", "todos", List.of(update));
            assertFailure(function.invoke(inputs, Map.of()), "todo item not found");
            assertFailure(function.stream(inputs, Map.of()).next(), "todo item not found");
        }
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.PENDING, TodoStatus.PENDING);
    }

    @Test
    void taskIdAliasShouldKeepExistingBusinessBehavior() throws Exception {
        Map<String, Object> inputs = Map.of("action", "update", "todos",
                List.of(Map.of("task_id", "task-1", "status", "completed")));

        assertSuccess(function.invoke(inputs, Map.of()));
        assertSuccess(function.stream(inputs, Map.of()).next());
        assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.COMPLETED, TodoStatus.PENDING);
    }

    @Test
    void concurrentUpdatesOfDifferentTodosShouldNotOverwriteEachOther() throws Exception {
        Map<String, Object> first = Map.of("action", "update",
                "todos", List.of(Map.of("id", "task-1", "result_summary", "first")));
        Map<String, Object> second = Map.of("action", "update",
                "todos", List.of(Map.of("id", "task-2", "result_summary", "second")));
        assertConcurrentUpdatesKeepBoth(first, second);
    }

    @Test
    void blankSessionIdShouldShareLockWithOmittedSessionId() throws Exception {
        Map<String, Object> blankSession = Map.of("action", "update", "session_id", "",
                "todos", List.of(Map.of("id", "task-1", "result_summary", "first")));
        Map<String, Object> omittedSession = Map.of("action", "update",
                "todos", List.of(Map.of("id", "task-2", "result_summary", "second")));
        assertConcurrentUpdatesKeepBoth(blankSession, omittedSession);
    }

    private void assertConcurrentUpdatesKeepBoth(Map<String, Object> first, Map<String, Object> second)
            throws Exception {
        for (int round = 0; round < 50; round++) {
            todoTool.save(SESSION_ID, List.of(
                    TodoItem.builder().id("task-1").content("Analyze workbook").status(TodoStatus.PENDING).build(),
                    TodoItem.builder().id("task-2").content("Check result").status(TodoStatus.PENDING).build()));
            CountDownLatch start = new CountDownLatch(1);
            FutureTask<Object> firstCall = startCall("todo-modify-first-" + round, start, first);
            FutureTask<Object> secondCall = startCall("todo-modify-second-" + round, start, second);
            start.countDown();

            assertSuccess(firstCall.get(10, TimeUnit.SECONDS));
            assertSuccess(secondCall.get(10, TimeUnit.SECONDS));
            assertThat(todoTool.load(SESSION_ID)).extracting(TodoItem::getResultSummary)
                    .containsExactly("first", "second");
        }
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
                    .doesNotContainKey("additionalProperties");
            assertThat(card.getInputParams().get("properties")).isInstanceOfSatisfying(Map.class, properties ->
                    assertThat(properties).containsKeys("todos", "ids", "todo_data").doesNotContainKey("updates"));
            assertThat(card.getDescription()).contains("\"action\": \"update\"", "\"todo_data\"",
                    "\"selected_model_id\"", "in_progress");
        }
    }

    private FutureTask<Object> startCall(String threadName, CountDownLatch start, Map<String, Object> inputs) {
        FutureTask<Object> call = new FutureTask<>(() -> {
            start.await();
            return function.invoke(inputs, Map.of());
        });
        new Thread(call, threadName).start();
        return call;
    }

    private static void assertSuccess(Object result) {
        assertThat(result).isInstanceOfSatisfying(ToolOutput.class,
                output -> assertThat(output.isSuccess()).isTrue());
    }

    private static void assertFailure(Object result, String expectedError) {
        assertThat(result).isInstanceOfSatisfying(ToolOutput.class, output -> {
            assertThat(output.isSuccess()).isFalse();
            assertThat(output.getError()).contains(expectedError);
        });
    }
}
