/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the agent-team models package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.models} in
 * {@code openjiuwen/agent_teams/models/__init__.py}.</p>
 */
class ModelsPackageTest {

    @Test
    void moduleConstantMatchesPythonPackage() {
        assertThat(ModelsPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/models/__init__.py");
    }

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertThat(ModelsPackage.EXPORTED_SYMBOLS).isEqualTo(List.of(
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
        ));
    }
}
