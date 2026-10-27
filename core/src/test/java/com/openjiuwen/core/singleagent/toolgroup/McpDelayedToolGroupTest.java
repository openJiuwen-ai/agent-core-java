/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.toolgroup.ToolGroup;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Focused tests for delayed MCP tools managed through explicit tool groups.
 *
 * @since 0.1.17
 */
@Tag("issue196-w10")
class McpDelayedToolGroupTest {
    private static final Duration MCP_RESOLUTION_TIMEOUT = Duration.ofMillis(500L);
    private static final long MCP_RESOLUTION_DELAY_MILLIS = 20L;
    private static final long MCP_POLL_INTERVAL_MILLIS = 10L;

    private static final AtomicInteger TOOL_NAME_SEQUENCE = new AtomicInteger();

    private final List<String> registeredToolNames = new ArrayList<>();

    @AfterEach
    void removeRegisteredTools() {
        registeredToolNames.forEach(name -> Runner.resourceMgr().removeTool(name));
    }

    @Test
    @Tag("TC-TG-01")
    @Tag("TC-LNK-01")
    void delayedNormalAndMcpToolsVisibleAfterResolution()
            throws Exception {
        DelayedToolGroupAgent agent = new DelayedToolGroupAgent();
        String normalToolName = newNormalToolName();
        String mcpToolName = newMcpToolName();
        DelayedMcpAbilityManager abilityManager = new DelayedMcpAbilityManager(mcpToolName);
        agent.setAbilityManager(abilityManager);
        abilityManager.add(
                McpServerConfig.builder().serverName("weather").serverId("mcp-weather").build());
        registerGlobalTool(agent, normalToolName);
        agent.registerToolGroup(
                new ToolGroup(
                        "delayed_tools",
                        "Delayed tools",
                        List.of(normalToolName, mcpToolName)));

        MemorySession session = new MemorySession("delayed-resolution-session");
        agent.activateToolGroup("delayed_tools", session);

        assertThat(visibleToolNames(agent, session))
                .contains(normalToolName)
                .doesNotContain(mcpToolName);

        abilityManager.resolveAfterDelay(MCP_RESOLUTION_DELAY_MILLIS);
        awaitMcpToolVisible(agent, session, mcpToolName);

        assertThat(visibleToolNames(agent, session))
                .contains(normalToolName, mcpToolName);

        agent.deactivateToolGroup("delayed_tools", session);
        assertThat(visibleToolNames(agent, session))
                .doesNotContain(normalToolName, mcpToolName);
    }

    @Test
    @Tag("TC-TG-01")
    @Tag("TC-SEC-01")
    void delayedMcpToolUnavailableUntilResolved() throws Exception {
        DelayedToolGroupAgent agent = new DelayedToolGroupAgent();
        String mcpToolName = newMcpToolName();
        DelayedMcpAbilityManager abilityManager = new DelayedMcpAbilityManager(mcpToolName);
        agent.setAbilityManager(abilityManager);
        abilityManager.add(
                McpServerConfig.builder().serverName("weather").serverId("mcp-weather").build());
        agent.registerToolGroup(
                new ToolGroup("mcp_tools", "MCP tools", List.of(mcpToolName)));

        MemorySession session = new MemorySession("delayed-mcp-execution-session");
        agent.activateToolGroup("mcp_tools", session);

        List<AbilityManager.ExecutionResult> pendingResults =
                executeMcpTool(agent, session, "pending-mcp-call", mcpToolName);
        assertThat(pendingResults).hasSize(1);
        assertThat(pendingResults.get(0).toolMessage().getContentAsString())
                .doesNotContain("not active")
                .contains("Ability not found");
        assertThat(abilityManager.mcpToolExecutionCount()).isZero();

        abilityManager.resolveAfterDelay(MCP_RESOLUTION_DELAY_MILLIS);
        awaitMcpToolVisible(agent, session, mcpToolName);

        List<AbilityManager.ExecutionResult> resolvedResults =
                executeMcpTool(agent, session, "resolved-mcp-call", mcpToolName);
        assertThat(resolvedResults.get(0).toolMessage().getContentAsString())
                .contains("resolved");
        assertThat(abilityManager.mcpToolExecutionCount()).isEqualTo(1);
    }

    @Test
    @Tag("TC-TG-04")
    @Tag("TC-SEC-01")
    void deactivatingGroupDuringMcpResolutionPreventsExecution()
            throws Exception {
        DelayedToolGroupAgent agent = new DelayedToolGroupAgent();
        String mcpToolName = newMcpToolName();
        DelayedMcpAbilityManager abilityManager = new DelayedMcpAbilityManager(mcpToolName);
        agent.setAbilityManager(abilityManager);
        abilityManager.add(
                McpServerConfig.builder().serverName("weather").serverId("mcp-weather").build());
        agent.registerToolGroup(
                new ToolGroup("mcp_tools", "MCP tools", List.of(mcpToolName)));

        MemorySession session = new MemorySession("deactivate-during-resolution-session");
        agent.activateToolGroup("mcp_tools", session);
        assertThat(visibleToolNames(agent, session)).doesNotContain(mcpToolName);

        List<AbilityManager.ExecutionResult> pendingResults =
                executeMcpTool(agent, session, "pending-deactivated-mcp-call", mcpToolName);
        assertThat(pendingResults.get(0).toolMessage().getContentAsString())
                .doesNotContain("not active")
                .contains("Ability not found");
        assertThat(abilityManager.mcpToolExecutionCount()).isZero();

        agent.deactivateToolGroup("mcp_tools", session);
        abilityManager.resolveAfterDelay(MCP_RESOLUTION_DELAY_MILLIS);
        abilityManager.awaitResolution();

        assertThat(visibleToolNames(agent, session)).doesNotContain(mcpToolName);
        List<AbilityManager.ExecutionResult> rejectedResults =
                executeMcpTool(agent, session, "deactivated-mcp-call", mcpToolName);
        assertThat(rejectedResults.get(0).toolMessage().getContentAsString())
                .contains("not active")
                .contains(mcpToolName);
        assertThat(abilityManager.mcpToolExecutionCount()).isZero();

        agent.activateToolGroup("mcp_tools", session);
        assertThat(visibleToolNames(agent, session)).contains(mcpToolName);
        List<AbilityManager.ExecutionResult> allowedResults =
                executeMcpTool(agent, session, "reactivated-mcp-call", mcpToolName);
        assertThat(allowedResults.get(0).toolMessage().getContentAsString()).contains("resolved");
        assertThat(abilityManager.mcpToolExecutionCount()).isEqualTo(1);
    }

