/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.memory;

import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryTools;
import com.openjiuwen.core.memory.lite.Frontmatter;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_coding_memory.py} in
 * {@code tests/system_tests/memory/test_coding_memory.py}.
 */
public class TestCodingMemory {

    private Path tmpDir;
    private String workDir;
    private String codingMemoryDir;

    @BeforeEach
    void setUp() throws Exception {
        tmpDir = Files.createTempDirectory("coding_memory_st_");
        workDir = tmpDir.toString();
        codingMemoryDir = tmpDir.resolve("coding_memory").toString();
        Files.createDirectories(Path.of(codingMemoryDir));
        CodingMemoryToolContext.bind(new Workspace(workDir, "cn"), null, codingMemoryDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        CodingMemoryToolContext.reset();
        deleteTree(tmpDir);
    }

    @Nested
    class TestCodingMemoryScenario {

        @Test
        void testGetMemoryScenarioCoding() {
            assertThat(getMemoryScenario(Map.of("memory", Map.of("scenario", "coding")))).isEqualTo("coding");
            assertThat(getMemoryScenario(Map.of("memory", Map.of("scenario", "CODING")))).isEqualTo("coding");
            assertThat(getMemoryScenario(Map.of("memory", Map.of("scenario", "personal")))).isEqualTo("personal");
            assertThat(getMemoryScenario(Map.of("memory", Map.of()))).isEqualTo("personal");
        }
    }

    @Nested
    class TestCodingMemoryRailLifecycle {

        @Test
        void testRailInitialization() {
            CodingMemoryRail rail = new CodingMemoryRail(
                    codingMemoryDir,
                    new Object(),
                    "cn");

            assertThat(rail.getCodingMemoryDir()).isEqualTo(codingMemoryDir);
            assertThat(rail.getLanguage()).isEqualTo("cn");
            assertThat(rail.isManagerInitialized()).isFalse();
            assertThat(CodingMemoryRail.MAX_RECALL_RESULTS).isEqualTo(5);
            assertThat(CodingMemoryRail.MAX_RECALL_TOTAL_BYTES).isEqualTo(10240);
        }

        @Test
        void testRailInitRegistersTools() {
            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            MockAgent agent = new MockAgent();

            rail.init(agent);

            assertThat(agent.abilityManager.addedNames())
                    .containsExactlyInAnyOrder("coding_memory_read", "coding_memory_write", "coding_memory_edit");
            rail.uninit(agent);
        }

        @Test
        void testRailUninitCleanup() {
            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            MockAgent agent = new MockAgent();

            rail.init(agent);
            rail.uninit(agent);

            assertThat(agent.abilityManager.removedNames())
                    .containsExactlyInAnyOrder("coding_memory_read", "coding_memory_write", "coding_memory_edit");
            assertThat(agent.promptBuilder.removedSections()).contains("memory");
            assertThat(rail.isManagerInitialized()).isFalse();
            assertThat(rail.getOwnedToolNames()).isEmpty();
        }
    }

    @Nested
    class TestCodingMemoryToolsIntegration {

        @Test
        void testCodingMemoryWriteCreatesFile() {
            String content = """
                    ---
                    name: User Preference
                    description: User prefers Python for backend
                    type: user
                    ---

                    用户喜欢使用 Python 开发后端服务.
                    """;

            Map<String, Object> result = CodingMemoryToolContext.write("user_pref.md", content);

            assertThat(result).containsEntry("success", true);
            assertThat(result).containsEntry("type", "user");

            Map<String, Object> readResult = CodingMemoryToolContext.read("user_pref.md", null, null);
            assertThat(readResult).containsEntry("success", true);
            assertThat(readResult.get("content").toString()).contains("User Preference");
        }

        @Test
        void testCodingMemoryWriteUpdatesMemoryIndex() {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "code_style.md",
                    Map.of("name", "Code Style Guide", "description", "Prefer integration tests over mocks"));

            String indexContent = readText(Path.of(codingMemoryDir, "MEMORY.md"));
            assertThat(indexContent).contains("Code Style Guide", "code_style.md");
        }

        @Test
        void testCodingMemoryReadFullContent() {
            String content = """
                    ---
                    name: Project Deadline
                    description: Mobile release freeze date
                    type: project
                    ---

                    移动端发布冻结日期：2026-04-15.
                    """;

            CodingMemoryToolContext.write("deadline.md", content);
            Map<String, Object> result = CodingMemoryToolContext.read("deadline.md", null, null);

            assertThat(result).containsEntry("success", true);
            assertThat(result.get("content").toString()).contains("Project Deadline", "2026-04-15");
            assertThat(result.get("totalLines")).isInstanceOf(Integer.class);
        }

        @Test
        void testCodingMemoryReadWithOffsetLimit() {
            String content = """
                    ---
                    name: Test Memory
                    description: Test offset and limit
                    type: reference
                    ---

                    Line 1
                    Line 2
                    Line 3
                    Line 4
                    Line 5
                    """;

            assertThat(CodingMemoryToolContext.write("lines.md", content)).containsEntry("success", true);
            Map<String, Object> result = CodingMemoryToolContext.read("lines.md", 1, 3);

            assertThat(result).containsEntry("success", true);
            assertThat((Integer) result.get("totalLines")).isGreaterThan(0);
        }

        @Test
        void testCodingMemoryEditUpdatesContent() {
            String content = """
                    ---
                    name: API Reference
                    description: External API documentation
                    type: reference
                    ---

                    API 文档地址: https://old-api-docs.com
                    """;

            CodingMemoryToolContext.write("api_ref.md", content);
            Map<String, Object> result = CodingMemoryToolContext.staticEdit("api_ref.md",
                    "https://old-api-docs.com",
                    "https://new-api-docs.com");

            assertThat(result).containsEntry("success", true);
            assertThat(CodingMemoryToolContext.read("api_ref.md", null, null).get("content").toString())
                    .contains("https://new-api-docs.com");
        }

        @Test
        void testCodingMemoryEditUpdatesIndexWhenFrontmatterChanges() {
            String content = """
                    ---
                    name: Old Name
                    description: Old description
                    type: user
                    ---

                    内容.
                    """;

            CodingMemoryToolContext.write("test.md", content);
            Map<String, Object> result = CodingMemoryToolContext.staticEdit("test.md",
                    "name: Old Name",
                    "name: New Name");

            assertThat(result).containsEntry("success", true);
            assertThat(readText(Path.of(codingMemoryDir, "MEMORY.md"))).contains("New Name");
        }

        @Test
        void testCodingMemoryWriteInvalidFrontmatterRejected() {
            Map<String, Object> result = CodingMemoryToolContext.write("invalid.md", "纯文本，没有 frontmatter");
            assertThat(result).containsEntry("success", false);
            assertThat(result.get("error").toString().toLowerCase()).contains("frontmatter");
        }

        @Test
        void testCodingMemoryWriteInvalidTypeRejected() {
            String content = """
                    ---
                    name: Test
                    description: Test
                    type: invalid_type
                    ---

                    内容.
                    """;

            Map<String, Object> result = CodingMemoryToolContext.write("invalid_type.md", content);
            assertThat(result).containsEntry("success", false);
            assertThat(result.get("error").toString().toLowerCase()).contains("type");
        }

        @Test
        void testCodingMemoryWritePathTraversalRejected() {
            String content = """
                    ---
                    name: Test
                    description: Test
                    type: user
                    ---

                    内容.
                    """;

            Map<String, Object> result = CodingMemoryToolContext.write("../etc/passwd.md", content);
            assertThat(result).containsEntry("success", false);
        }

        @Test
        void testCodingMemoryEditOldTextNotFound() {
            String content = """
                    ---
                    name: Test
                    description: Test
                    type: user
                    ---

                    原始内容.
                    """;

            CodingMemoryToolContext.write("test.md", content);
            Map<String, Object> result = CodingMemoryToolContext.staticEdit("test.md",
                    "不存在的文本", "新文本");

            assertThat(result).containsEntry("success", false);
            assertThat(result.get("error").toString().toLowerCase()).contains("not found");
        }

        @Test
        void testCodingMemoryEditMultipleMatchesRejected() {
            String content = """
                    ---
                    name: Test
                    description: Test
                    type: user
                    ---

                    重复文本
                    重复文本
                    """;

            CodingMemoryToolContext.write("multi.md", content);
            Map<String, Object> result = CodingMemoryToolContext.staticEdit("multi.md", "重复文本", "替换文本");

            assertThat(result).containsEntry("success", false);
            assertThat(result.get("error").toString().toLowerCase()).contains("appears");
        }
    }

