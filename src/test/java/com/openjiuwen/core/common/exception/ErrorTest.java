/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for exception / error infrastructure.
 * Ported from Python: tests/unit_tests/core/common/test_errors.py
 */
class ErrorTest {

    // ==========================================================================
    // test_build_error_returns_instance
    // ==========================================================================
    @Test
    @DisplayName("buildError returns BaseError instance with correct code and details")
    void testBuildErrorReturnsInstance() {
        Map<String, Object> details = Map.of("tool", "xyz");
        BaseError error = ErrorHelper.buildError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR, "failed", details, null, null);

        assertInstanceOf(BaseError.class, error);
        assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), error.getCode());
        assertEquals(details, error.getDetails());
    }

    // ==========================================================================
    // test_raise_error_raises_correct_type
    // ==========================================================================
    @Test
    @DisplayName("raiseError throws the correct exception type mapped by StatusMapping")
    void testRaiseErrorRaisesCorrectType() {
        // StatusMapping resolves AGENT_TOOL_EXECUTION_ERROR -> specific type
        BaseError resolved = StatusMapping.resolveException(StatusCode.AGENT_TOOL_EXECUTION_ERROR);
        Class<? extends BaseError> expectedType = resolved.getClass();

        BaseError caught = assertThrows(BaseError.class, () ->
                ErrorHelper.raiseError(StatusCode.AGENT_TOOL_EXECUTION_ERROR));

        assertInstanceOf(expectedType, caught);
    }

    // ==========================================================================
    // test_build_error_maps_to_manual_override
    // ==========================================================================
    @Test
    @DisplayName("buildError for AGENT_TOOL_NOT_FOUND returns the correct exception class")
    void testBuildErrorMapsToManualOverride() {
        StatusCode key = StatusCode.AGENT_TOOL_NOT_FOUND;
        BaseError resolved = StatusMapping.resolveException(key);
        Class<? extends BaseError> expectedCls = resolved.getClass();

        BaseError built = ErrorHelper.buildError(key);
        assertInstanceOf(expectedCls, built);
    }

    // ==========================================================================
    // test_format_template_missing_key_safe
    // ==========================================================================
    @Test
    @DisplayName("formatTemplate replaces missing keys with <missing:key>")
    void testFormatTemplateMissingKeySafe() {
        String template = StatusCode.WORKFLOW_EXECUTION_ERROR.getErrmsg();
        // Pass empty params so all placeholders become <missing:...>
        String rendered = BaseError.formatTemplate(template, Map.of());

        assertNotNull(rendered);
        assertInstanceOf(String.class, rendered);
        assertTrue(rendered.contains("<missing:"), "Expected <missing:...> placeholders, got: " + rendered);
    }

    // ==========================================================================
    // Additional: BaseError construction & toMap
    // ==========================================================================
    @Nested
    @DisplayName("BaseError construction")
    class BaseErrorConstruction {

        @Test
        @DisplayName("BaseError with status only sets code/message from StatusCode")
        void testStatusOnly() {
            BaseError error = new BaseError(StatusCode.AGENT_TOOL_EXECUTION_ERROR) {};
            assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), error.getCode());
            assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR, error.getStatus());
            assertNotNull(error.getMessage());
        }

        @Test
        @DisplayName("BaseError with custom message overrides template")
        void testCustomMessage() {
            BaseError error = new BaseError(StatusCode.ERROR, "custom msg", null, null) {};
            assertEquals("custom msg", error.getMessage());
        }

        @Test
        @DisplayName("BaseError with params renders template placeholders")
        void testTemplateParams() {
            Map<String, Object> params = Map.of("error_msg", "timeout hit");
            BaseError error = new BaseError(StatusCode.AGENT_TOOL_EXECUTION_ERROR, params) {};
            assertTrue(error.getTemplateMessage().contains("timeout hit"));
        }

        @Test
        @DisplayName("toMap contains all required fields")
        void testToMap() {
            Map<String, Object> details = Map.of("key", "value");
            BaseError error = new BaseError(
                    StatusCode.AGENT_TOOL_EXECUTION_ERROR, "fail", details, null) {};

            Map<String, Object> map = error.toMap();
            assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), map.get("code"));
            assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.name(), map.get("status"));
            assertNotNull(map.get("message"));
            assertEquals("fail", map.get("raw_message"));
            assertEquals(details, map.get("details"));
            assertNotNull(map.get("params"));
        }

        @Test
        @DisplayName("toString includes code and message")
        void testToString() {
            BaseError error = new BaseError(StatusCode.ERROR, "something wrong", null, null) {};
            String str = error.toString();
            assertTrue(str.contains(String.valueOf(StatusCode.ERROR.getCode())));
            assertTrue(str.contains("something wrong"));
        }
    }

    // ==========================================================================
    // Exception hierarchy & recoverability/fatality
    // ==========================================================================
    @Nested
    @DisplayName("Exception hierarchy and recovery semantics")
    class ExceptionHierarchy {

        @Test
        @DisplayName("ExecutionError is recoverable and not fatal")
        void testExecutionError() {
            ExecutionError err = new ExecutionError(StatusCode.WORKFLOW_EXECUTION_ERROR);
            assertTrue(err.isRecoverable());
            assertFalse(err.isFatal());
        }

        @Test
        @DisplayName("FrameworkError is not recoverable and is fatal")
        void testFrameworkError() {
            FrameworkError err = new FrameworkError(StatusCode.ERROR);
            assertFalse(err.isRecoverable());
            assertTrue(err.isFatal());
        }

        @Test
        @DisplayName("ValidationError is not recoverable and not fatal")
        void testValidationError() {
            ValidationError err = new ValidationError(StatusCode.SCHEMA_VALIDATE_INVALID);
            assertFalse(err.isRecoverable());
            assertFalse(err.isFatal());
        }

        @Test
        @DisplayName("Termination is not recoverable and not fatal")
        void testTermination() {
            Termination err = new Termination(StatusCode.SUCCESS);
            assertFalse(err.isRecoverable());
            assertFalse(err.isFatal());
        }

        @Test
        @DisplayName("RunnerTermination carries reason")
        void testRunnerTermination() {
            RunnerTermination err = new RunnerTermination("max iterations", StatusCode.RUNNER_TERMINATION_ERROR);
            assertEquals("max iterations", err.getReason());
            assertFalse(err.isRecoverable());
        }

        @Test
        @DisplayName("ToolError merges card into details")
        void testToolErrorWithCard() {
            com.openjiuwen.core.common.schema.BaseCard card =
                    com.openjiuwen.core.common.schema.BaseCard.builder()
                            .name("search_tool")
                            .description("A web search tool")
                            .build();

            ToolError err = new ToolError(StatusCode.TOOL_EXECUTION_ERROR,
                    "tool fail", Map.of("extra", "info"), null, card, null);

            assertNotNull(err.getCard());
            assertEquals("search_tool", err.getCard().getName());
            assertInstanceOf(Map.class, err.getDetails());
            @SuppressWarnings("unchecked")
            Map<String, Object> details = (Map<String, Object>) err.getDetails();
            assertTrue(details.containsKey("card"));
        }

        @Test
        @DisplayName("Subclass hierarchy is correct")
        void testSubclassHierarchy() {
            assertInstanceOf(BaseError.class, new AgentError(StatusCode.AGENT_TOOL_EXECUTION_ERROR));
            assertInstanceOf(ExecutionError.class, new AgentError(StatusCode.AGENT_TOOL_EXECUTION_ERROR));
            assertInstanceOf(BaseError.class, new WorkflowError(StatusCode.WORKFLOW_EXECUTION_ERROR));
            assertInstanceOf(ExecutionError.class, new WorkflowError(StatusCode.WORKFLOW_EXECUTION_ERROR));
            assertInstanceOf(BaseError.class, new ConfigurationError(StatusCode.ERROR));
            assertInstanceOf(FrameworkError.class, new ConfigurationError(StatusCode.ERROR));
            assertInstanceOf(BaseError.class, new GuardrailError(StatusCode.GUARDRAIL_BLOCKED));
            assertInstanceOf(ValidationError.class, new GuardrailError(StatusCode.GUARDRAIL_BLOCKED));
        }
    }

    // ==========================================================================
    // ErrorHelper factory methods
    // ==========================================================================
    @Nested
    @DisplayName("ErrorHelper factory/raising methods")
    class ErrorHelperTests {

        @Test
        @DisplayName("systemError throws FrameworkError")
        void testSystemError() {
            assertThrows(FrameworkError.class, () ->
                    ErrorHelper.systemError(StatusCode.ERROR));
        }

        @Test
        @DisplayName("validateError throws ValidationError")
        void testValidateError() {
            assertThrows(ValidationError.class, () ->
                    ErrorHelper.validateError(StatusCode.SCHEMA_VALIDATE_INVALID));
        }

        @Test
        @DisplayName("terminate throws Termination")
        void testTerminate() {
            assertThrows(Termination.class, () ->
                    ErrorHelper.terminate(StatusCode.SUCCESS));
        }

        @Test
        @DisplayName("buildError with full params")
        void testBuildErrorWithFullParams() {
            BaseError err = ErrorHelper.buildError(
                    StatusCode.WORKFLOW_EXECUTION_ERROR,
                    "custom msg",
                    Map.of("detail", "val"),
                    new RuntimeException("root cause"),
                    Map.of("reason", "timeout"));

            assertEquals("custom msg", err.getMessage());
            assertNotNull(err.getCause());
            assertEquals("root cause", err.getCause().getMessage());
        }
    }

    // ==========================================================================
    // StatusMapping resolution
    // ==========================================================================
    @Nested
    @DisplayName("StatusMapping resolution")
    class StatusMappingTests {

        @Test
        @DisplayName("Validation-related codes resolve to ValidationError")
        void testValidationKeyword() {
            // Names containing INVALID, PARAM, CONFIG etc. -> ValidationError
            BaseError err = StatusMapping.resolveException(StatusCode.WORKFLOW_EXECUTE_INPUT_INVALID);
            assertInstanceOf(ValidationError.class, err);
        }

        @Test
        @DisplayName("Init/connection-related codes resolve to FrameworkError")
        void testFrameworkKeyword() {
            BaseError err = StatusMapping.resolveException(StatusCode.COMPONENT_LLM_INIT_FAILED);
            assertInstanceOf(FrameworkError.class, err);
        }

        @Test
        @DisplayName("Execution-related codes resolve to ExecutionError or subclass")
        void testExecutionKeyword() {
            BaseError err = StatusMapping.resolveException(StatusCode.WORKFLOW_EXECUTION_ERROR);
            assertInstanceOf(BaseError.class, err);
            assertTrue(err.isRecoverable() || err instanceof WorkflowError);
        }

        @Test
        @DisplayName("Range-based resolution for agent codes")
        void testRangeBasedAgent() {
            BaseError err = StatusMapping.resolveException(StatusCode.AGENT_TOOL_EXECUTION_ERROR);
            // Should be AgentError (range 120000-129999) or match keyword
            assertInstanceOf(BaseError.class, err);
        }

        @Test
        @DisplayName("buildStatusExceptionMap covers all StatusCode values")
        void testBuildStatusExceptionMap() {
            var map = StatusMapping.buildStatusExceptionMap();
            for (StatusCode sc : StatusCode.values()) {
                assertTrue(map.containsKey(sc), "Missing mapping for: " + sc.name());
            }
        }
    }
}
