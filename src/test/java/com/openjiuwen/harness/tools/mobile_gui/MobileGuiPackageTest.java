/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's package exports in
 * {@code openjiuwen/harness/tools/mobile_gui/__init__.py}.
 */
class MobileGuiPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertEquals(
                List.of(
                        MobileGuiRuntimeSettings.class,
                        "build_mobile_gui_rails",
                        "build_mobile_gui_tool_instances",
                        "infer_model_display_name",
                        "resolve_mobile_skill_root"
                ),
                MobileGuiPackage.exports()
        );
    }

    @Test
    void delegatesRailsFactoryHelpers() {
        MobileGuiRuntimeSettings settings = MobileGuiRuntimeSettings.fromEnvironment(
                java.util.Map.of("MULTIMODAL_SKILL_CONSULT_MODE", "none")
        );

        assertFalse(MobileGuiPackage.buildMobileGuiRails(settings).isEmpty());
        assertEquals("model-x", MobileGuiPackage.inferModelDisplayName("model-x"));
        assertTrue(MobileGuiPackage.resolveMobileSkillRoot("workspace").replace('\\', '/')
                .endsWith("workspace/.skills"));
    }
}
