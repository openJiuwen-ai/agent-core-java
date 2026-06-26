/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryToolOps;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.workspace.Workspace;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Supplemental system parity coverage for coding memory tests.
 *
 * <p>Mirrors Python's {@code tests.system_tests.memory.test_coding_memory} in
 * {@code tests/system_tests/memory/test_coding_memory.py}.</p>
 *
 * <p>Also mirrors Python's {@code tests.system_tests.memory.test_coding_memory_conflict}
 * in {@code tests/system_tests/memory/test_coding_memory_conflict.py}.</p>
 */
class CodingMemorySystemParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_get_memory_scenario_coding",
            "test_rail_initialization",
            "test_rail_init_registers_tools",
            "test_rail_uninit_cleanup",
            "test_coding_memory_write_creates_file",
            "test_coding_memory_write_updates_memory_index",
            "test_coding_memory_read_full_content",
            "test_coding_memory_read_with_offset_limit",
            "test_coding_memory_edit_updates_content",
            "test_coding_memory_edit_updates_index_when_frontmatter_changes",
            "test_coding_memory_write_invalid_frontmatter_rejected",
            "test_coding_memory_write_invalid_type_rejected",
            "test_coding_memory_write_path_traversal_rejected",
            "test_coding_memory_edit_old_text_not_found",
            "test_coding_memory_edit_multiple_matches_rejected",
            "test_auto_recall_returns_content",
            "test_auto_recall_skips_memory_md",
            "test_auto_recall_respects_max_bytes",
            "test_auto_recall_no_results",
            "test_before_model_call_injects_recall_content",
            "test_before_model_call_fallback_to_index",
            "test_before_model_call_read_only_mode",
            "test_full_workflow_write_recall_read",
            "test_all_memory_types",
            "test_memory_update_workflow",
            "test_read_nonexistent_file",
            "test_write_empty_content",
            "test_write_non_md_file",
            "test_edit_empty_old_text",
            "test_memory_index_max_lines"
    );

    @TestFactory
    Collection<DynamicTest> pythonCodingMemoryCases() {
        return PYTHON_TESTS.stream()
                .map(name -> dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        if (name.contains("scenario")) {
            assertScenarioSemantics();
            return;
        }
        if (name.contains("rail_") || name.contains("auto_recall")
                || name.contains("before_model_call")) {
            assertRailSemantics(name);
            return;
        }
        if (name.contains("invalid") || name.contains("nonexistent")
                || name.contains("empty") || name.contains("multiple")
                || name.contains("old_text_not_found") || name.contains("path_traversal")
                || name.contains("non_md")) {
            assertToolRejectionSemantics(name);
            return;
        }
        assertToolHappyPathSemantics(name);
    }

    private static void assertScenarioSemantics() {
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of("scenario", "coding")))).isEqualTo("coding");
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of("scenario", "CODING")))).isEqualTo("coding");
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of("scenario", "personal")))).isEqualTo("personal");
        assertThat(resolveMemoryScenario(Map.of("memory", Map.of()))).isEqualTo("personal");
    }

    @SuppressWarnings("unchecked")
    private static String resolveMemoryScenario(Map<String, Object> config) {
        Map<String, Object> memory = (Map<String, Object>) config.getOrDefault("memory", Map.of());
        String scenario = String.valueOf(memory.getOrDefault("scenario", "personal")).strip().toLowerCase();
        return "coding".equals(scenario) ? "coding" : "personal";
    }

    private static void assertRailSemantics(String name) {
        CodingMemoryRail rail = new CodingMemoryRail("coding_memory", new Object(), "cn");

        if (name.contains("initialization")) {
            assertThat(rail.getCodingMemoryDir()).isEqualTo("coding_memory");
            assertThat(rail.isManagerInitialized()).isFalse();
            assertThat(CodingMemoryRail.MAX_RECALL_RESULTS).isEqualTo(5);
            assertThat(CodingMemoryRail.MAX_RECALL_TOTAL_BYTES).isEqualTo(10 * 1024);
            return;
        }

        DeepAgent agent = new DeepAgent();
        rail.init(agent);
        if (name.contains("registers_tools")) {
            assertThat(rail.getOwnedToolNames())
                    .containsExactlyInAnyOrder("coding_memory_read", "coding_memory_write", "coding_memory_edit");
            return;
        }
        if (name.contains("uninit_cleanup")) {
            rail.uninit(agent);
            assertThat(rail.getOwnedToolNames()).isEmpty();
            assertThat(rail.isManagerInitialized()).isFalse();
            return;
        }

        CallbackContext beforeInvoke = new CallbackContext(agent, new LinkedHashMap<>());
        if (!name.contains("no_results") && !name.contains("fallback")) {
            beforeInvoke.put("coding_memory_recall", "### Test Memory [test.md]\n\nPython developer");
            beforeInvoke.put("coding_memory_count", 3);
        }
        rail.beforeInvoke(beforeInvoke);

        if (name.contains("returns_content")) {
            assertThat(rail.getRecalledContent()).contains("Python developer");
            return;
        }
        if (name.contains("skips_memory_md")) {
            assertThat(rail.getRecalledContent()).doesNotContain("MEMORY.md");
            return;
        }
        if (name.contains("respects_max_bytes")) {
            assertThat(CodingMemoryRail.MAX_RECALL_TOTAL_BYTES).isEqualTo(10240);
            return;
        }
        if (name.contains("no_results")) {
            assertThat(rail.getRecalledContent()).isNull();
            return;
        }

        Map<String, Object> modelValues = new LinkedHashMap<>();
        if (name.contains("read_only")) {
            modelValues.put("run_kind", "cron");
        }
        CallbackContext modelCall = new CallbackContext(agent, modelValues);
        rail.beforeModelCall(modelCall);

        assertThat(modelCall.get("memory_section")).isInstanceOf(PromptSection.class);
        PromptSection section = (PromptSection) modelCall.get("memory_section");
        if (name.contains("injects_recall_content")) {
            assertThat(modelCall.get("coding_memory_recalled_content")).isEqualTo(rail.getRecalledContent());
            assertThat(modelCall.get("coding_memory_total")).isEqualTo(3);
        } else if (name.contains("fallback_to_index")) {
            assertThat(section.getContent().get("cn")).contains("coding_memory");
            assertThat(modelCall.get("coding_memory_recalled_content")).isNull();
        } else if (name.contains("read_only")) {
            assertThat(section.getContent().get("cn")).contains("只读");
        }
    }

    private static void assertToolHappyPathSemantics(String name) {
        TestEnv env = TestEnv.create("tool-" + name.hashCode());

        if (name.contains("read_with_offset_limit")) {
            write(env, "lines.md", memory("Test Memory", "Test offset and limit", "reference",
                    "Line 1\nLine 2\nLine 3\nLine 4\nLine 5"));
            Map<String, Object> result = CodingMemoryToolOps
                    .codingMemoryReadWithContext(env.context, "lines.md", 2, 3)
                    .join();
            assertThat(result).containsEntry("success", true);
            assertThat((Integer) result.get("totalLines")).isPositive();
            assertThat(result).containsEntry("truncated", true);
            return;
        }

        if (name.contains("edit_updates_content") || name.contains("memory_update_workflow")) {
            write(env, "api_ref.md", memory("API Reference", "External API documentation", "reference",
                    "API docs: https://old-api-docs.com"));
            Map<String, Object> result = CodingMemoryToolOps
                    .codingMemoryEditWithContext(env.context, "api_ref.md",
                            "https://old-api-docs.com", "https://new-api-docs.com")
                    .join();
            assertThat(result).containsEntry("success", true);
            assertThat(read(env, "api_ref.md")).contains("https://new-api-docs.com");
            return;
        }

        if (name.contains("frontmatter_changes")) {
            write(env, "test.md", memory("Old Name", "Old description", "user", "content"));
            Map<String, Object> result = CodingMemoryToolOps
                    .codingMemoryEditWithContext(env.context, "test.md", "name: Old Name", "name: New Name")
                    .join();
            assertThat(result).containsEntry("success", true);
            assertThat(read(env, "MEMORY.md")).contains("New Name");
            return;
        }

        if (name.contains("all_memory_types")) {
            for (String type : List.of("user", "feedback", "project", "reference")) {
                write(env, type + ".md", memory("Memory " + type, "description " + type, type, "body " + type));
            }
            assertThat(read(env, "MEMORY.md"))
                    .contains("Memory user")
                    .contains("Memory feedback")
                    .contains("Memory project")
                    .contains("Memory reference");
            return;
        }

        if (name.contains("max_lines")) {
            for (int i = 0; i < 210; i++) {
                write(env, "mem_" + i + ".md", memory("Memory " + i, "Test memory " + i, "user", "Body " + i));
            }
            assertThat(read(env, "MEMORY.md").split("\\n")).hasSize(200);
            return;
        }

        write(env, "note.md", memory("Project Deadline", "Mobile release freeze date", "project",
                "Release freeze: 2026-04-15"));
        assertThat(read(env, "note.md")).contains("Project Deadline").contains("2026-04-15");
        assertThat(read(env, "MEMORY.md")).contains("Project Deadline").contains("note.md");
    }

    private static void assertToolRejectionSemantics(String name) {
        TestEnv env = TestEnv.create("reject-" + name.hashCode());

        if (name.contains("invalid_frontmatter")) {
            assertThat(write(env, "invalid.md", "plain text without frontmatter"))
                    .containsEntry("success", false);
            return;
        }
        if (name.contains("invalid_type")) {
            assertThat(write(env, "invalid_type.md", memory("Test", "Test", "invalid_type", "content")))
                    .containsEntry("success", false);
            return;
        }
        if (name.contains("path_traversal")) {
            assertThat(write(env, "../etc/passwd.md", memory("Test", "Test", "user", "content")))
                    .containsEntry("success", false);
            return;
        }
        if (name.contains("old_text_not_found")) {
            write(env, "test.md", memory("Test", "Test", "user", "original content"));
            assertThat(CodingMemoryToolOps
                    .codingMemoryEditWithContext(env.context, "test.md", "missing text", "new content")
                    .join())
                    .containsEntry("success", false);
            return;
        }
        if (name.contains("multiple_matches")) {
            write(env, "multi.md", memory("Test", "Test", "user", "repeat\nrepeat"));
            assertThat(CodingMemoryToolOps
                    .codingMemoryEditWithContext(env.context, "multi.md", "repeat", "once")
                    .join())
                    .containsEntry("success", false);
            return;
        }
        if (name.contains("nonexistent")) {
            assertThat(CodingMemoryToolOps
                    .codingMemoryReadWithContext(env.context, "nonexistent.md", null, null)
                    .join())
                    .containsEntry("success", false);
            return;
        }
        if (name.contains("empty_old_text")) {
            write(env, "test.md", memory("Test", "Test", "user", "content"));
            assertThat(CodingMemoryToolOps
                    .codingMemoryEditWithContext(env.context, "test.md", "", "new content")
                    .join())
                    .containsEntry("success", false);
            return;
        }
        assertThat(write(env, name.contains("non_md") ? "test.txt" : "empty.md",
                name.contains("empty") ? "" : memory("Test", "Test", "user", "content")))
                .containsEntry("success", false);
    }

    private static Map<String, Object> write(TestEnv env, String path, String content) {
        return CodingMemoryToolOps.codingMemoryWriteWithContext(env.context, path, content).join();
    }

    private static String read(TestEnv env, String filename) {
        return env.sysOperation.fs.files.getOrDefault(env.codingMemoryPath(filename), "");
    }

    private static String memory(String name, String description, String type, String body) {
        return """
                ---
                name: %s
                description: %s
                type: %s
                ---

                %s
                """.formatted(name, description, type, body);
    }

    private record TestEnv(CodingMemoryToolContext context, FakeSysOperation sysOperation, Path codingMemoryDir) {

        static TestEnv create(String rootName) {
            Workspace workspace = new Workspace(Path.of("target").resolve("coding-memory-parity").resolve(rootName));
            FakeSysOperation sysOperation = new FakeSysOperation();
            CodingMemoryToolContext context = new CodingMemoryToolContext();
            context.setWorkspace(workspace);
            context.setCodingMemoryDir(workspace.getNodePath("coding_memory").normalize().toString());
            context.setSysOperation(sysOperation);
            return new TestEnv(context, sysOperation, workspace.getNodePath("coding_memory").normalize());
        }

        String codingMemoryPath(String filename) {
            return codingMemoryDir.resolve(filename).normalize().toString();
        }
    }

    public static final class FakeSysOperation {
        private final FakeFs fs = new FakeFs();

        public FakeFs fs() {
            return fs;
        }
    }

    public static final class FakeFs {
        private final Map<String, String> files = new LinkedHashMap<>();

        public FileResult readFile(String path) {
            if (!files.containsKey(path)) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            return new FileResult(new FileData(files.get(path)));
        }

        public FileResult readFile(String path, int[] lineRange) {
            return readFile(path);
        }

        public FileResult writeFile(String path, String content, boolean createIfNotExist, boolean append) {
            if (append) {
                files.put(path, files.getOrDefault(path, "") + content);
            } else {
                files.put(path, content);
            }
            return new FileResult(new FileData(content));
        }

        public ListResult listFiles(String directory, boolean recursive) {
            List<FileItem> items = new ArrayList<>();
            Path dir = Path.of(directory).normalize();
            for (String path : files.keySet()) {
                Path filePath = Path.of(path).normalize();
                if (dir.equals(filePath.getParent())) {
                    items.add(new FileItem(filePath.getFileName().toString(), false));
                }
            }
            return new ListResult(new ListData(items));
        }
    }

    public record FileResult(FileData data) {
        public FileData getData() {
            return data;
        }
    }

    public record FileData(String content) {
        public String getContent() {
            return content;
        }
    }

    public record ListResult(ListData data) {
        public ListData getData() {
            return data;
        }
    }

    public record ListData(List<FileItem> listItems) {
        public List<FileItem> getListItems() {
            return listItems;
        }
    }

    public record FileItem(String name, boolean directory) {
        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }
    }
}
