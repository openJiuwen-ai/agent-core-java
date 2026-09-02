/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_creator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class SkillCreatorSecurityTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesOutputOnlyWithinAllowedRoot() throws IOException {
        Path allowedRoot = Files.createDirectories(tempDir.resolve("allowed"));
        SkillCreator creator = new SkillCreator(allowedRoot);

        Path resolved = creator.resolveSafeOutputDirectory("generated/skill");

        assertEquals(allowedRoot.resolve("generated/skill").toRealPath(), resolved);
        assertThrows(SecurityException.class, () -> creator.resolveSafeOutputDirectory("../outside"));
        assertThrows(SecurityException.class,
                () -> creator.resolveSafeOutputDirectory(tempDir.resolve("absolute-outside").toString()));
    }

    @Test
    void rejectsOutputThroughSymbolicLink() throws IOException {
        Path allowedRoot = Files.createDirectories(tempDir.resolve("allowed"));
        Path outsideRoot = Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(allowedRoot.resolve("linked"), outsideRoot);
        SkillCreator creator = new SkillCreator(allowedRoot);

        assertThrows(SecurityException.class, () -> creator.resolveSafeOutputDirectory("linked/generated"));
    }
}
