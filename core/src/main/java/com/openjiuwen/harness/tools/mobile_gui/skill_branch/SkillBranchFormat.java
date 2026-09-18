/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's formatter helpers in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/format.py}.
 */
public final class SkillBranchFormat {

    private SkillBranchFormat() {
    }

    public static String formatPlannerToolMessage(
            String skillName,
            Map<String, String> planner,
            String stage1Note
    ) {
        Map<String, String> values = planner == null ? Map.of() : planner;
        List<String> lines = new ArrayList<>();
        lines.add("Skill consult: " + skillName);
        lines.add("Applicability: " + values.getOrDefault("skill_applicability", "unknown"));
        lines.add("Subgoal: " + values.getOrDefault("subgoal", ""));
        lines.add("Plan: " + values.getOrDefault("plan", ""));
        lines.add("Do not do: " + values.getOrDefault("do_not_do", ""));
        lines.add("Fallback if no progress: " + values.getOrDefault("fallback_if_no_progress", ""));
        lines.add("Expected state: " + values.getOrDefault("expected_state", ""));
        lines.add("Completion scope: " + values.getOrDefault("completion_scope", "needs_verification"));
        if (stage1Note != null && !stage1Note.trim().isEmpty()) {
            lines.add(1, "Visual selection: " + stage1Note.trim());
        }
        return String.join("\n", lines);
    }

    public static String formatBranchFailureToolMessage(
            String skillName,
            String error,
            String skillExcerpt,
            int maxExcerptChars
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("Skill consult: " + skillName);
        lines.add("Branch consult failed: " + error);

        String excerpt = skillExcerpt == null ? "" : skillExcerpt.trim();
        if (!excerpt.isEmpty()) {
            if (excerpt.length() > maxExcerptChars) {
                excerpt = excerpt.substring(0, maxExcerptChars - 3).stripTrailing() + "...";
            }
            lines.add("Skill excerpt:\n" + excerpt);
        }
        return String.join("\n", lines);
    }
}
