/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.DeepAgent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal runtime binding for a team member's DeepAgent and Session.
 *
 * <p>Mirrors Python's in-process/process member runtime intent in
 * {@code openjiuwen.agent_teams.spawn.inprocess_spawn} and
 * {@code openjiuwen.agent_teams.agent.team_agent}.
 */
public class TeamMemberRuntime {

    private final TeamMember member;
    private final DeepAgent agent;
    private final Session session;

    public TeamMemberRuntime(TeamMember member, DeepAgent agent, Session session) {
        this.member = member;
        this.agent = agent;
        this.session = session;
    }

    public TeamMember getMember() {
        return member;
    }

    public DeepAgent getAgent() {
        return agent;
    }

    public Session getSession() {
        return session;
    }

    public Object invoke(Object content) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", content != null ? String.valueOf(content) : "");
        inputs.put("team_name", member.getTeamName());
        inputs.put("member_name", member.getMemberName());
        return agent.invoke(inputs, session);
    }
}
