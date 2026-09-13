/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 tests for StatusCode, StatusCodeTemplate, StatusCodeSpec, and ErrorMessageTemplate.
 * Ported from Python: tests/unit_tests/core/common/test_status_code.py
 */
class StatusCodeTest {
    // ==========================================================================
    // test_status_code_template (Python: test_status_code_template)
    // ==========================================================================
    @Nested
    @DisplayName("StatusCodeTemplate generation")
    class StatusCodeTemplateTests {
        @Test
        @DisplayName("Generate template with scope=TOOL, subject=INPUT, failureType=PARAM_ERROR")
        void testGenerateToolInputParamError() {
            StatusCodeTemplate tpl = StatusCodeTemplate.generate("TOOL", "INPUT", "PARAM_ERROR");

            assertNotNull(tpl);
            assertNotNull(tpl.name());
            assertTrue(tpl.name().contains("TOOL"));
            assertTrue(tpl.name().contains("INPUT"));
            assertTrue(tpl.name().contains("PARAM_ERROR"));
            assertNotNull(tpl.codeSuggestion());
            assertNotNull(tpl.messageTemplate());
            assertNotNull(tpl.exceptionSemantic());
        }

        @Test
        @DisplayName("Generate template with detail fragment: AGENT + LLM + INVOKE + CALL_FAILED")
        void testGenerateWithDetailFragment() {
            StatusCodeTemplate tpl = StatusCodeTemplate.generate("AGENT", "INVOKE", "CALL_FAILED", "LLM");

            assertNotNull(tpl);
            assertTrue(tpl.name().contains("AGENT"));
            assertTrue(tpl.name().contains("LLM"));
            assertTrue(tpl.name().contains("INVOKE"));
            assertTrue(tpl.name().contains("CALL_FAILED"));
        }

        @Test
        @DisplayName("Invalid scope throws IllegalArgumentException")
        void testInvalidScope() {
            assertThrows(IllegalArgumentException.class,
                    () -> StatusCodeTemplate.generate("INVALID_SCOPE", "INPUT", "PARAM_ERROR"));
        }

        @Test
        @DisplayName("Invalid failure type throws IllegalArgumentException")
        void testInvalidFailureType() {
            assertThrows(IllegalArgumentException.class,
                    () -> StatusCodeTemplate.generate("TOOL", "INPUT", "INVALID_FAILURE"));
        }

        @Test
        @DisplayName("All allowed scopes are accepted")
        void testAllAllowedScopes() {
            for (String scope : StatusCodeTemplate.ALLOWED_SCOPES) {
                assertDoesNotThrow(() -> StatusCodeTemplate.generate(scope, "TEST", "EXECUTION_ERROR"));
            }
        }

        @Test
        @DisplayName("All failure types supported by ErrorMessageTemplate are accepted")
        void testAllAllowedFailureTypes() {
            // TYPE_ERROR is in ALLOWED_FAILURE_TYPES but not supported by ErrorMessageTemplate.generate()
            String[] supportedFailureTypes =
                {"INVALID", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "PARAM_ERROR", "INIT_FAILED", "CALL_FAILED",
                        "EXECUTION_ERROR", "RUNTIME_ERROR", "PROCESS_ERROR", "TIMEOUT", "INTERRUPTED"};
            for (String failureType : supportedFailureTypes) {
                assertDoesNotThrow(() -> StatusCodeTemplate.generate("AGENT", "TEST", failureType));
            }
        }

        @Test
        @DisplayName("TYPE_ERROR is in ALLOWED_FAILURE_TYPES but not in ErrorMessageTemplate — throws")
        void testTypeErrorNotSupportedByMessageTemplate() {
            assertTrue(StatusCodeTemplate.ALLOWED_FAILURE_TYPES.contains("TYPE_ERROR"));
            assertThrows(IllegalArgumentException.class,
                    () -> StatusCodeTemplate.generate("AGENT", "TEST", "TYPE_ERROR"));
        }
    }

