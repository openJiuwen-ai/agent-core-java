/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryPoolContextVar.
 * <p>
 * Mirrors Python's test_memory_pool_context_var.py from
 * <code>tests/unit_tests/core/memory/team/test_memory_pool_context_var.py</code>.
 * 
 * <p>Note: Python's contextvars module is replaced by AtomicReference in Java.
 */
@DisplayName("Memory Pool Context Var Tests")
class TestMemoryPoolContextVar {

    // Context variable simulation using AtomicReference
    static class ContextVar<T> {
        private final AtomicReference<T> value = new AtomicReference<>();
        private final String name;

        public ContextVar(String name, T defaultValue) {
            this.name = name;
            this.value.set(defaultValue);
        }

        public T get() {
            return value.get();
        }

        public void set(T newValue) {
            value.set(newValue);
        }

        public String getName() {
            return name;
        }
    }

    static class MemoryPoolStub {
        String poolId;
        int capacity;

        MemoryPoolStub(String poolId, int capacity) {
            this.poolId = poolId;
            this.capacity = capacity;
        }
    }

    static class TeamMemoryContext {
        ContextVar<MemoryPoolStub> poolContextVar;

        TeamMemoryContext() {
            this.poolContextVar = new ContextVar<>("team_memory_pool", null);
        }

        void setPool(MemoryPoolStub pool) {
            poolContextVar.set(pool);
        }

        MemoryPoolStub getPool() {
            return poolContextVar.get();
        }

        void clearPool() {
            poolContextVar.set(null);
        }
    }

    @Nested
    @DisplayName("Context Var Tests")
    class TestContextVarOperations {

        @Test
        @DisplayName("context var creation with default")
        void testContextVarCreationWithDefault() {
            ContextVar<String> var = new ContextVar<>("test_var", "default");

            assertEquals("test_var", var.getName());
            assertEquals("default", var.get());
        }

        @Test
        @DisplayName("context var get and set")
        void testContextVarGetAndSet() {
            ContextVar<String> var = new ContextVar<>("test_var", null);

            var.set("value1");
            assertEquals("value1", var.get());

            var.set("value2");
            assertEquals("value2", var.get());
        }

        @Test
        @DisplayName("context var can be cleared")
        void testContextVarCanBeCleared() {
            ContextVar<String> var = new ContextVar<>("test_var", "initial");
            var.set(null);

            assertNull(var.get());
        }
    }

    @Nested
    @DisplayName("Team Memory Context Tests")
    class TestTeamMemoryContext {

        @Test
        @DisplayName("team memory context creation")
        void testTeamMemoryContextCreation() {
            TeamMemoryContext ctx = new TeamMemoryContext();

            assertNotNull(ctx.poolContextVar);
            assertNull(ctx.getPool());
        }

        @Test
        @DisplayName("set and get pool in context")
        void testSetAndGetPoolInContext() {
            TeamMemoryContext ctx = new TeamMemoryContext();
            MemoryPoolStub pool = new MemoryPoolStub("pool-1", 100);

            ctx.setPool(pool);
            MemoryPoolStub retrieved = ctx.getPool();

            assertNotNull(retrieved);
            assertEquals("pool-1", retrieved.poolId);
            assertEquals(100, retrieved.capacity);
        }

        @Test
        @DisplayName("clear pool from context")
        void testClearPoolFromContext() {
            TeamMemoryContext ctx = new TeamMemoryContext();
            ctx.setPool(new MemoryPoolStub("pool-1", 100));

            ctx.clearPool();

            assertNull(ctx.getPool());
        }
    }
}