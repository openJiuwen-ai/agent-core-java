/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
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

        if (config.railToolNames != null && !config.railToolNames.isEmpty()) {
            ConfirmInterruptRail rail = new ConfirmInterruptRail(config.railToolNames);
            agent.registerRail(rail);
        }

        return agent;
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
    protected void assertInterruptResult(Map<String, Object> result) {
        assertInterruptResult(result, 1);
    }

    /**
     * Assert interrupt result with expected count.
     */
    @SuppressWarnings("unchecked")
    protected void assertInterruptResult(Map<String, Object> result, int expectedCount) {
        assertNotNull(result);
        assertEquals("interrupt", result.get("result_type"));
        List<String> interruptIds = (List<String>) result.get("interrupt_ids");
        List<Object> stateList = (List<Object>) result.get("state");
        assertNotNull(interruptIds);
        assertNotNull(stateList);
        assertEquals(expectedCount, interruptIds.size(), 
            "Expected " + expectedCount + " interrupts, actual " + interruptIds.size());
        assertEquals(expectedCount, stateList.size());
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

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        Runner.stop();
    }

    @Test
    @DisplayName("Test base setup")
    void testBaseSetup() {
        assertTrue(true);
    }
}