/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Tests Skill document path validation.
 *
 * @since 0.1.17
 */
class SkillPathValidatorTest {
    @TempDir
    private Path tempDir;

    @Test
    void safeRelativePath_isAccepted() {
        assertThat(SkillPathValidator.isSafeRelativePath("SKILL.md")).isTrue();
        assertThat(SkillPathValidator.isSafeRelativePath("docs/readme.md")).isTrue();
    }

    @Test
    void traversalSequence_isRejected() {
        assertThat(SkillPathValidator.isSafeRelativePath("../SKILL.md")).isFalse();
        assertThat(SkillPathValidator.isSafeRelativePath("a/../../etc/passwd")).isFalse();
        assertThat(SkillPathValidator.isSafeRelativePath("..")).isFalse();
    }

    @Test
    void absolutePath_isRejected() {
        assertThat(SkillPathValidator.isSafeRelativePath("/etc/passwd")).isFalse();
        assertThat(SkillPathValidator.isSafeRelativePath("C:\\Windows\\system32")).isFalse();
        assertThat(SkillPathValidator.isSafeRelativePath("\\\\server\\share\\file.md")).isFalse();
    }

    @Test
    void blankOrNull_isRejected() {
        assertThat(SkillPathValidator.isSafeRelativePath("")).isFalse();
        assertThat(SkillPathValidator.isSafeRelativePath(null)).isFalse();
        assertThat(SkillPathValidator.isSafeRelativePath("   ")).isFalse();
    }

    @Test
    void resolveSafe_returnsPathWithinSkillDirectory() throws java.io.IOException {
        java.nio.file.Path demoDir = tempDir.resolve("demo").resolve("docs");
        java.nio.file.Files.createDirectories(demoDir);
        java.nio.file.Files.writeString(demoDir.resolve("readme.md"), "hello");
        Skill skill = new Skill("demo", "Demo.", tempDir.resolve("demo"));
        Optional<Path> resolved = SkillPathValidator.resolveSafe(skill, "docs/readme.md");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().startsWith(tempDir.resolve("demo"))).isTrue();
    }

    @Test
    void resolveSafe_returnsNullOnEscape() {
        Skill skill = new Skill("demo", "Demo.", tempDir.resolve("demo"));
        assertThat(SkillPathValidator.resolveSafe(skill, "../../etc/passwd")).isEmpty();
        assertThat(SkillPathValidator.resolveSafe(skill, null)).isEmpty();
    }
}