    private String newNormalToolName() {
        String name = "normal_tool_" + TOOL_NAME_SEQUENCE.incrementAndGet();
        registeredToolNames.add(name);
        return name;
    }

    private String newMcpToolName() {
        String name = "mcp_weather_forecast_" + TOOL_NAME_SEQUENCE.incrementAndGet();
        registeredToolNames.add(name);
        return name;
    }

    private void registerGlobalTool(DelayedToolGroupAgent agent, String name) {
        LocalFunction tool =
                new LocalFunction(
                        ToolCard.builder()
                                .id(name)
                                .name(name)
                                .description(name)
                                .inputParams(
                                        Map.of(
                                                "type", "object",
                                                "properties", Map.of(),
                                                "required", List.of()))
                                .build(),
                        inputs -> Map.of());
        agent.getAbilityManager().addAbility(tool.getCard(), tool);
    }

    private static List<String> visibleToolNames(BaseAgent agent, AgentSessionApi session) {
        return agent.listEffectiveToolInfo(session).stream()
                .map(ToolInfo::getName)
                .toList();
    }

    private static List<AbilityManager.ExecutionResult> executeMcpTool(
            BaseAgent agent, AgentSessionApi session, String callId, String toolName) {
        ToolCall call = ToolCall.builder().id(callId).name(toolName).arguments("{}").build();
        return agent.getAbilityManager().execute(null, call, session, false);
    }

    private static void awaitMcpToolVisible(
            BaseAgent agent, AgentSessionApi session, String toolName) {
        long deadlineNanos = System.nanoTime() + MCP_RESOLUTION_TIMEOUT.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (visibleToolNames(agent, session).contains(toolName)) {
                return;
            }
            sleepBeforeNextPoll();
        }
        fail("MCP tool was not resolved within " + MCP_RESOLUTION_TIMEOUT);
    }

    private static void sleepBeforeNextPoll() {
        try {
            TimeUnit.MILLISECONDS.sleep(MCP_POLL_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            throw new IllegalStateException(
                    "Interrupted while waiting for MCP tool resolution", exception);
        }
    }

    private static final class DelayedToolGroupAgent extends BaseAgent {
        private DelayedToolGroupAgent() {
            super(new com.openjiuwen.core.singleagent.schema.AgentCard("e2e", "e2e", "test"));
        }

        @Override
        public BaseAgent configure(Object config) {
            setConfig(config);
            return this;
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Iterator<Object> stream(
                Object inputs,
                AgentSessionApi session,
                List<com.openjiuwen.core.session.stream.StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class DelayedMcpAbilityManager extends AbilityManager {
        private final String toolName;
        private final CountDownLatch resolutionCompleted = new CountDownLatch(1);
        private final AtomicBoolean resolved = new AtomicBoolean();
        private final AtomicInteger toolExecutionCount = new AtomicInteger();

        private DelayedMcpAbilityManager(String toolName) {
            this.toolName = toolName;
        }

        void resolveAfterDelay(long delayMillis) {
            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS)
                    .execute(this::resolveNow);
        }

        void awaitResolution() throws InterruptedException {
            if (!resolutionCompleted.await(MCP_RESOLUTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                fail("MCP tool resolution did not complete within " + MCP_RESOLUTION_TIMEOUT);
            }
        }

        int mcpToolExecutionCount() {
            return toolExecutionCount.get();
        }

        private void resolveNow() {
            if (!resolved.compareAndSet(false, true)) {
                return;
            }
            ToolCard toolCard = ToolCard.builder()
                    .id(toolName)
                    .name(toolName)
                    .description("Resolved weather forecast")
                    .inputParams(
                            Map.of(
                                    "type", "object",
                                    "properties", Map.of(),
                                    "required", List.of()))
                    .build();
            LocalFunction tool = new LocalFunction(toolCard, inputs -> {
                toolExecutionCount.incrementAndGet();
                return Map.of("result", "resolved");
            });
            addAbility(tool.getCard(), tool);
            resolutionCompleted.countDown();
        }

        @Override
        protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
            if (!resolved.get() || getTools().containsKey(toolName)) {
                return List.of();
            }
            return List.of(
                    ToolInfo.builder()
                            .name(toolName)
                            .description("Resolved weather forecast")
                            .parameters(Map.of())
                            .build());
        }
    }

    private static final class MemorySession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private MemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
