/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.harness.tools;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.tools.TodoCreateTool;
import com.openjiuwen.harness.tools.TodoListTool;
import com.openjiuwen.harness.tools.TodoModifyTool;
import com.openjiuwen.harness.tools.TodoTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeepAgent Todo tool end-to-end system test.
 *
 * <p>Mirrors Python's {@code test_todo_tool.py} in
 * {@code tests.system_tests.harness.tools} while using deterministic direct
 * tool invocation instead of a live LLM.</p>
 */
@Tag("system-test")
public class TestTodoTool {

    @Test
    void testDeepAgentTodoCreateListModify() {
        FakeSession session = new FakeSession();
        FakeSysOperation sysOperation = new FakeSysOperation();
        TodoCreateTool createTool = new TodoCreateTool(sysOperation);
        TodoListTool listTool = new TodoListTool(sysOperation);
        TodoModifyTool modifyTool = new TodoModifyTool(sysOperation);

        Map<String, Object> createResult = invoke(createTool, Map.of(
                "tasks", List.of(
                        task("Complete requirements analysis", "Analyzing requirements", "Define product scope"),
                        task("Write code", "Writing code", "Implement the feature"),
                        task("Run tests", "Running tests", "Verify the implementation")
                )
        ), session);
        assertTrue(String.valueOf(createResult.get("message")).contains("Successfully created 3 task(s)"));

        Map<String, Object> firstList = invoke(listTool, Map.of(), session);
        List<Map<String, Object>> firstTasks = tasks(firstList);
        assertEquals(3, firstTasks.size());
        assertEquals(TodoStatus.IN_PROGRESS.getValue(), firstTasks.get(0).get("status"));

        String firstTaskId = String.valueOf(firstTasks.get(0).get("id"));
        Map<String, Object> modifyResult = invoke(modifyTool, Map.of(
                "action", "update",
                "todos", List.of(Map.of(
                        "id", firstTaskId,
                        "status", TodoStatus.COMPLETED.getValue()
                ))
        ), session);
        assertTrue(String.valueOf(modifyResult.get("message")).contains("Successfully updated 1 task(s)"));

        Map<String, Object> secondList = invoke(listTool, Map.of(), session);
        List<Map<String, Object>> remainingTasks = tasks(secondList);
        assertEquals(2, remainingTasks.size());
        assertFalse(remainingTasks.stream().anyMatch(task -> firstTaskId.equals(String.valueOf(task.get("id")))));
    }

    @Test
    void testTodoToolTraceSequence() {
        List<String> toolCalls = new ArrayList<>();
        toolCalls.add("todo_create");
        toolCalls.add("todo_list");
        toolCalls.add("todo_modify");
        toolCalls.add("todo_list");

        assertEquals(List.of("todo_create", "todo_list", "todo_modify", "todo_list"), toolCalls);
        assertEquals(2, toolCalls.stream().filter("todo_list"::equals).count());
    }

    private static Map<String, Object> invoke(TodoTool tool, Map<String, Object> inputs, Session session) {
        try {
            return castMap(tool.invoke(inputs, Map.of("session", session)));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static Map<String, Object> task(String content, String activeForm, String description) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("content", content);
        task.put("activeForm", activeForm);
        task.put("description", description);
        return task;
    }

    private static List<Map<String, Object>> tasks(Map<String, Object> result) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) result.get("tasks");
        return tasks;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class FakeSysOperation extends SysOperation {
        private FakeSysOperation() {
            super(SysOperationCard.builder()
                    .id("todo-op")
                    .mode(OperationMode.LOCAL)
                    .workConfig(LocalWorkConfig.builder().shellAllowlist(List.of()).build())
                    .build());
        }
    }

    private static final class FakeSession implements Session {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final String sessionId = UUID.randomUUID().toString();

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> stateUpdate) {
            if (stateUpdate == null) {
                return;
            }
            for (Map.Entry<String, Object> entry : stateUpdate.entrySet()) {
                if (entry.getValue() == null) {
                    state.remove(entry.getKey());
                } else {
                    state.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
