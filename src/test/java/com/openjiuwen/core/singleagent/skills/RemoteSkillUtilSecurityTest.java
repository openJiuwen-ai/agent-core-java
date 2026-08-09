/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class RemoteSkillUtilSecurityTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesNestedSkillFilesWithinBaseDirectory() throws Exception {
        Path baseDir = Files.createDirectories(tempDir.resolve("skills"));

        Path target = RemoteSkillUtil.resolveSafeTarget(baseDir, "example/references/guide.md");

        assertEquals(baseDir.toRealPath().resolve("example/references/guide.md"), target);
        assertEquals(baseDir.resolve("example/references").toRealPath(), target.getParent());
    }

    @Test
    void rejectsAbsoluteAndParentTraversalPaths() throws Exception {
        Path baseDir = Files.createDirectories(tempDir.resolve("skills"));

        assertThrows(SecurityException.class,
                () -> RemoteSkillUtil.resolveSafeTarget(baseDir, "../escaped.md"));
        assertThrows(SecurityException.class,
                () -> RemoteSkillUtil.resolveSafeTarget(baseDir, tempDir.resolve("absolute.md").toString()));
    }

    @Test
    void rejectsSymbolicLinksOutsideBaseDirectory() throws Exception {
        Path baseDir = Files.createDirectories(tempDir.resolve("skills"));
        Path outsideDir = Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(baseDir.resolve("linked"), outsideDir);

        assertThrows(SecurityException.class,
                () -> RemoteSkillUtil.resolveSafeTarget(baseDir, "linked/escaped.md"));

        Path skillDir = Files.createDirectories(baseDir.resolve("safe"));
        Path outsideFile = outsideDir.resolve("outside.md");
        Files.writeString(outsideFile, "outside");
        Files.createSymbolicLink(skillDir.resolve("SKILL.md"), outsideFile);
        assertThrows(SecurityException.class,
                () -> RemoteSkillUtil.resolveSafeTarget(baseDir, "safe/SKILL.md"));
    }
}
