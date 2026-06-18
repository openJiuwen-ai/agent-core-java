/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

/**
 * Package marker for trainer helpers.
 * <p>
 * Mirrors Python's {@code openjiuwen/dev_tools/tune/trainer/__init__.py}.
 */
public final class TrainerPackage {
    public static final String PYTHON_MODULE = "openjiuwen/dev_tools/tune/trainer/__init__.py";
    public static final Class<Trainer> TRAINER = Trainer.class;
    public static final Class<ParameterSearcher> PARAMETER_SEARCHER = ParameterSearcher.class;
    public static final Class<Progress> PROGRESS = Progress.class;
    public static final Class<Callbacks> CALLBACKS = Callbacks.class;
    public static final java.util.List<String> EXPORTED_SYMBOLS = java.util.List.of(
            "Trainer",
            "ParameterSearcher",
            "Progress",
            "Callbacks"
    );

    private TrainerPackage() {
    }
}