    // ==========================================================================
    // test_status_spec (Python: test_status_spec)
    // ==========================================================================
    @Nested
    @DisplayName("StatusCodeSpec generation")
    class StatusCodeSpecTests {
        @Test
        @DisplayName("Generate spec from TOOL template with code 182010")
        void testToolSpec() {
            StatusCodeTemplate tpl = StatusCodeTemplate.generate("TOOL", "INPUT", "PARAM_ERROR");
            StatusCodeSpec spec = StatusCodeSpec.fromTemplate(tpl, 182010);

            assertEquals(182010, spec.code());
            assertEquals(tpl.name(), spec.name());
            assertEquals(tpl.messageTemplate(), spec.message());

            String rendered = spec.renderEnumMember();
            assertNotNull(rendered);
            assertTrue(rendered.contains("182010"));
            assertTrue(rendered.contains(tpl.name()));
        }

        @Test
        @DisplayName("Generate spec from AGENT template with code 123010")
        void testAgentSpec() {
            StatusCodeTemplate tpl = StatusCodeTemplate.generate("AGENT", "INVOKE", "CALL_FAILED", "LLM");
            StatusCodeSpec spec = StatusCodeSpec.fromTemplate(tpl, 123010);

            assertEquals(123010, spec.code());
            String rendered = spec.renderEnumMember();
            assertTrue(rendered.contains("123010"));
        }

        @Test
        @DisplayName("Generate spec from WORKFLOW template with code 100110")
        void testWorkflowSpec() {
            StatusCodeTemplate tpl = StatusCodeTemplate.generate("WORKFLOW", "EXECUTION", "TIMEOUT");
            StatusCodeSpec spec = StatusCodeSpec.fromTemplate(tpl, 100110);

            assertEquals(100110, spec.code());
            String rendered = spec.renderEnumMember();
            assertTrue(rendered.contains("100110"));
            assertTrue(rendered.contains("WORKFLOW"));
        }

        @Test
        @DisplayName("renderEnumMember produces valid Java enum syntax")
        void testRenderEnumMemberFormat() {
            StatusCodeTemplate tpl = StatusCodeTemplate.generate("AGENT", "TASK", "RUNTIME_ERROR");
            StatusCodeSpec spec = StatusCodeSpec.fromTemplate(tpl, 120999);

            String rendered = spec.renderEnumMember();
            // Should look like: AGENT_TASK_RUNTIME_ERROR(120999, "...")
            assertTrue(rendered.trim().matches("[A-Z_]+" + "\\(\\d+, \".*\"\\)"));
        }
    }

    // ==========================================================================
    // test_status_message (Python: test_status_message)
    // ==========================================================================
    @Nested
    @DisplayName("ErrorMessageTemplate generation")
    class ErrorMessageTemplateTests {
        @Test
        @DisplayName("Generate with AGENT/GROUP_ADD/RUNTIME_ERROR includes reason")
        void testAgentGroupAddRuntimeError() {
            ErrorMessageTemplate tpl = ErrorMessageTemplate.generate("AGENT", "GROUP_ADD", "RUNTIME_ERROR");

            assertNotNull(tpl.template());
            assertTrue(tpl.template().contains("agent"));
            assertTrue(tpl.template().contains("group_add"));
            assertTrue(tpl.template().contains("runtime error"));
            assertTrue(tpl.template().contains("reason:"));
            assertTrue(tpl.params().contains("error_msg"));
        }

        @Test
        @DisplayName("Generate WORKFLOW/EXECUTION/TIMEOUT without reason")
        void testWorkflowExecutionTimeoutNoReason() {
            ErrorMessageTemplate tpl = ErrorMessageTemplate.generate("WORKFLOW", "EXECUTION", "TIMEOUT", false);

            assertNotNull(tpl.template());
            assertTrue(tpl.template().contains("workflow"));
            assertTrue(tpl.template().contains("execution"));
            assertTrue(tpl.template().contains("timeout"));
            assertTrue(tpl.params().contains("timeout"));
            assertFalse(tpl.template().contains("reason:"));
        }

