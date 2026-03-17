/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for structured logging events and EventClassRegistry.
 * Ported from Python: tests/unit_tests/core/common/log/test_structured_log.py
 */
class StructuredLogEventTest {

    // ==========================================================================
    // test_create_agent_event
    // ==========================================================================
    @Test
    @DisplayName("Create AgentEvent with correct fields via EventClassRegistry")
    void testCreateAgentEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setModuleName("TestAgent");
        event.setSessionId("session_456");
        event.setTraceId("trace_789");

        assertInstanceOf(AgentEvent.class, event);
        AgentEvent agentEvent = (AgentEvent) event;
        agentEvent.setAgentType("ReActAgent");

        assertEquals(LogEventType.AGENT_START, event.getEventType());
        assertEquals("agent_123", event.getModuleId());
        assertEquals("TestAgent", event.getModuleName());
        assertEquals("ReActAgent", agentEvent.getAgentType());
        assertEquals(ModuleType.AGENT, event.getModuleType());
    }

    // ==========================================================================
    // test_create_workflow_event
    // ==========================================================================
    @Test
    @DisplayName("Create WorkflowEvent with correct fields")
    void testCreateWorkflowEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.WORKFLOW_EXECUTE_START);

        assertInstanceOf(WorkflowEvent.class, event);
        WorkflowEvent workflowEvent = (WorkflowEvent) event;
        workflowEvent.setWorkflowId("workflow_001");
        workflowEvent.setWorkflowName("TestWorkflow");

        assertEquals("workflow_001", workflowEvent.getWorkflowId());
        assertEquals(ModuleType.WORKFLOW, event.getModuleType());
    }

    // ==========================================================================
    // test_create_workflow_component_event
    // ==========================================================================
    @Test
    @DisplayName("WorkflowEvent with component info")
    void testCreateWorkflowComponentEvent() {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventType(LogEventType.WORKFLOW_COMPONENT_START);
        event.setWorkflowId("workflow_001");
        event.setComponentId("component_001");
        event.setComponentName("LLMComponent");

        assertEquals("component_001", event.getComponentId());
        assertEquals("LLMComponent", event.getComponentName());
    }

    // ==========================================================================
    // test_create_llm_event
    // ==========================================================================
    @Test
    @DisplayName("Create LLMEvent with correct fields")
    void testCreateLlmEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START);
        event.setModuleId("llm_gpt4");

        assertInstanceOf(LLMEvent.class, event);
        LLMEvent llmEvent = (LLMEvent) event;
        llmEvent.setModelName("gpt-4");
        llmEvent.setQuery("What is Python?");
        llmEvent.setTemperature(0.7);

        assertEquals("gpt-4", llmEvent.getModelName());
        assertEquals("What is Python?", llmEvent.getQuery());
        assertEquals(ModuleType.LLM, event.getModuleType());
    }

    // ==========================================================================
    // test_event_to_dict
    // ==========================================================================
    @Test
    @DisplayName("Event toMap() produces correct dictionary")
    void testEventToDict() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMessage("Test message");

        Map<String, Object> eventMap = event.toMap();

        assertInstanceOf(Map.class, eventMap);
        assertEquals("agent_123", eventMap.get("module_id"));
        assertEquals("Test message", eventMap.get("message"));
        assertEquals("agent_start", eventMap.get("event_type"));
        assertEquals("INFO", eventMap.get("log_level"));
        assertNotNull(eventMap.get("event_id"));
        assertNotNull(eventMap.get("timestamp"));
    }

    // ==========================================================================
    // test_event_serialization
    // ==========================================================================
    @Test
    @DisplayName("Event can be serialized to JSON-compatible map")
    void testEventSerialization() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMessage("Test message");

        Map<String, Object> eventMap = event.toMap();
        // Verify all values are JSON-compatible types
        assertInstanceOf(String.class, eventMap.get("module_id"));
        assertInstanceOf(String.class, eventMap.get("message"));
        assertInstanceOf(String.class, eventMap.get("event_type"));
    }

    // ==========================================================================
    // test_event_with_message_and_stacktrace
    // ==========================================================================
    @Test
    @DisplayName("Event with message, stacktrace, and error fields")
    void testEventWithMessageAndStacktrace() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_ERROR);
        event.setModuleId("agent_123");
        event.setMessage("Error occurred");
        event.setStacktrace("Traceback (most recent call last):\n  File...");
        event.setErrorCode("AGENT_ERROR");
        event.setErrorMessage("Execution failed");

        assertEquals("Error occurred", event.getMessage());
        assertNotNull(event.getStacktrace());
        assertEquals("AGENT_ERROR", event.getErrorCode());
        assertEquals("Execution failed", event.getErrorMessage());
    }

    // ==========================================================================
    // test_validate_event (basic validation)
    // ==========================================================================
    @Test
    @DisplayName("Valid event has non-empty eventId")
    void testValidateEvent() {
        BaseLogEvent validEvent = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        validEvent.setModuleId("agent_123");
        assertNotNull(validEvent.getEventId());
        assertFalse(validEvent.getEventId().isEmpty());

        // Invalid: empty eventId
        AgentEvent invalidEvent = new AgentEvent();
        invalidEvent.setEventId("");
        invalidEvent.setEventType(LogEventType.AGENT_START);
        assertTrue(invalidEvent.getEventId().isEmpty());
    }

    // ==========================================================================
    // test_event_correlation
    // ==========================================================================
    @Test
    @DisplayName("Event correlation via parentEventId and correlationId")
    void testEventCorrelation() {
        BaseLogEvent parentEvent = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        parentEvent.setModuleId("agent_123");

        BaseLogEvent childEvent = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START);
        childEvent.setModuleId("llm_gpt4");
        childEvent.setParentEventId(parentEvent.getEventId());
        childEvent.setCorrelationId(parentEvent.getEventId());

        assertEquals(parentEvent.getEventId(), childEvent.getParentEventId());
        assertEquals(parentEvent.getEventId(), childEvent.getCorrelationId());
    }

    // ==========================================================================
    // test_agent_events (all agent event types)
    // ==========================================================================
    @Test
    @DisplayName("All agent event types create AgentEvent instances")
    void testAgentEvents() {
        LogEventType[] agentTypes = {
                LogEventType.AGENT_START,
                LogEventType.AGENT_END,
                LogEventType.AGENT_INVOKE,
                LogEventType.AGENT_RESPONSE,
                LogEventType.AGENT_ERROR,
        };

        for (LogEventType eventType : agentTypes) {
            BaseLogEvent event = EventClassRegistry.createEvent(eventType);
            event.setModuleId("agent_123");
            assertInstanceOf(AgentEvent.class, event,
                    "Expected AgentEvent for type: " + eventType);
            assertEquals(eventType, event.getEventType());
        }
    }

    // ==========================================================================
    // test_llm_events
    // ==========================================================================
    @Test
    @DisplayName("LLM event types create LLMEvent instances")
    void testLlmEvents() {
        BaseLogEvent startEvent = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START);
        startEvent.setModuleId("llm_gpt4");
        assertInstanceOf(LLMEvent.class, startEvent);
        ((LLMEvent) startEvent).setQuery("Test query");
        assertEquals("Test query", ((LLMEvent) startEvent).getQuery());

        BaseLogEvent endEvent = EventClassRegistry.createEvent(LogEventType.LLM_CALL_END);
        endEvent.setModuleId("llm_gpt4");
        assertInstanceOf(LLMEvent.class, endEvent);
        ((LLMEvent) endEvent).setResponseContent("Response");
        assertEquals("Response", ((LLMEvent) endEvent).getResponseContent());
    }

    // ==========================================================================
    // test_tool_events
    // ==========================================================================
    @Test
    @DisplayName("Tool event types create ToolEvent instances")
    void testToolEvents() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.TOOL_CALL_START);
        event.setModuleId("tool_search");
        assertInstanceOf(ToolEvent.class, event);

        ToolEvent toolEvent = (ToolEvent) event;
        toolEvent.setToolName("web_search");
        toolEvent.setArguments(Map.of("query", "Python"));
        assertEquals("web_search", toolEvent.getToolName());
    }

    // ==========================================================================
    // test_workflow_events
    // ==========================================================================
    @Test
    @DisplayName("Workflow event types create WorkflowEvent instances")
    void testWorkflowEvents() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.WORKFLOW_EXECUTE_START);
        assertInstanceOf(WorkflowEvent.class, event);
        ((WorkflowEvent) event).setWorkflowId("workflow_001");
        assertEquals("workflow_001", ((WorkflowEvent) event).getWorkflowId());
    }

    // ==========================================================================
    // test_event_with_metadata
    // ==========================================================================
    @Test
    @DisplayName("Event with metadata map")
    void testEventWithMetadata() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMetadata(Map.of("key1", "value1", "key2", 123));

        assertEquals("value1", event.getMetadata().get("key1"));
        assertEquals(123, event.getMetadata().get("key2"));
    }

    // ==========================================================================
    // test_event_metadata_serialization
    // ==========================================================================
    @Test
    @DisplayName("Metadata with nested map survives toMap() serialization")
    void testEventMetadataSerialization() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMetadata(Map.of("nested", Map.of("key", "value")));

        Map<String, Object> eventMap = event.toMap();
        assertNotNull(eventMap.get("metadata"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) eventMap.get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) metadata.get("nested");
        assertEquals("value", nested.get("key"));
    }

    // ==========================================================================
    // Dynamic Event Registration Tests
    // ==========================================================================
    @Nested
    @DisplayName("Dynamic event registration")
    class DynamicRegistrationTests {

        @AfterEach
        void cleanup() {
            // Clean up any custom registrations
            EventClassRegistry.unregister("custom_test_event");
            EventClassRegistry.unregister("my_custom_agent_event");
            EventClassRegistry.unregister("temp_event_type");
            EventClassRegistry.unregister("detailed_event_type");
            EventClassRegistry.unregister("serializable_event");
            EventClassRegistry.unregister("validated_event");
            EventClassRegistry.unregister("registered_custom_llm_type");
        }

        // ==========================================================================
        // test_register_custom_event_class
        // ==========================================================================
        @Test
        @DisplayName("Register and use custom event class with string key")
        void testRegisterCustomEventClass() {
            Supplier<BaseLogEvent> factory = () -> {
                BaseLogEvent e = new BaseLogEvent();
                e.setModuleType(ModuleType.SYSTEM);
                return e;
            };

            EventClassRegistry.register("custom_test_event", factory);

            Supplier<? extends BaseLogEvent> retrieved = EventClassRegistry.getFactory("custom_test_event");
            BaseLogEvent event = retrieved.get();
            event.setModuleId("test_123");

            assertNotNull(event);
            assertEquals("test_123", event.getModuleId());

            // Cleanup
            assertTrue(EventClassRegistry.unregister("custom_test_event"));
        }

        // ==========================================================================
        // test_unregister_event_class
        // ==========================================================================
        @Test
        @DisplayName("Unregister custom event class")
        void testUnregisterEventClass() {
            EventClassRegistry.register("temp_event_type", BaseLogEvent::new);

            // Verify it's registered
            Supplier<? extends BaseLogEvent> factory = EventClassRegistry.getFactory("temp_event_type");
            assertNotNull(factory);

            // Unregister
            boolean result = EventClassRegistry.unregister("temp_event_type");
            assertTrue(result);

            // After unregister, should fall back to BaseLogEvent
            Supplier<? extends BaseLogEvent> afterUnregister = EventClassRegistry.getFactory("temp_event_type");
            assertNotNull(afterUnregister); // Still returns a factory (the fallback)

            // Unregister again should return false
            boolean result2 = EventClassRegistry.unregister("temp_event_type");
            assertFalse(result2);
        }

        // ==========================================================================
        // test_get_event_class_priority
        // ==========================================================================
        @Test
        @DisplayName("get_event_class follows correct priority: custom > static > BaseLogEvent")
        void testGetEventClassPriority() {
            // Test 1: Static mapping for LogEventType enums
            Supplier<? extends BaseLogEvent> llmFactory = EventClassRegistry.getFactory(LogEventType.LLM_CALL_START);
            BaseLogEvent llmEvent = llmFactory.get();
            assertInstanceOf(LLMEvent.class, llmEvent);

            // Test 2: String key with no registration returns BaseLogEvent factory
            Supplier<? extends BaseLogEvent> unregistered = EventClassRegistry.getFactory("unregistered_custom_type");
            BaseLogEvent baseEvent = unregistered.get();
            assertInstanceOf(BaseLogEvent.class, baseEvent);

            // Test 3: Register with string and verify it overrides default
            EventClassRegistry.register("registered_custom_llm_type", () -> {
                BaseLogEvent e = new BaseLogEvent();
                e.setModuleType(ModuleType.LLM);
                return e;
            });
            Supplier<? extends BaseLogEvent> customFactory = EventClassRegistry.getFactory("registered_custom_llm_type");
            assertNotNull(customFactory);

            // Test 4: Unregister and restore default
            EventClassRegistry.unregister("registered_custom_llm_type");
            Supplier<? extends BaseLogEvent> afterRemoval = EventClassRegistry.getFactory("registered_custom_llm_type");
            // Should return BaseLogEvent factory (fallback)
            BaseLogEvent fallback = afterRemoval.get();
            assertInstanceOf(BaseLogEvent.class, fallback);

            // Test 5: Static mapping is always used for LogEventType enums
            Supplier<? extends BaseLogEvent> stillLlm = EventClassRegistry.getFactory(LogEventType.LLM_CALL_START);
            assertInstanceOf(LLMEvent.class, stillLlm.get());
        }

        // ==========================================================================
        // test_cannot_register_enum_conflicting_string
        // ==========================================================================
        @Test
        @DisplayName("String matching existing enum value is rejected")
        void testCannotRegisterEnumConflictingString() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    EventClassRegistry.register("agent_start", BaseLogEvent::new));

            assertTrue(ex.getMessage().contains("conflicts with predefined enum value"));
        }

        // ==========================================================================
        // test_register_invalid_class - in Java, type safety via Supplier<BaseLogEvent>
        // guarantees type correctness at compile time, but we test conflicting registration
        // ==========================================================================
        @Test
        @DisplayName("Re-registration overwrites previous custom factory")
        void testReRegistrationOverwrites() {
            EventClassRegistry.register("custom_test_event", () -> {
                AgentEvent e = new AgentEvent();
                e.setModuleId("first");
                return e;
            });

            EventClassRegistry.register("custom_test_event", () -> {
                LLMEvent e = new LLMEvent();
                e.setModuleId("second");
                return e;
            });

            Supplier<? extends BaseLogEvent> factory = EventClassRegistry.getFactory("custom_test_event");
            BaseLogEvent event = factory.get();
            assertInstanceOf(LLMEvent.class, event);
            assertEquals("second", event.getModuleId());
        }

        // ==========================================================================
        // test_custom_event_serialization
        // ==========================================================================
        @Test
        @DisplayName("Custom events can be serialized via toMap()")
        void testCustomEventSerialization() {
            EventClassRegistry.register("serializable_event", () -> {
                BaseLogEvent e = new BaseLogEvent();
                e.setModuleType(ModuleType.SYSTEM);
                return e;
            });

            Supplier<? extends BaseLogEvent> factory = EventClassRegistry.getFactory("serializable_event");
            BaseLogEvent event = factory.get();
            event.setModuleId("ser_123");
            event.setMessage("serialization test");

            Map<String, Object> eventMap = event.toMap();
            assertEquals("ser_123", eventMap.get("module_id"));
            assertEquals("serialization test", eventMap.get("message"));
        }

        // ==========================================================================
        // test_custom_event_validation
        // ==========================================================================
        @Test
        @DisplayName("Custom events have valid eventId after creation")
        void testCustomEventValidation() {
            EventClassRegistry.register("validated_event", BaseLogEvent::new);

            Supplier<? extends BaseLogEvent> factory = EventClassRegistry.getFactory("validated_event");
            BaseLogEvent event = factory.get();
            event.setModuleId("valid_123");

            // Should have valid eventId
            assertNotNull(event.getEventId());
            assertFalse(event.getEventId().isEmpty());
        }
    }

    // ==========================================================================
    // test_sanitize_event (Python: test_sanitize_event)
    // ==========================================================================
    @Test
    @DisplayName("Sanitize event replaces sensitive fields with <REDACTED>")
    void testSanitizeEvent() {
        LLMEvent event = new LLMEvent();
        event.setEventType(LogEventType.LLM_CALL_END);
        event.setModuleId("llm_gpt4");
        event.setMessages(List.of(Map.of("role", "user", "content", "secret")));
        event.setResponseContent("sensitive response");
        event.setQuery("sensitive query");

        Map<String, Object> sanitized = EventSanitizer.sanitizeEventForLogging(event);

        assertEquals("<REDACTED>", sanitized.get("messages"));
        assertEquals("<REDACTED>", sanitized.get("response_content"));
        assertEquals("<REDACTED>", sanitized.get("query"));
        assertEquals("llm_gpt4", sanitized.get("module_id"));  // Not sanitized
    }

    @Test
    @DisplayName("Sanitize event with custom sensitive fields")
    void testSanitizeEventCustomFields() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMessage("test message");

        Map<String, Object> sanitized = EventSanitizer.sanitizeEventForLogging(event,
                List.of("message"));

        assertEquals("<REDACTED>", sanitized.get("message"));
        assertEquals("agent_123", sanitized.get("module_id"));
    }

    @Test
    @DisplayName("Sanitize event with null sensitive fields does not replace")
    void testSanitizeEventNullFieldsNotReplaced() {
        LLMEvent event = new LLMEvent();
        event.setEventType(LogEventType.LLM_CALL_START);
        event.setModuleId("llm_gpt4");
        // query is null by default -> should not be redacted

        Map<String, Object> sanitized = EventSanitizer.sanitizeEventForLogging(event);

        // query is not in the map (putIfNotNull skips nulls)
        assertFalse(sanitized.containsKey("query") && "<REDACTED>".equals(sanitized.get("query")));
        assertEquals("llm_gpt4", sanitized.get("module_id"));
    }

    // ==========================================================================
    // test_static_event_class_map_unchanged (Python: test_static_event_class_map_unchanged)
    // ==========================================================================
    @Test
    @DisplayName("Dynamic registration does not modify static event class map")
    void testStaticEventClassMapUnchanged() {
        // Record original factory for AGENT_START
        var originalFactory = EventClassRegistry.getFactory(LogEventType.AGENT_START);
        BaseLogEvent originalEvent = originalFactory.get();
        assertInstanceOf(AgentEvent.class, originalEvent);

        // Register a dynamic custom event
        String newType = "new_dynamic_event_type";
        EventClassRegistry.register(newType, () -> {
            BaseLogEvent e = new BaseLogEvent();
            e.setModuleType(ModuleType.SYSTEM);
            return e;
        });

        // Static mapping should remain unchanged
        var afterFactory = EventClassRegistry.getFactory(LogEventType.AGENT_START);
        BaseLogEvent afterEvent = afterFactory.get();
        assertInstanceOf(AgentEvent.class, afterEvent);

        // Custom type should be retrievable via string key
        var customFactory = EventClassRegistry.getFactory(newType);
        assertNotNull(customFactory);

        // Cleanup
        EventClassRegistry.unregister(newType);

        // After unregister, static mapping is still intact
        var finalFactory = EventClassRegistry.getFactory(LogEventType.AGENT_START);
        assertInstanceOf(AgentEvent.class, finalFactory.get());
    }

    // ==========================================================================
    // Enum value tests
    // ==========================================================================
    @Nested
    @DisplayName("Enum value tests")
    class EnumTests {

        @Test
        @DisplayName("LogEventType.fromValue returns correct enum")
        void testLogEventTypeFromValue() {
            assertEquals(LogEventType.AGENT_START, LogEventType.fromValue("agent_start"));
            assertEquals(LogEventType.LLM_CALL_START, LogEventType.fromValue("llm_call_start"));
            assertNull(LogEventType.fromValue("nonexistent_type"));
        }

        @Test
        @DisplayName("ModuleType values are lowercase")
        void testModuleTypeValues() {
            assertEquals("agent", ModuleType.AGENT.getValue());
            assertEquals("workflow", ModuleType.WORKFLOW.getValue());
            assertEquals("llm", ModuleType.LLM.getValue());
            assertEquals("tool", ModuleType.TOOL.getValue());
            assertEquals("system", ModuleType.SYSTEM.getValue());
        }

        @Test
        @DisplayName("LogLevel values match expected strings")
        void testLogLevelValues() {
            assertEquals("DEBUG", LogLevel.DEBUG.getValue());
            assertEquals("INFO", LogLevel.INFO.getValue());
            assertEquals("WARNING", LogLevel.WARNING.getValue());
            assertEquals("ERROR", LogLevel.ERROR.getValue());
            assertEquals("CRITICAL", LogLevel.CRITICAL.getValue());
        }

        @Test
        @DisplayName("EventStatus values are lowercase")
        void testEventStatusValues() {
            assertEquals("success", EventStatus.SUCCESS.getValue());
            assertEquals("failure", EventStatus.FAILURE.getValue());
            assertEquals("pending", EventStatus.PENDING.getValue());
            assertEquals("timeout", EventStatus.TIMEOUT.getValue());
            assertEquals("cancelled", EventStatus.CANCELLED.getValue());
        }
    }

    // ==========================================================================
    // BaseLogEvent default values
    // ==========================================================================
    @Nested
    @DisplayName("BaseLogEvent defaults")
    class BaseLogEventDefaults {

        @Test
        @DisplayName("New BaseLogEvent has UUID eventId")
        void testDefaultEventId() {
            BaseLogEvent event = new BaseLogEvent();
            assertNotNull(event.getEventId());
            assertFalse(event.getEventId().isEmpty());
        }

        @Test
        @DisplayName("Default log level is INFO")
        void testDefaultLogLevel() {
            BaseLogEvent event = new BaseLogEvent();
            assertEquals(LogLevel.INFO, event.getLogLevel());
        }

        @Test
        @DisplayName("Default status is SUCCESS")
        void testDefaultStatus() {
            BaseLogEvent event = new BaseLogEvent();
            assertEquals(EventStatus.SUCCESS, event.getStatus());
        }

        @Test
        @DisplayName("Default moduleType is SYSTEM")
        void testDefaultModuleType() {
            BaseLogEvent event = new BaseLogEvent();
            assertEquals(ModuleType.SYSTEM, event.getModuleType());
        }

        @Test
        @DisplayName("Timestamp is set on creation")
        void testTimestampSet() {
            BaseLogEvent event = new BaseLogEvent();
            assertNotNull(event.getTimestamp());
        }

        @Test
        @DisplayName("Metadata is initialized as empty map")
        void testDefaultMetadata() {
            BaseLogEvent event = new BaseLogEvent();
            assertNotNull(event.getMetadata());
            assertTrue(event.getMetadata().isEmpty());
        }
    }
}
