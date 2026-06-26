/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import java.util.List;

/**
 * Package marker for skill tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.tools.skills} in
 * {@code openjiuwen/harness/tools/skills/__init__.py}.</p>
 */
public final class SkillsPackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(
            ListSkillTool.class,
            SkillTool.class,
            SkillDescriptor.class
    );

    private SkillsPackage() {
    }
}
