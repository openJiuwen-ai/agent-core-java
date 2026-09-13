// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supplementary tests for {@link AbilityManager} — execute, WorkflowCard, McpServerConfig.
 */
class AbilityManagerSupplementTest {
    private AbilityManager manager;

    @BeforeEach
    void setUp() {
        manager = new AbilityManager();
    }

    // ========== WorkflowCard ==========

    @Test
    void testAddAndGetWorkflowCard() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-1").description("test workflow")
                .inputParams(Map.of("type", "object")).build();

        manager.add(wc);

        Object result = manager.get("wf-1");
        assertThat(result).isInstanceOf(WorkflowCard.class);
        assertThat(((WorkflowCard) result).getName()).isEqualTo("wf-1");
    }

    @Test
    void testRemoveWorkflowCard() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-rem").build();
        manager.add(wc);

        Object removed = manager.remove("wf-rem");
        assertThat(removed).isNotNull();
        assertThat(manager.get("wf-rem")).isNull();
    }

    @Test
    void testListToolInfoWorkflow() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-info").description("workflow desc")
                .inputParams(Map.of("type", "object")).build();

        manager.add(wc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("wf-info");
        assertThat(infos.get(0).getDescription()).isEqualTo("workflow desc");
    }

    // ========== McpServerConfig ==========

    @Test
    void testAddAndGetMcpServerConfig() {
        McpServerConfig mcp = McpServerConfig.builder().serverName("mcp-server-1").serverId("mcp-id-1").build();

        manager.add(mcp);

        Object result = manager.get("mcp-server-1");
        assertThat(result).isInstanceOf(McpServerConfig.class);
    }

    @Test
    void testRemoveMcpServerAlsoRemovesAssociatedTools() {
        McpServerConfig mcp = McpServerConfig.builder().serverName("mcp-svr").serverId("mcp-prefix").build();
        manager.add(mcp);

        // Add tool cards that belong to this MCP server (id prefixed with serverId)
        ToolCard tc1 = ToolCard.builder().name("tool1").id("mcp-prefix.tool1").build();
        ToolCard tc2 = ToolCard.builder().name("tool2").id("mcp-prefix.tool2").build();
        ToolCard tc3 = ToolCard.builder().name("tool3").id("other.tool3").build();
        manager.add(tc1);
        manager.add(tc2);
        manager.add(tc3);

        assertThat(manager.list()).hasSize(4);

        // Remove MCP server — should also remove tool1 and tool2
        Object removed = manager.remove("mcp-svr");
        assertThat(removed).isNotNull();
        assertThat(manager.get("mcp-svr")).isNull();
        assertThat(manager.get("tool1")).isNull();
        assertThat(manager.get("tool2")).isNull();
        assertThat(manager.get("tool3")).isNotNull(); // Not removed - different prefix
    }

    // ========== Mixed abilities ==========

    @Test
    void testListAllMixed() {
        ToolCard tc = ToolCard.builder().name("t1").build();
        WorkflowCard wc = WorkflowCard.builder().name("w1").build();
        AgentCard ac = AgentCard.builder().name("a1").build();
        McpServerConfig mcp = McpServerConfig.builder().serverName("m1").build();

        manager.add(tc);
        manager.add(wc);
        manager.add(ac);
        manager.add(mcp);

        assertThat(manager.list()).hasSize(4);
    }

    @Test
    void testGetSearchesAllMaps() {
        // Verify get() searches tools -> workflows -> agents -> mcpServers
        WorkflowCard wc = WorkflowCard.builder().name("unique-wf").build();
        manager.add(wc);
        assertThat(manager.get("unique-wf")).isNotNull();

        AgentCard ac = AgentCard.builder().name("unique-agent").build();
        manager.add(ac);
        assertThat(manager.get("unique-agent")).isNotNull();

        McpServerConfig mcp = McpServerConfig.builder().serverName("unique-mcp").build();
        manager.add(mcp);
        assertThat(manager.get("unique-mcp")).isNotNull();
    }

    @Test
    void testRemoveSearchesAllMaps() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-to-remove").build();
        AgentCard ac = AgentCard.builder().name("ag-to-remove").build();
        manager.add(wc);
        manager.add(ac);

        Object removedWf = manager.remove("wf-to-remove");
        assertThat(removedWf).isNotNull();

        Object removedAg = manager.remove("ag-to-remove");
        assertThat(removedAg).isNotNull();
    }

    // ========== ToolInfo with nulls ==========

    @Test
    void testListToolInfoNullDescription() {
        ToolCard tc = ToolCard.builder().name("no-desc").build();
        manager.add(tc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getDescription()).isEqualTo("");
    }

    @Test
    void testListToolInfoNullInputParams() {
        ToolCard tc = ToolCard.builder().name("no-params").description("desc").build();
        manager.add(tc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos.get(0).getParameters()).isNotNull();
    }

    @Test
    void testListToolInfoWorkflowNullDescription() {
        WorkflowCard wc = WorkflowCard.builder().name("wf-null-desc").build();
        manager.add(wc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos.get(0).getDescription()).isEqualTo("");
    }

    @Test
    void testListToolInfoAgentWithInputParams() {
        AgentCard ac = AgentCard.builder().name("agent-params").description("desc")
                .inputParams(Map.of("query", Map.of("type", "string"))).build();
        manager.add(ac);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) infos.get(0).getParameters();
        assertThat(params).containsEntry("query", Map.of("type", "string"));
    }

    // ========== normalizeToolCalls ==========

    @Test
    void testExecuteWithEmptyList() {
        com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx =
            com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, List.of(), null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithInvalidToolCallType() {
        com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx =
            com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build();

        // Passing a string instead of ToolCall — normalizeToolCalls should log warning
        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, "not a tool call", null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecuteWithNullToolCall() {
        com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx =
            com.openjiuwen.core.singleagent.rail.AgentCallbackContext.builder().build();

        List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, null, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void testExecutePreservesSkipToolMarkerThroughAfterToolCallThenClearsIt() {
        class SkippingAgent extends BaseAgent {
            SkippingAgent() {
                super(AgentCard.builder().id("skip-agent").name("skip-agent").build());
            }

            @Override
            public BaseAgent configure(Object config) {
                return this;
            }

            @Override
            public Object getConfig() {
                return null;
            }

            @Override
            public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
                return null;
            }

            @Override
            public java.util.Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                    List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
                return List.of().iterator();
            }
        }

        class SkipRail extends AgentRail {
            private boolean skipVisibleInAfter;

            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                ctx.getExtra().put("_skip_tool", Boolean.TRUE);
            }

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                skipVisibleInAfter = Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"));
            }
        }

        String toolId = "skip-tool-" + UUID.randomUUID();
        LocalFunction tool =
            new LocalFunction(ToolCard.builder().id(toolId).name(toolId).description("skip test").build(),
                    inputs -> "should-not-run");
        Runner.resourceMgr().addTool(tool, null);
        try {
            SkippingAgent agent = new SkippingAgent();
            SkipRail rail = new SkipRail();
            agent.registerRail(rail);
            manager.add(tool.getCard());
            Map<String, Object> extra = new java.util.LinkedHashMap<>();
            AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).extra(extra).build();

            List<AbilityManager.ToolExecutionEntry> results =
                manager.execute(ctx, ToolCall.builder().id("tc-skip").name(toolId).arguments("{}").build(), null, null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).result()).isNull();
            assertThat(rail.skipVisibleInAfter).isTrue();
            assertThat(extra).doesNotContainKey("_skip_tool");
        } finally {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
    }

    // 验证多个工具调用默认会并行执行，同时结果仍按输入的 ToolCall 顺序返回。
    @Test
    void testExecuteRunsMultipleToolCallsInParallelAndKeepsResultOrder() {
        CountDownLatch bothToolsStarted = new CountDownLatch(2);
        AtomicInteger invocationOrder = new AtomicInteger();
        String firstToolId = "parallel-first-" + UUID.randomUUID();
        String secondToolId = "parallel-second-" + UUID.randomUUID();
        LocalFunction firstTool = blockingTool(firstToolId, bothToolsStarted, invocationOrder);
        LocalFunction secondTool = blockingTool(secondToolId, bothToolsStarted, invocationOrder);

        Runner.resourceMgr().addTool(firstTool, null);
        Runner.resourceMgr().addTool(secondTool, null);
        manager.add(List.of(firstTool.getCard(), secondTool.getCard()));
        try {
            AgentCallbackContext ctx = AgentCallbackContext.builder().build();
            List<ToolCall> toolCalls = List.of(
                    ToolCall.builder().id("tc-first").name(firstToolId).arguments("{}").build(),
                    ToolCall.builder().id("tc-second").name(secondToolId).arguments("{}").build()
            );

            long start = System.nanoTime();
            List<AbilityManager.ToolExecutionEntry> results = manager.execute(ctx, toolCalls, null, null);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(results).hasSize(2);
            assertThat(String.valueOf(results.get(0).result())).startsWith(firstToolId + ":parallel=true");
            assertThat(String.valueOf(results.get(1).result())).startsWith(secondToolId + ":parallel=true");
            assertThat(elapsedMillis).isLessThan(1500L);
        } finally {
            removeTool(firstToolId);
            removeTool(secondToolId);
        }
    }

    // 验证同一轮 tool call 的并行提交数量受默认上限（3）约束。
    @Test
    void testExecuteCapsParallelToolCallsToDefaultLimit() {
        assertCappedParallelExecution(null, 4, 3, false);
    }

    // 验证 ReActAgentConfig.maxParallelToolCalls 生效。
    @Test
    void testExecuteCapsParallelToolCallsUsingAgentConfig() {
        ReActAgentConfig config = ReActAgentConfig.builder().maxParallelToolCalls(1).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();
        assertCappedParallelExecution(ctx, 4, 1, false);
    }

    // 验证流式并行路径同样遵守 ReActAgentConfig 并行上限。
    @Test
    void testExecuteStreamCapsParallelToolCallsToConfiguredLimit() {
        ReActAgentConfig config = ReActAgentConfig.builder().maxParallelToolCalls(2).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();
        assertCappedParallelExecution(ctx, 5, 2, true);
    }

    // 验证提交循环在等待许可时响应中断：停止后续 submit，已提交的仍会 join。
    @Test
    void testExecuteStopsSubmittingRemainingToolsWhenInterrupted() throws Exception {
        ReActAgentConfig config = ReActAgentConfig.builder().maxParallelToolCalls(1).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();
        AtomicInteger startedCount = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstMayFinish = new CountDownLatch(1);
        List<String> toolIds = new ArrayList<>();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String toolId = "interrupt-gate-" + i + "-" + UUID.randomUUID();
            toolIds.add(toolId);
            LocalFunction tool = interruptibleGateTool(toolId, startedCount, firstStarted, firstMayFinish);
            Runner.resourceMgr().addTool(tool, null);
            manager.add(tool.getCard());
            toolCalls.add(ToolCall.builder().id("tc-int-" + i).name(toolId).arguments("{}").build());
        }

        AtomicReference<List<AbilityManager.ToolExecutionEntry>> resultsRef = new AtomicReference<>();
        AtomicBoolean isInterruptedAfterExecute = new AtomicBoolean();
        Thread executeThread = new Thread(() -> {
            resultsRef.set(manager.execute(ctx, toolCalls, null, null));
            isInterruptedAfterExecute.set(Thread.currentThread().isInterrupted());
        }, "gated-submit-interrupt-test");
        try {
            executeThread.start();
            assertThat(firstStarted.await(3, TimeUnit.SECONDS)).isTrue();
            executeThread.interrupt();
            firstMayFinish.countDown();
            executeThread.join(5_000L);
            assertThat(executeThread.isAlive()).isFalse();

            List<AbilityManager.ToolExecutionEntry> results = resultsRef.get();
            assertThat(results).isNotNull().hasSize(3);
            assertThat(startedCount.get()).isEqualTo(1);
            assertThat(String.valueOf(results.get(0).result())).isEqualTo(toolIds.get(0));
            assertThat(String.valueOf(results.get(1).toolMessage().getContent())).contains("cancelled");
            assertThat(String.valueOf(results.get(2).toolMessage().getContent())).contains("cancelled");
            assertThat(isInterruptedAfterExecute.get()).isTrue();
        } finally {
            firstMayFinish.countDown();
            toolIds.forEach(AbilityManagerSupplementTest::removeTool);
        }
    }

    // 验证 tool-call 超时完成 Future 后会释放提交许可，后续 tool 不必等挂起的 worker 结束。
    @Test
    void testExecuteTimeoutReleasesGatePermitBeforeWorkerFinishes() throws Exception {
        System.setProperty("openjiuwen.executor.tool-call.timeout-millis", "200");
        ReActAgentConfig config = ReActAgentConfig.builder().maxParallelToolCalls(1).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicLong firstFinishedAt = new AtomicLong(-1L);
        AtomicLong secondStartedAt = new AtomicLong(-1L);
        String firstId = "timeout-gate-first-" + UUID.randomUUID();
        String secondId = "timeout-gate-second-" + UUID.randomUUID();
        LocalFunction firstTool = hangingGateTool(firstId, firstStarted, firstFinishedAt, 2_000L);
        LocalFunction secondTool = startSignalTool(secondId, secondStarted, secondStartedAt);
        Runner.resourceMgr().addTool(firstTool, null);
        Runner.resourceMgr().addTool(secondTool, null);
        manager.add(List.of(firstTool.getCard(), secondTool.getCard()));
        try {
            long start = System.nanoTime();
            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    List.of(
                            ToolCall.builder().id("tc-timeout-1").name(firstId).arguments("{}").build(),
                            ToolCall.builder().id("tc-timeout-2").name(secondId).arguments("{}").build()
                    ),
                    null,
                    null
            );
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(results).hasSize(2);
            assertThat(String.valueOf(results.get(0).toolMessage().getContent())).contains("error");
            assertThat(results.get(1).result()).isEqualTo(secondId);
            assertThat(secondStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(elapsedMillis).isLessThan(1_500L);
            assertThat(secondStartedAt.get()).isPositive();
            assertThat(firstFinishedAt.get()).isEqualTo(-1L);
        } finally {
            System.clearProperty("openjiuwen.executor.tool-call.timeout-millis");
            removeTool(firstId);
            removeTool(secondId);
        }
    }

    // 验证并发工具调用会接入 OpenJiuwen 统一 tool-call 线程池，而不是默认 common pool。
    @Test
    void testParallelExecuteUsesOpenJiuwenToolCallExecutor() {
        CountDownLatch bothToolsStarted = new CountDownLatch(2);
        String firstToolId = "executor-first-" + UUID.randomUUID();
        String secondToolId = "executor-second-" + UUID.randomUUID();
        LocalFunction firstTool = threadNameTool(firstToolId, bothToolsStarted);
        LocalFunction secondTool = threadNameTool(secondToolId, bothToolsStarted);

        Runner.resourceMgr().addTool(firstTool, null);
        Runner.resourceMgr().addTool(secondTool, null);
        manager.add(List.of(firstTool.getCard(), secondTool.getCard()));
        try {
            AgentCallbackContext ctx = AgentCallbackContext.builder().build();

            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    List.of(
                            ToolCall.builder().id("tc-executor-1").name(firstToolId).arguments("{}").build(),
                            ToolCall.builder().id("tc-executor-2").name(secondToolId).arguments("{}").build()
                    ),
                    null,
                    null
            );

            assertThat(results).hasSize(2);
            assertThat(results).extracting(entry -> String.valueOf(entry.result()))
                    .anyMatch(threadName -> threadName.startsWith("openjiuwen-tool-call-"))
                    .noneMatch(threadName -> threadName.startsWith("ForkJoinPool.commonPool-worker"));
        } finally {
            removeTool(firstToolId);
            removeTool(secondToolId);
        }
    }

    // 验证某个工具被 rail 标记跳过时，_skip_tool 不会污染其他并发工具调用。
    @Test
    void testParallelExecuteDoesNotLetSkipMarkerLeakAcrossTools() {
        String skipToolId = "skip-me-" + UUID.randomUUID();
        class SelectiveSkippingAgent extends BaseAgent {
            SelectiveSkippingAgent() {
                super(AgentCard.builder().id("selective-skip-agent").name("selective-skip-agent").build());
            }

            @Override
            public BaseAgent configure(Object config) {
                return this;
            }

            @Override
            public Object getConfig() {
                return null;
            }

            @Override
            public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
                return null;
            }

            @Override
            public java.util.Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                    List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
                return List.of().iterator();
            }
        }

        class SelectiveSkipRail extends AgentRail {
            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
                    return;
                }
                if (!skipToolId.equals(inputs.getToolName())) {
                    return;
                }
                ctx.getExtra().put("_skip_tool", Boolean.TRUE);
                inputs.setToolResult("skipped");
                inputs.setToolMsg(ToolMessage.builder()
                        .content("skipped")
                        .toolCallId(inputs.getToolCall().getId())
                        .build());
            }
        }

        String runToolId = "run-me-" + UUID.randomUUID();
        LocalFunction skipTool = new LocalFunction(
                ToolCard.builder().id(skipToolId).name(skipToolId).description("skip").build(),
                inputs -> "should-not-run"
        );
        LocalFunction runTool = new LocalFunction(
                ToolCard.builder().id(runToolId).name(runToolId).description("run").build(),
                inputs -> "ran"
        );

        Runner.resourceMgr().addTool(skipTool, null);
        Runner.resourceMgr().addTool(runTool, null);
        manager.add(List.of(skipTool.getCard(), runTool.getCard()));
        try {
            SelectiveSkippingAgent agent = new SelectiveSkippingAgent();
            agent.registerRail(new SelectiveSkipRail());
            Map<String, Object> parentExtra = new java.util.LinkedHashMap<>();
            AgentCallbackContext ctx = AgentCallbackContext.builder()
                    .agent(agent)
                    .extra(parentExtra)
                    .build();

            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    List.of(
                            ToolCall.builder().id("tc-skip").name(skipToolId).arguments("{}").build(),
                            ToolCall.builder().id("tc-run").name(runToolId).arguments("{}").build()
                    ),
                    null,
                    null
            );

            assertThat(results).hasSize(2);
            assertThat(results.get(0).result()).isEqualTo("skipped");
            assertThat(results.get(1).result()).isEqualTo("ran");
            assertThat(parentExtra).doesNotContainKey("_skip_tool");
        } finally {
            removeTool(skipToolId);
            removeTool(runToolId);
        }
    }

    // 验证工具在线程池 worker 中执行时，仍能通过 SessionContextHolder 读取当前 session。
    @Test
    void testParallelExecuteKeepsSessionContextAvailableInWorkerThreads() {
        String firstToolId = "session-first-" + UUID.randomUUID();
        String secondToolId = "session-second-" + UUID.randomUUID();
        AgentSessionApi session = new AgentSessionApi("session-parallel");
        LocalFunction firstTool = sessionAwareTool(firstToolId);
        LocalFunction secondTool = sessionAwareTool(secondToolId);

        Runner.resourceMgr().addTool(firstTool, null);
        Runner.resourceMgr().addTool(secondTool, null);
        manager.add(List.of(firstTool.getCard(), secondTool.getCard()));
        try {
            AgentCallbackContext ctx = AgentCallbackContext.builder().build();

            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    List.of(
                            ToolCall.builder().id("tc-session-1").name(firstToolId).arguments("{}").build(),
                            ToolCall.builder().id("tc-session-2").name(secondToolId).arguments("{}").build()
                    ),
                    session,
                    null
            );

            assertThat(results).hasSize(2);
            assertThat(results).extracting(entry -> String.valueOf(entry.result()))
                    .containsExactly("session-parallel", "session-parallel");
        } finally {
            removeTool(firstToolId);
            removeTool(secondToolId);
        }
    }

    // 验证工具执行的嵌套 session 绑定退出后会恢复旧值，且 afterToolCall 仍能读到当前工具 session。
    @Test
    void testExecuteRestoresPreviousSessionAndKeepsSessionVisibleInAfterToolCall() {
        class SessionAwareAgent extends BaseAgent {
            SessionAwareAgent() {
                super(AgentCard.builder().id("session-aware-agent").name("session-aware-agent").build());
            }

            @Override
            public BaseAgent configure(Object config) {
                return this;
            }

            @Override
            public Object getConfig() {
                return null;
            }

            @Override
            public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
                return null;
            }

            @Override
            public java.util.Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                    List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
                return List.of().iterator();
            }
        }

        class SessionRail extends AgentRail {
            private String afterToolSessionId;

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                com.openjiuwen.core.session.Session currentSession = SessionContextHolder.getCurrentSession();
                afterToolSessionId = currentSession != null ? currentSession.getSessionId() : null;
            }
        }

        String toolId = "restore-session-" + UUID.randomUUID();
        AgentSessionApi previousSession = new AgentSessionApi("previous-session");
        AgentSessionApi toolSession = new AgentSessionApi("tool-session");
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("session restore").build(),
                (LocalFunction.ContextFunction) (inputs, kwargs) -> {
                    assertThat(SessionContextHolder.getCurrentSession()).isSameAs(toolSession);
                    return "ok";
                }
        );

        Runner.resourceMgr().addTool(tool, null);
        try {
            SessionAwareAgent agent = new SessionAwareAgent();
            SessionRail rail = new SessionRail();
            agent.registerRail(rail);
            manager.add(tool.getCard());

            SessionContextHolder.setCurrentSession(previousSession);
            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    AgentCallbackContext.builder().agent(agent).build(),
                    ToolCall.builder().id("tc-session-restore").name(toolId).arguments("{}").build(),
                    toolSession,
                    null
            );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).result()).isEqualTo("ok");
            assertThat(rail.afterToolSessionId).isEqualTo("tool-session");
            assertThat(SessionContextHolder.getCurrentSession()).isSameAs(previousSession);
        } finally {
            SessionContextHolder.clearCurrentSession();
            removeTool(toolId);
        }
    }

    // 验证并发工具里的 force_finish 请求会从子工具 context 回传到父 context。
    @Test
    void testParallelExecutePropagatesForceFinishFromToolContext() {
        String finishToolId = "force-finish-" + UUID.randomUUID();
        class ForceFinishAgent extends BaseAgent {
            ForceFinishAgent() {
                super(AgentCard.builder().id("force-finish-agent").name("force-finish-agent").build());
            }

            @Override
            public BaseAgent configure(Object config) {
                return this;
            }

            @Override
            public Object getConfig() {
                return null;
            }

            @Override
            public Object invoke(Object inputs, com.openjiuwen.core.session.Session session) {
                return null;
            }

            @Override
            public java.util.Iterator<Object> stream(Object inputs, com.openjiuwen.core.session.Session session,
                    List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
                return List.of().iterator();
            }
        }

        class ForceFinishRail extends AgentRail {
            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                if (ctx.getInputs() instanceof ToolCallInputs inputs && finishToolId.equals(inputs.getToolName())) {
                    ctx.requestForceFinish(Map.of("reason", "tool-force-finish"));
                }
            }
        }

        String otherToolId = "force-finish-other-" + UUID.randomUUID();
        LocalFunction finishTool = new LocalFunction(
                ToolCard.builder().id(finishToolId).name(finishToolId).description("finish").build(),
                inputs -> "finish"
        );
        LocalFunction otherTool = new LocalFunction(
                ToolCard.builder().id(otherToolId).name(otherToolId).description("other").build(),
                inputs -> "other"
        );

        Runner.resourceMgr().addTool(finishTool, null);
        Runner.resourceMgr().addTool(otherTool, null);
        manager.add(List.of(finishTool.getCard(), otherTool.getCard()));
        try {
            ForceFinishAgent agent = new ForceFinishAgent();
            agent.registerRail(new ForceFinishRail());
            AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    List.of(
                            ToolCall.builder().id("tc-force-finish").name(finishToolId).arguments("{}").build(),
                            ToolCall.builder().id("tc-force-other").name(otherToolId).arguments("{}").build()
                    ),
                    null,
                    null
            );

            assertThat(results).hasSize(2);
            assertThat(ctx.hasForceFinishRequest()).isTrue();
            assertThat(ctx.consumeForceFinish().getResult()).containsEntry("reason", "tool-force-finish");
        } finally {
            removeTool(finishToolId);
            removeTool(otherToolId);
        }
    }

    // 验证并发工具调用中一个工具失败时，其他工具的成功结果仍会被收集返回。
    @Test
    void testParallelExecuteCollectsFailureForOneToolAndContinuesOthers() {
        String failingToolId = "failing-tool-" + UUID.randomUUID();
        String okToolId = "ok-tool-" + UUID.randomUUID();
        LocalFunction failingTool = new LocalFunction(
                ToolCard.builder().id(failingToolId).name(failingToolId).description("fail").build(),
                inputs -> {
                    throw new IllegalStateException("boom");
                }
        );
        LocalFunction okTool = new LocalFunction(
                ToolCard.builder().id(okToolId).name(okToolId).description("ok").build(),
                inputs -> "ok"
        );

        Runner.resourceMgr().addTool(failingTool, null);
        Runner.resourceMgr().addTool(okTool, null);
        manager.add(List.of(failingTool.getCard(), okTool.getCard()));
        try {
            AgentCallbackContext ctx = AgentCallbackContext.builder().build();

            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    List.of(
                            ToolCall.builder().id("tc-fail").name(failingToolId).arguments("{}").build(),
                            ToolCall.builder().id("tc-ok").name(okToolId).arguments("{}").build()
                    ),
                    null,
                    null
            );

            assertThat(results).hasSize(2);
            assertThat(results.get(0).toolMessage().getContent().toString()).contains("Tool execution error");
            assertThat(results.get(1).result()).isEqualTo("ok");
        } finally {
            removeTool(failingToolId);
            removeTool(okToolId);
        }
    }

    @Test
    void toolErrorForceFinishesWhenFailTaskOnToolErrorEnabled() {
        String failingToolId = "fail-task-on-error-" + UUID.randomUUID();
        LocalFunction failingTool = new LocalFunction(
                ToolCard.builder().id(failingToolId).name(failingToolId).description("fail").build(),
                inputs -> {
                    throw new IllegalStateException("sandbox unavailable");
                }
        );

        Runner.resourceMgr().addTool(failingTool, null);
        manager.add(failingTool.getCard());
        try {
            ReActAgentConfig config = ReActAgentConfig.builder().shouldFailTaskOnToolError(true).build();
            AgentCallbackContext ctx = AgentCallbackContext.builder().config(config).build();

            List<AbilityManager.ToolExecutionEntry> results = manager.execute(
                    ctx,
                    ToolCall.builder().id("tc-fail-task").name(failingToolId).arguments("{}").build(),
                    null,
                    null
            );

            assertThat(results).hasSize(1);
            assertThat(ctx.hasForceFinishRequest()).isTrue();
            Map<String, Object> finish = ctx.consumeForceFinish().getResult();
            assertThat(finish.get("result_type")).isEqualTo("error");
            assertThat(finish.get("output").toString()).contains("sandbox unavailable");
            assertThat(finish.get("tool_outcomes")).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outcomes = (List<Map<String, Object>>) finish.get("tool_outcomes");
            assertThat(outcomes).hasSize(1);
            assertThat(outcomes.get(0).get("status")).isEqualTo("failed");
            assertThat(outcomes.get(0).get("tool_name")).isEqualTo(failingToolId);
        } finally {
            removeTool(failingToolId);
        }
    }

    // 验证工具任务被取消时，会转换为该工具的失败消息而不会向外抛出异常。
    @Test
    void testJoinToolExecutionCollectsCancelledToolFuture() {
        ToolCall toolCall = ToolCall.builder().id("tc-cancelled").name("cancelled-tool").arguments("{}").build();
        CompletableFuture<AbilityManager.ToolExecutionEntry> future = new CompletableFuture<>();
        future.cancel(false);

        AbilityManager.ToolExecutionEntry result = AbilityManager.joinToolExecution(toolCall, future);

        assertThat(result.result()).isNull();
        assertThat(result.toolMessage().getToolCallId()).isEqualTo("tc-cancelled");
        assertThat(result.toolMessage().getContent().toString()).isEqualTo("Ability execution cancelled");
    }

    @Test
    void testExecuteSingleToolCallNotFound() {
        ToolCall tc = ToolCall.builder().id("tc-1").name("nonexistent-tool").arguments("{}").build();

        // Tool not registered and not in ResourceMgr — should throw
        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallNullArguments() {
        ToolCall tc = ToolCall.builder().id("tc-2").name("nonexistent-tool").arguments(null).build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallBlankArguments() {
        ToolCall tc = ToolCall.builder().id("tc-3").name("nonexistent-tool").arguments("   ").build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteSingleToolCallInvalidJson() {
        ToolCall tc = ToolCall.builder().id("tc-4").name("nonexistent-tool").arguments("not json").build();

        // Invalid JSON args should be handled gracefully, then fail on tool lookup
        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class);
    }

    @Test
    void testExecuteAsToolExecutorWithNonToolCall() {
        var result = manager.executeAsToolExecutor("not a ToolCall", null);
        assertThat(result).isNotNull();
        assertThat(result.result()).isNull();
    }

    @Test
    void testExecuteSingleToolCallResolvesMcpToolByNameWithoutPreListing() throws Exception {
        String serverId = "mcp-server-id-" + UUID.randomUUID();
        String toolId = serverId + ".demo-server.browser_navigate";

        McpServerConfig server = McpServerConfig.builder().serverName("demo-server").serverId(serverId).build();
        manager.add(server);

        McpClient client = new McpClient() {
            @Override
            public boolean connect(int retryTimes, float timeout) {
                return true;
            }

            @Override
            public boolean disconnect(float timeout) {
                return true;
            }

            @Override
            public List<Object> listTools(float timeout) {
                return List.of();
            }

            @Override
            public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
                return Map.of("tool", toolName, "arguments", arguments);
            }

            @Override
            public Optional<Object> getToolInfo(String toolName, float timeout) {
                return Optional.empty();
            }

            @Override
            public String getServerPath() {
                return "mock://demo-server";
            }
        };

        McpToolCard card = McpToolCard.builder().id(toolId).name("browser_navigate").description("Navigate browser")
                .serverId(serverId).serverName("demo-server").build();
        Tool tool = new McpTool(client, card);
        Runner.resourceMgr().addTool(tool, "ut-mcp");

        try {
            ToolCall tc = ToolCall.builder().id("tc-mcp").name("browser_navigate")
                    .arguments("{\"url\":\"https://example.com\"}").build();

            AbilityManager.ToolExecutionEntry entry = manager.executeSingleToolCall(tc, null, null);

            assertThat(entry.result()).isEqualTo(Map.of("result",
                    Map.of("tool", "browser_navigate", "arguments", Map.of("url", "https://example.com"))));
            assertThat(manager.get("browser_navigate")).isInstanceOf(ToolCard.class);
        } finally {
            Runner.resourceMgr().removeTool(toolId, "ut-mcp", TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void testExecuteSingleToolCallMcpServerNameRaisesExplicitError() {
        manager.add(McpServerConfig.builder().serverName("mcp-server").serverId("mcp-server-id").build());

        ToolCall tc = ToolCall.builder().id("tc-mcp").name("mcp-server").arguments("{}").build();

        assertThatThrownBy(() -> manager.executeSingleToolCall(tc, null, null))
                .isInstanceOf(AbilityExecutionError.class).hasMessageContaining("not directly executable");
    }

    // ========== ToolExecutionEntry record ==========

    @Test
    void testToolExecutionEntryCreation() {
        ToolMessage msg = ToolMessage.builder().content("result").toolCallId("tc-1").build();
        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry("data", msg);

        assertThat(entry.result()).isEqualTo("data");
        assertThat(entry.toolMessage()).isSameAs(msg);
    }

    @Test
    void testToolExecutionEntryNulls() {
        AbilityManager.ToolExecutionEntry entry = new AbilityManager.ToolExecutionEntry(null, null);
        assertThat(entry.result()).isNull();
        assertThat(entry.toolMessage()).isNull();
    }

    private void assertCappedParallelExecution(
            AgentCallbackContext ctx,
            int toolCount,
            int expectedMaxInFlight,
            boolean stream
    ) {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        List<String> toolIds = new ArrayList<>();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            String toolId = "capped-" + expectedMaxInFlight + "-" + i + "-" + UUID.randomUUID();
            toolIds.add(toolId);
            LocalFunction tool = cappedConcurrencyTool(toolId, inFlight, maxInFlight);
            Runner.resourceMgr().addTool(tool, null);
            manager.add(tool.getCard());
            toolCalls.add(ToolCall.builder().id("tc-capped-" + i).name(toolId).arguments("{}").build());
        }

        AgentCallbackContext callbackContext = ctx != null ? ctx : AgentCallbackContext.builder().build();
        try {
            List<AbilityManager.ToolExecutionEntry> results = stream
                    ? manager.executeStream(callbackContext, toolCalls, null, null, null)
                    : manager.execute(callbackContext, toolCalls, null, null);

            assertThat(results).hasSize(toolCount);
            for (int i = 0; i < toolCount; i++) {
                assertThat(String.valueOf(results.get(i).result())).isEqualTo(toolIds.get(i));
            }
            assertThat(maxInFlight.get()).isEqualTo(expectedMaxInFlight);
        } finally {
            toolIds.forEach(AbilityManagerSupplementTest::removeTool);
        }
    }

    private static LocalFunction interruptibleGateTool(
            String toolId,
            AtomicInteger startedCount,
            CountDownLatch firstStarted,
            CountDownLatch firstMayFinish
    ) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("interrupt gate").build(),
                inputs -> {
                    int started = startedCount.incrementAndGet();
                    if (started == 1) {
                        firstStarted.countDown();
                        await(firstMayFinish, 5, TimeUnit.SECONDS);
                    }
                    return toolId;
                }
        );
    }

    private static LocalFunction hangingGateTool(
            String toolId,
            CountDownLatch started,
            AtomicLong finishedAt,
            long hangMillis
    ) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("hanging gate").build(),
                inputs -> {
                    started.countDown();
                    try {
                        TimeUnit.MILLISECONDS.sleep(hangMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishedAt.set(System.nanoTime());
                    }
                    return toolId;
                }
        );
    }

    private static LocalFunction startSignalTool(
            String toolId,
            CountDownLatch started,
            AtomicLong startedAt
    ) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("start signal").build(),
                inputs -> {
                    startedAt.set(System.nanoTime());
                    started.countDown();
                    return toolId;
                }
        );
    }

    private static LocalFunction cappedConcurrencyTool(
            String toolId,
            AtomicInteger inFlight,
            AtomicInteger maxInFlight
    ) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("capped parallel").build(),
                inputs -> {
                    int current = inFlight.incrementAndGet();
                    maxInFlight.accumulateAndGet(current, Math::max);
                    try {
                        TimeUnit.MILLISECONDS.sleep(80L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        inFlight.decrementAndGet();
                    }
                    return toolId;
                }
        );
    }

    private static LocalFunction blockingTool(
            String toolId,
            CountDownLatch bothToolsStarted,
            AtomicInteger invocationOrder
    ) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("parallel").build(),
                inputs -> {
                    int order = invocationOrder.incrementAndGet();
                    bothToolsStarted.countDown();
                    // 这里的 2 秒是等待另一个工具启动的最长时间，并行启动后会立即放行。
                    boolean parallel = await(bothToolsStarted, 2, TimeUnit.SECONDS);
                    return toolId + ":parallel=" + parallel + ":order=" + order;
                }
        );
    }

    private static LocalFunction sessionAwareTool(String toolId) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("session").build(),
                (inputs, kwargs) -> {
                    if (SessionContextHolder.getCurrentSession() == null) {
                        return "missing";
                    }
                    return SessionContextHolder.getCurrentSession().getSessionId();
                }
        );
    }

    private static LocalFunction threadNameTool(String toolId, CountDownLatch bothToolsStarted) {
        return new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("thread name").build(),
                inputs -> {
                    bothToolsStarted.countDown();
                    await(bothToolsStarted, 2, TimeUnit.SECONDS);
                    return Thread.currentThread().getName();
                }
        );
    }

    private static boolean await(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            return latch.await(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void removeTool(String toolId) {
        Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
    }
}
