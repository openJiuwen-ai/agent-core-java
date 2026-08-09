/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
        assertEquals(List.of(tool, workflow, agent, mcpServer), manager.list());

        ToolCard refreshed = tool("tool-1", "search");
        assertEquals("refreshed_tool", manager.add(refreshed).getReason());
        assertSame(refreshed, manager.get("search").orElseThrow());
        assertEquals("duplicate_tool", manager.add(tool("tool-2", "search")).getReason());
        assertEquals("duplicate_workflow",
                manager.add(new WorkflowCard("workflow-2", "plan", "other", "1", Map.of())).getReason());
        assertEquals("duplicate_agent", manager.add(new AgentCard("agent-2", "delegate", "other")).getReason());
        assertEquals("duplicate_mcp_server",
                manager.add(new McpServerConfig("mcp-2", "weather", "/other", "sse", Map.of(), Map.of(),
                        Map.of())).getReason());

        assertEquals(List.of(refreshed, workflow, agent, mcpServer), manager.list());
        assertSame(refreshed, manager.get("search").orElseThrow());
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
    void listToolInfoFiltersMcpServersByName() {
        TestableAbilityManager manager = new TestableAbilityManager(List.of(
                ToolInfo.builder().name("forecast").description("Forecast").parameters(Map.of()).build()
        ));
        manager.add(new McpServerConfig("mcp-1", "weather", "/mcp", "sse", Map.of(), Map.of(), Map.of()));
        manager.add(new McpServerConfig("mcp-2", "browser", "/mcp", "sse", Map.of(), Map.of(), Map.of()));

        List<String> allNames = manager.listToolInfo().stream().map(ToolInfo::getName).toList();
        List<String> weatherNames = manager.listToolInfo(null, "weather").stream().map(ToolInfo::getName).toList();

        assertEquals(List.of("mcp_weather_forecast", "mcp_browser_forecast"), allNames);
        assertEquals(List.of("mcp_weather_forecast"), weatherNames);
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
    void addAbilityQualifiesStatefulToolAndTeardownRemovesOnlyOwnedInstance() {
        AbilityManager manager = new AbilityManager("agent-owner");
        ToolCard card = ToolCard.builder()
                .id("echo")
                .name("echo")
                .description("echo")
                .inputParams(Map.of("type", "object"))
                .stateless(false)
                .build();
        LocalFunction tool = new LocalFunction(card, inputs -> inputs);
        try {
            AddAbilityResult result = manager.addAbility(card, tool);

            assertEquals("added_tool", result.getReason());
            assertEquals("echo_agent-owner", card.getId());
            assertSame(tool, Runner.resourceMgr().getTool("echo_agent-owner"));

            manager.teardownTools();

            assertTrue(manager.get("echo").isEmpty());
            assertEquals(null, Runner.resourceMgr().getTool("echo_agent-owner"));
        } finally {
            Runner.resourceMgr().removeTool("echo_agent-owner", null, TagMatchStrategy.ALL, true);
            Runner.resourceMgr().removeTool("echo", null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void addAbilityKeepsStatelessToolIdAndDoesNotTeardownSharedInstance() {
        AbilityManager manager = new AbilityManager("agent-owner");
        ToolCard card = ToolCard.builder()
                .id("shared-echo")
                .name("shared-echo")
                .description("shared")
                .inputParams(Map.of("type", "object"))
                .stateless(true)
                .build();
        LocalFunction tool = new LocalFunction(card, inputs -> "ok");
        try {
            manager.addAbility(card, tool);

            assertEquals("shared-echo", card.getId());
            assertSame(tool, Runner.resourceMgr().getTool("shared-echo"));

            manager.teardownTools();

            assertTrue(manager.get("shared-echo").isPresent());
            assertSame(tool, Runner.resourceMgr().getTool("shared-echo"));
        } finally {
            manager.remove("shared-echo");
            Runner.resourceMgr().removeTool("shared-echo", null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void executeFailsWhenRegisteredToolHasNoResourceInstance() {
        AbilityManager manager = new AbilityManager();
        manager.add(tool("ghost-id", "ghost"));
        ToolCall call = ToolCall.builder().id("call-1").name("ghost").arguments("{}").build();

        List<AbilityManager.ExecutionResult> results = manager.execute(call);

        assertEquals(1, results.size());
        assertEquals(null, results.get(0).result());
        assertTrue(String.valueOf(results.get(0).toolMessage().getContent())
                .contains("Tool instance not found in resource_mgr: ghost-id"));
    }

    @Test
    void executeFallsBackToResourceManagerByNameWhenAbilityIsUnregistered() {
        AbilityManager manager = new AbilityManager();
        String toolId = "fallback-echo";
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description("fallback")
                        .inputParams(Map.of("type", "object")).build(),
                inputs -> Map.of("echo", inputs.get("text"))
        );
        Runner.resourceMgr().addTool(tool);
        try {
            ToolCall call = ToolCall.builder()
                    .id("call-1")
                    .name(toolId)
                    .arguments("{\"text\":\"hi\"}")
                    .build();

            List<AbilityManager.ExecutionResult> results = manager.execute(call);

            assertEquals(1, results.size());
            assertEquals(Map.of("echo", "hi"), results.get(0).result());
        } finally {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void executeTimesOutWhenToolExceedsCardTimeout() {
        AbilityManager manager = new AbilityManager();
        ToolCard card = ToolCard.builder()
                .id("slow-tool")
                .name("slow-tool")
                .description("slow")
                .inputParams(Map.of("type", "object"))
                .properties(Map.of("resilience", Map.of("timeout_s", 0.05D)))
                .build();
        LocalFunction tool = new LocalFunction(card, inputs -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return "done";
        });
        Runner.resourceMgr().addTool(tool);
        try {
            manager.add(card);
            List<AbilityManager.ExecutionResult> results = manager.execute(
                    ToolCall.builder().id("call-1").name("slow-tool").arguments("{}").build());

            assertEquals(1, results.size());
            assertTrue(String.valueOf(results.get(0).toolMessage().getContent())
                    .contains("timed out after"));
        } finally {
            Runner.resourceMgr().removeTool("slow-tool", null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    void executePreservesToolInterruptExceptionAsResult() {
        AbilityManager manager = new AbilityManager();
        ToolInterruptException interrupt = new ToolInterruptException(new InterruptRequest("need confirm", Map.of(), ""));
        ToolCard card = ToolCard.builder()
                .id("ask-user")
                .name("ask-user")
                .description("ask")
                .inputParams(Map.of("type", "object"))
                .build();
        LocalFunction tool = new LocalFunction(card, inputs -> {
            throw interrupt;
        });
        Runner.resourceMgr().addTool(tool);
        try {
            manager.add(card);
            List<AbilityManager.ExecutionResult> results = manager.execute(
                    ToolCall.builder().id("call-1").name("ask-user").arguments("{}").build());

            assertEquals(1, results.size());
            assertInstanceOf(ToolInterruptException.class, results.get(0).result());
            assertEquals(null, results.get(0).toolMessage());
        } finally {
            Runner.resourceMgr().removeTool("ask-user", null, TagMatchStrategy.ALL, true);
        }
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
        assertEquals("call-1", results.get(0).toolMessage().getToolCallId());
        assertTrue(String.valueOf(results.get(0).toolMessage().getContent())
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
        assertEquals(Map.of("echo", "hello"), results.get(0).result());
        assertEquals("call-1", results.get(0).toolMessage().getToolCallId());
        assertEquals("echoTool", results.get(0).toolMessage().getName());
        assertEquals("{echo=hello}", results.get(0).toolMessage().getContent());
    }

    @Test
    void executeResolvedToolPassesCurrentSessionInKwargs() {
        AbilityManager manager = new AbilityManager();
        AtomicReference<Object> capturedSession = new AtomicReference<>();
        Tool tool = new Tool(ToolCard.builder()
                .id("sessionTool")
                .name("sessionTool")
                .description("session")
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                capturedSession.set(kwargs == null ? null : kwargs.get("session"));
                return "ok";
            }
        };
        AgentSession session = new AgentSession();
        SessionContextHolder.setCurrentSession(session);
        try {
            ToolCall call = ToolCall.builder()
                    .id("call-1")
                    .name("sessionTool")
                    .arguments("{}")
                    .build();
            manager.executeResolvedTool(tool, call);
            assertSame(session, capturedSession.get());
        } finally {
            SessionContextHolder.clearCurrentSession();
        }
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
            assertEquals(Map.of("echo", "hello"), results.get(0).result());
            assertEquals("{echo=hello}", results.get(0).toolMessage().getContent());
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
        assertEquals("call-1", results.get(0).toolMessage().getToolCallId());
        assertTrue(String.valueOf(results.get(0).toolMessage().getContent())
                .contains("Tool execution error: boom"));
    }

    @Test
    void executeResolvedToolLogsScalarResultOnceWithoutChangingExecutionResult() {
        AtomicInteger invocations = new AtomicInteger();
        Double expected = 30.0D;
        Tool tool = resultTool("sum", expected, invocations);
        ToolCall call = ToolCall.builder().id("call-sum").name("sum").arguments("{}").build();
        RecordingHandler handler = new RecordingHandler();
        int originalLevel = originalToolLogLevel();
        Loggers.TOOL.setLevel(LogLevels.DEBUG);
        Loggers.TOOL.addHandler(handler);
        try {
            List<AbilityManager.ExecutionResult> results = new AbilityManager().executeResolvedTool(tool, call);

            assertEquals(1, invocations.get());
            assertSame(expected, results.get(0).result());
            assertEquals("30.0", results.get(0).toolMessage().getContent());
            assertEquals(List.of("event=react_tool_result tool_name=sum status=success result_type=Double"),
                    handler.messages);
            assertEquals(Level.FINE, handler.records.get(0).getLevel());
            assertFalse(handler.messages.get(0).contains("30.0"));
        } finally {
            Loggers.TOOL.removeHandler(handler);
            Loggers.TOOL.setLevel(originalLevel);
        }
    }

    @Test
    void executeResolvedToolLogsRestEnvelopeWithoutChangingReturnedEnvelope() {
        AtomicInteger invocations = new AtomicInteger();
        Map<String, Object> weather = new LinkedHashMap<>();
        weather.put("location", "杭州");
        weather.put("temperature", "18℃ - 26℃");
        weather.put("condition", "晴");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        envelope.put("data", weather);
        envelope.put("message", "success");
        Tool tool = resultTool("weather", envelope, invocations);
        ToolCall call = ToolCall.builder().id("call-weather").name("weather").arguments("{}").build();
        RecordingHandler handler = new RecordingHandler();
        int originalLevel = originalToolLogLevel();
        Loggers.TOOL.setLevel(LogLevels.DEBUG);
        Loggers.TOOL.addHandler(handler);
        try {
            List<AbilityManager.ExecutionResult> results = new AbilityManager().executeResolvedTool(tool, call);

            assertEquals(1, invocations.get());
            assertSame(envelope, results.get(0).result());
            String content = AbilityManager.buildToolMessageContent(envelope);
            assertEquals(content, results.get(0).toolMessage().getContent());
            assertEquals(List.of("event=react_tool_result tool_name=weather status=success result_type=LinkedHashMap"),
                    handler.messages);
            assertEquals(Level.FINE, handler.records.get(0).getLevel());
            assertFalse(handler.messages.get(0).contains(content));
            assertFalse(handler.messages.get(0).contains("temperature"));
        } finally {
            Loggers.TOOL.removeHandler(handler);
            Loggers.TOOL.setLevel(originalLevel);
        }
    }

    @Test
    void executeResolvedToolOmitsResultTypeWhenResultIsNull() {
        AtomicInteger invocations = new AtomicInteger();
        Tool tool = resultTool("nullable", null, invocations);
        ToolCall call = ToolCall.builder().id("call-nullable").name("nullable").arguments("{}").build();
        RecordingHandler handler = new RecordingHandler();
        int originalLevel = originalToolLogLevel();
        Loggers.TOOL.setLevel(LogLevels.DEBUG);
        Loggers.TOOL.addHandler(handler);
        try {
            List<AbilityManager.ExecutionResult> results = new AbilityManager().executeResolvedTool(tool, call);

            assertEquals(1, invocations.get());
            assertEquals(1, results.size());
            assertEquals(List.of("event=react_tool_result tool_name=nullable status=success"), handler.messages);
            assertFalse(handler.messages.get(0).contains("result_type=null"));
        } finally {
            Loggers.TOOL.removeHandler(handler);
            Loggers.TOOL.setLevel(originalLevel);
        }
    }

    private static int originalToolLogLevel() {
        Integer effectiveLevel = readEffectiveLogLevel(Loggers.TOOL);
        if (effectiveLevel != null) {
            return effectiveLevel;
        }
        return LogLevels.normalizeLogLevel(Loggers.TOOL.getConfig().get("level"), LogLevels.INFO);
    }

    private static Integer readEffectiveLogLevel(Object logger) {
        Object current = logger;
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int depth = 0; current != null && seen.add(current) && depth < 8; depth++) {
            Integer level = readThresholdLevel(current);
            if (level != null) {
                return level;
            }
            Object next = unwrapLoggerDelegate(current);
            if (next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    private static Integer readThresholdLevel(Object logger) {
        try {
            java.lang.reflect.Field field = findField(logger.getClass(), "thresholdLevel");
            field.setAccessible(true);
            Object value = field.get(logger);
            return value instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object unwrapLoggerDelegate(Object logger) {
        Object methodDelegate = invokeDelegateGetter(logger);
        if (methodDelegate != null) {
            return methodDelegate;
        }
        try {
            java.lang.reflect.Field field = findField(logger.getClass(), "delegate");
            field.setAccessible(true);
            Object value = field.get(logger);
            return value == null ? logger : value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return logger;
        }
    }

    private static Object invokeDelegateGetter(Object logger) {
        try {
            java.lang.reflect.Method method = logger.getClass().getDeclaredMethod("getDelegate");
            method.setAccessible(true);
            return method.invoke(logger);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    void originalToolLogLevelReadsCurrentEffectiveLevel() {
        int originalLevel = originalToolLogLevel();
        try {
            Loggers.TOOL.setLevel(LogLevels.CRITICAL);
            assertEquals(LogLevels.CRITICAL, originalToolLogLevel());

            Loggers.TOOL.setLevel(LogLevels.NOTSET);
            assertEquals(LogLevels.NOTSET, originalToolLogLevel());
        } finally {
            Loggers.TOOL.setLevel(originalLevel);
        }
    }

    @Test
    void readEffectiveLogLevelUnwrapsInheritedDelegateField() {
        assertEquals(LogLevels.NOTSET, readEffectiveLogLevel(new ChildLoggerWrapper(new ThresholdLogger())));
    }

    @Test
    void buildToolMessageContentMirrorsPythonDataAndErrorRules() {
        Map<String, Object> contentData = new LinkedHashMap<>();
        contentData.put("content", null);
        assertEquals("", AbilityManager.buildToolMessageContent(Map.of("data", contentData)));
        assertEquals("boom", AbilityManager.buildToolMessageContent(Map.of("success", false, "error", "boom")));
        // Java-idiomatic Map.toString; functional short-circuits above mirror Python.
        assertEquals("{value=42}", AbilityManager.buildToolMessageContent(Map.of("value", 42)));
    }

    @Test
    void buildToolMessageContentSerializesPojoAndMapAsJson() {
        ReadFileResult readFileResult = new ReadFileResult();
        readFileResult.setCode(200);
        readFileResult.setMessage("ok");
        ReadFileData fileData = ReadFileData.builder()
                .path("/tmp/x")
                .content("hello")
                .mode("r")
                .build();
        readFileResult.setData(fileData);

        String pojoContent = AbilityManager.buildToolMessageContent(readFileResult);
        assertTrue(pojoContent.contains("ReadFileResult"), "POJO 无 dict content 时用 String.valueOf：" + pojoContent);

        String mapContent = AbilityManager.buildToolMessageContent(Map.of("skill", "x", "echo", "y"));
        assertTrue(mapContent.contains("skill=x") || mapContent.contains("echo=y"),
                "Map 结果应为 Java Map.toString：" + mapContent);

        assertEquals("plain text", AbilityManager.buildToolMessageContent("plain text"));
    }

    private static ToolCard tool(String id, String name) {
        return new ToolCard(id, name, name + " description", Map.of("type", "object"));
    }

    private static Tool resultTool(String name, Object result, AtomicInteger invocations) {
        return new Tool(ToolCard.builder()
                .id(name)
                .name(name)
                .description(name)
                .inputParams(Map.of("type", "object"))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                invocations.incrementAndGet();
                return result;
            }
        };
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

    private static class ParentLoggerWrapper {
        @SuppressWarnings("unused")
        private final Object delegate;

        private ParentLoggerWrapper(Object delegate) {
            this.delegate = delegate;
        }
    }

    @Test
    void concurrentAddOfDistinctToolsDoesNotLoseRegistrations() throws Exception {
        AbilityManager manager = new AbilityManager("concurrent-owner");
        int threadCount = 16;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<AddAbilityResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    ToolCard card = ToolCard.builder()
                            .id("race-tool-" + index)
                            .name("race-tool-" + index)
                            .description("concurrent")
                            .inputParams(Map.of("type", "object"))
                            .stateless(true)
                            .build();
                    return manager.add(card);
                }));
            }
            start.countDown();
            int added = 0;
            for (Future<AddAbilityResult> future : futures) {
                AddAbilityResult result = future.get(10, TimeUnit.SECONDS);
                assertTrue(result.isAdded());
                added++;
            }
            assertEquals(threadCount, added);
            for (int i = 0; i < threadCount; i++) {
                assertTrue(manager.get("race-tool-" + i).isPresent());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void abilityRegistriesAreSynchronizedToProtectConcurrentRegistration() throws Exception {
        AbilityManager manager = new AbilityManager();
        java.lang.reflect.Field tools = AbilityManager.class.getDeclaredField("tools");
        tools.setAccessible(true);
        assertTrue(tools.get(manager).getClass().getName().contains("Synchronized"));
        java.lang.reflect.Field workflows = AbilityManager.class.getDeclaredField("workflows");
        workflows.setAccessible(true);
        assertTrue(workflows.get(manager).getClass().getName().contains("Synchronized"));
    }

    private static final class ChildLoggerWrapper extends ParentLoggerWrapper {
        private ChildLoggerWrapper(Object delegate) {
            super(delegate);
        }
    }

    private static final class ThresholdLogger {
        @SuppressWarnings("unused")
        private final int thresholdLevel = LogLevels.NOTSET;
    }

    private static final class RecordingHandler extends Handler {
        private final List<String> messages = new java.util.ArrayList<>();
        private final List<LogRecord> records = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
