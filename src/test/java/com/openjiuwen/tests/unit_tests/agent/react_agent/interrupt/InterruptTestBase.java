/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ability.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent.react_agent.interrupt.test_base}.
 * Base test utilities for interrupt tests.
 */
public final class InterruptTestBase {

    private InterruptTestBase() {}

    public static class NestedAgentConfig {
        public String agentId;
        public String agentName;
        public String systemPrompt;
        public List<Tool> tools = new ArrayList<>();
        public List<AgentCard> subAgentCards = new ArrayList<>();
        public List<String> railToolNames = new ArrayList<>();

        public NestedAgentConfig() {}

        public NestedAgentConfig(String agentId, String agentName, String systemPrompt) {
            this.agentId = agentId;
            this.agentName = agentName;
            this.systemPrompt = systemPrompt;
        }
    }

    public static class AgentWithToolsConfig {
        public List<Tool> tools;
        public String sessionIdPrefix = "test";
        public String systemPrompt = "You are an assistant.";
        public List<String> railToolNames = new ArrayList<>();
        public List<String> traceToolNames = new ArrayList<>();

        public AgentWithToolsConfig(List<Tool> tools) {
            this.tools = tools;
        }
    }

    public static class ReadTool extends Tool {
        public int invokeCount = 0;

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
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokeCount++;
            String filepath = (String) inputs.getOrDefault("filepath", "");
            return Map.of(
                    "success", true,
                    "content", "Content of file " + filepath,
                    "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Object result;
            try {
                result = invoke(inputs, kwargs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return List.of(result).iterator();
        }
    }

    public static class WriteTool extends Tool {
        public int invokeCount = 0;

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
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokeCount++;
            String filepath = (String) inputs.getOrDefault("filepath", "");
            return Map.of(
                    "success", true,
                    "message", "Written to " + filepath,
                    "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Object result;
            try {
                result = invoke(inputs, kwargs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return List.of(result).iterator();
        }
    }

    public static class ActionTool extends Tool {
        private final String actionName;
        public int invokeCount = 0;

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
            this.actionName = name;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            invokeCount++;
            String action = (String) inputs.getOrDefault("action", "");
            return Map.of(
                    "success", true,
                    "data", "Execute " + actionName + ": " + action,
                    "invoke_count", invokeCount
            );
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Object result;
            try {
                result = invoke(inputs, kwargs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return List.of(result).iterator();
        }
    }

    public static class TraceRail extends AgentRail {
        private final List<String> toolNames;
        private final Map<String, Integer> toolInvokeCount = new HashMap<>();

        public TraceRail(List<String> toolNames) {
            this.toolNames = toolNames;
        }

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            Object inputs = ctx.getInputs();
            String toolName = "";
            if (inputs instanceof Map) {
                toolName = (String) ((Map<?, ?>) inputs).getOrDefault("tool_name", "");
            }
            toolInvokeCount.merge(toolName, 0, (old, val) -> old);
            Object toolResult = null;
            if (inputs instanceof Map) {
                toolResult = ((Map<?, ?>) inputs).get("tool_result");
            }
            if (toolResult != null) {
                toolInvokeCount.merge(toolName, 1, Integer::sum);
            }
        }

        public int getExecutionCount(String toolName) {
            return toolInvokeCount.getOrDefault(toolName, 0);
        }
    }

    public static ReActAgent createAgentWithTools(AgentWithToolsConfig config) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(config.sessionIdPrefix + "_agent")
                .build());
        ReActAgentConfig agentConfig = ReActAgentConfig.builder().build();
        agentConfig.configureModelClient(
                "OpenAI", "sk-fake", "https://api.openai.com/v1",
                "gpt-3.5-turbo", false
        );
        agentConfig.configurePromptTemplate(List.of(
                Map.of("role", "system", "content", config.systemPrompt)
        ));
        agent.configure(agentConfig);

        for (Tool tool : config.tools) {
            Runner.resourceMgr().addTool(tool);
            agent.getAbilityManager().add(tool.getCard());
        }

        if (!config.traceToolNames.isEmpty()) {
            TraceRail traceRail = new TraceRail(config.traceToolNames);
            agent.registerRail(traceRail);
        }

        if (!config.railToolNames.isEmpty()) {
            ConfirmInterruptRail rail = new ConfirmInterruptRail(config.railToolNames);
            agent.registerRail(rail);
        }

        return agent;
    }

    public static ReActAgent createNestedAgent(NestedAgentConfig config) {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id(config.agentId)
                .name(config.agentName)
                .build());
        ReActAgentConfig agentConfig = ReActAgentConfig.builder().build();
        agentConfig.configureModelClient(
                "OpenAI", "sk-fake", "https://api.openai.com/v1",
                "gpt-3.5-turbo", false
        );
        agentConfig.configurePromptTemplate(List.of(
                Map.of("role", "system", "content", config.systemPrompt)
        ));
        agent.configure(agentConfig);

        if (config.tools != null) {
            for (Tool tool : config.tools) {
                Runner.resourceMgr().addTool(tool);
                agent.getAbilityManager().add(tool.getCard());
            }
        }

        if (config.subAgentCards != null) {
            for (AgentCard card : config.subAgentCards) {
                agent.getAbilityManager().add(card);
            }
        }

        if (config.railToolNames != null && !config.railToolNames.isEmpty()) {
            ConfirmInterruptRail rail = new ConfirmInterruptRail(config.railToolNames);
            agent.registerRail(rail);
        }

        return agent;
    }

