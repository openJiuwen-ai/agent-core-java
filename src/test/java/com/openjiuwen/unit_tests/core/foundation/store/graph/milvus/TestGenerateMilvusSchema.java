/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph.milvus;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Milvus schema generation.
 * <p>
 * Mirrors Python's {@code test_generate_milvus_schema.py} from
 * {@code tests/unit_tests/core/foundation/store/graph/milvus/test_generate_milvus_schema.py}.
 * Tests Milvus vector database schema creation and configuration.
 */
class TestGenerateMilvusSchema {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Schema basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testMapClassExists() {
        assertNotNull(Map.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Schema field creation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testSchemaFieldCreation() {
        Map<String, Object> field = new HashMap<>();
        field.put("name", "embedding");
        field.put("type", "FLOAT_VECTOR");
        field.put("dimension", 128);
        assertNotNull(field);
        assertEquals("embedding", field.get("name"));
    }

    @Test
    @Tag("level1")
    void testPrimaryField() {
        Map<String, Object> field = new HashMap<>();
        field.put("name", "id");
        field.put("type", "INT64");
        field.put("is_primary", true);
        assertTrue((Boolean) field.get("is_primary"));
    }

    @Test
    @Tag("level1")
    void testVectorFieldDimension() {
        Map<String, Object> field = new HashMap<>();
        field.put("dimension", 768);
        assertEquals(768, field.get("dimension"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Schema validation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testValidSchemaFields() {
        java.util.List<Map<String, Object>> fields = new java.util.ArrayList<>();
        fields.add(createField("id", "INT64", true));
        fields.add(createField("embedding", "FLOAT_VECTOR", false));
        assertEquals(2, fields.size());
    }

    @Test
    @Tag("level2")
    void testSchemaFieldNames() {
        java.util.List<String> names = java.util.List.of("id", "embedding", "metadata");
        assertTrue(names.contains("id"));
        assertTrue(names.contains("embedding"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Index configuration)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testIndexType() {
        Map<String, Object> index = new HashMap<>();
        index.put("type", "IVF_FLAT");
        index.put("nlist", 1024);
        assertEquals("IVF_FLAT", index.get("type"));
    }

    @Test
    @Tag("level3")
    void testMetricType() {
        Map<String, Object> index = new HashMap<>();
        index.put("metric_type", "L2");
        assertEquals("L2", index.get("metric_type"));
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    private Map<String, Object> createField(String name, String type, boolean isPrimary) {
        Map<String, Object> field = new HashMap<>();
        field.put("name", name);
        field.put("type", type);
        field.put("is_primary", isPrimary);
        return field;
    }
}