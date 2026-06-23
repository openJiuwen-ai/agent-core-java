/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.events.AgentEvent;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.EventClassRegistry;
import com.openjiuwen.core.common.logging.events.EventSanitizer;
import com.openjiuwen.core.common.logging.events.LLMEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.ModuleType;
import com.openjiuwen.core.common.logging.events.RunnerEvent;
import com.openjiuwen.core.common.logging.events.ToolEvent;
import com.openjiuwen.core.common.logging.events.WorkflowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.common.log.test_structured_log} in
 * {@code tests/unit_tests/core/common/log/test_structured_log.py}.</p>
 */
class StructuredLogPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    Collection<DynamicTest> structuredLogPythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "test_create_agent_event", this::createAgentEvent);
        add(tests, "test_create_workflow_event", this::createWorkflowEvent);
        add(tests, "test_create_workflow_component_event", this::createWorkflowComponentEvent);
        add(tests, "test_create_llm_event", this::createLlmEvent);
        add(tests, "test_event_to_dict", this::eventToDict);
        add(tests, "test_event_serialization", this::eventSerialization);
        add(tests, "test_structured_event_logging_uses_json_by_default", this::structuredEventLoggingUsesJson);
        add(tests, "test_structured_event_logging_can_use_text_output", this::structuredEventCanBeRenderedAsText);
        add(tests, "test_event_object_logging_can_use_text_output", this::eventObjectCanBeRenderedAsText);
        add(tests, "test_text_output_skips_missing_values", this::textOutputSkipsMissingValues);
        add(tests, "test_runner_event_default_values_are_not_tuples", this::runnerEventDefaultsAreNull);
        add(tests, "test_plain_string_logging_is_not_affected_by_text_output", this::plainStringLoggingUnaffected);
        add(tests, "test_event_with_message_and_stacktrace", this::eventWithMessageAndStacktrace);
        add(tests, "test_validate_event", this::validateEvent);
        add(tests, "test_sanitize_event", this::sanitizeEvent);
        add(tests, "test_event_correlation", this::eventCorrelation);
        add(tests, "test_log_string_message", this::logStringMessage);
        add(tests, "test_log_with_event_type", this::logWithEventType);
        add(tests, "test_log_with_event_object", this::logWithEventObject);
        add(tests, "test_log_with_message_field", this::logWithMessageField);
        add(tests, "test_log_different_levels", this::logDifferentLevels);
        add(tests, "test_log_exception_with_stacktrace", this::logExceptionWithStacktrace);
        add(tests, "test_log_with_trace_id", this::logWithTraceId);
        add(tests, "test_log_with_custom_kwargs", this::logWithCustomKwargs);
        add(tests, "test_log_json_format", this::logJsonFormat);
        add(tests, "test_agent_events", this::agentEvents);
        add(tests, "test_llm_events", this::llmEvents);
        add(tests, "test_tool_events", this::toolEvents);
        add(tests, "test_workflow_events", this::workflowEvents);
        add(tests, "test_event_with_metadata", this::eventWithMetadata);
        add(tests, "test_event_metadata_serialization", this::eventMetadataSerialization);
        add(tests, "test_register_custom_event_class", this::registerCustomEventClass);
        add(tests, "test_register_with_custom_string_identifier", this::registerWithCustomStringIdentifier);
        add(tests, "test_unregister_event_class", this::unregisterEventClass);
        add(tests, "test_get_event_class_priority", this::getEventClassPriority);
        add(tests, "test_register_invalid_class_raises_error", this::registerInvalidClassRaisesError);
        add(tests, "test_cannot_register_enum_conflicting_string", this::cannotRegisterEnumConflictingString);
        add(tests, "test_static_event_class_map_unchanged", this::staticEventClassMapUnchanged);
        add(tests, "test_custom_event_with_create_log_event", this::customEventWithCreateLogEvent);
        add(tests, "test_custom_event_serialization", this::customEventSerialization);
        add(tests, "test_custom_event_validation", this::customEventValidation);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void createAgentEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "module_name", "TestAgent", "agent_type", "ReActAgent",
                        "session_id", "session_456", "trace_id", "trace_789"));

        AgentEvent agentEvent = assertInstanceOf(AgentEvent.class, event);
        assertEquals(LogEventType.AGENT_START, agentEvent.getEventType());
        assertEquals("agent_123", agentEvent.getModuleId());
        assertEquals("TestAgent", agentEvent.getModuleName());
        assertEquals("ReActAgent", agentEvent.getAgentType());
        assertEquals(ModuleType.AGENT, agentEvent.getModuleType());
    }

    private void createWorkflowEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.WORKFLOW_EXECUTE_START,
                props("workflow_id", "workflow_001", "workflow_name", "TestWorkflow"));

        WorkflowEvent workflowEvent = assertInstanceOf(WorkflowEvent.class, event);
        assertEquals("workflow_001", workflowEvent.getWorkflowId());
        assertEquals(ModuleType.WORKFLOW, workflowEvent.getModuleType());
    }

    private void createWorkflowComponentEvent() {
        WorkflowEvent event = new WorkflowEvent();
        event.setEventType(LogEventType.WORKFLOW_COMPONENT_START);
        event.setWorkflowId("workflow_001");
        event.setComponentId("component_001");
        event.setComponentName("LLMComponent");

        assertEquals(ModuleType.WORKFLOW_COMPONENT, event.getModuleType());
        assertEquals("component_001", event.getComponentId());
    }

    private void createLlmEvent() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START,
                props("module_id", "llm_gpt4", "model_name", "gpt-4", "query", "What is Python?",
                        "temperature", 0.7d));

        LLMEvent llmEvent = assertInstanceOf(LLMEvent.class, event);
        assertEquals("gpt-4", llmEvent.getModelName());
        assertEquals("What is Python?", llmEvent.getQuery());
        assertEquals(ModuleType.LLM, llmEvent.getModuleType());
    }

    private void eventToDict() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "message", "Test message"));
        Map<String, Object> eventMap = event.toMap();

        assertEquals("agent_123", eventMap.get("module_id"));
        assertEquals("Test message", eventMap.get("message"));
        assertEquals("agent_start", eventMap.get("event_type"));
        assertEquals("INFO", eventMap.get("log_level"));
        assertTrue(eventMap.containsKey("event_id"));
        assertTrue(eventMap.containsKey("timestamp"));
    }

    private void eventSerialization() throws Exception {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "message", "Test message"));

        String json = OBJECT_MAPPER.writeValueAsString(event.toMap());
        Map<?, ?> parsed = OBJECT_MAPPER.readValue(json, Map.class);
        assertEquals("agent_123", parsed.get("module_id"));
        assertEquals("Test message", parsed.get("message"));
    }

    private void structuredEventLoggingUsesJson() throws Exception {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);

        logger.logEvent("Agent started", LogEventType.AGENT_START, null);

        Map<?, ?> parsed = OBJECT_MAPPER.readValue(handler.lastMessage(), Map.class);
        assertEquals("Agent started", parsed.get("message"));
        assertEquals("agent_start", parsed.get("event_type"));
    }

    private void structuredEventCanBeRenderedAsText() {
        ToolEvent event = (ToolEvent) EventClassRegistry.createEvent(LogEventType.TOOL_CALL_END,
                props("module_id", "tool_1", "tool_name", "search", "arguments", Map.of("query", "weather"),
                        "result", List.of("ok"), "message", "Tool finished"));

        String text = renderText(event, "Tool finished");

        assertTrue(text.startsWith("Tool finished; "));
        assertFalse(text.contains("message=Tool finished"));
        assertTrue(text.contains("event_type=tool_call_end"));
        assertTrue(text.contains("module_id=tool_1"));
        assertTrue(text.contains("tool_name=search"));
        assertTrue(text.contains("arguments={query=weather}"));
        assertTrue(text.contains("result=[ok]"));
    }

    private void eventObjectCanBeRenderedAsText() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "message", "Original message", "metadata", Map.of("step", 1)));
        event.setMessage("Replacement message");

        String text = renderText(event, "Replacement message");

        assertTrue(text.startsWith("Replacement message; "));
        assertFalse(text.contains("message=Replacement message"));
        assertTrue(text.contains("event_type=agent_start"));
        assertTrue(text.contains("module_id=agent_123"));
        assertTrue(text.contains("metadata={step=1}"));
    }

    private void textOutputSkipsMissingValues() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "trace_id", "", "metadata", Map.of(), "message", "Agent started"));

        String text = renderText(event, "Agent started");

        assertTrue(text.startsWith("Agent started; "));
        assertTrue(text.contains("module_id=agent_123"));
        assertFalse(text.contains("trace_id="));
        assertFalse(text.contains("metadata={}"));
    }

    private void runnerEventDefaultsAreNull() {
        RunnerEvent event = (RunnerEvent) EventClassRegistry.createEvent(LogEventType.RUNNER_START,
                props("message", "Runner started"));

        assertNull(event.getRunnerId());
        assertNull(event.getInputs());
        assertNull(event.getOutputs());
        assertNull(event.getChunk());
        String text = renderText(event, "Runner started");
        assertTrue(text.contains("event_id="));
        assertTrue(text.contains("event_type=runner_start"));
        assertFalse(text.contains("runner_id="));
        assertFalse(text.contains("inputs="));
        assertFalse(text.contains("outputs="));
        assertFalse(text.contains("chunk="));
    }

    private void plainStringLoggingUnaffected() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);

        logger.info("Plain log line");

        assertEquals("Plain log line", handler.lastMessage());
    }

    private void eventWithMessageAndStacktrace() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_ERROR,
                props("module_id", "agent_123", "message", "Error occurred",
                        "stacktrace", "Traceback (most recent call last):\n  File...",
                        "error_code", "AGENT_ERROR", "error_message", "Execution failed"));

        assertEquals("Error occurred", event.getMessage());
        assertTrue(event.getStacktrace().contains("Traceback"));
        assertEquals("AGENT_ERROR", event.getErrorCode());
        assertEquals("Execution failed", event.getErrorMessage());
    }

    private void validateEvent() {
        BaseLogEvent validEvent = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123"));
        BaseLogEvent invalidEvent = EventClassRegistry.createEvent(LogEventType.AGENT_START);
        invalidEvent.setEventId("");

        assertTrue(EventClassRegistry.validateEvent(validEvent));
        assertFalse(EventClassRegistry.validateEvent(invalidEvent));
    }

    private void sanitizeEvent() {
        LLMEvent event = (LLMEvent) EventClassRegistry.createEvent(LogEventType.LLM_CALL_END,
                props("module_id", "llm_gpt4", "messages", List.of(Map.of("role", "user", "content", "secret")),
                        "response_content", "sensitive response", "query", "sensitive query"));
        Map<String, Object> sanitized = EventSanitizer.sanitizeEventForLogging(event);

        assertEquals(EventSanitizer.REDACTED, sanitized.get("messages"));
        assertEquals(EventSanitizer.REDACTED, sanitized.get("response_content"));
        assertEquals(EventSanitizer.REDACTED, sanitized.get("query"));
        assertEquals("llm_gpt4", sanitized.get("module_id"));
    }

    private void eventCorrelation() {
        BaseLogEvent parent = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123"));
        BaseLogEvent child = EventClassRegistry.createEvent(LogEventType.LLM_CALL_START,
                props("module_id", "llm_gpt4", "parent_event_id", parent.getEventId(),
                        "correlation_id", parent.getEventId()));

        assertEquals(parent.getEventId(), child.getParentEventId());
        assertEquals(parent.getEventId(), child.getCorrelationId());
    }

    private void logStringMessage() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);

        logger.info("Test message");

        assertTrue(handler.lastMessage().contains("Test message"));
    }

    private void logWithEventType() throws Exception {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);

        logger.logEvent("Agent started", LogEventType.AGENT_START, null);

        assertTrue(handler.lastMessage().contains("agent_start"));
    }

    private void logWithEventObject() throws Exception {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "module_name", "TestAgent", "agent_type", "ReActAgent"));

        logger.logEvent("", null, event);

        assertTrue(handler.lastMessage().contains("agent_123"));
        assertTrue(handler.lastMessage().contains("TestAgent"));
    }

    private void logWithMessageField() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123"));
        event.setMessage("Custom message");

        assertEquals("Custom message", event.toMap().get("message"));
    }

    private void logDifferentLevels() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);

        logger.debug("Debug message");
        logger.info("Info message");
        logger.warning("Warning message");
        logger.error("Error message");
        logger.critical("Critical message");

        assertTrue(handler.messages.toString().contains("Info message"));
        assertTrue(handler.messages.toString().contains("Warning message"));
        assertTrue(handler.messages.toString().contains("Error message"));
        assertTrue(handler.messages.toString().contains("Critical message"));
    }

    private void logExceptionWithStacktrace() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);

        logger.exception("Exception occurred", new ValueError("Test exception"));

        assertTrue(handler.lastMessage().contains("Exception occurred"));
        assertTrue(handler.lastThrown().toString().contains("Test exception"));
    }

    private void logWithTraceId() {
        CapturingHandler handler = new CapturingHandler();
        DefaultLogger logger = loggerWithHandler(handler);
        LoggingUtils.setSessionId("test_trace_123");
        try {
            logger.logEvent("Message with trace", LogEventType.AGENT_START, null);
            assertTrue(handler.lastMessage().contains("test_trace_123"));
        } finally {
            LoggingUtils.setSessionId();
        }
    }

    private void logWithCustomKwargs() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "session_id", "session_456", "trace_id", "trace_789",
                        "metadata", Map.of("custom_field", "custom_value"), "message", "Custom event"));

        Map<String, Object> map = event.toMap();
        assertEquals("agent_123", map.get("module_id"));
        assertEquals("session_456", map.get("session_id"));
        assertEquals("trace_789", map.get("trace_id"));
    }

    private void logJsonFormat() throws Exception {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "message", "Test message"));
        String json = OBJECT_MAPPER.writeValueAsString(event.toMap());
        Map<?, ?> parsed = OBJECT_MAPPER.readValue(json, Map.class);

        assertEquals("agent_123", parsed.get("module_id"));
        assertEquals("Test message", parsed.get("message"));
    }

    private void agentEvents() {
        for (LogEventType type : List.of(LogEventType.AGENT_START, LogEventType.AGENT_END,
                LogEventType.AGENT_INVOKE, LogEventType.AGENT_RESPONSE, LogEventType.AGENT_ERROR)) {
            assertInstanceOf(AgentEvent.class, EventClassRegistry.createEvent(type, props("module_id", "agent_123")));
        }
    }

    private void llmEvents() {
        LLMEvent start = (LLMEvent) EventClassRegistry.createEvent(LogEventType.LLM_CALL_START,
                props("module_id", "llm_gpt4", "query", "Test query"));
        LLMEvent end = (LLMEvent) EventClassRegistry.createEvent(LogEventType.LLM_CALL_END,
                props("module_id", "llm_gpt4", "response_content", "Response"));

        assertEquals("Test query", start.getQuery());
        assertEquals("Response", end.getResponseContent());
    }

    private void toolEvents() {
        ToolEvent event = (ToolEvent) EventClassRegistry.createEvent(LogEventType.TOOL_CALL_START,
                props("module_id", "tool_search", "tool_name", "web_search", "arguments", Map.of("query", "Python")));

        assertEquals("web_search", event.getToolName());
    }

    private void workflowEvents() {
        WorkflowEvent event = (WorkflowEvent) EventClassRegistry.createEvent(LogEventType.WORKFLOW_EXECUTE_START,
                props("workflow_id", "workflow_001"));

        assertEquals("workflow_001", event.getWorkflowId());
    }

    private void eventWithMetadata() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "metadata", Map.of("key1", "value1", "key2", 123)));

        assertEquals("value1", event.getMetadata().get("key1"));
        assertEquals(123, event.getMetadata().get("key2"));
    }

    private void eventMetadataSerialization() {
        BaseLogEvent event = EventClassRegistry.createEvent(LogEventType.AGENT_START,
                props("module_id", "agent_123", "metadata", Map.of("nested", Map.of("key", "value"))));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) event.toMap().get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) metadata.get("nested");

        assertEquals("value", nested.get("key"));
    }

    private void registerCustomEventClass() {
        String customType = "custom_test_event";
        try {
            EventClassRegistry.register(customType, CustomEvent::new);
            BaseLogEvent event = EventClassRegistry.createEvent(customType,
                    props("custom_field", "test_value", "module_id", "test_123"));

            CustomEvent customEvent = assertInstanceOf(CustomEvent.class, event);
            assertEquals("test_value", customEvent.getCustomField());
            assertEquals("test_123", customEvent.getModuleId());
        } finally {
            EventClassRegistry.unregister(customType);
        }
    }

    private void registerWithCustomStringIdentifier() {
        String customType = "my_custom_agent_event";
        try {
            EventClassRegistry.register(customType, CustomAgentEvent::new);
            BaseLogEvent event = EventClassRegistry.createEvent(customType,
                    props("custom_agent_field", "custom_value", "module_id", "agent_123"));

            assertInstanceOf(CustomAgentEvent.class, event);
            assertInstanceOf(AgentEvent.class, EventClassRegistry.createEvent(LogEventType.AGENT_START,
                    props("module_id", "agent_456")));
        } finally {
            EventClassRegistry.unregister(customType);
        }
    }

    private void unregisterEventClass() {
        String tempType = "temp_event_type";
        EventClassRegistry.register(tempType, TempEvent::new);
        assertInstanceOf(TempEvent.class, EventClassRegistry.createEvent(tempType));
        assertTrue(EventClassRegistry.unregister(tempType));
        assertInstanceOf(BaseLogEvent.class, EventClassRegistry.createEvent(tempType));
        assertFalse(EventClassRegistry.unregister(tempType));
    }

    private void getEventClassPriority() {
        String registeredType = "registered_custom_llm_type";
        assertInstanceOf(LLMEvent.class, EventClassRegistry.createEvent(LogEventType.LLM_CALL_START));
        assertEquals(BaseLogEvent.class, EventClassRegistry.createEvent("unregistered_custom_type").getClass());
        try {
            EventClassRegistry.register(registeredType, CustomLLMEvent::new);
            assertInstanceOf(CustomLLMEvent.class, EventClassRegistry.createEvent(registeredType));
        } finally {
            EventClassRegistry.unregister(registeredType);
        }
        assertEquals(BaseLogEvent.class, EventClassRegistry.createEvent(registeredType).getClass());
        assertInstanceOf(LLMEvent.class, EventClassRegistry.createEvent(LogEventType.LLM_CALL_START));
    }

    private void registerInvalidClassRaisesError() {
        assertThrows(NullPointerException.class, () -> EventClassRegistry.register("invalid_event_type", null));
    }

    private void cannotRegisterEnumConflictingString() {
        assertThrows(IllegalArgumentException.class,
                () -> EventClassRegistry.register("agent_start", CustomEvent::new));
    }

    private void staticEventClassMapUnchanged() {
        Supplier<? extends BaseLogEvent> before = EventClassRegistry.getFactory(LogEventType.AGENT_START);
        String newType = "new_dynamic_event_type";
        try {
            EventClassRegistry.register(newType, CustomEvent::new);
            Supplier<? extends BaseLogEvent> after = EventClassRegistry.getFactory(LogEventType.AGENT_START);
            assertSame(before, after);
            assertInstanceOf(CustomEvent.class, EventClassRegistry.createEvent(newType));
            assertInstanceOf(AgentEvent.class, EventClassRegistry.createEvent(LogEventType.AGENT_START));
        } finally {
            EventClassRegistry.unregister(newType);
        }
    }

    private void customEventWithCreateLogEvent() {
        String detailedType = "detailed_event_type";
        try {
            EventClassRegistry.register(detailedType, DetailedEvent::new);
            DetailedEvent event = (DetailedEvent) EventClassRegistry.createEvent(detailedType,
                    props("module_id", "detail_123", "detail_field", "important_detail",
                            "detail_count", 42, "message", "Detailed event message"));

            assertEquals("important_detail", event.getDetailField());
            assertEquals(42, event.getDetailCount());
            assertEquals("detail_123", event.getModuleId());
            assertEquals("Detailed event message", event.getMessage());
        } finally {
            EventClassRegistry.unregister(detailedType);
        }
    }

    private void customEventSerialization() throws Exception {
        String customType = "serializable_event";
        try {
            EventClassRegistry.register(customType, SerializableEvent::new);
            SerializableEvent event = (SerializableEvent) EventClassRegistry.createEvent(customType,
                    props("extra_data", "test_data", "module_id", "ser_123"));

            Map<String, Object> eventMap = event.toMap();
            String json = OBJECT_MAPPER.writeValueAsString(eventMap);
            Map<?, ?> parsed = OBJECT_MAPPER.readValue(json, Map.class);
            assertEquals("test_data", parsed.get("extra_data"));
            assertEquals("ser_123", parsed.get("module_id"));
        } finally {
            EventClassRegistry.unregister(customType);
        }
    }

    private void customEventValidation() {
        String customType = "validated_event";
        try {
            EventClassRegistry.register(customType, ValidatedEvent::new);
            BaseLogEvent event = EventClassRegistry.createEvent(customType, props("module_id", "valid_123"));

            assertTrue(EventClassRegistry.validateEvent(event));
        } finally {
            EventClassRegistry.unregister(customType);
        }
    }

    private static DefaultLogger loggerWithHandler(CapturingHandler handler) {
        DefaultLogger logger = new DefaultLogger("test", Map.of("output", List.of("console")));
        logger.setLevel(10);
        logger.addHandler(handler);
        return logger;
    }

    private static String renderText(BaseLogEvent event, String message) {
        Map<String, Object> map = event.toMap();
        StringBuilder builder = new StringBuilder(message);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if ("message".equals(entry.getKey()) || value == null) {
                continue;
            }
            if (value instanceof String stringValue && stringValue.isBlank()) {
                continue;
            }
            if (value instanceof Map<?, ?> mapValue && mapValue.isEmpty()) {
                continue;
            }
            builder.append(builder.indexOf("; ") < 0 ? "; " : ", ");
            builder.append(entry.getKey()).append("=").append(value);
        }
        return builder.toString();
    }

    private static Map<String, Object> props(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        private String lastMessage() {
            assertFalse(messages.isEmpty(), "expected at least one captured log record");
            return messages.getLast();
        }

        private Throwable lastThrown() {
            assertFalse(records.isEmpty(), "expected at least one captured log record");
            return records.getLast().getThrown();
        }
    }

    private static class ValueError extends RuntimeException {
        private ValueError(String message) {
            super(message);
        }
    }

    public static class CustomEvent extends BaseLogEvent {
        private String customField = "default";

        public String getCustomField() {
            return customField;
        }

        public void setCustomField(String customField) {
            this.customField = customField;
        }

        @Override
        protected void addFieldsToMap(Map<String, Object> map) {
            putIfNotNull(map, "custom_field", customField);
        }
    }

    public static class CustomAgentEvent extends AgentEvent {
        private String customAgentField = "default";

        public String getCustomAgentField() {
            return customAgentField;
        }

        public void setCustomAgentField(String customAgentField) {
            this.customAgentField = customAgentField;
        }

        @Override
        protected void addFieldsToMap(Map<String, Object> map) {
            super.addFieldsToMap(map);
            putIfNotNull(map, "custom_agent_field", customAgentField);
        }
    }

    public static class TempEvent extends BaseLogEvent {
    }

    public static class CustomLLMEvent extends BaseLogEvent {
    }

    public static class DetailedEvent extends BaseLogEvent {
        private String detailField = "";
        private int detailCount;

        public String getDetailField() {
            return detailField;
        }

        public void setDetailField(String detailField) {
            this.detailField = detailField;
        }

        public int getDetailCount() {
            return detailCount;
        }

        public void setDetailCount(int detailCount) {
            this.detailCount = detailCount;
        }

        @Override
        protected void addFieldsToMap(Map<String, Object> map) {
            putIfNotNull(map, "detail_field", detailField);
            putIfNotNull(map, "detail_count", detailCount);
        }
    }

    public static class SerializableEvent extends BaseLogEvent {
        private String extraData = "";

        public String getExtraData() {
            return extraData;
        }

        public void setExtraData(String extraData) {
            this.extraData = extraData;
        }

        @Override
        protected void addFieldsToMap(Map<String, Object> map) {
            putIfNotNull(map, "extra_data", extraData);
        }
    }

    public static class ValidatedEvent extends BaseLogEvent {
    }
}
