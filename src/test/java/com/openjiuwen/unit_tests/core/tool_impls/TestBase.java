/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.tool_impls;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.core.foundation.tool.Tool;

/**
 * Tests for base tool implementation.
 * <p>
 * Mirrors Python's tests.unit_tests.harness.tools base tool tests.
 * Tests base class for all tools and ToolOutput structure.
 */
class TestBase {

    @Test
    @Tag("level0")
    void testBaseToolExists() {
        assertNotNull(AbstractHarnessTool.class);
    }

    @Test
    @Tag("level0")
    void testToolOutputExists() {
        assertNotNull(ToolOutput.class);
    }

    @Test
    @Tag("level0")
    void testToolBaseClassExists() {
        assertNotNull(Tool.class);
    }

    @Test
    @Tag("level1")
    void testToolOutputConstruction() {
        ToolOutput successOutput = new ToolOutput(true, "data", null);
        assertTrue(successOutput.isSuccess());
        assertEquals("data", successOutput.getData());
        assertNull(successOutput.getError());

        ToolOutput errorOutput = new ToolOutput(false, null, "error message");
        assertFalse(errorOutput.isSuccess());
        assertNull(errorOutput.getData());
        assertEquals("error message", errorOutput.getError());
    }

    @Test
    @Tag("level1")
    void testToolOutputWithDataMap() {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("path", "/test/path");
        data.put("size", 100);
        data.put("content", "test content");

        ToolOutput output = new ToolOutput(true, data, null);
        assertTrue(output.isSuccess());
        assertNotNull(output.getData());
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> resultData = (java.util.Map<String, Object>) output.getData();
        assertEquals("/test/path", resultData.get("path"));
        assertEquals(100, resultData.get("size"));
        assertEquals("test content", resultData.get("content"));
    }

    @Test
    @Tag("level1")
    void testToolOutputWithList() {
        java.util.List<String> files = java.util.List.of("file1.txt", "file2.txt", "file3.txt");
        ToolOutput output = new ToolOutput(true, files, null);
        
        assertTrue(output.isSuccess());
        
        @SuppressWarnings("unchecked")
        java.util.List<String> resultFiles = (java.util.List<String>) output.getData();
        assertEquals(3, resultFiles.size());
        assertTrue(resultFiles.contains("file1.txt"));
    }

    @Test
    @Tag("level0")
    void testBaseToolMethods() {
        assertTrue(AbstractHarnessTool.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level0")
    void testToolOutputMethods() {
        assertTrue(ToolOutput.class.getDeclaredMethods().length > 0);
    }

    @Test
    @Tag("level1")
    void testAbstractHarnessToolFields() throws Exception {
        // Check that AbstractHarnessTool has the sysOperation field
        java.lang.reflect.Field[] fields = AbstractHarnessTool.class.getDeclaredFields();
        assertTrue(fields.length > 0);
        
        // Check protected field exists
        boolean hasSysOpField = false;
        for (java.lang.reflect.Field field : fields) {
            if (field.getName().equals("sysOperation")) {
                hasSysOpField = true;
                break;
            }
        }
        assertTrue(hasSysOpField);
    }
}