/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.PluginSchema;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused LLMAgent compatibility tests.
 *
 * <p>Mirrors Python's {@code LLMAgent} and factory functions in
 * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.</p>
 */
class LLMAgentTest {

    @Test
    void createConfigPreservesFactoryFieldsAndDefaultsToolsToEmptyList() {
        WorkflowSchema workflow = WorkflowSchema.builder()
                .id("wf")
                .version("1.0")
                .name("workflow")
                .build();
        PluginSchema plugin = PluginSchema.builder()
                .id("plugin")
                .name("search")
                .build();
        ModelConfig model = ModelConfig.builder().modelProvider("mock").build();
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "stay exact"));

        LegacyReActAgentConfig config = LLMAgentFactory.createLlmAgentConfig(
                "agent-id",
                "v1",
                "desc",
                List.of(workflow),
                List.of(plugin),
                model,
                prompt
        );

        assertEquals("agent-id", config.getId());
        assertEquals("v1", config.getVersion());
        assertEquals("desc", config.getDescription());
        assertEquals(1, config.getWorkflows().size());
        assertEquals(1, config.getPlugins().size());
        assertEquals(model, config.getModel());
        assertEquals(prompt, config.getPromptTemplate());
        assertTrue(config.getTools().isEmpty());
        assertEquals(ControllerType.REACT_CONTROLLER, config.getControllerType());
    }

    @Test
    void createAgentAddsWorkflowsAndToolsLikePythonFactory() {
        LegacyReActAgentConfig config = LLMAgentFactory.createLlmAgentConfig(
                "agent-with-tools",
                "v1",
                "desc",
                new ArrayList<>(),
                new ArrayList<>(),
                new ModelConfig(),
                List.of()
        );
        Workflow workflow = new Workflow(new WorkflowCard("wf", "workflow", "desc", "1.0", Map.of()));
        Tool tool = new EchoTool();

        LLMAgent agent = LLMAgentFactory.createLlmAgent(config, List.of(workflow), List.of(tool));

        assertEquals(1, agent.getWorkflows().size());
        assertEquals(1, agent.getTools().size());
        assertTrue(config.getTools().contains("echo"));
    }

    @Test
    void constructorRejectsNonReactControllerType() {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        config.setControllerType(ControllerType.UNDEFINED);

        assertThrows(UnsupportedOperationException.class, () -> new LLMAgent(config));
    }

    @Test
    void setPromptTemplateUpdatesConfigWrapperAndControllerConfig() {
        LegacyReActAgentConfig config = new LegacyReActAgentConfig();
        LLMAgent agent = new LLMAgent(config);
        List<Map<String, Object>> prompt = List.of(Map.of("role", "system", "content", "new"));

        agent.setPromptTemplate(prompt);

        assertEquals(prompt, config.getPromptTemplate());
        assertEquals(config, agent.getConfigWrapper().getAgentConfig());
        assertEquals(prompt, agent.getLlmController().getAgentConfig().getPromptTemplate());
    }

    @Test
    void responseConversionHelpersMatchPythonCases() {
        OutputSchema outputSchema = new OutputSchema(
                "answer",
                0,
                Map.of("result_type", "answer", "output", "ok")
        );
        AssistantMessage fromOutputSchema = LLMAgent.convertResponseToMessage(outputSchema);
        AssistantMessage fromMap = LLMAgent.convertResponseToMessage(
                Map.of("result_type", "answer", "output", "map-ok"));
        AssistantMessage fromString = LLMAgent.convertResponseToMessage("text-ok");

        assertEquals("ok", fromOutputSchema.getContentAsString());
        assertEquals("map-ok", fromMap.getContentAsString());
        assertEquals("text-ok", fromString.getContentAsString());
        assertEquals("ok", LLMAgent.extractAnswerOutput(outputSchema));
        assertEquals("", LLMAgent.extractAnswerOutput(Map.of("result_type", "answer", "output", "ignored")));
    }

    @Test
    void invokeDelegatesToControllerPathWithoutMemoryWhenScopeMissing() {
        StubLLMAgent agent = new StubLLMAgent(new LegacyReActAgentConfig());
        Map<String, Object> result = castMap(agent.invoke(Map.of("query", "hello"), null)
                .toCompletableFuture()
                .join());

        assertEquals("hello", result.get("output"));
        assertEquals("default_session", result.get("session_id"));
        assertEquals(1, agent.invocations);
    }

    @Test
    void streamOwningSessionYieldsWrittenChunksAndCompletesCleanup() {
        StubLLMAgent agent = new StubLLMAgent(new LegacyReActAgentConfig());
        Iterator<Object> iterator = agent.stream(
                Map.of("query", "stream", "conversation_id", "stream-session"),
                null,
                List.of(StreamMode.OUTPUT)
        );

        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }

        assertEquals(1, chunks.size());
        OutputSchema chunk = assertInstanceOf(OutputSchema.class, chunks.get(0));
        assertEquals("answer", chunk.getType());
        assertEquals("stream", castMap(chunk.getPayload()).get("output"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        assertNotNull(value);
        assertInstanceOf(Map.class, value);
        return (Map<String, Object>) value;
    }

    /**
     * Mirrors Python's {@code LLMAgent} delegation seam in
     * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.
     */
    private static final class StubLLMAgent extends LLMAgent {
        private int invocations;

        private StubLLMAgent(LegacyReActAgentConfig agentConfig) {
            super(agentConfig);
        }

        @Override
        protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
            invocations++;
            String output = String.valueOf(inputs.getOrDefault("query", ""));
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("result_type", "answer");
            payload.put("output", output);
            payload.put("session_id", session.getSessionId());
            session.writeStream(new OutputSchema("answer", 0, payload));
            return payload;
        }
    }

    /**
     * Mirrors Python's tool dependency used by {@code create_llm_agent} in
     * {@code openjiuwen/core/application/llm_agent/llm_agent.py}.
     */
    private static final class EchoTool extends Tool {
        private EchoTool() {
            super(new ToolCard("tool-id", "echo", "echo tool"));
        }
    }
}
