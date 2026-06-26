/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's callback lifecycle hook tests in
 * {@code tests/unit_tests/core/runner/callback/test_framework_hooks.py}.
 */
class FrameworkHooksPythonParityTest {

    @Test
    void beforeHookExecutesBeforeCallback() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> executionOrder = new ArrayList<>();

        framework.on("event").apply(named("callback", kwargs -> {
            executionOrder.add("callback");
            return null;
        }));
        framework.addHook("event", HookType.BEFORE, kwargs -> executionOrder.add("before_hook"));

        framework.triggerResults("event");

        assertThat(executionOrder).containsExactly("before_hook", "callback");
    }

    @Test
    void beforeHookReceivesArgs() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicReference<Object[]> receivedArgs = new AtomicReference<>();
        AtomicReference<Map<String, Object>> receivedKwargs = new AtomicReference<>();

        framework.on("event").apply(named("callback", kwargs -> null));
        framework.addHook("event", HookType.BEFORE, kwargs -> {
            receivedArgs.set((Object[]) kwargs.get("_args"));
            receivedKwargs.set(userKwargs(kwargs));
        });

        framework.triggerResults("event", new Object[]{"arg1"}, Map.of("key", "value"));

        assertThat(receivedArgs.get()).containsExactly("arg1");
        assertThat(receivedKwargs.get()).containsExactlyEntriesOf(Map.of("key", "value"));
    }

    @Test
    void multipleBeforeHooksExecuteInOrder() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> order = new ArrayList<>();

        framework.on("event").apply(named("callback", kwargs -> {
            order.add("callback");
            return null;
        }));
        framework.addHook("event", HookType.BEFORE, kwargs -> order.add("hook1"));
        framework.addHook("event", HookType.BEFORE, kwargs -> order.add("hook2"));

        framework.triggerResults("event");

        assertThat(order).containsExactly("hook1", "hook2", "callback");
    }

    @Test
    void afterHookExecutesAfterCallback() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> executionOrder = new ArrayList<>();

        framework.addHook("event", HookType.AFTER, kwargs -> executionOrder.add("after_hook"));
        framework.on("event").apply(named("callback", kwargs -> {
            executionOrder.add("callback");
            return "result";
        }));

        framework.triggerResults("event");

        assertThat(executionOrder).containsExactly("callback", "after_hook");
    }

    @Test
    void afterHookReceivesResults() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicReference<List<?>> receivedResults = new AtomicReference<>();

        framework.addHook("event", HookType.AFTER, kwargs -> {
            Object[] args = (Object[]) kwargs.get("_args");
            receivedResults.set((List<?>) args[0]);
        });
        framework.on("event").apply(named("callback1", kwargs -> "result1"));
        framework.on("event").apply(named("callback2", kwargs -> "result2"));

        framework.triggerResults("event");

        assertThat(receivedResults.get()).isEqualTo(List.of("result1", "result2"));
    }

    @Test
    void errorHookExecutesWhenCallbackThrows() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicReference<Exception> errorReceived = new AtomicReference<>();

        framework.addHook("event", HookType.ERROR, kwargs -> errorReceived.set((Exception) kwargs.get("error")));
        framework.on("event").apply(named("failingCallback", kwargs -> {
            throw new IllegalArgumentException("Test error");
        }));

        framework.triggerResults("event");

        assertThat(errorReceived.get()).isInstanceOf(IllegalArgumentException.class).hasMessage("Test error");
    }

    @Test
    void errorHookReceivesOriginalArgs() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicReference<Object[]> receivedArgs = new AtomicReference<>();
        AtomicReference<Map<String, Object>> receivedKwargs = new AtomicReference<>();

        framework.addHook("event", HookType.ERROR, kwargs -> {
            receivedArgs.set((Object[]) kwargs.get("_args"));
            receivedKwargs.set(userKwargs(kwargs));
        });
        framework.on("event").apply(named("failingCallback", kwargs -> {
            throw new RuntimeException("Error!");
        }));

        framework.triggerResults("event", new Object[]{"arg1"}, Map.of("key", "value"));

        assertThat(receivedArgs.get()).containsExactly("arg1");
        assertThat(receivedKwargs.get()).containsEntry("key", "value").containsEntry("session", null);
    }

    @Test
    void errorHookCalledForEachError() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicInteger errorCount = new AtomicInteger();

        framework.addHook("event", HookType.ERROR, kwargs -> errorCount.incrementAndGet());
        framework.on("event", 10, false, "default", null, null, 0, 0.0, null, "")
                .apply(named("failing1", kwargs -> {
                    throw new IllegalArgumentException("Error 1");
                }));
        framework.on("event", 5, false, "default", null, null, 0, 0.0, null, "")
                .apply(named("failing2", kwargs -> {
                    throw new IllegalArgumentException("Error 2");
                }));

        framework.triggerResults("event");

        assertThat(errorCount.get()).isEqualTo(2);
    }

    @Test
    void cleanupHookInTriggerGenerator() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        List<String> executionOrder = new ArrayList<>();

        framework.on("stream").apply(named("generator", kwargs -> {
            executionOrder.add("generating");
            List<String> generated = List.of("item1", "item2");
            generated.forEach(item -> executionOrder.add("received: " + item));
            return generated;
        }));
        framework.addHook("stream", HookType.CLEANUP, kwargs -> executionOrder.add("cleanup"));

        Iterator<Object> items = framework.triggerGenerator("stream", new Object[0], Map.of());

        assertThat(toList(items)).containsExactly("item1", "item2");
        assertThat(executionOrder).contains("cleanup");
        assertThat(executionOrder.get(executionOrder.size() - 1)).isEqualTo("cleanup");
    }

    @Test
    void syncBeforeHookWorks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicBoolean called = new AtomicBoolean();

        framework.on("event").apply(named("callback", kwargs -> null));
        framework.addHook("event", HookType.BEFORE, kwargs -> called.set(true));

        framework.triggerResults("event");

        assertThat(called).isTrue();
    }

    @Test
    void syncAfterHookWorks() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicReference<List<?>> receivedResults = new AtomicReference<>();

        framework.addHook("event", HookType.AFTER, kwargs -> {
            Object[] args = (Object[]) kwargs.get("_args");
            receivedResults.set((List<?>) args[0]);
        });
        framework.on("event").apply(named("callback", kwargs -> "result"));

        framework.triggerResults("event");

        assertThat(receivedResults.get()).isEqualTo(List.of("result"));
    }

    @Test
    void hookExceptionDoesNotStopExecution() {
        AsyncCallbackFramework framework = new AsyncCallbackFramework();
        AtomicBoolean callbackExecuted = new AtomicBoolean();

        framework.addHook("event", HookType.BEFORE, kwargs -> {
            throw new RuntimeException("Hook failed!");
        });
        framework.on("event").apply(named("callback", kwargs -> {
            callbackExecuted.set(true);
            return null;
        }));

        framework.triggerResults("event");

        assertThat(callbackExecuted).isTrue();
    }

    @Test
    void afterHookExceptionLogged() {
        Logger logger = (Logger) LoggerFactory.getLogger(AsyncCallbackFramework.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AsyncCallbackFramework framework = new AsyncCallbackFramework();
            framework.addHook("event", HookType.AFTER, kwargs -> {
                throw new RuntimeException("Hook error!");
            });
            framework.on("event").apply(named("callback", kwargs -> "result"));

            framework.triggerResults("event");

            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getFormattedMessage())
                            .contains("Hook execution failed")
                            .contains("Hook error!"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static Map<String, Object> userKwargs(Map<String, Object> kwargs) {
        Map<String, Object> userKwargs = new LinkedHashMap<>(kwargs);
        Arrays.asList("_args", "_event", "_hook_type", "error").forEach(userKwargs::remove);
        return userKwargs;
    }

    /**
     * Mirrors Python's local callback functions in
     * {@code tests/unit_tests/core/runner/callback/test_framework_hooks.py}.
     */
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
