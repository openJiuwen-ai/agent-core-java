/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.memmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for memory unit dataclasses.
 * Corresponds to Python: test_memory_unit.py
 */
class MemoryUnitTest {

    @Nested
    @DisplayName("Tests for BaseMemoryUnit")
    class TestBaseMemoryUnit {

        @Test
        @DisplayName("Test creation and equality of BaseMemoryUnit")
        void testBaseMemoryUnitCreateAndEquality() {
            BaseMemoryUnit unit1 = new BaseMemoryUnit(
                MemoryType.VARIABLE,
                "user_1",
                "scope_1"
            );
            BaseMemoryUnit unit2 = new BaseMemoryUnit(
                MemoryType.VARIABLE,
                "user_1",
                "scope_1"
            );

            assertEquals(MemoryType.VARIABLE, unit1.getMemType());
            assertEquals("user_1", unit1.getUserId());
            assertEquals("scope_1", unit1.getScopeId());
            assertEquals(unit1, unit2);
        }
    }

    @Nested
    @DisplayName("Tests for UserProfileUnit")
    class TestUserProfileUnit {

        @Test
        @DisplayName("Test UserProfileUnit creation with defaults and all fields")
        void testUserProfileUnitCreateWithDefaultsAndAllFields() {
            // Test with minimal fields
            UserProfileUnit minimal = UserProfileUnit.builder()
                .userId("user_123")
                .scopeId("scope_456")
                .profileType("preference")
                .profileMem("likes music")
                .build();

            assertEquals("user_123", minimal.getUserId());
            assertEquals("preference", minimal.getProfileType());
            assertEquals("likes music", minimal.getProfileMem());
            // Default values
            assertEquals(MemoryType.USER_PROFILE, minimal.getMemType());
            assertNull(minimal.getScore());
            assertEquals("", minimal.getMemId());
            assertFalse(minimal.isImplicit());

            // Test with all fields
            UserProfileUnit full = UserProfileUnit.builder()
                .userId("user_123")
                .scopeId("scope_456")
                .profileType("preference")
                .profileMem("likes music")
                .score(0.95)
                .messageMemId("msg_789")
                .memId("mem_001")
                .isImplicit(true)
                .reasoning("inferred from conversation")
                .contextSummary("user mentioned favorite songs")
                .build();

            assertEquals(0.95, full.getScore());
            assertEquals("mem_001", full.getMemId());
            assertTrue(full.isImplicit());
            assertTrue(full instanceof BaseMemoryUnit);
        }
    }

    @Nested
    @DisplayName("Tests for VariableUnit")
    class TestVariableUnit {

        @Test
        @DisplayName("Test VariableUnit creation with defaults")
        void testVariableUnitCreateWithDefaults() {
            VariableUnit unit = VariableUnit.builder()
                .userId("user_123")
                .scopeId("scope_456")
                .variableName("user_name")
                .variableMem("John Doe")
                .build();

            assertEquals("user_123", unit.getUserId());
            assertEquals("user_name", unit.getVariableName());
            assertEquals("John Doe", unit.getVariableMem());
            // Default values
            assertEquals(MemoryType.VARIABLE, unit.getMemType());
            assertEquals("", unit.getMemId());
            assertTrue(unit instanceof BaseMemoryUnit);
        }

        @Test
        @DisplayName("Test VariableUnit with custom mem_id")
        void testVariableUnitCreateWithMemId() {
            VariableUnit unit = VariableUnit.builder()
                .userId("user_123")
                .scopeId("scope_456")
                .variableName("key")
                .variableMem("value")
                .memId("var_001")
                .build();

            assertEquals("var_001", unit.getMemId());
        }
    }
}

