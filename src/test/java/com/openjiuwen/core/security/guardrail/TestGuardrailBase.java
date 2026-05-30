/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.common.exception.GuardrailError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GuardrailBase.
 * Mirrors Python's tests/unit_tests/core/security/guardrail/test_guardrail_base.py
 */
class TestGuardrailBase {

    private CustomTestGuardrail guardrail;
    private GuardrailBackend mockBackend;
    private GuardrailBackend riskyBackend;
    private GuardrailBackend riskyBackendWithDetails;
    private CallbackFramework framework;

    @BeforeEach
    void setUp() {
        mockBackend = GuardrailTestConfig.mockBackend();
        riskyBackend = GuardrailTestConfig.riskyBackend();
        riskyBackendWithDetails = GuardrailTestConfig.riskyBackendWithDetails();
        framework = GuardrailTestConfig.framework();
    }

    @Nested
    @DisplayName("TestBaseGuardrail")
    class TestBaseGuardrailTests {

        @Test
        @DisplayName("test init without params")
        void testInitWithoutParams() {
            guardrail = new CustomTestGuardrail(null, null);
            assertEquals(Arrays.asList("test_event"), guardrail.listenEvents());
        }

        @Test
        @DisplayName("test init with custom events")
        void testInitWithCustomEvents() {
            guardrail = new CustomTestGuardrail(null, Arrays.asList("event1", "event2"));
            assertEquals(Arrays.asList("event1", "event2"), guardrail.listenEvents());
        }

        @Test
        @DisplayName("test init with backend")
        void testInitWithBackend() {
            guardrail = new CustomTestGuardrail(mockBackend, null);
            assertSame(mockBackend, guardrail.getBackend());
        }

        @Test
        @DisplayName("test init with events and backend")
        void testInitWithEventsAndBackend() {
            guardrail = new CustomTestGuardrail(mockBackend, Arrays.asList("custom_event"));
            assertEquals(Arrays.asList("custom_event"), guardrail.listenEvents());
            assertSame(mockBackend, guardrail.getBackend());
        }

        @Test
        @DisplayName("test listen events returns copy")
        void testListenEventsReturnsCopy() {
            guardrail = new CustomTestGuardrail(null, null);
            List<String> events1 = guardrail.listenEvents();
            List<String> events2 = guardrail.listenEvents();
            assertNotSame(events1, events2);
            assertEquals(events1, events2);
        }

        @Test
        @DisplayName("test with events chaining")
        void testWithEventsChaining() {
            guardrail = new CustomTestGuardrail(null, null);
            BaseGuardrail result = guardrail.withEvents(Arrays.asList("new_event"));
            assertSame(guardrail, result);
            assertEquals(Arrays.asList("new_event"), guardrail.listenEvents());
        }

        @Test
        @DisplayName("test set backend chaining")
        void testSetBackendChaining() {
            guardrail = new CustomTestGuardrail(null, null);
            BaseGuardrail result = guardrail.setBackend(mockBackend);
            assertSame(guardrail, result);
            assertSame(mockBackend, guardrail.getBackend());
        }

        @Test
        @DisplayName("test combined chaining")
        void testCombinedChaining() {
            guardrail = new CustomTestGuardrail(null, null);
            BaseGuardrail result = guardrail
                    .withEvents(Arrays.asList("custom_event"))
                    .setBackend(mockBackend);
            assertSame(guardrail, result);
            assertEquals(Arrays.asList("custom_event"), guardrail.listenEvents());
            assertSame(mockBackend, guardrail.getBackend());
        }

        @Test
        @DisplayName("test events immutable after init")
        void testEventsImmutableAfterInit() {
            List<String> originalEvents = new ArrayList<>(Arrays.asList("event1", "event2"));
            guardrail = new CustomTestGuardrail(null, originalEvents);
            originalEvents.add("event3");
            assertEquals(Arrays.asList("event1", "event2"), guardrail.listenEvents());
        }

        @Test
        @DisplayName("test default events used when none provided")
        void testDefaultEventsUsedWhenNoneProvided() {
            guardrail = new CustomTestGuardrail(null, null);
            assertEquals(Arrays.asList("test_event"), guardrail.listenEvents());
        }

        @Test
        @DisplayName("test empty events when no default")
        void testEmptyEventsWhenNoDefault() {
            NoDefaultEventsGuardrail noDefaultGuardrail = new NoDefaultEventsGuardrail();
            assertEquals(new ArrayList<>(), noDefaultGuardrail.listenEvents());
        }
    }

