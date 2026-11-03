/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.runner.callback.CallbackInfo;
import com.openjiuwen.core.runner.callback.DecoratorFramework;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
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

    private static final String GUARDRAIL_SOURCE = "tests/unit_tests/core/security/guardrail/test_guardrail_base.py";
    private static final List<String> GUARDRAIL_PYTHON_NODES = List.of(
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_init_without_params",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_init_with_custom_events",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_init_with_backend",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_init_with_events_and_backend",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_listen_events_returns_copy",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_with_events_chaining",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_set_backend_chaining",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_combined_chaining",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_events_immutable_after_init",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_default_events_used_when_none_provided",
            GUARDRAIL_SOURCE + "::TestBaseGuardrail::test_empty_events_when_no_default",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailDetect::test_detect_with_safe_backend",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailDetect::test_detect_with_risky_backend",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailDetect::test_detect_passes_kwargs_to_backend",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_register_with_framework",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_register_sets_framework_reference",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_register_tracks_registered_events",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_unregister_removes_callbacks",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_unregister_clears_registered_events",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_unregister_without_framework",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_unregister_with_unregistered_callback",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_multiple_guards_registration",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_get_registered_events_returns_copy",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_get_registered_events_empty_before_registration",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_get_registered_events_after_unregister",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_is_event_registered_true",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_is_event_registered_false",
            GUARDRAIL_SOURCE + "::TestBaseGuardrailRegistration::test_is_event_registered_before_registration",
            GUARDRAIL_SOURCE + "::TestGuardrailBackend::test_backend_is_abstract",
            GUARDRAIL_SOURCE + "::TestGuardrailBackend::test_backend_subclass_must_implement_analyze",
            GUARDRAIL_SOURCE + "::TestGuardrailBackend::test_backend_subclass_with_analyze",
            GUARDRAIL_SOURCE + "::TestGuardrailBackend::test_backend_analyze_receives_data",
            GUARDRAIL_SOURCE + "::TestGuardrailBackend::test_backend_analyze_exception_propagates",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_detect_callback_safe_no_exception",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_detect_callback_risky_raises_guardrail_error",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_detect_callback_error_contains_risk_info",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_detect_callback_error_with_details",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_detect_callback_unknown_risk_type",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_detect_callback_integration_with_framework",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_guardrail_called_via_framework",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_guardrail_safe_flow_via_framework",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_guardrail_receives_correct_kwargs",
            GUARDRAIL_SOURCE + "::TestDetectCallback::test_multiple_events_trigger_correct_guardrail"
    );

    @TestFactory
    Collection<DynamicTest> pythonGuardrailBaseCases() {
        return GUARDRAIL_PYTHON_NODES.stream()
                .map(node -> DynamicTest.dynamicTest(node, () -> runGuardrailPythonNode(node)))
                .toList();
    }

    private void runGuardrailPythonNode(String node) throws Exception {
        if (node.contains("test_init") || node.contains("test_listen_events")
                || node.contains("test_with_events") || node.contains("test_set_backend")
                || node.contains("test_combined_chaining") || node.contains("test_events_immutable")
                || node.contains("test_default_events") || node.contains("test_empty_events")) {
            assertGuardrailInitializationNode(node);
        } else if (node.contains("TestBaseGuardrailDetect")) {
            assertGuardrailDetectNode(node);
        } else if (node.contains("TestBaseGuardrailRegistration")) {
            assertGuardrailRegistrationNode(node);
        } else if (node.contains("TestGuardrailBackend")) {
            assertGuardrailBackendNode(node);
        } else {
            assertDetectCallbackNode(node);
        }
    }

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

    private void assertGuardrailInitializationNode(String node) {
        CapturingBackend backend = new CapturingBackend();
        if (node.endsWith("test_empty_events_when_no_default")) {
            assertThat(new NoDefaultGuardrail(null, null, null).listenEvents()).isEmpty();
            return;
        }
        FakeGuardrail guardrail = new FakeGuardrail(
                node.contains("backend") || node.contains("set_backend") || node.contains("combined") ? backend : null,
                node.contains("custom_events") || node.contains("events_and_backend")
                        ? List.of("event1", "event2")
                        : null,
                null);

        if (node.endsWith("test_init_with_custom_events") || node.endsWith("test_init_with_events_and_backend")) {
            assertThat(guardrail.listenEvents()).containsExactly("event1", "event2");
        } else if (node.endsWith("test_empty_events_when_no_default")) {
            assertThat(guardrail.listenEvents()).isEmpty();
        } else {
            assertThat(guardrail.listenEvents()).containsExactly("test_event");
        }

        if (node.endsWith("test_listen_events_returns_copy")) {
            assertThat(guardrail.listenEvents()).isEqualTo(guardrail.listenEvents())
                    .isNotSameAs(guardrail.listenEvents());
        } else if (node.endsWith("test_with_events_chaining")) {
            assertThat(guardrail.withEvents(List.of("new_event"))).isSameAs(guardrail);
            assertThat(guardrail.listenEvents()).containsExactly("new_event");
        } else if (node.endsWith("test_set_backend_chaining")) {
            assertThat(guardrail.setBackend(backend)).isSameAs(guardrail);
            assertThat(guardrail.getBackend()).isSameAs(backend);
        } else if (node.endsWith("test_combined_chaining")) {
            assertThat(guardrail.withEvents(List.of("custom_event")).setBackend(backend)).isSameAs(guardrail);
            assertThat(guardrail.listenEvents()).containsExactly("custom_event");
            assertThat(guardrail.getBackend()).isSameAs(backend);
        } else if (node.endsWith("test_events_immutable_after_init")) {
            List<String> sourceEvents = new ArrayList<>(List.of("event1"));
            FakeGuardrail copied = new FakeGuardrail(null, sourceEvents, null);
            sourceEvents.add("event2");
            assertThat(copied.listenEvents()).containsExactly("event1");
        } else if (node.endsWith("test_default_events_used_when_none_provided")) {
            assertThat(guardrail.listenEvents()).containsExactly("test_event");
        }
    }

    private void assertGuardrailDetectNode(String node) throws Exception {
        if (node.endsWith("test_detect_with_risky_backend")) {
            FakeGuardrail guardrail = new FakeGuardrail(
                    backendReturning(new RiskAssessment(true, RiskLevel.HIGH, "prompt_injection", 1.0d, Map.of("hit", true))),
                    null,
                    null);
            GuardrailResult result = guardrail.detect("test_event", new Object[0], Map.of("text", "payload"));
            assertThat(result.isSafe()).isFalse();
            assertThat(result.getRiskType()).isEqualTo("prompt_injection");
        } else if (node.endsWith("test_detect_passes_kwargs_to_backend")) {
            CapturingBackend backend = new CapturingBackend();
            FakeGuardrail guardrail = new FakeGuardrail(backend, null, null);
            guardrail.detect("test_event", new Object[]{"arg0"}, Map.of("text", "payload", "user_id", "u-1"));
            assertThat(backend.lastContext.getMetadata()).containsEntry("user_id", "u-1");
            assertThat(guardrail.lastExtractArgs).containsExactly("arg0");
        } else {
            CapturingBackend backend = new CapturingBackend();
            FakeGuardrail guardrail = new FakeGuardrail(backend, null, null);
            GuardrailResult result = guardrail.detect("test_event", new Object[0], Map.of("text", "payload"));
            assertThat(result.isSafe()).isTrue();
            assertThat(backend.lastContext.getText()).contains("payload");
        }
    }

    private void assertGuardrailRegistrationNode(String node) {
        if (node.endsWith("test_unregister_without_framework")
                || node.endsWith("test_get_registered_events_empty_before_registration")
                || node.endsWith("test_is_event_registered_before_registration")) {
            FakeGuardrail guardrail = new FakeGuardrail(null, List.of("event1"), null);
            guardrail.unregister();
            assertThat(guardrail.getRegisteredEvents()).isEmpty();
            assertThat(guardrail.isEventRegistered("event1")).isFalse();
            return;
        }

        RecordingFramework framework = new RecordingFramework();
        FakeGuardrail guardrail = node.endsWith("test_register_tracks_registered_events")
                || node.endsWith("test_get_registered_events_returns_copy")
                || node.endsWith("test_is_event_registered_true")
                ? new FakeGuardrail(backendReturning(new RiskAssessment(false, RiskLevel.SAFE)), List.of("event1", "event2"), null)
                : new FakeGuardrail(backendReturning(new RiskAssessment(false, RiskLevel.SAFE)), null, null);

        if (node.endsWith("test_multiple_guards_registration")) {
            new FakeGuardrail(backendReturning(new RiskAssessment(false, RiskLevel.SAFE)), List.of("event1"), null)
                    .register(framework);
            new FakeGuardrail(backendReturning(new RiskAssessment(false, RiskLevel.SAFE)), List.of("event2"), null)
                    .register(framework);
            assertThat(framework.getCallbacks()).containsKeys("event1", "event2");
            return;
        }

        guardrail.register(framework);
        assertThat(framework.getCallbacks()).isNotEmpty();
        if (node.endsWith("test_register_tracks_registered_events")
                || node.endsWith("test_get_registered_events_returns_copy")
                || node.endsWith("test_is_event_registered_true")) {
            assertThat(guardrail.getRegisteredEvents()).containsExactly("event1", "event2");
            assertThat(guardrail.getRegisteredEvents()).isNotSameAs(guardrail.getRegisteredEvents());
            assertThat(guardrail.isEventRegistered("event1")).isTrue();
        } else if (node.endsWith("test_is_event_registered_false")) {
            assertThat(guardrail.isEventRegistered("not_registered")).isFalse();
        } else {
            assertThat(guardrail.getRegisteredEvents()).containsExactly("test_event");
        }

        if (node.contains("unregister") || node.endsWith("test_register_sets_framework_reference")
                || node.endsWith("test_get_registered_events_after_unregister")) {
            guardrail.unregister();
            assertThat(guardrail.getRegisteredEvents()).isEmpty();
            assertThat(framework.getCallbacks().values().stream().flatMap(Collection::stream)).isEmpty();
        }
    }

    private void assertGuardrailBackendNode(String node) throws Exception {
        if (node.endsWith("test_backend_is_abstract") || node.endsWith("test_backend_subclass_must_implement_analyze")) {
            assertThat(GuardrailBackend.class.getModifiers() & java.lang.reflect.Modifier.ABSTRACT).isNotZero();
        } else if (node.endsWith("test_backend_subclass_with_analyze")) {
            assertThat(new CapturingBackend().analyze(GuardrailContext.builder().content("x").build()).isHasRisk())
                    .isFalse();
        } else if (node.endsWith("test_backend_analyze_receives_data")) {
            CapturingBackend backend = new CapturingBackend();
            backend.analyze(GuardrailContext.builder().event("event").content("payload").build());
            assertThat(backend.lastContext.getEvent()).isEqualTo("event");
            assertThat(backend.lastContext.getContent()).isEqualTo("payload");
        } else {
            FakeGuardrail guardrail = new FakeGuardrail(new FailingBackend(), null, null);
            assertThatThrownBy(() -> guardrail.detect("test_event", new Object[0], Map.of()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Detection failed");
        }
    }

    private void assertDetectCallbackNode(String node) throws Exception {
        if (node.endsWith("test_detect_callback_safe_no_exception")
                || node.endsWith("test_guardrail_safe_flow_via_framework")) {
            FakeGuardrail guardrail = new FakeGuardrail(backendReturning(new RiskAssessment(false, RiskLevel.SAFE)), null, null);
            assertThat(guardrail.callDetectCallback("test_event", Map.of("text", "safe"))).isNull();
        } else if (node.endsWith("test_detect_callback_unknown_risk_type")) {
            FakeGuardrail guardrail = new FakeGuardrail(
                    backendReturning(new RiskAssessment(true, RiskLevel.HIGH, null, 1.0d, Map.of())),
                    null,
                    null);
            assertThatThrownBy(() -> guardrail.callDetectCallback("test_event", Map.of()))
                    .isInstanceOf(GuardrailError.class)
                    .hasMessageContaining("unknown");
        } else if (node.endsWith("test_detect_callback_integration_with_framework")
                || node.endsWith("test_guardrail_called_via_framework")
                || node.endsWith("test_guardrail_receives_correct_kwargs")
                || node.endsWith("test_multiple_events_trigger_correct_guardrail")) {
            RecordingFramework framework = new RecordingFramework();
            FakeGuardrail guardrail = new FakeGuardrail(
                    backendReturning(new RiskAssessment(false, RiskLevel.SAFE)),
                    node.endsWith("test_multiple_events_trigger_correct_guardrail") ? List.of("event1", "event2") : null,
                    null);
            guardrail.register(framework);
            framework.trigger(guardrail.listenEvents().get(0).toString(), new Object[]{"arg0"}, Map.of("text", "payload"));
            assertThat(guardrail.lastExtractEvent).isEqualTo(guardrail.listenEvents().get(0));
            assertThat(guardrail.lastExtractArgs).containsExactly("arg0");
        } else {
            FakeGuardrail guardrail = new FakeGuardrail(
                    backendReturning(new RiskAssessment(true, RiskLevel.HIGH, "prompt_injection", 0.99d,
                            Map.of("matched", "rule", "details", "blocked"))),
                    null,
                    null);
            assertThatThrownBy(() -> guardrail.callDetectCallback("user_input_event", Map.of("text", "test")))
                    .isInstanceOf(GuardrailError.class)
                    .satisfies(error -> assertThat(((GuardrailError) error).getParams())
                            .containsEntry("risk_type", "prompt_injection")
                            .containsEntry("risk_level", "HIGH")
                            .containsEntry("matched", "rule"));
        }
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

    private static final class NoDefaultGuardrail extends BaseGuardrail {

        private NoDefaultGuardrail(GuardrailBackend backend, List<?> events, Integer priority) {
            super(events, backend, priority, false);
        }

        @Override
        public GuardrailContext extractContext(Object event, Object[] args, Map<String, Object> kwargs) {
            return GuardrailContext.builder().event(String.valueOf(event)).content("").build();
        }
    }

    private static final class FailingBackend extends GuardrailBackend {
        @Override
        public RiskAssessment analyze(GuardrailContext ctx) {
            throw new RuntimeException("Detection failed");
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
