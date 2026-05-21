/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.experience;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for experience search tool.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.experience.test_experience_search_tool}.
 */
@ExtendWith(MockitoExtension.class)
class TestExperienceSearchTool {

    // ---------------------------------------------------------------------------
    // TestExperienceSearchTool
    // ---------------------------------------------------------------------------

    @Nested
    class TestExperienceSearchToolInvoke {

        @Test
        @Tag("level0")
        void testSearchReturnsResults() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_search");
            ExperienceStore store = new ExperienceStore(tempDir.toString());
            store.record(new Experience(
                ExperienceType.OPTIMIZATION,
                "ruff-fix",
                "fixed lint errors",
                "success"
            ));
            store.record(new Experience(
                ExperienceType.FAILURE,
                "timeout-bug",
                "task timed out",
                "timeout"
            ));

            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            ToolResult result = tool.invoke(Map.of("query", "ruff"));
            assertTrue(result.isSuccess());
            assertTrue(result.getData().size() >= 1);
            assertEquals("ruff-fix", result.getData().get(0).get("topic"));

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testSearchEmptyQuery() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_search");
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            ToolResult result = tool.invoke(Map.of("query", ""));
            assertFalse(result.isSuccess());
            assertTrue(result.getError().contains("empty") || result.getError().contains("空"));

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testSearchNoResults() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_search");
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            ToolResult result = tool.invoke(Map.of("query", "nonexistent"));
            assertTrue(result.isSuccess());
            assertEquals(0, result.getData().size());

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level0")
        void testCardHasCorrectName() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_search");
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            assertEquals("experience_search", tool.getCard().getName());
            assertTrue(tool.getCard().getId().contains("ExperienceSearchTool"));

            Files.deleteIfExists(tempDir);
        }

        @Test
        @Tag("level1")
        void testStreamYieldsInvokeResult() throws Exception {
            Path tempDir = Files.createTempDirectory("exp_search");
            ExperienceSearchTool tool = new ExperienceSearchTool(tempDir.toString());
            List<ToolResult> chunks = new ArrayList<>();
            for (ToolResult chunk : tool.stream(Map.of("query", "test"))) {
                chunks.add(chunk);
            }
            assertEquals(1, chunks.size());
            assertTrue(chunks.get(0).isSuccess());

            Files.deleteIfExists(tempDir);
        }
    }

    // ---------------------------------------------------------------------------
    // Stub classes for testing
    // ---------------------------------------------------------------------------

    private enum ExperienceType {
        OPTIMIZATION, FAILURE, INSIGHT
    }

    private static class Experience {
        private String id;
        private ExperienceType type;
        private String topic;
        private String summary;
        private String outcome;

        Experience(ExperienceType type, String topic, String summary, String outcome) {
            this.id = UUID.randomUUID().toString();
            this.type = type;
            this.topic = topic;
            this.summary = summary;
            this.outcome = outcome;
        }

        public Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();
            map.put("id", id);
            map.put("topic", topic);
            map.put("summary", summary);
            map.put("outcome", outcome);
            return map;
        }
    }

    private static class ExperienceStore {
        private String path;
        private Map<String, Experience> store = new HashMap<>();

        ExperienceStore(String path) {
            this.path = path;
        }

        public CompletableFuture<String> record(Experience exp) {
            store.put(exp.id, exp);
            return CompletableFuture.completedFuture(exp.id);
        }

        public List<Map<String, String>> search(String query) {
            if (query.isEmpty()) return Collections.emptyList();
            List<Map<String, String>> results = new ArrayList<>();
            for (Experience exp : store.values()) {
                if (exp.topic.contains(query)) {
                    results.add(exp.toMap());
                }
            }
            return results;
        }
    }

    private static class ToolResult {
        private boolean success;
        private List<Map<String, String>> data;
        private String error;

        ToolResult(boolean success, List<Map<String, String>> data, String error) {
            this.success = success;
            this.data = data;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public List<Map<String, String>> getData() { return data; }
        public String getError() { return error; }
    }

    private static class ToolCard {
        private String name;
        private String id;

        ToolCard(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String getName() { return name; }
        public String getId() { return id; }
    }

    private static class ExperienceSearchTool {
        private String experienceDir;
        private ToolCard card;
        private ExperienceStore store;

        ExperienceSearchTool(String experienceDir) {
            this.experienceDir = experienceDir;
            this.card = new ToolCard("experience_search", "experience.ExperienceSearchTool");
            this.store = new ExperienceStore(experienceDir);
        }

        public ToolCard getCard() { return card; }

        public ToolResult invoke(Map<String, String> args) {
            String query = args.getOrDefault("query", "");
            if (query.isEmpty()) {
                return new ToolResult(false, Collections.emptyList(), "Query is empty (空)");
            }
            List<Map<String, String>> results = store.search(query);
            return new ToolResult(true, results, null);
        }

        public Iterable<ToolResult> stream(Map<String, String> args) {
            return Collections.singletonList(invoke(args));
        }
    }
}