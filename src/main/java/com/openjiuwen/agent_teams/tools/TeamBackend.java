/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.agent.TeamMemberRuntime;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.messager.Messagers;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskRecord;
import com.openjiuwen.agent_teams.schema.task.TaskStatus;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.SecurityRail;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal in-memory team backend.
 *
 * <p>Mirrors Python's {@code TeamBackend} in
 * {@code openjiuwen.agent_teams.tools.team}.
 */
public class TeamBackend {

    private final String teamName;
    private final String memberName;
    private final boolean leader;
    private final MemberMode teammateMode;
    private final List<TeamMemberSpec> predefinedMembers;
    private final Map<String, TeamMember> members;
    private final Set<String> cleanupPaths = new LinkedHashSet<>();
    private final TeamTaskManager taskManager;
    private final TeamMessageManager messageManager;
    private final Map<String, Session> memberSessions = new LinkedHashMap<>();
    private final Map<String, TeamMemberRuntime> memberRuntimes = new LinkedHashMap<>();
    private final TeamBackendStore store;
    private final Messager messager;

    public TeamBackend(
            String teamName,
            String memberName,
            boolean leader,
            MemberMode teammateMode,
            List<TeamMemberSpec> predefinedMembers
    ) {
        this.teamName = teamName;
        this.memberName = memberName;
        this.leader = leader;
        this.teammateMode = teammateMode != null ? teammateMode : MemberMode.BUILD_MODE;
        this.predefinedMembers = predefinedMembers != null ? new ArrayList<>(predefinedMembers) : new ArrayList<>();
        this.store = TeamBackendRegistry.getOrCreate(teamName);
        this.members = store.getMembers();
        Messager sharedMessager = store.getMessager();
        if (sharedMessager == null) {
            MessagerTransportConfig transportConfig = new MessagerTransportConfig();
            transportConfig.setBackend("inprocess");
            transportConfig.setTeamName(teamName);
            transportConfig.setNodeId(memberName);
            sharedMessager = Messagers.createMessager(transportConfig);
            sharedMessager.start();
            store.setMessager(sharedMessager);
        }
        this.messager = sharedMessager;
        this.taskManager = new TeamTaskManager(teamName, memberName, store.getTasks());
        this.messageManager = new TeamMessageManager(teamName, memberName, store.getMessages(), this.messager);
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

    public MemberMode getTeammateMode() {
        return teammateMode;
    }

    public void registerCleanupPath(String path) {
        if (path != null && !path.isBlank()) {
            cleanupPaths.add(path);
        }
    }

    public TeamMember spawnMember(
            String memberName,
            String displayName,
            AgentCard agentCard,
            String desc,
            String prompt,
            MemberStatus status,
            ExecutionStatus executionStatus
    ) {
        if (members.containsKey(memberName)) {
            return members.get(memberName);
        }
        TeamMember member = new TeamMember(
                memberName,
                teamName,
                displayName,
                agentCard,
                prompt,
                desc,
                status,
                executionStatus
        );
        members.put(memberName, member);
        ensureMemberSession(memberName, agentCard);
        ensureMemberRuntime(memberName);
        return member;
    }

    public List<String> startup() {
        List<String> started = new ArrayList<>();
        for (TeamMember member : members.values()) {
            if (member.getStatus() == MemberStatus.UNSTARTED) {
                member.setStatus(MemberStatus.READY);
                ensureMemberSession(member.getMemberName(), member.getAgentCard());
                ensureMemberRuntime(member.getMemberName());
                started.add(member.getMemberName());
            }
        }
        return started;
    }

    public TeamMember getMember(String memberName) {
        return members.get(memberName);
    }

    public boolean hasMember(String memberName) {
        return memberName != null && members.containsKey(memberName);
    }

    public List<String> humanAgentNames() {
        List<String> names = new ArrayList<>();
        for (TeamMemberSpec spec : predefinedMembers) {
            if (spec.getRoleType() == TeamRole.HUMAN_AGENT && spec.getMemberName() != null) {
                names.add(spec.getMemberName());
            }
        }
        if (members.containsKey("human_agent") && !names.contains("human_agent")) {
            names.add("human_agent");
        }
        return names;
    }

    public void registerMemberSession(String memberName, Session session) {
        if (memberName != null && !memberName.isBlank() && session != null) {
            memberSessions.put(memberName, session);
        }
    }

    /**
     * Create or return the minimal Java session associated with a team member.
     *
     * <p>Mirrors Python's member session context in
     * {@code openjiuwen.agent_teams.spawn.context} and the spawn flows under
     * {@code openjiuwen.agent_teams.spawn}.
     */
    public Session ensureMemberSession(String memberName, AgentCard card) {
        if (memberName == null || memberName.isBlank()) {
            return null;
        }
        Session existing = memberSessions.get(memberName);
        if (existing != null) {
            return existing;
        }
        String sessionId = teamName + "_" + memberName;
        Map<String, Object> envs = new LinkedHashMap<>();
        envs.put("team_name", teamName);
        envs.put("member_name", memberName);
        AgentSessionApi session = AgentSessionApi.create(sessionId, envs, card);
        registerMemberSession(memberName, session);
        return session;
    }

    public Session getMemberSession(String memberName) {
        return memberSessions.get(memberName);
    }

    public TeamMemberRuntime getMemberRuntime(String memberName) {
        return memberRuntimes.get(memberName);
    }

    public void clearMemberRuntime(String memberName) {
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        memberRuntimes.remove(memberName);
    }

    public Session rebindMemberSession(String memberName) {
        TeamMember member = members.get(memberName);
        if (member == null) {
            return null;
        }
        memberSessions.remove(memberName);
        memberRuntimes.remove(memberName);
        return ensureMemberSession(memberName, member.getAgentCard());
    }

    /**
     * Create or return the minimal Java runtime associated with a team member.
     *
     * <p>Mirrors Python's teammate runtime creation under
     * {@code openjiuwen.agent_teams.spawn}.
     */
    public TeamMemberRuntime ensureMemberRuntime(String memberName) {
        if (memberName == null || memberName.isBlank()) {
            return null;
        }
        TeamMemberRuntime existing = memberRuntimes.get(memberName);
        if (existing != null) {
            return existing;
        }
        TeamMember member = members.get(memberName);
        if (member == null) {
            return null;
        }
        Session session = ensureMemberSession(memberName, member.getAgentCard());
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(member.getAgentCard());
        String prompt = member.getPrompt() != null ? member.getPrompt() : "";
        String desc = member.getDesc() != null ? member.getDesc() : "";
        config.setSystemPrompt("You are team member '" + memberName + "' in team '" + teamName + "'.\n"
                + desc + "\n" + prompt);
        DeepAgent agent = HarnessFactory.createDeepAgent(config);
        TeamMemberRuntime runtime = new TeamMemberRuntime(member, agent, session);
        memberRuntimes.put(memberName, runtime);
        return runtime;
    }

    public Object runMember(String memberName, Object content) {
        TeamMemberRuntime runtime = ensureMemberRuntime(memberName);
        if (runtime == null) {
            return null;
        }
        TeamMember member = runtime.getMember();
        member.setStatus(MemberStatus.BUSY);
        member.setExecutionStatus(ExecutionStatus.RUNNING);
        Object result = runtime.invoke(content);
        member.setStatus(MemberStatus.READY);
        member.setExecutionStatus(ExecutionStatus.IDLE);
        return result;
    }

    public TeamTaskManager getTaskManager() {
        return taskManager;
    }

    public TeamMessageManager getMessageManager() {
        return messageManager;
    }

    public Messager getMessager() {
        return messager;
    }

    public TeamBackendStore getStore() {
        return store;
    }

    public List<TeamMember> listMembers() {
        return new ArrayList<>(members.values());
    }

    public boolean shutdownMember(String memberName, boolean force) {
        TeamMember member = members.get(memberName);
        if (member == null) {
            return false;
        }
        member.setExecutionStatus(ExecutionStatus.IDLE);
        member.setStatus(force ? MemberStatus.SHUTDOWN : MemberStatus.SHUTDOWN_REQUESTED);
        return true;
    }

    public boolean approveTool(String memberName, String toolCallId, boolean approved, String feedback) {
        return approveTool(memberName, toolCallId, approved, feedback, false);
    }

    public boolean approveTool(String memberName, String toolCallId, boolean approved, String feedback, boolean autoConfirm) {
        TeamMember member = members.get(memberName);
        if (member == null) {
            return false;
        }
        member.setExecutionStatus(approved ? ExecutionStatus.RUNNING : ExecutionStatus.INTERRUPTED);
        Session session = memberSessions.get(memberName);
        if (session != null) {
            Object pendingObj = session.getState(SecurityRail.PENDING_APPROVAL_STATE_KEY);
            if (pendingObj instanceof Map<?, ?> pending) {
                String pendingToolName = pending.get("tool_name") != null ? String.valueOf(pending.get("tool_name")) : null;
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("approved", approved);
                decision.put("feedback", feedback != null ? feedback : "");
                decision.put("auto_confirm", autoConfirm);
                decision.put("tool_call_id", toolCallId);
                if (pendingToolName != null && !pendingToolName.isBlank()) {
                    decision.put("tool_name", pendingToolName);
                    session.updateState(Map.of(
                            SecurityRail.PENDING_APPROVAL_STATE_KEY,
                            new LinkedHashMap<>(Map.of(
                                    "tool_name", pendingToolName,
                                    "decision", decision,
                                    "approved_by", this.memberName,
                                    "tool_call_id", toolCallId
                            ))
                    ));
                }
            }
        }
        return true;
    }

    public TaskRecord createTask(String title, String content, String taskId, List<String> dependencies) {
        return taskManager.add(title, content, taskId, dependencies);
    }

    public TaskRecord createTaskWithPriority(
            String title,
            String content,
            String taskId,
            List<String> dependencies,
            List<String> dependedBy
    ) {
        return taskManager.addWithPriority(title, content, taskId, dependencies, dependedBy);
    }

    public List<TaskSummary> listTasks() {
        return listTasks(null);
    }

    public List<TaskSummary> listTasks(TaskStatus status) {
        return taskManager.listByStatus(status);
    }

    public TaskDetail getTask(String taskId) {
        return taskManager.get(taskId);
    }

    public boolean claimTask(String taskId, String assignee) {
        return taskManager.claim(taskId, assignee);
    }

    /**
     * Claim a task for a member and immediately run the teammate runtime on the
     * task content.
     *
     * <p>Mirrors Python's task-driven teammate execution intent in
     * {@code openjiuwen.agent_teams.tools.team_tools} and the surrounding team
     * runtime flow.
     */
    public Map<String, Object> claimAndRunTask(String taskId, String assignee) {
        TaskDetail task = taskManager.get(taskId);
        if (task == null) {
            return null;
        }
        if (!taskManager.claim(taskId, assignee)) {
            return null;
        }
        Object runtimeResult = runMember(assignee, task.getContent());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task_id", taskId);
        result.put("assignee", assignee);
        result.put("runtime_result", runtimeResult);
        return result;
    }

    public boolean completeTask(String taskId) {
        return taskManager.complete(taskId);
    }

    public boolean assignTask(String taskId, String assignee) {
        TaskDetail task = taskManager.get(taskId);
        if (task == null) {
            return false;
        }
        if (!hasMember(assignee)) {
            return false;
        }
        return taskManager.assign(taskId, assignee);
    }

    public boolean updateTask(String taskId, String title, String content) {
        return taskManager.update(taskId, title, content);
    }

    public boolean addBlockedBy(String taskId, List<String> dependencies) {
        return taskManager.addBlockedBy(taskId, dependencies);
    }

    public boolean cancelTask(String taskId) {
        return taskManager.cancel(taskId);
    }

    public int cancelAllTasks() {
        int count = 0;
        for (TaskSummary task : listTasks()) {
            if (cancelTask(task.getTaskId())) {
                count++;
            }
        }
        return count;
    }

    public String sendMessage(String content, String toMemberName, String fromMemberName) {
        return messageManager.sendMessage(content, toMemberName, fromMemberName);
    }

    public void createTeam(
            String displayName,
            String desc,
            String leaderDisplayName,
            String leaderDesc
    ) {
        store.createTeam(displayName, desc, memberName, leaderDisplayName, leaderDesc);
        AgentCard card = new AgentCard();
        card.setName(memberName);
        card.setDescription(leaderDisplayName != null && !leaderDisplayName.isBlank() ? leaderDisplayName : memberName);
        spawnMember(
                memberName,
                leaderDisplayName != null && !leaderDisplayName.isBlank() ? leaderDisplayName : memberName,
                card,
                leaderDesc,
                null,
                MemberStatus.READY,
                ExecutionStatus.IDLE
        );
    }

    public boolean canCleanTeam() {
        for (TeamMember member : members.values()) {
            if (member.getMemberName().equals(memberName)) {
                continue;
            }
            if (member.getStatus() != MemberStatus.SHUTDOWN) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deliver a point-to-point message and trigger the recipient's minimal Java
     * runtime execution path.
     *
     * <p>Mirrors Python's send-message intent in
     * {@code openjiuwen.agent_teams.tools.team_tools} and teammate wake-up flow
     * in {@code openjiuwen.agent_teams.agent.dispatcher} / spawn runtime.
     */
    public Map<String, Object> deliverMessage(String content, String toMemberName, String fromMemberName) {
        if (toMemberName == null || toMemberName.isBlank()) {
            return null;
        }
        String messageId = sendMessage(content, toMemberName, fromMemberName);
        Object runtimeResult = hasMember(toMemberName) ? runMember(toMemberName, content) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", messageId);
        result.put("runtime_result", runtimeResult);
        return result;
    }

    public String broadcastMessage(String content, String fromMemberName) {
        return messageManager.broadcastMessage(content, fromMemberName);
    }

    public Map<String, Object> broadcastMessageToMembers(String content, String fromMemberName) {
        String messageId = broadcastMessage(content, fromMemberName);
        List<String> triggered = new ArrayList<>();
        for (TeamMember member : members.values()) {
            if (member.getMemberName().equals(fromMemberName)) {
                continue;
            }
            if (runMember(member.getMemberName(), content) != null) {
                triggered.add(member.getMemberName());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message_id", messageId);
        result.put("triggered_members", triggered);
        return result;
    }

    public List<MessageRecord> getMessages(String toMemberName, boolean unreadOnly, String fromMemberName) {
        return messageManager.getMessages(toMemberName, unreadOnly, fromMemberName);
    }

    public List<MessageRecord> getBroadcastMessages(boolean unreadOnly, String fromMemberName) {
        return messageManager.getBroadcastMessages(unreadOnly, fromMemberName);
    }

    public boolean approvePlan(String memberName, boolean approved, String feedback) {
        TeamMember member = members.get(memberName);
        if (member == null) {
            return false;
        }
        member.setExecutionStatus(approved ? ExecutionStatus.RUNNING : ExecutionStatus.INTERRUPTED);
        return true;
    }

    public void registerPredefinedMembers() {
        for (TeamMemberSpec spec : predefinedMembers) {
            AgentCard card = new AgentCard();
            assignField(card, "name", spec.getMemberName());
            assignField(card, "description", spec.getDisplayName());
            spawnMember(
                    spec.getMemberName(),
                    spec.getDisplayName(),
                    card,
                    spec.getPersona(),
                    spec.getPromptHint(),
                    spec.getRoleType() == TeamRole.HUMAN_AGENT ? MemberStatus.READY : MemberStatus.UNSTARTED,
                    ExecutionStatus.IDLE
            );
        }
    }

    public List<String> cleanTeam() {
        List<String> removed = new ArrayList<>();
        for (String rawPath : cleanupPaths) {
            try {
                Path path = Path.of(rawPath);
                if (Files.isDirectory(path)) {
                    deleteRecursively(path);
                    removed.add(rawPath);
                }
            } catch (Exception ignored) {
                // Keep cleanup best-effort for the minimal Java port.
            }
        }
        members.clear();
        cleanupPaths.clear();
        return removed;
    }

    private void deleteRecursively(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // best effort
                        }
                    });
        }
    }

    private static void assignField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to set field '" + fieldName + "'", e);
            }
        }
        throw new IllegalStateException("Field not found: " + fieldName + " on " + target.getClass().getName());
    }
}
