/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import java.util.List;
import java.util.Map;

/**
 * Multimodal skill branch runner boundary.
 *
 * <p>Mirrors Python's {@code BranchResult} and {@code run_skill_branch} in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/runner.py}.</p>
 */
public final class SkillBranchRunner {

    private SkillBranchRunner() {
    }

    /**
     * Mirrors Python's {@code BranchResult} in
     * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/runner.py}.
     */
    public record BranchResult(boolean selected, String memo, List<String> selectedImages, Map<String, Object> raw) {
    }

    public static BranchResult empty() {
        return new BranchResult(false, "", List.of(), Map.of());
    }

    public static BranchResult fromPlannerMemo(String memo, List<String> selectedImages) {
        return new BranchResult(memo != null && !memo.isBlank(), memo == null ? "" : memo,
                selectedImages == null ? List.of() : List.copyOf(selectedImages), Map.of());
    }

    public static String assistantText(Object response) {
        return response == null ? "" : String.valueOf(response);
    }
}