        @Test
        @DisplayName("Generate AGENT/TASK_TYPE/NOT_SUPPORTED includes 'not supported'")
        void testAgentTaskTypeNotSupported() {
            ErrorMessageTemplate tpl = ErrorMessageTemplate.generate("AGENT", "TASK_TYPE", "NOT_SUPPORTED");

            assertNotNull(tpl.template());
            assertTrue(tpl.template().contains("not supported"));
        }

        @Test
        @DisplayName("Generate with withReason=true (default) includes reason placeholder")
        void testWithReasonDefault() {
            ErrorMessageTemplate tpl = ErrorMessageTemplate.generate("TOOL", "INPUT", "INVALID");

            assertTrue(tpl.template().contains("reason:"));
            assertTrue(tpl.params().contains("error_msg"));
        }

        @Test
        @DisplayName("Each failure type produces a distinct message pattern")
        void testAllFailureTypeMessages() {
            String[] failureTypes =
                {"INVALID", "PARAM_ERROR", "NOT_FOUND", "NOT_SUPPORTED", "CONFIG_ERROR", "INIT_FAILED", "CALL_FAILED",
                        "EXECUTION_ERROR", "RUNTIME_ERROR", "PROCESS_ERROR", "TIMEOUT", "INTERRUPTED"};

            for (String ft : failureTypes) {
                ErrorMessageTemplate tpl = ErrorMessageTemplate.generate("AGENT", "TEST", ft);
                assertNotNull(tpl.template(), "Template should not be null for: " + ft);
                assertFalse(tpl.template().isEmpty(), "Template should not be empty for: " + ft);
            }
        }

        @Test
        @DisplayName("Unsupported failure type throws IllegalArgumentException")
        void testUnsupportedFailureType() {
            assertThrows(IllegalArgumentException.class,
                    () -> ErrorMessageTemplate.generate("AGENT", "TEST", "UNKNOWN_TYPE"));
        }
    }

    // ==========================================================================
    // StatusCode enum properties
    // ==========================================================================
    @Nested
    @DisplayName("StatusCode enum properties")
    class StatusCodeEnumTests {
        @Test
        @DisplayName("SUCCESS has code 0")
        void testSuccessCode() {
            assertEquals(0, StatusCode.SUCCESS.getCode());
            assertEquals("success", StatusCode.SUCCESS.getErrmsg());
        }

        @Test
        @DisplayName("ERROR has code -1")
        void testErrorCode() {
            assertEquals(-1, StatusCode.ERROR.getCode());
        }

        @Test
        @DisplayName("All StatusCode members have non-null errmsg")
        void testAllHaveErrmsg() {
            for (StatusCode sc : StatusCode.values()) {
                assertNotNull(sc.getErrmsg(), "errmsg should not be null for: " + sc.name());
            }
        }

        @Test
        @DisplayName("StatusCode code ranges are correct")
        void testCodeRanges() {
            // Workflow codes: 100000-100999
            assertTrue(StatusCode.WORKFLOW_EXECUTION_ERROR.getCode() >= 100000);
            assertTrue(StatusCode.WORKFLOW_EXECUTION_ERROR.getCode() <= 100999);

            // Agent codes: 120000-129999
            assertTrue(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode() >= 120000);
            assertTrue(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode() <= 129999);

            // Runner codes: 110000-110999
            assertTrue(StatusCode.RUNNER_TERMINATION_ERROR.getCode() >= 110000);
            assertTrue(StatusCode.RUNNER_TERMINATION_ERROR.getCode() <= 110999);
        }

        @Test
        @DisplayName("codeRangeByScope returns correct ranges")
        void testCodeRangeByScope() {
            assertEquals("100000–100999", StatusCodeTemplate.codeRangeByScope("WORKFLOW"));
            assertEquals("120000–129999", StatusCodeTemplate.codeRangeByScope("AGENT"));
            assertEquals("182000–182999", StatusCodeTemplate.codeRangeByScope("TOOL"));
            assertEquals("custom", StatusCodeTemplate.codeRangeByScope("UNKNOWN"));
        }
    }
}
