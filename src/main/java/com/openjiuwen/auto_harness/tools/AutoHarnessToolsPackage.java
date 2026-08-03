/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.tools;

import java.util.List;

/**
 * Public facade for auto-harness tools.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.tools} in
 * {@code openjiuwen/auto_harness/tools/__init__.py}.</p>
 */
public final class AutoHarnessToolsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/tools/__init__.py";
    public static final List<String> ALL = List.of("ExperienceSearchTool");
    public static final Class<ExperienceSearchTool> EXPERIENCE_SEARCH_TOOL = ExperienceSearchTool.class;

    private AutoHarnessToolsPackage() {
    }
}
