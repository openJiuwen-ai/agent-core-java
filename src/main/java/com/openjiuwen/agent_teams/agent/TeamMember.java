/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.MemberExecutionChangedEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Manages a team member's persisted status and status-change events.
 *
 * <p>Mirrors Python's {@code TeamMember} in
 * {@code openjiuwen/agent_teams/agent/member.py}.</p>
 */
public class TeamMember {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;

    private final String memberName;
    private final String teamName;
    private final String displayName;
    private final AgentCard agentCard;
    private final MemberStore db;
    private final Messager messager;
    private final String prompt;
    private final String desc;

    public TeamMember(
            String memberName,
            String teamName,
            AgentCard agentCard,
            MemberStore db,
            Messager messager
    ) {
        this(memberName, teamName, agentCard, db, messager, null, null, null);
    }

    public TeamMember(
            String memberName,
            String teamName,
            AgentCard agentCard,
            MemberStore db,
            Messager messager,
            String displayName,
            String prompt,
            String desc
    ) {
        this.memberName = memberName;
        this.teamName = teamName;
        this.displayName = displayName == null || displayName.isEmpty() ? memberName : displayName;
        this.agentCard = agentCard;
        this.db = db;
        this.messager = messager;
        this.prompt = prompt;
        this.desc = desc;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public MemberStore getDb() {
        return db;
    }

    public Messager getMessager() {
        return messager;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getDesc() {
        return desc;
    }

    public CompletionStage<MemberStatus> status() {
        return db.getMember(memberName, teamName)
                .thenApply(member -> member == null ? null : MemberStatus.fromValue(member.status()));
    }

    public CompletionStage<ExecutionStatus> executionStatus() {
        return db.getMember(memberName, teamName)
                .thenApply(member -> member == null ? null : ExecutionStatus.fromValue(member.executionStatus()));
    }

    public CompletionStage<Boolean> updateStatus(MemberStatus newStatus) {
        return status().thenCompose(oldStatus -> {
            if (oldStatus == null) {
                TEAM_LOGGER.debug(
                        "Member %s not registered yet; skipping status update to %s",
                        memberName,
                        newStatus.value()
                );
                return CompletableFuture.completedFuture(false);
            }
            if (oldStatus == newStatus) {
                return CompletableFuture.completedFuture(true);
            }
            return db.updateMemberStatus(memberName, teamName, newStatus.value())
                    .thenCompose(success -> handleStatusUpdateResult(oldStatus, newStatus, success));
        });
    }

    public CompletionStage<Boolean> updateExecutionStatus(ExecutionStatus newStatus) {
        return executionStatus().thenCompose(oldStatus -> {
            if (oldStatus == null) {
                TEAM_LOGGER.debug(
                        "Member %s not registered yet; skipping execution status update to %s",
                        memberName,
                        newStatus.value()
                );
                return CompletableFuture.completedFuture(false);
            }
            return db.updateMemberExecutionStatus(memberName, teamName, newStatus.value())
                    .thenCompose(success -> handleExecutionStatusUpdateResult(oldStatus, newStatus, success));
        });
    }

    private CompletionStage<Boolean> handleStatusUpdateResult(
            MemberStatus oldStatus,
            MemberStatus newStatus,
            Boolean success
    ) {
        if (!Boolean.TRUE.equals(success)) {
            TEAM_LOGGER.error("Failed to update member status for %s: %s", memberName, newStatus.value());
            return CompletableFuture.completedFuture(false);
        }

        TEAM_LOGGER.debug("Member %s status: %s -> %s", memberName, oldStatus.value(), newStatus.value());
        return publishStatusChanged(oldStatus, newStatus).thenApply(ignored -> true);
    }

    private CompletionStage<Boolean> handleExecutionStatusUpdateResult(
            ExecutionStatus oldStatus,
            ExecutionStatus newStatus,
            Boolean success
    ) {
        if (!Boolean.TRUE.equals(success)) {
            TEAM_LOGGER.error("Failed to update member execution status for %s: %s", memberName, newStatus.value());
            return CompletableFuture.completedFuture(false);
        }

        TEAM_LOGGER.debug(
                "Member %s execution status: %s -> %s",
                memberName,
                oldStatus.value(),
                newStatus.value()
        );
        return publishExecutionStatusChanged(oldStatus, newStatus).thenApply(ignored -> true);
    }

    private CompletionStage<Void> publishStatusChanged(MemberStatus oldStatus, MemberStatus newStatus) {
        MemberStatusChangedEvent event = new MemberStatusChangedEvent();
        event.setTeamName(teamName);
        event.setMemberName(memberName);
        event.setOldStatus(oldStatus.value());
        event.setNewStatus(newStatus.value());

        return publishEvent(
                EventMessage.fromEvent(event),
                "Failed to publish member status changed event for %s: %s"
        ).thenRun(() -> TEAM_LOGGER.debug(
                "Member status changed event published: %s, %s -> %s",
                memberName,
                oldStatus.value(),
                newStatus.value()
        ));
    }

    private CompletionStage<Void> publishExecutionStatusChanged(
            ExecutionStatus oldStatus,
            ExecutionStatus newStatus
    ) {
        MemberExecutionChangedEvent event = new MemberExecutionChangedEvent();
        event.setTeamName(teamName);
        event.setMemberName(memberName);
        event.setOldStatus(oldStatus.value());
        event.setNewStatus(newStatus.value());

        return publishEvent(
                EventMessage.fromEvent(event),
                "Failed to publish member execution status changed event for %s: %s"
        ).thenRun(() -> TEAM_LOGGER.debug(
                "Member execution status changed event published: %s, %s -> %s",
                memberName,
                oldStatus.value(),
                newStatus.value()
        ));
    }

    private CompletionStage<Void> publishEvent(EventMessage message, String errorTemplate) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            String topicId = TeamTopic.TEAM.build(AgentTeamsContext.getSessionId(), teamName);
            messager.publish(topicId, message).whenComplete((ignored, exception) -> {
                if (exception != null) {
                    TEAM_LOGGER.error(errorTemplate, memberName, exception.getMessage());
                }
                result.complete(null);
            });
        } catch (Exception exception) {
            TEAM_LOGGER.error(errorTemplate, memberName, exception.getMessage());
            result.complete(null);
        }
        return result;
    }

    /**
     * Minimal member persistence boundary used by this translation unit.
     *
     * <p>Mirrors Python's {@code TeamDatabase.member} API subset used in
     * {@code openjiuwen/agent_teams/agent/member.py}.</p>
     */
    public interface MemberStore {
        CompletionStage<MemberSnapshot> getMember(String memberName, String teamName);

        CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status);

        CompletionStage<Boolean> updateMemberExecutionStatus(String memberName, String teamName, String status);
    }

    /**
     * Persisted member status view.
     *
     * <p>Mirrors Python's persisted member row status fields read in
     * {@code openjiuwen/agent_teams/agent/member.py}.</p>
     */
    public record MemberSnapshot(
            String status,
            String executionStatus,
            String memberName,
            String role,
            String desc,
            String prompt,
            String modelRefJson
    ) {
        public MemberSnapshot(String status, String executionStatus) {
            this(status, executionStatus, null, null, null, null, null);
        }
    }
}
