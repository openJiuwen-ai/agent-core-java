/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OperationMode enum.
 */
class OperationModeTest {

    @Test
    @DisplayName("OperationMode.LOCAL has value 'local'")
    void testLocalValue() {
        assertEquals("local", OperationMode.LOCAL.getValue());
    }

    @Test
    @DisplayName("OperationMode.SANDBOX has value 'sandbox'")
    void testSandboxValue() {
        assertEquals("sandbox", OperationMode.SANDBOX.getValue());
    }

    @Test
    @DisplayName("fromString parses 'local' correctly")
    void testFromStringLocal() {
        assertEquals(OperationMode.LOCAL, OperationMode.fromString("local"));
    }

    @Test
    @DisplayName("fromString parses 'sandbox' correctly")
    void testFromStringSandbox() {
        assertEquals(OperationMode.SANDBOX, OperationMode.fromString("sandbox"));
    }

    @Test
    @DisplayName("fromString is case-insensitive")
    void testFromStringCaseInsensitive() {
        assertEquals(OperationMode.LOCAL, OperationMode.fromString("LOCAL"));
        assertEquals(OperationMode.SANDBOX, OperationMode.fromString("Sandbox"));
    }

    @Test
    @DisplayName("fromString throws on invalid value")
    void testFromStringInvalid() {
        assertThrows(IllegalArgumentException.class, () -> OperationMode.fromString("invalid"));
    }
}
