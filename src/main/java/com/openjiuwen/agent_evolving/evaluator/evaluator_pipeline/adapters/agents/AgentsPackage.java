/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.agents;

import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.AdapterRegistry;
import com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.BaseAgentAdapter;

import java.util.List;

/**
 * Package bridge for evaluator-pipeline agent adapter exports.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters.agents}
 * in {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/__init__.py}.
 * </p>
 */
public final class AgentsPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/evaluator/evaluator_pipeline/adapters/agents/__init__.py";
    public static final Class<JiuWenSwarmAgent> JIU_WEN_SWARM_AGENT = JiuWenSwarmAgent.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("JiuWenSwarmAgent");

    static {
        registerAll();
    }

    private AgentsPackage() {
    }

    public static void registerAll() {
        AdapterRegistry.registerAgent("jiuwenswarm", JiuWenSwarmAgent.class);
    }

    public static List<Class<? extends BaseAgentAdapter>> exportedAgentAdapters() {
        registerAll();
        return List.<Class<? extends BaseAgentAdapter>>of(JiuWenSwarmAgent.class);
    }
}
