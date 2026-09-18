/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable runtime state shared across TeamAgent operators.
 *
 * <p>Mirrors Python's {@code TeamAgentState} in
 * {@code openjiuwen/agent_teams/agent/state.py}.</p>
 */
public class TeamAgentState implements SessionManager.TeamAgentStateView {

    private SessionManager.AgentTeamSessionView teamSession;
    private TeamMember teamMember;
    private String pendingUserQuery = "";
    private List<Object> eventListeners = emptyListenerList();
    private boolean teamCleaned;

    public static List<Object> emptyListenerList() {
        return new ArrayList<>();
    }

    @Override
    public SessionManager.AgentTeamSessionView getTeamSession() {
        return teamSession;
    }

    @Override
    public void setTeamSession(SessionManager.AgentTeamSessionView teamSession) {
        this.teamSession = teamSession;
    }

    public TeamMember getTeamMember() {
        return teamMember;
    }

    public void setTeamMember(TeamMember teamMember) {
        this.teamMember = teamMember;
    }

    public String getPendingUserQuery() {
        return pendingUserQuery;
    }

    public void setPendingUserQuery(String pendingUserQuery) {
        this.pendingUserQuery = pendingUserQuery == null ? "" : pendingUserQuery;
    }

    public List<Object> getEventListeners() {
        return eventListeners;
    }

    public void setEventListeners(List<?> eventListeners) {
        this.eventListeners = eventListeners == null ? emptyListenerList() : new ArrayList<>(eventListeners);
    }

    public boolean isTeamCleaned() {
        return teamCleaned;
    }

    public void setTeamCleaned(boolean teamCleaned) {
        this.teamCleaned = teamCleaned;
    }
}
