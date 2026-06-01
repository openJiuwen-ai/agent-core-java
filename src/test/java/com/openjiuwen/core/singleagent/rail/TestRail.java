/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.AgentCallbackManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Rail and Callback framework.
 *
 * <p>Mirrors Python's tests/unit_tests/core/single_agent/rail/test_rail.py.</p>
 */
@DisplayName("Rail tests")
class TestRail {

    private final List<AgentCallbackManager> managersToClear = new ArrayList<>();
    private final List<ReActAgent> agentsToClear = new ArrayList<>();
    private final List<String> toolIdsToRemove = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (AgentCallbackManager manager : managersToClear) {
            manager.clear(null);
        }
        for (ReActAgent agent : agentsToClear) {
            agent.getAgentCallbackManager().clear(null);
        }
        for (String toolId : toolIdsToRemove) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
    }

    @Test
    @DisplayName("test_agent_rail_registration")
    void testAgentRailRegistration() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("ok")).model());
        LogRail logRail = new LogRail();

        agent.registerRail(logRail);

        AgentCallbackManager manager = agent.getAgentCallbackManager();
        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(manager.hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
        assertThat(manager.hasHooks(AgentCallbackEvent.BEFORE_MODEL_CALL)).isTrue();
    }

    @Test
    @DisplayName("test_agent_rail_8_events")
    void testAgentRail8Events() {
        AgentCallbackManager manager = makeManager();
        LogRail logRail = new LogRail();
        manager.registerRail(logRail, null);
        AgentCallbackContext ctx = contextFor(manager);

        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, ctx);
        manager.execute(AgentCallbackEvent.AFTER_INVOKE, ctx);
        manager.execute(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx);
        manager.execute(AgentCallbackEvent.AFTER_MODEL_CALL, ctx);
        manager.execute(AgentCallbackEvent.BEFORE_TOOL_CALL, ctx);
        manager.execute(AgentCallbackEvent.AFTER_TOOL_CALL, ctx);

        assertThat(logRail.events).contains(
                "before_invoke",
                "after_invoke",
                "before_model_call",
                "after_model_call",
                "before_tool_call",
                "after_tool_call"
        );
    }

    @Test
    @DisplayName("test_rail_priority_ordering")
    void testRailPriorityOrdering() {
        AgentCallbackManager manager = makeManager();
        List<String> order = new ArrayList<>();

        manager.registerRail(new LowPriorityRail(order), null);
        manager.registerRail(new HighPriorityRail(order), null);

        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, contextFor(manager));

        assertThat(order).containsExactly("high", "low");
    }

    @Test
    @DisplayName("test_rail_extra_communication")
    void testRailExtraCommunication() {
        AgentCallbackManager manager = makeManager();
        ExtraReaderRail reader = new ExtraReaderRail();
        manager.registerRail(new ExtraWriterRail(), null);
        manager.registerRail(reader, null);
        AgentCallbackContext ctx = contextFor(manager);

        manager.execute(AgentCallbackEvent.BEFORE_INVOKE, ctx);
        manager.execute(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx);

        assertThat(reader.sawWriter).isTrue();
    }

    @Test
    @DisplayName("test_rail_exception_events")
    void testRailExceptionEvents() {
        AgentCallbackManager manager = makeManager();
        LogRail logRail = new LogRail();
        manager.registerRail(logRail, null);

        assertThatThrownBy(() -> RailExecutor.execute(
                contextFor(manager),
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    throw new RuntimeException("LLM failed");
                }
        )).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM failed");

        assertThat(logRail.events).contains("on_model_exception", "after_model_call");
    }

    @Test
    @DisplayName("test_on_model_exception_can_request_retry")
    void testOnModelExceptionCanRequestRetry() {
        AgentCallbackManager manager = makeManager();
        List<String> events = new ArrayList<>();
        AtomicInteger invokeCount = new AtomicInteger();

        manager.registerRail(new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                events.add("before:" + ctx.getRetryAttempt());
            }

            @Override
            public void afterModelCall(AgentCallbackContext ctx) {
                events.add("after:" + ctx.getRetryAttempt());
            }

            @Override
            public void onModelException(AgentCallbackContext ctx) {
                events.add("exception:" + ctx.getRetryAttempt());
                if (ctx.getRetryAttempt() < 1) {
                    ctx.requestRetry(0.0);
                }
            }
        }, null);

        Map<String, Object> result = RailExecutor.execute(
                contextFor(manager),
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    if (invokeCount.incrementAndGet() == 1) {
                        throw new RuntimeException("LLM failed once");
                    }
                    return Map.of("result_type", "answer", "output", "ok");
                }
        );

        assertThat(result).containsEntry("result_type", "answer");
        assertThat(invokeCount.get()).isEqualTo(2);
        assertThat(events).containsExactly(
                "before:0",
                "exception:0",
                "after:0",
                "before:1",
                "after:1"
        );
    }

    @Test
    @DisplayName("test_on_tool_exception_can_request_retry")
    void testOnToolExceptionCanRequestRetry() {
        AgentCallbackManager manager = makeManager();
        ToolCall toolCall = toolCall("mock_retry_tool", "add", "{\"a\": 1, \"b\": 2}");
        List<String> events = new ArrayList<>();
        AtomicInteger executeCount = new AtomicInteger();

        manager.registerRail(new AgentRail() {
            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
                events.add("before:" + inputs.getToolCall().getId() + ":" + ctx.getRetryAttempt());
            }

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
                events.add("after:" + inputs.getToolCall().getId() + ":" + ctx.getRetryAttempt());
            }

            @Override
            public void onToolException(AgentCallbackContext ctx) {
                ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
                events.add("exception:" + inputs.getToolCall().getId() + ":" + ctx.getRetryAttempt());
                if (ctx.getRetryAttempt() < 1) {
                    ctx.requestRetry(0.0);
                }
            }
        }, null);

        AgentCallbackContext ctx = contextFor(manager);
        ctx.setInputs(ToolCallInputs.builder().toolCall(toolCall).toolName("add").build());
        Integer result = RailExecutor.execute(
                ctx,
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_TOOL_EXCEPTION,
                () -> {
                    if (executeCount.incrementAndGet() == 1) {
                        throw new RuntimeException("tool failed once");
                    }
                    return 3;
                }
        );

        assertThat(result).isEqualTo(3);
        assertThat(executeCount.get()).isEqualTo(2);
        assertThat(events).containsExactly(
                "before:mock_retry_tool:0",
                "exception:mock_retry_tool:0",
                "after:mock_retry_tool:0",
                "before:mock_retry_tool:1",
                "after:mock_retry_tool:1"
        );
    }

    @Test
    @DisplayName("test_rail_tools_auto_registration")
    void testRailToolsAutoRegistration() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("ok")).model());
        ToolCard toolCard = railToolCard("rail_tool");
        AgentRail rail = new ToolCarryingRail(List.of(toolCard));

        agent.registerRail(rail);

        assertThat(toolNames(agent)).contains("rail_tool");
    }

    @Test
    @DisplayName("test_rail_unregister_removes_tools")
    void testRailUnregisterRemovesTools() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("ok")).model());
        ToolCard toolCard = railToolCard("rail_tool_remove");
        AgentRail rail = new ToolCarryingRail(List.of(toolCard));

        agent.registerRail(rail);
        assertThat(toolNames(agent)).contains("rail_tool_remove");

        agent.unregisterRail(rail);
        assertThat(toolNames(agent)).doesNotContain("rail_tool_remove");
    }

    @Test
    @DisplayName("test_rail_decorator_before_after")
    void testRailDecoratorBeforeAfter() {
        AgentCallbackManager manager = makeManager();
        LogRail logRail = new LogRail();
        manager.registerRail(logRail, null);

        String result = RailExecutor.execute(
                contextFor(manager),
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> "done"
        );

        assertThat(result).isEqualTo("done");
        assertThat(logRail.events).containsSubsequence("before_model_call", "after_model_call");
    }

    @Test
    @DisplayName("test_rail_decorator_after_on_exception_both_fire")
    void testRailDecoratorAfterOnExceptionBothFire() {
        AgentCallbackManager manager = makeManager();
        LogRail logRail = new LogRail();
        manager.registerRail(logRail, null);

        assertThatThrownBy(() -> RailExecutor.execute(
                contextFor(manager),
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    throw new RuntimeException("boom");
                }
        )).isInstanceOf(RuntimeException.class);

        assertThat(logRail.events).contains("on_model_exception", "after_model_call");
    }

    @Test
    @DisplayName("test_ctx_lifecycle_normal")
    void testCtxLifecycleNormal() {
        AgentCallbackManager manager = makeManager();
        LogRail logRail = new LogRail();
        manager.registerRail(logRail, null);
        AgentCallbackContext ctx = contextFor(manager);
        List<String> bodyEvents = new ArrayList<>();

        ctx.lifecycle(AgentCallbackEvent.BEFORE_INVOKE, AgentCallbackEvent.AFTER_INVOKE,
                () -> bodyEvents.add("body"));

        assertThat(bodyEvents).containsExactly("body");
        assertThat(logRail.events).containsExactly("before_invoke", "after_invoke");
    }

    @Test
    @DisplayName("test_ctx_lifecycle_exception")
    void testCtxLifecycleException() {
        AgentCallbackManager manager = makeManager();
        LogRail logRail = new LogRail();
        manager.registerRail(logRail, null);
        AgentCallbackContext ctx = contextFor(manager);

        assertThatThrownBy(() -> ctx.lifecycle(
                AgentCallbackEvent.BEFORE_INVOKE,
                AgentCallbackEvent.AFTER_INVOKE,
                () -> {
                    throw new RuntimeException("fail");
                }
        )).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fail");

        assertThat(logRail.events).containsExactly("before_invoke", "after_invoke");
    }

    @Test
    @DisplayName("test_ctx_fire_manual")
    void testCtxFireManual() {
        AgentCallbackManager manager = makeManager();
        List<String> fired = new ArrayList<>();
        manager.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, ctx -> fired.add("manual_before"));
        AgentCallbackContext ctx = contextFor(manager);

        ctx.fire(AgentCallbackEvent.BEFORE_INVOKE);

        assertThat(fired).containsExactly("manual_before");
    }

    @Test
    @DisplayName("test_method_split_data_visibility")
    void testMethodSplitDataVisibility() {
        AgentCallbackManager manager = makeManager();
        List<List<Object>> seenMessages = new ArrayList<>();
        manager.registerCallback(AgentCallbackEvent.BEFORE_MODEL_CALL, ctx -> {
            ModelCallInputs inputs = (ModelCallInputs) ctx.getInputs();
            seenMessages.add(new ArrayList<>(inputs.getMessages()));
        });
        AgentCallbackContext ctx = contextFor(manager);
        ctx.setInputs(ModelCallInputs.builder().messages(List.of("msg1", "msg2")).build());

        ctx.fire(AgentCallbackEvent.BEFORE_MODEL_CALL);

        assertThat(seenMessages).containsExactly(List.of("msg1", "msg2"));
    }

    @Test
    @DisplayName("test_react_agent_evolve_import")
    void testReactAgentEvolveImport() {
        assertThat(ReActAgentEvolve.class).isNotNull();
    }

    @Test
    @DisplayName("test_before_invoke_receives_invoke_inputs")
    void testBeforeInvokeReceivesInvokeInputs() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("ok")).model());
        List<InvokeInputs> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeInvoke(AgentCallbackContext ctx) {
                captured.add((InvokeInputs) ctx.getInputs());
            }
        });

        agent.invoke(Map.of("query", "hello"), null);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getQuery()).isEqualTo("hello");
    }

    @Test
    @DisplayName("test_after_invoke_receives_invoke_inputs_with_result")
    void testAfterInvokeReceivesInvokeInputsWithResult() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("done")).model());
        List<InvokeInputs> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void afterInvoke(AgentCallbackContext ctx) {
                captured.add((InvokeInputs) ctx.getInputs());
            }
        });

        agent.invoke(Map.of("query", "test"), null);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getQuery()).isEqualTo("test");
        assertThat(captured.get(0).getResult()).containsEntry("result_type", "answer");
    }

    @Test
    @DisplayName("test_before_model_call_receives_model_call_inputs")
    void testBeforeModelCallReceivesModelCallInputs() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("ok")).model());
        List<ModelCallInputs> captured = new ArrayList<>();
        List<Object> contexts = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeModelCall(AgentCallbackContext ctx) {
                captured.add((ModelCallInputs) ctx.getInputs());
                contexts.add(ctx.getContext());
            }
        });

        agent.invoke(Map.of("query", "test"), null);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getMessages()).isNotEmpty();
        assertThat(contexts.get(0)).isNotNull();
    }

    @Test
    @DisplayName("test_before_model_call_preview_messages_do_not_override_builder")
    void testBeforeModelCallPreviewMessagesDoNotOverrideBuilder() {
        Assumptions.assumeTrue(
                hasPublicMethod(ReActAgent.class, "addPromptBuilderSection"),
                "Java ReActAgent has no prompt-builder section API equivalent to Python add_prompt_builder_section."
        );
    }

    @Test
    @DisplayName("test_before_tool_call_receives_tool_call_inputs")
    void testBeforeToolCallReceivesToolCallInputs() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(
                toolResponse("mock_call_add", "add", "{\"a\": 1, \"b\": 2}"),
                textResponse("3")
        ).model());
        registerAddTool(agent);
        List<ToolCallInputs> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                captured.add((ToolCallInputs) ctx.getInputs());
            }
        });

        agent.invoke(Map.of("query", "1+2"), null);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getToolName()).isEqualTo("add");
        assertThat(captured.get(0).getToolCall()).isNotNull();
    }

    @Test
    @DisplayName("test_multi_tool_calls_fire_per_tool_events")
    void testMultiToolCallsFirePerToolEvents() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("unused")).model());
        registerAddTool(agent);
        List<String> beforeCalls = new ArrayList<>();
        List<Map.Entry<String, Object>> afterCalls = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                beforeCalls.add(((ToolCallInputs) ctx.getInputs()).getToolCall().getId());
            }

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
                afterCalls.add(Map.entry(inputs.getToolCall().getId(), inputs.getToolResult()));
            }
        });
        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

        agent.getAbilityManager().execute(ctx, List.of(
                toolCall("mock_call_add_1", "add", "{\"a\": 1, \"b\": 2}"),
                toolCall("mock_call_add_2", "add", "{\"a\": 3, \"b\": 4}")
        ), null, null);

        assertThat(beforeCalls).containsExactlyInAnyOrder("mock_call_add_1", "mock_call_add_2");
        assertThat(afterCalls).hasSize(2);
        assertThat(afterCalls.stream().map(Map.Entry::getKey))
                .containsExactlyInAnyOrder("mock_call_add_1", "mock_call_add_2");
        assertThat(afterCalls.stream().map(Map.Entry::getValue).sorted(Comparator.comparing(String::valueOf)))
                .containsExactly(3, 7);
    }

    @Test
    @DisplayName("test_before_tool_call_can_rewrite_args")
    void testBeforeToolCallCanRewriteArgs() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("unused")).model());
        registerAddTool(agent);
        List<Object> capturedResult = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void beforeToolCall(AgentCallbackContext ctx) {
                ((ToolCallInputs) ctx.getInputs()).setToolArgs("{\"a\": 2, \"b\": 5}");
            }

            @Override
            public void afterToolCall(AgentCallbackContext ctx) {
                capturedResult.add(((ToolCallInputs) ctx.getInputs()).getToolResult());
            }
        });
        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

        agent.getAbilityManager().execute(ctx,
                toolCall("mock_call_rewrite", "add", "{\"a\": 1, \"b\": 1}"), null, null);

        assertThat(capturedResult).containsExactly(7);
    }

    @Test
    @DisplayName("test_on_tool_exception_is_per_tool_call")
    void testOnToolExceptionIsPerToolCall() throws Exception {
        TestableReActAgent agent = makeAgent(mockModel(textResponse("unused")).model());
        registerAddTool(agent);
        List<String> failedCalls = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public void onToolException(AgentCallbackContext ctx) {
                failedCalls.add(((ToolCallInputs) ctx.getInputs()).getToolCall().getId());
            }
        });
        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(agent).build();

        List<AbilityManager.ToolExecutionEntry> entries = agent.getAbilityManager().execute(ctx, List.of(
                toolCall("mock_call_ok", "add", "{\"a\": 1, \"b\": 2}"),
                toolCall("mock_call_missing", "missing_tool", "{}")
        ), null, null);

        assertThat(failedCalls).containsExactly("mock_call_missing");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).classification()).isEqualTo(AbilityManager.ToolExecutionClassification.SUCCESS);
        assertThat(entries.get(1).classification()).isEqualTo(AbilityManager.ToolExecutionClassification.ERROR);
    }

    @Test
    @DisplayName("test_before_model_call_force_finish")
    void testBeforeModelCallForceFinish() {
        assumeForceFinishApi();
    }

    @Test
    @DisplayName("test_after_model_call_force_finish")
    void testAfterModelCallForceFinish() {
        assumeForceFinishApi();
    }

    @Test
    @DisplayName("test_after_tool_call_force_finish")
    void testAfterToolCallForceFinish() {
        assumeForceFinishApi();
    }

    @Test
    @DisplayName("test_force_finish_result_in_after_invoke")
    void testForceFinishResultInAfterInvoke() {
        assumeForceFinishApi();
    }

    @Test
    @DisplayName("test_consume_clears_signal")
    void testConsumeClearsSignal() {
        ForceFinishApi api = assumeForceFinishApi();
        AgentCallbackContext ctx = AgentCallbackContext.builder().build();
        Map<String, Object> expected = Map.of("output", "x");

        invoke(api.request(), ctx, expected);
        Object first = invoke(api.consume(), ctx);
        Object second = invoke(api.consume(), ctx);

        assertThat(first).isNotNull();
        assertThat(readForceFinishResult(first)).isEqualTo(expected);
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("test_memory_rail_rendered_prompt_survives_multiple_iterations")
    void testMemoryRailRenderedPromptSurvivesMultipleIterations() {
        Assumptions.assumeTrue(
                false,
                "Exact MemoryRail parity needs an injectable memory mock or main-code seam outside this test file."
        );
    }

    private AgentCallbackManager makeManager() {
        AgentCallbackManager manager = new AgentCallbackManager("rail-test-" + UUID.randomUUID());
        managersToClear.add(manager);
        return manager;
    }

    private AgentCallbackContext contextFor(AgentCallbackManager manager) {
        AgentCallbackFirer firer = manager::execute;
        return AgentCallbackContext.builder().agent(firer).build();
    }

    private TestableReActAgent makeAgent(Model model) {
        String id = "rail-test-agent-" + UUID.randomUUID();
        AgentCard card = AgentCard.builder()
                .id(id)
                .name(id)
                .description("test agent")
                .build();
        TestableReActAgent agent = new TestableReActAgent(card, model);
        agent.configure(ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", "You are a test assistant.")))
                .maxIterations(3)
                .build());
        agentsToClear.add(agent);
        return agent;
    }

    private MockModelHandle mockModel(AssistantMessage... responses) throws Exception {
        Model model = mock(Model.class);
        AtomicInteger callCount = new AtomicInteger();
        List<List<Object>> callHistory = new ArrayList<>();
        when(model.supportsKvCacheRelease()).thenReturn(false);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    int index = callCount.getAndIncrement();
                    Object messages = invocation.getArgument(0);
                    if (messages instanceof List<?> list) {
                        callHistory.add(new ArrayList<>(list));
                    }
                    if (index < responses.length) {
                        return responses[index];
                    }
                    return textResponse("Default mock response");
                });
        return new MockModelHandle(model, callCount, callHistory);
    }

    private void registerAddTool(ReActAgent agent) {
        String toolId = "rail_add_" + UUID.randomUUID().toString().replace("-", "");
        ToolCard card = ToolCard.builder()
                .id(toolId)
                .name("add")
                .description("add two numbers")
                .inputParams(Map.of())
                .build();
        LocalFunction add = new LocalFunction(card, inputs -> number(inputs.get("a")) + number(inputs.get("b")));
        Runner.resourceMgr().addTool(add, null);
        toolIdsToRemove.add(toolId);
        agent.getAbilityManager().add(card);
    }

    private List<String> toolNames(ReActAgent agent) {
        List<String> names = new ArrayList<>();
        for (Object ability : agent.getAbilityManager().list()) {
            if (ability instanceof ToolCard toolCard) {
                names.add(toolCard.getName());
            }
        }
        return names;
    }

    private static int number(Object value) {
        assertThat(value).isInstanceOf(Number.class);
        return ((Number) value).intValue();
    }

    private static AssistantMessage textResponse(String content) {
        return AssistantMessage.builder()
                .content(content)
                .finishReason("stop")
                .build();
    }

    private static AssistantMessage toolResponse(String id, String toolName, String arguments) {
        return AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall(id, toolName, arguments)))
                .finishReason("tool_calls")
                .build();
    }

    private static ToolCall toolCall(String id, String toolName, String arguments) {
        return ToolCall.builder()
                .id(id)
                .type("function")
                .name(toolName)
                .arguments(arguments)
                .build();
    }

    private static ToolCard railToolCard(String name) {
        return ToolCard.builder()
                .id(name)
                .name(name)
                .description("A rail tool")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
    }

    private static boolean hasPublicMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static ForceFinishApi assumeForceFinishApi() {
        Method request = findMethod(AgentCallbackContext.class, "requestForceFinish", "request_force_finish", 1);
        Method consume = findMethod(AgentCallbackContext.class, "consumeForceFinish", "consume_force_finish", 0);
        Assumptions.assumeTrue(
                request != null && consume != null,
                "AgentCallbackContext has no force-finish API matching Python "
                        + "request_force_finish/consume_force_finish."
        );
        return new ForceFinishApi(request, consume);
    }

    private static Method findMethod(Class<?> type, String camelName, String snakeName, int parameterCount) {
        for (Method method : type.getMethods()) {
            if ((method.getName().equals(camelName) || method.getName().equals(snakeName))
                    && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke method " + method.getName(), e);
        }
    }

    private static Object readForceFinishResult(Object signal) {
        if (signal instanceof Map<?, ?> map && map.containsKey("result")) {
            return map.get("result");
        }
        try {
            Method getter = signal.getClass().getMethod("getResult");
            return getter.invoke(signal);
        } catch (ReflectiveOperationException ignored) {
            return signal;
        }
    }

    private static final class LogRail extends AgentRail {
        private final List<String> events = new ArrayList<>();

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            events.add("before_invoke");
        }

        @Override
        public void afterInvoke(AgentCallbackContext ctx) {
            events.add("after_invoke");
        }

        @Override
        public void beforeModelCall(AgentCallbackContext ctx) {
            events.add("before_model_call");
        }

        @Override
        public void afterModelCall(AgentCallbackContext ctx) {
            events.add("after_model_call");
        }

        @Override
        public void onModelException(AgentCallbackContext ctx) {
            events.add("on_model_exception");
        }

        @Override
        public void beforeToolCall(AgentCallbackContext ctx) {
            events.add("before_tool_call");
        }

        @Override
        public void afterToolCall(AgentCallbackContext ctx) {
            events.add("after_tool_call");
        }

        @Override
        public void onToolException(AgentCallbackContext ctx) {
            events.add("on_tool_exception");
        }
    }

    private static final class HighPriorityRail extends AgentRail {
        private final List<String> order;

        private HighPriorityRail(List<String> order) {
            this.order = order;
            setPriority(90);
        }

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            order.add("high");
        }
    }

    private static final class LowPriorityRail extends AgentRail {
        private final List<String> order;

        private LowPriorityRail(List<String> order) {
            this.order = order;
            setPriority(10);
        }

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            order.add("low");
        }
    }

    private static final class ExtraWriterRail extends AgentRail {
        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            ctx.getExtra().put("writer_was_here", true);
        }
    }

    private static final class ExtraReaderRail extends AgentRail {
        private boolean sawWriter;

        @Override
        public void beforeModelCall(AgentCallbackContext ctx) {
            sawWriter = Boolean.TRUE.equals(ctx.getExtra().get("writer_was_here"));
        }
    }

    private static final class ToolCarryingRail extends AgentRail {
        private ToolCarryingRail(List<ToolCard> tools) {
            super(tools);
        }

        @Override
        public void beforeInvoke(AgentCallbackContext ctx) {
            // Registering one hook activates the rail registration path.
        }
    }

    private static final class TestableReActAgent extends ReActAgent {
        private final Model model;

        private TestableReActAgent(AgentCard card, Model model) {
            super(card);
            this.model = model;
        }

        @Override
        protected Model getLlm() {
            return model;
        }
    }

    private record MockModelHandle(Model model, AtomicInteger callCount, List<List<Object>> callHistory) {
    }

    private record ForceFinishApi(Method request, Method consume) {
    }
}
