/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillBranchManifestTest {

    @TempDir
    Path tempDir;

    @Test
    void buildManifestSkipsRemoteImagesAndResolvesLocalFiles() throws Exception {
        Files.writeString(tempDir.resolve("step one.png"), "x");
        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/diagram.png"), "y");

        List<SkillImageEntry> entries = SkillBranchManifest.buildSkillImageManifest(
                """
                ![First](step%20one.png)
                ![Remote](https://example.com/image.png)
                ![](nested/diagram.png)
                """,
                tempDir.toString()
        );

        assertEquals(2, entries.size());
        assertEquals("step one", entries.get(0).imageId());
        assertEquals("First", entries.get(0).alt());
        assertEquals("nested/diagram.png", entries.get(1).relPath());
        assertEquals("diagram.png", entries.get(1).alt());
        assertTrue(entries.get(1).absPath().endsWith("nested\\diagram.png")
                || entries.get(1).absPath().endsWith("nested/diagram.png"));
    }

    @Test
    void duplicateImageIdsReceiveIndexSuffixAndPromptIsCompact() throws Exception {
        Files.writeString(tempDir.resolve("same.png"), "a");
        Files.createDirectories(tempDir.resolve("other"));
        Files.writeString(tempDir.resolve("other/same.png"), "b");

        List<SkillImageEntry> entries = SkillBranchManifest.buildSkillImageManifest(
                """
                ![One](same.png)
                ![Two](other/same.png)
                """,
                tempDir.toString()
        );

        assertEquals("same", entries.get(0).imageId());
        assertEquals("same_1", entries.get(1).imageId());
        assertEquals(
                "- same: alt='One', path=same.png\n- same_1: alt='Two', path=other/same.png",
                SkillBranchManifest.formatManifestForPrompt(entries)
        );
    }

    @Test
    void emptyManifestUsesPythonFallbackText() {
        assertEquals("(no local reference images)", SkillBranchManifest.formatManifestForPrompt(List.of()));
    }
}
