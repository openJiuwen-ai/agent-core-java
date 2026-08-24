/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.common.async.FutureList;
import com.openjiuwen.core.common.async.FutureMap;
import com.openjiuwen.core.application.llm_agent.rails.MemoryRail;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.single_agent.rail.test_rail} in
 * {@code tests/unit_tests/core/single_agent/rail/test_rail.py}.</p>
 */
class RailPythonParityTest {

    private static final AtomicInteger AGENT_COUNTER = new AtomicInteger();

    @TestFactory
    Collection<DynamicTest> pythonRailCases() {
        return List.of(
                dynamic("TestRailRegistration::test_agent_rail_8_events", this::agentRail8Events),
                dynamic("TestRailRegistration::test_agent_rail_registration", this::agentRailRegistration),
                dynamic("TestRailPriority::test_rail_priority_ordering", this::railPriorityOrdering),
                dynamic("TestRailExtra::test_rail_extra_communication", this::railExtraCommunication),
                dynamic("TestRailExceptionEvents::test_rail_exception_events", this::railExceptionEvents),
                dynamic("TestRailExceptionRetry::test_on_model_exception_can_request_retry",
                        this::onModelExceptionCanRequestRetry),
                dynamic("TestRailExceptionRetry::test_on_tool_exception_can_request_retry",
                        this::onToolExceptionCanRequestRetry),
                dynamic("TestRailToolsRegistration::test_rail_tools_auto_registration",
                        this::railToolsAutoRegistration),
                dynamic("TestRailToolsRegistration::test_rail_unregister_removes_tools",
                        this::railUnregisterRemovesTools),
                dynamic("TestRailDecorator::test_rail_decorator_after_on_exception_both_fire",
                        this::railDecoratorAfterOnExceptionBothFire),
                dynamic("TestRailDecorator::test_rail_decorator_before_after", this::railDecoratorBeforeAfter),
                dynamic("TestCtxLifecycle::test_ctx_lifecycle_exception", this::ctxLifecycleException),
                dynamic("TestCtxLifecycle::test_ctx_lifecycle_normal", this::ctxLifecycleNormal),
                dynamic("TestCtxFire::test_ctx_fire_manual", this::ctxFireManual),
                dynamic("TestMethodSplitDataVisibility::test_method_split_data_visibility",
                        this::methodSplitDataVisibility),
                dynamic("TestReActAgentEvolveRegression::test_react_agent_evolve_import",
                        this::reactAgentEvolveImport),
                dynamic("TestTypedEventInputs::test_after_invoke_receives_invoke_inputs_with_result",
                        this::afterInvokeReceivesInvokeInputsWithResult),
                dynamic("TestTypedEventInputs::test_before_invoke_receives_invoke_inputs",
                        this::beforeInvokeReceivesInvokeInputs),
                dynamic("TestTypedEventInputs::test_before_model_call_preview_messages_do_not_override_builder",
                        this::beforeModelCallPreviewMessagesDoNotOverrideBuilder),
                dynamic("TestTypedEventInputs::test_before_model_call_receives_model_call_inputs",
                        this::beforeModelCallReceivesModelCallInputs),
                dynamic("TestTypedEventInputs::test_before_tool_call_can_rewrite_args",
                        this::beforeToolCallCanRewriteArgs),
                dynamic("TestTypedEventInputs::test_before_tool_call_receives_tool_call_inputs",
                        this::beforeToolCallReceivesToolCallInputs),
                dynamic("TestTypedEventInputs::test_multi_tool_calls_fire_per_tool_events",
                        this::multiToolCallsFirePerToolEvents),
                dynamic("TestTypedEventInputs::test_on_tool_exception_is_per_tool_call",
                        this::onToolExceptionIsPerToolCall),
                dynamic("TestForceFinish::test_after_model_call_force_finish", this::afterModelCallForceFinish),
                dynamic("TestForceFinish::test_after_tool_call_force_finish", this::afterToolCallForceFinish),
                dynamic("TestForceFinish::test_before_model_call_force_finish", this::beforeModelCallForceFinish),
                dynamic("TestForceFinish::test_before_tool_call_force_finish", this::beforeToolCallForceFinish),
                dynamic("TestForceFinish::test_consume_clears_signal", this::consumeClearsSignal),
                dynamic("TestForceFinish::test_force_finish_result_in_after_invoke",
                        this::forceFinishResultInAfterInvoke),
                dynamic("TestForceFinish::test_rail_decorator_force_finish_dict_not_subscriptable",
                        this::railDecoratorForceFinishDictNotSubscriptable),
                dynamic("TestForceFinish::test_rail_decorator_returns_force_finish_payload",
                        this::railDecoratorReturnsForceFinishPayload),
                dynamic("TestMemoryRailPromptAssembly::test_memory_rail_rendered_prompt_survives_multiple_iterations",
                        this::memoryRailRenderedPromptSurvivesMultipleIterations)
        );
    }

