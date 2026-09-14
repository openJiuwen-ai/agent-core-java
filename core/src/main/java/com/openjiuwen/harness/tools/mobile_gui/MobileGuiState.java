/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-local shared bus mirroring Python's mobile shared state.
 *
 * <p>Mirrors Python's {@code mobile_gui_shared} in
 * {@code openjiuwen/harness/tools/mobile_gui/state.py}.
 */
public final class MobileGuiState {

    public static final Map<String, Object> MOBILE_GUI_SHARED =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private MobileGuiState() {
    }
}
