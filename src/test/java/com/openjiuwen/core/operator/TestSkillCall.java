/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.operator;

import com.openjiuwen.core.operator.skill_call.SkillCallOperator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        @DisplayName("test flush records to store does not mutate staged queue")
        void testFlushRecordsToStoreDoesNotMutateStagedQueue() throws Exception {
            SkillCallOperator op = new SkillCallOperator("skill-a");
            op.setParameter("experiences", "later-record");
            RecordingStore store = new RecordingStore();

            SkillCallOperator.FlushResult result =
                    op.flushRecordsToStore(store, List.of("approved-record")).get();

            assertEquals(List.of(List.of("skill-a", "approved-record")), store.calls);
            assertEquals(1, result.getFlushedCount());
            assertTrue(result.getRemainingRecords().isEmpty());
            assertEquals(List.of("later-record"), op.getStagedRecords());
        }

        @Test
        @DisplayName("test flush records to store returns remaining tail on failure")
        void testFlushRecordsToStoreReturnsRemainingTailOnFailure() throws Exception {
            SkillCallOperator op = new SkillCallOperator("skill-a");
            RecordingStore store = new RecordingStore();
            store.failOnRecord = "r2";

            SkillCallOperator.FlushResult result =
                    op.flushRecordsToStore(store, List.of("r1", "r2", "r3")).get();

            assertEquals(1, result.getFlushedCount());
            assertEquals(List.of("r2", "r3"), result.getRemainingRecords());
            assertTrue(op.getStagedRecords().isEmpty());
            assertEquals(List.of("r1"), op.getFlushedRecords());
        }

        @Test
        @DisplayName("test flush to store preserves records staged during io")
        void testFlushToStorePreservesRecordsStagedDuringIo() throws Exception {
            SkillCallOperator op = new SkillCallOperator("skill-a");
            op.setParameter("experiences", "r1");
            RecordingStore store = new RecordingStore();
            store.onAppend = record -> {
                if ("r1".equals(record)) {
                    op.setParameter("experiences", "r2");
                }
            };

            int flushed = op.flushToStore(store).get();

            assertEquals(1, flushed);
            assertEquals(List.of("r2"), op.getStagedRecords());
        }
    }

    private static class RecordingStore {
        private final List<List<Object>> calls = new ArrayList<>();
        private String failOnRecord;
        private java.util.function.Consumer<Object> onAppend;

        public void appendRecord(String skillName, Object record) {
            if (failOnRecord != null && failOnRecord.equals(record)) {
                throw new RuntimeException("disk full");
            }
            calls.add(List.of(skillName, record));
            if (onAppend != null) {
                onAppend.accept(record);
            }
        }
    }
}
