/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import com.openjiuwen.agent_teams.interaction.BridgeProtocolAdapter;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.BridgeMemberSpec;
import com.openjiuwen.agent_teams.schema.ExternalCliAgentSpec;
import com.openjiuwen.agent_teams.schema.MemberCanceledEvent;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MemberSpawnedEvent;
import com.openjiuwen.agent_teams.schema.TeamCleanedEvent;
import com.openjiuwen.agent_teams.schema.TeamCompletionSnapshot;
import com.openjiuwen.agent_teams.schema.TeamCreatedEvent;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.ToolApprovalResultEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.StatusTransitions;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import com.openjiuwen.agent_teams.tools.database.MemberDao;
import com.openjiuwen.agent_teams.tools.database.TeamDao;
import com.openjiuwen.agent_teams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Runtime backend that manages a team row, members, tasks, and messages.
 *
 * <p>Mirrors Python's {@code TeamBackend} and {@code CapabilityOverrides} in
 * {@code openjiuwen/agent_teams/tools/team.py}.</p>
 */
public class TeamBackend {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TASK_TERMINAL_STATUSES = Set.of(
            TaskStatus.COMPLETED.value(),
            TaskStatus.CANCELLED.value()
    );

    private final String teamName;
    private final String memberName;
    private final boolean leader;
    private final String leaderMemberName;
    private final TeamStore store;
    private final Messager messager;
    private final MemberMode teammateMode;
    private final List<TeamMemberSpec> predefinedMembers;
    private final AgentConfigurator.ModelAllocator modelConfigAllocator;
    private final AgentConfigurator.Allocation leaderAllocation;
    private final boolean specEnableHitt;
    private final boolean specEnableBridge;
    private final Supplier<CompletionStage<Void>> onTeamCleaned;
    private final Supplier<CompletionStage<Void>> onTeamBuilt;
    private final TeamTaskManager taskManager;
    private final TeamMessageManager messageManager;
    private final Map<String, HumanAgentInboundCallback> humanAgentInboundCallbacks = new LinkedHashMap<>();
    private final Map<String, BridgeMemberSpec> bridgeMemberSpecs = new LinkedHashMap<>();
    private final Map<String, BridgeProtocolAdapter> bridgeAdapters = new LinkedHashMap<>();
    private final Map<String, String> externalCliSpecs = new LinkedHashMap<>();
    private final Map<String, ExternalCliAgentSpec> externalCliConfigs = new LinkedHashMap<>();
    private final Set<String> cleanupPaths = new LinkedHashSet<>();

    private boolean enableHitt;
    private boolean enableBridge;

