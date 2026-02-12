/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InteractiveInput.
 * 
 * <p>Converted from Python: test_interactive_input.py</p>
 */
class InteractiveInputTest {
    
    @Test
    @DisplayName("invalid raw inputs throws exception")
    void testInvalidRawInputs() {
        JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, () -> {
            new InteractiveInput(null);
        });
        assertEquals(-1, ex.getErrorCode());
    }
    
    @Test
    @DisplayName("invalid update throws exception")
    void testInvalidUpdate() {
        InteractiveInput interactiveInput = new InteractiveInput();
        JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, () -> {
            interactiveInput.update("id", null);
        });
        assertEquals(-1, ex.getErrorCode());
    }
    
    @Test
    @DisplayName("empty constructor works")
    void testEmptyConstructor() {
        InteractiveInput input = new InteractiveInput();
        assertNotNull(input);
        assertNull(input.getRawInputs());
        assertTrue(input.getUserInputs().isEmpty());
    }
    
    @Test
    @DisplayName("static empty factory works")
    void testEmptyFactory() {
        InteractiveInput input = InteractiveInput.empty();
        assertNotNull(input);
        assertFalse(input.hasRawInputs());
    }
    
    @Test
    @DisplayName("update with valid inputs works")
    void testUpdateWithValidInputs() {
        InteractiveInput input = new InteractiveInput();
        input.update("node1", "value1");
        assertEquals("value1", input.getUserInputs().get("node1"));
    }
    
    @Test
    @DisplayName("update with null nodeId throws exception")
    void testUpdateWithNullNodeId() {
        InteractiveInput input = new InteractiveInput();
        assertThrows(JiuWenBaseException.class, () -> {
            input.update(null, "value");
        });
    }
    
    @Test
    @DisplayName("has raw inputs reflects state")
    void testHasRawInputsReflectsState() {
        InteractiveInput withRaw = new InteractiveInput("raw_data");
        assertTrue(withRaw.hasRawInputs());
        
        InteractiveInput withoutRaw = new InteractiveInput();
        assertFalse(withoutRaw.hasRawInputs());
    }
}

