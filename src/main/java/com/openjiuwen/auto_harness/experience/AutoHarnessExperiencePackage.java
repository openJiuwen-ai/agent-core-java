/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.experience;

import java.util.List;

/**
 * Public facade for the auto-harness experience package.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.experience} in
 * {@code openjiuwen/auto_harness/experience/__init__.py}.</p>
 */
public final class AutoHarnessExperiencePackage {

    public static final String PYTHON_MODULE = "openjiuwen/auto_harness/experience/__init__.py";
    public static final List<String> ALL = List.of("ActiveContextSynthesizer", "ExperienceStore");
    public static final Class<ActiveContextSynthesizer> ACTIVE_CONTEXT_SYNTHESIZER = ActiveContextSynthesizer.class;
    public static final Class<ExperienceStore> EXPERIENCE_STORE = ExperienceStore.class;

    private AutoHarnessExperiencePackage() {
    }
}
