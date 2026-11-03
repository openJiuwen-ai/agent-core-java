/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MobileGuiStateTest {

    @Test
    void sharedBusIsStaticAndMutable() {
        Map<String, Object> shared = MobileGuiState.MOBILE_GUI_SHARED;
        shared.clear();
        shared.put("foreground_app", "Maps");

        assertSame(shared, MobileGuiState.MOBILE_GUI_SHARED);
        assertEquals("Maps", MobileGuiState.MOBILE_GUI_SHARED.get("foreground_app"));
    }
}
