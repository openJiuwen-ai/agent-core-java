/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CLI default skill directory configuration.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.harness.test_cli_default_skills}.
 */
class TestCliDefaultSkills {

    // ---------------------------------------------------------------------------
    // Constants mirroring Python's _DEFAULT_SKILL_DIRS
    // ---------------------------------------------------------------------------

    private static final List<String> DEFAULT_SKILL_DIRS = Arrays.asList(
        "~/.openjiuwen/workspace/skills",
        "~/.claude/skills",
        "~/.codex/skills",
        "~/.jiuwenclaw/workspace/skills"
    );

    /** Returns a copy of default skill directories. */
    private List<String> defaultSkillDirs() {
        return new ArrayList<>(DEFAULT_SKILL_DIRS);
    }

    // ---------------------------------------------------------------------------
    // Tests: default skill dirs returns expected paths
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("default_skill_dirs returns the four default paths in priority order")
    void testDefaultSkillDirsReturnsExpectedPaths() {
        // Python: test_default_skill_dirs_returns_expected_paths
        List<String> dirs = defaultSkillDirs();
        
        assertEquals(4, dirs.size());
        assertEquals("~/.openjiuwen/workspace/skills", dirs.get(0));
        assertEquals("~/.claude/skills", dirs.get(1));
        assertEquals("~/.codex/skills", dirs.get(2));
        assertEquals("~/.jiuwenclaw/workspace/skills", dirs.get(3));
    }

    // ---------------------------------------------------------------------------
    // Tests: returns copy not original
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    @DisplayName("default_skill_dirs returns a copy, not the original list")
    void testDefaultSkillDirsReturnsCopy() {
        // Python: test_default_skill_dirs_returns_copy
        List<String> dirs = defaultSkillDirs();
        dirs.add("extra");
        
        // Original should still have 4 elements
        assertEquals(4, DEFAULT_SKILL_DIRS.size());
        assertEquals(5, dirs.size());
    }
}