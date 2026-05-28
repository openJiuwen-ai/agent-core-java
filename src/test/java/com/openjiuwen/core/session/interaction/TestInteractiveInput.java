/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InteractiveInput.
 * Mirrors Python's tests/unit_tests/core/session/interaction/test_interactive_input.py
 */
class TestInteractiveInput {

    @Nested
    @DisplayName("InteractiveInput tests")
    class InputTests {

        @Test
        @DisplayName("test invalid raw inputs - null should throw BaseError")
        void testInvalidRawInputs() {
            BaseError thrown = assertThrows(BaseError.class, () -> {
                new InteractiveInput(null);
            });
            assertEquals(StatusCode.INTERACTION_INPUT_INVALID.getCode(), thrown.getCode());
        }

        @Test
        @DisplayName("test invalid update - null value should throw BaseError")
        void testInvalidUpdate() {
            BaseError thrown = assertThrows(BaseError.class, () -> {
                InteractiveInput interactiveInput = new InteractiveInput();
                interactiveInput.update("id", null);
            });
            assertEquals(StatusCode.INTERACTION_INPUT_INVALID.getCode(), thrown.getCode());
        }
    }
}
