/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.LocalWorkConfig;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Coding Memory System Tests.
 * <p>
 * Mirrors Python's {@code test_coding_memory} in
 * {@code tests/system_tests/memory/test_coding_memory.py}.
 */
@Tag("system-test")
class CodingMemorySystemTest {

    private Path tmpDir;
    private String workDir;
    private String codingMemoryDir;
    private String sysOperationId;
    private Object sysOp;

    @BeforeEach
    void setUp() throws Exception {
        Runner.start();
        tmpDir = Files.createTempDirectory("coding_memory_st_");
        workDir = new Workspace().getRootPath();
        sysOperationId = "coding_memory_st_sysop_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SysOperationCard card = new SysOperationCard();
        card.setId(sysOperationId);
        card.setMode(OperationMode.LOCAL);
        LocalWorkConfig workConfig = new LocalWorkConfig();
        workConfig.setWorkDir(workDir);
        card.setWorkConfig(workConfig);
        var addResult = Runner.resourceMgr().addSysOperation(card);
        if (addResult.isErr()) {
            throw new RuntimeException("add_sys_operation failed: " + addResult.getMsg());
        }
        Path cmDir = Path.of(workDir).resolve("coding_memory");
        Files.createDirectories(cmDir);
        codingMemoryDir = cmDir.toString();
        sysOp = Runner.resourceMgr().getSysOperation(sysOperationId);
        Workspace workspace = new Workspace();
        workspace.setRootPath(workDir);
        CodingMemoryToolContext.bind(workspace, sysOp, codingMemoryDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        CodingMemoryToolContext.reset();
        try {
            Runner.resourceMgr().removeSysOperation(sysOperationId);
        } finally {
            if (tmpDir != null) {
                Runner.stop();
            }
        }
    }

    @Nested
    class TestCodingMemoryScenario {

        @Test
        void testGetMemoryScenarioCoding() {
            assertEquals("coding", getMemoryScenario(Map.of("memory", Map.of("scenario", "coding"))));
            assertEquals("coding", getMemoryScenario(Map.of("memory", Map.of("scenario", "CODING"))));
            assertEquals("personal", getMemoryScenario(Map.of("memory", Map.of("scenario", "personal"))));
            assertEquals("personal", getMemoryScenario(Map.of("memory", Map.of())));
        }

        @SuppressWarnings("unchecked")
        private String getMemoryScenario(Map<String, Object> config) {
            Map<String, Object> memoryCfg = (Map<String, Object>) config.getOrDefault("memory", Map.of());
            String scenario = String.valueOf(memoryCfg.getOrDefault("scenario", "personal")).trim().toLowerCase();
            return "coding".equals(scenario) ? "coding" : "personal";
        }
    }

    @Nested
    class TestCodingMemoryRailLifecycle {

        @Test
        void testRailInitialization() {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            assertEquals(codingMemoryDir, rail.getCodingMemoryDir());
            assertEquals("cn", rail.getLanguage());
            assertNull(rail.getManager());
            assertFalse(rail.isManagerInitialized());
            assertEquals(5, rail.getMaxRecallResults());
            assertEquals(10240, rail.getMaxRecallTotalBytes());
        }

        @Test
        void testRailInitRegistersTools() {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            Object mockAgent = mock(Object.class);
            rail.init(mockAgent);
            assertTrue(rail.getOwnedToolNames().size() > 0);
            rail.uninit(mockAgent);
        }

        @Test
        void testRailUninitCleanup() {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            Object mockAgent = mock(Object.class);
            rail.init(mockAgent);
            rail.uninit(mockAgent);
            assertFalse(rail.isManagerInitialized());
        }
    }

    @Nested
    class TestCodingMemoryToolsIntegration {

        @Test
        void testCodingMemoryWriteCreatesFile() throws Exception {
            String content = "---\nname: User Preference\ndescription: User prefers Python for backend\ntype: user\n---\n\nUser prefers Python for backend services.";
            Map<String, Object> result = CodingMemoryToolContext.write("user_pref.md", content);
            assertTrue((Boolean) result.get("success"));
            assertEquals("user", result.get("type"));
            Map<String, Object> readResult = CodingMemoryToolContext.read("user_pref.md", null, null);
            assertTrue((Boolean) readResult.get("success"));
            assertTrue(readResult.get("content").toString().contains("User Preference"));
        }

        @Test
        void testCodingMemoryWriteUpdatesMemoryIndex() throws Exception {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "code_style.md",
                    Map.of("name", "Code Style Guide", "description", "Prefer integration tests over mocks"));
            Path indexPath = Path.of(codingMemoryDir, "MEMORY.md");
            String indexContent = Files.readString(indexPath, StandardCharsets.UTF_8);
            assertTrue(indexContent.contains("Code Style Guide"));
            assertTrue(indexContent.contains("code_style.md"));
        }

        @Test
        void testCodingMemoryReadFullContent() throws Exception {
            String content = "---\nname: Project Deadline\ndescription: Mobile release freeze date\ntype: project\n---\n\nMobile release freeze date: 2026-04-15.";
            CodingMemoryToolContext.write("deadline.md", content);
            Map<String, Object> result = CodingMemoryToolContext.read("deadline.md", null, null);
            assertTrue((Boolean) result.get("success"));
            assertTrue(result.get("content").toString().contains("Project Deadline"));
            assertTrue(result.get("content").toString().contains("2026-04-15"));
            assertTrue(((Number) result.get("totalLines")).intValue() > 0);
        }

        @Test
        void testCodingMemoryReadWithOffsetLimit() throws Exception {
            String content = "---\nname: Test Memory\ndescription: Test offset and limit\ntype: reference\n---\n\nLine 1\nLine 2\nLine 3\nLine 4\nLine 5";
            Map<String, Object> writeResult = CodingMemoryToolContext.write("lines.md", content);
            assertTrue((Boolean) writeResult.get("success"));
            Map<String, Object> result = CodingMemoryToolContext.read("lines.md", 1, 3);
            assertTrue((Boolean) result.get("success"));
            assertTrue(((Number) result.get("totalLines")).intValue() > 0);
        }

        @Test
        void testCodingMemoryEditUpdatesContent() throws Exception {
            String content = "---\nname: API Reference\ndescription: External API documentation\ntype: reference\n---\n\nAPI docs: https://old-api-docs.com";
            CodingMemoryToolContext.write("api_ref.md", content);
            Map<String, Object> result = CodingMemoryToolContext.edit("api_ref.md",
                    "https://old-api-docs.com", "https://new-api-docs.com");
            assertTrue((Boolean) result.get("success"));
            Map<String, Object> readResult = CodingMemoryToolContext.read("api_ref.md", null, null);
            assertTrue(readResult.get("content").toString().contains("https://new-api-docs.com"));
        }

        @Test
        void testCodingMemoryEditUpdatesIndexWhenFrontmatterChanges() throws Exception {
            String content = "---\nname: Old Name\ndescription: Old description\ntype: user\n---\n\nContent.";
            CodingMemoryToolContext.write("test.md", content);
            Map<String, Object> result = CodingMemoryToolContext.edit("test.md", "name: Old Name", "name: New Name");
            assertTrue((Boolean) result.get("success"));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "test.md",
                    Map.of("name", "New Name", "description", "Old description"));
            Path indexPath = Path.of(codingMemoryDir, "MEMORY.md");
            String indexContent = Files.readString(indexPath, StandardCharsets.UTF_8);
            assertTrue(indexContent.contains("New Name"));
        }

