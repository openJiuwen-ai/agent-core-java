/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.single_agent.rail.HeartbeatReason;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;
import com.openjiuwen.core.single_agent.rail.RunContext;
import com.openjiuwen.core.single_agent.rail.RunKind;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.HeartbeatRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.schema.task.TodoItem;
import com.openjiuwen.harness.schema.task.TodoStatus;
import com.openjiuwen.harness.tools.FilesystemTools;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Missing system-test coverage for DeepAgent E2E behavior.
 *
 * <p>Mirrors Python's {@code TestDeepAgentE2E} in
 * {@code tests/system_tests/harness/test_deep_agent_e2e.py}.</p>
 */
class DeepAgentE2eMissingTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: skip system test";

    @TempDir
    Path tempDir;

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void deepAgentInvokeE2eRequireApiKeyBase() {
    }

    @Test
    void deepAgentComplexTaskMultiToolChain() throws Exception {
        List<String> toolCalls = new ArrayList<>();
        Tool writeFile = new FilesystemTools.WriteFileTool(tempDir.toString());
        Tool listFiles = new FilesystemTools.ListDirTool(tempDir.toString());
        Tool readFile = new FilesystemTools.ReadFileTool(tempDir.toString());

        ToolOutput writeAlpha = invoke(toolCalls, writeFile, Map.of(
                "path", "todo_alpha.txt",
                "content", "准备数据\n实现功能\n验证结果"
        ));
        ToolOutput writeBeta = invoke(toolCalls, writeFile, Map.of(
                "path", "todo_beta.txt",
                "content", "发布版本\n回滚预案"
        ));
        ToolOutput listed = invoke(toolCalls, listFiles, Map.of("path", "."));
        ToolOutput alpha = invoke(toolCalls, readFile, Map.of("path", "todo_alpha.txt"));
        ToolOutput beta = invoke(toolCalls, readFile, Map.of("path", "todo_beta.txt"));

        assertThat(writeAlpha.isSuccess()).isTrue();
        assertThat(writeBeta.isSuccess()).isTrue();
        assertThat(listed.isSuccess()).isTrue();
        assertThat(alpha.isSuccess()).isTrue();
        assertThat(beta.isSuccess()).isTrue();
        assertThat(toolCalls).containsExactly("write_file", "write_file", "list_files", "read_file", "read_file");
        assertThat(Files.readString(tempDir.resolve("todo_alpha.txt"))).contains("准备数据", "验证结果");
        assertThat(Files.readString(tempDir.resolve("todo_beta.txt"))).contains("发布版本", "回滚预案");
        assertThat(mapData(alpha).get("content")).asString().contains("实现功能");
        assertThat(mapData(beta).get("content")).asString().contains("回滚预案");
        assertThat(mapData(listed).get("entries")).asList().hasSize(2);
    }

    @Test
    void deepAgentTaskPlanning() {
        DeepAgent agent = configuredAgentWithPlanMode();

        Map<String, Object> result = agent.invoke(Map.of("query", "构建一个打卡系统")).join();

        assertThat(result).containsEntry("type", "deep_agent_result");
        assertThat(agent.findRailsByType(TaskPlanningRail.class)).hasSize(1);
        assertThat(toolIds(agent)).contains("todo_create", "todo_list", "todo_modify");
    }

    @Test
    void deepAgentTaskPlanningWithProgressReminder() {
        TaskPlanningRail taskPlanning = new TaskPlanningRail(true, 3, Map.of());
        CallbackContext ctx = context(
                "session_id", "deep-agent-e2e-planning",
                "tool_name", "todo_modify",
                "messages", new ArrayList<>(),
                "language", "cn",
                "todos", todos(
                        todo("task-1", "任务1", TodoStatus.COMPLETED),
                        todo("task-2", "任务2", TodoStatus.IN_PROGRESS)
                )
        );

        taskPlanning.afterToolCall(ctx);
        taskPlanning.afterToolCall(ctx);
        assertThat(ctx.get("should_repeat_progress")).isNull();
        taskPlanning.afterToolCall(ctx);

        assertThat(taskPlanning.getToolCallCount("deep-agent-e2e-planning")).isEqualTo(3);
        assertThat(ctx.get("should_repeat_progress")).isEqualTo(Boolean.TRUE);
        assertThat(ctx.get("messages")).asList().singleElement().asString().contains("确保计划正在正确执行", "任务2");
    }

    @Test
    void deepAgentHeartbeat() {
        DeepAgent agent = new DeepAgent();
        DeepAgentConfig config = new DeepAgentConfig();
        HeartbeatRail heartbeatRail = new HeartbeatRail();
        config.setRails(List.of(heartbeatRail));
        agent.configure(config);

        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery("heartbeat check");
        inputs.setRunKind(RunKind.HEARTBEAT);
        RunContext runContext = new RunContext();
        runContext.setReason(HeartbeatReason.INTERVAL);
        runContext.setSessionId("test-session");
        runContext.setContextMode("lightweight");
        inputs.setRunContext(runContext);
        CallbackContext ctx = context("inputs", inputs);

        heartbeatRail.beforeInvoke(ctx);
        heartbeatRail.beforeModelCall(ctx);

        assertThat(ctx.get("run_kind")).isEqualTo(RunKind.HEARTBEAT);
        assertThat(ctx.get("run_context")).isSameAs(runContext);
        assertThat(ctx.get("heartbeat_enabled")).isEqualTo(Boolean.TRUE);
        assertThat(ctx.get("heartbeat_section")).isNotNull();
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void deepAgentTaskLoopRealMultistepSteerFollowUp() {
    }

    @Test
    void deepAgentAutoRailsCreationE2e() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnablePlanMode(true);
        config.setSkills(List.of("name", "test_skill", "description", "test"));
        config.setWorkspace(new Workspace(tempDir.toString(), "cn"));
        DeepAgent agent = new DeepAgent();

        agent.configure(config);

        assertThat(agent.findRailsByType(TaskPlanningRail.class)).hasSize(1);
        assertThat(agent.findRailsByType(SkillUseRail.class)).hasSize(1);
        assertThat(toolIds(agent)).contains("todo_create", "skill_tool", "list_skill");
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void deepAgentStreamE2eRequireApiKeyBase() {
    }

    @Test
    @Disabled(PYTHON_SKIP_REASON)
    void deepAgentTaskLoopStreamE2e() {
    }

    private static DeepAgent configuredAgentWithPlanMode() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnablePlanMode(true);
        DeepAgent agent = new DeepAgent();
        agent.configure(config);
        return agent;
    }

    private static ToolOutput invoke(List<String> toolCalls, Tool tool, Map<String, Object> inputs) throws Exception {
        toolCalls.add(tool.getCard().getId());
        return (ToolOutput) tool.invoke(inputs);
    }

    private static Map<?, ?> mapData(ToolOutput output) {
        return (Map<?, ?>) output.getData();
    }

    private static List<String> toolIds(DeepAgent agent) {
        return agent.getTools().values().stream()
                .map(tool -> tool.getCard().getId())
                .toList();
    }

    private static CallbackContext context(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return new CallbackContext(configuredAgentWithPlanMode(), map);
    }

    private static TodoItem todo(String id, String content, TodoStatus status) {
        return new TodoItem(id, content, "Executing " + content, "", status, List.of(), null, null, null);
    }

    private static List<TodoItem> todos(TodoItem... items) {
        return new ArrayList<>(List.of(items));
    }
}
