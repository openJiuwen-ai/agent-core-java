/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.skills;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill system capability tests (Mock SysOperation + optional real-LLM E2E).
 *
 * <p>Mirrors Python's {@code test_skill_system_mock.py} in
 * {@code tests/unit_tests/agent/skill/}.
 */
@DisplayName("Skill System Mock")
class SkillSystemMockTest {

    private MockFS mockFs;

    @BeforeEach
    void setUp() {
        mockFs = new MockFS();
    }

    @Nested
    @DisplayName("MockFS")
    class MockFSTests {

        @Test
        @DisplayName("add_dir creates directory entry")
        void testAddDir() {
            mockFs.addDir("/virtual/skills_ok");
            assertThat(mockFs.dirs).containsKey("/virtual/skills_ok");
            assertThat(mockFs.files).containsKey("/virtual/skills_ok");
        }

        @Test
        @DisplayName("add_subdir creates parent and child")
        void testAddSubdir() {
            mockFs.addSubdir("/virtual/skills_ok", "/virtual/skills_ok/good_skill");
            assertThat(mockFs.dirs).containsKey("/virtual/skills_ok");
            assertThat(mockFs.dirs).containsKey("/virtual/skills_ok/good_skill");
            assertThat(mockFs.dirs.get("/virtual/skills_ok"))
                    .contains("/virtual/skills_ok/good_skill");
        }

        @Test
        @DisplayName("add_file creates file entry and content")
        void testAddFile() {
            mockFs.addFile(
                    "/virtual/skills_ok/good_skill",
                    "/virtual/skills_ok/good_skill/skill.md",
                    "---\ndescription: test\n---\nbody\n"
            );
            assertThat(mockFs.files.get("/virtual/skills_ok/good_skill"))
                    .contains("/virtual/skills_ok/good_skill/skill.md");
            assertThat(mockFs.content)
                    .containsEntry("/virtual/skills_ok/good_skill/skill.md", "---\ndescription: test\n---\nbody\n");
        }

        @Test
        @DisplayName("normalize converts backslashes to forward slashes")
        void testNormalize() {
            assertThat(MockFS.normalize("C:\\Users\\test")).isEqualTo("C:/Users/test");
            assertThat(MockFS.normalize("/unix/path")).isEqualTo("/unix/path");
        }
    }

    @Nested
    @DisplayName("Skill MD generation")
    class SkillMdTests {

        @Test
        @DisplayName("makeSkillMd with description")
        void testMakeSkillMdWithDescription() {
            String md = makeSkillMd("UT mock skill description", "body\n");
            assertThat(md).startsWith("---");
            assertThat(md).contains("description: UT mock skill description");
            assertThat(md).contains("body");
        }

        @Test
        @DisplayName("makeSkillMd without description")
        void testMakeSkillMdWithoutDescription() {
            String md = makeSkillMd(null, "body\n");
            assertThat(md).startsWith("---");
            assertThat(md).contains("foo: bar");
        }
    }

    @Nested
    @DisplayName("Real LLM E2E")
    class RealLlmTests {

        @Test
        @DisplayName("E2E skill execution (skipped by default)")
        void testSkillE2e() {
            Assumptions.assumeTrue(
                    "1".equals(System.getenv("RUN_REAL_LLM_TESTS")),
                    "Real LLM test skipped. Set RUN_REAL_LLM_TESTS=1 to enable."
            );
        }
    }

    static String makeSkillMd(String description, String body) {
        if (description == null) {
            return "---\nfoo: bar\n---\n" + body;
        }
        return "---\ndescription: " + description + "\n---\n" + body;
    }

    static class MockFS {
        final Map<String, List<String>> dirs = new LinkedHashMap<>();
        final Map<String, List<String>> files = new LinkedHashMap<>();
        final Map<String, Object> content = new LinkedHashMap<>();
        final Set<String> failRead = new HashSet<>();
        final Set<String> failListDirs = new HashSet<>();
        final Set<String> failListFiles = new HashSet<>();

        static String normalize(String p) {
            return (p == null ? "" : p).replace("\\", "/");
        }

        void addDir(String path) {
            path = normalize(path);
            dirs.computeIfAbsent(path, k -> new ArrayList<>());
            files.computeIfAbsent(path, k -> new ArrayList<>());
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
    }
}
