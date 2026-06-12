/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's guardrail-base unit coverage in
 * {@code tests/unit_tests/core/security/guardrail/test_guardrail_base.py}.
 */
class BaseGuardrailTest {

    @Test
    void usesSubclassDefaultsAndReturnsEventCopies() {
        FakeGuardrail guardrail = new FakeGuardrail(null, null, null);

        List<Object> eventsA = guardrail.listenEvents();
        List<Object> eventsB = guardrail.listenEvents();

        assertThat(eventsA).containsExactly("test_event");
        assertThat(eventsA).isEqualTo(eventsB).isNotSameAs(eventsB);
        assertThat(guardrail.getPriority()).isEqualTo(123);
        assertThat(guardrail.getNamespace()).isEqualTo("fake_guardrail");
    }

    @Test
    void detectExtractsContextAndDelegatesToBackend() throws Exception {
        CapturingBackend backend = new CapturingBackend();
        FakeGuardrail guardrail = new FakeGuardrail(backend, null, null);

        GuardrailResult result = guardrail.detect(
                "test_event",
                new Object[]{"arg0"},
                Map.of("text", "payload", "user_id", "u-1")
        );

        assertThat(result.isSafe()).isTrue();
        assertThat(backend.lastContext).isNotNull();
        assertThat(backend.lastContext.getEvent()).isEqualTo("test_event");
        assertThat(backend.lastContext.getText()).contains("payload");
        assertThat(backend.lastContext.getMetadata()).containsEntry("user_id", "u-1");
        assertThat(guardrail.lastExtractEvent).isEqualTo("test_event");
    }

    @Test
    void detectWithoutBackendRaises() {
        FakeGuardrail guardrail = new FakeGuardrail(null, null, null);

        assertThatThrownBy(() -> guardrail.detect("test_event", new Object[0], Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No backend configured");
    }

    @Test
    void detectCallbackRaisesGuardrailErrorOrAbortError() {
        FakeGuardrail highRiskGuardrail = new FakeGuardrail(
                backendReturning(new RiskAssessment(true, RiskLevel.HIGH, "prompt_injection", 1.0d, Map.of("matched", "rule"))),
                null,
                null
        );
        FakeGuardrail criticalGuardrail = new FakeGuardrail(
                backendReturning(new RiskAssessment(true, RiskLevel.CRITICAL, "critical_risk", 1.0d, Map.of("source", "tool"))),
                null,
                null
        );

        assertThatThrownBy(() -> highRiskGuardrail.callDetectCallback("tool_event", Map.of("text", "payload")))
                .isInstanceOf(GuardrailError.class)
                .satisfies(error -> {
                    GuardrailError guardrailError = (GuardrailError) error;
                    assertThat(guardrailError.getStatus()).isEqualTo(StatusCode.GUARDRAIL_BLOCKED);
                    assertThat(guardrailError.getParams())
                            .containsEntry("risk_type", "prompt_injection")
                            .containsEntry("risk_level", "HIGH")
                            .containsEntry("event", "tool_event")
                            .containsEntry("matched", "rule");
                });

        assertThatThrownBy(() -> criticalGuardrail.callDetectCallback("tool_event", Map.of("text", "payload")))
                .isInstanceOf(AbortError.class)
                .hasMessageContaining("Critical security risk detected: critical_risk");
    }

    @Test
    void registerAndUnregisterTrackCallbacksAgainstFramework() {
        RecordingFramework framework = new RecordingFramework();
        FakeGuardrail guardrail = new FakeGuardrail(backendReturning(new RiskAssessment(false, RiskLevel.SAFE)), null, null);

        guardrail.register(framework);

        assertThat(framework.getCallbacks()).containsKey("test_event");
        assertThat(framework.getCallbacks().get("test_event")).hasSize(1);
        assertThat(guardrail.getRegisteredEvents()).containsExactly("test_event");
        assertThat(guardrail.isEventRegistered("test_event")).isTrue();

        framework.trigger("test_event", new Object[]{"arg0"}, Map.of("text", "payload"));
        assertThat(guardrail.lastExtractEvent).isEqualTo("test_event");
        assertThat(guardrail.lastExtractArgs).containsExactly("arg0");

        guardrail.unregister();

        assertThat(framework.getCallbacks().getOrDefault("test_event", List.of())).isEmpty();
        assertThat(guardrail.getRegisteredEvents()).isEmpty();
    }

    private static GuardrailBackend backendReturning(RiskAssessment assessment) {
        return new GuardrailBackend() {
            @Override
            public RiskAssessment analyze(GuardrailContext ctx) {
                return assessment;
            }
        };
    }

    private static final class FakeGuardrail extends BaseGuardrail {

        public static final List<Object> DEFAULT_EVENTS = List.of("test_event");
        public static final int DEFAULT_PRIORITY = 123;
        public static final String NAMESPACE = "fake_guardrail";

        private Object lastExtractEvent;
        private Object[] lastExtractArgs = new Object[0];

        private FakeGuardrail(GuardrailBackend backend, List<?> events, Integer priority) {
            super(events, backend, priority, false);
        }

        @Override
        public GuardrailContext extractContext(Object event, Object[] args, Map<String, Object> kwargs) {
            this.lastExtractEvent = event;
            this.lastExtractArgs = args != null ? args.clone() : new Object[0];
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (kwargs.containsKey("user_id")) {
                metadata.put("user_id", kwargs.get("user_id"));
            }
            return GuardrailContext.builder()
                    .contentType(GuardrailContentType.TEXT)
                    .content(String.valueOf(kwargs.getOrDefault("text", "")))
                    .event(String.valueOf(event))
                    .metadata(metadata)
                    .build();
        }

        private Object callDetectCallback(Object event, Map<String, Object> kwargs) throws Exception {
            return detectCallback(event, new Object[0], kwargs);
        }
    }

    private static final class CapturingBackend extends GuardrailBackend {
        private GuardrailContext lastContext;

        @Override
        public RiskAssessment analyze(GuardrailContext ctx) {
            this.lastContext = ctx;
            return new RiskAssessment(false, RiskLevel.SAFE, null, 1.0d, Map.of("seen", true));
        }
    }

    private static final class RecordingFramework implements DecoratorFramework {
        private final Map<String, List<CallbackInfo>> callbacks = new LinkedHashMap<>();

        @Override
        public CallbackInfo registerSync(
                String event,
                Function<Map<String, Object>, Object> callback,
                int priority,
                boolean once,
                String namespace,
                Set<String> tags,
                List<com.openjiuwen.core.runner.callback.EventFilter> filters,
                Function<Map<String, Object>, Object> rollbackHandler,
                Function<Map<String, Object>, Object> errorHandler,
                int maxRetries,
                double retryDelay,
                Double timeout,
                String callbackType) {
            CallbackInfo info = CallbackInfo.builder()
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
            callbacks.computeIfAbsent(event, ignored -> new ArrayList<>()).add(info);
            return info;
        }

        @Override
        public void trigger(String event, Object[] args, Map<String, Object> kwargs) {
            Map<String, Object> merged = new LinkedHashMap<>();
            if (kwargs != null) {
                merged.putAll(kwargs);
            }
            merged.put("_args", args != null ? args.clone() : new Object[0]);

            for (CallbackInfo info : callbacks.getOrDefault(event, List.of())) {
                if (info.isEnabled()) {
                    info.getCallback().apply(merged);
                }
            }
        }

        @Override
        public Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs) {
            return null;
        }

        @Override
        public Map<String, List<CallbackInfo>> getCallbacks() {
            return callbacks;
        }
    }
}
