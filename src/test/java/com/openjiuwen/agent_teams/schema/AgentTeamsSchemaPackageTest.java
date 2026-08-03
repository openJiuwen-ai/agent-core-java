/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for the agent-team schema package facade.
 *
 * <p>Mirrors Python's {@code __all__} in
 * {@code openjiuwen/agent_teams/schema/__init__.py}.</p>
 */
class AgentTeamsSchemaPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        List<String> expected = List.of(
                "AudioModelSpec",
                "DeepAgentSpec",
                "LeaderSpec",
                "ProgressiveToolSpec",
                "RailSpec",
                "StorageSpec",
                "SubAgentSpec",
                "SysOperationSpec",
                "TeamAgentSpec",
                "TeamOutputSchema",
                "TransportSpec",
                "VisionModelSpec",
                "WorkspaceSpec",
                "register_rail_type",
                "register_storage",
                "register_transport",
                "TeamLifecycle",
                "TeamMemberSpec",
                "TeamRole",
                "TeamRuntimeContext",
                "TeamSpec"
        );

        assertThat(AgentTeamsSchemaPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_teams/schema/__init__.py");
        assertThat(AgentTeamsSchemaPackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(expected);
        assertThat(AgentTeamsSchemaPackage.all()).isSameAs(AgentTeamsSchemaPackage.EXPORTED_SYMBOLS);
    }

    @Test
    void resolvesExportedJavaTypesAndLeavesRegistryFunctionsUntyped() {
        assertThat(AgentTeamsSchemaPackage.typeFor("AudioModelSpec")).isEqualTo(AudioModelSpec.class);
        assertThat(AgentTeamsSchemaPackage.typeFor("DeepAgentSpec")).isEqualTo(DeepAgentSpec.class);
        assertThat(AgentTeamsSchemaPackage.typeFor("TeamAgentSpec")).isEqualTo(TeamAgentSpec.class);
        assertThat(AgentTeamsSchemaPackage.typeFor("TeamOutputSchema")).isEqualTo(TeamOutputSchema.class);
        assertThat(AgentTeamsSchemaPackage.typeFor("TeamRole")).isEqualTo(TeamRole.class);
        assertThat(AgentTeamsSchemaPackage.typeFor("TeamSpec")).isEqualTo(TeamSpec.class);
        assertThat(AgentTeamsSchemaPackage.typeFor("register_storage")).isNull();
        assertThat(AgentTeamsSchemaPackage.exports("register_transport")).isTrue();
        assertThat(AgentTeamsSchemaPackage.exports("missing")).isFalse();
    }
}
