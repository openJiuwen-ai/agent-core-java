/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.sse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerExampleTest {

    @Test
    void calculatorToolsMirrorPythonBehavior() {
        assertEquals(5.0d, ServerExample.add(2.0d, 3.0d), 0.0d);
        assertEquals(4.0d, ServerExample.subtract(7.0d, 3.0d), 0.0d);
        assertEquals(12.0d, ServerExample.multiply(3.0d, 4.0d), 0.0d);
        assertEquals(3.0d, (Double) ServerExample.divide(9.0d, 3.0d), 0.0d);
        assertEquals("Error: division by zero", ServerExample.divide(9.0d, 0.0d));
        assertEquals(8.0d, ServerExample.power(2.0d, 3.0d), 0.0d);
    }
}
