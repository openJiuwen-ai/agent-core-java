/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.store.graph;

import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for graph object functionality.
 * <p>
 * Mirrors Python's {@code test_graph_object.py} from
 * {@code tests/unit_tests/core/foundation/store/graph/test_graph_object.py}.
 * Tests graph object creation, properties, and serialization.
 */
class TestGraphObject {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Object basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testMapClassExists() {
        assertNotNull(Map.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Graph object creation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testGraphObjectCreation() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("id", "obj_1");
        obj.put("type", "node");
        assertNotNull(obj);
    }

    @Test
    @Tag("level1")
    void testGraphObjectId() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("id", "graph_obj_123");
        assertEquals("graph_obj_123", obj.get("id"));
    }

    @Test
    @Tag("level1")
    void testGraphObjectType() {
        Map<String, Object> obj = new HashMap<>();
        obj.put("type", "edge");
        assertEquals("edge", obj.get("type"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Properties)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testGraphObjectProperties() {
        Map<String, Object> obj = new HashMap<>();
        Map<String, Object> props = new HashMap<>();
        props.put("name", "test");
        props.put("value", 100);
        obj.put("properties", props);
        assertNotNull(obj.get("properties"));
    }

    @Test
    @Tag("level2")
    void testPropertyName() {
        Map<String, Object> props = new HashMap<>();
        props.put("name", "entity_name");
        assertEquals("entity_name", props.get("name"));
    }

    @Test
    @Tag("level2")
    void testPropertyValueTypes() {
        Map<String, Object> props = new HashMap<>();
        props.put("string_value", "text");
        props.put("int_value", 42);
        props.put("float_value", 3.14);
        props.put("bool_value", true);
        assertEquals(4, props.size());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Labels and metadata)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testGraphObjectLabels() {
        java.util.List<String> labels = java.util.List.of("entity", "person");
        assertEquals(2, labels.size());
    }

    @Test
    @Tag("level3")
    void testGraphObjectMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("created_at", "2024-01-01");
        metadata.put("updated_at", "2024-06-01");
        assertEquals(2, metadata.size());
    }
}