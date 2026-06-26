/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.op;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for context evolver operation exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.core.op} in
 * {@code openjiuwen/extensions/context_evolver/core/op/__init__.py}.</p>
 */
public final class OpPackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/core/op/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseOp",
            "SequentialOp",
            "ParallelOp"
    );

    public static final Class<BaseOp> BASE_OP = BaseOp.class;
    public static final Class<SequentialOp> SEQUENTIAL_OP = SequentialOp.class;
    public static final Class<ParallelOp> PARALLEL_OP = ParallelOp.class;

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private OpPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("BaseOp", BaseOp.class);
        exports.put("SequentialOp", SequentialOp.class);
        exports.put("ParallelOp", ParallelOp.class);
        return Map.copyOf(exports);
    }
}
