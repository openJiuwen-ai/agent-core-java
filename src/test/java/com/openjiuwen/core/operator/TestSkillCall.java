/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.skill_call.SkillCallOperator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SkillCallOperator.
 * Mirrors Python's tests/unit_tests/core/operator/test_skill_call.py
 */
class TestSkillCall {

    @Nested
    @DisplayName("SkillCall tests")
    class SkillCallTests {

        @Test
        @DisplayName("test operator id includes skill name")
        void testOperatorIdIncludesSkillName() {
            // Test operator_id includes skill name.
            SkillCallOperator op = new SkillCallOperator("skill-a");
            assertEquals("skill_call_skill-a", op.getOperatorId());
        }

        @Test
        @DisplayName("test get tunables")
        void testGetTunables() {
            // Test getTunables returns experiences tunable.
            SkillCallOperator op = new SkillCallOperator("skill-a");
            Map<String, TunableSpec> tunables = op.getTunables();

            assertTrue(tunables.containsKey("experiences"));
        }

        @Test
        @DisplayName("test set parameter experiences")
        void testSetParameterExperiences() {
            // Test setParameter for experiences adds to staged records.
            SkillCallOperator op = new SkillCallOperator("skill-a");

            op.setParameter("experiences", "record1");

            List<Object> staged = op.getStagedRecords();
            assertTrue(staged.contains("record1"));
        }

        @Test
        @DisplayName("test discard staged")
        void testDiscardStaged() {
            // Test discardStaged clears staged records.
            SkillCallOperator op = new SkillCallOperator("skill-a");

            op.setParameter("experiences", "record1");
            op.discardStaged();

            List<Object> staged = op.getStagedRecords();
            assertTrue(staged.isEmpty());
        }

        @Test
        @DisplayName("test get state")
        void testGetState() {
            // Test getState returns staged and flushed records.
            SkillCallOperator op = new SkillCallOperator("skill-a");
            op.setParameter("experiences", "record1");

            Map<String, Object> state = op.getState();

            assertTrue(state.containsKey("staged_records"));
            assertTrue(state.containsKey("flushed_records"));
        }
    }
}