/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import java.util.List;
import java.util.Map;

/**
 * Package facade for multimodal skill branch helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/tools/mobile_gui/skill_branch/__init__.py}.</p>
 */
public final class SkillBranchPackage {

    private SkillBranchPackage() {
    }

    public static List<Object> exports() {
        return List.of(
                SkillBranchRunner.BranchResult.class,
                SkillImageEntry.class,
                "build_skill_image_manifest",
                "format_planner_tool_message",
                "run_skill_branch"
        );
    }

    public static List<SkillImageEntry> buildSkillImageManifest(String skillMarkdown, String skillDirectory) {
        return SkillBranchManifest.buildSkillImageManifest(skillMarkdown, skillDirectory);
    }

    public static String formatPlannerToolMessage(
            String skillName,
            Map<String, String> planner,
            String stage1Note
    ) {
        return SkillBranchFormat.formatPlannerToolMessage(skillName, planner, stage1Note);
    }

    public static SkillBranchRunner.BranchResult runSkillBranch() {
        return SkillBranchRunner.empty();
    }

    public static SkillBranchRunner.BranchResult runSkillBranch(String plannerMemo, List<String> selectedImages) {
        return SkillBranchRunner.fromPlannerMemo(plannerMemo, selectedImages);
    }
}
