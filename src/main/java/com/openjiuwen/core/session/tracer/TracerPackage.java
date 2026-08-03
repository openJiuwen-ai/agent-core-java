/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.tracer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for tracer exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/session/tracer/__init__.py}.</p>
 */
public final class TracerPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/session/tracer/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Tracer",
            "TracerWorkflowUtils",
            "decorate_model_with_trace",
            "decorate_tool_with_trace",
            "decorate_workflow_with_trace"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private TracerPackage() {
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("Tracer", Tracer.class);
        exports.put("TracerWorkflowUtils", TracerWorkflowUtils.class);
        exports.put("decorate_model_with_trace", TracerDecorator.class);
        exports.put("decorate_tool_with_trace", TracerDecorator.class);
        exports.put("decorate_workflow_with_trace", TracerDecorator.class);
        return Map.copyOf(exports);
    }
}
