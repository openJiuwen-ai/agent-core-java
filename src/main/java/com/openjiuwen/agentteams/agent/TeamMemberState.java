/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.spawn.SpawnContext;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;

import java.util.Optional;

/**
 * Manages a TeamMember's state — status transitions with event publishing.
 *
 * <p>Mirrors Python TeamMember (agent/member.py): manages member status
 * query, status change, and event publishing via messager.</p>
 *
 * @since 2026/7/9
 */
public class TeamMemberState {

    private final String memberName;
    private final String teamName;
    private final TeamDatabase db;
    private final Messager messager;
    private final String teamSessionId;

    /**
     * Construct a TeamMemberState bound to a specific member and team.
     *
     * @param memberName the member name this state tracks
     * @param teamName the team name the member belongs to
     * @param db the team database used to read and write member status
     * @param messager the messager used to publish status-change events; may be {@code null}
     */
    public TeamMemberState(String memberName, String teamName, TeamDatabase db, Messager messager) {
        this(memberName, teamName, db, messager, SpawnContext.getSessionId());
    }

    /**
     * Construct a TeamMemberState bound to a pinned team-level session id.
     *
     * @param memberName the member name this state tracks
     * @param teamName the team name the member belongs to
     * @param db the team database used to read and write member status
     * @param messager the messager used to publish status-change events; may be {@code null}
     * @param teamSessionId team-level session id pinned at construction time
     * @since 0.1.13
     */
    public TeamMemberState(String memberName, String teamName, TeamDatabase db, Messager messager,
                           String teamSessionId) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.db = db;
        this.messager = messager;
        this.teamSessionId = teamSessionId != null ? teamSessionId : "";
    }

    /**
     * Read the member's current persisted status from the database.
     *
     * @return an {@link Optional} containing the current {@link MemberStatus},
     *     or {@link Optional#empty()} when the record is missing or the value is unparseable
     */
    public Optional<MemberStatus> status() {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || record.getStatus() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MemberStatus.valueOf(record.getStatus()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
