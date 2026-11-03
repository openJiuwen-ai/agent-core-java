/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class TeamRuntimeManagerTest {
    private final TeamRuntimeManager manager = new TeamRuntimeManager();

    @AfterEach
    void cleanup() {
        manager.stopAll();
        InProcessMessager.cleanupInprocessBus();
        TeamBackend.resetSharedDbCache();
    }

    @Test
    void persistentTeamShouldResumeByNameOnSameSession() {
        TeamAgentSpec spec = teamSpec("persistent-runner-team", "persistent");

        TeamRuntimeManager.Activation created = manager.activate(spec, "session-1");

        assertThat(created.action()).isEqualTo(TeamRuntimeManager.ActivationAction.CREATE);
        assertThat(manager.getState(spec.getName()))
                .contains(TeamRuntimeManager.RuntimeState.ACTIVE);
        assertThatThrownBy(() -> manager.activate(spec.getName(), "session-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");

        manager.finalizeRound(created);

        assertThat(manager.getState(spec.getName()))
                .contains(TeamRuntimeManager.RuntimeState.PAUSED);
        assertThat(created.agent().getContext().getLifecycle()).isEqualTo(TeamLifecycle.PAUSED);

        TeamRuntimeManager.Activation resumed = manager.activate(spec.getName(), "session-1");

        assertThat(resumed.action()).isEqualTo(TeamRuntimeManager.ActivationAction.RESUME_FROM_PAUSE);
        assertThat(resumed.agent()).isSameAs(created.agent());

        manager.finalizeRound(resumed);
        assertThat(manager.destroyTeam(spec.getName(), true)).isTrue();
        assertThat(manager.getState(spec.getName())).isEmpty();
    }

    @Test
    void temporaryTeamShouldBeRemovedAfterRound() {
        TeamAgentSpec spec = teamSpec("temporary-runner-team", "temporary");
        TeamRuntimeManager.Activation activation = manager.activate(spec, "session-2");

        manager.finalizeRound(activation);

        assertThat(manager.getState(spec.getName())).isEmpty();
        assertThat(activation.agent().getContext().getLifecycle()).isEqualTo(TeamLifecycle.COMPLETED);
    }

    @Test
    void suppliedSpecShouldRebuildPausedTeamForNewSession() {
        TeamAgentSpec spec = teamSpec("session-switch-team", "persistent");
        TeamRuntimeManager.Activation first = manager.activate(spec, "session-old");
        manager.finalizeRound(first);

        TeamRuntimeManager.Activation replacement = manager.activate(spec, "session-new");

        assertThat(replacement.action()).isEqualTo(TeamRuntimeManager.ActivationAction.REBUILD_FOR_SESSION);
        assertThat(replacement.agent()).isNotSameAs(first.agent());
        assertThat(replacement.sessionId()).isEqualTo("session-new");
    }

    @Test
    void unknownTeamNameShouldBeRejected() {
        assertThatThrownBy(() -> manager.activate("missing-team", "session-3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered");
    }

    private static TeamAgentSpec teamSpec(String name, String lifecycle) {
        return TeamAgentSpec.builder()
                .name(name)
                .lifecycle(lifecycle)
                .spawnMode("inprocess")
                .transport("inprocess")
                .storage("memory")
                .members(List.of(TeamMemberSpec.builder()
                        .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER)
                        .build()))
                .build();
    }
}
