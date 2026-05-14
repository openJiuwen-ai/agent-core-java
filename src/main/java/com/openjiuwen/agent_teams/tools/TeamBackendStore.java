/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared backend state for one Java team name.
 *
 * <p>Mirrors the shift from per-instance in-memory state toward a shared
 * backend/database surface in Python's {@code TeamBackend} and database layer.</p>
 */
public class TeamBackendStore {

    private final String teamName;
    private final Map<String, TeamMember> members = new LinkedHashMap<>();
    private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();
    private final Map<String, MessageRecord> messages = new LinkedHashMap<>();
    private Messager messager;

    public TeamBackendStore(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }

    public Map<String, TeamMember> getMembers() {
        return members;
    }

    public Map<String, TaskRecord> getTasks() {
        return tasks;
    }

    public Map<String, MessageRecord> getMessages() {
        return messages;
    }

    public Messager getMessager() {
        return messager;
    }

    public void setMessager(Messager messager) {
        this.messager = messager;
    }
}
