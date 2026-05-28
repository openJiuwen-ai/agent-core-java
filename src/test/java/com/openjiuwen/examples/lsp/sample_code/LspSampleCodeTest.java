/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.lsp.sample_code;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSP sample code with intentional type warnings.
 * <p>
 * Mirrors Python's {@code test} in
 * {@code examples.lsp.sample_code.test}.
 * <p>
 * Python uses runtime type hints that are not enforced; Java's static type
 * system catches these at compile time. This test class uses {@link Object}
 * fields to simulate the same loose behaviour and validates the logic.
 */
class LspSampleCodeTest {

    @Test
    void addReturnsSum() {
        int result = add(1, 2);
        assertEquals(3, result);
    }

    @Test
    void xFieldHoldsStringDespiteIntAnnotation() {
        Object x = "not_an_integer";
        assertInstanceOf(String.class, x);
        assertEquals("not_an_integer", x);
    }

    @Test
    void resultFieldHoldsIntDespiteStrAnnotation() {
        Object result = add(1, 2);
        assertInstanceOf(Integer.class, result);
        assertEquals(3, result);
    }

    private int add(int a, int b) {
        return a + b;
    }
}