    @Nested
    class TestCodingMemoryAutoRecall {

        @Test
        void testAutoRecallReturnsContent() {
            String content = """
                    ---
                    name: Python Developer Role
                    description: User is a Python developer
                    type: user
                    ---

                    用户是高级 Python 开发者，熟悉 Django 和 Flask.
                    """;
            CodingMemoryToolContext.write("python_dev.md", content);

            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            rail.setMemoryManager(new MockManager(List.of(Map.of("path", "python_dev.md", "score", 0.95))));

            CodingMemoryRail.RecallResult result = rail.autoRecall("Python developer");

            assertThat(result.getContent()).contains("Python Developer Role");
            assertThat(result.getTotal()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void testAutoRecallSkipsMemoryMd() {
            String content = """
                    ---
                    name: Other
                    description: Other memory
                    type: user
                    ---

                    其他内容.
                    """;
            CodingMemoryToolContext.write("other.md", content);

            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            rail.setMemoryManager(new MockManager(List.of(
                    Map.of("path", "MEMORY.md", "score", 0.9),
                    Map.of("path", "other.md", "score", 0.8))));

            CodingMemoryRail.RecallResult result = rail.autoRecall("test");

            assertThat(result.getContent()).doesNotContain("MEMORY.md");
            assertThat(result.getContent()).contains("Other");
        }

        @Test
        void testAutoRecallRespectsMaxBytes() {
            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            assertThat(CodingMemoryRail.MAX_RECALL_TOTAL_BYTES).isEqualTo(10240);
            assertThat(rail.autoRecall("UnknownQuery12345").getTotal()).isZero();
        }

        @Test
        void testAutoRecallNoResults() {
            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            rail.setMemoryManager(new MockManager(List.of()));

            CodingMemoryRail.RecallResult result = rail.autoRecall("UnknownQuery12345");

            assertThat(result.getContent()).isNull();
            assertThat(result.getTotal()).isZero();
        }
    }

    @Nested
    class TestCodingMemoryPromptInjection {

        @Test
        void testBeforeModelCallInjectsRecallContent() {
            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            MockAgent agent = new MockAgent();
            rail.init(agent);
            rail.setRecalledContent("### 测试记忆 [test.md]\n\n测试内容");

            rail.beforeModelCall(AgentCallbackContext.builder().build());

            assertThat(agent.promptBuilder.sections()).hasSize(1);
            PromptSection section = agent.promptBuilder.sections().get(0);
            assertThat(section.getContent().get("cn")).contains("已加载的相关记忆", "测试记忆");
        }

        @Test
        void testBeforeModelCallFallbackToIndex() {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "test.md",
                    Map.of("name", "Test Memory", "description", "Test description"));

            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            MockAgent agent = new MockAgent();
            rail.init(agent);
            rail.setRecalledContent(null);

            rail.beforeModelCall(AgentCallbackContext.builder().build());

            assertThat(agent.promptBuilder.sections()).hasSize(1);
            assertThat(agent.promptBuilder.sections().get(0).getContent().get("cn"))
                    .contains("当前记忆索引", "Test Memory");
        }

        @Test
        void testBeforeModelCallReadOnlyMode() {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "test.md",
                    Map.of("name", "Test", "description", "Test"));

            CodingMemoryRail rail = new CodingMemoryRail(codingMemoryDir, new Object(), "cn");
            MockAgent agent = new MockAgent();
            rail.init(agent);

            AgentCallbackContext ctx = AgentCallbackContext.builder()
                    .inputs(new MockInputs(true, false))
                    .build();
            rail.beforeModelCall(ctx);

            assertThat(agent.promptBuilder.sections()).hasSize(1);
            assertThat(agent.promptBuilder.sections().get(0).getContent().get("cn"))
                    .contains("只读");
        }
    }

