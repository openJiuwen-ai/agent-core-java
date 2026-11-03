package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusCodeTest {

    @Test
    void successKeepsPythonCodeAndMessage() {
        assertEquals(0, StatusCode.SUCCESS.getCode());
        assertEquals(0, StatusCode.SUCCESS.code());
        assertEquals("success", StatusCode.SUCCESS.getErrmsg());
        assertEquals("success", StatusCode.SUCCESS.errmsg());
    }

    @Test
    void workflowComponentIdInvalidKeepsTemplatePlaceholders() {
        String message = StatusCode.WORKFLOW_COMPONENT_ID_INVALID.errmsg();

        assertEquals(100010, StatusCode.WORKFLOW_COMPONENT_ID_INVALID.code());
        assertTrue(message.contains("{comp_id}"));
        assertTrue(message.contains("{reason}"));
        assertTrue(message.contains("{workflow}"));
    }

    @Test
    void modelStatusCodesBuildModelErrorAndKeepErrorMessageParam() {
        BaseError error = ErrorHelper.buildError(
                StatusCode.MODEL_CALL_FAILED,
                "error_msg",
                "upstream http 500: body");

        assertTrue(error instanceof ModelError);
        assertEquals("upstream http 500: body", error.getParams().get("error_msg"));
    }
}
