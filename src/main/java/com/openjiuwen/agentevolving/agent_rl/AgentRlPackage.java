/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.openjiuwen.agentevolving.agent_rl.config.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.online.rail.RLOnlineRail;
import com.openjiuwen.agentevolving.agent_rl.optimizer.OfflineRLOptimizer;
import com.openjiuwen.agentevolving.agent_rl.optimizer.OnlineRLOptimizer;
import com.openjiuwen.agentevolving.agent_rl.schemas.RLTask;
import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutWithReward;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RL training extension package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl} module in
 * {@code openjiuwen/agent_evolving/agent_rl/__init__.py}.</p>
 */
public final class AgentRlPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/agent_rl/__init__.py";
    public static final String DESCRIPTION = "RL training extension for openjiuwen (agent_rl).";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "RLConfig",
            "OfflineRLOptimizer",
            "OnlineRLOptimizer",
            "RewardRegistry",
            "RLRail",
            "RLOnlineRail",
            "RLTask",
            "Rollout",
            "RolloutMessage",
            "RolloutWithReward"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private AgentRlPackage() {
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

    public static Class<?> getAttribute(String name) {
        Class<?> exportedType = typeFor(name);
        if (exportedType != null) {
            return exportedType;
        }
        throw new IllegalArgumentException(
                "module 'openjiuwen.agent_evolving.agent_rl' has no attribute '" + name + "'"
        );
    }

    public static boolean isLazyLoggerPatchApplied() {
        return true;
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("RLConfig", RLConfig.class);
        exports.put("OfflineRLOptimizer", OfflineRLOptimizer.class);
        exports.put("OnlineRLOptimizer", OnlineRLOptimizer.class);
        exports.put("RewardRegistry", RewardRegistry.class);
        exports.put("RLRail", RLRail.class);
        exports.put("RLOnlineRail", RLOnlineRail.class);
        exports.put("RLTask", RLTask.class);
        exports.put("Rollout", Rollout.class);
        exports.put("RolloutMessage", RolloutMessage.class);
        exports.put("RolloutWithReward", RolloutWithReward.class);
        return Collections.unmodifiableMap(exports);
    }
}
