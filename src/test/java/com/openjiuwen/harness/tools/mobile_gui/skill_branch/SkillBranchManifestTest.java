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

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.mobile_gui.test_skill_branch_manifest} in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_skill_branch_manifest.py}.</p>
 */
class SkillBranchManifestTest {

    @TempDir
    Path tempDir;

    @Test
    void buildManifestSkipsRemoteImagesAndResolvesLocalFiles() throws Exception {
        Path skillDir = tempDir.resolve("github-com");
        Path images = skillDir.resolve("images");
        Files.createDirectories(images);
        Path png = images.resolve("github_landing_page.png");
        Files.writeString(png, "fake-png-bytes");

        List<SkillImageEntry> entries = SkillBranchManifest.buildSkillImageManifest(
                """
                # GitHub

                ![Landing page](images/github_landing_page.png)

                ![Remote](https://example.com/x.png)
                ![](images/missing.png)
                """,
                skillDir.toString()
        );

        assertEquals(1, entries.size());
        SkillImageEntry entry = entries.get(0);
        assertEquals("github_landing_page", entry.imageId());
        assertEquals("Landing page", entry.alt());
        assertEquals("images/github_landing_page.png", entry.relPath());
        assertEquals(png.toRealPath().toString(), Path.of(entry.absPath()).toRealPath().toString());
        assertTrue(Files.isRegularFile(Path.of(entry.absPath())));
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

    @Test
    void buildSkillImageManifestEmptyMarkdownReturnsNoEntries() {
        assertEquals(List.of(), SkillBranchManifest.buildSkillImageManifest("", tempDir.toString()));
    }

    @Test
    void buildSkillImageManifestCollectsMultipleLocalImages() throws Exception {
        Path skillDir = tempDir.resolve("skill");
        Path images = skillDir.resolve("images");
        Files.createDirectories(images);
        for (String name : List.of("one.png", "two.png")) {
            Files.writeString(images.resolve(name), "x");
        }

        List<SkillImageEntry> entries = SkillBranchManifest.buildSkillImageManifest(
                """
                ![One](images/one.png)
                ![Two](images/two.png)
                """,
                skillDir.toString()
        );

        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(entry -> "one".equals(entry.imageId())));
        assertTrue(entries.stream().anyMatch(entry -> "two".equals(entry.imageId())));
    }
}
