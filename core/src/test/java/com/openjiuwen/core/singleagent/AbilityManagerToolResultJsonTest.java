/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import com.openjiuwen.harness.tools.TodoStorage;
import com.openjiuwen.harness.tools.TodoTool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

class AbilityManagerToolResultJsonTest {

    private static final Set<String> TODO_FIELDS = Set.of(
            "id", "content", "activeForm", "description", "status",
            "depends_on", "result_summary", "meta_data", "selected_model_id", "priority");

    @TempDir
    Path tempDir;

    @Test
    void renderToolResult_plainTextStaysTextAndNullBecomesEmpty() {
        assertThat(AbilityManager.renderToolResult(null)).isEmpty();
        assertThat(AbilityManager.renderToolResult("找到 3 条结果")).isEqualTo("找到 3 条结果");
        assertThat(AbilityManager.renderToolResult(3)).isEqualTo("3");
    }

    @Test
    void renderToolResult_todoCreateIsJsonWithTodoFields() {
        TodoTool tool = new TodoTool(tempDir.toString());
        ToolOutput output = tool.create("s1", List.of(Map.of(
                "content", "扫描列重复率",
                "activeForm", "正在扫描",
                "description", "统计重复列")));

        String content = rendered(output);

        Map<String, Object> parsed = JsonUtils.safeJsonLoads(content, Map.class);
        assertThat(parsed).containsEntry("isSuccess", true);
        assertThat(parsed.get("error")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");
        assertThat(data).hasSize(1);
        assertTodoFields(data.get(0), "扫描列重复率", "正在扫描", "统计重复列", TodoStatus.IN_PROGRESS);
    }

    @Test
    void renderToolResult_todoListGetAndModifyExposeTodoFields() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        tool.create("s1", List.of(Map.of(
                "id", "task-1",
                "content", "扫描列重复率",
                "activeForm", "正在扫描",
                "description", "统计重复列")));

        Map<String, Object> listed = json(rendered(tool.list("s1")));
        assertThat(listed).containsEntry("isSuccess", true);
        assertThat(items(listed)).singleElement().satisfies(item ->
                assertTodoFields(item, "扫描列重复率", "正在扫描", "统计重复列", TodoStatus.IN_PROGRESS));

        Map<String, Object> fetched = json(rendered(tool.get("s1", "task-1")));
        assertThat(fetched).containsEntry("isSuccess", true);
        assertTodoFields(item(fetched), "扫描列重复率", "正在扫描", "统计重复列", TodoStatus.IN_PROGRESS);

        Map<String, Object> modified = json(rendered(tool.modify("s1", Map.of(
                "action", "update",
                "todos", List.of(Map.of("id", "task-1", "status", "completed"))))));
        assertThat(modified).containsEntry("isSuccess", true);
        assertTodoFields(items(modified).get(0), "扫描列重复率", "正在扫描", "统计重复列", TodoStatus.COMPLETED);
    }

    @Test
    void renderToolResult_todoFailuresStayJson() throws IOException {
        TodoTool tool = new TodoTool(tempDir.toString());
        tool.save("s1", List.of(TodoItem.builder().id("task-1").content("检查")
                .activeForm("检查中").description("核对").status(TodoStatus.PENDING).build()));
        TodoTool failing = new TodoTool(new TodoStorage() {
            @Override
            public List<TodoItem> load(String sessionId) throws IOException {
                throw new IOException("storage unavailable");
            }

            @Override
            public void save(String sessionId, List<TodoItem> todos) throws IOException {
                throw new IOException("storage unavailable");
            }

            @Override
            public void delete(String sessionId) throws IOException {
                throw new IOException("storage unavailable");
            }
        });

        assertFailure(rendered(tool.create("s1", List.of())), "Task list cannot be empty");
        assertFailure(rendered(failing.list("s1")), "storage unavailable");
        assertFailure(rendered(tool.get("s1", "missing")), "not found");
        assertFailure(rendered(tool.modify("s1", Map.of("action", "update"))), "non-empty list");
    }

    @Test
    void renderToolResult_failedTodoModifyKeepsErrorInJson() throws Exception {
        TodoTool tool = new TodoTool(tempDir.toString());
        tool.save("s1", List.of(TodoItem.builder().id("task-1").content("检查").status(TodoStatus.PENDING).build()));

        String content = AbilityManager.renderToolResult(tool.modify("s1", Map.of("action", "update")));

        Map<String, Object> parsed = JsonUtils.safeJsonLoads(content, Map.class);
        assertThat(parsed).containsEntry("isSuccess", false);
        assertThat(String.valueOf(parsed.get("error"))).contains("non-empty list");
    }

    @Test
    void toolOutputChunk_usesSameJsonAsToolMessage() throws Exception {
        ToolOutput output = ToolOutput.builder().success(true).data(List.of(Map.of("id", "task-1"))).build();
        String rendered = AbilityManager.renderToolResult(output);
        Method method = AbilityManager.class.getDeclaredMethod(
                "buildToolOutputChunk", String.class, ToolCall.class, Object.class, int.class);
        method.setAccessible(true);
        ToolCall call = ToolCall.builder().id("call-1").name("todo_create").arguments("{}").build();
        OutputSchema chunk = (OutputSchema) method.invoke(null, "task-1", call, output, 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        assertThat(payload.get("content")).isEqualTo(rendered);
        assertThat(JsonUtils.safeJsonLoads(rendered, Map.class))
                .containsEntry("isSuccess", true);
    }

    private static String rendered(ToolOutput output) {
        String content = AbilityManager.renderToolResult(output);
        assertThat(String.valueOf(output)).isEqualTo(content);
        assertThat(content).doesNotContain("ToolOutput(");
        assertThat(JsonUtils.safeJsonLoads(content, Map.class)).isNotNull();
        return content;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(String content) {
        return JsonUtils.safeJsonLoads(content, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> parsed) {
        return (List<Map<String, Object>>) parsed.get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> parsed) {
        return (Map<String, Object>) parsed.get("data");
    }

    private static void assertTodoFields(Map<String, Object> item, String content, String activeForm,
                                         String description, TodoStatus status) {
        assertThat(item.keySet()).containsAll(TODO_FIELDS);
        assertThat(item)
                .containsEntry("content", content)
                .containsEntry("activeForm", activeForm)
                .containsEntry("description", description)
                .containsEntry("status", status.name());
        assertThat(item.get("id")).isNotNull();
    }

    private static void assertFailure(String content, String errorFragment) {
        Map<String, Object> parsed = json(content);
        assertThat(parsed).containsEntry("isSuccess", false);
        assertThat(String.valueOf(parsed.get("error"))).contains(errorFragment);
    }
}