    @Nested
    class TestCodingMemoryEndToEnd {

        @Test
        void testFullWorkflowWriteRecallRead() {
            String content = """
                    ---
                    name: Database Preference
                    description: Prefer PostgreSQL over MySQL
                    type: feedback
                    ---

                    数据库选择：优先使用 PostgreSQL 而不是 MySQL.
                    **原因：** PostgreSQL 支持更丰富的数据类型和更好的扩展性.
                    **如何应用：** 新项目默认使用 PostgreSQL.
                    """;

            Map<String, Object> writeResult = CodingMemoryToolContext.write("db_pref.md", content);
            assertThat(writeResult).containsEntry("success", true);

            Map<String, Object> readResult = CodingMemoryToolContext.read("db_pref.md", null, null);
            assertThat(readResult).containsEntry("success", true);
            assertThat(readResult.get("content").toString()).contains("PostgreSQL");

            assertThat(readText(Path.of(codingMemoryDir, "MEMORY.md"))).contains("Database Preference");
        }

        @Test
        void testAllMemoryTypes() {
            List<Object[]> memories = List.of(
                    new Object[]{"user_role.md", "user", "User Role", "用户角色"},
                    new Object[]{"feedback_style.md", "feedback", "Code Style", "代码风格反馈"},
                    new Object[]{"project_deadline.md", "project", "Project Deadline", "项目截止日期"},
                    new Object[]{"reference_api.md", "reference", "API Reference", "API 参考"});

            for (Object[] memory : memories) {
                String filename = (String) memory[0];
                String memType = (String) memory[1];
                String name = (String) memory[2];
                String description = (String) memory[3];
                String content = """
                        ---
                        name: %s
                        description: %s
                        type: %s
                        ---

                        这是 %s 类型的记忆内容.
                        """.formatted(name, description, memType, memType);

                Map<String, Object> result = CodingMemoryToolContext.write(filename, content);
                assertThat(result).containsEntry("success", true).containsEntry("type", memType);
                CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, filename,
                        Map.of("name", name, "description", description));
            }

