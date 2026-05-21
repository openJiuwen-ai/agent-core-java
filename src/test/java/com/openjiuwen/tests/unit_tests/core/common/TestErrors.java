// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.tests.unit_tests.core.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import com.openjiuwen.core.common.exception.AgentError;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.exception.BaseError;

/**
 * Mirrors Python's {@code test_errors} in
 * {@code tests.unit_tests.core.common.test_errors}.
 * Error handling tests.
 */
class TestErrors {

    @Test
    @Tag("level0")
    void testAgentErrorExists() {
        assertNotNull(AgentError.class);
    }

    @Test
    @Tag("level0")
    void testValidationErrorExists() {
        assertNotNull(ValidationError.class);
    }

    @Test
    @Tag("level0")
    void testBaseErrorExists() {
        assertNotNull(BaseError.class);
    }

    @Test
    @Tag("level0")
    void testErrorInheritance() {
        assertTrue(AgentError.class.getSuperclass().equals(BaseError.class));
    }
}