/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams;

import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.RuntimeConfig;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamsUtilsTest {

    @Test
    void standaloneInvokeContextBindsSessionUntilCleanup() {
        TeamRuntime runtime = new TeamRuntime(RuntimeConfig.builder().teamId("team-1").build());
        TeamCard card = TeamCard.builder().id("team-1").name("Team").build();

        TeamsUtils.InvokeContext context = TeamsUtils.standaloneInvokeContext(
                runtime,
                card,
                Map.of("conversation_id", "conv-1"),
                null);

        assertThat(context.getSession().getSessionId()).isEqualTo("conv-1");
        assertThat(runtime.getTeamSession("conv-1")).isSameAs(context.getSession());

        context.cleanup();

        assertThat(runtime.getTeamSession("conv-1")).isNull();
    }

    @Test
    void runnerSessionPathDoesNotBindRuntimeSession() {
        TeamRuntime runtime = new TeamRuntime(RuntimeConfig.builder().teamId("team-1").build());
        TeamCard card = TeamCard.builder().id("team-1").name("Team").build();
        Session runnerSession = new AgentTeamSession("runner-1", "team-1");

        TeamsUtils.InvokeContext context = TeamsUtils.standaloneInvokeContext(
                runtime,
                card,
                "hello",
                runnerSession);

        assertThat(context.getSession()).isSameAs(runnerSession);
        assertThat(context.getSessionId()).isEqualTo("runner-1");
        assertThat(runtime.getTeamSession("runner-1")).isNull();

        context.cleanup();

        assertThat(runtime.getTeamSession("runner-1")).isNull();
    }
}
