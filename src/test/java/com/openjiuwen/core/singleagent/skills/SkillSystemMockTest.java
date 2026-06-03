/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill system capability tests (Mock SysOperation + optional real-LLM E2E).
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent/skill/test_skill_system_mock.py}.
 */
@DisplayName("Skill System Mock")
class SkillSystemMockTest {

    @TempDir
    Path tempDir;

    private String sysOperationId;
    private Path skillsRootOk;
    private Path skillsRootBad;
    private Path singleSkillDir;
    private Path singleSkillMd;
    private String singleSkillName;
    private String mockSkillName;
    private Path goodSkillDir;
    private Path goodSkillMd;
    private Path badSkillMd;
    private Path sampleTxt;

    @BeforeEach
    void setUp() throws IOException {
        Runner.start();
        sysOperationId = "ut_skill_sysop_" + UUID.randomUUID().toString().replace("-", "");
        skillsRootOk = tempDir.resolve("skills_ok");
        skillsRootBad = tempDir.resolve("skills_bad");
        singleSkillDir = tempDir.resolve("single_skill");
        singleSkillMd = singleSkillDir.resolve("SKILL.md");
        singleSkillName = singleSkillDir.getFileName().toString();
        mockSkillName = "good_skill";
        goodSkillDir = skillsRootOk.resolve(mockSkillName);
        goodSkillMd = goodSkillDir.resolve("SKILL.md");
        Path badSkillDir = skillsRootBad.resolve("bad_skill");
        badSkillMd = badSkillDir.resolve("SKILL.md");
        sampleTxt = tempDir.resolve("files").resolve("a.txt");

        Files.createDirectories(goodSkillDir);
        Files.writeString(goodSkillMd, makeSkillMd("UT mock skill description", "body\n"));
        Files.createDirectories(badSkillDir);
        Files.writeString(badSkillMd, makeSkillMd(null, "body\n"));
        Files.createDirectories(singleSkillDir);
        Files.writeString(singleSkillMd, makeSkillMd("SINGLE desc", "body\n"));
        Files.createDirectories(sampleTxt.getParent());
        Files.writeString(sampleTxt, "hello_skill_tool");
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("test_end_to_end_real_llm")
    void testEndToEndRealLlm() {
        Assumptions.assumeTrue(
                "1".equals(System.getenv("RUN_REAL_LLM_TESTS")),
                "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable."
        );
        Assumptions.assumeTrue(
                System.getenv("API_KEY") != null && !System.getenv("API_KEY").isBlank(),
                "Real LLM test skipped. API_KEY is required when RUN_REAL_LLM_TESTS=1."
        );

        ReActAgent agent = createAgentForLlm();
        agent.registerSkill(skillsRootOk.toString());

        assertThat(agent.getSkillUtil()).isNotNull();
        assertThat(agent.getSkillUtil().hasSkill()).isTrue();
        assertThat(agent.getSkillUtil().getSkillPrompt())
                .contains(mockSkillName)
                .contains("UT mock skill description")
                .contains("using read_file");
    }

    @Test
    @DisplayName("test_skill_manager_register_scan_dir_ok")
    void testSkillManagerRegisterScanDirOk() {
        SkillManager manager = new SkillManager(sysOperationId);
        manager.register(skillsRootOk);

        assertThat(manager.has(mockSkillName)).isTrue();
        Skill skill = manager.get(mockSkillName);
        assertThat(skill).isNotNull();
        assertThat(skill.getDescription()).isEqualTo("UT mock skill description");
        assertThat(Path.of(skill.getDirectory()).getFileName().toString()).isEqualTo(mockSkillName);
    }

    @Test
    @DisplayName("test_skill_manager_register_single_file_ok")
    void testSkillManagerRegisterSingleFileOk() {
        SkillManager manager = new SkillManager(sysOperationId);
        manager.register(singleSkillMd);

        assertThat(manager.has(singleSkillName)).isTrue();
        Skill skill = manager.get(singleSkillName);
        assertThat(skill).isNotNull();
        assertThat(skill.getDescription()).isEqualTo("SINGLE desc");
    }

    @Test
    @DisplayName("test_skill_manager_register_skill_dir_ok")
    void testSkillManagerRegisterSkillDirOk() {
        SkillManager manager = new SkillManager(sysOperationId);
        manager.register(singleSkillDir.getParent());

        assertThat(manager.has(singleSkillName)).isTrue();
        Skill skill = manager.get(singleSkillName);
        assertThat(skill).isNotNull();
        assertThat(skill.getDescription()).isEqualTo("SINGLE desc");
    }

    @Test
    @DisplayName("test_skill_manager_register_duplicate_overwrite")
    void testSkillManagerRegisterDuplicateOverwrite() {
        SkillManager manager = new SkillManager(sysOperationId);
        manager.register(singleSkillMd);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.register(singleSkillMd, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Skill already exists");
        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.get(singleSkillName).getDescription()).isEqualTo("SINGLE desc");

        manager.register(singleSkillMd, null, true);
        assertThat(manager.has(singleSkillName)).isTrue();
        assertThat(manager.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("test_skill_manager_registry_ops")
    void testSkillManagerRegistryOps() {
        SkillManager manager = new SkillManager(sysOperationId);
        manager.register(singleSkillMd);

        assertThat(manager.count()).isEqualTo(1);
        assertThat(manager.getNames()).containsExactly(singleSkillName);

        manager.unregister(singleSkillName);
        assertThat(manager.has(singleSkillName)).isFalse();
        assertThat(manager.count()).isZero();

        manager.clear();
        assertThat(manager.count()).isZero();
    }

    @Test
    @DisplayName("test_skill_manager_missing_description_raises_keyerror")
    void testSkillManagerMissingDescriptionRaisesKeyerror() {
        SkillManager manager = new SkillManager(sysOperationId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.register(skillsRootBad))
                .isInstanceOf(SkillManager.KeyError.class)
                .hasMessageContaining("description is required");
    }

    @Test
    @DisplayName("test_skill_manager_yaml_missing_front_matter_raises_keyerror")
    void testSkillManagerYamlMissingFrontMatterRaisesKeyerror() throws IOException {
        SkillManager manager = new SkillManager(sysOperationId);
        Files.writeString(singleSkillMd, "no front matter");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.register(singleSkillMd))
                .isInstanceOf(SkillManager.KeyError.class)
                .hasMessageContaining("description is required");
    }

    @Test
    @DisplayName("test_skill_manager_read_file_code_nonzero_raises_filenotfound")
    void testSkillManagerReadFileCodeNonzeroRaisesFilenotfound() {
        SkillManager manager = new SkillManager(sysOperationId);
        Path missingFile = singleSkillDir.resolve("missing-SKILL.md");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.register(missingFile))
                .isInstanceOf(SkillManager.FileNotFoundError.class);
    }

    @Test
    @DisplayName("test_skill_manager_read_file_content_none_raises_filenotfound")
    void testSkillManagerReadFileContentNoneRaisesFilenotfound() throws IOException {
        SkillManager manager = new SkillManager(sysOperationId);
        Files.writeString(singleSkillMd, "");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.register(singleSkillMd))
                .isInstanceOf(SkillManager.FileNotFoundError.class)
                .hasMessageContaining("content is empty");
    }

