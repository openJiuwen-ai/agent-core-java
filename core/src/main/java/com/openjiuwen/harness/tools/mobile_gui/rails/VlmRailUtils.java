/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * Shared ctx.extra keys and compact observation footers for VLM grounding rails.
 *
 * <p>Mirrors Python's {@code append_vlm_observation_meta_footer} and module
 * constants in {@code openjiuwen/harness/tools/mobile_gui/rails/vlm_rail_utils.py}.
 */
public final class VlmRailUtils {

    public static final String GOAL_ANCHOR_KEY = "_ephemeral_goal_anchor";
    public static final String GOAL_ANCHOR_INJECTOR_STATE_KEY = "_goal_anchor_injector_state";
    public static final String VLM_OBSERVATION_META_EXTRA_KEY = "_vlm_observation_meta";

    private static final String VLM_OPEN = "[vlm_meta]";
    private static final String VLM_CLOSE = "[/vlm_meta]";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VlmRailUtils() {
    }

    public static String appendVlmObservationMetaFooter(String baseText, String foregroundApp) {
        String prefix = (baseText == null ? "" : baseText).stripTrailing();
        try {
            String metaJson = OBJECT_MAPPER.writeValueAsString(Map.of("foreground_app", foregroundApp));
            return prefix + "\n" + VLM_OPEN + metaJson + VLM_CLOSE;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize VLM observation metadata", ex);
        }
    }
}
