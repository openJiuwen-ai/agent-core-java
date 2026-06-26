/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.context;

import java.util.List;

/**
 * Package bridge for context-evolver core context exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/extensions/context_evolver/core/context/__init__.py}.
 * </p>
 */
public final class ContextPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/core/context/__init__.py";
    public static final Class<RuntimeContext> RUNTIME_CONTEXT = RuntimeContext.class;
    public static final Class<ServiceContext> SERVICE_CONTEXT = ServiceContext.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("RuntimeContext", "ServiceContext");

    private ContextPackage() {
    }
}