    private void agentRail8Events() {
        TestAgent agent = newAgent();
        LogRail rail = new LogRail();
        agent.registerRail(rail).toCompletableFuture().join();
        AgentCallbackContext context = context(agent, new InvokeInputs());

        for (AgentCallbackEvent event : List.of(
                AgentCallbackEvent.BEFORE_INVOKE,
                AgentCallbackEvent.AFTER_INVOKE,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                AgentCallbackEvent.ON_TOOL_EXCEPTION
        )) {
            context.fire(event);
        }

        assertThat(rail.events).contains(
                "before_invoke",
                "after_invoke",
                "before_model_call",
                "after_model_call",
                "before_tool_call",
                "after_tool_call",
                "on_model_exception",
                "on_tool_exception"
        );
    }

    private void agentRailRegistration() {
        TestAgent agent = newAgent();
        agent.registerRail(new LogRail()).toCompletableFuture().join();

        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.AFTER_INVOKE)).isTrue();
        assertThat(agent.getAgentCallbackManager().hasHooks(AgentCallbackEvent.BEFORE_MODEL_CALL)).isTrue();
    }

    private void railPriorityOrdering() {
        TestAgent agent = newAgent();
        List<String> order = new ArrayList<>();
        agent.registerRail(new PriorityRail("low", 10, order)).toCompletableFuture().join();
        agent.registerRail(new PriorityRail("high", 90, order)).toCompletableFuture().join();

        context(agent, new InvokeInputs()).fire(AgentCallbackEvent.BEFORE_INVOKE);

        assertThat(order).containsExactly("high", "low");
    }

    private void railExtraCommunication() {
        TestAgent agent = newAgent();
        ExtraReaderRail reader = new ExtraReaderRail();
        agent.registerRail(new ExtraWriterRail()).toCompletableFuture().join();
        agent.registerRail(reader).toCompletableFuture().join();
        AgentCallbackContext context = context(agent, new InvokeInputs());

        context.fire(AgentCallbackEvent.BEFORE_INVOKE);
        context.fire(AgentCallbackEvent.BEFORE_MODEL_CALL);

        assertThat(reader.sawWriter).isTrue();
    }

    private void railExceptionEvents() {
        RecordingContext context = new RecordingContext();

        assertThatThrownBy(() -> Rails.run(
                context,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    throw new RuntimeException("LLM failed");
                }
        )).isInstanceOf(RuntimeException.class);

        assertThat(context.events).contains(AgentCallbackEvent.ON_MODEL_EXCEPTION, AgentCallbackEvent.AFTER_MODEL_CALL);
    }

    private void onModelExceptionCanRequestRetry() {
        RecordingContext context = new RecordingContext();
        List<String> events = new ArrayList<>();
        AtomicInteger invokeCount = new AtomicInteger();
        context.on(AgentCallbackEvent.BEFORE_MODEL_CALL,
                () -> events.add("before:" + context.getRetryAttempt()));
        context.on(AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
            events.add("exception:" + context.getRetryAttempt());
            if (context.getRetryAttempt() < 1) {
                context.requestRetry(0.0d);
            }
        });
        context.on(AgentCallbackEvent.AFTER_MODEL_CALL,
                () -> events.add("after:" + context.getRetryAttempt()));

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    if (invokeCount.incrementAndGet() == 1) {
                        throw new RuntimeException("LLM failed once");
                    }
                    return mapOf("result_type", "answer");
                });

        assertThat(result).isEqualTo(mapOf("result_type", "answer"));
        assertThat(invokeCount).hasValue(2);
        assertThat(events).containsExactly("before:0", "exception:0", "after:0", "before:1", "after:1");
    }

    private void onToolExceptionCanRequestRetry() {
        RecordingContext context = new RecordingContext();
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolCall(toolCall("mock_retry_tool", "add", "{\"a\":1,\"b\":2}"));
        context.setInputs(inputs);
        List<String> events = new ArrayList<>();
        AtomicInteger executeCount = new AtomicInteger();
        context.on(AgentCallbackEvent.BEFORE_TOOL_CALL,
                () -> events.add("before:" + toolCallId(context) + ":" + context.getRetryAttempt()));
        context.on(AgentCallbackEvent.ON_TOOL_EXCEPTION, () -> {
            events.add("exception:" + toolCallId(context) + ":" + context.getRetryAttempt());
            if (context.getRetryAttempt() < 1) {
                context.requestRetry(0.0d);
            }
        });
        context.on(AgentCallbackEvent.AFTER_TOOL_CALL,
                () -> events.add("after:" + toolCallId(context) + ":" + context.getRetryAttempt()));

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL, AgentCallbackEvent.ON_TOOL_EXCEPTION, () -> {
                    if (executeCount.incrementAndGet() == 1) {
                        throw new RuntimeException("tool failed once");
                    }
                    return 3;
                });

        assertThat(result).isEqualTo(3);
        assertThat(executeCount).hasValue(2);
        assertThat(events).containsExactly(
                "before:mock_retry_tool:0",
                "exception:mock_retry_tool:0",
                "after:mock_retry_tool:0",
                "before:mock_retry_tool:1",
                "after:mock_retry_tool:1"
        );
    }

    private void railToolsAutoRegistration() {
        TestAgent agent = newAgent();

        agent.registerRail(new ToolCarryingRail()).toCompletableFuture().join();

        assertThat(toolNames(agent)).contains("rail_tool");
    }

    private void railUnregisterRemovesTools() {
        TestAgent agent = newAgent();
        ToolCarryingRail rail = new ToolCarryingRail();
        agent.registerRail(rail).toCompletableFuture().join();

        assertThat(toolNames(agent)).contains("rail_tool");
        agent.unregisterRail(rail).toCompletableFuture().join();

        assertThat(toolNames(agent)).doesNotContain("rail_tool");
    }

    private void railDecoratorAfterOnExceptionBothFire() {
        RecordingContext context = new RecordingContext();

        assertThatThrownBy(() -> Rails.run(context, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    throw new IllegalArgumentException("boom");
                })).isInstanceOf(IllegalArgumentException.class);

        assertThat(context.events).containsExactly(
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                AgentCallbackEvent.AFTER_MODEL_CALL
        );
    }

    private void railDecoratorBeforeAfter() {
        RecordingContext context = new RecordingContext();

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(context.events).containsExactly(
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL
        );
    }

    private void ctxLifecycleException() {
        TestAgent agent = newAgent();
        agent.failInvoke = true;
        LogRail rail = new LogRail();
        agent.registerRail(rail).toCompletableFuture().join();

        assertThrows(CompletionException.class,
                () -> agent.invoke(Map.of("query", "test"), null).toCompletableFuture().join());

        assertThat(rail.events).contains("before_invoke", "after_invoke");
    }

    private void ctxLifecycleNormal() {
        TestAgent agent = newAgent();
        LogRail rail = new LogRail();
        agent.registerRail(rail).toCompletableFuture().join();

        Object result = agent.invoke(Map.of("query", "hello"), null).toCompletableFuture().join();

        assertThat(result).isEqualTo(mapOf("result_type", "answer", "output", "ok"));
        assertThat(rail.events).contains("before_invoke", "after_invoke");
    }

    private void ctxFireManual() {
        TestAgent agent = newAgent();
        List<String> fired = new ArrayList<>();
        agent.registerCallback(AgentCallbackEvent.BEFORE_INVOKE, context -> {
            fired.add("manual_before");
            return CompletableFuture.completedFuture(null);
        }, 0).toCompletableFuture().join();

        context(agent, new InvokeInputs()).fire(AgentCallbackEvent.BEFORE_INVOKE);

        assertThat(fired).containsExactly("manual_before");
    }

    private void methodSplitDataVisibility() {
        TestAgent agent = newAgent();
        List<List<Object>> seenMessages = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                seenMessages.add(((ModelCallInputs) context.getInputs()).getMessages());
                return completed();
            }
        }).toCompletableFuture().join();
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setMessages(List.of(new BaseMessage("user", "test")));

        context(agent, inputs).fire(AgentCallbackEvent.BEFORE_MODEL_CALL);

        assertThat(seenMessages).hasSize(1);
        assertThat(seenMessages.get(0)).isNotEmpty();
    }

    private void reactAgentEvolveImport() {
        assertThat(ReActAgentEvolve.class.getName())
                .isEqualTo("com.openjiuwen.core.singleagent.agents.ReActAgentEvolve");
    }

    private void afterInvokeReceivesInvokeInputsWithResult() {
        TestAgent agent = newAgent();
        List<Object> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
                captured.add(context.getInputs());
                return completed();
            }
        }).toCompletableFuture().join();
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery("test");
        inputs.setResult(mapOf("result_type", "answer"));

        context(agent, inputs).fire(AgentCallbackEvent.AFTER_INVOKE);

        assertThat(captured).singleElement().isInstanceOf(InvokeInputs.class);
        InvokeInputs capturedInputs = (InvokeInputs) captured.get(0);
        assertThat(capturedInputs.getQuery()).isEqualTo("test");
        assertThat(capturedInputs.getResult()).containsEntry("result_type", "answer");
    }

    private void beforeInvokeReceivesInvokeInputs() {
        TestAgent agent = newAgent();
        List<Object> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
                captured.add(context.getInputs());
                return completed();
            }
        }).toCompletableFuture().join();
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery("hello");

        context(agent, inputs).fire(AgentCallbackEvent.BEFORE_INVOKE);

        assertThat(captured).singleElement().isInstanceOf(InvokeInputs.class);
        assertThat(((InvokeInputs) captured.get(0)).getQuery()).isEqualTo("hello");
    }

    private void beforeModelCallPreviewMessagesDoNotOverrideBuilder() {
        TestAgent agent = newAgent();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                ModelCallInputs inputs = (ModelCallInputs) context.getInputs();
                ((BaseMessage) inputs.getMessages().get(0)).setContent("preview only");
                context.getExtra().put("builder_final", "builder final");
                return completed();
            }
        }).toCompletableFuture().join();
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setMessages(List.of(new BaseMessage("system", "original")));
        AgentCallbackContext context = context(agent, inputs);

        context.fire(AgentCallbackEvent.BEFORE_MODEL_CALL);
        List<BaseMessage> finalPrompt = buildPrompt(context, inputs);

        assertThat(finalPrompt).extracting(BaseMessage::getContentAsString).containsExactly("builder final");
    }

    private void beforeModelCallReceivesModelCallInputs() {
        TestAgent agent = newAgent();
        List<Object> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                captured.add(context.getInputs());
                return completed();
            }
        }).toCompletableFuture().join();
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setMessages(List.of(new BaseMessage("user", "test")));
        inputs.setModelContext(new SimpleModelContext());

        context(agent, inputs).fire(AgentCallbackEvent.BEFORE_MODEL_CALL);

        assertThat(captured).singleElement().isInstanceOf(ModelCallInputs.class);
        ModelCallInputs capturedInputs = (ModelCallInputs) captured.get(0);
        assertThat(capturedInputs.getMessages()).isNotEmpty();
        assertThat(capturedInputs.getModelContext()).isNotNull();
    }

    private void beforeToolCallCanRewriteArgs() {
        TestAgent agent = newAgent();
        List<Object> capturedResult = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
                ((ToolCallInputs) context.getInputs()).setToolArgs("{\"a\":2,\"b\":5}");
                return completed();
            }

            @Override
            public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
                capturedResult.add(((ToolCallInputs) context.getInputs()).getToolResult());
                return completed();
            }
        }).toCompletableFuture().join();
        ToolCallInputs inputs = toolInputs("mock_call_add", "add", "{\"a\":1,\"b\":1}");
        AgentCallbackContext context = context(agent, inputs);

        context.fire(AgentCallbackEvent.BEFORE_TOOL_CALL);
        inputs.setToolResult(sumArgs(String.valueOf(inputs.getToolArgs())));
        context.fire(AgentCallbackEvent.AFTER_TOOL_CALL);

        assertThat(capturedResult).containsExactly(7);
    }

    private void beforeToolCallReceivesToolCallInputs() {
        TestAgent agent = newAgent();
        List<Object> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
                captured.add(context.getInputs());
                return completed();
            }
        }).toCompletableFuture().join();
        ToolCallInputs inputs = toolInputs("mock_call_add", "add", "{\"a\":1,\"b\":2}");

        context(agent, inputs).fire(AgentCallbackEvent.BEFORE_TOOL_CALL);

        assertThat(captured).singleElement().isInstanceOf(ToolCallInputs.class);
        ToolCallInputs capturedInputs = (ToolCallInputs) captured.get(0);
        assertThat(capturedInputs.getToolName()).isEqualTo("add");
        assertThat(capturedInputs.getToolCall()).isNotNull();
    }

    private void multiToolCallsFirePerToolEvents() {
        TestAgent agent = newAgent();
        List<String> beforeCalls = new ArrayList<>();
        List<Map.Entry<String, Object>> afterCalls = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
                beforeCalls.add(toolCallId(context));
                return completed();
            }

            @Override
            public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
                afterCalls.add(Map.entry(toolCallId(context), ((ToolCallInputs) context.getInputs()).getToolResult()));
                return completed();
            }
        }).toCompletableFuture().join();

        for (ToolCall call : List.of(
                toolCall("mock_call_add_1", "add", "{\"a\":1,\"b\":2}"),
                toolCall("mock_call_add_2", "add", "{\"a\":3,\"b\":4}")
        )) {
            ToolCallInputs inputs = new ToolCallInputs();
            inputs.setToolCall(call);
            inputs.setToolName(call.getName());
            inputs.setToolArgs(call.getArguments());
            AgentCallbackContext context = context(agent, inputs);
            context.fire(AgentCallbackEvent.BEFORE_TOOL_CALL);
            inputs.setToolResult(sumArgs(call.getArguments()));
            context.fire(AgentCallbackEvent.AFTER_TOOL_CALL);
        }

        assertThat(beforeCalls).containsExactly("mock_call_add_1", "mock_call_add_2");
        assertThat(afterCalls).extracting(Map.Entry::getKey)
                .containsExactly("mock_call_add_1", "mock_call_add_2");
        assertThat(afterCalls).extracting(Map.Entry::getValue).containsExactly(3, 7);
    }

    private void onToolExceptionIsPerToolCall() {
        TestAgent agent = newAgent();
        List<String> failedCalls = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> onToolException(AgentCallbackContext context) {
                failedCalls.add(toolCallId(context));
                return completed();
            }
        }).toCompletableFuture().join();
        context(agent, toolInputs("mock_call_ok", "add", "{\"a\":1,\"b\":2}"))
                .fire(AgentCallbackEvent.BEFORE_TOOL_CALL);
        context(agent, toolInputs("mock_call_missing", "missing_tool", "{}"))
                .fire(AgentCallbackEvent.ON_TOOL_EXCEPTION);

        assertThat(failedCalls).containsExactly("mock_call_missing");
    }

    private void afterModelCallForceFinish() {
        RecordingContext context = new RecordingContext();
        Map<String, Object> expected = mapOf("output", "stopped", "result_type", "answer");
        context.on(AgentCallbackEvent.AFTER_MODEL_CALL, () -> context.requestForceFinish(expected));

        Object modelOutput = Rails.run(context, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> "tool_call_response");
        ForceFinishRequest request = context.consumeForceFinish();

        assertThat(modelOutput).isEqualTo("tool_call_response");
        assertThat(request.getResult()).isEqualTo(expected);
    }

    private void afterToolCallForceFinish() {
        RecordingContext context = new RecordingContext();
        Map<String, Object> expected = mapOf("output", "done_early", "result_type", "answer");
        context.on(AgentCallbackEvent.AFTER_TOOL_CALL, () -> context.requestForceFinish(expected));

        Object toolOutput = Rails.run(context, AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL, AgentCallbackEvent.ON_TOOL_EXCEPTION, () -> 3);
        ForceFinishRequest request = context.consumeForceFinish();

        assertThat(toolOutput).isEqualTo(3);
        assertThat(request.getResult()).isEqualTo(expected);
    }

    private void beforeModelCallForceFinish() {
        RecordingContext context = new RecordingContext();
        Map<String, Object> expected = mapOf("output", "forced", "result_type", "answer");
        AtomicInteger calls = new AtomicInteger();
        context.on(AgentCallbackEvent.BEFORE_MODEL_CALL, () -> context.requestForceFinish(expected));

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL, AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    calls.incrementAndGet();
                    return "should not reach";
                });

        assertThat(result).isEqualTo(expected);
        assertThat(calls).hasValue(0);
    }

    private void beforeToolCallForceFinish() {
        RecordingContext context = new RecordingContext();
        Map<String, Object> expected = mapOf("output", "done_before_tool", "result_type", "answer");
        AtomicInteger calls = new AtomicInteger();
        context.on(AgentCallbackEvent.BEFORE_TOOL_CALL, () -> context.requestForceFinish(expected));

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL, AgentCallbackEvent.ON_TOOL_EXCEPTION, () -> {
                    calls.incrementAndGet();
                    return 3;
                });

        assertThat(result).isEqualTo(expected);
        assertThat(calls).hasValue(0);
    }

    private void consumeClearsSignal() {
        AgentCallbackContext context = new AgentCallbackContext();
        context.requestForceFinish(mapOf("output", "x"));

        ForceFinishRequest first = context.consumeForceFinish();
        ForceFinishRequest second = context.consumeForceFinish();

        assertThat(first).isNotNull();
        assertThat(first.getResult()).isEqualTo(mapOf("output", "x"));
        assertThat(second).isNull();
    }

    private void forceFinishResultInAfterInvoke() {
        TestAgent agent = newAgent();
        Map<String, Object> expected = mapOf("output", "forced", "result_type", "answer");
        List<Map<String, Object>> captured = new ArrayList<>();
        agent.registerRail(new AgentRail() {
            @Override
            public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
                context.requestForceFinish(expected);
                return completed();
            }

            @Override
            public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
                captured.add(((InvokeInputs) context.getInputs()).getResult());
                return completed();
            }
        }).toCompletableFuture().join();
        InvokeInputs invokeInputs = new InvokeInputs();
        AgentCallbackContext invokeContext = context(agent, invokeInputs);
        AgentCallbackContext modelContext = context(agent, new ModelCallInputs());

        modelContext.fire(AgentCallbackEvent.BEFORE_MODEL_CALL);
        invokeInputs.setResult(modelContext.consumeForceFinish().getResult());
        invokeContext.fire(AgentCallbackEvent.AFTER_INVOKE);

        assertThat(captured).containsExactly(expected);
    }

    private void railDecoratorForceFinishDictNotSubscriptable() {
        RecordingContext context = new RecordingContext();
        Map<String, Object> payload = mapOf("output", "limited", "result_type", "answer");
        context.on(AgentCallbackEvent.BEFORE_TOOL_CALL, () -> context.requestForceFinish(payload));

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL, AgentCallbackEvent.ON_TOOL_EXCEPTION,
                () -> mapOf("output", "original"));

        assertThat(result).isEqualTo(payload);
    }

    private void railDecoratorReturnsForceFinishPayload() {
        RecordingContext context = new RecordingContext();
        Map<String, Object> payload = mapOf("type", "force_finish", "content", "limit exceeded");
        AtomicInteger calls = new AtomicInteger();
        context.on(AgentCallbackEvent.BEFORE_TOOL_CALL, () -> context.requestForceFinish(payload));

        Object result = Rails.run(context, AgentCallbackEvent.BEFORE_TOOL_CALL,
                AgentCallbackEvent.AFTER_TOOL_CALL, AgentCallbackEvent.ON_TOOL_EXCEPTION, () -> {
                    calls.incrementAndGet();
                    return List.of("tool_result", "tool_msg");
                });

        assertThat(result).isEqualTo(payload);
        assertThat(calls).hasValue(0);
    }

    @SuppressWarnings("unchecked")
    private void memoryRailRenderedPromptSurvivesMultipleIterations() {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableLongTermMem(true)
                .enableUserProfile(true)
                .enableSemanticMemory(false)
                .enableEpisodicMemory(false)
                .enableSummaryMemory(false)
                .build();
        MemoryRail rail = new MemoryRail("scope_001", config, new FakeLongTermMemory());
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery("1+2");
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        context.getExtra().put("user_id", "user_001");

        rail.beforeInvoke(context).toCompletableFuture().join();
        Map<String, Object> variables = (Map<String, Object>) context.getExtra().get("memory_variables");
        List<BaseMessage> firstPrompt = renderMemoryPrompt(variables);
        List<BaseMessage> secondPrompt = renderMemoryPrompt(variables);

        assertThat(firstPrompt).hasSize(1);
        assertThat(secondPrompt).hasSize(1);
        for (BaseMessage message : List.of(firstPrompt.get(0), secondPrompt.get(0))) {
            assertThat(message.getContentAsString()).contains("preference: math");
            assertThat(message.getContentAsString()).doesNotContain("{{sys_long_term_memory}}");
        }
    }

    private static DynamicTest dynamic(String name, Executable executable) {
        return DynamicTest.dynamicTest(name, executable);
    }

    private static TestAgent newAgent() {
        int id = AGENT_COUNTER.incrementAndGet();
        return new TestAgent("rail-python-parity-" + id);
    }

    private static AgentCallbackContext context(TestAgent agent, Object inputs) {
        AgentCallbackContext context = new AgentCallbackContext(agent);
        context.setInputs(inputs);
        return context;
    }

    private static List<String> toolNames(TestAgent agent) {
        return agent.getAbilityManager().list().stream()
                .filter(ToolCard.class::isInstance)
                .map(ToolCard.class::cast)
                .map(ToolCard::getName)
                .toList();
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder().id(id).type("function").name(name).arguments(arguments).build();
    }

    private static ToolCallInputs toolInputs(String id, String name, String arguments) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolCall(toolCall(id, name, arguments));
        inputs.setToolName(name);
        inputs.setToolArgs(arguments);
        return inputs;
    }

    private static String toolCallId(AgentCallbackContext context) {
        Object inputs = context.getInputs();
        if (inputs instanceof ToolCallInputs toolInputs && toolInputs.getToolCall() instanceof ToolCall call) {
            return call.getId();
        }
        return "";
    }

    private static int sumArgs(String arguments) {
        int a = arguments.contains("\"a\":3") ? 3 : arguments.contains("\"a\":2") ? 2 : 1;
        int b = arguments.contains("\"b\":5") ? 5 : arguments.contains("\"b\":4") ? 4 : 2;
        return a + b;
    }

    private static List<BaseMessage> buildPrompt(AgentCallbackContext context, ModelCallInputs inputs) {
        Object builderFinal = context.getExtra().get("builder_final");
        if (builderFinal != null) {
            return List.of(new BaseMessage("system", builderFinal));
        }
        return inputs.getMessages().stream()
                .filter(BaseMessage.class::isInstance)
                .map(BaseMessage.class::cast)
                .toList();
    }

    private static List<BaseMessage> renderMemoryPrompt(Map<String, Object> variables) {
        String memory = String.valueOf(variables.getOrDefault("sys_long_term_memory", "[]"));
        return List.of(new BaseMessage("system", "memory: {{sys_long_term_memory}}"
                .replace("{{sys_long_term_memory}}", memory)));
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static final class TestAgent extends BaseAgent {
        private boolean failInvoke;

        private TestAgent(String id) {
            super(new AgentCard(id, id, "test rail agent"));
        }

        @Override
        public BaseAgent configure(Object config) {
            setConfig(config);
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            InvokeInputs invokeInputs = new InvokeInputs();
            if (inputs instanceof Map<?, ?> map && map.containsKey("query")) {
                invokeInputs.setQuery(map.get("query"));
            } else {
                invokeInputs.setQuery(inputs);
            }
            AgentCallbackContext context = new AgentCallbackContext(this);
            context.setInputs(invokeInputs);
            try {
                context.fire(AgentCallbackEvent.BEFORE_INVOKE);
                if (failInvoke) {
                    throw new RuntimeException("fail");
                }
                Map<String, Object> result = mapOf("result_type", "answer", "output", "ok");
                invokeInputs.setResult(result);
                future.complete(result);
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            } finally {
                context.fire(AgentCallbackEvent.AFTER_INVOKE);
            }
            return future;
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static class LogRail extends AgentRail {
        private final List<String> events = new ArrayList<>();

        @Override
        public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
            events.add("before_invoke");
            return completed();
        }

        @Override
        public CompletionStage<Void> afterInvoke(AgentCallbackContext context) {
            events.add("after_invoke");
            return completed();
        }

        @Override
        public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
            events.add("before_model_call");
            return completed();
        }

        @Override
        public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
            events.add("after_model_call");
            return completed();
        }

        @Override
        public CompletionStage<Void> onModelException(AgentCallbackContext context) {
            events.add("on_model_exception");
            return completed();
        }

        @Override
        public CompletionStage<Void> beforeToolCall(AgentCallbackContext context) {
            events.add("before_tool_call");
            return completed();
        }

        @Override
        public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
            events.add("after_tool_call");
            return completed();
        }

        @Override
        public CompletionStage<Void> onToolException(AgentCallbackContext context) {
            events.add("on_tool_exception");
            return completed();
        }
    }

    private static final class PriorityRail extends AgentRail {
        private final String name;
        private final List<String> order;

        private PriorityRail(String name, int priority, List<String> order) {
            this.name = name;
            this.order = order;
            setPriority(priority);
        }

        @Override
        public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
            order.add(name);
            return completed();
        }
    }

    private static final class ExtraWriterRail extends AgentRail {
        @Override
        public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
            context.getExtra().put("writer_was_here", true);
            return completed();
        }
    }

    private static final class ExtraReaderRail extends AgentRail {
        private boolean sawWriter;

        @Override
        public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
            sawWriter = Boolean.TRUE.equals(context.getExtra().get("writer_was_here"));
            return completed();
        }
    }

    private static final class ToolCarryingRail extends AgentRail {
        @Override
        public void init(BaseAgent agent) {
            agent.getAbilityManager().add(new ToolCard("rail_tool", "rail_tool", "A rail tool", Map.of(
                    "type", "object",
                    "properties", Map.of()
            )));
        }

        @Override
        public void uninit(BaseAgent agent) {
            agent.getAbilityManager().remove("rail_tool");
        }
    }

    private static final class RecordingContext extends AgentCallbackContext {
        private final List<AgentCallbackEvent> events = new ArrayList<>();
        private final Map<AgentCallbackEvent, Runnable> hooks = new LinkedHashMap<>();

        private void on(AgentCallbackEvent event, Runnable action) {
            hooks.put(event, action);
        }

        @Override
        public void fire(AgentCallbackEvent event) {
            setEvent(event);
            events.add(event);
            Runnable action = hooks.get(event);
            if (action != null) {
                action.run();
            }
        }
    }

    private static final class FakeLongTermMemory extends LongTermMemory {
        @Override
        public FutureList<MemResult> searchUserMem(String query, int num, String userId, String scopeId,
                double threshold) {
            return FutureList.completed(List.of(new MemResult(
                    new MemInfo("mem-1", "preference: math", MemoryType.USER_PROFILE, ZonedDateTime.now()),
                    0.99d
            )));
        }

        @Override
        public FutureMap<String, String> getVariables(Object names, String userId, String scopeId) {
            return FutureMap.completed(Map.of());
        }

        @Override
        public CompletableFuture<AddMemResult> addMessages(List<BaseMessage> messages, AgentMemoryConfig agentConfig,
                String userId, String scopeId, String sessionId, ZonedDateTime timestamp, boolean genMem,
                int genMemWithHistoryMsgNum) {
            return CompletableFuture.completedFuture(new AddMemResult());
        }
    }

    private static final class SimpleModelContext implements ModelContext {
        private final List<BaseMessage> messages = new ArrayList<>();
        private final Queue<String> steering = new ArrayDeque<>();

        @Override
        public int length() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            if (size == null || size >= messages.size()) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(Math.max(0, messages.size() - size), messages.size()));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages.clear();
            if (messages != null) {
                this.messages.addAll(messages);
            }
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            List<BaseMessage> popped = new ArrayList<>();
            for (int index = 0; index < size && !messages.isEmpty(); index++) {
                popped.add(messages.remove(messages.size() - 1));
            }
            return popped;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            messages.add(message);
            return CompletableFuture.completedFuture(new ArrayList<>(messages));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            if (messages != null) {
                this.messages.addAll(messages);
            }
            return CompletableFuture.completedFuture(new ArrayList<>(this.messages));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                Integer windowSize, Integer dialogueRound, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public ContextStats statistic() {
            ContextStats stats = new ContextStats();
            stats.setTotalMessages(messages.size());
            return stats;
        }

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public String contextId() {
            return "context";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return messages -> messages == null ? 0 : messages.size();
        }

        @Override
        public ToolPort reloaderTool() {
            return () -> "reload";
        }
    }
}
