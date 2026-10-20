/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.FileTodoStorage;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Todo tools backed by a {@code TodoStorage} must keep every todo field across save and load.
 *
 * @since 2026-10-20
 */
class TaskPlanningRailTodoStorageRoundTripTest {

    private static final Map<String, Object> SESSION = Map.of("session_id", "s1");

    @TempDir
    Path workspace;

    @Test
    void testStatusSurvivesCreateAndModify() throws Exception {
        TaskPlanningRail rail = initRail();
        tool(rail, "todo_create").invoke(Map.of("tasks", List.of(
                Map.of("id", "A", "content", "task A", "activeForm", "doing A", "description", "a"),
                Map.of("id", "B", "content", "task B", "activeForm", "doing B", "description", "b"))), SESSION);

        assertThat(listed(rail)).containsExactly(
                Map.of("id", "A", "status", "in_progress"),
                Map.of("id", "B", "status", "pending"));

        tool(rail, "todo_modify").invoke(Map.of("action", "update",
                "todos", List.of(Map.of("id", "A", "status", "completed"))), SESSION);

        assertThat(listed(rail)).containsExactly(Map.of("id", "B", "status", "pending"));
        List<TodoItem> stored = storage().load("s1");
        assertThat(stored).extracting(TodoItem::getStatus)
                .containsExactly(TodoStatus.COMPLETED, TodoStatus.PENDING);
        assertThat(stored).extracting(TodoItem::getActiveForm).containsExactly("doing A", "doing B");
    }

    @Test
    void testOptionalFieldsSurviveRoundTrip() throws Exception {
        TaskPlanningRail rail = initRail();
        tool(rail, "todo_create").invoke(Map.of("tasks", List.of(Map.of(
                "id", "A", "content", "task A", "activeForm", "doing A", "description", "a",
                "selected_model_id", "fast"))), SESSION);
        tool(rail, "todo_modify").invoke(Map.of("action", "update",
                "todos", List.of(Map.of("id", "A", "content", "task A2"))), SESSION);

        TodoItem stored = storage().load("s1").get(0);
        assertThat(stored.getContent()).isEqualTo("task A2");
        assertThat(stored.getActiveForm()).isEqualTo("doing A");
        assertThat(stored.getStatus()).isEqualTo(TodoStatus.IN_PROGRESS);
        assertThat(stored.getSelectedModelId()).isEqualTo("fast");
    }

    @Test
    void testLegacyStoredStatusesAreMapped() throws Exception {
        TaskPlanningRail rail = initRail();
        storage().save("s1", List.of(
                TodoItem.builder().id("A").content("done").status(TodoStatus.DONE).build(),
                TodoItem.builder().id("B").content("todo").status(TodoStatus.TODO).build(),
                TodoItem.builder().id("C").content("doing").status(TodoStatus.IN_PROGRESS).build()));

        assertThat(listed(rail)).containsExactly(
                Map.of("id", "B", "status", "pending"),
                Map.of("id", "C", "status", "in_progress"));
    }

    private TaskPlanningRail initRail() {
        DeepAgent agent = new DeepAgent(new AgentCard("deep", "deep", "test"));
        DeepAgentConfig config = new DeepAgentConfig();
        config.setWorkspace(new Workspace(workspace.toString(), "cn"));
        agent.configure(config);
        TaskPlanningRail rail = new TaskPlanningRail();
        rail.init(agent);
        return rail;
    }

    private FileTodoStorage storage() {
        return new FileTodoStorage(workspace.resolve(".todo"));
    }

    private static List<Map<String, Object>> listed(TaskPlanningRail rail) throws Exception {
        Object result = tool(rail, "todo_list").invoke(Map.of(), SESSION);
        if (!(result instanceof Map<?, ?> resultMap) || !(resultMap.get("tasks") instanceof List<?> tasks)) {
            throw new IllegalStateException("task list result expected");
        }
        return tasks.stream()
                .map(TaskPlanningRailTodoStorageRoundTripTest::idAndStatus)
                .toList();
    }

    private static Map<String, Object> idAndStatus(Object row) {
        if (!(row instanceof Map<?, ?> rowMap)) {
            throw new IllegalStateException("task row map expected");
        }
        return Map.of("id", rowMap.get("id"), "status", rowMap.get("status"));
    }

    private static Tool tool(TaskPlanningRail rail, String name) {
        return rail.getTools().stream()
                .filter(tool -> name.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow();
    }
}
