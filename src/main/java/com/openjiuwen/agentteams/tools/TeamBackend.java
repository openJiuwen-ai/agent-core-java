/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.agent.Allocation;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.status.ExecutionStatus;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.database.DatabaseConfig;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.agentteams.tools.database.TeamRecord;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Public class TeamBackend used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TeamBackend {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String teamName;
    private final String memberName;
    private final boolean isLeader;
    private final Messager messager;
    private final String displayName;
    private final String description;
    private final long created;
    private TeamDatabase db;
    private TeamMessageManager messageManager;
    private TeamTaskManager taskManager;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<TeamMember> members = new ArrayList<>();
    private BiConsumer<String, String> onAutoLaunch;

    /**
     * TeamBackend.
     * 
     * @param teamName teamName
     * @param memberName memberName
     * @param isLeader isLeader
     * @param messager messager
     * @since 0.1.7
     */
    public TeamBackend(String teamName, String memberName, boolean isLeader, Messager messager) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.isLeader = isLeader;
        this.messager = messager;
        this.displayName = teamName;
        this.description = "";
        this.created = System.currentTimeMillis();
        this.db = new TeamDatabase(DatabaseConfig.builder().build());
        this.db.initialize();
        Loggers.TOOL.info("TeamBackend created: db={} team={} member={}",
                Integer.toHexString(System.identityHashCode(this.db)), teamName, memberName);
        this.db.team.createTeam(teamName, teamName, memberName, description, null);
        this.db.member.createMember(memberName, teamName, memberName, "{}", MemberStatus.READY.value(), description,
                null, "build", null, null);
        this.messageManager = new TeamMessageManager(teamName, memberName, db, messager, this::humanAgentNames);
        this.taskManager = new TeamTaskManager(teamName, memberName, db, messager);
        this.members.add(TeamMember.builder().memberName(memberName).displayName(memberName)
                .role(isLeader ? TeamRole.LEADER : TeamRole.MEMBER).status(MemberStatus.READY).build());
    }

    /**
     * Share only the database reference with another TeamBackend.
     * Unlike the previous shareState(), this does NOT share messageManager,
     * members, or taskManager — each member keeps its own per-member identity
     * for correct self-message filtering and ownership checks. Matches Python's
     * design where TeamBackend is created independently per member with only
     * the DB handle shared.
     * 
     * @param other other
     * @since 0.1.7
     */
    public void shareDb(TeamBackend other) {
        this.db = other.db;
        // Re-create taskManager with shared DB but member's own memberName.
        this.taskManager = new TeamTaskManager(this.teamName, this.memberName, this.db, this.messager);
        Loggers.TOOL.info("shareDb: db={} memberName={} taskManager={}",
                Integer.toHexString(System.identityHashCode(this.db)), this.memberName,
                Integer.toHexString(System.identityHashCode(this.taskManager)));
    }

    /**
     * spawnMember.
     * 
     * @param memberName memberName
     * @param displayName displayName
     * @param agentCard agentCard
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> spawnMember(String memberName, String displayName, AgentCard agentCard) {
        return spawnMember(memberName, displayName, agentCard, TeamRole.MEMBER);
    }

    /**
     * spawnMember.
     * 
     * @param memberName memberName
     * @param displayName displayName
     * @param agentCard agentCard
     * @param role role
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> spawnMember(String memberName, String displayName, AgentCard agentCard,
            TeamRole role) {
        return spawnMember(memberName, displayName, agentCard, role, null, null);
    }

    /**
     * spawnMember.
     * 
     * @param memberName memberName
     * @param displayName displayName
     * @param agentCard agentCard
     * @param role role
     * @param prompt prompt
     * @param allocation allocation
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> spawnMember(String memberName, String displayName, AgentCard agentCard,
            TeamRole role, String prompt, Allocation allocation) {
        members.removeIf(member -> memberName.equals(member.getMemberName()));
        members.add(TeamMember.builder().memberName(memberName).displayName(displayName)
                .description(agentCard != null ? agentCard.getDescription() : "").prompt(prompt)
                .role(role != null ? role : TeamRole.MEMBER).status(MemberStatus.UNSTARTED).build());
        db.member.createMember(memberName, teamName, displayName, agentCard != null ? agentCard.toString() : "{}",
                MemberStatus.UNSTARTED.value(), agentCard != null ? agentCard.getDescription() : "", null, "build",
                prompt, modelRefJson(allocation));
        return CompletableFuture.completedFuture(true);
    }

    /**
     * syncMembers.
     * 
     * @param memberSpecs memberSpecs
     * @since 0.1.7
     */
    public void syncMembers(List<TeamMemberSpec> memberSpecs) {
        if (memberSpecs == null || memberSpecs.isEmpty()) {
            return;
        }
        Set<String> rosterNames = new LinkedHashSet<>();
        rosterNames.add(memberName);
        for (TeamMemberSpec spec : memberSpecs) {
            if (spec == null || spec.getName() == null || spec.getName().isBlank()) {
                continue;
            }
            rosterNames.add(spec.getName());
            TeamRole role = spec.getRole() != null ? spec.getRole() : TeamRole.MEMBER;
            TeamMember existing = members.stream().filter(member -> spec.getName().equals(member.getMemberName()))
                    .findFirst().orElse(null);
            members.removeIf(member -> spec.getName().equals(member.getMemberName()));
            members.add(TeamMember.builder().memberName(spec.getName()).displayName(spec.getName())
                    .description(spec.getDescription()).role(role)
                    .status(existing != null ? existing.getStatus() : defaultStatusFor(role)).build());
            db.member.createMember(spec.getName(), teamName, spec.getName(), "{}",
                    (existing != null ? existing.getStatus() : defaultStatusFor(role)).value(), spec.getDescription(),
                    null, "build", null, null);
        }
        members.removeIf(member -> !rosterNames.contains(member.getMemberName()));
    }

    /**
     * listMembers.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TeamMember> listMembers() {
        return members.stream().filter(member -> !member.getMemberName().equals(memberName)).toList();
    }

    /**
     * setOnAutoLaunch.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    public void setOnAutoLaunch(BiConsumer<String, String> handler) {
        this.onAutoLaunch = handler;
    }

    /**
     * launchMemberIfUnstarted.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public boolean launchMemberIfUnstarted(String memberName) {
        return launchMemberIfUnstarted(memberName, null);
    }

    /**
     * launchMemberIfUnstarted.
     * 
     * @param memberName memberName
     * @param initialMessage initialMessage
     * @return the result
     * @since 0.1.7
     */
    public boolean launchMemberIfUnstarted(String memberName, String initialMessage) {
        if (onAutoLaunch == null) {
            return false;
        }
        TeamMember member = getMember(memberName);
        if (member == null || member.getStatus() != MemberStatus.UNSTARTED) {
            Loggers.TOOL.info("launchMemberIfUnstarted: member={} not found or not UNSTARTED (status={})", memberName,
                    member != null ? member.getStatus() : "null");
            return false;
        }
        try {
            Loggers.TOOL.info("launchMemberIfUnstarted: launching member={}", memberName);
            // Mark member as BUSY before spawning to prevent duplicate launches
            // when the leader sends multiple broadcasts in quick succession.
            forceUpdateMemberStatus(memberName, MemberStatus.READY);
            onAutoLaunch.accept(member.getMemberName(), initialMessage);
            return true;
        } catch (Exception e) {
            Loggers.TOOL.error("launchMemberIfUnstarted: failed to launch member={}", memberName, e);
            return false;
        }
    }

    /**
     * launchUnstartedMembers.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int launchUnstartedMembers() {
        return launchUnstartedMembers(null);
    }

    /**
     * launchUnstartedMembers.
     * 
     * @param initialMessage initialMessage
     * @return the result
     * @since 0.1.7
     */
    public int launchUnstartedMembers(String initialMessage) {
        if (onAutoLaunch == null) {
            Loggers.TOOL.warn("launchUnstartedMembers: onAutoLaunch is null, cannot launch");
            return 0;
        }
        List<TeamMember> unstarted =
            members.stream().filter(member -> member.getStatus() == MemberStatus.UNSTARTED).toList();
        Loggers.TOOL.info("launchUnstartedMembers: found {} UNSTARTED member(s) out of {} total", unstarted.size(),
                members.size());
        for (TeamMember member : unstarted) {
            try {
                Loggers.TOOL.info("launchUnstartedMembers: launching member={} status={}", member.getMemberName(),
                        member.getStatus());
                forceUpdateMemberStatus(member.getMemberName(), MemberStatus.READY);
                onAutoLaunch.accept(member.getMemberName(), initialMessage);
            } catch (Exception e) {
                Loggers.TOOL.error("launchUnstartedMembers: failed to launch member={}", member.getMemberName(), e);
            }
        }
        return unstarted.size();
    }

    /**
     * getTeamInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamRecord getTeamInfo() {
        return db.team.getTeam(teamName);
    }

    /**
     * getTeamUpdatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long getTeamUpdatedAt() {
        return db.team.getTeamUpdatedAt(teamName);
    }

    /**
     * getMembersMaxUpdatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long getMembersMaxUpdatedAt() {
        return db.member.getMembersMaxUpdatedAt(teamName);
    }

    /**
     * hasMember.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public boolean hasMember(String memberName) {
        return members.stream().anyMatch(member -> member.getMemberName().equals(memberName));
    }

    /**
     * Resolve a name to member_name. First tries exact member_name match,
     * then falls back to display_name lookup.
     * Returns the member_name if found, null otherwise.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public String resolveMemberName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // Try exact member_name match
        if (hasMember(name)) {
            return name;
        }
        // Try display_name match
        return members.stream().filter(member -> name.equals(member.getDisplayName())).map(TeamMember::getMemberName)
                .findFirst().orElse(null);
    }

    /**
     * isHumanAgent.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public boolean isHumanAgent(String memberName) {
        return members.stream().anyMatch(
                member -> member.getMemberName().equals(memberName) && member.getRole() == TeamRole.HUMAN_AGENT);
    }

    /**
     * getMember.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public TeamMember getMember(String memberName) {
        return members.stream().filter(member -> member.getMemberName().equals(memberName)).findFirst().orElse(null);
    }

    /**
     * updateMemberStatus.
     * 
     * @param memberName memberName
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public boolean updateMemberStatus(String memberName, MemberStatus status) {
        return updateMemberStatus(memberName, status, false);
    }

    /**
     * forceUpdateMemberStatus.
     * 
     * @param memberName memberName
     * @param status status
     * @return the result
     * @since 0.1.7
     */
    public boolean forceUpdateMemberStatus(String memberName, MemberStatus status) {
        return updateMemberStatus(memberName, status, true);
    }

    /**
     * updateMemberStatus.
     * 
     * @param memberName memberName
     * @param status status
     * @param isForceEnabled isForceEnabled
     * @return the result
     * @since 0.1.7
     */
    private boolean updateMemberStatus(String memberName, MemberStatus status, boolean isForceEnabled) {
        TeamMember member = getMember(memberName);
        if (member == null || status == null) {
            return false;
        }
        MemberRecord record = db.member.getMember(memberName, teamName);
        String oldStatus = record != null ? record.getStatus() : member.getStatus().value();
        if (status.value().equals(oldStatus)) {
            return true;
        }
        MemberStatus currentStatus = MemberStatus.fromValue(oldStatus);
        if (!isForceEnabled && !currentStatus.canTransitionTo(status)) {
            return false;
        }
        if (!db.member.updateMemberStatus(memberName, teamName, status.value())) {
            return false;
        }
        member.setStatus(status);
        publishTeamEvent("member_status_changed", Map.of("team_name", teamName, "member_name", memberName, "old_status",
                oldStatus, "new_status", status.value())).join();
        return true;
    }

    /**
     * updateMemberExecutionStatus.
     * 
     * @param memberName memberName
     * @param executionStatus executionStatus
     * @return the result
     * @since 0.1.7
     */
    public boolean updateMemberExecutionStatus(String memberName, String executionStatus) {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null || executionStatus == null) {
            return false;
        }
        String oldStatus = record.getExecutionStatus();
        if (executionStatus.equals(oldStatus)) {
            return true;
        }
        ExecutionStatus currentStatus = ExecutionStatus.fromValue(oldStatus);
        ExecutionStatus nextStatus = ExecutionStatus.fromValue(executionStatus);
        if (!currentStatus.canTransitionTo(nextStatus)) {
            return false;
        }
        if (!db.member.updateMemberExecutionStatus(memberName, teamName, executionStatus)) {
            return false;
        }
        publishTeamEvent("member_execution_changed", Map.of("team_name", teamName, "member_name", memberName,
                "old_status", oldStatus != null ? oldStatus : "", "new_status", executionStatus)).join();
        return true;
    }

    /**
     * approvePlan.
     * 
     * @param memberName memberName
     * @param isApproved isApproved
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> approvePlan(String memberName, boolean isApproved) {
        return approvePlan(memberName, isApproved, null);
    }

    /**
     * approvePlan.
     * 
     * @param memberName memberName
     * @param isApproved isApproved
     * @param feedback feedback
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> approvePlan(String memberName, boolean isApproved, String feedback) {
        if (db.member.getMember(memberName, teamName) == null) {
            return CompletableFuture.completedFuture(false);
        }
        int approvedCount = 0;
        if (isApproved) {
            for (TeamTask task : taskManager.getTasksByAssignee(memberName, "claimed")) {
                if (taskManager.approvePlan(task.getTaskId()).join()) {
                    approvedCount++;
                }
            }
        }
        String content;
        if (isApproved) {
            content = "Your plan has been APPROVED. " + approvedCount + " task(s) are now approved for completion."
                    + (feedback != null && !feedback.isBlank() ? "Feedback: " + feedback : "");
        } else {
            content = "Your plan has been REJECTED. Please revise and resubmit. Feedback: "
                    + (feedback != null && !feedback.isBlank() ? feedback : "No specific feedback provided.");
        }
        return messageManager.sendMessage(content, memberName).thenCompose(messageId -> {
            if (messageId == null || messageId.isBlank()) {
                return CompletableFuture.completedFuture(false);
            }
            return publishTeamEvent("plan_approval",
                    Map.of("team_name", teamName, "member_name", memberName, "approved", isApproved))
                    .thenApply(ignored -> true);
        });
    }

    /**
     * approveTool.
     * 
     * @param memberName memberName
     * @param toolCallId toolCallId
     * @param isApproved isApproved
     * @param feedback feedback
     * @param shouldAutoConfirm shouldAutoConfirm
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> approveTool(String memberName, String toolCallId, boolean isApproved,
            String feedback, boolean shouldAutoConfirm) {
        if (db.member.getMember(memberName, teamName) == null) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("team_name", teamName);
        payload.put("member_name", memberName);
        payload.put("tool_call_id", toolCallId);
        payload.put("approved", isApproved);
        payload.put("feedback", feedback != null ? feedback : "");
        payload.put("auto_confirm", shouldAutoConfirm);
        return publishTeamEvent("tool_approval_result", payload).thenApply(ignored -> true);
    }

    /**
     * shutdownMember.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> shutdownMember(String memberName) {
        return shutdownMember(memberName, false);
    }

    /**
     * shutdownMember.
     * 
     * @param memberName memberName
     * @param isForceEnabled isForceEnabled
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> shutdownMember(String memberName, boolean isForceEnabled) {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null) {
            return CompletableFuture.completedFuture(false);
        }
        MemberStatus current = MemberStatus.fromValue(record.getStatus());
        if (current == MemberStatus.SHUTDOWN || current == MemberStatus.SHUTDOWN_REQUESTED) {
            return CompletableFuture.completedFuture(true);
        }
        if (current == MemberStatus.UNSTARTED) {
            return CompletableFuture.completedFuture(false);
        }
        if (!updateMemberStatus(memberName, MemberStatus.SHUTDOWN_REQUESTED)) {
            return CompletableFuture.completedFuture(false);
        }
        return messageManager.sendMessage("Shutdown requested by team leader.", memberName)
                .thenCompose(ignored -> publishTeamEvent("member_shutdown",
                        Map.of("team_name", teamName, "member_name", memberName, "isForceEnabled", isForceEnabled)))
                .thenApply(ignored -> true);
    }

    /**
     * cancelMember.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> cancelMember(String memberName) {
        MemberRecord record = db.member.getMember(memberName, teamName);
        if (record == null) {
            return CompletableFuture.completedFuture(false);
        }
        MemberStatus current = MemberStatus.fromValue(record.getStatus());
        if (current != MemberStatus.BUSY) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Void> resetChain = CompletableFuture.completedFuture(null);
        for (TeamTask task : taskManager.getTasksByAssignee(memberName, "claimed")) {
            resetChain = resetChain.thenCompose(ignored -> taskManager.reset(task.getTaskId()).thenApply(done -> null));
        }
        return resetChain
                .thenCompose(ignored -> messageManager.sendMessage("Cancel requested by team leader.", memberName))
                .thenCompose(ignored -> publishTeamEvent("member_canceled",
                        Map.of("team_name", teamName, "member_name", memberName)))
                .thenApply(ignored -> true);
    }

    /**
     * cleanTeam.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CompletableFuture<Boolean> cleanTeam() {
        for (MemberRecord record : db.member.getTeamMembers(teamName)) {
            if (memberName.equals(record.getMemberName())) {
                continue;
            }
            if (!MemberStatus.SHUTDOWN.value().equals(record.getStatus())) {
                return CompletableFuture.completedFuture(false);
            }
        }
        if (!db.team.deleteTeam(teamName)) {
            return CompletableFuture.completedFuture(false);
        }
        members.removeIf(member -> !memberName.equals(member.getMemberName()));
        return publishTeamEvent("team_cleaned", Map.of("team_name", teamName)).thenApply(ignored -> true);
    }

    /**
     * humanAgentNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> humanAgentNames() {
        Set<String> names = new LinkedHashSet<>();
        for (TeamMember member : members) {
            if (member.getRole() == TeamRole.HUMAN_AGENT) {
                names.add(member.getMemberName());
            }
        }
        return Set.copyOf(names);
    }

    /**
     * hittEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hittEnabled() {
        return !humanAgentNames().isEmpty();
    }

    /**
     * getMessageManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamMessageManager getMessageManager() {
        return messageManager;
    }

    /**
     * getTaskManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamTaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * getDb.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TeamDatabase getDb() {
        return db;
    }

    /**
     * getMessager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Messager getMessager() {
        return messager;
    }

    /**
     * getTeamName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * getMemberName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMemberName() {
        return memberName;
    }

    /**
     * isLeader.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isLeader() {
        return isLeader;
    }

    /**
     * getDisplayName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * getDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getDescription() {
        return description;
    }

    /**
     * getCreated.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long getCreated() {
        return created;
    }

    /**
     * defaultStatusFor.
     * 
     * @param role role
     * @return the result
     * @since 0.1.7
     */
    private MemberStatus defaultStatusFor(TeamRole role) {
        return role == TeamRole.LEADER ? MemberStatus.READY : MemberStatus.UNSTARTED;
    }

    /**
     * modelRefJson.
     * 
     * @param allocation allocation
     * @return the result
     * @since 0.1.7
     */
    private static String modelRefJson(Allocation allocation) {
        if (allocation == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(allocation.toDbRef());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize model allocation reference", e);
        }
    }

    /**
     * publishTeamEvent.
     * 
     * @param eventType eventType
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    private CompletableFuture<Void> publishTeamEvent(String eventType, Map<String, Object> payload) {
        return messager.publish("team:" + teamName,
                EventMessage.builder().eventType(eventType).payload(payload).build());
    }
}
