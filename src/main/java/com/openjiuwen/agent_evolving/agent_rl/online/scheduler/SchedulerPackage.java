/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Online RL scheduler package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.scheduler} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/__init__.py}.</p>
 */
public final class SchedulerPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/agent_rl/online/scheduler/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "OnlineTrainingScheduler",
            "PPOTrainingExecutor"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private SchedulerPackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("OnlineTrainingScheduler", OnlineTrainingScheduler.class);
        exports.put("PPOTrainingExecutor", PpoTrainingExecutor.class);
        return Collections.unmodifiableMap(exports);
    }
}
