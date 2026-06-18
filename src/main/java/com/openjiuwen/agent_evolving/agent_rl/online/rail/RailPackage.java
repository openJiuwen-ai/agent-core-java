/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rail-based online RL collection package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.rail} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/__init__.py}.</p>
 */
public final class RailPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/agent_rl/online/rail/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "OnlineTrajectoryConverter",
            "PerTurnSample",
            "RailV1Batch",
            "RLOnlineRail",
            "TrajectoryMeta",
            "TrajectoryUploader",
            "build_rl_online_rail_from_env",
            "is_rl_online_rail_enabled_from_env"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private RailPackage() {
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

    public static RLOnlineRail buildRlOnlineRailFromEnv() {
        return RLOnlineRailFactory.buildRlOnlineRailFromEnv();
    }

    public static boolean isRlOnlineRailEnabledFromEnv() {
        return RLOnlineRailFactory.isRlOnlineRailEnabledFromEnv();
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("OnlineTrajectoryConverter", OnlineTrajectoryConverter.class);
        exports.put("PerTurnSample", PerTurnSample.class);
        exports.put("RailV1Batch", RailV1Batch.class);
        exports.put("RLOnlineRail", RLOnlineRail.class);
        exports.put("TrajectoryMeta", TrajectoryMeta.class);
        exports.put("TrajectoryUploader", TrajectoryUploader.class);
        return Collections.unmodifiableMap(exports);
    }
}
