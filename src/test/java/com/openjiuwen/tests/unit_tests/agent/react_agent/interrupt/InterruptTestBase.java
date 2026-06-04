/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_base.py} in {@code tests.unit_tests.agent.react_agent.interrupt}.
 * 
 * Base test infrastructure for interrupt tests including test tools and helper methods.
 */
@Tag("unit-test")
class InterruptTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    // -----------------------------------------------------------------------
    // NestedAgentConfig
    // -----------------------------------------------------------------------
    static class NestedAgentConfig {
        String agentId;
        String agentName;
        String systemPrompt;
        List<Tool> tools = new ArrayList<>();
        List<AgentCard> subAgentCards = new ArrayList<>();
        List<String> railToolNames = new ArrayList<>();

        NestedAgentConfig(String agentId, String agentName, String systemPrompt) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.systemPrompt = systemPrompt;
        }
    }

    // -----------------------------------------------------------------------
    // AgentWithToolsConfig
    // -----------------------------------------------------------------------
    static class AgentWithToolsConfig {
        List<Tool> tools;
        String sessionIdPrefix = "test";
        String systemPrompt = "You are an assistant.";
        List<String> railToolNames = new ArrayList<>();
        List<String> traceToolNames = new ArrayList<>();

        AgentWithToolsConfig(List<Tool> tools) {
            this.tools = tools;
        }
    }

    static class AgentWithToolsFixture {
        final ReActAgent agent;
        final AgentSessionApi session;
        final TraceRail traceRail;

        AgentWithToolsFixture(ReActAgent agent, AgentSessionApi session, TraceRail traceRail) {
            this.agent = agent;
            this.session = session;
            this.traceRail = traceRail;
        }
    }

    static class SimpleAgentFixture {
        final ReActAgent agent;
        final AgentSessionApi session;
        final ReadTool readTool;
        final WriteTool writeTool;
        final TraceRail traceRail;

        SimpleAgentFixture(ReActAgent agent, AgentSessionApi session, ReadTool readTool, WriteTool writeTool,
                           TraceRail traceRail) {
            this.agent = agent;
            this.session = session;
            this.readTool = readTool;
            this.writeTool = writeTool;
            this.traceRail = traceRail;
        }
    }

    // -----------------------------------------------------------------------
    // ReadTool - Generic read tool for testing
    // -----------------------------------------------------------------------
    static class ReadTool extends Tool {
        private int invokeCount = 0;

        public ReadTool() {
            super(ToolCard.builder()
                .id("read")
                .name("read")
                .description("Read file content")
                .inputParams(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filepath", Map.of("description", "File path", "type", "string")
                    ),
                    "required", List.of("filepath")
                ))
                .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            invokeCount++;
            String filepath = String.valueOf(inputs.getOrDefault("filepath", ""));
            return Map.of(
                "success", true,
                "content", "Content of file " + filepath,
                "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            List<Object> result = new ArrayList<>();
            result.add(invoke(inputs, kwargs));
            return result.iterator();
        }

        public int getInvokeCount() {
            return invokeCount;
        }
    }

    // -----------------------------------------------------------------------
    // WriteTool - Generic write tool for testing
    // -----------------------------------------------------------------------
    static class WriteTool extends Tool {
        private int invokeCount = 0;

        public WriteTool() {
            super(ToolCard.builder()
                .id("write")
                .name("write")
                .description("Write file content")
                .inputParams(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "filepath", Map.of("description", "File path", "type", "string"),
                        "content", Map.of("description", "Content", "type", "string")
                    ),
                    "required", List.of("filepath", "content")
                ))
                .build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            invokeCount++;
            String filepath = String.valueOf(inputs.getOrDefault("filepath", ""));
            return Map.of(
                "success", true,
                "message", "Written to " + filepath,
                "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            List<Object> result = new ArrayList<>();
            result.add(invoke(inputs, kwargs));
            return result.iterator();
        }

        public int getInvokeCount() {
            return invokeCount;
        }
    }

    // -----------------------------------------------------------------------
    // ActionTool - Generic action tool for testing
    // -----------------------------------------------------------------------
    static class ActionTool extends Tool {
        private final String toolName;
        private int invokeCount = 0;

        public ActionTool() {
            this("action");
        }

        public ActionTool(String name) {
            super(ToolCard.builder()
                .id(name)
                .name(name)
                .description("Execute " + name + " operation")
                .inputParams(Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "action", Map.of("description", "Operation", "type", "string")
                    ),
                    "required", List.of("action")
                ))
                .build());
            this.toolName = name;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            invokeCount++;
            String action = String.valueOf(inputs.getOrDefault("action", ""));
            return Map.of(
                "success", true,
                "data", "Execute " + toolName + ": " + action,
                "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
            List<Object> result = new ArrayList<>();
            result.add(invoke(inputs, kwargs));
            return result.iterator();
        }

        public int getInvokeCount() {
            return invokeCount;
        }
    }

    // -----------------------------------------------------------------------
    // TraceRail - Rail for verifying tool call flows
    // -----------------------------------------------------------------------
    static class TraceRail extends AgentRail {
        private final List<String> toolNames;
        private final Map<String, Integer> toolInvokeCount = new HashMap<>();

        public TraceRail(List<String> toolNames) {
            this.toolNames = toolNames;
        }

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            Object inputsObj = ctx.getInputs();
            String toolName = "";
            Object toolResult = null;
            
            if (inputsObj instanceof ToolCallInputs inputs) {
                toolName = inputs.getToolName() != null ? inputs.getToolName() : "";
                toolResult = inputs.getToolResult();
            }
            
            toolInvokeCount.putIfAbsent(toolName, 0);
            if (toolResult != null) {
                toolInvokeCount.put(toolName, toolInvokeCount.get(toolName) + 1);
            }
        }

        public int getExecutionCount(String toolName) {
            return toolInvokeCount.getOrDefault(toolName, 0);
        }
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    /**
     * Create agent with tools.
     */
    protected ReActAgent createAgentWithTools(AgentWithToolsConfig config) throws Exception {
        return createAgentWithToolsFixture(config).agent;
    }

    /**
     * Create agent with tools and return the test session/trace rail, mirroring Python's tuple helper.
     */
    protected AgentWithToolsFixture createAgentWithToolsFixture(AgentWithToolsConfig config) throws Exception {
        AgentCard card = AgentCard.builder()
            .id(config.sessionIdPrefix + "_agent")
            .name(config.sessionIdPrefix + "_agent")
            .build();
        ReActAgent agent = new ReActAgent(card);
        
        ReActAgentConfig agentConfig = ReActAgentConfig.builder()
            .modelProvider("OpenAI")
            .apiKey("sk-fake")
            .apiBase("https://api.openai.com/v1")
            .modelName("gpt-3.5-turbo")
            .promptTemplate(List.of(
                Map.of("role", "system", "content", config.systemPrompt)
            ))
            .build();
        agent.configure(agentConfig);

        for (Tool tool : config.tools) {
            Runner.resourceMgr().addTool(tool, null);
            agent.getAbilityManager().add(tool.getCard());
        }

        TraceRail traceRail = null;
        if (config.traceToolNames != null && !config.traceToolNames.isEmpty()) {
            traceRail = new TraceRail(config.traceToolNames);
            agent.registerRail(traceRail);
        }

        if (config.railToolNames != null && !config.railToolNames.isEmpty()) {
            ConfirmInterruptRail rail = new ConfirmInterruptRail(config.railToolNames);
            agent.registerRail(rail);
        }

        AgentSessionApi session = AgentSessionApi.create(config.sessionIdPrefix + "_test", null, card);
        return new AgentWithToolsFixture(agent, session, traceRail);
    }

    /**
     * Create simple agent with read tool and optional write tool.
     */
    protected SimpleAgentFixture createSimpleAgent() throws Exception {
        return createSimpleAgent("test",
            "You are an assistant. When the user requests to execute operations, call the read tool.",
            List.of("read"),
            false);
    }

    /**
     * Create simple agent with read tool and optional write tool.
     */
    protected SimpleAgentFixture createSimpleAgent(String sessionIdPrefix, String systemPrompt,
                                                  List<String> railToolNames, boolean withWriteTool) throws Exception {
        ReadTool readTool = new ReadTool();
        WriteTool writeTool = null;
        List<Tool> tools = new ArrayList<>();
        tools.add(readTool);
        if (withWriteTool) {
            writeTool = new WriteTool();
            tools.add(writeTool);
        }

        AgentWithToolsConfig config = new AgentWithToolsConfig(tools);
        config.sessionIdPrefix = sessionIdPrefix;
        config.systemPrompt = systemPrompt;
        config.railToolNames = railToolNames != null ? railToolNames : List.of("read");
        config.traceToolNames = tools.stream().map(tool -> tool.getCard().getName()).toList();

        AgentWithToolsFixture fixture = createAgentWithToolsFixture(config);
        return new SimpleAgentFixture(fixture.agent, fixture.session, readTool, writeTool, fixture.traceRail);
    }

    /**
     * Create nested agent with tools and sub-agents.
     */
    protected ReActAgent createNestedAgent(NestedAgentConfig config) throws Exception {
        AgentCard card = AgentCard.builder()
            .id(config.agentId)
            .name(config.agentName)
            .build();
        ReActAgent agent = new ReActAgent(card);
        
        ReActAgentConfig agentConfig = ReActAgentConfig.builder()
            .modelProvider("OpenAI")
            .apiKey("sk-fake")
            .apiBase("https://api.openai.com/v1")
            .modelName("gpt-3.5-turbo")
            .promptTemplate(List.of(
                Map.of("role", "system", "content", config.systemPrompt)
            ))
            .build();
        agent.configure(agentConfig);

        if (config.tools != null) {
            for (Tool tool : config.tools) {
                Runner.resourceMgr().addTool(tool, null);
                agent.getAbilityManager().add(tool.getCard());
            }
        }

        if (config.subAgentCards != null) {
            for (AgentCard subCard : config.subAgentCards) {
                agent.getAbilityManager().add(subCard);
            }
        }

        if (config.railToolNames != null && !config.railToolNames.isEmpty()) {
            ConfirmInterruptRail rail = new ConfirmInterruptRail(config.railToolNames);
            agent.registerRail(rail);
        }

        return agent;
    }

    /**
     * Assert interrupt result.
     */
    protected List<String> assertInterruptResult(Map<String, Object> result) {
        return assertInterruptResult(result, 1);
    }

    /**
     * Assert interrupt result with expected count.
     */
    @SuppressWarnings("unchecked")
    protected List<String> assertInterruptResult(Map<String, Object> result, int expectedCount) {
        assertNotNull(result);
        assertEquals("interrupt", result.get("result_type"));
        List<String> interruptIds = (List<String>) result.get("interrupt_ids");
        List<Object> stateList = (List<Object>) result.get("state");
        assertNotNull(interruptIds);
        assertNotNull(stateList);
        assertEquals(expectedCount, interruptIds.size(), 
            "Expected " + expectedCount + " interrupts, actual " + interruptIds.size());
        assertEquals(expectedCount, stateList.size());
        return interruptIds;
    }

    /**
     * Assert answer result.
     */
    protected void assertAnswerResult(Map<String, Object> result) {
        assertNotNull(result);
        assertEquals("answer", result.get("result_type"));
    }

    /**
     * Create InteractiveInput to confirm an interrupt.
     */
    protected InteractiveInput confirmInterrupt(String toolCallId) {
        return confirmInterrupt(toolCallId, false);
    }

    /**
     * Create InteractiveInput to confirm an interrupt with auto_confirm option.
     */
    protected InteractiveInput confirmInterrupt(String toolCallId, boolean autoConfirm) {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(toolCallId, Map.of(
            "approved", true,
            "feedback", "Confirm",
            "auto_confirm", autoConfirm
        ));
        return interactiveInput;
    }

    /**
     * Create InteractiveInput to reject an interrupt.
     */
    protected InteractiveInput rejectInterrupt(String toolCallId) {
        return rejectInterrupt(toolCallId, "Reject");
    }

    /**
     * Create InteractiveInput to reject an interrupt with feedback.
     */
    protected InteractiveInput rejectInterrupt(String toolCallId, String feedback) {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(toolCallId, Map.of(
            "approved", false,
            "feedback", feedback
        ));
        return interactiveInput;
    }

    @SuppressWarnings("unchecked")
    protected List<String> interruptIds(Map<String, Object> result) {
        Object ids = result.get("interrupt_ids");
        return ids instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of();
    }

    @SuppressWarnings("unchecked")
    protected List<Object> stateList(Map<String, Object> result) {
        Object state = result.get("state");
        return state instanceof List<?> list ? (List<Object>) list : List.of();
    }

    protected String getToolNameFromState(Object stateItem) {
        Object payload = statePayloadValue(stateItem);
        if (payload instanceof Map<?, ?> payloadMap) {
            Object toolName = payloadMap.get("tool_name");
            return toolName != null ? String.valueOf(toolName) : "";
        }
        return "";
    }

    protected String getFilepathFromState(Object stateItem) {
        Object payload = statePayloadValue(stateItem);
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return "";
        }
        Object toolArgs = payloadMap.get("tool_args");
        Map<String, Object> args = normalizeArgs(toolArgs);
        Object filepath = args.get("filepath");
        return filepath != null ? String.valueOf(filepath) : "";
    }

    protected ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
            .id(id)
            .type("function")
            .name(name)
            .arguments(arguments)
            .build();
    }

    protected AssistantFlow newConfirmFlow(ConfirmInterruptRail rail, Tool... tools) {
        return new AssistantFlow(rail, Arrays.asList(tools));
    }

    protected static Map<String, Object> parseArgs(String json) {
        return normalizeArgs(json);
    }

    protected static Map<String, Object> answerResult(String output) {
        return Map.of("result_type", "answer", "output", output);
    }

    protected OutputSchema interactionChunk(String id, Object value, int index) {
        return new OutputSchema(Constant.INTERACTION, index, new InteractionOutput(id, value));
    }

    private static Object statePayloadValue(Object stateItem) {
        if (stateItem instanceof OutputSchema outputSchema) {
            Object payload = outputSchema.getPayload();
            if (payload instanceof InteractionOutput interactionOutput) {
                return interactionOutput.getValue();
            }
            return payload;
        }
        if (stateItem instanceof Map<?, ?> stateMap) {
            Object payload = stateMap.get("payload");
            if (payload instanceof Map<?, ?> payloadMap && payloadMap.containsKey("value")) {
                return payloadMap.get("value");
            }
            return payload;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            return copy;
        }
        if (rawArgs instanceof String text && !text.isBlank()) {
            try {
                return JSON.readValue(text, new TypeReference<>() {
                });
            } catch (JsonProcessingException e) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private static Object interruptRequest(InterruptDecision.InterruptResult decision) {
        return decision.getRequest();
    }

    @SuppressWarnings("unchecked")
    private static String autoConfirmKey(InterruptDecision.InterruptResult decision) {
        Object request = interruptRequest(decision);
        if (request instanceof Map<?, ?> map) {
            Object key = map.get("auto_confirm_key");
            return key != null ? String.valueOf(key) : "";
        }
        return "";
    }

    /**
     * Small deterministic HITL flow used by these unit tests instead of a live LLM.
     *
     * <p>It exercises the same rail decisions, confirmation payloads, auto-confirm
     * merging, tool invocation counts, and interrupt result shape that the Python
     * tests assert around {@code Runner.run_agent(...)}.</p>
     */
    protected static class AssistantFlow {
        private final ConfirmInterruptRail rail;
        private final Map<String, Tool> toolsByName = new LinkedHashMap<>();
        private final Map<String, ToolCall> pending = new LinkedHashMap<>();
        private final Map<String, String> autoConfirmByInterruptId = new LinkedHashMap<>();
        private final Map<String, Object> autoConfirm = new LinkedHashMap<>();

        AssistantFlow(ConfirmInterruptRail rail, List<Tool> tools) {
            this.rail = rail;
            for (Tool tool : tools) {
                toolsByName.put(tool.getCard().getName(), tool);
            }
        }

        Map<String, Object> start(ToolCall... calls) {
            pending.clear();
            autoConfirmByInterruptId.clear();
            List<ToolCall> interrupted = new ArrayList<>();
            for (ToolCall call : calls) {
                handleFirstPass(call).ifPresent(interrupted::add);
            }
            return interrupted.isEmpty()
                ? answerResult("Operation completed")
                : interruptResult(interrupted);
        }

        Map<String, Object> resume(InteractiveInput input) {
            if (pending.isEmpty()) {
                return answerResult("Operation completed");
            }
            if (input == null || input.getUserInputs() == null || input.getUserInputs().isEmpty()) {
                return interruptResult(new ArrayList<>(pending.values()));
            }

            List<String> ids = new ArrayList<>(pending.keySet());
            for (String id : ids) {
                if (!input.getUserInputs().containsKey(id)) {
                    continue;
                }
                ToolCall call = pending.get(id);
                Object userInput = input.getUserInputs().get(id);
                InterruptDecision decision = rail.resolveInterrupt(null, call, userInput, autoConfirm);
                if (decision.isApproved()) {
                    maybeStoreAutoConfirm(id, userInput);
                    invokeTool(call);
                    pending.remove(id);
                } else if (decision.isRejected()) {
                    pending.remove(id);
                }
            }
            drainAutoConfirmedPending();
            return pending.isEmpty()
                ? answerResult("Operation completed")
                : interruptResult(new ArrayList<>(pending.values()));
        }

        Map<String, Object> autoConfirmConfig() {
            return autoConfirm;
        }

        private Optional<ToolCall> handleFirstPass(ToolCall call) {
            if (!rail.hasTool(call.getName())) {
                invokeTool(call);
                return Optional.empty();
            }
            InterruptDecision decision = rail.resolveInterrupt(null, call, null, autoConfirm);
            if (decision.isApproved()) {
                invokeTool(call);
                return Optional.empty();
            }
            if (decision instanceof InterruptDecision.InterruptResult interrupt) {
                pending.put(call.getId(), call);
                String key = autoConfirmKey(interrupt);
                if (!key.isBlank()) {
                    autoConfirmByInterruptId.put(call.getId(), key);
                }
                return Optional.of(call);
            }
            return Optional.empty();
        }

        private void maybeStoreAutoConfirm(String id, Object userInput) {
            if (!(userInput instanceof Map<?, ?> map)) {
                return;
            }
            if (!Boolean.TRUE.equals(map.get("auto_confirm"))) {
                return;
            }
            String key = autoConfirmByInterruptId.get(id);
            if (key != null && !key.isBlank()) {
                autoConfirm.put(key, true);
            }
        }

        private void invokeTool(ToolCall call) {
            Tool tool = toolsByName.get(call.getName());
            if (tool == null) {
                return;
            }
            try {
                tool.invoke(normalizeArgs(call.getArguments()), Map.of());
            } catch (Exception e) {
                throw new AssertionError("Tool invocation failed in test flow", e);
            }
        }

        private void drainAutoConfirmedPending() {
            List<String> ids = new ArrayList<>(pending.keySet());
            for (String id : ids) {
                ToolCall call = pending.get(id);
                InterruptDecision decision = rail.resolveInterrupt(null, call, null, autoConfirm);
                if (decision.isApproved()) {
                    invokeTool(call);
                    pending.remove(id);
                }
            }
        }

        private static Map<String, Object> interruptResult(List<ToolCall> calls) {
            List<String> ids = calls.stream().map(ToolCall::getId).toList();
            List<Object> state = calls.stream()
                .map(AssistantFlow::stateItem)
                .map(Object.class::cast)
                .toList();
            return Map.of(
                "result_type", "interrupt",
                "interrupt_ids", ids,
                "state", state
            );
        }

        private static Map<String, Object> stateItem(ToolCall call) {
            Map<String, Object> payloadValue = new LinkedHashMap<>();
            payloadValue.put("tool_name", call.getName());
            payloadValue.put("tool_args", call.getArguments());
            payloadValue.put("tool_call_id", call.getId());
            return Map.of("payload", Map.of("value", payloadValue));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        Runner.stop();
    }
}
