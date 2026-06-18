/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Branches skill consultation through a dedicated multimodal planner.
 *
 * <p>Mirrors Python's {@code MultimodalSkillBranchRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/multimodal_skill_branch_rail.py}.</p>
 */
public class MultimodalSkillBranchRail extends DeepAgentRail {

    public static final String BRANCH_STATE_KEY = "_multimodal_skill_branch";

    private final MobileGuiRuntimeSettings settings;

    public MultimodalSkillBranchRail(MobileGuiRuntimeSettings settings) {
        this.settings = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("max_images", settings.getSkillBranchMaxImages());
        state.put("max_consults_per_skill", settings.getSkillBranchMaxConsultsPerSkill());
        state.put("previous_steps_turns", settings.getSkillBranchPreviousStepsTurns());
        ctx.put(BRANCH_STATE_KEY, state);
    }
}
