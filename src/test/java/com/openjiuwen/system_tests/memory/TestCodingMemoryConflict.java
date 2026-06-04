/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.memory;

import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_coding_memory_conflict.py} in
 * {@code tests/system_tests/memory/test_coding_memory_conflict.py}.
 */
public class TestCodingMemoryConflict {

    private Path tmpDir;
    private String codingMemoryDir;

    @BeforeEach
    void setUp() throws Exception {
        tmpDir = Files.createTempDirectory("coding_memory_conflict_");
        Path cmDir = tmpDir.resolve("coding_memory");
        Files.createDirectories(cmDir);
        codingMemoryDir = cmDir.toString();
        CodingMemoryToolContext.bind(new Workspace(tmpDir.toString(), "cn"), null, codingMemoryDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        CodingMemoryToolContext.reset();
        deleteTree(tmpDir);
    }

    @Nested
    class TestConflictResolutionWorkflow {

        @Test
        void testConflictDetectedThenReadAndEdit() {
            String initialContent = """
                    ---
                    name: User Role
                    description: User is a Python developer
                    type: user
                    ---

                    User is a senior Python developer familiar with Django and Flask.
                    """;
            Map<String, Object> result1 = CodingMemoryToolContext.write("user_role.md", initialContent);
            assertThat(result1).containsEntry("success", true).containsEntry("mode", "create");

            String similarContent = """
                    ---
                    name: Developer Role
                    description: User develops in Python
                    type: user
                    ---

                    User develops backend services using Python and Django framework.
                    """;
            Map<String, Object> result2 = CodingMemoryToolContext.write("developer_role.md", similarContent);
            assertThat(result2).containsEntry("success", true);
            assertThat(result2).containsKeys("conflict_detected", "conflicting_files");

            Map<String, Object> editResult = CodingMemoryToolContext.staticEdit("developer_role.md",
                    "User develops backend services using Python and Django framework.",
                    "User develops backend services using Python, Django, and also has experience with FastAPI.");
            assertThat(editResult).containsEntry("success", true);

            Map<String, Object> verifyResult = CodingMemoryToolContext.read("developer_role.md", null, null);
            assertThat(verifyResult.get("content").toString()).contains("FastAPI");
        }

        @Test
        void testAppendModeSelfConflictResolution() {
            String initialContent = """
                    ---
                    name: Project Setup
                    description: Project initialization steps
                    type: project
                    ---

                    Step 1: Install dependencies
                    Step 2: Configure database
                    """;
            assertThat(CodingMemoryToolContext.write("project_setup.md", initialContent))
                    .containsEntry("success", true);

            String appendContent = """
                    ---
                    name: Project Setup Extended
                    description: More project setup details
                    type: project
                    ---

                    Step 3: Run migrations
                    """;
            Map<String, Object> result2 = CodingMemoryToolContext.write("project_setup.md", appendContent);
            assertThat(result2).containsEntry("success", true).containsEntry("mode", "append");

            Map<String, Object> readResult = CodingMemoryToolContext.read("project_setup.md", null, null);
            assertThat(readResult.get("content").toString()).contains("Step 1", "Step 3");
        }
    }

    @Nested
    class TestRedundantHandling {

        @Test
        void testRedundantContentSkip() {
            String originalContent = """
                    ---
                    name: API Endpoint
                    description: User login API
                    type: reference
                    ---

                    POST /api/v1/login
                    Request: {username, password}
                    Response: {token, expires_in}
                    """;
            assertThat(CodingMemoryToolContext.write("api_login.md", originalContent))
                    .containsEntry("success", true);

            String redundantContent = """
                    ---
                    name: Login API
                    description: API for user login
                    type: reference
                    ---

                    POST /api/v1/login
                    Request: {username, password}
                    Response: {token, expires_in}
                    """;
            Map<String, Object> result2 = CodingMemoryToolContext.write("login_api.md", redundantContent);

            assertThat(result2).containsEntry("success", true);
            assertThat(result2.get("mode")).isIn("create", "append", "skip");
        }

        @Test
        void testNoActionNeededForSkip() {
            String content = """
                    ---
                    name: Test Memory
                    description: Test description
                    type: user
                    ---

                    Test content for skip scenario.
                    """;
            Map<String, Object> result = CodingMemoryToolContext.write("test_skip.md", content);

            assertThat(result).containsEntry("success", true);
            assertThat(result).containsEntry("conflict_detected", false);
        }
    }

    @Nested
    class TestConflictNoteFormat {

        @Test
        void testConflictNoteContainsReadInstruction() {
            String note = "Conflicts with: db_config.md. Use coding_memory_read to review, then coding_memory_edit to update.";
            assertThat(note.toLowerCase()).contains("coding_memory_read", "read");
        }

        @Test
        void testConflictNoteContainsEditInstruction() {
            String note = "Conflicts with: code_style.md. Use coding_memory_read to review, then coding_memory_edit to update.";
            assertThat(note.toLowerCase()).contains("coding_memory_edit", "edit");
        }
    }

    @Nested
    class TestWriteResultStructure {

        @Test
        void testSuccessfulWriteResultStructure() {
            String content = """
                    ---
                    name: Test Structure
                    description: Testing result structure
                    type: user
                    ---

                    Test content.
                    """;
            Map<String, Object> result = CodingMemoryToolContext.write("test_structure.md", content);

            assertThat(result).containsKeys("success", "path", "mode", "type", "conflict_detected", "conflicting_files");
            assertThat(result).containsEntry("success", true);
            assertThat(result.get("mode")).isIn("create", "append", "skip");
        }

        @Test
        void testFailedWriteResultStructure() {
            Map<String, Object> result = CodingMemoryToolContext.write("test_fail.md", "No frontmatter here");

            assertThat(result).containsEntry("success", false).containsKey("error");
            assertThat(result.get("error").toString().toLowerCase()).contains("frontmatter");
        }

        @Test
        void testConflictResultIncludesConflictingFiles() {
            String content = """
                    ---
                    name: Architecture Decision
                    description: Microservices architecture
                    type: project
                    ---

                    We use microservices architecture with Kubernetes.
                    """;
            Map<String, Object> result = CodingMemoryToolContext.write("arch.md", content);

            assertThat(result).containsEntry("success", true);
            assertThat(result).containsKey("conflicting_files");
            assertThat(result.get("conflicting_files")).isInstanceOf(List.class);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
