/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.common.log;

import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.events.AgentEvent;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.EventClassRegistry;
import com.openjiuwen.core.common.logging.events.EventSanitizer;
import com.openjiuwen.core.common.logging.events.EventStatus;
import com.openjiuwen.core.common.logging.events.LLMEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.LogLevel;
import com.openjiuwen.core.common.logging.events.ModuleType;
import com.openjiuwen.core.common.logging.events.RunnerEvent;
import com.openjiuwen.core.common.logging.events.ToolEvent;
import com.openjiuwen.core.common.logging.events.WorkflowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_structured_log.py} in
 * {@code tests.unit_tests.core.common.log}.
 */
@Tag("unit-test")
class TestStructuredLog {

    @AfterEach
    void cleanup() {
        LoggingUtils.clearSessionId();
        EventClassRegistry.unregister("custom_test_event");
        EventClassRegistry.unregister("my_custom_agent_event");
        EventClassRegistry.unregister("temp_event_type");
        EventClassRegistry.unregister("custom_create_event");
        EventClassRegistry.unregister("serializable_event");
        EventClassRegistry.unregister("validated_event");
        EventClassRegistry.unregister("registered_custom_llm_type");
        EventClassRegistry.unregister("new_dynamic_event_type");
    }

    @Test
    @DisplayName("Test create agent event")
    void testCreateAgentEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setModuleName("TestAgent");
        AgentEvent agentEvent = (AgentEvent) event;
        agentEvent.setAgentType("ReActAgent");

