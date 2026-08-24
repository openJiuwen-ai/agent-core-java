/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers skill-branch helpers that used to be reached only through the deleted
 * {@code SkillBranchPackage} export bridge.
 */
class SkillBranchPackageTest {

    @TempDir
    Path tempDir;

    @Test
    void manifestAndFormatter() throws Exception {
        Files.writeString(tempDir.resolve("reference.png"), "x");

        List<SkillImageEntry> entries = SkillBranchManifest.buildSkillImageManifest(
                "![Reference](reference.png)",
                tempDir.toString()
        );
        assertEquals(1, entries.size());
        assertEquals("reference", entries.get(0).imageId());

        String rendered = SkillBranchFormat.formatPlannerToolMessage(
                "contacts",
                Map.of("skill_applicability", "high", "plan", "open profile"),
                "use image one"
        );
        assertTrue(rendered.startsWith("Skill consult: contacts\nVisual selection: use image one"));
        assertTrue(rendered.contains("Plan: open profile"));
    }

    @Test
    void runnerBoundary() {
        SkillBranchRunner.BranchResult empty = SkillBranchRunner.empty();
        assertFalse(empty.selected());
        assertEquals("", empty.memo());

        SkillBranchRunner.BranchResult selected = SkillBranchRunner.fromPlannerMemo("tap card", List.of("ref"));
        assertTrue(selected.selected());
        assertEquals("tap card", selected.memo());
        assertEquals(List.of("ref"), selected.selectedImages());
    }
}