    public static void assertInterruptResult(Map<String, Object> result, int expectedCount) {
        assertNotNull(result);
        assertEquals("interrupt", result.get("result_type"),
                "Expected result_type=interrupt, got " + result.get("result_type"));
        List<?> interruptIds = (List<?>) result.get("interrupt_ids");
        List<?> stateList = (List<?>) result.get("state");
        assertNotNull(interruptIds);
        assertEquals(expectedCount, interruptIds.size(),
                "Expected " + expectedCount + " interrupts, actual " + interruptIds.size());
        if (stateList != null) {
            assertEquals(expectedCount, stateList.size());
        }
    }

    public static void assertInterruptResult(Map<String, Object> result) {
        assertInterruptResult(result, 1);
    }

    public static void assertAnswerResult(Map<String, Object> result) {
        assertNotNull(result);
        assertEquals("answer", result.get("result_type"),
                "Expected result_type=answer, got " + result.get("result_type"));
    }

    @SuppressWarnings("unchecked")
    public static String getToolNameFromState(Object stateItem) {
        if (stateItem instanceof Map) {
            Map<String, Object> state = (Map<String, Object>) stateItem;
            Object payload = state.get("payload");
            if (payload instanceof Map) {
                return (String) ((Map<String, Object>) payload).getOrDefault("tool_name", "");
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public static String getFilepathFromState(Object stateItem) {
        if (stateItem instanceof Map) {
            Map<String, Object> state = (Map<String, Object>) stateItem;
            Object payload = state.get("payload");
            if (payload instanceof Map) {
                Object toolArgs = ((Map<String, Object>) payload).get("tool_args");
                if (toolArgs instanceof Map) {
                    return (String) ((Map<String, Object>) toolArgs).getOrDefault("filepath", "");
                }
            }
        }
        return "";
    }

    public static InteractiveInput confirmInterrupt(String toolCallId) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", true,
                "feedback", "Confirm"
        ));
        return input;
    }

    public static InteractiveInput confirmInterrupt(String toolCallId, boolean autoConfirm) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", true,
                "feedback", "Confirm",
                "auto_confirm", autoConfirm
        ));
        return input;
    }

    public static InteractiveInput rejectInterrupt(String toolCallId, String feedback) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", false,
                "feedback", feedback
        ));
        return input;
    }
}
