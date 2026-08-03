/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's tests for
 * {@code openjiuwen/core/session/interaction/interactive_input.py}.
 */
class InteractiveInputTest {

    @Test
    @DisplayName("null raw inputs throws BaseError")
    void invalidRawInputsThrow() {
        BaseError ex = assertThrows(BaseError.class, () -> new InteractiveInput(null));
        assertNotNull(ex);
        assertEquals(111110, ex.getCode());
    }

    @Test
    @DisplayName("default constructor creates empty state")
    void defaultConstructorCreatesEmptyState() {
        InteractiveInput input = new InteractiveInput();

        assertNull(input.getRawInputs());
        assertNotNull(input.getUserInputs());
        assertTrue(input.getUserInputs().isEmpty());
    }

    @Test
    @DisplayName("valid raw inputs constructor works")
    void validRawInputsArePreserved() {
        InteractiveInput input = new InteractiveInput("hello");

        assertEquals("hello", input.getRawInputs());
        assertTrue(input.getUserInputs().isEmpty());
    }

    @Test
    @DisplayName("update with raw inputs existing throws BaseError")
    void updateWithRawInputsFails() {
        InteractiveInput input = new InteractiveInput("some-raw-input");
        BaseError ex = assertThrows(BaseError.class, () -> input.update("id", "value"));

        assertNotNull(ex);
        assertEquals(111110, ex.getCode());
    }

    @Test
    @DisplayName("default constructor allows ordered updates")
    void updateStoresInputs() {
        InteractiveInput input = new InteractiveInput();

        assertDoesNotThrow(() -> {
            input.update("node1", "value1");
            input.update("node2", "value2");
        });
        assertEquals("value1", input.getUserInputs().get("node1"));
        assertEquals("value2", input.getUserInputs().get("node2"));
    }

    @Test
    @DisplayName("update rejects null node or value")
    void updateRejectsNulls() {
        InteractiveInput input = new InteractiveInput();

        assertThrows(BaseError.class, () -> input.update(null, "value"));
        assertThrows(BaseError.class, () -> input.update("id", null));
    }
}
