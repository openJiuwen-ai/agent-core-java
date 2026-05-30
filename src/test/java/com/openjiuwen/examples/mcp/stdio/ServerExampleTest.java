/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.mcp.stdio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerExampleTest {

    @Test
    void textToolsMirrorPythonBehavior() {
        assertEquals(0, ServerExample.wordCount("   "));
        assertEquals(3, ServerExample.wordCount("one  two\nthree"));
        assertEquals(11, ServerExample.charCount("hello world"));
        assertEquals("cba", ServerExample.reverseText("abc"));
        assertEquals("HELLO", ServerExample.toUppercase("hello"));
        assertEquals("hello", ServerExample.toLowercase("HELLO"));
        assertEquals(2, ServerExample.countLines("a\nb\n"));
    }
}
