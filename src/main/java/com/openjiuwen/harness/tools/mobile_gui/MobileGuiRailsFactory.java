/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.mobile_gui.rails.DeviceLifecycleRail;
import com.openjiuwen.harness.tools.mobile_gui.rails.GoalAnchorInjectorRail;
import com.openjiuwen.harness.tools.mobile_gui.rails.MultimodalContextSummarizerRail;
import com.openjiuwen.harness.tools.mobile_gui.rails.MultimodalSkillBranchRail;
import com.openjiuwen.harness.tools.mobile_gui.rails.MultimodalSkillReadRail;
import com.openjiuwen.harness.tools.mobile_gui.rails.VlmGroundingPerceptionRail;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mobile GUI rail factory.
 *
 * <p>Mirrors Python's {@code resolve_mobile_skill_root},
 * {@code infer_model_display_name}, and {@code build_mobile_gui_rails} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails_factory.py}.</p>
 */
public final class MobileGuiRailsFactory {

    private MobileGuiRailsFactory() {
    }

    public static String resolveMobileSkillRoot(Object workspace) {
        if (workspace == null) {
            return "";
        }
        return Path.of(String.valueOf(workspace)).resolve(".skills").normalize().toString();
    }

    public static String inferModelDisplayName(Object model) {
        return model == null ? "unknown" : String.valueOf(model);
    }

    public static List<DeepAgentRail> buildMobileGuiRails(MobileGuiRuntimeSettings settings) {
        MobileGuiRuntimeSettings resolved = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        List<DeepAgentRail> rails = new ArrayList<>();
        rails.add(new DeviceLifecycleRail(resolved));
        rails.add(new GoalAnchorInjectorRail());
        rails.add(new MultimodalContextSummarizerRail(resolved.getContextMaxMessageNum()));
        rails.add(new MultimodalSkillReadRail(resolved.getSkillBranchMaxImages()));
        if (resolved.getSkillConsultMode() == SkillConsultMode.BRANCH) {
            rails.add(new MultimodalSkillBranchRail(resolved));
        }
        rails.add(new VlmGroundingPerceptionRail(resolved));
        return rails;
    }
}