    @Nested
    @DisplayName("TestBaseGuardrailDetect")
    class TestBaseGuardrailDetectTests {

        @Test
        @DisplayName("test detect without backend raises")
        void testDetectWithoutBackendRaises() {
            DirectBaseCallGuardrail directGuardrail = new DirectBaseCallGuardrail();
            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                directGuardrail.detect("test_event", new Object[0], new HashMap<>());
            });
            assertTrue(exception.getMessage().contains("No backend configured"));
        }

        @Test
        @DisplayName("test detect with safe backend")
        void testDetectWithSafeBackend() throws Exception {
            guardrail = new CustomTestGuardrail(mockBackend, null);
            GuardrailResult result = guardrail.detect("test_event", new Object[0], createKwargs("test", "value"));
            assertNotNull(result);
            assertTrue(result.isSafe());
            assertEquals(RiskLevel.SAFE, result.getRiskLevel());
        }

        @Test
        @DisplayName("test detect with risky backend")
        void testDetectWithRiskyBackend() throws Exception {
            guardrail = new CustomTestGuardrail(riskyBackend, null);
            GuardrailResult result = guardrail.detect("test_event", new Object[0], createKwargs("test", "value"));
            assertNotNull(result);
            assertFalse(result.isSafe());
            assertEquals(RiskLevel.HIGH, result.getRiskLevel());
            assertEquals("test_risk", result.getRiskType());
        }

        @Test
        @DisplayName("test detect passes kwargs to backend")
        void testDetectPassesKwargsToBackend() throws Exception {
            DataCaptureGuardrail dataCaptureGuardrail = new DataCaptureGuardrail(mockBackend);
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("text", "test content");
            kwargs.put("user_id", "123");
            GuardrailResult result = dataCaptureGuardrail.detect("test_event", new Object[0], kwargs);
            assertNotNull(result);
            assertNotNull(dataCaptureGuardrail.capturedData);
            assertEquals("test content", dataCaptureGuardrail.capturedData.get("text"));
            assertEquals("123", dataCaptureGuardrail.capturedData.get("user_id"));
        }
    }

    @Nested
    @DisplayName("TestBaseGuardrailRegistration")
    class TestBaseGuardrailRegistrationTests {

        @Test
        @DisplayName("test register with framework")
        void testRegisterWithFramework() {
            guardrail = new CustomTestGuardrail(null, null);
            guardrail.register(framework);
            List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");
            assertEquals(1, callbacks.size());
            guardrail.unregister();
        }

        @Test
        @DisplayName("test register sets framework reference")
        void testRegisterSetsFrameworkReference() {
            guardrail = new CustomTestGuardrail(null, null);
            guardrail.register(framework);
            guardrail.unregister();
            List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");
            assertEquals(0, callbacks.size());
        }

        @Test
        @DisplayName("test register tracks registered events")
        void testRegisterTracksRegisteredEvents() {
            guardrail = new CustomTestGuardrail(null, Arrays.asList("event1", "event2"));
            guardrail.register(framework);
            List<Map<String, Object>> callbacks1 = framework.listCallbacks("event1");
            List<Map<String, Object>> callbacks2 = framework.listCallbacks("event2");
            assertEquals(1, callbacks1.size());
            assertEquals(1, callbacks2.size());
            guardrail.unregister();
        }

        @Test
        @DisplayName("test unregister removes callbacks")
        void testUnregisterRemovesCallbacks() {
            guardrail = new CustomTestGuardrail(null, null);
            guardrail.register(framework);
            guardrail.unregister();
            List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");
            assertEquals(0, callbacks.size());
        }

        @Test
        @DisplayName("test unregister clears registered events")
        void testUnregisterClearsRegisteredEvents() {
            guardrail = new CustomTestGuardrail(null, null);
            guardrail.register(framework);
            guardrail.unregister();
            List<Map<String, Object>> callbacks = framework.listCallbacks("test_event");
            assertEquals(0, callbacks.size());
        }

        @Test
        @DisplayName("test unregister without framework")
        void testUnregisterWithoutFramework() {
            guardrail = new CustomTestGuardrail(null, null);
            assertDoesNotThrow(() -> guardrail.unregister());
        }

        @Test
        @DisplayName("test unregister with unregistered callback")
        void testUnregisterWithUnregisteredCallback() {
            guardrail = new CustomTestGuardrail(null, null);
            guardrail.setFramework(framework);
            guardrail.addRegisteredEvent("test_event");
            assertDoesNotThrow(() -> guardrail.unregister());
        }

        @Test
        @DisplayName("test multiple guards registration")
        void testMultipleGuardsRegistration() {
            CustomTestGuardrail guardrail1 = new CustomTestGuardrail(null, Arrays.asList("event1"));
            CustomTestGuardrail guardrail2 = new CustomTestGuardrail(null, Arrays.asList("event2"));
            guardrail1.register(framework);
            guardrail2.register(framework);
            List<Map<String, Object>> callbacks1 = framework.listCallbacks("event1");
            List<Map<String, Object>> callbacks2 = framework.listCallbacks("event2");
            assertEquals(1, callbacks1.size());
            assertEquals(1, callbacks2.size());
            guardrail1.unregister();
            guardrail2.unregister();
        }

        @Test
        @DisplayName("test get registered events returns copy")
        void testGetRegisteredEventsReturnsCopy() {
            guardrail = new CustomTestGuardrail(null, Arrays.asList("event1", "event2"));
            guardrail.register(framework);
            List<String> events1 = guardrail.getRegisteredEvents();
            List<String> events2 = guardrail.getRegisteredEvents();
            assertNotSame(events1, events2);
            assertEquals(events1, events2);
            guardrail.unregister();
        }

        @Test
        @DisplayName("test get registered events empty before registration")
        void testGetRegisteredEventsEmptyBeforeRegistration() {
            guardrail = new CustomTestGuardrail(null, null);
            assertEquals(new ArrayList<>(), guardrail.getRegisteredEvents());
        }

        @Test
        @DisplayName("test get registered events after unregister")
        void testGetRegisteredEventsAfterUnregister() {
            guardrail = new CustomTestGuardrail(null, null);
            guardrail.register(framework);
            guardrail.unregister();
            assertEquals(new ArrayList<>(), guardrail.getRegisteredEvents());
        }

        @Test
        @DisplayName("test is event registered true")
        void testIsEventRegisteredTrue() {
            guardrail = new CustomTestGuardrail(null, Arrays.asList("event1", "event2"));
            guardrail.register(framework);
            assertTrue(guardrail.isEventRegistered("event1"));
            assertTrue(guardrail.isEventRegistered("event2"));
            guardrail.unregister();
        }

        @Test
        @DisplayName("test is event registered false")
        void testIsEventRegisteredFalse() {
            guardrail = new CustomTestGuardrail(null, Arrays.asList("event1"));
            guardrail.register(framework);
            assertTrue(guardrail.isEventRegistered("event1"));
            assertFalse(guardrail.isEventRegistered("not_registered"));
            guardrail.unregister();
        }

        @Test
        @DisplayName("test is event registered before registration")
        void testIsEventRegisteredBeforeRegistration() {
            guardrail = new CustomTestGuardrail(null, Arrays.asList("event1"));
            assertFalse(guardrail.isEventRegistered("event1"));
        }
    }

    @Nested
    @DisplayName("TestGuardrailBackend")
    class TestGuardrailBackendTests {

        @Test
        @DisplayName("test backend is functional interface")
        void testBackendIsFunctionalInterface() {
            assertTrue(GuardrailBackend.class.isAnnotationPresent(FunctionalInterface.class));
        }

        @Test
        @DisplayName("test backend subclass with analyze")
        void testBackendSubclassWithAnalyze() {
            GuardrailBackend backend = data -> RiskAssessment.builder()
                    .hasRisk(false)
                    .riskLevel(RiskLevel.SAFE)
                    .build();
            assertNotNull(backend);
        }

        @Test
        @DisplayName("test backend analyze receives data")
        void testBackendAnalyzeReceivesData() throws Exception {
            final Map<String, Object>[] receivedData = new Map[]{null};
            GuardrailBackend dataCaptureBackend = data -> {
                receivedData[0] = data;
                return RiskAssessment.builder()
                        .hasRisk(false)
                        .riskLevel(RiskLevel.SAFE)
                        .build();
            };
            Map<String, Object> testData = new HashMap<>();
            testData.put("text", "test");
            testData.put("user_id", "123");
            dataCaptureBackend.analyze(testData);
            assertEquals(testData, receivedData[0]);
        }

        @Test
        @DisplayName("test backend analyze exception propagates")
        void testBackendAnalyzeExceptionPropagates() {
            GuardrailBackend failingBackend = data -> {
                throw new RuntimeException("Detection failed");
            };
            guardrail = new CustomTestGuardrail(failingBackend, null);
            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                guardrail.callDetectCallback("test_event", new HashMap<>());
            });
            assertEquals("Detection failed", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("TestDetectCallback")
    class TestDetectCallbackTests {

        @Test
        @DisplayName("test detect callback safe no exception")
        void testDetectCallbackSafeNoException() throws Exception {
            guardrail = new CustomTestGuardrail(mockBackend, null);
            Object result = guardrail.callDetectCallback("test_event", createKwargs("test", "value"));
            assertNull(result);
        }

        @Test
        @DisplayName("test detect callback risky raises guardrail error")
        void testDetectCallbackRiskyRaisesGuardrailError() {
            guardrail = new CustomTestGuardrail(riskyBackend, null);
            GuardrailError error = assertThrows(GuardrailError.class, () -> {
                guardrail.callDetectCallback("test_event", createKwargs("test", "value"));
            });
            assertEquals(StatusCode.GUARDRAIL_BLOCKED, error.getStatus());
        }

        @Test
        @DisplayName("test detect callback error contains risk info")
        void testDetectCallbackErrorContainsRiskInfo() {
            guardrail = new CustomTestGuardrail(riskyBackend, null);
            GuardrailError error = assertThrows(GuardrailError.class, () -> {
                guardrail.callDetectCallback("user_input_event", createKwargs("text", "test"));
            });
            assertNotNull(error.getParams());
            assertEquals("test_risk", error.getParams().get("risk_type"));
            assertEquals("HIGH", error.getParams().get("risk_level"));
            assertEquals("user_input_event", error.getParams().get("event"));
        }

        @Test
        @DisplayName("test detect callback error with details")
        void testDetectCallbackErrorWithDetails() {
            guardrail = new CustomTestGuardrail(riskyBackendWithDetails, null);
            GuardrailError error = assertThrows(GuardrailError.class, () -> {
                guardrail.callDetectCallback("llm_input_event", createKwargs("prompt", "test"));
            });
            assertNotNull(error.getParams());
            assertEquals("prompt_injection", error.getParams().get("risk_type"));
            assertEquals("HIGH", error.getParams().get("risk_level"));
            assertEquals("llm_input_event", error.getParams().get("event"));
            assertTrue(error.getParams().containsKey("matched_pattern"));
            assertEquals("ignore previous instructions", error.getParams().get("matched_pattern"));
            assertTrue(error.getParams().containsKey("confidence"));
            assertEquals(0.95, error.getParams().get("confidence"));
        }

        @Test
        @DisplayName("test detect callback unknown risk type")
        void testDetectCallbackUnknownRiskType() {
            GuardrailBackend unknownRiskBackend = data -> RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.MEDIUM)
                    .riskType(null)
                    .build();
            guardrail = new CustomTestGuardrail(unknownRiskBackend, null);
            GuardrailError error = assertThrows(GuardrailError.class, () -> {
                guardrail.callDetectCallback("test_event", new HashMap<>());
            });
            assertEquals("unknown", error.getParams().get("risk_type"));
            assertEquals("MEDIUM", error.getParams().get("risk_level"));
        }

        @Test
        @DisplayName("test detect callback integration with framework")
        void testDetectCallbackIntegrationWithFramework() {
            guardrail = new CustomTestGuardrail(riskyBackend, null);
            guardrail.register(framework);
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("text", "malicious input");
            List<Object> results = framework.trigger("test_event", kwargs);
            assertTrue(results.isEmpty());
            guardrail.unregister();
        }
    }

    @Nested
    @DisplayName("TestGuardrailIntegration")
    class TestGuardrailIntegrationTests {

        @Test
        @DisplayName("test guardrail called via framework")
        void testGuardrailCalledViaFramework() {
            SpyGuardrail spyGuardrail = new SpyGuardrail(null, null);
            spyGuardrail.register(framework);
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("text", "test input");
            kwargs.put("user_id", "123");
            framework.trigger("test_event", kwargs);
            assertTrue(spyGuardrail.detectCalled);
            assertEquals("test_event", spyGuardrail.detectedEventName);
            assertTrue(spyGuardrail.detectedKwargs.containsKey("text"));
            assertEquals("test input", spyGuardrail.detectedKwargs.get("text"));
            spyGuardrail.unregister();
        }

        @Test
        @DisplayName("test guardrail receives correct kwargs")
        void testGuardrailReceivesCorrectKwargs() {
            SpyGuardrail spyGuardrail = new SpyGuardrail(null, null);
            spyGuardrail.register(framework);
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("prompt", "test prompt");
            kwargs.put("user_id", "user123");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", "web");
            kwargs.put("metadata", metadata);
            framework.trigger("test_event", kwargs);
            assertEquals("test prompt", spyGuardrail.detectedKwargs.get("prompt"));
            assertEquals("user123", spyGuardrail.detectedKwargs.get("user_id"));
            assertEquals(metadata, spyGuardrail.detectedKwargs.get("metadata"));
            spyGuardrail.unregister();
        }

        @Test
        @DisplayName("test multiple events trigger correct guardrail")
        void testMultipleEventsTriggerCorrectGuardrail() {
            SpyGuardrail spyGuardrail1 = new SpyGuardrail(null, Arrays.asList("event1"));
            SpyGuardrail spyGuardrail2 = new SpyGuardrail(null, Arrays.asList("event2"));
            spyGuardrail1.register(framework);
            spyGuardrail2.register(framework);
            framework.trigger("event1", createKwargs("event", "1"));
            assertTrue(spyGuardrail1.detectCalled);
            assertFalse(spyGuardrail2.detectCalled);
            assertEquals("event1", spyGuardrail1.detectedEventName);
            spyGuardrail1.detectCalled = false;
            framework.trigger("event2", createKwargs("event", "2"));
            assertFalse(spyGuardrail1.detectCalled);
            assertTrue(spyGuardrail2.detectCalled);
            assertEquals("event2", spyGuardrail2.detectedEventName);
            spyGuardrail1.unregister();
            spyGuardrail2.unregister();
        }

        @Test
        @DisplayName("test guardrail safe flow via framework")
        void testGuardrailSafeFlowViaFramework() {
            guardrail = new CustomTestGuardrail(mockBackend, null);
            guardrail.register(framework);
            Map<String, Object> kwargs = new HashMap<>();
            kwargs.put("safe", "content");
            List<Object> results = framework.trigger("test_event", kwargs);
            assertNotNull(results);
            guardrail.unregister();
        }
    }

    private Map<String, Object> createKwargs(String key, Object value) {
        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put(key, value);
        return kwargs;
    }

    private static class CustomTestGuardrail extends BaseGuardrail {
        CustomTestGuardrail(GuardrailBackend backend, List<String> events) {
            super(backend, events, true);
        }

        @Override
        protected List<String> defaultEvents() {
            return Arrays.asList("test_event");
        }

        public void setFramework(CallbackFramework framework) {
            this.framework = framework;
        }

        public void addRegisteredEvent(String event) {
            super.addRegisteredEvent(event);
        }

        public List<String> getRegisteredEvents() {
            return super.getRegisteredEvents();
        }

        public boolean isEventRegistered(String event) {
            return super.isEventRegistered(event);
        }

        public Object callDetectCallback(String eventName, Map<String, Object> kwargs) {
            Object[] args = new Object[0];
            return detectCallback(eventName, args, kwargs);
        }

        @Override
        public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
            if (getBackend() != null) {
                return super.detect(eventName, args, kwargs);
            }
            return GuardrailResult.pass();
        }
    }

    private static class SpyGuardrail extends BaseGuardrail {
        boolean detectCalled = false;
        String detectedEventName = null;
        Map<String, Object> detectedKwargs = null;

        SpyGuardrail(GuardrailBackend backend, List<String> events) {
            super(backend, events, true);
        }

        @Override
        protected List<String> defaultEvents() {
            return Arrays.asList("test_event");
        }

        @Override
        public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
            detectCalled = true;
            detectedEventName = eventName;
            detectedKwargs = kwargs != null ? new HashMap<>(kwargs) : new HashMap<>();
            return GuardrailResult.pass();
        }
    }

    private static class DataCaptureGuardrail extends BaseGuardrail {
        Map<String, Object> capturedData = null;

        DataCaptureGuardrail(GuardrailBackend backend) {
            super(backend, null, true);
        }

        @Override
        protected List<String> defaultEvents() {
            return Arrays.asList("test_event");
        }

        @Override
        public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
            capturedData = kwargs;
            if (getBackend() != null) {
                return super.detect(eventName, args, kwargs);
            }
            return GuardrailResult.pass();
        }
    }

    private static class DirectBaseCallGuardrail extends BaseGuardrail {
        DirectBaseCallGuardrail() {
            super(null, null, true);
        }

        @Override
        protected List<String> defaultEvents() {
            return Arrays.asList("test_event");
        }
    }

    private static class NoDefaultEventsGuardrail extends BaseGuardrail {
        NoDefaultEventsGuardrail() {
            super(null, null, true);
        }

        @Override
        protected List<String> defaultEvents() {
            return new ArrayList<>();
        }

        @Override
        public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
            return GuardrailResult.pass();
        }
    }
}
