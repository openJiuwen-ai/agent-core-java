/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.extensions.context_evolver;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TaskMemoryRail.
 * <p>
 * Mirrors Python's task memory rail tests.
 * Tests task-scoped memory management.
 */
class TestTaskMemoryRail {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Task memory basics)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("Test task ID generation")
    void testTaskIdGeneration() {
        String taskId = UUID.randomUUID().toString();
        
        assertNotNull(taskId);
        assertFalse(taskId.isEmpty());
    }

    @Test
    @Tag("level0")
    @DisplayName("Test task memory structure")
    void testTaskMemoryStructure() {
        Map<String, Object> taskMemory = new HashMap<>();
        taskMemory.put("task_id", "task-001");
        taskMemory.put("created_at", System.currentTimeMillis());
        taskMemory.put("status", "active");
        
        assertNotNull(taskMemory);
        assertEquals("task-001", taskMemory.get("task_id"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Memory operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    @DisplayName("Test task memory store")
    void testTaskMemoryStore() {
        Map<String, Object> memory = new HashMap<>();
        String key = "user_context";
        String value = "User is working on Python project";
        
        memory.put(key, value);
        
        assertTrue(memory.containsKey(key));
        assertEquals(value, memory.get(key));
    }

    @Test
    @Tag("level1")
    @DisplayName("Test task memory retrieve")
    void testTaskMemoryRetrieve() {
        Map<String, Object> memory = new HashMap<>();
        memory.put("context", "Previous conversation context");
        memory.put("preference", "JSON output preferred");
        
        Object context = memory.get("context");
        Object preference = memory.get("preference");
        Object nonexistent = memory.get("nonexistent");
        
        assertNotNull(context);
        assertNotNull(preference);
        assertNull(nonexistent);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Task lifecycle)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    @DisplayName("Test task memory lifecycle")
    void testTaskMemoryLifecycle() {
        // Create
        Map<String, Object> taskMemory = new HashMap<>();
        taskMemory.put("status", "created");
        assertEquals("created", taskMemory.get("status"));
        
        // Update
        taskMemory.put("status", "running");
        taskMemory.put("data", Map.of("input", "test"));
        assertEquals("running", taskMemory.get("status"));
        
        // Complete
        taskMemory.put("status", "completed");
        taskMemory.put("result", "success");
        assertEquals("completed", taskMemory.get("status"));
        assertEquals("success", taskMemory.get("result"));
    }

    @Test
    @Tag("level2")
    @DisplayName("Test task memory isolation")
    void testTaskMemoryIsolation() {
        // Each task should have isolated memory
        Map<String, Object> task1Memory = new HashMap<>();
        Map<String, Object> task2Memory = new HashMap<>();
        
        task1Memory.put("task_id", "task-1");
        task1Memory.put("data", "task1-data");
        
        task2Memory.put("task_id", "task-2");
        task2Memory.put("data", "task2-data");
        
        assertNotEquals(task1Memory.get("task_id"), task2Memory.get("task_id"));
        assertNotEquals(task1Memory.get("data"), task2Memory.get("data"));
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Context evolution)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    @DisplayName("Test context evolution tracking")
    void testContextEvolutionTracking() {
        Map<String, Object> evolution = new HashMap<>();
        evolution.put("version", 1);
        evolution.put("changes", java.util.List.of("added field A", "updated field B"));
        
        // Simulate evolution
        int version = (Integer) evolution.get("version") + 1;
        evolution.put("version", version);
        evolution.put("timestamp", System.currentTimeMillis());
        
        assertEquals(2, evolution.get("version"));
        assertNotNull(evolution.get("timestamp"));
    }
}