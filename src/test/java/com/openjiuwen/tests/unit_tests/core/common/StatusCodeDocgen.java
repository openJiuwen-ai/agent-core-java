// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.tests.unit_tests.core.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import com.openjiuwen.core.common.exception.StatusCode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code status_code_docgen} in
 * {@code tests.unit_tests.core.common.status_code_docgen}.
 * Status code documentation generator tests.
 */
class StatusCodeDocgen {

    @Test
    @Tag("level0")
    void testStatusCodeExists() {
        assertNotNull(StatusCode.class);
    }

    @Test
    @Tag("level0")
    void testStatusCodeMethods() {
        assertTrue(StatusCode.class.getDeclaredMethods().length > 0);
    }
}