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
import com.openjiuwen.harness.tools.subagent.SessionTools;
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
 * Supplemental parity coverage for DeepAgent subagent/session system tests.
 *
 * <p>Mirrors Python's collected tests in
 * {@code tests/system_tests/harness/test_deep_agent_subagent.py}.</p>
 */
class DeepAgentSubagentMissingTest {

    private static final String PYTHON_SKIP_REASON = "Skipped in Python source: skip system test";

    @TempDir
    Path tempDir;

    @Test
    void testDeepAgentE2eAutoRailsCreationE2e() {
        assertAutoRailsCreation();
    }

    @Test
    void testDeepAgentE2eComplexTaskMultiToolChain() throws Exception {
        assertComplexTaskMultiToolChain();
    }

    @Test
    void testDeepAgentE2eHeartbeat() {
        assertHeartbeat();
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testDeepAgentE2eInvokeE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testDeepAgentE2eStreamE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testDeepAgentE2eTaskLoopRealMultistepSteerFollowUp() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void testDeepAgentE2eTaskLoopStreamE2e() {
    }

    @Test
    void testDeepAgentE2eTaskPlanning() {
        assertTaskPlanning();
    }

    @Test
    void testDeepAgentE2eTaskPlanningWithProgressReminder() {
        assertTaskPlanningWithProgressReminder();
    }

    @Test
    void subagentRailAutoRailsCreationE2e() {
        assertAutoRailsCreation();
    }

    @Test
    void subagentRailComplexTaskMultiToolChain() throws Exception {
        assertComplexTaskMultiToolChain();
    }

    @Test
    void subagentRailHeartbeat() {
        assertHeartbeat();
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void subagentRailInvokeE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void subagentRailStreamE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void subagentRailTaskLoopRealMultistepSteerFollowUp() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void subagentRailTaskLoopStreamE2e() {
    }

    @Test
    void subagentRailTaskPlanning() {
        assertTaskPlanning();
    }

    @Test
    void subagentRailTaskPlanningWithProgressReminder() {
        assertTaskPlanningWithProgressReminder();
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void subagentRailTasksUsingPredefinedSubagents() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void subagentRailTasksUsingSubagents() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailAsyncSpawnQuery2NotBlocked() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailAsyncSpawnSteeringVisibleDuringQuery3() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailAutoInvokeDedupMultiSpawn() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailAutoInvokeOnSpawnDoneNoQuery2() {
    }

    @Test
    void sessionRailDeepAgentAutoRailsCreationE2e() {
        assertAutoRailsCreation();
    }

    @Test
    void sessionRailDeepAgentComplexTaskMultiToolChain() throws Exception {
        assertComplexTaskMultiToolChain();
    }

    @Test
    void sessionRailDeepAgentHeartbeat() {
        assertHeartbeat();
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailDeepAgentInvokeE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailDeepAgentStreamE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailDeepAgentTaskLoopRealMultistepSteerFollowUp() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailDeepAgentTaskLoopStreamE2e() {
    }

    @Test
    void sessionRailDeepAgentTaskPlanning() {
        assertTaskPlanning();
    }

    @Test
    void sessionRailDeepAgentTaskPlanningWithProgressReminder() {
        assertTaskPlanningWithProgressReminder();
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void sessionRailRealLlmTwoSpawnCancelOneOtherCompletes() {
    }

    @Test
    void cancelMockDeepAgentAutoRailsCreationE2e() {
        assertAutoRailsCreation();
    }

    @Test
    void cancelMockDeepAgentComplexTaskMultiToolChain() throws Exception {
        assertComplexTaskMultiToolChain();
    }

    @Test
    void cancelMockDeepAgentHeartbeat() {
        assertHeartbeat();
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void cancelMockDeepAgentInvokeE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void cancelMockDeepAgentStreamE2eRequireApiKeyBase() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void cancelMockDeepAgentTaskLoopRealMultistepSteerFollowUp() {
    }

    @Disabled(PYTHON_SKIP_REASON)
    @Test
    void cancelMockDeepAgentTaskLoopStreamE2e() {
    }

    @Test
    void cancelMockDeepAgentTaskPlanning() {
        assertTaskPlanning();
    }

    @Test
    void cancelMockDeepAgentTaskPlanningWithProgressReminder() {
        assertTaskPlanningWithProgressReminder();
    }

    @Test
    void sessionsCancelScenario1ImmediateCancel() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = runningToolkit("cancel_s1_task_id", "long task");

        ToolOutput output = cancel(toolkit, "cancel_s1_task_id");

        assertThat(output.isSuccess()).isTrue();
        assertThat(toolkit.get("cancel_s1_task_id").status()).isEqualTo("canceled");
    }

    @Test
    void sessionsCancelScenario2CancelWhenRunning() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = runningToolkit("cancel_s2_task_id", "slow analysis");

        ToolOutput output = cancel(toolkit, "cancel_s2_task_id");

        assertThat(output.isSuccess()).isTrue();
        assertThat(toolkit.get("cancel_s2_task_id").status()).isEqualTo("canceled");
    }

    @Test
    void sessionsCancelScenario3CancelShouldNotTriggerSteering() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = runningToolkit("cancel_s3_task_id", "slow task");

        cancel(toolkit, "cancel_s3_task_id");
        ToolOutput listed = (ToolOutput) new SessionTools.SessionsListTool(toolkit).invoke(Map.of());

        assertThat(String.valueOf(listed.getData())).contains("cancel_s3_task_id", "canceled");
        assertThat(String.valueOf(listed.getData())).doesNotContain("[STEERING]");
    }

    @Test
    void sessionsCancelScenario4CancelOneOfMultipleTasks() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();
        toolkit.upsertRunning("cancel_s4_task_id_1", "sub-1", "task a");
        toolkit.upsertRunning("cancel_s4_task_id_2", "sub-2", "task b");
        toolkit.markCompleted("cancel_s4_task_id_2", "b done");

        cancel(toolkit, "cancel_s4_task_id_1");

        assertThat(toolkit.get("cancel_s4_task_id_1").status()).isEqualTo("canceled");
        assertThat(toolkit.get("cancel_s4_task_id_2").status()).isEqualTo("completed");
    }

    @Test
    void sessionsCancelScenario5RepeatCancelIdempotent() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = runningToolkit("cancel_s5_task_id", "task");

        cancel(toolkit, "cancel_s5_task_id");
        cancel(toolkit, "cancel_s5_task_id");

        assertThat(toolkit.get("cancel_s5_task_id").status()).isEqualTo("canceled");
    }

    @Test
    void sessionsCancelScenario6CancelCompletedTask() throws Exception {
        SessionTools.InMemorySessionToolkit toolkit = runningToolkit("cancel_s6_task_id", "fast complete");
        toolkit.markCompleted("cancel_s6_task_id", "completed quickly");

        ToolOutput output = cancel(toolkit, "cancel_s6_task_id");

        assertThat(output.isSuccess()).isFalse();
        assertThat(toolkit.get("cancel_s6_task_id").status()).isEqualTo("completed");
    }

    private void assertComplexTaskMultiToolChain() throws Exception {
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

    private static void assertTaskPlanning() {
        DeepAgent agent = configuredAgentWithPlanMode();

        Map<String, Object> result = agent.invoke(Map.of("query", "构建一个打卡系统")).join();

        assertThat(result).containsEntry("type", "deep_agent_result");
        assertThat(agent.findRailsByType(TaskPlanningRail.class)).hasSize(1);
        assertThat(toolIds(agent)).contains("todo_create", "todo_list", "todo_modify");
    }

    private static void assertTaskPlanningWithProgressReminder() {
        TaskPlanningRail taskPlanning = new TaskPlanningRail(true, 3, Map.of());
        CallbackContext ctx = context(
                "session_id", "deep-agent-subagent-planning",
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

        assertThat(taskPlanning.getToolCallCount("deep-agent-subagent-planning")).isEqualTo(3);
        assertThat(ctx.get("should_repeat_progress")).isEqualTo(Boolean.TRUE);
        assertThat(ctx.get("messages")).asList().singleElement().asString().contains("确保计划正在正确执行", "任务2");
    }

    private static void assertHeartbeat() {
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

    private void assertAutoRailsCreation() {
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

    private static SessionTools.InMemorySessionToolkit runningToolkit(String taskId, String description) {
        SessionTools.InMemorySessionToolkit toolkit = new SessionTools.InMemorySessionToolkit();
        toolkit.upsertRunning(taskId, taskId + "_sub_session", description);
        return toolkit;
    }

    private static ToolOutput cancel(SessionTools.InMemorySessionToolkit toolkit, String taskId) throws Exception {
        return (ToolOutput) new SessionTools.SessionsCancelTool(toolkit).invoke(Map.of("task_id", taskId));
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
