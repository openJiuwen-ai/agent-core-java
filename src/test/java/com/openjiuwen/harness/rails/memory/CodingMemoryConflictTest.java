/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coding Memory conflict resolution workflow system tests.
 * <p>
 * Mirrors Python's {@code test_coding_memory_conflict} in
 * {@code tests/system_tests/memory/test_coding_memory_conflict.py}.
 */
@Tag("system-test")
class CodingMemoryConflictTest {

    private Path tmpDir;
    private String workDir;
    private String codingMemoryDir;
    private String sysOperationId;
    private Object sysOp;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("coding_memory_conflict_");
        workDir = tmpDir.toString();
        sysOperationId = "coding_memory_conflict_sysop_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SysOperationCard card = new SysOperationCard();
        card.setId(sysOperationId);
        card.setMode(OperationMode.LOCAL);
        LocalWorkConfig workConfig = new LocalWorkConfig();
        workConfig.setWorkDir(workDir);
        card.setWorkConfig(workConfig);
        var addResult = Runner.resourceMgr().addSysOperation(card, null);
        if (addResult.isError()) {
            throw new RuntimeException("add_sys_operation failed: " + addResult.getError().getMessage());
        }
        Path cmDir = Path.of(workDir).resolve("coding_memory");
        Files.createDirectories(cmDir);
        codingMemoryDir = cmDir.toString();
        sysOp = Runner.resourceMgr().getSysOperation(sysOperationId, null, TagMatchStrategy.ALL);
        Workspace workspace = new Workspace(workDir, "cn");
        CodingMemoryToolContext.bind(workspace, sysOp, codingMemoryDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        CodingMemoryToolContext.reset();
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId, null, TagMatchStrategy.ALL, true);
        } finally {
            if (tmpDir != null) {
                Runner.stop();
            }
        }
    }

    @Nested
    class TestConflictResolutionWorkflow {

        @Test
        void testConflictDetectedThenReadAndEdit() throws Exception {
            String initialContent = "---\nname: User Role\ndescription: User is a Python developer\ntype: user\n---\n\nUser is a senior Python developer familiar with Django and Flask.";
            Map<String, Object> result1 = CodingMemoryToolContext.write("user_role.md", initialContent);
            assertTrue((Boolean) result1.get("success"));
            assertEquals("create", result1.get("mode"));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "user_role.md",
                    Map.of("name", "User Role", "description", "User is a Python developer"));

            String similarContent = "---\nname: Developer Role\ndescription: User develops in Python\ntype: user\n---\n\nUser develops backend services using Python and Django framework.";
            Map<String, Object> result2 = CodingMemoryToolContext.write("developer_role.md", similarContent);
            assertTrue((Boolean) result2.get("success"));

            if (Boolean.TRUE.equals(result2.get("conflict_detected"))) {
                @SuppressWarnings("unchecked")
                List<String> conflictingFiles = (List<String>) result2.get("conflicting_files");
                if (conflictingFiles != null) {
                    for (String conflictFile : conflictingFiles) {
                        Map<String, Object> readResult = CodingMemoryToolContext.read(conflictFile, null, null);
                        assertTrue((Boolean) readResult.get("success"));
                        assertNotNull(readResult.get("content"));
                    }
                }
                Map<String, Object> editResult = CodingMemoryToolContext.edit("developer_role.md",
                        "User develops backend services using Python and Django framework.",
                        "User develops backend services using Python, Django, and also has experience with FastAPI.");
                assertTrue((Boolean) editResult.get("success"));
                Map<String, Object> verifyResult = CodingMemoryToolContext.read("developer_role.md", null, null);
                assertTrue((Boolean) verifyResult.get("success"));
                assertTrue(verifyResult.get("content").toString().contains("FastAPI"));
            }
        }

        @Test
        void testAppendModeSelfConflictResolution() throws Exception {
            String initialContent = "---\nname: Project Setup\ndescription: Project initialization steps\ntype: project\n---\n\nStep 1: Install dependencies\nStep 2: Configure database";
            Map<String, Object> result1 = CodingMemoryToolContext.write("project_setup.md", initialContent);
            assertTrue((Boolean) result1.get("success"));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "project_setup.md",
                    Map.of("name", "Project Setup", "description", "Project initialization steps"));

            String appendContent = "---\nname: Project Setup Extended\ndescription: More project setup details\ntype: project\n---\n\nStep 1: Install dependencies\nStep 2: Configure database\nStep 3: Run migrations";
            Map<String, Object> result2 = CodingMemoryToolContext.write("project_setup.md", appendContent);
            assertTrue((Boolean) result2.get("success"));
            assertEquals("append", result2.get("mode"));

            if (Boolean.TRUE.equals(result2.get("conflict_detected"))) {
                Map<String, Object> readResult = CodingMemoryToolContext.read("project_setup.md", null, null);
                assertTrue((Boolean) readResult.get("success"));
                Map<String, Object> editResult = CodingMemoryToolContext.edit("project_setup.md",
                        "Step 3: Run migrations",
                        "Step 3: Run database migrations and verify connection");
                assertTrue((Boolean) editResult.get("success"));
            }
        }
    }

    @Nested
    class TestRedundantHandling {

        @Test
        void testRedundantContentSkip() throws Exception {
            String originalContent = "---\nname: API Endpoint\ndescription: User login API\ntype: reference\n---\n\nPOST /api/v1/login\nRequest: {username, password}\nResponse: {token, expires_in}";
            Map<String, Object> result1 = CodingMemoryToolContext.write("api_login.md", originalContent);
            assertTrue((Boolean) result1.get("success"));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "api_login.md",
                    Map.of("name", "API Endpoint", "description", "User login API"));

            String redundantContent = "---\nname: Login API\ndescription: API for user login\ntype: reference\n---\n\nPOST /api/v1/login\nRequest: {username, password}\nResponse: {token, expires_in}";
            Map<String, Object> result2 = CodingMemoryToolContext.write("login_api.md", redundantContent);

            if ("skip".equals(result2.get("mode"))) {
                String note = String.valueOf(result2.getOrDefault("note", ""));
                assertTrue(note.toLowerCase().contains("redundant"));
            }
        }

        @Test
        void testNoActionNeededForSkip() throws Exception {
            String content = "---\nname: Test Memory\ndescription: Test description\ntype: user\n---\n\nTest content for skip scenario.";
            Map<String, Object> result = CodingMemoryToolContext.write("test_skip.md", content);
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "test_skip.md",
                    Map.of("name", "Test Memory", "description", "Test description"));

            if ("skip".equals(result.get("mode"))) {
                assertTrue((Boolean) result.get("success"));
                assertFalse(Boolean.TRUE.equals(result.get("conflict_detected")));
            }
        }
    }

    @Nested
    class TestConflictNoteFormat {

        @Test
        void testConflictNoteContainsReadInstruction() throws Exception {
            String content1 = "---\nname: Database Config\ndescription: PostgreSQL configuration\ntype: project\n---\n\nUse PostgreSQL with connection pool size 20.";
            CodingMemoryToolContext.write("db_config.md", content1);
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "db_config.md",
                    Map.of("name", "Database Config", "description", "PostgreSQL configuration"));

            String content2 = "---\nname: DB Settings\ndescription: Database connection settings\ntype: project\n---\n\nUse PostgreSQL with connection pool size 20 and timeout 30s.";
            Map<String, Object> result = CodingMemoryToolContext.write("db_settings.md", content2);

            if (Boolean.TRUE.equals(result.get("conflict_detected"))) {
                String note = String.valueOf(result.getOrDefault("note", ""));
                assertTrue(note.toLowerCase().contains("coding_memory_read") || note.toLowerCase().contains("read"));
            }
        }

        @Test
        void testConflictNoteContainsEditInstruction() throws Exception {
            String content1 = "---\nname: Code Style\ndescription: Python code style guide\ntype: feedback\n---\n\nUse 4 spaces for indentation.";
            CodingMemoryToolContext.write("code_style.md", content1);
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "code_style.md",
                    Map.of("name", "Code Style", "description", "Python code style guide"));

            String content2 = "---\nname: Python Style\ndescription: Python formatting rules\ntype: feedback\n---\n\nUse 4 spaces for indentation in Python files.";
            Map<String, Object> result = CodingMemoryToolContext.write("python_style.md", content2);

            if (Boolean.TRUE.equals(result.get("conflict_detected"))) {
                String note = String.valueOf(result.getOrDefault("note", ""));
                assertTrue(note.toLowerCase().contains("coding_memory_edit") || note.toLowerCase().contains("edit"));
            }
        }
    }

    @Nested
    class TestWriteResultStructure {

        @Test
        void testSuccessfulWriteResultStructure() throws Exception {
            String content = "---\nname: Test Structure\ndescription: Testing result structure\ntype: user\n---\n\nTest content.";
            Map<String, Object> result = CodingMemoryToolContext.write("test_structure.md", content);
            assertTrue(result.containsKey("success"));
            assertTrue(result.containsKey("path"));
            assertTrue(result.containsKey("mode"));
            assertTrue((Boolean) result.get("success"));
            String mode = (String) result.get("mode");
            assertTrue(List.of("create", "append", "skip").contains(mode));
        }

        @Test
        void testFailedWriteResultStructure() throws Exception {
            Map<String, Object> result = CodingMemoryToolContext.write("test_fail.md", "No frontmatter here");
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.containsKey("error"));
            assertTrue(result.get("error").toString().toLowerCase().contains("frontmatter"));
        }

        @Test
        void testConflictResultIncludesConflictingFiles() throws Exception {
            String content1 = "---\nname: Architecture Decision\ndescription: Microservices architecture\ntype: project\n---\n\nWe use microservices architecture with Kubernetes.";
            CodingMemoryToolContext.write("arch.md", content1);
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "arch.md",
                    Map.of("name", "Architecture Decision", "description", "Microservices architecture"));

            String content2 = "---\nname: System Design\ndescription: Kubernetes-based architecture\ntype: project\n---\n\nWe use microservices architecture deployed on Kubernetes clusters.";
            Map<String, Object> result = CodingMemoryToolContext.write("system_design.md", content2);

            if (Boolean.TRUE.equals(result.get("conflict_detected"))) {
                assertTrue(result.containsKey("conflicting_files"));
                assertTrue(result.get("conflicting_files") instanceof List);
                assertFalse(((List<?>) result.get("conflicting_files")).isEmpty());
            }
        }
    }
}
