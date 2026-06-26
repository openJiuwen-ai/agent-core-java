/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's callback utility module in
 * {@code openjiuwen/core/runner/callback/utils.py}.
 */
class CallbackUtilsTest {

    @AfterEach
    void resetFrameworkSupplier() {
        CallbackUtils.resetFrameworkSupplier();
    }

    @Test
    void triggerResolvesFrameworkAtCallTime() {
        RecordingFramework framework = new RecordingFramework();
        CallbackUtils.setCallbackFramework(framework);

        CallbackUtils.trigger("event", Map.of("value", 1));

        assertThat(framework.calls).hasSize(1);
        assertThat(framework.calls.get(0).event()).isEqualTo("event");
        assertThat(framework.calls.get(0).kwargs()).containsEntry("value", 1);
    }

    @Test
    void lazyEmitBeforeDoesNotResolveFrameworkUntilDecoratedFunctionRuns() {
        AtomicInteger resolutions = new AtomicInteger();
        RecordingFramework framework = new RecordingFramework();
        CallbackUtils.setFrameworkSupplier(() -> {
            resolutions.incrementAndGet();
            return framework;
        });

        Function<Function<Map<String, Object>, Object>, Function<Map<String, Object>, Object>> decorator =
                CallbackUtils.lazyCallbackFramework.emitBefore("before");
        Function<Map<String, Object>, Object> wrapped = decorator.apply(kwargs -> "ok");

        assertThat(resolutions).hasValue(0);

        Object result = wrapped.apply(Map.of("_args", new Object[]{"arg"}, "name", "demo"));

        assertThat(result).isEqualTo("ok");
        assertThat(resolutions).hasValue(1);
        assertThat(framework.calls.get(0).event()).isEqualTo("before");
        assertThat(framework.calls.get(0).args()).containsExactly("arg");
    }

    @Test
    void lazyEmitAfterDelegatesResultPayloadToCurrentFramework() {
        RecordingFramework first = new RecordingFramework();
        RecordingFramework second = new RecordingFramework();
        AtomicInteger resolutions = new AtomicInteger();
        CallbackUtils.setFrameworkSupplier(() -> resolutions.incrementAndGet() == 1 ? first : second);

        Function<Map<String, Object>, Object> wrapped = CallbackUtils.lazyCallbackFramework.emitAfter("after")
                .apply(kwargs -> "result-value");

        assertThat(wrapped.apply(Map.of())).isEqualTo("result-value");
        assertThat(wrapped.apply(Map.of())).isEqualTo("result-value");

        assertThat(first.calls).hasSize(1);
        assertThat(second.calls).hasSize(1);
        assertThat(first.calls.get(0).kwargs()).containsEntry("result", "result-value");
        assertThat(second.calls.get(0).kwargs()).containsEntry("result", "result-value");
    }

    @Test
    void defaultResolverFailsLikePythonWhenRunnerIsUnavailable() {
        assertThatThrownBy(CallbackUtils::getCallbackFramework)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Runner or callback_framework is not initialized");
    }

    private record RecordedCall(String event, Object[] args, Map<String, Object> kwargs) {
    }

    /**
     * Mirrors Python's {@code AsyncCallbackFramework} collaborator in
     * {@code openjiuwen/core/runner/callback/utils.py}.
     */
    private static final class RecordingFramework implements DecoratorFramework {
        private final List<RecordedCall> calls = new ArrayList<>();

        @Override
        public CallbackInfo registerSync(String event,
                                         Function<Map<String, Object>, Object> callback,
                                         int priority,
                                         boolean once,
                                         String namespace,
                                         Set<String> tags,
                                         List<EventFilter> filters,
                                         Function<Map<String, Object>, Object> rollbackHandler,
                                         Function<Map<String, Object>, Object> errorHandler,
                                         int maxRetries,
                                         double retryDelay,
                                         Double timeout,
                                         String callbackType) {
            return CallbackInfo.builder()
                    .callback(callback)
                    .priority(priority)
                    .once(once)
                    .namespace(namespace)
                    .tags(tags)
                    .maxRetries(maxRetries)
                    .retryDelay(retryDelay)
                    .timeout(timeout)
                    .callbackType(callbackType)
                    .build();
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            calls.add(new RecordedCall(event, args.clone(), new LinkedHashMap<>(kwargs)));
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            calls.add(new RecordedCall(event, args.clone(), new LinkedHashMap<>(kwargs)));
            return CallbackDecorators.TRANSFORM_NOOP;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return Map.of();
        }
    }
}
