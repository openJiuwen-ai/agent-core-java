/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for single-agent ability registration and tool metadata behavior.
 *
 * <p>Mirrors Python's {@code AbilityManager}, {@code AddAbilityResult}, and
 * {@code AbilityExecutionError} behavior in
 * {@code openjiuwen/core/single_agent/ability_manager.py}.</p>
 */
class AbilityManagerTest {

    @Test
    void addKeepsSeparateRegistriesAndReportsDuplicateReasons() {
        AbilityManager manager = new AbilityManager();
        ToolCard tool = tool("tool-1", "search");
        WorkflowCard workflow = new WorkflowCard("workflow-1", "plan", "planner", "1", Map.of());
        AgentCard agent = new AgentCard("agent-1", "delegate", "delegate agent");
        McpServerConfig mcpServer = new McpServerConfig("mcp-1", "weather", "/mcp", "sse", Map.of(), Map.of(),
                Map.of());

        assertEquals("added_tool", manager.add(tool).getReason());
        assertEquals("added_workflow", manager.add(workflow).getReason());
        assertEquals("added_agent", manager.add(agent).getReason());
        assertEquals("added_mcp_server", manager.add(mcpServer).getReason());

        assertEquals("duplicate_tool", manager.add(tool("tool-2", "search")).getReason());
        assertEquals("duplicate_workflow",
                manager.add(new WorkflowCard("workflow-2", "plan", "other", "1", Map.of())).getReason());
        assertEquals("duplicate_agent", manager.add(new AgentCard("agent-2", "delegate", "other")).getReason());
        assertEquals("duplicate_mcp_server",
                manager.add(new McpServerConfig("mcp-2", "weather", "/other", "sse", Map.of(), Map.of(),
                        Map.of())).getReason());

        assertEquals(List.of(tool, workflow, agent, mcpServer), manager.list());
        assertSame(tool, manager.get("search").orElseThrow());
        assertEquals(List.of("search", "plan", "delegate", "weather"),
                manager.getAbilities().keySet().stream().toList());
    }

    @Test
    void addCollectionReturnsOneResultPerAbility() {
        AbilityManager manager = new AbilityManager();

        List<AddAbilityResult> results = manager.add(List.of(tool("tool-1", "alpha"), tool("tool-2", "beta")));

        assertEquals(2, results.size());
        assertEquals(List.of("added_tool", "added_tool"), results.stream().map(AddAbilityResult::getReason).toList());
    }

    @Test
    void removeMcpServerAlsoRemovesGeneratedMcpTools() {
        TestableAbilityManager manager = new TestableAbilityManager(List.of(
                ToolInfo.builder().name("forecast").description("forecast").parameters(Map.of()).build()
        ));
        McpServerConfig server = new McpServerConfig("server-1", "weather", "/mcp", "sse", Map.of(), Map.of(),
                Map.of());
        manager.add(server);
        manager.listToolInfo();

        assertTrue(manager.get("mcp_weather_forecast").isPresent());

        Object removed = manager.remove("weather");

        assertSame(server, removed);
        assertFalse(manager.get("weather").isPresent());
        assertFalse(manager.get("mcp_weather_forecast").isPresent());
    }

    @Test
    void reorderToolsOnlyReordersToolRegistry() {
        AbilityManager manager = new AbilityManager();
        ToolCard free = tool("tool-free", "free_search");
        ToolCard paid = tool("tool-paid", "paid_search");
        WorkflowCard workflow = new WorkflowCard("workflow-1", "plan", "planner", "1", Map.of());
        manager.add(free);
        manager.add(paid);
        manager.add(workflow);

        manager.reorderTools(List.of("paid_search", "free_search"));

        assertEquals(List.of(paid, free, workflow), manager.list());
        assertEquals(List.of("paid_search", "free_search", "plan"),
                manager.listToolInfo().stream().map(ToolInfo::getName).toList());
    }

    @Test
    void listToolInfoConvertsCardsAndPrefixesMcpTools() {
        Map<String, Object> agentParams = new LinkedHashMap<>();
        agentParams.put("type", "object");
        agentParams.put("properties", Map.of("question", Map.of("type", "string")));
        TestableAbilityManager manager = new TestableAbilityManager(List.of(
                ToolInfo.builder().name("forecast").description("Forecast weather").parameters(Map.of()).build()
        ));
        manager.add(tool("tool-1", "free_search"));
        manager.add(tool("tool-2", "paid_search"));
        manager.add(new WorkflowCard("workflow-1", "plan", "Planner", "1", Map.of("type", "object")));
        AgentCard agent = new AgentCard("agent-1", "delegate", "Delegate");
        agent.setInputParams(agentParams);
        manager.add(agent);
        manager.add(new McpServerConfig("mcp-1", "weather", "/mcp", "sse", Map.of(), Map.of(), Map.of()));

        List<ToolInfo> infos = manager.listToolInfo();

        assertEquals(List.of("paid_search", "free_search", "plan", "delegate", "mcp_weather_forecast"),
                infos.stream().map(ToolInfo::getName).toList());
        assertEquals("mcp-1.weather.forecast", manager.getTools().get("mcp_weather_forecast").getId());
    }

