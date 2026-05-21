/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end tests for the force-finish rail signal.
 * <p>
 * Mirrors Python's {@code test_force_finish_rail} in
 * {@code tests/system_tests/rail/test_force_finish_rail.py}.
 */
@Tag("system-test")
class ForceFinishRailTest {

    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        agent = createAgent();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    private static ReActAgent createAgent() {
        if (!System.getenv().containsKey("LLM_SSL_VERIFY")) {
            System.setProperty("LLM_SSL_VERIFY", "false");
        }
        AgentCard card = AgentCard.builder()
                .description("force-finish test assistant")
                .build();
        ModelRequestConfig modelConfig = new ModelRequestConfig();
        modelConfig.setModel("gpt-3.5-turbo");
        modelConfig.setTemperature(0.8);
        modelConfig.setTopP(0.9);
        ModelClientConfig clientConfig = new ModelClientConfig();
        clientConfig.setClientProvider("OpenAI");
        clientConfig.setApiKey("mock_key");
        clientConfig.setApiBase("mock_url");
        clientConfig.setTimeout(30);
        clientConfig.setVerifySsl(false);
        List<Map<String, String>> promptTemplate = List.of(
                Map.of("role", "system", "content", "You are a math calculation assistant.")
        );
        ReActAgentConfig config = new ReActAgentConfig();
        config.setModelConfigObj(modelConfig);
        config.setModelClientConfig(clientConfig);
        config.setPromptTemplate(promptTemplate);
        ReActAgent reactAgent = new ReActAgent(card);
        reactAgent.configure(config);

        ToolCard toolCard = new ToolCard();
        toolCard.setId("add");
        toolCard.setName("add");
        toolCard.setDescription("Addition operation");
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> aParam = new HashMap<>();
        aParam.put("description", "First addend");
        aParam.put("type", "number");
        Map<String, Object> bParam = new HashMap<>();
        bParam.put("description", "Second addend");
        bParam.put("type", "number");
        properties.put("a", aParam);
        properties.put("b", bParam);
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("a", "b"));
        toolCard.setInputParams(inputParams);

        LocalFunction tool = new LocalFunction(toolCard, (args) -> {
            Number a = (Number) args.get("a");
            Number b = (Number) args.get("b");
            return a.doubleValue() + b.doubleValue();
        });
        reactAgent.getAbilityManager().add(toolCard);
        if (Runner.resourceMgr().getTool(toolCard.getId()) == null) {
            Runner.resourceMgr().addTool(tool);
        }
        return reactAgent;
    }

    @Test
    void testBeforeModelCallSkipsLlmAndReturnsResult() throws Exception {
        Map<String, Object> forced = Map.of("output", "intercepted", "result_type", "answer");
        AtomicBoolean modelCalled = new AtomicBoolean(false);

        AgentRail interceptRail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }
        };
        agent.registerRail(interceptRail);

        Map<String, Object> result = agent.invoke(Map.of("query", "hello"));
        assertEquals(forced, result);
    }

    @Test
    void testAfterModelCallStopsBeforeToolExecution() throws Exception {
        Map<String, Object> forced = Map.of("output", "stopped_after_model", "result_type", "answer");
        AtomicBoolean toolCalled = new AtomicBoolean(false);

        AgentRail stopAfterModelRail = new AgentRail() {
            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }

            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                toolCalled.set(true);
            }
        };
        agent.registerRail(stopAfterModelRail);

        Map<String, Object> result = agent.invoke(Map.of("query", "1+2"));
        assertEquals(forced, result);
        assertFalse(toolCalled.get());
    }

    @Test
    void testAfterToolCallBreaksLoop() throws Exception {
        Map<String, Object> forced = Map.of("output", "done_after_tool", "result_type", "answer");
        AtomicInteger callCount = new AtomicInteger(0);

        AgentRail stopAfterToolRail = new AgentRail() {
            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                callCount.incrementAndGet();
                ctx.requestForceFinish(forced);
            }
        };
        agent.registerRail(stopAfterToolRail);

        Map<String, Object> result = agent.invoke(Map.of("query", "3+4"));
        assertEquals(forced, result);
        assertEquals(1, callCount.get());
    }

    @Test
    void testForceFinishResultVisibleInAfterInvoke() throws Exception {
        Map<String, Object> forced = Map.of("output", "forced_result", "result_type", "answer");
        List<Map<String, Object>> captured = new ArrayList<>();

        AgentRail captureRail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }

            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                if (ctx.getInputs() instanceof InvokeInputs inputs) {
                    captured.add(inputs.getResult());
                }
            }
        };
        agent.registerRail(captureRail);

        agent.invoke(Map.of("query", "test"));
        assertEquals(1, captured.size());
        assertEquals(forced, captured.get(0));
    }

    @Test
    void testForceFinishWithConversationId() throws Exception {
        Map<String, Object> forced = Map.of("output", "with_conv_id", "result_type", "answer");

        AgentRail interceptRail = new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                ctx.requestForceFinish(forced);
            }
        };
        agent.registerRail(interceptRail);

        Map<String, Object> result = agent.invoke(Map.of(
                "query", "test",
                "conversation_id", "conv_123"
        ));
        assertEquals(forced, result);
    }
}
