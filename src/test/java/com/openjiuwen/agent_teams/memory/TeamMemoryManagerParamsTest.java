/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.harness.workspace.Workspace;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link TeamMemoryManagerParams} and manager parameter literals.
 *
 * <p>Mirrors Python's {@code manager_params.py} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
class TeamMemoryManagerParamsTest {

    @TempDir
    Path tempDir;

    @Test
    void literalEnumsPreservePythonValues() {
        assertThat(TeamRole.LEADER.getValue()).isEqualTo("leader");
        assertThat(TeamRole.TEAMMATE.toString()).isEqualTo("teammate");
        assertThat(TeamLifecycle.TEMPORARY.getValue()).isEqualTo("temporary");
        assertThat(TeamLifecycle.PERSISTENT.toString()).isEqualTo("persistent");
        assertThat(TeamScenario.GENERAL.getValue()).isEqualTo("general");
        assertThat(TeamScenario.CODING.toString()).isEqualTo("coding");
        assertThat(TeamLanguage.CN.getValue()).isEqualTo("cn");
        assertThat(TeamLanguage.EN.toString()).isEqualTo("en");
        assertThat(PromptMode.PROACTIVE.getValue()).isEqualTo("proactive");
        assertThat(PromptMode.PASSIVE.toString()).isEqualTo("passive");
    }

    @Test
    void literalEnumsRoundTripFromPythonValues() {
        assertThat(TeamRole.fromValue("leader")).isEqualTo(TeamRole.LEADER);
        assertThat(TeamLifecycle.fromValue("persistent")).isEqualTo(TeamLifecycle.PERSISTENT);
        assertThat(TeamScenario.fromValue("coding")).isEqualTo(TeamScenario.CODING);
        assertThat(TeamLanguage.fromValue("en")).isEqualTo(TeamLanguage.EN);
        assertThat(PromptMode.fromValue("passive")).isEqualTo(PromptMode.PASSIVE);

        assertThatThrownBy(() -> TeamRole.fromValue("owner"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown team role");
    }

    @Test
    void builderPreservesDataclassFieldsAndDefaultTimezone() {
        Workspace workspace = new Workspace(tempDir.toString(), "en");

        TeamMemoryManagerParams params = TeamMemoryManagerParams.builder()
                .memberName("alice")
                .teamName("research")
                .role(TeamRole.LEADER)
                .lifecycle(TeamLifecycle.PERSISTENT)
                .scenario(TeamScenario.CODING)
                .workspace(workspace)
                .teamMemoryDir(tempDir.resolve("team-memory").toString())
                .language(TeamLanguage.EN)
                .promptMode(PromptMode.PROACTIVE)
                .enableAutoExtract(true)
                .readOnlySourceWorkspace(tempDir.resolve("source").toString())
                .build();

        assertThat(params.getMemberName()).isEqualTo("alice");
        assertThat(params.getTeamName()).isEqualTo("research");
        assertThat(params.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(params.getLifecycle()).isEqualTo(TeamLifecycle.PERSISTENT);
        assertThat(params.getScenario()).isEqualTo(TeamScenario.CODING);
        assertThat(params.getWorkspace()).isSameAs(workspace);
        assertThat(params.getLanguage()).isEqualTo(TeamLanguage.EN);
        assertThat(params.getPromptMode()).isEqualTo(PromptMode.PROACTIVE);
        assertThat(params.isEnableAutoExtract()).isTrue();
        assertThat(params.getTimezoneOffsetHours()).isEqualTo(8.0d);
    }

    @Test
    void paramsExposeManagerInterfaceValuesAsPythonLiterals() {
        Workspace workspace = new Workspace(tempDir.toString(), "en");
        TeamMemoryManagerParams params = TeamMemoryManagerParams.builder()
                .memberName("bob")
                .teamName("ops")
                .role(TeamRole.TEAMMATE)
                .lifecycle(TeamLifecycle.TEMPORARY)
                .scenario(TeamScenario.GENERAL)
                .workspace(workspace)
                .language(TeamLanguage.CN)
                .promptMode(PromptMode.PASSIVE)
                .timezoneOffsetHours(9.0d)
                .build();

        TeamMemoryManager.Parameters managerParams = params;

        assertThat(managerParams.memberName()).isEqualTo("bob");
        assertThat(managerParams.teamName()).isEqualTo("ops");
        assertThat(managerParams.role()).isEqualTo("teammate");
        assertThat(managerParams.lifecycle()).isEqualTo("temporary");
        assertThat(managerParams.scenario()).isEqualTo("general");
        assertThat(managerParams.workspace()).isSameAs(workspace);
        assertThat(managerParams.language()).isEqualTo("cn");
        assertThat(managerParams.promptMode()).isEqualTo("passive");
        assertThat(managerParams.timezoneOffsetHours()).isEqualTo(9.0d);
    }

    @Test
    void paramsCanConstructTeamMemoryManager() {
        Workspace workspace = new Workspace(tempDir.toString(), "en");
        TeamMemoryManagerParams params = TeamMemoryManagerParams.builder()
                .memberName("carol")
                .teamName("team")
                .role(TeamRole.TEAMMATE)
                .lifecycle(TeamLifecycle.TEMPORARY)
                .scenario(TeamScenario.GENERAL)
                .workspace(workspace)
                .language(TeamLanguage.EN)
                .promptMode(PromptMode.PASSIVE)
                .build();

        TeamMemoryManager manager = new TeamMemoryManager(params);

        assertThat(manager.initToolkit().toCompletableFuture().join()).isTrue();
        assertThat(manager.getWorkspace()).isSameAs(workspace);
    }
}
