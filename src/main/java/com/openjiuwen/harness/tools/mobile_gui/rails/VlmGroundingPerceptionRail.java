/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;
import com.openjiuwen.harness.tools.mobile_gui.rails.VlmRailUtils;

import java.util.Map;

/**
 * Captures VLM observation metadata for grounding prompts.
 *
 * <p>Mirrors Python's {@code VlmGroundingPerceptionRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/vlm_grounding_perception_rail.py}.</p>
 */
public class VlmGroundingPerceptionRail extends DeepAgentRail {

    public static final String VLM_OBSERVATION_META_EXTRA_KEY = "_vlm_observation_meta";

    private final MobileGuiRuntimeSettings settings;

    public VlmGroundingPerceptionRail(MobileGuiRuntimeSettings settings) {
        this.settings = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx != null) {
            ctx.put(VLM_OBSERVATION_META_EXTRA_KEY, Map.of(
                    "coordinate_scale", settings.getVlmCoordinateScale(),
                    "max_width", settings.getVlmGroundingMaxWidth(),
                    "jpeg_quality", settings.getVlmGroundingJpegQuality()
            ));
        }
    }

    public String appendObservationFooter(String text, Map<String, Object> meta) {
        Object foregroundApp = meta == null ? null : meta.get("foreground_app");
        return VlmRailUtils.appendVlmObservationMetaFooter(text, foregroundApp == null ? "" : String.valueOf(foregroundApp));
    }
}