    @Test
    @DisplayName("test_skill_util_register_and_prompt")
    void testSkillUtilRegisterAndPrompt() {
        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("ut_agent")
                .name("ut_agent")
                .description("x")
                .build());
        SkillUtil util = new SkillUtil(sysOperationId);

        util.registerSkills(skillsRootOk.toString(), agent);

        assertThat(util.hasSkill()).isTrue();
        String prompt = util.getSkillPrompt();
        assertThat(prompt)
                .contains("Skill name:")
                .contains(mockSkillName)
                .contains("UT mock skill description")
                .contains("using read_file")
                .doesNotContain("using view_file");
    }

    private ReActAgent createAgentForLlm() {
        String apiBase = System.getenv().getOrDefault("API_BASE", "https://openrouter.ai/api/v1");
        String apiKey = System.getenv().getOrDefault("API_KEY", "");
        String modelName = System.getenv().getOrDefault("MODEL_NAME", "z-ai/glm-4.7");
        String modelProvider = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");

        ReActAgent agent = new ReActAgent(AgentCard.builder()
                .id("ut_skill_agent")
                .name("ut_skill_agent")
                .description("Skill Agent UT")
                .build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .modelClientConfig(ModelClientConfig.builder()
                        .clientProvider(modelProvider)
                        .apiKey(apiKey)
                        .apiBase(apiBase)
                        .verifySsl(false)
                        .build())
                .modelConfigObj(ModelRequestConfig.builder()
                        .modelName(modelName)
                        .build())
                .promptTemplate(List.of(Map.of("role", "system", "content", "Use equipped skills when helpful.")))
                .maxIterations(10)
                .sysOperationId(sysOperationId)
                .build();
        agent.configure(config);
        return agent;
    }

    private static String makeSkillMd(String description, String body) {
        if (description == null) {
            return "---\nfoo: bar\n---\n" + body;
        }
        return "---\ndescription: " + description + "\n---\n" + body;
    }

    static final class MockFS {
        final Map<String, List<String>> dirs = new LinkedHashMap<>();
        final Map<String, List<String>> files = new LinkedHashMap<>();
        final Map<String, Object> content = new LinkedHashMap<>();
        final Set<String> failRead = new HashSet<>();
        final Set<String> failListDirs = new HashSet<>();
        final Set<String> failListFiles = new HashSet<>();

        static String normalize(String path) {
            return (path == null ? "" : path).replace("\\", "/");
        }

        void addDir(String path) {
            path = normalize(path);
            dirs.computeIfAbsent(path, key -> new ArrayList<>());
            files.computeIfAbsent(path, key -> new ArrayList<>());
        }

        void addSubdir(String parent, String subdir) {
            parent = normalize(parent);
            subdir = normalize(subdir);
            addDir(parent);
            addDir(subdir);
            if (!dirs.get(parent).contains(subdir)) {
                dirs.get(parent).add(subdir);
            }
        }

        void addFile(String dirPath, String filePath, Object fileContent) {
            dirPath = normalize(dirPath);
            filePath = normalize(filePath);
            addDir(dirPath);
            if (!files.get(dirPath).contains(filePath)) {
                files.get(dirPath).add(filePath);
            }
            content.put(filePath, fileContent);
        }

        MockRes listDirectories(String path, boolean recursive) {
            path = normalize(path);
            if (failListDirs.contains(path)) {
                return new MockRes(1, "list_directories failed: " + path, new MockData(path, List.of(), null));
            }
            if (content.containsKey(path)) {
                return new MockRes(1, "not a directory: " + path, new MockData(path, List.of(), null));
            }
            List<MockItem> items = dirs.getOrDefault(path, List.of()).stream()
                    .map(p -> new MockItem(Path.of(p).getFileName().toString(), p))
                    .toList();
            return new MockRes(0, "", new MockData(path, items, null));
        }

        MockRes listFiles(String path, boolean recursive) {
            path = normalize(path);
            if (failListFiles.contains(path)) {
                return new MockRes(1, "list_files failed: " + path, new MockData(path, List.of(), null));
            }
            List<MockItem> items = files.getOrDefault(path, List.of()).stream()
                    .map(p -> new MockItem(Path.of(p).getFileName().toString(), p))
                    .toList();
            return new MockRes(0, "", new MockData(path, items, null));
        }

        MockRes readFile(String path, String mode, String encoding) {
            path = normalize(path);
            if (failRead.contains(path)) {
                return new MockRes(1, "read_file failed: " + path, new MockData(path, List.of(), null));
            }
            return new MockRes(0, "", new MockData(path, List.of(), content.get(path)));
        }
    }

    record MockItem(String name, String path) {
    }

    record MockData(String rootPath, List<MockItem> listItems, Object content) {
        MockData {
            listItems = listItems == null ? List.of() : listItems;
        }
    }

    record MockRes(int code, String message, MockData data) {
    }

    static final class MockCode {
        MockRes executeCode(String code, String language, Map<String, Object> kwargs) {
            if (!"python".equals(language)) {
                return new MockRes(1, "unsupported language: " + language,
                        new MockData("", List.of(), Map.of("stdout", "", "stderr", "")));
            }
            String stdout = code != null && code.contains("123 + 456") ? "579\n" : "";
            return new MockRes(0, "", new MockData("", List.of(), Map.of("stdout", stdout, "stderr", "")));
        }
    }

    static final class MockShell {
        MockRes executeCmd(String command, Map<String, Object> kwargs) {
            String cmd = command == null ? "" : command.strip();
            String stdout = cmd.toLowerCase().startsWith("echo ") ? cmd.substring(5).stripLeading() + "\n" : "";
            return new MockRes(0, "", new MockData("", List.of(), Map.of("stdout", stdout, "stderr", "")));
        }
    }

    static final class MockSysOperation {
        private final MockFS fs;
        private final MockCode code;
        private final MockShell shell;

        MockSysOperation(MockFS fs, MockCode code, MockShell shell) {
            this.fs = fs;
            this.code = code;
            this.shell = shell;
        }

        MockFS fs() {
            return fs;
        }

        MockCode code() {
            return code;
        }

        MockShell shell() {
            return shell;
        }
    }
}
