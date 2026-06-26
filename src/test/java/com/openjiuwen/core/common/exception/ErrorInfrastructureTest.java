/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import com.openjiuwen.core.common.schema.BaseCard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused regression tests for translated error helpers and status mapping.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/core/common/test_errors.py}.
 * </p>
 */
class ErrorInfrastructureTest {

    @Test
    void buildErrorReturnsMappedInstance() {
        BaseError error = ErrorHelper.buildError(
                StatusCode.AGENT_TOOL_EXECUTION_ERROR,
                "failed",
                Map.of("tool", "xyz"),
                null,
                null);

        assertEquals(StatusCode.AGENT_TOOL_EXECUTION_ERROR.getCode(), error.getCode());
        assertEquals(Map.of("tool", "xyz"), error.getDetails());
    }

    @Test
    void raiseErrorThrowsResolvedType() {
        BaseError expected = StatusMapping.resolveException(StatusCode.AGENT_TOOL_EXECUTION_ERROR);
        BaseError caught = assertThrows(BaseError.class, () -> ErrorHelper.raiseError(StatusCode.AGENT_TOOL_EXECUTION_ERROR));
        assertInstanceOf(expected.getClass(), caught);
    }

    @Test
    void buildErrorMapsToolNotFoundToStatusMappingType() {
        BaseError expected = StatusMapping.resolveException(StatusCode.AGENT_TOOL_NOT_FOUND);

        BaseError error = ErrorHelper.buildError(StatusCode.AGENT_TOOL_NOT_FOUND);

        assertInstanceOf(expected.getClass(), error);
        assertEquals(StatusCode.AGENT_TOOL_NOT_FOUND.getCode(), error.getCode());
    }

    @Test
    void formatTemplateUsesMissingMarkers() {
        String rendered = BaseError.formatTemplate(StatusCode.WORKFLOW_EXECUTION_ERROR.getErrmsg(), Map.of());
        assertTrue(rendered.contains("<missing:"));
    }

    @Test
    void frameworkExecutionValidationFlagsMatchPythonSemantics() {
        assertTrue(new ExecutionError(StatusCode.WORKFLOW_EXECUTION_ERROR).isRecoverable());
        assertTrue(new FrameworkError(StatusCode.ERROR).isFatal());
        assertFalse(new ValidationError(StatusCode.SCHEMA_VALIDATE_INVALID).isRecoverable());
    }

    @Test
    void runnerTerminationCarriesReason() {
        RunnerTermination termination = new RunnerTermination("max iterations", StatusCode.RUNNER_TERMINATION_ERROR);
        assertEquals("max iterations", termination.getReason());
    }

    @Test
    void toolErrorKeepsCopiedCardAndPreservesPythonDetailsBehavior() {
        BaseCard card = new BaseCard("card-1", "search_tool", "Search tool");
        ToolError error = new ToolError(StatusCode.TOOL_EXECUTION_ERROR, "tool fail", Map.of("extra", "info"), null, card, null);

        assertNotNull(error.getCard());
        assertEquals("search_tool", error.getCard().getName());
        assertNull(error.getDetails());
    }

    @Test
    void cryptErrorKeepsFrameworkFlags() {
        CryptError error = new CryptError(StatusCode.ERROR);
        assertTrue(error.isFatal());
        assertFalse(error.isRecoverable());
    }

    @Test
    void statusMappingResolvesExpectedFamilies() {
        assertInstanceOf(ValidationError.class, StatusMapping.resolveException(StatusCode.WORKFLOW_EXECUTE_INPUT_INVALID));
        assertInstanceOf(FrameworkError.class, StatusMapping.resolveException(StatusCode.COMPONENT_LLM_INIT_FAILED));
        assertInstanceOf(AgentError.class, StatusMapping.resolveException(StatusCode.AGENT_TEAM_EXECUTION_ERROR));
    }

    @Test
    void buildStatusExceptionMapCoversAllCodes() {
        var mapping = StatusMapping.buildStatusExceptionMap();
        for (StatusCode status : StatusCode.values()) {
            assertTrue(mapping.containsKey(status), "missing mapping for " + status.name());
        }
    }

    @Test
    void helperThrowersMatchFamilies() {
        assertThrows(FrameworkError.class, () -> ErrorHelper.systemError(StatusCode.ERROR));
        assertThrows(ValidationError.class, () -> ErrorHelper.validateError(StatusCode.SCHEMA_VALIDATE_INVALID));
        assertThrows(Termination.class, () -> ErrorHelper.terminate(StatusCode.SUCCESS));
    }

    private static void assertFalse(boolean value) {
        assertTrue(!value);
    }
}
