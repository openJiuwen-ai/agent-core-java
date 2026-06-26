/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for the agent-team memory package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.memory} in
 * {@code openjiuwen/agent_teams/memory/__init__.py}.</p>
 */
class MemoryPackageTest {

    @Test
    void constantsMatchPythonModule() {
        assertThat(MemoryPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/memory/__init__.py");
        assertThat(MemoryPackage.TEAM_MEMORY_FILENAME).isEqualTo("TEAM_MEMORY.md");
        assertThat(MemoryPackage.TEAM_MEMORY_MAX_READ_LINES).isEqualTo(200);
    }

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertThat(MemoryPackage.EXPORTED_SYMBOLS).isEqualTo(List.of(
                "MemberMemoryToolkit",
                "TEAM_MEMORY_FILENAME",
                "TEAM_MEMORY_MAX_READ_LINES",
                "PromptMode",
                "TeamLanguage",
                "TeamLifecycle",
                "TeamMemoryConfig",
                "TeamMemoryManager",
                "TeamMemoryManagerParams",
                "TeamRole",
                "TeamScenario",
                "resolve_embedding_config"
        ));
    }

    @Test
    void implementedConfigExportReferencesTranslatedType() {
        assertThat(MemoryPackage.TEAM_MEMORY_CONFIG).isSameAs(TeamMemoryConfig.class);
    }
}