            for (Object[] memory : memories) {
                assertThat(CodingMemoryToolContext.read((String) memory[0], null, null))
                        .containsEntry("success", true);
            }
            String indexContent = readText(Path.of(codingMemoryDir, "MEMORY.md"));
            for (Object[] memory : memories) {
                assertThat(indexContent).contains((String) memory[2]);
            }
        }

        @Test
        void testMemoryUpdateWorkflow() {
            String uniqueId = "123456";
            String filename = "team_" + uniqueId + ".md";
            String content = """
                    ---
                    name: Team Member 123456
                    description: Team member info
                    type: project
                    ---

                    团队成员：张三，负责后端开发.
                    """;

            assertThat(CodingMemoryToolContext.write(filename, content)).containsEntry("success", true);
            assertThat(CodingMemoryToolContext.staticEdit(filename,
                    "张三，负责后端开发",
                    "张三，负责后端开发和架构设计")).containsEntry("success", true);
            assertThat(CodingMemoryToolContext.read(filename, null, null).get("content").toString())
                    .contains("架构设计");
            assertThat(readText(Path.of(codingMemoryDir, "MEMORY.md"))).contains("Team Member 123456");
        }
    }

    @Nested
    class TestCodingMemoryEdgeCases {

        @Test
        void testReadNonexistentFile() {
            Map<String, Object> result = CodingMemoryToolContext.read("nonexistent.md", null, null);
            assertThat(result).containsEntry("success", false);
        }

        @Test
        void testWriteEmptyContent() {
            Map<String, Object> result = CodingMemoryToolContext.write("empty.md", "");
            assertThat(result).containsEntry("success", false);
        }

        @Test
        void testWriteNonMdFile() {
            String content = """
                    ---
                    name: Test
                    description: Test
                    type: user
                    ---

                    内容.
                    """;

            Map<String, Object> result = CodingMemoryToolContext.write("test.txt", content);
            assertThat(result).containsEntry("success", false);
        }

        @Test
        void testEditEmptyOldText() {
            String content = """
                    ---
                    name: Test
                    description: Test
                    type: user
                    ---

                    内容.
                    """;

            CodingMemoryToolContext.write("test.md", content);
            Map<String, Object> result = CodingMemoryToolContext.staticEdit("test.md", "", "新内容");
            assertThat(result).containsEntry("success", false);
        }

        @Test
        void testMemoryIndexMaxLines() {
            for (int i = 0; i < 10; i++) {
                String content = """
                        ---
                        name: Memory %d
                        description: Test memory %d
                        type: user
                        ---

                        内容 %d.
                        """.formatted(i, i, i);

                assertThat(CodingMemoryToolContext.write("mem_" + i + ".md", content))
                        .containsEntry("success", true);
                CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir, "mem_" + i + ".md",
                        Map.of("name", "Memory " + i, "description", "Test memory " + i));
            }

            String indexContent = readText(Path.of(codingMemoryDir, "MEMORY.md"));
            for (int i = 0; i < 10; i++) {
                assertThat(indexContent).contains("Memory " + i);
            }
            assertThat(CodingMemoryTools.MAX_INDEX_LINES).isGreaterThanOrEqualTo(10);
        }
    }

    private static String getMemoryScenario(Map<String, Object> config) {
        Map<String, Object> memoryCfg = (Map<String, Object>) config.getOrDefault("memory", Map.of());
        String scenario = String.valueOf(memoryCfg.getOrDefault("scenario", "personal")).trim().toLowerCase();
        return "coding".equals(scenario) ? "coding" : "personal";
    }

    private static String readText(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception e) {
            return "";
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

    private static final class MockAgent {
        private final MockAbilityManager abilityManager = new MockAbilityManager();
        private final MockPromptBuilder promptBuilder = new MockPromptBuilder();

        public MockAbilityManager getAbilityManager() {
            return abilityManager;
        }

        public MockPromptBuilder getSystemPromptBuilder() {
            return promptBuilder;
        }
    }

    private static final class MockAbilityManager {
        private final List<Object> added = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        public void add(Object ability) {
            added.add(ability);
        }

        public void remove(String name) {
            removed.add(name);
        }

        List<String> addedNames() {
            List<String> names = new ArrayList<>();
            for (Object ability : added) {
                if (ability instanceof com.openjiuwen.core.foundation.tool.ToolCard toolCard) {
                    names.add(toolCard.getName());
                } else {
                    names.add(String.valueOf(ability));
                }
            }
            return names;
        }

        List<String> removedNames() {
            return removed;
        }
    }

    private static final class MockPromptBuilder {
        private final List<PromptSection> sections = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        public void addSection(PromptSection section) {
            sections.add(section);
        }

        public void removeSection(String name) {
            removed.add(name);
        }

        List<PromptSection> sections() {
            return sections;
        }

        List<String> removedSections() {
            return removed;
        }
    }

    private static final class MockManager {
        private final List<Map<String, Object>> results;

        private MockManager(List<Map<String, Object>> results) {
            this.results = results;
        }

        public List<Map<String, Object>> search(String query, Map<String, Object> opts) {
            return results;
        }
    }

    private static final class MockInputs implements EventInputs {
        private final boolean cron;
        private final boolean heartbeat;

        private MockInputs(boolean cron, boolean heartbeat) {
            this.cron = cron;
            this.heartbeat = heartbeat;
        }

        public boolean isCron() {
            return cron;
        }

        public boolean isHeartbeat() {
            return heartbeat;
        }
    }
}
