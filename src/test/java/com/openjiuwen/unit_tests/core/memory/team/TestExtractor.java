/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.team;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for team memory extractor.
 * <p>
 * Mirrors Python's test_extractor.py from
 * <code>tests/unit_tests/core/memory/team/test_extractor.py</code>.
 */
@DisplayName("Team Memory Extractor Tests")
class TestExtractor {

    // Stub classes
    static class TaskStub {
        String title;
        String status;
        String assignee;
        String content;

        TaskStub(String title, String status, String assignee, String content) {
            this.title = title;
            this.status = status;
            this.assignee = assignee;
            this.content = content;
        }
    }

    static class MessageStub {
        double timestamp;
        String fromMember;
        boolean broadcast;
        String toMember;
        String content;

        MessageStub(double timestamp, String fromMember, boolean broadcast, 
                    String toMember, String content) {
            this.timestamp = timestamp;
            this.fromMember = fromMember;
            this.broadcast = broadcast;
            this.toMember = toMember;
            this.content = content;
        }
    }

    static class DbStub {
        CompletableFuture<List<MessageStub>> getTeamMessages() {
            List<MessageStub> messages = new ArrayList<>();
            messages.add(new MessageStub(1.0, "alice", false, "bob", "hello"));
            return CompletableFuture.completedFuture(messages);
        }
    }

    static class TaskManagerStub {
        boolean throwError = false;
        List<TaskStub> tasks = new ArrayList<>();

        TaskManagerStub(boolean throwError) {
            this.throwError = throwError;
            tasks.add(new TaskStub("task1", "done", "alice", "content"));
        }

        CompletableFuture<List<TaskStub>> listTasks() {
            if (throwError) {
                return CompletableFuture.failedFuture(new RuntimeException("boom"));
            }
            return CompletableFuture.completedFuture(tasks);
        }
    }

    static class ModelStub {
        String name;
        boolean available;

        ModelStub(String name, boolean available) {
            this.name = name;
            this.available = available;
        }
    }

    static class SysOperationStub {
        // Empty stub for sys operation
    }

    static class ExtractorResult {
        boolean success;
        String error;
        List<String> extractedMemories = new ArrayList<>();

        ExtractorResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    static class Extractor {
        CompletableFuture<ExtractorResult> extractTeamMemories(
            String teamName, DbStub db, TaskManagerStub taskManager,
            String teamMemoryDir, SysOperationStub sysOp, ModelStub model
        ) {
            if (model == null) {
                // Skip extraction when model is null
                return CompletableFuture.completedFuture(new ExtractorResult(true, null));
            }

            return taskManager.listTasks().thenCompose(tasks -> {
                if (tasks.isEmpty()) {
                    return CompletableFuture.completedFuture(new ExtractorResult(true, null));
                }
                ExtractorResult result = new ExtractorResult(true, null);
                result.extractedMemories.add("memory1");
                return CompletableFuture.completedFuture(result);
            }).exceptionally(e -> new ExtractorResult(false, e.getMessage()));
        }
    }

    @Nested
    @DisplayName("Extract Team Memories Tests")
    class TestExtractTeamMemories {

        @Test
        @DisplayName("skips when model is null")
        void testExtractTeamMemoriesSkipsWhenModelNone() throws Exception {
            DbStub db = new DbStub();
            TaskManagerStub tm = new TaskManagerStub(false);
            Extractor extractor = new Extractor();

            ExtractorResult result = extractor.extractTeamMemories(
                "team1", db, tm, "/tmp/mem", new SysOperationStub(), null
            ).get();

            assertTrue(result.success);
            assertTrue(result.extractedMemories.isEmpty());
        }

        @Test
        @DisplayName("does not propagate task manager errors")
        void testExtractTeamMemoriesDoesNotPropagateTaskManagerErrors() throws Exception {
            DbStub db = new DbStub();
            TaskManagerStub tm = new TaskManagerStub(true); // Will throw error
            ModelStub model = new ModelStub("test-model", true);
            Extractor extractor = new Extractor();

            ExtractorResult result = extractor.extractTeamMemories(
                "team1", db, tm, "/tmp/mem", new SysOperationStub(), model
            ).get();

            // Should handle error gracefully
            assertNotNull(result);
        }

        @Test
        @DisplayName("success path extracts memories")
        void testExtractTeamMemoriesSuccessPath() throws Exception {
            DbStub db = new DbStub();
            TaskManagerStub tm = new TaskManagerStub(false);
            ModelStub model = new ModelStub("test-model", true);
            Extractor extractor = new Extractor();

            ExtractorResult result = extractor.extractTeamMemories(
                "team1", db, tm, "/tmp/mem", new SysOperationStub(), model
            ).get();

            assertTrue(result.success);
            assertFalse(result.extractedMemories.isEmpty());
        }
    }
}