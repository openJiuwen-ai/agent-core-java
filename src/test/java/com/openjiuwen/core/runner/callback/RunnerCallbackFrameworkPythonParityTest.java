/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_runner_callback_framework} in
 * {@code tests/unit_tests/core/runner/callback/test_runner_callback_framework.py}.
 */
class RunnerCallbackFrameworkPythonParityTest {

    @Test
    void runnerCallbackFrameworkProperty() {
        AsyncCallbackFramework framework = Runner.getCallbackFramework();

        assertThat(framework).isNotNull();
        assertThat(framework.getCallbacks()).isNotNull();
        assertThat(framework.getChains()).isNotNull();
    }

    @Test
    void registerAndTriggerCallback() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> callLog = new ArrayList<>();
        framework.on("test_event").apply(named("handler", kwargs -> {
            String message = String.valueOf(kwargs.get("message"));
            callLog.add("received: " + message);
            return "processed: " + message;
        }));

        List<Object> results = framework.triggerResults("test_event", Map.of("message", "hello"));

        assertThat(results).containsExactly("processed: hello");
        assertThat(callLog).containsExactly("received: hello");
    }

    @Test
    void multipleCallbacksRespectPriority() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        framework.on("event", 1, false, "default", null, null, 0, 0.0, null, "")
                .apply(named("low", kwargs -> {
                    executionOrder.add("low");
                    return "low_result";
                }));
        framework.on("event", 10, false, "default", null, null, 0, 0.0, null, "")
                .apply(named("high", kwargs -> {
                    executionOrder.add("high");
                    return "high_result";
                }));

        List<Object> results = framework.triggerResults("event");

        assertThat(results).containsExactly("high_result", "low_result");
        assertThat(executionOrder).containsExactly("high", "low");
    }

    @Test
    void callbackFrameworkFiltersCallbacks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        ValidationFilter validator = new ValidationFilter((args, kwargs) ->
                ((Integer) kwargs.getOrDefault("value", 0)) > 0);
        framework.on("event", 0, false, "default", null, List.of(validator), 0, 0.0, null, "")
                .apply(named("double", kwargs -> ((Integer) kwargs.get("value")) * 2));

        assertThat(framework.triggerResults("event", Map.of("value", 10))).containsExactly(20);
        assertThat(framework.triggerResults("event", Map.of("value", -5))).isEmpty();
    }

    @Test
    void rateLimitSkipsCallsBeyondLimit() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        RateLimitFilter rateLimit = new RateLimitFilter(2, 1.0);
        framework.on("event", 0, false, "default", null, List.of(rateLimit), 0, 0.0, null, "")
                .apply(named("counter", kwargs -> "ok"));

        assertThat(framework.triggerResults("event")).containsExactly("ok");
        assertThat(framework.triggerResults("event")).containsExactly("ok");
        assertThat(framework.triggerResults("event")).isEmpty();
    }

    @Test
    void emitBeforeDecoratorTriggersBeforeEvent() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> eventLog = new ArrayList<>();
        framework.on("before_event").apply(named("before", kwargs -> {
            eventLog.add("before");
            return null;
        }));
        Function<Map<String, Object>, Object> processData = framework.emitBefore("before_event", false, Map.of())
                .apply(named("process", kwargs -> {
                    eventLog.add("process: " + kwargs.get("data"));
                    return Map.of("result", kwargs.get("data"));
                }));

        Object result = processData.apply(Map.of("data", "test"));

        assertThat(result).isEqualTo(Map.of("result", "test"));
        assertThat(eventLog).containsExactly("before", "process: test");
    }

    @Test
    void triggerChainReturnsRollbackAction() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.register(
                "chain_event",
                named("rollbacker", kwargs -> ChainResult.builder()
                        .action(ChainAction.ROLLBACK)
                        .error(new Exception("fail"))
                        .build()),
                0,
                false,
                "default",
                Set.of(),
                List.of(),
                kwargs -> null,
                null,
                0,
                0.0,
                null,
                ""
        );

        ChainResult result = framework.triggerChain("chain_event", new Object[0], Map.of());

        assertThat(result.getAction()).isEqualTo(ChainAction.ROLLBACK);
    }

    @Test
    void hooksRunBeforeAndAfterCallbacks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        framework.addHook("event", HookType.BEFORE, kwargs -> executionOrder.add("before_hook"));
        framework.addHook("event", HookType.AFTER, kwargs -> executionOrder.add("after_hook"));
        framework.on("event").apply(named("callback", kwargs -> {
            executionOrder.add("callback");
            return "result";
        }));

        assertThat(framework.triggerResults("event")).containsExactly("result");
        assertThat(executionOrder).containsExactly("before_hook", "callback", "after_hook");
    }

    @Test
    void namespaceCallbacksShareEventButKeepMetadata() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.on("event", 0, false, "ns1", null, null, 0, 0.0, null, "")
                .apply(named("callback1", kwargs -> "ns1_result"));
        framework.on("event", 0, false, "ns2", null, null, 0, 0.0, null, "")
                .apply(named("callback2", kwargs -> "ns2_result"));

        List<Object> results = framework.triggerResults("event");

        assertThat(results).containsExactly("ns1_result", "ns2_result");
        assertThat(framework.listCallbacks("event"))
                .extracting(info -> info.get("namespace"))
                .containsExactly("ns1", "ns2");
    }

    @Test
    void unregisterRemovesOneCallback() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        Function<Map<String, Object>, Object> callback1 = named("callback1", kwargs -> "result1");
        Function<Map<String, Object>, Object> callback2 = named("callback2", kwargs -> "result2");
        framework.register("event", callback1, 0, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");
        framework.register("event", callback2, 0, false, "default", Set.of(), List.of(), null, null, 0, 0.0, null, "");

        framework.unregister("event", callback1);

        assertThat(framework.triggerResults("event")).containsExactly("result2");
    }

    @Test
    void unregisterDecoratorWrapperRemovesCallback() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        Function<Map<String, Object>, Object> callback1 = framework.on("event")
                .apply(named("callback1", kwargs -> "result1"));
        Function<Map<String, Object>, Object> callback2 = framework.on("event")
                .apply(named("callback2", kwargs -> "result2"));

        framework.unregister("event", callback1);
        assertThat(framework.triggerResults("event")).containsExactly("result2");
        framework.unregister("event", callback2);
        assertThat(framework.triggerResults("event")).isEmpty();
    }

    @Test
    void unregisterEventRemovesAllCallbacksForOnlyThatEvent() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.on("test_event").apply(named("callback1", kwargs -> "result1"));
        framework.on("test_event").apply(named("callback2", kwargs -> "result2"));
        framework.on("other_event").apply(named("callback3", kwargs -> "result3"));

        framework.unregisterEvent("test_event");

        assertThat(framework.triggerResults("test_event")).isEmpty();
        assertThat(framework.triggerResults("other_event")).containsExactly("result3");
    }

    @Test
    void unregisterEventRemovesEventFilters() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        ValidationFilter validator = new ValidationFilter((args, kwargs) ->
                ((Integer) kwargs.getOrDefault("value", 0)) > 0);
        framework.on("test_event", 0, false, "default", null, List.of(validator), 0, 0.0, null, "")
                .apply(named("double", kwargs -> ((Integer) kwargs.get("value")) * 2));
        framework.addFilter("test_event", validator);

        framework.unregisterEvent("test_event");

        assertThat(framework.triggerResults("test_event", Map.of("value", 10))).isEmpty();
    }

    @Test
    void unregisterEventRemovesChains() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.register(
                "chain_event",
                named("callback", kwargs -> ChainResult.builder().action(ChainAction.CONTINUE).build()),
                0,
                false,
                "default",
                Set.of(),
                List.of(),
                kwargs -> null,
                null,
                0,
                0.0,
                null,
                ""
        );

        framework.unregisterEvent("chain_event");

        assertThat(framework.getChains()).doesNotContainKey("chain_event");
        assertThat(framework.listCallbacks("chain_event")).isEmpty();
    }

    @Test
    void unregisterEventRemovesHooks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> executionOrder = new ArrayList<>();
        framework.on("test_event").apply(named("callback", kwargs -> {
            executionOrder.add("callback");
            return "result";
        }));
        framework.addHook("test_event", HookType.BEFORE, kwargs -> executionOrder.add("before"));
        framework.addHook("test_event", HookType.AFTER, kwargs -> executionOrder.add("after"));

        framework.unregisterEvent("test_event");
        framework.triggerResults("test_event");

        assertThat(executionOrder).isEmpty();
        assertThat(framework.listCallbacks("test_event")).isEmpty();
    }

    @Test
    void unregisterNonexistentEventDoesNotError() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();

        framework.unregisterEvent("nonexistent_event");

        assertThat(framework.listCallbacks("nonexistent_event")).isEmpty();
    }

    @Test
    void callbackTagsAreListed() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        framework.on("event", 0, false, "default", Set.of("debug", "test"), null, 0, 0.0, null, "")
                .apply(named("debugCallback", kwargs -> "debug_result"));
        framework.on("event", 0, false, "default", Set.of("production"), null, 0, 0.0, null, "")
                .apply(named("prodCallback", kwargs -> "prod_result"));

        List<Map<String, Object>> callbacks = framework.listCallbacks("event");
        List<String> tags = callbacks.stream()
                .flatMap(info -> ((List<?>) info.get("tags")).stream())
                .map(String::valueOf)
                .toList();

        assertThat(callbacks).hasSize(2);
        assertThat(tags).contains("debug", "test", "production");
    }

    @Test
    void emitAroundRunsStartProcessingEnd() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> eventLog = new ArrayList<>();
        framework.on("start").apply(named("onStart", kwargs -> {
            eventLog.add("start");
            return null;
        }));
        framework.on("end").apply(named("onEnd", kwargs -> {
            eventLog.add("end: " + kwargs.get("result"));
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitAround("start", "end", true, true, null)
                .apply(named("process", kwargs -> {
                    eventLog.add("processing");
                    return "done";
                }));

        Object result = process.apply(Map.of());

        assertThat(result).isEqualTo("done");
        assertThat(eventLog).containsExactly("start", "processing", "end: done");
    }

    @Test
    void emitAfterTriggersFollowupEvent() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<Object> receivedResults = new ArrayList<>();
        framework.on("data_ready").apply(named("onReady", kwargs -> {
            receivedResults.add(kwargs.get("result"));
            return null;
        }));
        Function<Map<String, Object>, Object> process = framework.emitAfter(
                "data_ready",
                "result",
                null,
                false,
                null,
                Map.of()
        ).apply(named("process", kwargs -> Map.of("status", "done")));

        Object result = process.apply(Map.of());

        assertThat(result).isEqualTo(Map.of("status", "done"));
        assertThat(receivedResults).containsExactly(Map.of("status", "done"));
    }

    @Test
    void triggerChainErrorHandlerCanRecover() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicReference<Exception> errorReceived = new AtomicReference<>();
        framework.register(
                "event",
                named("failingCallback", kwargs -> {
                    throw new IllegalArgumentException("Test error");
                }),
                0,
                false,
                "default",
                Set.of(),
                List.of(),
                null,
                kwargs -> {
                    errorReceived.set((Exception) kwargs.get("_error"));
                    return "recovered";
                },
                0,
                0.0,
                null,
                ""
        );

        ChainResult result = framework.triggerChain("event", new Object[0], Map.of());

        assertThat(result.getAction()).isEqualTo(ChainAction.CONTINUE);
        assertThat(result.getResult()).isEqualTo("recovered");
        assertThat(errorReceived.get()).isInstanceOf(IllegalArgumentException.class);
        assertThat(errorReceived.get()).hasMessage("Test error");
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