    public TeamBackend(
            String teamName,
            String memberName,
            boolean leader,
            InMemoryTeamDatabase database,
            Messager messager) {
        this(
                teamName,
                memberName,
                leader,
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                false,
                false,
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public TeamBackend(
            String teamName,
            String memberName,
            boolean leader,
            TeamDatabase database,
            Messager messager) {
        this(
                teamName,
                memberName,
                leader,
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                false,
                false,
                List.of(),
                null,
                null,
                null,
                null,
                null
        );
    }

    public TeamBackend(
            String teamName,
            String memberName,
            boolean leader,
            InMemoryTeamDatabase database,
            Messager messager,
            MemberMode teammateMode,
            List<TeamMemberSpec> predefinedMembers,
            AgentConfigurator.ModelAllocator modelConfigAllocator,
            AgentConfigurator.Allocation leaderAllocation,
            boolean enableHitt,
            boolean enableBridge,
            List<ExternalCliAgentSpec> externalCliAgents,
            Supplier<CompletionStage<Void>> onTeamCleaned,
            Supplier<CompletionStage<Void>> onTeamBuilt,
            Path planStorageDir,
            String planId,
            String leaderMemberName) {
        this(
                teamName,
                memberName,
                leader,
                new InMemoryStore(Objects.requireNonNull(database, "database")),
                database,
                null,
                messager,
                teammateMode,
                predefinedMembers,
                modelConfigAllocator,
                leaderAllocation,
                enableHitt,
                enableBridge,
                externalCliAgents,
                onTeamCleaned,
                onTeamBuilt,
                planStorageDir,
                planId,
                leaderMemberName
        );
    }

    public TeamBackend(
            String teamName,
            String memberName,
            boolean leader,
            TeamDatabase database,
            Messager messager,
            MemberMode teammateMode,
            List<TeamMemberSpec> predefinedMembers,
            AgentConfigurator.ModelAllocator modelConfigAllocator,
            AgentConfigurator.Allocation leaderAllocation,
            boolean enableHitt,
            boolean enableBridge,
            List<ExternalCliAgentSpec> externalCliAgents,
            Supplier<CompletionStage<Void>> onTeamCleaned,
            Supplier<CompletionStage<Void>> onTeamBuilt,
            Path planStorageDir,
            String planId,
            String leaderMemberName) {
        Objects.requireNonNull(database, "database").initialize().join();
        this.teamName = emptyToDefault(teamName, "default");
        this.memberName = nullToEmpty(memberName);
        this.leader = leader;
        this.leaderMemberName = firstNonBlank(leaderMemberName, leader ? this.memberName : "");
        this.store = new SqlStore(database);
        this.messager = messager;
        this.teammateMode = teammateMode == null ? MemberMode.BUILD_MODE : teammateMode;
        this.predefinedMembers = predefinedMembers == null ? List.of() : List.copyOf(predefinedMembers);
        this.modelConfigAllocator = modelConfigAllocator;
        this.leaderAllocation = leaderAllocation;
        this.specEnableHitt = enableHitt;
        this.specEnableBridge = enableBridge;
        this.enableHitt = enableHitt;
        this.enableBridge = enableBridge;
        this.onTeamCleaned = onTeamCleaned;
        this.onTeamBuilt = onTeamBuilt;
        this.taskManager = new TeamTaskManager(
                this.teamName,
                this.memberName,
                database,
                messager,
                planStorageDir,
                planId,
                this.leaderMemberName
        );
        this.messageManager = new TeamMessageManager(this.teamName, this.memberName, database, messager);
        seedBridgeAndExternalIndexes(externalCliAgents);
        TEAM_LOGGER.info("AgentTeam manager initialized for %s, member=%s", this.teamName, this.memberName);
    }

    private TeamBackend(
            String teamName,
            String memberName,
            boolean leader,
            TeamStore store,
            InMemoryTeamDatabase inMemoryDatabase,
            TeamDatabase sqlDatabase,
            Messager messager,
            MemberMode teammateMode,
            List<TeamMemberSpec> predefinedMembers,
            AgentConfigurator.ModelAllocator modelConfigAllocator,
            AgentConfigurator.Allocation leaderAllocation,
            boolean enableHitt,
            boolean enableBridge,
            List<ExternalCliAgentSpec> externalCliAgents,
            Supplier<CompletionStage<Void>> onTeamCleaned,
            Supplier<CompletionStage<Void>> onTeamBuilt,
            Path planStorageDir,
            String planId,
            String leaderMemberName) {
        this.teamName = emptyToDefault(teamName, "default");
        this.memberName = nullToEmpty(memberName);
        this.leader = leader;
        this.leaderMemberName = firstNonBlank(leaderMemberName, leader ? this.memberName : "");
        this.store = store;
        this.messager = messager;
        this.teammateMode = teammateMode == null ? MemberMode.BUILD_MODE : teammateMode;
        this.predefinedMembers = predefinedMembers == null ? List.of() : List.copyOf(predefinedMembers);
        this.modelConfigAllocator = modelConfigAllocator;
        this.leaderAllocation = leaderAllocation;
        this.specEnableHitt = enableHitt;
        this.specEnableBridge = enableBridge;
        this.enableHitt = enableHitt;
        this.enableBridge = enableBridge;
        this.onTeamCleaned = onTeamCleaned;
        this.onTeamBuilt = onTeamBuilt;
        if (inMemoryDatabase != null) {
            this.taskManager = new TeamTaskManager(
                    this.teamName,
                    this.memberName,
                    inMemoryDatabase,
                    messager,
                    planStorageDir,
                    planId,
                    this.leaderMemberName
            );
            this.messageManager = new TeamMessageManager(this.teamName, this.memberName, inMemoryDatabase, messager);
        } else {
            this.taskManager = new TeamTaskManager(
                    this.teamName,
                    this.memberName,
                    Objects.requireNonNull(sqlDatabase, "sqlDatabase"),
                    messager,
                    planStorageDir,
                    planId,
                    this.leaderMemberName
            );
            this.messageManager = new TeamMessageManager(this.teamName, this.memberName, sqlDatabase, messager);
        }
        seedBridgeAndExternalIndexes(externalCliAgents);
        TEAM_LOGGER.info("AgentTeam manager initialized for %s, member=%s", this.teamName, this.memberName);
    }

    public String getTeamName() {
        return teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public boolean isLeader() {
        return leader;
    }

    public String getLeaderMemberName() {
        return leaderMemberName;
    }

    public TeamTaskManager getTaskManager() {
        return taskManager;
    }

    public TeamMessageManager getMessageManager() {
        return messageManager;
    }

    public void registerCleanupPath(String path) {
        if (isBlank(path)) {
            return;
        }
        cleanupPaths.add(Path.of(path).toAbsolutePath().normalize().toString());
    }

    public CompletionStage<MemberOpResult> spawnMember(
            String memberName,
            String displayName,
            AgentConfigurator.AgentCard agentCard,
            String desc,
            String prompt,
            MemberStatus status,
            ExecutionStatus executionStatus,
            MemberMode mode,
            AgentConfigurator.Allocation allocation,
            TeamRole role) {
        return supplyStage(() -> {
            if (join(store.getMember(memberName, teamName)).isPresent()) {
                return MemberOpResult.fail("Member " + memberName + " already exists in team " + teamName);
            }
            boolean success = join(store.createMember(
                    memberName,
                    teamName,
                    displayName,
                    serialize(agentCard),
                    enumValue(status, MemberStatus.UNSTARTED),
                    enumValue(role, TeamRole.TEAMMATE),
                    desc,
                    enumValue(executionStatus, ExecutionStatus.IDLE),
                    enumValue(mode, MemberMode.BUILD_MODE),
                    prompt,
                    allocationToJson(allocation)
            ));
            if (!success) {
                return MemberOpResult.fail(
                        "Database rejected create_member for " + memberName + " in team " + teamName
                );
            }
            TEAM_LOGGER.info("Member %s created successfully", memberName);
            return MemberOpResult.success();
        });
    }

    public CompletionStage<List<String>> startup(MemberStartupCallback onCreated) {
        return supplyStage(() -> {
            List<TeamMember> unstarted = join(store.getTeamMembers(teamName, MemberStatus.UNSTARTED.value()));
            List<String> started = new ArrayList<>();
            for (TeamMember member : unstarted) {
                if (join(startupMember(member.getMemberName(), onCreated))) {
                    started.add(member.getMemberName());
                }
            }
            return started;
        });
    }

    public CompletionStage<Boolean> startupMember(String memberName, MemberStartupCallback onCreated) {
        return supplyStage(() -> {
            boolean transitioned = join(store.tryTransitionMemberStatus(
                    memberName,
                    teamName,
                    MemberStatus.UNSTARTED,
                    MemberStatus.STARTING
            ));
            if (!transitioned) {
                return false;
            }
            try {
                join(spawnAndPublish(memberName, onCreated));
                return true;
            } catch (RuntimeException exception) {
                join(store.tryTransitionMemberStatus(memberName, teamName, MemberStatus.STARTING, MemberStatus.UNSTARTED));
                throw exception;
            }
        });
    }

    public CompletionStage<Boolean> approvePlan(String planId) {
        return approvePlan(planId, true, null);
    }

    public CompletionStage<Boolean> approvePlan(String planId, boolean approved, String feedback) {
        return supplyStage(() -> {
            if (isBlank(planId)) {
                TEAM_LOGGER.error("approve_plan requires plan_id");
                return false;
            }
            Map<String, Object> planRecord = taskManager.getPlanRecord(planId);
            if (planRecord == null || planRecord.isEmpty()) {
                TEAM_LOGGER.error("Plan %s not found", planId);
                return false;
            }
            String targetMember = stringValue(planRecord.get("member_name"));
            String taskId = stringValue(planRecord.get("task_id"));
            if (targetMember.isEmpty()) {
                TEAM_LOGGER.error("Plan %s has no member_name", planId);
                return false;
            }
            if (join(store.getMember(targetMember, teamName)).isEmpty()) {
                TEAM_LOGGER.error("Member %s not found in team %s", targetMember, teamName);
                return false;
            }
            TEAM_LOGGER.info(
                    "Approving plan for member %s: approved=%s, task_id=%s, plan_id=%s, feedback=%s",
                    targetMember,
                    approved,
                    taskId,
                    planId,
                    feedback
            );
            var result = join(taskManager.approvePlan(planId, approved, nullToEmpty(feedback), memberName));
            if (!result.ok()) {
                TEAM_LOGGER.error("Failed to approve/reject plan %s: %s", planId, result.reason());
                return false;
            }
            return true;
        });
    }

    public CompletionStage<Boolean> approveTool(
            String memberName,
            String toolCallId,
            boolean approved,
            String feedback,
            boolean autoConfirm) {
        return supplyStage(() -> {
            if (join(store.getMember(memberName, teamName)).isEmpty()) {
                TEAM_LOGGER.error("Member %s not found in team %s", memberName, teamName);
                return false;
            }
            ToolApprovalResultEvent event = new ToolApprovalResultEvent();
            event.setTeamName(teamName);
            event.setMemberName(memberName);
            event.setToolCallId(toolCallId);
            event.setApproved(approved);
            event.setFeedback(nullToEmpty(feedback));
            event.setAutoConfirm(autoConfirm);
            publishTeamEvent(event, "tool approval result event for " + memberName + " / " + toolCallId);
            TEAM_LOGGER.info(
                    "Tool approval event sent to member %s for tool_call_id=%s, approved=%s, auto_confirm=%s",
                    memberName,
                    toolCallId,
                    approved,
                    autoConfirm
            );
            return true;
        });
    }

    public CompletionStage<MemberOpResult> shutdownMember(String memberName) {
        return shutdownMember(memberName, false);
    }

    public CompletionStage<MemberOpResult> shutdownMember(String memberName, boolean force) {
        return supplyStage(() -> {
            Optional<TeamMember> memberData = join(store.getMember(memberName, teamName));
            if (memberData.isEmpty()) {
                return MemberOpResult.fail("Member " + memberName + " not found in team " + teamName);
            }
            MemberStatus currentStatus = MemberStatus.fromValue(memberData.get().getStatus());
            if (currentStatus == MemberStatus.SHUTDOWN || currentStatus == MemberStatus.SHUTDOWN_REQUESTED) {
                return MemberOpResult.success();
            }
            if (!StatusTransitions.isValidTransition(
                    currentStatus,
                    MemberStatus.SHUTDOWN_REQUESTED,
                    StatusTransitions.MEMBER_TRANSITIONS
            )) {
                return MemberOpResult.fail(
                        "Member " + memberName + " cannot shut down from status '" + currentStatus.value() + "'"
                );
            }
            boolean success = join(store.updateMemberStatus(memberName, teamName, MemberStatus.SHUTDOWN_REQUESTED.value()));
            if (!success) {
                return MemberOpResult.fail("Database rejected status update for member " + memberName);
            }
            String messageId = join(messageManager.sendMessage(
                    AgentTeamI18n.t("team.shutdown_request_content"),
                    memberName
            ));
            if (isBlank(messageId)) {
                TEAM_LOGGER.warning("Failed to send shutdown request message to member %s", memberName);
            }
            MemberShutdownEvent event = new MemberShutdownEvent();
            event.setTeamName(teamName);
            event.setMemberName(memberName);
            event.setForce(force);
            publishTeamEvent(event, "member shutdown event for " + memberName);
            TEAM_LOGGER.info("Shutdown request sent to member %s", memberName);
            return MemberOpResult.success();
        });
    }

    public CompletionStage<Boolean> cancelMember(String memberName) {
        return supplyStage(() -> {
            Optional<TeamMember> memberData = join(store.getMember(memberName, teamName));
            if (memberData.isEmpty()) {
                TEAM_LOGGER.error("Member %s not found in team %s", memberName, teamName);
                return false;
            }
            MemberStatus currentStatus = MemberStatus.fromValue(memberData.get().getStatus());
            if (currentStatus != MemberStatus.BUSY) {
                TEAM_LOGGER.info(
                        "Member %s is not busy (status: %s), no need to cancel execution",
                        memberName,
                        currentStatus.value()
                );
                return true;
            }
            List<TeamTask> claimedTasks = join(taskManager.getTasksByAssignee(memberName, TaskStatus.CLAIMED.value()));
            int resetCount = 0;
            for (TeamTask task : claimedTasks) {
                if (join(taskManager.reset(task.getTaskId())).ok()) {
                    resetCount++;
                }
            }
            if (resetCount > 0) {
                TEAM_LOGGER.info("Reset %d tasks from member %s", resetCount, memberName);
            }
            String messageId = join(messageManager.sendMessage(AgentTeamI18n.t("team.cancel_request_content"), memberName));
            if (isBlank(messageId)) {
                TEAM_LOGGER.error("Failed to send cancel request message to member %s", memberName);
                return false;
            }
            MemberCanceledEvent event = new MemberCanceledEvent();
            event.setTeamName(teamName);
            event.setMemberName(memberName);
            publishTeamEvent(event, "member canceled event for " + memberName);
            TEAM_LOGGER.info("Cancel request sent to member %s", memberName);
            return true;
        });
    }

    public CompletionStage<Boolean> cleanTeam() {
        return supplyStage(() -> {
            List<TeamMember> members = join(store.getTeamMembers(teamName, null));
            for (TeamMember member : members) {
                if (Objects.equals(member.getMemberName(), memberName)) {
                    continue;
                }
                if (!Objects.equals(member.getStatus(), MemberStatus.SHUTDOWN.value())) {
                    TEAM_LOGGER.info(
                            "Member %s is not shutdown (status: %s)",
                            member.getMemberName(),
                            member.getStatus()
                    );
                    TEAM_LOGGER.error("Cannot clean team %s: not all members are shutdown", teamName);
                    return false;
                }
            }
            join(store.deleteTeam(teamName));
            invokeCallback(onTeamCleaned, "on_team_cleaned");
            join(removeCleanupPaths());
            TeamCleanedEvent event = new TeamCleanedEvent();
            event.setTeamName(teamName);
            publishTeamEvent(event, "team cleaned event for " + teamName);
            TEAM_LOGGER.info("Team %s cleaned successfully", teamName);
            return true;
        });
    }

    public CompletionStage<Boolean> forceCleanTeam() {
        return forceCleanTeam(true);
    }

    public CompletionStage<Boolean> forceCleanTeam(boolean shutdownMembers) {
        return supplyStage(() -> {
            if (shutdownMembers) {
                for (TeamMember member : join(store.getTeamMembers(teamName, null))) {
                    if (Objects.equals(member.getMemberName(), memberName)) {
                        continue;
                    }
                    try {
                        join(shutdownMember(member.getMemberName(), true));
                    } catch (RuntimeException exception) {
                        TEAM_LOGGER.warning(
                                "Failed to request shutdown for member %s during force cleanup: %s",
                                member.getMemberName(),
                                exception.getMessage()
                        );
                    }
                }
            }
            boolean success = join(store.forceDeleteTeamSession(teamName));
            try {
                join(removeCleanupPaths());
            } catch (RuntimeException exception) {
                TEAM_LOGGER.error("Failed to remove cleanup paths for %s: %s", teamName, exception.getMessage());
                success = false;
            }
            if (success) {
                TEAM_LOGGER.info("Team %s force cleaned successfully", teamName);
            }
            return success;
        });
    }

    public CompletionStage<Optional<TeamMember>> getMember(String memberName) {
        return store.getMember(memberName, teamName);
    }

    public CompletionStage<List<TeamMember>> listMembers() {
        return store.getTeamMembers(teamName, null)
                .thenApply(members -> members.stream()
                        .filter(member -> !Objects.equals(member.getMemberName(), memberName))
                        .toList());
    }

    public CompletionStage<Optional<Team>> getTeamInfo() {
        return store.getTeam(teamName);
    }

    public CompletionStage<Optional<TeamCompletionSnapshot>> isTeamCompleted() {
        return supplyStage(() -> {
            List<TeamTask> tasks = join(taskManager.listTasks());
            if (tasks.isEmpty() || tasks.stream().anyMatch(task -> !TASK_TERMINAL_STATUSES.contains(task.getStatus()))) {
                return Optional.empty();
            }
            List<TeamMember> members = join(store.getTeamMembers(teamName, null));
            if (members.isEmpty()
                    || members.stream().anyMatch(member -> !StatusTransitions.MEMBER_SETTLED_STATUSES.contains(
                            member.getStatus()))) {
                return Optional.empty();
            }
            if (join(messageManager.hasUnreadMessages(true))) {
                return Optional.empty();
            }
            return Optional.of(new TeamCompletionSnapshot(members.size(), tasks.size()));
        });
    }

    public CompletionStage<Long> getTeamUpdatedAt() {
        return store.getTeamUpdatedAt(teamName);
    }

    public CompletionStage<Long> getMembersMaxUpdatedAt() {
        return store.getMembersMaxUpdatedAt(teamName);
    }

    public CompletionStage<Boolean> cancelTask(String taskId) {
        return supplyStage(() -> {
            Optional<TeamTask> task = join(taskManager.get(taskId));
            if (task.isEmpty()) {
                TEAM_LOGGER.error("Task %s not found", taskId);
                return false;
            }
            if (Objects.equals(task.get().getStatus(), TaskStatus.CANCELLED.value())) {
                TEAM_LOGGER.info("Task %s is already cancelled", taskId);
                return true;
            }
            TeamTask cancelledTask = join(taskManager.cancel(taskId));
            if (cancelledTask == null) {
                TEAM_LOGGER.error("Failed to cancel task %s", taskId);
                return false;
            }
            if (!isBlank(task.get().getAssignee())) {
                String content = "Task '" + task.get().getTitle() + "' (ID: " + taskId
                        + ") has been cancelled by the team leader.";
                String messageId = join(messageManager.sendMessage(content, task.get().getAssignee()));
                if (isBlank(messageId)) {
                    TEAM_LOGGER.warning(
                            "Failed to send cancellation notification to assignee %s",
                            task.get().getAssignee()
                    );
                }
            }
            TEAM_LOGGER.info("Task %s cancelled successfully", taskId);
            return true;
        });
    }

    public CompletionStage<Integer> cancelAllTasks() {
        return cancelAllTasks(null);
    }

    public CompletionStage<Integer> cancelAllTasks(Set<String> skipAssignees) {
        return supplyStage(() -> {
            List<TeamTask> cancelledTasks = join(taskManager.cancelAllTasks(skipAssignees));
            if (cancelledTasks.isEmpty()) {
                TEAM_LOGGER.info("No tasks to cancel in team %s", teamName);
                return 0;
            }
            join(messageManager.broadcastMessage(
                    "All tasks (" + cancelledTasks.size() + ") have been cancelled by team leader."
            ));
            TEAM_LOGGER.info("Cancelled %d tasks in team %s", cancelledTasks.size(), teamName);
            return cancelledTasks.size();
        });
    }

    public CompletionStage<Void> buildTeam(
            String displayName,
            String desc,
            String leaderDisplayName,
            String leaderDesc) {
        return buildTeam(displayName, desc, leaderDisplayName, leaderDesc, null);
    }

    public CompletionStage<Void> buildTeam(
            String displayName,
            String desc,
            String leaderDisplayName,
            String leaderDesc,
            CapabilityOverrides overrides) {
        return supplyStage(() -> {
            Boolean overrideHitt = overrides == null ? null : overrides.enableHitt();
            Boolean overrideBridge = overrides == null ? null : overrides.enableBridge();
            if (Boolean.TRUE.equals(overrideHitt) && !specEnableHitt) {
                throw new IllegalStateException(
                        "build_team(enable_hitt=True) requires TeamAgentSpec.enable_hitt=True (capability ceiling)"
                );
            }
            if (Boolean.TRUE.equals(overrideBridge) && !specEnableBridge) {
                throw new IllegalStateException(
                        "build_team(enable_bridge=True) requires TeamAgentSpec.enable_bridge=True (capability ceiling)"
                );
            }

            enableHitt = overrideHitt == null ? specEnableHitt : overrideHitt;
            enableBridge = overrideBridge == null ? specEnableBridge : overrideBridge;
            boolean success = join(store.createTeam(teamName, displayName, memberName, desc, null));
            if (!success) {
                throw new IllegalStateException("Failed to create team " + teamName);
            }

            AgentConfigurator.AgentCard leaderCard = new AgentConfigurator.AgentCard(
                    teamName + "_" + memberName,
                    leaderDisplayName,
                    leaderDesc
            );
            join(spawnMember(
                    memberName,
                    leaderDisplayName,
                    leaderCard,
                    leaderDesc,
                    null,
                    MemberStatus.BUSY,
                    ExecutionStatus.RUNNING,
                    MemberMode.BUILD_MODE,
                    leaderAllocation,
                    TeamRole.TEAMMATE
            ));

            List<BridgeMemberSpec> skippedBridgeSpecs = new ArrayList<>();
            for (TeamMemberSpec spec : predefinedMembers) {
                if (spec.getRoleType() == TeamRole.HUMAN_AGENT) {
                    continue;
                }
                if (spec instanceof BridgeMemberSpec bridgeSpec && !enableBridge) {
                    skippedBridgeSpecs.add(bridgeSpec);
                    bridgeMemberSpecs.remove(bridgeSpec.getMemberName());
                    continue;
                }
                AgentConfigurator.AgentCard memberCard = new AgentConfigurator.AgentCard(
                        teamName + "_" + spec.getMemberName(),
                        spec.getDisplayName(),
                        spec.getPersona()
                );
                AgentConfigurator.Allocation allocation = modelConfigAllocator == null
                        ? null
                        : modelConfigAllocator.allocate(spec.getModelName());
                join(spawnMember(
                        spec.getMemberName(),
                        spec.getDisplayName(),
                        memberCard,
                        spec.getPersona(),
                        spec.getPromptHint(),
                        MemberStatus.UNSTARTED,
                        ExecutionStatus.IDLE,
                        teammateMode,
                        allocation,
                        spec.getRoleType()
                ));
            }
            if (!skippedBridgeSpecs.isEmpty()) {
                TEAM_LOGGER.warning(
                        "Skipped %d predefined BRIDGE_AGENT(s) for team %s because build_team(enable_bridge=False)",
                        skippedBridgeSpecs.size(),
                        teamName
                );
            }

            List<TeamMemberSpec> humanSpecs = predefinedMembers.stream()
                    .filter(spec -> spec.getRoleType() == TeamRole.HUMAN_AGENT)
                    .toList();
            if (enableHitt) {
                for (TeamMemberSpec humanSpec : humanSpecs) {
                    join(spawnHumanAgent(
                            humanSpec.getMemberName(),
                            humanSpec.getDisplayName(),
                            humanSpec.getPersona(),
                            humanSpec.getPromptHint()
                    ));
                }
            } else if (!humanSpecs.isEmpty()) {
                TEAM_LOGGER.warning(
                        "Skipped %d predefined HUMAN_AGENT(s) for team %s because build_team(enable_hitt=False)",
                        humanSpecs.size(),
                        teamName
                );
            }

            invokeCallback(onTeamBuilt, "on_team_built");
            TeamCreatedEvent event = new TeamCreatedEvent();
            event.setTeamName(teamName);
            event.setDisplayName(displayName);
            event.setLeaderMemberName(memberName);
            event.setCreated((int) InMemoryTeamDatabase.getCurrentTime());
            publishTeamEvent(event, "team created event for " + teamName);
            TEAM_LOGGER.info("Team %s created successfully", teamName);
            return null;
        });
    }

    public CompletionStage<MemberOpResult> spawnHumanAgent(
            String memberName,
            String displayName,
            String desc,
            String prompt) {
        return supplyStage(() -> {
            if (!enableHitt) {
                return MemberOpResult.fail(
                        "Cannot spawn human agent: HITT capability is disabled "
                                + "(enable_hitt=False on TeamAgentSpec or build_team)"
                );
            }
            String resolvedDisplayName = firstNonBlank(displayName, AgentTeamI18n.t("hitt.human_agent_display_name"));
            String resolvedDesc = firstNonBlank(desc, AgentTeamI18n.t("hitt.human_agent_default_persona"));
            AgentConfigurator.AgentCard memberCard = new AgentConfigurator.AgentCard(
                    teamName + "_" + memberName,
                    resolvedDisplayName,
                    resolvedDesc
            );
            MemberOpResult result = join(spawnMember(
                    memberName,
                    resolvedDisplayName,
                    memberCard,
                    resolvedDesc,
                    prompt,
                    MemberStatus.UNSTARTED,
                    ExecutionStatus.IDLE,
                    MemberMode.BUILD_MODE,
                    null,
                    TeamRole.HUMAN_AGENT
            ));
            if (!result.isOk()) {
                TEAM_LOGGER.warning(
                        "Failed to register human agent '%s' for team %s: %s",
                        memberName,
                        teamName,
                        result.getReason()
                );
            }
            return result;
        });
    }

    public CompletionStage<Boolean> isHumanAgent(String memberName) {
        if (isBlank(memberName)) {
            return CompletableFuture.completedFuture(false);
        }
        return store.isHumanAgent(teamName, memberName);
    }

    public CompletionStage<Void> registerHumanAgentInbound(
            String memberName,
            HumanAgentInboundCallback callback) {
        return supplyStage(() -> {
            if (!join(isHumanAgent(memberName))) {
                Set<String> names = join(humanAgentNames());
                throw new IllegalArgumentException(
                        "'" + memberName + "' is not a registered human-agent member; registered members: " + names
                );
            }
            if (callback == null) {
                humanAgentInboundCallbacks.remove(memberName);
            } else {
                humanAgentInboundCallbacks.put(memberName, callback);
            }
            return null;
        });
    }

    public HumanAgentInboundCallback getHumanAgentInbound(String memberName) {
        return humanAgentInboundCallbacks.get(memberName);
    }

    public CompletionStage<Set<String>> humanAgentNames() {
        return store.listHumanAgentNames(teamName).thenApply(LinkedHashSet::new);
    }

    public boolean hittEnabled() {
        return enableHitt;
    }

    public boolean bridgeEnabled() {
        return enableBridge;
    }

    public boolean isBridgeAgent(String memberName) {
        return !isBlank(memberName) && bridgeMemberSpecs.containsKey(memberName);
    }

    public Set<String> bridgeAgentNames() {
        return new LinkedHashSet<>(bridgeMemberSpecs.keySet());
    }

    public BridgeMemberSpec getBridgeMemberSpec(String memberName) {
        return bridgeMemberSpecs.get(memberName);
    }

    public void setBridgeAdapter(String memberName, BridgeProtocolAdapter adapter) {
        if (!bridgeMemberSpecs.containsKey(memberName)) {
            throw new IllegalArgumentException(
                    "'" + memberName + "' is not a registered bridge-agent member; registered members: "
                            + bridgeMemberSpecs.keySet()
            );
        }
        if (adapter == null) {
            bridgeAdapters.remove(memberName);
        } else {
            bridgeAdapters.put(memberName, adapter);
        }
    }

    public BridgeProtocolAdapter getBridgeAdapter(String memberName) {
        return bridgeAdapters.get(memberName);
    }

    public CompletionStage<MemberOpResult> spawnBridgeAgent(
            String memberName,
            String displayName,
            String persona,
            String desc,
            String modelName,
            BridgeMailboxInjectMode mailboxInjectMode,
            String protocol,
            Map<String, Object> adapterConfig) {
        return supplyStage(() -> {
            if (!enableBridge) {
                return MemberOpResult.fail(
                        "Cannot spawn bridge agent: Bridge capability is disabled "
                                + "(enable_bridge=False on TeamAgentSpec or build_team)"
                );
            }
            if (isBlank(persona)) {
                return MemberOpResult.fail(
                        "spawn_bridge_agent requires non-empty 'persona' - it is the briefing the remote agent adopts"
                );
            }
            String resolvedDesc = firstNonBlank(desc, persona);
            AgentConfigurator.AgentCard memberCard = new AgentConfigurator.AgentCard(
                    teamName + "_" + memberName,
                    displayName,
                    resolvedDesc
            );
            AgentConfigurator.Allocation allocation = modelConfigAllocator == null
                    ? null
                    : modelConfigAllocator.allocate(modelName);
            MemberOpResult result = join(spawnMember(
                    memberName,
                    displayName,
                    memberCard,
                    resolvedDesc,
                    null,
                    MemberStatus.UNSTARTED,
                    ExecutionStatus.IDLE,
                    teammateMode,
                    allocation,
                    TeamRole.BRIDGE_AGENT
            ));
            if (!result.isOk()) {
                TEAM_LOGGER.warning(
                        "Failed to register bridge agent '%s' for team %s: %s",
                        memberName,
                        teamName,
                        result.getReason()
                );
                return result;
            }
            BridgeMemberSpec bridgeSpec = new BridgeMemberSpec();
            bridgeSpec.setMemberName(memberName);
            bridgeSpec.setDisplayName(displayName);
            bridgeSpec.setPersona(persona);
            bridgeSpec.setModelName(modelName);
            bridgeSpec.setMailboxInjectMode(mailboxInjectMode);
            bridgeSpec.setProtocol(protocol);
            bridgeSpec.setAdapterConfig(adapterConfig);
            bridgeMemberSpecs.put(memberName, bridgeSpec);
            return result;
        });
    }

    public boolean isExternalCliAgent(String memberName) {
        return externalCliSpecs.containsKey(memberName);
    }

    public String getExternalCliAgent(String memberName) {
        return externalCliSpecs.get(memberName);
    }

    public Set<String> externalCliAgentNames() {
        return new LinkedHashSet<>(externalCliSpecs.keySet());
    }

    public ExternalCliAgentSpec externalCliConfig(String cliAgent) {
        return externalCliConfigs.get(cliAgent);
    }

    public Set<String> externalCliKinds() {
        return new LinkedHashSet<>(externalCliConfigs.keySet());
    }

    public CompletionStage<MemberOpResult> spawnExternalCliAgent(
            String memberName,
            String displayName,
            String cliAgent,
            String persona,
            String desc,
            String modelName) {
        return supplyStage(() -> {
            if (isBlank(persona)) {
                return MemberOpResult.fail("spawn_external_cli_agent requires non-empty 'persona'");
            }
            if (!externalCliConfigs.containsKey(cliAgent)) {
                String declared = externalCliConfigs.isEmpty() ? "<none>" : String.join(", ", externalCliConfigs.keySet());
                return MemberOpResult.fail(
                        "cli_agent '" + cliAgent + "' is not declared in TeamAgentSpec.external_cli_agents "
                                + "(declared: " + declared + "); add a static config entry for it first"
                );
            }
            if (!CliAgentAdapter.availableAdapters().contains(cliAgent)) {
                return MemberOpResult.fail(
                        "Unknown cli_agent '" + cliAgent + "'; known: "
                                + String.join(", ", CliAgentAdapter.availableAdapters())
                );
            }
            String resolvedDesc = firstNonBlank(desc, persona);
            AgentConfigurator.AgentCard memberCard = new AgentConfigurator.AgentCard(
                    teamName + "_" + memberName,
                    displayName,
                    resolvedDesc
            );
            externalCliSpecs.put(memberName, cliAgent);
            MemberOpResult result = join(spawnMember(
                    memberName,
                    displayName,
                    memberCard,
                    resolvedDesc,
                    null,
                    MemberStatus.UNSTARTED,
                    ExecutionStatus.IDLE,
                    teammateMode,
                    null,
                    TeamRole.TEAMMATE
            ));
            if (!result.isOk()) {
                externalCliSpecs.remove(memberName);
                TEAM_LOGGER.warning(
                        "Failed to register external-cli agent '%s' for team %s: %s",
                        memberName,
                        teamName,
                        result.getReason()
                );
            }
            return result;
        });
    }

    private CompletionStage<Void> spawnAndPublish(String memberName, MemberStartupCallback onCreated) {
        return callMemberCallback(onCreated, memberName)
                .thenCompose(ignored -> {
                    MemberSpawnedEvent event = new MemberSpawnedEvent();
                    event.setTeamName(teamName);
                    event.setMemberName(memberName);
                    publishTeamEvent(event, "member spawned event for " + memberName);
                    TEAM_LOGGER.info("Member %s started", memberName);
                    return CompletableFuture.completedFuture(null);
                });
    }

    private CompletionStage<Void> removeCleanupPaths() {
        return supplyStage(() -> {
            if (cleanupPaths.isEmpty()) {
                return null;
            }
            List<Path> ordered = cleanupPaths.stream()
                    .map(Path::of)
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .toList();
            for (Path path : ordered) {
                if (!Files.isDirectory(path)) {
                    continue;
                }
                try (Stream<Path> stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(target -> {
                        try {
                            Files.deleteIfExists(target);
                        } catch (IOException exception) {
                            throw new CompletionException(exception);
                        }
                    });
                    TEAM_LOGGER.info("Removed team filesystem path: %s", path);
                } catch (IOException | CompletionException exception) {
                    TEAM_LOGGER.error("Failed to remove path %s: %s", path, exception.getMessage());
                }
            }
            return null;
        });
    }

    private void seedBridgeAndExternalIndexes(List<ExternalCliAgentSpec> externalCliAgents) {
        for (TeamMemberSpec memberSpec : predefinedMembers) {
            if (memberSpec instanceof BridgeMemberSpec bridgeSpec) {
                bridgeMemberSpecs.put(bridgeSpec.getMemberName(), bridgeSpec);
            }
        }
        if (externalCliAgents == null) {
            return;
        }
        for (ExternalCliAgentSpec spec : externalCliAgents) {
            if (spec != null && !isBlank(spec.getCliAgent())) {
                externalCliConfigs.put(spec.getCliAgent(), spec);
            }
        }
    }

    private void publishTeamEvent(com.openjiuwen.agent_teams.schema.BaseEventMessage event, String label) {
        if (messager == null) {
            return;
        }
        try {
            CompletionStage<Void> stage = messager.publish(
                    TeamTopic.TEAM.build(AgentTeamsContext.getSessionId(), teamName),
                    EventMessage.fromEvent(event)
            );
            if (stage != null) {
                stage.exceptionally(exception -> {
                    TEAM_LOGGER.error("Failed to publish %s: %s", label, unwrap(exception).getMessage());
                    return null;
                });
            }
        } catch (RuntimeException exception) {
            TEAM_LOGGER.error("Failed to publish %s: %s", label, exception.getMessage());
        }
    }

    private void invokeCallback(Supplier<CompletionStage<Void>> callback, String label) {
        if (callback == null) {
            return;
        }
        try {
            CompletionStage<Void> stage = callback.get();
            if (stage != null) {
                join(stage);
            }
        } catch (RuntimeException exception) {
            TEAM_LOGGER.error("%s callback failed for team %s: %s", label, teamName, exception.getMessage());
        }
    }

    private static CompletionStage<Void> callMemberCallback(MemberStartupCallback callback, String memberName) {
        if (callback == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> stage = callback.onCreated(memberName);
        return stage == null ? CompletableFuture.completedFuture(null) : stage;
    }

    private static String serialize(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName(), exception);
        }
    }

    private static String allocationToJson(AgentConfigurator.Allocation allocation) {
        if (allocation == null) {
            return null;
        }
        return serialize(allocation.toTeamModelConfig());
    }

    private static <T> CompletionStage<T> supplyStage(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier);
    }

    private static <T> T join(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException exception) {
            throw new CompletionException(unwrap(exception));
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String enumValue(MemberStatus status, MemberStatus defaultStatus) {
        return (status == null ? defaultStatus : status).value();
    }

    private static String enumValue(ExecutionStatus status, ExecutionStatus defaultStatus) {
        return (status == null ? defaultStatus : status).value();
    }

    private static String enumValue(MemberMode mode, MemberMode defaultMode) {
        return (mode == null ? defaultMode : mode).value();
    }

    private static String enumValue(TeamRole role, TeamRole defaultRole) {
        return (role == null ? defaultRole : role).value();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static String firstNonBlank(String first, String fallback) {
        return isBlank(first) ? nullToEmpty(fallback) : first;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Runtime flags for one {@link #buildTeam} call.
     *
     * <p>Mirrors Python's {@code CapabilityOverrides} in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    public record CapabilityOverrides(Boolean enableHitt, Boolean enableBridge) {
    }

    /**
     * Callback used to launch a member after the STARTING CAS succeeds.
     *
     * <p>Mirrors Python's {@code on_created} callback in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    @FunctionalInterface
    public interface MemberStartupCallback {
        CompletionStage<Void> onCreated(String memberName);
    }

    /**
     * Callback fired when a team-side message reaches a human agent.
     *
     * <p>Mirrors Python's callback accepted by {@code register_human_agent_inbound} in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    @FunctionalInterface
    public interface HumanAgentInboundCallback {
        CompletionStage<Void> onInbound(Object event);
    }

    /**
     * Narrow persistence surface used by the backend across memory and SQL stores.
     *
     * <p>Mirrors Python's {@code TeamBackend.db} DAO usage in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    private interface TeamStore {
        CompletionStage<Boolean> createTeam(
                String teamName,
                String displayName,
                String leaderMemberName,
                String desc,
                String prompt);

        CompletionStage<Optional<Team>> getTeam(String teamName);

        CompletionStage<Boolean> deleteTeam(String teamName);

        CompletionStage<Long> getTeamUpdatedAt(String teamName);

        CompletionStage<Boolean> forceDeleteTeamSession(String teamName);

        CompletionStage<Boolean> createMember(
                String memberName,
                String teamName,
                String displayName,
                String agentCard,
                String status,
                String role,
                String desc,
                String executionStatus,
                String mode,
                String prompt,
                String modelRefJson);

        CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName);

        CompletionStage<List<TeamMember>> getTeamMembers(String teamName, String status);

        CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status);

        CompletionStage<Boolean> tryTransitionMemberStatus(
                String memberName,
                String teamName,
                MemberStatus expected,
                MemberStatus target);

        CompletionStage<Long> getMembersMaxUpdatedAt(String teamName);

        CompletionStage<Boolean> isHumanAgent(String teamName, String memberName);

        CompletionStage<List<String>> listHumanAgentNames(String teamName);
    }

    /**
     * In-memory store adapter for tests and memory-backed runtime sessions.
     *
     * <p>Mirrors Python's {@code TeamBackend} memory database usage in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    private static final class InMemoryStore implements TeamStore {
        private final InMemoryTeamDatabase database;

        private InMemoryStore(InMemoryTeamDatabase database) {
            this.database = database;
        }

        @Override
        public CompletionStage<Boolean> createTeam(
                String teamName,
                String displayName,
                String leaderMemberName,
                String desc,
                String prompt) {
            return database.createTeam(teamName, displayName, leaderMemberName, desc, prompt);
        }

        @Override
        public CompletionStage<Optional<Team>> getTeam(String teamName) {
            return database.getTeam(teamName);
        }

        @Override
        public CompletionStage<Boolean> deleteTeam(String teamName) {
            return database.deleteTeam(teamName);
        }

        @Override
        public CompletionStage<Long> getTeamUpdatedAt(String teamName) {
            return database.getTeamUpdatedAt(teamName);
        }

        @Override
        public CompletionStage<Boolean> forceDeleteTeamSession(String teamName) {
            return database.forceDeleteTeamSession(teamName);
        }

        @Override
        public CompletionStage<Boolean> createMember(
                String memberName,
                String teamName,
                String displayName,
                String agentCard,
                String status,
                String role,
                String desc,
                String executionStatus,
                String mode,
                String prompt,
                String modelRefJson) {
            return database.createMember(
                    memberName,
                    teamName,
                    displayName,
                    agentCard,
                    status,
                    role,
                    desc,
                    executionStatus,
                    mode,
                    prompt,
                    modelRefJson
            );
        }

        @Override
        public CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName) {
            return database.getMember(memberName, teamName);
        }

        @Override
        public CompletionStage<List<TeamMember>> getTeamMembers(String teamName, String status) {
            return database.getTeamMembers(teamName, status);
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            return database.updateMemberStatus(memberName, teamName, status);
        }

        @Override
        public CompletionStage<Boolean> tryTransitionMemberStatus(
                String memberName,
                String teamName,
                MemberStatus expected,
                MemberStatus target) {
            return supplyStage(() -> {
                Optional<TeamMember> member = database.getMember(memberName, teamName).join();
                if (member.isEmpty() || !Objects.equals(member.get().getStatus(), expected.value())) {
                    return false;
                }
                return database.updateMemberStatus(memberName, teamName, target.value()).join();
            });
        }

        @Override
        public CompletionStage<Long> getMembersMaxUpdatedAt(String teamName) {
            return database.getMembersMaxUpdatedAt(teamName);
        }

        @Override
        public CompletionStage<Boolean> isHumanAgent(String teamName, String memberName) {
            return database.isHumanAgent(teamName, memberName);
        }

        @Override
        public CompletionStage<List<String>> listHumanAgentNames(String teamName) {
            return database.listHumanAgentNames(teamName);
        }
    }

    /**
     * SQL store adapter for the asynchronous {@link TeamDatabase} DAO facade.
     *
     * <p>Mirrors Python's {@code TeamBackend} SQL database usage in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    private static final class SqlStore implements TeamStore {
        private final TeamDatabase database;

        private SqlStore(TeamDatabase database) {
            this.database = database;
        }

        @Override
        public CompletionStage<Boolean> createTeam(
                String teamName,
                String displayName,
                String leaderMemberName,
                String desc,
                String prompt) {
            TeamDao teamDao = requireTeamDao();
            return teamDao.createTeam(teamName, displayName, leaderMemberName, desc, prompt);
        }

        @Override
        public CompletionStage<Optional<Team>> getTeam(String teamName) {
            return requireTeamDao().getTeam(teamName);
        }

        @Override
        public CompletionStage<Boolean> deleteTeam(String teamName) {
            return requireTeamDao().deleteTeam(teamName);
        }

        @Override
        public CompletionStage<Long> getTeamUpdatedAt(String teamName) {
            return requireTeamDao().getTeamUpdatedAt(teamName);
        }

        @Override
        public CompletionStage<Boolean> forceDeleteTeamSession(String teamName) {
            return database.forceDeleteTeamSession(teamName);
        }

        @Override
        public CompletionStage<Boolean> createMember(
                String memberName,
                String teamName,
                String displayName,
                String agentCard,
                String status,
                String role,
                String desc,
                String executionStatus,
                String mode,
                String prompt,
                String modelRefJson) {
            return requireMemberDao().createMember(
                    memberName,
                    teamName,
                    displayName,
                    agentCard,
                    status,
                    role,
                    desc,
                    executionStatus,
                    mode,
                    prompt,
                    modelRefJson
            );
        }

        @Override
        public CompletionStage<Optional<TeamMember>> getMember(String memberName, String teamName) {
            return requireMemberDao().getMember(memberName, teamName);
        }

        @Override
        public CompletionStage<List<TeamMember>> getTeamMembers(String teamName, String status) {
            return requireMemberDao().getTeamMembers(teamName, status);
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            return requireMemberDao().updateMemberStatus(memberName, teamName, status);
        }

        @Override
        public CompletionStage<Boolean> tryTransitionMemberStatus(
                String memberName,
                String teamName,
                MemberStatus expected,
                MemberStatus target) {
            return requireMemberDao().tryTransitionMemberStatus(memberName, teamName, expected, target);
        }

        @Override
        public CompletionStage<Long> getMembersMaxUpdatedAt(String teamName) {
            return requireMemberDao().getMembersMaxUpdatedAt(teamName);
        }

        @Override
        public CompletionStage<Boolean> isHumanAgent(String teamName, String memberName) {
            return requireMemberDao().isHumanAgent(teamName, memberName);
        }

        @Override
        public CompletionStage<List<String>> listHumanAgentNames(String teamName) {
            return requireMemberDao().listHumanAgentNames(teamName);
        }

        private TeamDao requireTeamDao() {
            TeamDao teamDao = database.getTeam();
            if (teamDao == null) {
                database.initialize().join();
                teamDao = database.getTeam();
            }
            if (teamDao == null) {
                throw new IllegalStateException("TeamDatabase team DAO is not initialized");
            }
            return teamDao;
        }

        private MemberDao requireMemberDao() {
            MemberDao memberDao = database.getMember();
            if (memberDao == null) {
                database.initialize().join();
                memberDao = database.getMember();
            }
            if (memberDao == null) {
                throw new IllegalStateException("TeamDatabase member DAO is not initialized");
            }
            return memberDao;
        }
    }
}
