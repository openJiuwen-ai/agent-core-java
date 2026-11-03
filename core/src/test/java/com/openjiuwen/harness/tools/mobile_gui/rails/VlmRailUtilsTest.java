/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VlmRailUtilsTest {

    @Test
    void constantsMatchPythonKeys() {
        assertEquals("_ephemeral_goal_anchor", VlmRailUtils.GOAL_ANCHOR_KEY);
        assertEquals("_goal_anchor_injector_state", VlmRailUtils.GOAL_ANCHOR_INJECTOR_STATE_KEY);
        assertEquals("_vlm_observation_meta", VlmRailUtils.VLM_OBSERVATION_META_EXTRA_KEY);
    }

    @Test
    void appendFooterTrimsTrailingWhitespaceAndEmbedsJson() {
        String rendered = VlmRailUtils.appendVlmObservationMetaFooter("observation  \n", "Maps");

        assertTrue(rendered.startsWith("observation\n[vlm_meta]{\"foreground_app\":\"Maps\"}[/vlm_meta]"));
    }
}
