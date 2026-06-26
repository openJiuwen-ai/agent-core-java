/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import java.util.List;

/**
 * Package facade for agent-team model allocation exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.models} in
 * {@code openjiuwen/agent_teams/models/__init__.py}.</p>
 */
public final class ModelsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/models/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Allocation",
            "ByModelNameAllocator",
            "ModelAllocator",
            "ModelPoolEntry",
            "ModelRouterConfig",
            "RoundRobinModelAllocator",
            "RouterAllocator",
            "build_model_allocator",
            "inherit_pool_ids",
            "resolve_member_model"
    );

    private ModelsPackage() {
    }
}
