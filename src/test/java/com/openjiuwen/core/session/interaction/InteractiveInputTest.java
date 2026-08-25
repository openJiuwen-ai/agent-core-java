/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("InteractiveInput is Java-serializable for Redis checkpointer")
    void testInteractiveInputIsSerializable() throws Exception {
        InteractiveInput input = new InteractiveInput("raw");
        input.getUserInputs().put("n1", "v1");

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(input);
        }
        try (java.io.ObjectInputStream ois =
                new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            InteractiveInput restored = (InteractiveInput) ois.readObject();
            assertEquals("raw", restored.getRawInputs());
            assertEquals("v1", restored.getUserInputs().get("n1"));
        }
    }
}
