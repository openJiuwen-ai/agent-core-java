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
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/__init__.py}.
 */
class SkillBranchPackageTest {

    @TempDir
    Path tempDir;

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        SkillBranchRunner.BranchResult.class,
                        SkillImageEntry.class,
                        "build_skill_image_manifest",
                        "format_planner_tool_message",
                        "run_skill_branch"
                ),
                SkillBranchPackage.exports()
        );
    }

    @Test
    void delegatesManifestAndFormatter() throws Exception {
        Files.writeString(tempDir.resolve("reference.png"), "x");

        List<SkillImageEntry> entries = SkillBranchPackage.buildSkillImageManifest(
                "![Reference](reference.png)",
                tempDir.toString()
        );
        assertEquals(1, entries.size());
        assertEquals("reference", entries.get(0).imageId());

        String rendered = SkillBranchPackage.formatPlannerToolMessage(
                "contacts",
                Map.of("skill_applicability", "high", "plan", "open profile"),
                "use image one"
        );
        assertTrue(rendered.startsWith("Skill consult: contacts\nVisual selection: use image one"));
        assertTrue(rendered.contains("Plan: open profile"));
    }

    @Test
    void delegatesRunnerBoundary() {
        SkillBranchRunner.BranchResult empty = SkillBranchPackage.runSkillBranch();
        assertFalse(empty.selected());
        assertEquals("", empty.memo());

        SkillBranchRunner.BranchResult selected = SkillBranchPackage.runSkillBranch("tap card", List.of("ref"));
        assertTrue(selected.selected());
        assertEquals("tap card", selected.memo());
        assertEquals(List.of("ref"), selected.selectedImages());
    }
}
