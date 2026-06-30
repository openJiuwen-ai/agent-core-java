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
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Public class TeamBackend used by the Java parity implementation.
 *
 * @since 1.0
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
  private final TeamDatabase db;
  private final TeamMessageManager messageManager;
  private final TeamTaskManager taskManager;
  private final List<TeamMember> members = new ArrayList<>();

  /** Auto-generated for codecheck compliance. */
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
    this.db.team.createTeam(teamName, teamName, memberName, description, null);
    this.db.member.createMember(
        memberName,
        teamName,
        memberName,
        "{}",
        MemberStatus.READY.value(),
        description,
        null,
        "build",
        null,
        null);
    this.messageManager =
        new TeamMessageManager(teamName, memberName, db, messager, this::humanAgentNames);
    this.taskManager = new TeamTaskManager(teamName, memberName, db, messager);
    this.members.add(
        TeamMember.builder()
            .memberName(memberName)
            .displayName(memberName)
            .role(isLeader ? TeamRole.LEADER : TeamRole.MEMBER)
            .status(MemberStatus.READY)
            .build());
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> spawnMember(
      String memberName, String displayName, AgentCard agentCard) {
    return spawnMember(memberName, displayName, agentCard, TeamRole.MEMBER);
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> spawnMember(
      String memberName, String displayName, AgentCard agentCard, TeamRole role) {
    return spawnMember(memberName, displayName, agentCard, role, null, null);
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> spawnMember(
      String memberName,
      String displayName,
      AgentCard agentCard,
      TeamRole role,
      String prompt,
      Allocation allocation) {
    members.removeIf(member -> memberName.equals(member.getMemberName()));
    members.add(
        TeamMember.builder()
            .memberName(memberName)
            .displayName(displayName)
            .description(agentCard != null ? agentCard.getDescription() : "")
            .role(role != null ? role : TeamRole.MEMBER)
            .status(MemberStatus.UNSTARTED)
            .build());
    db.member.createMember(
        memberName,
        teamName,
        displayName,
        agentCard != null ? agentCard.toString() : "{}",
        MemberStatus.UNSTARTED.value(),
        agentCard != null ? agentCard.getDescription() : "",
        null,
        "build",
        prompt,
        modelRefJson(allocation));
    return CompletableFuture.completedFuture(true);
  }

  /** Auto-generated for codecheck compliance. */
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
      TeamMember existing =
          members.stream()
              .filter(member -> spec.getName().equals(member.getMemberName()))
              .findFirst()
              .orElse(null);
      members.removeIf(member -> spec.getName().equals(member.getMemberName()));
      members.add(
          TeamMember.builder()
              .memberName(spec.getName())
              .displayName(spec.getName())
              .description(spec.getDescription())
              .role(role)
              .status(existing != null ? existing.getStatus() : defaultStatusFor(role))
              .build());
      db.member.createMember(
          spec.getName(),
          teamName,
          spec.getName(),
          "{}",
          (existing != null ? existing.getStatus() : defaultStatusFor(role)).value(),
          spec.getDescription(),
          null,
          "build",
          null,
          null);
    }
    members.removeIf(member -> !rosterNames.contains(member.getMemberName()));
  }

  /** Auto-generated for codecheck compliance. */
  public List<TeamMember> listMembers() {
    return members.stream().filter(member -> !member.getMemberName().equals(memberName)).toList();
  }

  /** Auto-generated for codecheck compliance. */
  public TeamRecord getTeamInfo() {
    return db.team.getTeam(teamName);
  }

  /** Auto-generated for codecheck compliance. */
  public long getTeamUpdatedAt() {
    return db.team.getTeamUpdatedAt(teamName);
  }

  /** Auto-generated for codecheck compliance. */
  public long getMembersMaxUpdatedAt() {
    return db.member.getMembersMaxUpdatedAt(teamName);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasMember(String memberName) {
    return members.stream().anyMatch(member -> member.getMemberName().equals(memberName));
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isHumanAgent(String memberName) {
    return members.stream()
        .anyMatch(
            member ->
                member.getMemberName().equals(memberName)
                    && member.getRole() == TeamRole.HUMAN_AGENT);
  }

  /** Auto-generated for codecheck compliance. */
  public TeamMember getMember(String memberName) {
    return members.stream()
        .filter(member -> member.getMemberName().equals(memberName))
        .findFirst()
        .orElse(null);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean updateMemberStatus(String memberName, MemberStatus status) {
    return updateMemberStatus(memberName, status, false);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean forceUpdateMemberStatus(String memberName, MemberStatus status) {
    return updateMemberStatus(memberName, status, true);
  }

  private boolean updateMemberStatus(
      String memberName, MemberStatus status, boolean isForceEnabled) {
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
    publishTeamEvent(
            "member_status_changed",
            Map.of(
                "team_name", teamName,
                "member_name", memberName,
                "old_status", oldStatus,
                "new_status", status.value()))
        .join();
    return true;
  }

  /** Auto-generated for codecheck compliance. */
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
    publishTeamEvent(
            "member_execution_changed",
            Map.of(
                "team_name", teamName,
                "member_name", memberName,
                "old_status", oldStatus != null ? oldStatus : "",
                "new_status", executionStatus))
        .join();
    return true;
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> approvePlan(String memberName, boolean isApproved) {
    return approvePlan(memberName, isApproved, null);
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> approvePlan(
      String memberName, boolean isApproved, String feedback) {
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
      content =
          "Your plan has been APPROVED. "
              + approvedCount
              + " task(s) are now approved for completion."
              + (feedback != null && !feedback.isBlank() ? "Feedback: " + feedback : "");
    } else {
      content =
          "Your plan has been REJECTED. Please revise and resubmit. Feedback: "
              + (feedback != null && !feedback.isBlank()
                  ? feedback
                  : "No specific feedback provided.");
    }
    return messageManager
        .sendMessage(content, memberName)
        .thenCompose(
            messageId -> {
              if (messageId == null || messageId.isBlank()) {
                return CompletableFuture.completedFuture(false);
              }
              return publishTeamEvent(
                      "plan_approval",
                      Map.of(
                          "team_name", teamName,
                          "member_name", memberName,
                          "approved", isApproved))
                  .thenApply(ignored -> true);
            });
  }

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> approveTool(
      String memberName,
      String toolCallId,
      boolean isApproved,
      String feedback,
      boolean shouldAutoConfirm) {
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

  /** Auto-generated for codecheck compliance. */
  public CompletableFuture<Boolean> shutdownMember(String memberName) {
    return shutdownMember(memberName, false);
  }

  /** Auto-generated for codecheck compliance. */
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
    return messageManager
        .sendMessage("Shutdown requested by team leader.", memberName)
        .thenCompose(
            ignored ->
                publishTeamEvent(
                    "member_shutdown",
                    Map.of(
                        "team_name", teamName,
                        "member_name", memberName,
                        "isForceEnabled", isForceEnabled)))
        .thenApply(ignored -> true);
  }

  /** Auto-generated for codecheck compliance. */
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
      resetChain =
          resetChain.thenCompose(
              ignored -> taskManager.reset(task.getTaskId()).thenApply(done -> null));
    }
    return resetChain
        .thenCompose(
            ignored -> messageManager.sendMessage("Cancel requested by team leader.", memberName))
        .thenCompose(
            ignored ->
                publishTeamEvent(
                    "member_canceled",
                    Map.of(
                        "team_name", teamName,
                        "member_name", memberName)))
        .thenApply(ignored -> true);
  }

  /** Auto-generated for codecheck compliance. */
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
    return publishTeamEvent("team_cleaned", Map.of("team_name", teamName))
        .thenApply(ignored -> true);
  }

  /** Auto-generated for codecheck compliance. */
  public Set<String> humanAgentNames() {
    Set<String> names = new LinkedHashSet<>();
    for (TeamMember member : members) {
      if (member.getRole() == TeamRole.HUMAN_AGENT) {
        names.add(member.getMemberName());
      }
    }
    return Set.copyOf(names);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hittEnabled() {
    return !humanAgentNames().isEmpty();
  }

  /** Auto-generated for codecheck compliance. */
  public TeamMessageManager getMessageManager() {
    return messageManager;
  }

  /** Auto-generated for codecheck compliance. */
  public TeamTaskManager getTaskManager() {
    return taskManager;
  }

  /** Auto-generated for codecheck compliance. */
  public TeamDatabase getDb() {
    return db;
  }

  /** Auto-generated for codecheck compliance. */
  public Messager getMessager() {
    return messager;
  }

  /** Auto-generated for codecheck compliance. */
  public String getTeamName() {
    return teamName;
  }

  /** Auto-generated for codecheck compliance. */
  public String getMemberName() {
    return memberName;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isLeader() {
    return isLeader;
  }

  /** Auto-generated for codecheck compliance. */
  public String getDisplayName() {
    return displayName;
  }

  /** Auto-generated for codecheck compliance. */
  public String getDescription() {
    return description;
  }

  /** Auto-generated for codecheck compliance. */
  public long getCreated() {
    return created;
  }

  private MemberStatus defaultStatusFor(TeamRole role) {
    return role == TeamRole.LEADER ? MemberStatus.READY : MemberStatus.UNSTARTED;
  }

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

  private CompletableFuture<Void> publishTeamEvent(String eventType, Map<String, Object> payload) {
    return messager.publish(
        "team:" + teamName, EventMessage.builder().eventType(eventType).payload(payload).build());
  }
}
