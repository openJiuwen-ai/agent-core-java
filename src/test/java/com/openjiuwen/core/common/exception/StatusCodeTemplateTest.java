/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code openjiuwen.core.common.exception.code_template} in
 * {@code openjiuwen/core/common/exception/code_template.py}.
 */
class StatusCodeTemplateTest {

    @Test
    void generateStatusCodeMirrorsPythonSemantics() {
        StatusCodeTemplate template = StatusCodeTemplate.generateStatusCode(
                "WORKFLOW",
                "TASK",
                "TIMEOUT",
                "EXECUTE");

        assertEquals("WORKFLOW_EXECUTE_TASK_TIMEOUT", template.name());
        assertEquals("100000-100999", template.codeSuggestion());
        assertEquals("ExecutionError", template.exceptionSemantic());
        assertEquals("workflow task timeout ({timeout}s), reason: {error_msg}", template.messageTemplate());

        StatusCodeSpec spec = StatusCodeTemplate.generateStatusCodeSpec(template, 100123);
        assertEquals("WORKFLOW_EXECUTE_TASK_TIMEOUT", spec.name());
        assertEquals(100123, spec.code());
        assertEquals("    WORKFLOW_EXECUTE_TASK_TIMEOUT = (100123, \"workflow task timeout ({timeout}s), reason: {error_msg}\")",
                spec.renderEnumMember());
    }

    @Test
    void invalidInputsRaise() {
        assertThrows(IllegalArgumentException.class,
                () -> StatusCodeTemplate.generateStatusCode("UNKNOWN", "TASK", "TIMEOUT"));
        assertThrows(IllegalArgumentException.class,
                () -> StatusCodeTemplate.generateStatusCode("WORKFLOW", "TASK", "UNKNOWN"));
    }
}
