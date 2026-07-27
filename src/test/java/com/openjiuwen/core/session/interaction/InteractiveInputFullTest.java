/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link InteractiveInput}.
 * <p>
 * Ported from Python's {@code test_interactive_input.py}.
 */
class InteractiveInputFullTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null raw inputs throws BaseError")
        void testNullRawInputsThrows() {
            BaseError ex = assertThrows(BaseError.class, () -> new InteractiveInput(null));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("valid raw inputs constructor works")
        void testValidRawInputs() {
            InteractiveInput input = new InteractiveInput("hello");
            assertEquals("hello", input.getRawInputs());
            assertTrue(input.getUserInputs().isEmpty());
        }

        @Test
        @DisplayName("default constructor creates empty state")
        void testDefaultConstructor() {
            InteractiveInput input = new InteractiveInput();
            assertNull(input.getRawInputs());
            assertNotNull(input.getUserInputs());
            assertTrue(input.getUserInputs().isEmpty());
        }

        @Test
        @DisplayName("raw inputs can be non-string object")
        void testRawInputsNonString() {
            InteractiveInput input = new InteractiveInput(42);
            assertEquals(42, input.getRawInputs());
        }
    }

    @Nested
    @DisplayName("Update validation")
    class UpdateTests {

        @Test
        @DisplayName("update with rawInputs existing throws BaseError")
        void testUpdateWithRawInputsThrows() {
            InteractiveInput input = new InteractiveInput("some-raw-input");
            BaseError ex = assertThrows(BaseError.class, () -> input.update("id", "value"));
            assertNotNull(ex);
        }

        @Test
        @DisplayName("update with null nodeId throws BaseError")
        void testUpdateNullNodeIdThrows() {
            InteractiveInput input = new InteractiveInput();
            assertThrows(BaseError.class, () -> input.update(null, "value"));
        }

        @Test
        @DisplayName("update with null value throws BaseError")
        void testUpdateNullValueThrows() {
            InteractiveInput input = new InteractiveInput();
            assertThrows(BaseError.class, () -> input.update("id", null));
        }

        @Test
        @DisplayName("default constructor allows valid update")
        void testDefaultConstructorAllowsUpdate() {
            InteractiveInput input = new InteractiveInput();
            assertDoesNotThrow(() -> input.update("nodeId", "value"));
            assertEquals("value", input.getUserInputs().get("nodeId"));
        }

        @Test
        @DisplayName("multiple updates accumulate")
        void testMultipleUpdates() {
            InteractiveInput input = new InteractiveInput();
            input.update("node1", "value1");
            input.update("node2", "value2");
            assertEquals("value1", input.getUserInputs().get("node1"));
            assertEquals("value2", input.getUserInputs().get("node2"));
            assertEquals(2, input.getUserInputs().size());
        }

        @Test
        @DisplayName("update overwrites existing key")
        void testUpdateOverwrites() {
            InteractiveInput input = new InteractiveInput();
            input.update("node1", "value1");
            input.update("node1", "value2");
            assertEquals("value2", input.getUserInputs().get("node1"));
            assertEquals(1, input.getUserInputs().size());
        }
    }

    @Nested
    @DisplayName("Setter operations")
    class SetterTests {

        @Test
        @DisplayName("setRawInputs changes raw inputs")
        void testSetRawInputs() {
            InteractiveInput input = new InteractiveInput();
            input.setRawInputs("new-raw");
            assertEquals("new-raw", input.getRawInputs());
        }
    }
}