    @Test
    void parseToolArgumentsRepairsBalancedSuffixAndRaisesOnInvalidJson() {
        Object repaired = AbilityManager.parseToolArguments("{\"query\":[1,2");
        assertInstanceOf(Map.class, repaired);
        assertEquals(List.of(1, 2), ((Map<?, ?>) repaired).get("query"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AbilityManager.parseToolArguments("{\"query\": bare}")
        );
        assertTrue(error.getMessage().contains("Invalid tool arguments JSON:"));
        assertTrue(error.getMessage().contains("Raw arguments: '{\"query\": bare}'"));
    }

    @Test
    void executeReturnsToolMessageForMalformedArguments() {
        AbilityManager manager = new AbilityManager();
        ToolCall call = ToolCall.builder()
                .id("call-1")
                .name("missing")
                .arguments("{\"query\": bare}")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.execute(call);

        assertEquals(1, results.size());
        assertEquals("call-1", results.getFirst().toolMessage().getToolCallId());
        assertTrue(String.valueOf(results.getFirst().toolMessage().getContent())
                .contains("Invalid tool arguments JSON:"));
    }

    @Test
    void executeResolvedToolInvokesConcreteToolInstance() {
        AbilityManager manager = new AbilityManager();
        EchoTool tool = new EchoTool();
        ToolCall call = ToolCall.builder()
                .id("call-1")
                .name("echoTool")
                .arguments("{\"text\":\"hello\"}")
                .build();

        List<AbilityManager.ExecutionResult> results = manager.executeResolvedTool(tool, call);

        assertEquals(1, results.size());
        assertEquals("hello", tool.invokedText);
        assertEquals(Map.of("echo", "hello"), results.getFirst().result());
        assertEquals("call-1", results.getFirst().toolMessage().getToolCallId());
        assertEquals("echoTool", results.getFirst().toolMessage().getName());
        assertEquals("{echo=hello}", results.getFirst().toolMessage().getContent());
    }

    @Test
    void executeInvokesRunnerToolResolvedFromRegisteredToolCard() {
        AbilityManager manager = new AbilityManager();
        EchoTool tool = new EchoTool();
        Runner.resourceMgr().removeTool(tool.getCard().getId());
        Runner.resourceMgr().addTool(tool);
        try {
            manager.add(tool.getCard());
            ToolCall call = ToolCall.builder()
                    .id("call-1")
                    .name("echoTool")
                    .arguments("{\"text\":\"hello\"}")
                    .build();

            List<AbilityManager.ExecutionResult> results = manager.execute(call);

            assertEquals(1, results.size());
            assertEquals("hello", tool.invokedText);
            assertEquals(Map.of("echo", "hello"), results.getFirst().result());
            assertEquals("{echo=hello}", results.getFirst().toolMessage().getContent());
        } finally {
            Runner.resourceMgr().removeTool(tool.getCard().getId());
        }
    }

    @Test
    void executeResolvedToolReturnsToolMessageForInvocationError() {
        AbilityManager manager = new AbilityManager();
        Tool explodingTool = new Tool(ToolCard.builder()
                .id("explode")
                .name("explode")
                .description("explode")
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                throw new IllegalStateException("boom");
            }
        };
        ToolCall call = ToolCall.builder().id("call-1").name("explode").arguments("{}").build();

        List<AbilityManager.ExecutionResult> results = manager.executeResolvedTool(explodingTool, call);

        assertEquals(1, results.size());
        assertEquals("call-1", results.getFirst().toolMessage().getToolCallId());
        assertTrue(String.valueOf(results.getFirst().toolMessage().getContent())
                .contains("Ability execution error: boom"));
    }

    @Test
    void buildToolMessageContentMirrorsPythonDataAndErrorRules() {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("content", null);
        assertEquals("", AbilityManager.buildToolMessageContent(Map.of("data", contentData)));
        assertEquals("boom", AbilityManager.buildToolMessageContent(Map.of("success", false, "error", "boom")));
        assertEquals("{value=42}", AbilityManager.buildToolMessageContent(Map.of("value", 42)));
    }

    private static ToolCard tool(String id, String name) {
        return new ToolCard(id, name, name + " description", Map.of("type", "object"));
    }

    private static final class EchoTool extends Tool {
        private String invokedText;

        private EchoTool() {
            super(ToolCard.builder()
                    .id("echoTool")
                    .name("echoTool")
                    .description("echo")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokedText = String.valueOf(inputs.get("text"));
            return Map.of("echo", invokedText);
        }
    }

    private static final class TestableAbilityManager extends AbilityManager {
        private final List<ToolInfo> mcpToolInfos;

        private TestableAbilityManager(List<ToolInfo> mcpToolInfos) {
            this.mcpToolInfos = mcpToolInfos;
        }

        @Override
        protected List<ToolInfo> loadMcpToolInfos(McpServerConfig mcpServer) {
            return mcpToolInfos;
        }
    }
}
