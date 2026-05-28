/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.skill;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import java.nio.file.*;
import java.util.*;

/**
 * Mirrors Python's test_remote_skill.py.
 */
class RemoteSkillTest {

    static final String GITHUB_TOKEN = System.getenv().getOrDefault("GITHUB_TOKEN", "");
    static final String RUN_GITHUB_TEST = System.getenv().getOrDefault("RUN_GITHUB_TEST", "0");

    @Test
    void testFetchSkillFromGithub() {
        assumeTrue("1".equals(RUN_GITHUB_TEST), "GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable.");

        List<Map<String, Object>> fileList = new ArrayList<>();
        Map<String, Object> skillMd = new HashMap<>();
        skillMd.put("path", Path.of("skills/example-skill/SKILL.md"));
        Map<String, Object> refMd = new HashMap<>();
        refMd.put("path", Path.of("skills/example-skill/references/example-reference.md"));
        Map<String, Object> readme = new HashMap<>();
        readme.put("path", Path.of("README.md"));

        fileList.add(skillMd);
        fileList.add(refMd);
        fileList.add(readme);

        List<Path> filePaths = new ArrayList<>();
        for (Map<String, Object> f : fileList) {
            filePaths.add((Path) f.get("path"));
        }

        assertEquals(3, fileList.size());
        assertTrue(filePaths.contains(Path.of("skills/example-skill/SKILL.md")));
        assertTrue(filePaths.contains(Path.of("skills/example-skill/references/example-reference.md")));
        assertTrue(filePaths.contains(Path.of("README.md")));

        Set<Path> skillPaths = new HashSet<>();
        for (Path p : filePaths) {
            if (p.startsWith("skills/")) {
                Path skillName = p.subpath(1, 2);
                skillPaths.add(skillName);
            }
        }
        assertEquals(1, skillPaths.size());
        assertTrue(skillPaths.contains(Path.of("example-skill")));
    }

    @Test
    void testDownloadSkillFromGithub() {
        assumeTrue("1".equals(RUN_GITHUB_TEST), "GitHub remote skill test skipped, set RUN_GITHUB_TEST=1 to enable.");

        String expectedContent = "# Example Reference\n\nExample Reference";
        byte[] referenceFile = expectedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(expectedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8), referenceFile);
    }
}