        assertInstanceOf(AgentEvent.class, event);
        assertEquals(LogEventType.AGENT_START, event.getEventType());
        assertEquals("agent_123", event.getModuleId());
        assertEquals("TestAgent", event.getModuleName());
        assertEquals("ReActAgent", agentEvent.getAgentType());
        assertEquals(ModuleType.AGENT, event.getModuleType());
    }

    @Test
    @DisplayName("Test create workflow event")
    void testCreateWorkflowEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.WORKFLOW_EXECUTE_START);
        WorkflowEvent workflowEvent = (WorkflowEvent) event;
        workflowEvent.setWorkflowId("workflow_001");
        workflowEvent.setWorkflowName("TestWorkflow");

        assertInstanceOf(WorkflowEvent.class, event);
        assertEquals("workflow_001", workflowEvent.getWorkflowId());
        assertEquals("TestWorkflow", workflowEvent.getWorkflowName());
        assertEquals(ModuleType.WORKFLOW, event.getModuleType());
    }

    @Test
    @DisplayName("Test create workflow component event")
    void testCreateWorkflowComponentEvent() {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventType(LogEventType.WORKFLOW_COMPONENT_START);
        event.setWorkflowId("workflow_001");
        event.setComponentId("component_001");
        event.setComponentName("LLMComponent");

        assertEquals("component_001", event.getComponentId());
        assertEquals("LLMComponent", event.getComponentName());
        assertEquals(ModuleType.WORKFLOW_COMPONENT, event.getModuleType());
    }

    @Test
    @DisplayName("Test create llm event")
    void testCreateLlmEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START);
        LLMEvent llmEvent = (LLMEvent) event;
        llmEvent.setModelName("gpt-4");
        llmEvent.setQuery("What is Python?");
        llmEvent.setTemperature(0.7);

        assertInstanceOf(LLMEvent.class, event);
        assertEquals("gpt-4", llmEvent.getModelName());
        assertEquals("What is Python?", llmEvent.getQuery());
        assertEquals(0.7, llmEvent.getTemperature());
        assertEquals(ModuleType.LLM, event.getModuleType());
    }

    @Test
    @DisplayName("Test event to dict")
    void testEventToDict() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMessage("Test message");

        Map<String, Object> eventMap = event.toMap();

        assertEquals("agent_123", eventMap.get("module_id"));
        assertEquals("Test message", eventMap.get("message"));
        assertEquals("agent_start", eventMap.get("event_type"));
        assertEquals("INFO", eventMap.get("log_level"));
        assertNotNull(eventMap.get("event_id"));
        assertNotNull(eventMap.get("timestamp"));
    }

    @Test
    @DisplayName("Test event serialization")
    void testEventSerialization() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMessage("Test message");

        Map<String, Object> eventMap = event.toMap();

        assertInstanceOf(String.class, eventMap.get("module_id"));
        assertInstanceOf(String.class, eventMap.get("message"));
        assertInstanceOf(String.class, eventMap.get("event_type"));
    }

    @Test
    @DisplayName("Test structured event logging uses JSON by default")
    void testStructuredEventLoggingUsesJsonByDefault() {
        DefaultLogger logger = new DefaultLogger("structured-json-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.logEvent("agent started", LogEventType.AGENT_START, null);

        assertTrue(handler.messages().stream().anyMatch(message ->
                message.contains("\"event_type\":\"agent_start\"")
                        && message.contains("\"message\":\"agent started\"")));
    }

    @Test
    @DisplayName("Test plain string logging is not affected by text output")
    void testPlainStringLoggingIsNotAffectedByTextOutput() {
        DefaultLogger logger = new DefaultLogger("plain-string-test",
                Map.of("output", "console", "structured_output_format", "text"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("plain {} message", "text");

        assertTrue(handler.messages().contains("plain text message"));
    }

    @Test
    @DisplayName("Test log string message")
    void testLogStringMessage() {
        DefaultLogger logger = new DefaultLogger("string-message-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.info("hello structured logger");

        assertTrue(handler.messages().contains("hello structured logger"));
    }

    @Test
    @DisplayName("Test log with event type")
    void testLogWithEventType() {
        DefaultLogger logger = new DefaultLogger("event-type-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.logEvent("agent event", LogEventType.AGENT_START, null);

        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("\"event_type\":\"agent_start\"")));
    }

    @Test
    @DisplayName("Test log with event object")
    void testLogWithEventObject() {
        DefaultLogger logger = new DefaultLogger("event-object-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.LLM_CALL_ERROR);
        event.setMessage("llm failed");
        event.setErrorMessage("timeout");

        logger.logEvent("ignored", null, event);

        assertTrue(handler.messages().stream().anyMatch(message ->
                message.contains("\"event_type\":\"llm_call_error\"")
                        && message.contains("\"error_message\":\"timeout\"")));
    }

    @Test
    @DisplayName("Test log with message field")
    void testLogWithMessageField() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setMessage("message from field");

        assertEquals("message from field", event.toMap().get("message"));
    }

    @Test
    @DisplayName("Test log different levels")
    void testLogDifferentLevels() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_ERROR);
        event.setLogLevel(LogLevel.ERROR);

        assertEquals("ERROR", event.toMap().get("log_level"));
    }

    @Test
    @DisplayName("Test log exception with stacktrace")
    void testLogExceptionWithStacktrace() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_ERROR);
        event.setStacktrace("Traceback line");
        event.setErrorMessage("boom");

        assertEquals("Traceback line", event.toMap().get("stacktrace"));
        assertEquals("boom", event.toMap().get("error_message"));
    }

    @Test
    @DisplayName("Test log with trace id")
    void testLogWithTraceId() {
        LoggingUtils.setSessionId("trace-123");
        DefaultLogger logger = new DefaultLogger("trace-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.logEvent("with trace", LogEventType.AGENT_START, null);

        assertTrue(handler.messages().stream().anyMatch(message -> message.contains("\"trace_id\":\"trace-123\"")));
    }

    @Test
    @DisplayName("Test log with custom kwargs as metadata")
    void testLogWithCustomKwargs() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setMetadata(Map.of("custom", "value", "count", 2));

        Map<String, Object> metadata = metadata(event);

        assertEquals("value", metadata.get("custom"));
        assertEquals(2, metadata.get("count"));
    }

    @Test
    @DisplayName("Test log JSON format contains module id")
    void testLogJsonFormat() {
        DefaultLogger logger = new DefaultLogger("json-format-test", Map.of("output", "console"));
        RecordingHandler handler = new RecordingHandler();
        logger.addHandler(handler);

        logger.logEvent("json event", LogEventType.AGENT_START, null);

        assertTrue(handler.messages().stream().anyMatch(message ->
                message.contains("\"module_id\":\"json-format-test\"")));
    }

    @Test
    @DisplayName("Test event with message and stacktrace")
    void testEventWithMessageAndStacktrace() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_ERROR);
        event.setModuleId("agent_123");
        event.setMessage("Error occurred");
        event.setStacktrace("Traceback");
        event.setErrorCode("AGENT_ERROR");
        event.setErrorMessage("Execution failed");

        assertEquals("Error occurred", event.getMessage());
        assertEquals("Traceback", event.getStacktrace());
        assertEquals("AGENT_ERROR", event.getErrorCode());
        assertEquals("Execution failed", event.getErrorMessage());
    }

    @Test
    @DisplayName("Test validate event")
    void testValidateEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);

        assertTrue(EventClassRegistry.validateEvent(event));
    }

    @Test
    @DisplayName("Test validate event rejects missing type")
    void testValidateEventRejectsMissingType() {
        BaseLogEvent event = new BaseLogEvent();

        assertFalse(EventClassRegistry.validateEvent(event));
    }

    @Test
    @DisplayName("Test sanitize event")
    void testSanitizeEvent() {
        LLMEvent event = new LLMEvent();
        event.setEventType(LogEventType.LLM_CALL_END);
        event.setMessages(List.of(Map.of("role", "user", "content", "secret")));
        event.setResponseContent("sensitive response");
        event.setQuery("sensitive query");

        Map<String, Object> sanitized = EventSanitizer.sanitizeEventForLogging(event);

        assertEquals("<REDACTED>", sanitized.get("messages"));
        assertEquals("<REDACTED>", sanitized.get("response_content"));
        assertEquals("<REDACTED>", sanitized.get("query"));
    }

    @Test
    @DisplayName("Test sanitize event custom fields")
    void testSanitizeEventCustomFields() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");
        event.setMessage("test message");

        Map<String, Object> sanitized = EventSanitizer.sanitizeEventForLogging(event, List.of("message"));

        assertEquals("<REDACTED>", sanitized.get("message"));
        assertEquals("agent_123", sanitized.get("module_id"));
    }

    @Test
    @DisplayName("Test text output skips missing values")
    void testTextOutputSkipsMissingValues() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setModuleId("agent_123");

        Map<String, Object> eventMap = event.toMap();

        assertFalse(eventMap.containsKey("message"));
        assertEquals("agent_123", eventMap.get("module_id"));
    }

    @Test
    @DisplayName("Test runner event default values are not tuples")
    void testRunnerEventDefaultValuesAreNotTuples() {
        RunnerEvent event = new RunnerEvent();
        event.setEventType(LogEventType.RUNNER_START);

        assertFalse(event.toMap().get("module_type") instanceof List<?>);
        assertEquals("system", event.toMap().get("module_type"));
    }

    @Test
    @DisplayName("Test event correlation")
    void testEventCorrelation() {
        BaseLogEvent parent = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        BaseLogEvent child = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START);
        child.setParentEventId(parent.getEventId());
        child.setCorrelationId(parent.getEventId());

        assertEquals(parent.getEventId(), child.getParentEventId());
        assertEquals(parent.getEventId(), child.getCorrelationId());
    }

    @Test
    @DisplayName("Test agent events")
    void testAgentEvents() {
        for (LogEventType eventType : List.of(LogEventType.AGENT_START, LogEventType.AGENT_END,
                LogEventType.AGENT_INVOKE, LogEventType.AGENT_RESPONSE, LogEventType.AGENT_ERROR)) {
            assertInstanceOf(AgentEvent.class, EventClassRegistry.createEvent(eventType));
        }
    }

    @Test
    @DisplayName("Test llm events")
    void testLlmEvents() {
        BaseLogEvent start = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START);
        BaseLogEvent end = EventClassRegistry.createEvent(LogEventType.LLM_CALL_END);

        assertInstanceOf(LLMEvent.class, start);
        assertInstanceOf(LLMEvent.class, end);
    }

    @Test
    @DisplayName("Test tool events")
    void testToolEvents() {
        ToolEvent event = (ToolEvent) EventClassRegistry.createEvent(LogEventType.TOOL_CALL_START);
        event.setToolName("web_search");
        event.setArguments(Map.of("query", "Python"));

        assertEquals("web_search", event.getToolName());
        assertEquals(Map.of("query", "Python"), event.getArguments());
    }

    @Test
    @DisplayName("Test workflow events")
    void testWorkflowEvents() {
        WorkflowEvent event = (WorkflowEvent) EventClassRegistry.createEvent(LogEventType.WORKFLOW_EXECUTE_START);
        event.setWorkflowId("workflow_001");

        assertEquals("workflow_001", event.getWorkflowId());
        assertEquals(ModuleType.WORKFLOW, event.getModuleType());
    }

    @Test
    @DisplayName("Test runner events")
    void testRunnerEvents() {
        RunnerEvent event = (RunnerEvent) EventClassRegistry.createEvent(LogEventType.RUNNER_START);
        event.setRunnerId("runner-1");

        assertEquals("runner-1", event.getRunnerId());
        assertEquals("runner-1", event.toMap().get("runner_id"));
    }

    @Test
    @DisplayName("Test event with metadata")
    void testEventWithMetadata() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setMetadata(Map.of("key1", "value1", "key2", 123));

        assertEquals("value1", event.getMetadata().get("key1"));
        assertEquals(123, event.getMetadata().get("key2"));
    }

    @Test
    @DisplayName("Test event metadata serialization")
    void testEventMetadataSerialization() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        event.setMetadata(Map.of("nested", Map.of("key", "value")));

        Map<String, Object> nested = metadataMap(event, "nested");

        assertEquals("value", nested.get("key"));
    }

    @Test
    @DisplayName("Test register custom event class")
    void testRegisterCustomEventClass() {
        EventClassRegistry.register("custom_test_event", BaseLogEvent::new);

        Supplier<? extends BaseLogEvent> factory = EventClassRegistry.getFactory("custom_test_event");
        BaseLogEvent event = factory.get();
        event.setModuleId("test_123");

        assertEquals("test_123", event.getModuleId());
        assertTrue(EventClassRegistry.unregister("custom_test_event"));
    }

    @Test
    @DisplayName("Test register with custom string identifier")
    void testRegisterWithCustomStringIdentifier() {
        EventClassRegistry.register("my_custom_agent_event", AgentEvent::new);

        BaseLogEvent event = EventClassRegistry.createEvent("my_custom_agent_event",
                Map.of("module_id", "agent-42", "message", "custom", "agent_type", "ReActAgent"));

        assertInstanceOf(AgentEvent.class, event);
        assertEquals("my_custom_agent_event", event.getEventTypeKey());
        assertEquals("agent-42", event.getModuleId());
        assertEquals("custom", event.getMessage());
        assertEquals("ReActAgent", ((AgentEvent) event).getAgentType());
    }

    @Test
    @DisplayName("Test unregister event class")
    void testUnregisterEventClass() {
        EventClassRegistry.register("temp_event_type", BaseLogEvent::new);

        assertTrue(EventClassRegistry.unregister("temp_event_type"));
        assertFalse(EventClassRegistry.unregister("temp_event_type"));
        assertInstanceOf(BaseLogEvent.class, EventClassRegistry.getFactory("temp_event_type").get());
    }

    @Test
    @DisplayName("Test get event class priority")
    void testGetEventClassPriority() {
        assertInstanceOf(LLMEvent.class, EventClassRegistry.getFactory(LogEventType.LLM_CALL_START).get());
        assertInstanceOf(BaseLogEvent.class, EventClassRegistry.getFactory("unregistered_custom_type").get());

        EventClassRegistry.register("registered_custom_llm_type", LLMEvent::new);

        assertInstanceOf(LLMEvent.class, EventClassRegistry.getFactory("registered_custom_llm_type").get());
    }

    @Test
    @DisplayName("Test register invalid class raises error")
    void testRegisterInvalidClassRaisesError() {
        EventClassRegistry.register("custom_test_event", () -> null);

        assertFalse(EventClassRegistry.validateEvent(EventClassRegistry.getFactory("custom_test_event").get()));
    }

    @Test
    @DisplayName("Test cannot register enum conflicting string")
    void testCannotRegisterEnumConflictingString() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> EventClassRegistry.register("agent_start", BaseLogEvent::new));

        assertTrue(thrown.getMessage().contains("conflicts with predefined enum value"));
    }

    @Test
    @DisplayName("Test static event class map unchanged")
    void testStaticEventClassMapUnchanged() {
        BaseLogEvent original = EventClassRegistry.getFactory(LogEventType.AGENT_START).get();
        EventClassRegistry.register("new_dynamic_event_type", BaseLogEvent::new);
        BaseLogEvent afterRegistration = EventClassRegistry.getFactory(LogEventType.AGENT_START).get();

        assertInstanceOf(AgentEvent.class, original);
        assertInstanceOf(AgentEvent.class, afterRegistration);
    }

    @Test
    @DisplayName("Test custom event with create log event")
    void testCustomEventWithCreateLogEvent() {
        EventClassRegistry.register("custom_create_event", AgentEvent::new);

        BaseLogEvent event = EventClassRegistry.createEvent("custom_create_event",
                Map.of("module_id", "agent-custom", "agent_type", "ReActAgent"));

        assertInstanceOf(AgentEvent.class, event);
        assertEquals("agent-custom", event.getModuleId());
        assertEquals("ReActAgent", ((AgentEvent) event).getAgentType());
    }

    @Test
    @DisplayName("Test custom event serialization")
    void testCustomEventSerialization() {
        EventClassRegistry.register("serializable_event", BaseLogEvent::new);
        BaseLogEvent event = EventClassRegistry.createEvent("serializable_event",
                Map.of("module_id", "ser_123", "message", "serialization test"));

        assertEquals("ser_123", event.toMap().get("module_id"));
        assertEquals("serialization test", event.toMap().get("message"));
    }

    @Test
    @DisplayName("Test custom event validation")
    void testCustomEventValidation() {
        EventClassRegistry.register("validated_event", BaseLogEvent::new);
        BaseLogEvent event = EventClassRegistry.createEvent("validated_event");

        assertTrue(EventClassRegistry.validateEvent(event));
    }

    @Test
    @DisplayName("Test log event type from value")
    void testLogEventTypeFromValue() {
        assertEquals(LogEventType.AGENT_START, LogEventType.fromValue("agent_start"));
        assertEquals(LogEventType.LLM_CALL_START, LogEventType.fromValue("llm_call_start"));
        assertNull(LogEventType.fromValue("nonexistent_type"));
    }

    @Test
    @DisplayName("Test module type values")
    void testModuleTypeValues() {
        assertEquals("agent", ModuleType.AGENT.getValue());
        assertEquals("workflow", ModuleType.WORKFLOW.getValue());
        assertEquals("llm", ModuleType.LLM.getValue());
        assertEquals("tool", ModuleType.TOOL.getValue());
        assertEquals("system", ModuleType.SYSTEM.getValue());
    }

    @Test
    @DisplayName("Test log level values")
    void testLogLevelValues() {
        assertEquals("DEBUG", LogLevel.DEBUG.getValue());
        assertEquals("INFO", LogLevel.INFO.getValue());
        assertEquals("WARNING", LogLevel.WARNING.getValue());
        assertEquals("ERROR", LogLevel.ERROR.getValue());
        assertEquals("CRITICAL", LogLevel.CRITICAL.getValue());
    }

    @Test
    @DisplayName("Test event status values")
    void testEventStatusValues() {
        assertEquals("success", EventStatus.SUCCESS.getValue());
        assertEquals("failure", EventStatus.FAILURE.getValue());
        assertEquals("pending", EventStatus.PENDING.getValue());
        assertEquals("timeout", EventStatus.TIMEOUT.getValue());
        assertEquals("cancelled", EventStatus.CANCELLED.getValue());
    }

    @Test
    @DisplayName("Test base event default values")
    void testBaseEventDefaultValues() {
        BaseLogEvent event = new BaseLogEvent();

        assertNotNull(event.getEventId());
        assertEquals(LogLevel.INFO, event.getLogLevel());
        assertEquals(EventStatus.SUCCESS, event.getStatus());
        assertEquals(ModuleType.SYSTEM, event.getModuleType());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("Test metadata is initialized as empty map")
    void testDefaultMetadata() {
        BaseLogEvent event = new BaseLogEvent();

        assertNotNull(event.getMetadata());
        assertTrue(event.getMetadata().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(BaseLogEvent event) {
        return (Map<String, Object>) event.toMap().get("metadata");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadataMap(BaseLogEvent event, String key) {
        return (Map<String, Object>) metadata(event).get(key);
    }

    private static final class RecordingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<String> messages() {
            return records.stream().map(LogRecord::getMessage).toList();
        }
    }
}
