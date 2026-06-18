/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public facade for the handoff multi-agent team package.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.multi_agent.teams.handoff} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/__init__.py}.</p>
 */
public final class HandoffPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/multi_agent/teams/handoff/__init__.py";
    public static final List<String> ALL = List.of(
            "HandoffTeam",
            "HandoffOrchestrator",
            "TeamInterruptSignal",
            "HandoffConfig",
            "HandoffTeamConfig",
            "HandoffRoute",
            "HandoffSignal",
            "extract_handoff_signal",
            "HANDOFF_TARGET_KEY",
            "HANDOFF_MESSAGE_KEY",
            "HANDOFF_REASON_KEY"
    );
    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private HandoffPackage() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static boolean exports(String symbolName) {
        return ALL.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("HandoffTeam", HandoffTeam.class);
        exports.put("HandoffOrchestrator", HandoffOrchestrator.class);
        exports.put("TeamInterruptSignal", TeamInterruptSignal.class);
        exports.put("HandoffConfig", HandoffConfig.class);
        exports.put("HandoffTeamConfig", HandoffTeamConfig.class);
        exports.put("HandoffRoute", HandoffRoute.class);
        exports.put("HandoffSignal", HandoffSignal.class);
        return Collections.unmodifiableMap(exports);
    }
}
