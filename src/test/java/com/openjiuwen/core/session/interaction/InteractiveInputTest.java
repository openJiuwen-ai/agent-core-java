/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InteractiveInput}.
 * <p>
 * Ported from Python's {@code test_interactive_input.py}.
 */
class InteractiveInputTest {

    @Test
    @DisplayName("null raw inputs throws BaseError")
    void testInvalidRawInputs() {
        BaseError ex = assertThrows(BaseError.class, () -> new InteractiveInput(null));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("update with rawInputs existing throws BaseError")
    void testInvalidUpdate() {
        InteractiveInput input = new InteractiveInput("some-raw-input");
        BaseError ex = assertThrows(BaseError.class, () -> input.update("id", "value"));
        assertNotNull(ex);
    }

    @Test
    @DisplayName("default constructor allows update")
    void testDefaultConstructorAllowsUpdate() {
        InteractiveInput input = new InteractiveInput();
        assertDoesNotThrow(() -> input.update("nodeId", "value"));
        assertEquals("value", input.getUserInputs().get("nodeId"));
    }

    @Test
    @DisplayName("valid raw inputs constructor works")
    void testValidRawInputs() {
        InteractiveInput input = new InteractiveInput("hello");
        assertEquals("hello", input.getRawInputs());
        assertTrue(input.getUserInputs().isEmpty());
    }

    @Test
    @DisplayName("update with null nodeId throws BaseError")
    void testUpdateNullNodeId() {
        InteractiveInput input = new InteractiveInput();
        assertThrows(BaseError.class, () -> input.update(null, "value"));
    }

    @Test
    @DisplayName("update with null value throws BaseError")
    void testUpdateNullValue() {
        InteractiveInput input = new InteractiveInput();
        assertThrows(BaseError.class, () -> input.update("id", null));
    }
}