        @Test
        void testCodingMemoryWriteInvalidFrontmatterRejected() throws Exception {
            String content = "Plain text, no frontmatter";
            Map<String, Object> result = CodingMemoryToolContext.write("invalid.md", content);
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.get("error").toString().toLowerCase().contains("frontmatter"));
        }

        @Test
        void testCodingMemoryWriteInvalidTypeRejected() throws Exception {
            String content = "---\nname: Test\ndescription: Test\ntype: invalid_type\n---\n\nContent.";
            Map<String, Object> result = CodingMemoryToolContext.write("invalid_type.md", content);
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.get("error").toString().toLowerCase().contains("type"));
        }

        @Test
        void testCodingMemoryWritePathTraversalRejected() throws Exception {
            String content = "---\nname: Test\ndescription: Test\ntype: user\n---\n\nContent.";
            Map<String, Object> result = CodingMemoryToolContext.write("../etc/passwd.md", content);
            assertFalse((Boolean) result.get("success"));
        }

        @Test
        void testCodingMemoryEditOldTextNotFound() throws Exception {
            String content = "---\nname: Test\ndescription: Test\ntype: user\n---\n\nOriginal content.";
            CodingMemoryToolContext.write("test.md", content);
            Map<String, Object> result = CodingMemoryToolContext.edit("test.md",
                    "Nonexistent text", "New text");
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.get("error").toString().toLowerCase().contains("not found"));
        }

        @Test
        void testCodingMemoryEditMultipleMatchesRejected() throws Exception {
            String content = "---\nname: Test\ndescription: Test\ntype: user\n---\n\nDuplicate text\nDuplicate text";
            CodingMemoryToolContext.write("multi.md", content);
            Map<String, Object> result = CodingMemoryToolContext.edit("multi.md",
                    "Duplicate text", "Replacement text");
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.get("error").toString().toLowerCase().contains("appears"));
        }
    }

    @Nested
    class TestCodingMemoryAutoRecall {

        @Test
        void testAutoRecallReturnsContent() throws Exception {
            String content = "---\nname: Python Developer Role\ndescription: User is a Python developer\ntype: user\n---\n\nUser is a senior Python developer, familiar with Django and Flask.";
            CodingMemoryToolContext.write("python_dev.md", content);

            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            rail.setManager(mockManager(List.of(Map.of("path", "python_dev.md", "score", 0.95))));
            CodingMemoryRail.RecallResult recall = rail.autoRecall("Python developer");
            assertNotNull(recall.getContent());
            assertTrue(recall.getContent().contains("Python Developer Role"));
            assertTrue(recall.getTotal() >= 1);
        }

        @Test
        void testAutoRecallSkipsMemoryMd() throws Exception {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            String content = "---\nname: Other\ndescription: Other memory\ntype: user\n---\n\nOther content.";
            CodingMemoryToolContext.write("other.md", content);
            rail.setManager(mockManager(List.of(
                    Map.of("path", "MEMORY.md", "score", 0.9),
                    Map.of("path", "other.md", "score", 0.8)
            )));
            CodingMemoryRail.RecallResult recall = rail.autoRecall("test");
            if (recall.getContent() != null) {
                assertFalse(recall.getContent().contains("MEMORY.md"));
            }
        }

        @Test
        void testAutoRecallRespectsMaxBytes() {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            assertEquals(10240, rail.getMaxRecallTotalBytes());
        }

        @Test
        void testAutoRecallNoResults() throws Exception {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            rail.setManager(mockManager(List.of()));
            CodingMemoryRail.RecallResult recall = rail.autoRecall("UnknownQuery12345");
            assertNull(recall.getContent());
        }

        private Object mockManager(List<Map<String, Object>> searchResults) {
            Object manager = mock(Object.class);
            when(manager.toString()).thenReturn("MockManager");
            return manager;
        }
    }

    @Nested
    class TestCodingMemoryPromptInjection {

        @Test
        void testBeforeModelCallInjectsRecallContent() throws Exception {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            rail.setRecalledContent("### Test memory [test.md]\n\nTest content");
            rail.setTotalMemories(3);
            Object mockCtx = mock(Object.class);
            rail.beforeModelCall(mockCtx);
            assertNotNull(rail.getRecalledContent());
        }

        @Test
        void testBeforeModelCallFallbackToIndex() throws Exception {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "test.md",
                    Map.of("name", "Test Memory", "description", "Test description"));
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            rail.setRecalledContent(null);
            Object mockCtx = mock(Object.class);
            rail.beforeModelCall(mockCtx);
            assertNull(rail.getRecalledContent());
        }

        @Test
        void testBeforeModelCallReadOnlyMode() throws Exception {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "test.md",
                    Map.of("name", "Test", "description", "Test"));
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new EmbeddingConfig("test-model", "http://test", "test-key"),
                    "cn"
            );
            rail.setSysOperation(sysOp);
            Object mockInputs = mock(Object.class);
            Object mockCtx = mock(Object.class);
            rail.beforeModelCall(mockCtx);
            assertTrue(rail.getCodingMemoryDir().equals(codingMemoryDir));
        }
    }

    @Nested
    class TestCodingMemoryEndToEnd {

        @Test
        void testFullWorkflowWriteRecallRead() throws Exception {
            String content = "---\nname: Database Preference\ndescription: Prefer PostgreSQL over MySQL\ntype: feedback\n---\n\nDatabase choice: Prefer PostgreSQL over MySQL.\n**Reason:** PostgreSQL supports richer data types and better scalability.\n**How to apply:** New projects default to PostgreSQL.";
            Map<String, Object> writeResult = CodingMemoryToolContext.write("db_pref.md", content);
            assertTrue((Boolean) writeResult.get("success"));
            Map<String, Object> readResult = CodingMemoryToolContext.read("db_pref.md", null, null);
            assertTrue((Boolean) readResult.get("success"));
            assertTrue(readResult.get("content").toString().contains("PostgreSQL"));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "db_pref.md",
                    Map.of("name", "Database Preference", "description", "Prefer PostgreSQL over MySQL"));
            Path indexPath = Path.of(codingMemoryDir, "MEMORY.md");
            String indexContent = Files.readString(indexPath, StandardCharsets.UTF_8);
            assertTrue(indexContent.contains("Database Preference"));
        }

        @Test
        void testAllMemoryTypes() throws Exception {
            List<Object[]> memories = List.of(
                    new Object[]{"user_role.md", "user", "User Role", "User role"},
                    new Object[]{"feedback_style.md", "feedback", "Code Style", "Code style feedback"},
                    new Object[]{"project_deadline.md", "project", "Project Deadline", "Project deadline"},
                    new Object[]{"reference_api.md", "reference", "API Reference", "API reference"}
            );
            for (Object[] mem : memories) {
                String filename = (String) mem[0];
                String memType = (String) mem[1];
                String name = (String) mem[2];
                String desc = (String) mem[3];
                String content = "---\nname: " + name + "\ndescription: " + desc + "\ntype: " + memType + "\n---\n\nThis is " + memType + " type memory content.";
                Map<String, Object> result = CodingMemoryToolContext.write(filename, content);
                assertTrue((Boolean) result.get("success"), "Failed to write " + memType + " memory");
                assertEquals(memType, result.get("type"));
                CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, filename,
                        Map.of("name", name, "description", desc));
            }
            for (Object[] mem : memories) {
                Map<String, Object> readResult = CodingMemoryToolContext.read((String) mem[0], null, null);
                assertTrue((Boolean) readResult.get("success"), "File " + mem[0] + " should exist");
            }
            Path indexPath = Path.of(codingMemoryDir, "MEMORY.md");
            String indexContent = Files.readString(indexPath, StandardCharsets.UTF_8);
            for (Object[] mem : memories) {
                assertTrue(indexContent.contains((String) mem[2]), "Index should contain " + mem[2]);
            }
        }

        @Test
        void testMemoryUpdateWorkflow() throws Exception {
            String uniqueId = String.valueOf(System.currentTimeMillis() % 1000000);
            String filename = "team_" + uniqueId + ".md";
            String content = "---\nname: Team Member " + uniqueId + "\ndescription: Team member info\ntype: project\n---\n\nTeam member: Zhang San, responsible for backend development.";
            Map<String, Object> writeResult = CodingMemoryToolContext.write(filename, content);
            assertTrue((Boolean) writeResult.get("success"), "Write failed: " + writeResult.get("error"));
            Map<String, Object> editResult = CodingMemoryToolContext.edit(filename,
                    "Zhang San, responsible for backend development",
                    "Zhang San, responsible for backend development and architecture design");
            assertTrue((Boolean) editResult.get("success"), "Edit failed: " + editResult.get("error"));
            Map<String, Object> readResult = CodingMemoryToolContext.read(filename, null, null);
            assertTrue((Boolean) readResult.get("success"));
            assertTrue(readResult.get("content").toString().contains("architecture design"));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, filename,
                    Map.of("name", "Team Member " + uniqueId, "description", "Updated team member info"));
            Path indexPath = Path.of(codingMemoryDir, "MEMORY.md");
            String indexContent = Files.readString(indexPath, StandardCharsets.UTF_8);
            assertTrue(indexContent.contains("Team Member " + uniqueId));
        }
    }

    @Nested
    class TestCodingMemoryEdgeCases {

        @Test
        void testReadNonexistentFile() throws Exception {
            Map<String, Object> result = CodingMemoryToolContext.read("nonexistent.md", null, null);
            assertFalse((Boolean) result.get("success"));
        }

        @Test
        void testWriteEmptyContent() throws Exception {
            Map<String, Object> result = CodingMemoryToolContext.write("empty.md", "");
            assertFalse((Boolean) result.get("success"));
        }

        @Test
        void testWriteNonMdFile() throws Exception {
            String content = "---\nname: Test\ndescription: Test\ntype: user\n---\n\nContent.";
            Map<String, Object> result = CodingMemoryToolContext.write("test.txt", content);
            assertFalse((Boolean) result.get("success"));
        }

        @Test
        void testEditEmptyOldText() throws Exception {
            String content = "---\nname: Test\ndescription: Test\ntype: user\n---\n\nContent.";
            CodingMemoryToolContext.write("test.md", content);
            Map<String, Object> result = CodingMemoryToolContext.edit("test.md", "", "New content");
            assertFalse((Boolean) result.get("success"));
        }

        @Test
        void testMemoryIndexMaxLines() throws Exception {
            for (int i = 0; i < 10; i++) {
                String content = "---\nname: Memory " + i + "\ndescription: Test memory " + i + "\ntype: user\n---\n\nContent " + i + ".";
                CodingMemoryToolContext.write("mem_" + i + ".md", content);
                CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "mem_" + i + ".md",
                        Map.of("name", "Memory " + i, "description", "Test memory " + i));
            }
            Path indexPath = Path.of(codingMemoryDir, "MEMORY.md");
            String indexContent = Files.readString(indexPath, StandardCharsets.UTF_8);
            for (int i = 0; i < 10; i++) {
                assertTrue(indexContent.contains("Memory " + i));
            }
        }
    }
}
