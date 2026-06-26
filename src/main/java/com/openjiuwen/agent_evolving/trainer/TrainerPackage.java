/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trainer;

import java.util.List;

/**
 * Package exports for the trainer module.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trainer} module in
 * {@code openjiuwen/agent_evolving/trainer/__init__.py}.</p>
 */
public final class TrainerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/trainer/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Trainer",
            "Progress",
            "Callbacks"
    );

    private TrainerPackage() {
    }
}
