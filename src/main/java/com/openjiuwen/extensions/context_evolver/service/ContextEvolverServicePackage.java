/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.service;

import java.util.List;

/**
 * Package facade for context-evolver service exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.service} in
 * {@code openjiuwen/extensions/context_evolver/service/__init__.py}.</p>
 */
public final class ContextEvolverServicePackage {

    public static final List<Class<?>> EXPORTED_TYPES = List.of(
            TaskMemoryService.class,
            AddMemoryRequest.class,
            TrajectoryGenerator.SummarizeTrajectoriesInput.class
    );

    public static final String ALGO_TO_NAME = "_ALGO_TO_NAME";

    private ContextEvolverServicePackage() {
    }
}
