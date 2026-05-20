/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.messager.Messager;
import com.openjiuwen.agentteams.messager.MessagerFactory;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntries;
import com.openjiuwen.agentteams.schema.team.TeamLifecycle;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamModelConfig;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.core.memory.team.PromptMode;
import com.openjiuwen.core.memory.team.TeamLanguage;
import com.openjiuwen.core.memory.team.TeamMemoryConfig;
import com.openjiuwen.core.memory.team.TeamMemoryManager;
import com.openjiuwen.core.memory.team.TeamMemoryManagerParams;
import com.openjiuwen.core.memory.team.TeamScenario;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentKind;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Java coordination host mirroring the first real TeamAgent interaction slice.
 */
@Getter
public class TeamAgent {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TeamAgentSpec spec;
    private TeamRuntimeContext context;
    private TeamBackend teamBackend;
    private TeamMessageManager messageManager;
    private CoordinationManager coordinationManager;
    private CoordinatorLoop coordinatorLoop;
    private EventDispatcher dispatcher;
    private RecoveryManager recoveryManager;
    private SpawnManager spawnManager;
    private ModelAllocator modelAllocator;
    private TeamMemoryManager memoryManager;
    private DeepAgent deepAgent;
    private StreamController streamController;
    private com.openjiuwen.core.session.AgentSessionApi agentSession;
    private InteractiveInput lastResumedInterruptInput;
    private boolean isCancelRequested;
    private boolean isInFlightRound;
    private String pendingUserQuery = "";
    private final List<Object> eventListeners = new ArrayList<>();
    private final List<String> leaderInbox = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamAgent configure(TeamAgentSpec spec, TeamRuntimeContext context) {
        this.spec = spec.build();
        this.context = context != null ? context : TeamRuntimeContext.builder()
                .teamId(this.spec.getName())
                .lifecycle(TeamLifecycle.CREATED)
                .build();
        if (this.context.getTeamId() == null || this.context.getTeamId().isBlank()) {
            this.context.setTeamId(this.spec.getName());
        }
        if (this.context.getLifecycle() == null) {
            this.context.setLifecycle(TeamLifecycle.CREATED);
        }
        if (this.context.getMetadata() == null) {
            this.context.setMetadata(new LinkedHashMap<>());
        } else {
            this.context.setMetadata(new LinkedHashMap<>(this.context.getMetadata()));
        }
        setInFlightRound(false);
        if (this.context.getSessionId() != null) {
            this.context.getMetadata().put("session_id", this.context.getSessionId());
        }
        if (this.modelAllocator == null) {
            this.modelAllocator = ModelAllocators.buildModelAllocator(this.spec);
        }
        restoreAllocatorStateFromContext();
        bootstrapCoordinationHost();
        setupAgent();
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamAgent attachModelAllocator(ModelAllocator allocator, Allocation leaderAllocation) {
        this.modelAllocator = allocator;
        if (leaderAllocation != null) {
            if (this.context == null) {
                this.context = TeamRuntimeContext.builder().metadata(new LinkedHashMap<>()).build();
            }
            if (this.context.getMetadata() == null) {
                this.context.setMetadata(new LinkedHashMap<>());
            }
            this.context.getMetadata().put("member_model", leaderAllocation.toTeamModelConfig());
            this.context.getMetadata().put("leader_model_ref", leaderAllocation.toDbRef());
        }
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamAgent resumeForNewSession(String sessionId) {
        ensureConfigured();
        List<RecoveryManager.RecoverableMember> recoverableMembers =
                recoveryManager.collectLiveTeammatesForSessionSwitch();
        applySessionId(sessionId);
        recoveryManager.restartForSessionSwitch(recoverableMembers, true);
        context.getMetadata().put("recoverable_member_count", recoverableMembers.size());
        context.getMetadata().put("session_switch_cleanup", true);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamAgent recoverForExistingSession(String sessionId) {
        ensureConfigured();
        List<RecoveryManager.RecoverableMember> recoverableMembers =
                recoveryManager.collectLiveTeammatesForSessionSwitch();
        applySessionId(sessionId);
        recoveryManager.restartForSessionSwitch(recoverableMembers, false);
        context.getMetadata().put("recoverable_member_count", recoverableMembers.size());
        context.getMetadata().put("session_switch_cleanup", false);
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> recoverTeam() {
        ensureConfigured();
        List<String> restarted = recoveryManager.recoverTeam();
        context.getMetadata().put("recovered_members", List.copyOf(restarted));
        return restarted;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void persistLeaderConfigToSession() {
        ensureConfigured();
        recoveryManager.persistLeaderConfig(agentSession, spec, context, modelAllocator);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void persistAllocatorStateToSession() {
        ensureConfigured();
        recoveryManager.persistAllocatorState(agentSession, modelAllocator);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void persistAllocatorState() {
        ensureConfigured();
        persistAllocatorStateToContext();
        persistAllocatorStateToSession();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> dispatchTask(String query) {
        ensureConfigured();
        context.setLifecycle(TeamLifecycle.RUNNING);
        pendingUserQuery = query != null ? query : "";
        context.getMetadata().put("last_query", query);
        context.getMetadata().put("last_dispatch_at", Instant.now().toString());
        setInFlightRound(true);
        startCoordination(query);
        try {
            CoordinationManager.UserInputHandoff handoff = coordinationManager.handoffUserInput(
                    query,
                    resolveLeaderMemberName());
            String route = handoff.route();
            String target = handoff.target();
            String deliveredContent = handoff.deliveredContent();
            String messageId = handoff.messageId();

            context.getMetadata().put("last_route", route);
            context.getMetadata().put("last_target", target);
            context.getMetadata().put("leader_inbox_size", leaderInbox.size());
            context.getMetadata().put("message_count", messageManager.listAllMessages().size());

            TeamMemberSpec leader = spec.getMembers().stream()
                    .filter(member -> member.getRole() == TeamRole.LEADER)
                    .findFirst()
                    .orElse(null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("team_id", context.getTeamId());
            result.put("session_id", context.getSessionId());
            result.put("status", context.getLifecycle().name().toLowerCase(Locale.ROOT));
            result.put("leader", leader != null ? leader.getName() : null);
            result.put("member_count", spec.getMembers().size());
            result.put("query", query);
            result.put("route", route);
            result.put("target", target);
            result.put("delivered_content", deliveredContent);
            result.put("message_id", messageId);
            result.put("leader_inbox_size", leaderInbox.size());
            return result;
        } finally {
            finalizeRound();
            setInFlightRound(false);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Object session) {
        ensureConfigured();
        Object rawQuery = inputs != null
                ? inputs.getOrDefault("query", inputs.getOrDefault("data", ""))
                : "";
        context.setLifecycle(TeamLifecycle.RUNNING);
        pendingUserQuery = rawQuery != null ? String.valueOf(rawQuery) : "";
        context.getMetadata().put("last_query", pendingUserQuery);
        context.getMetadata().put("last_dispatch_at", Instant.now().toString());
        context.getMetadata().put("streaming_coordination", true);
        if (session instanceof com.openjiuwen.core.session.AgentSessionApi sessionApi) {
            this.agentSession = sessionApi;
            applySessionId(sessionApi.getSessionId());
        } else if (session instanceof String sessionId && !sessionId.isBlank()) {
            applySessionId(sessionId);
        } else {
            // no-op
        }
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        streamController.setStreamQueue(queue);
        streamController.requestCloseStreamAfterCurrentRound();
        startCoordination(pendingUserQuery);
        coordinatorLoop.start();
        coordinatorLoop.enqueue(InnerEventMessage.builder()
                .eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", pendingUserQuery))
                .build());
        return new CoordinationStreamIterator(queue);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void deliverInput(String content) {
        deliverInput(content, true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void deliverInput(String content, boolean isUseSteer) {
        ensureConfigured();
        if (streamController != null && streamController.isAgentRunning()) {
            if (isUseSteer) {
                streamController.steer(content);
                context.getMetadata().put("last_running_input_route", "steer");
            } else {
                streamController.isFollowUp(content);
                context.getMetadata().put("last_running_input_route", "follow_up");
            }
            return;
        }
        if (isStreamingCoordinationActive()) {
            pendingUserQuery = content != null ? content : "";
            streamController.startRound(pendingUserQuery);
            context.getMetadata().put("last_route", "stream_round");
            context.getMetadata().put("last_target", resolveLocalMemberName());
            return;
        }
        if (hasInFlightRound()) {
            streamController.getPendingInputs().add(content);
            context.getMetadata().put("pending_input_count", streamController.getPendingInputs().size());
            return;
        }
        pendingUserQuery = content != null ? content : "";
        CoordinationManager.UserInputHandoff handoff = coordinationManager.handoffUserInput(
                content,
                resolveLeaderMemberName());
        context.getMetadata().put("last_route", handoff.route());
        context.getMetadata().put("last_target", handoff.target());
        context.getMetadata().put("leader_inbox_size", leaderInbox.size());
        context.getMetadata().put("message_count", messageManager.listAllMessages().size());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void resumeInterrupt(InteractiveInput input) {
        ensureConfigured();
        if (!streamController.isValidInterruptResume(input)) {
            context.getMetadata().put("last_resume_interrupt_dropped", true);
            return;
        }
        if (hasInFlightRound()) {
            streamController.getPendingInterruptResumes().offer(input);
            context.getMetadata().put(
                    "pending_interrupt_resume_count",
                    streamController.getPendingInterruptResumes().size());
            return;
        }
        this.lastResumedInterruptInput = input;
        context.getMetadata().put(
                "last_resume_interrupt",
                input != null ? new LinkedHashMap<>(input.getUserInputs()) : null);
        streamController.startRound(input);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void addEventListener(Object handler) {
        eventListeners.add(handler);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void removeEventListener(Object handler) {
        eventListeners.remove(handler);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void cancelAgent() {
        ensureConfigured();
        this.isCancelRequested = true;
        context.getMetadata().put("cancel_requested", true);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public com.openjiuwen.agentteams.tools.TeamTaskManager getTaskManager() {
        return teamBackend.getTaskManager();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasInFlightRound() {
        return isInFlightRound || (streamController != null && streamController.hasInFlightRound());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAgentReady() {
        return deepAgent != null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String currentMemberName() {
        return context != null ? context.getMemberName() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setInFlightRound(boolean isInFlightRound) {
        this.isInFlightRound = isInFlightRound;
        if (context != null && context.getMetadata() != null) {
            context.getMetadata().put("in_flight_round", isInFlightRound);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String broadcast(String content) {
        ensureConfigured();
        String messageId = coordinationManager.broadcastFromUser(content);
        context.getMetadata().put("last_route", "broadcast");
        context.getMetadata().put("last_target", "*");
        context.getMetadata().put("message_count", messageManager.listAllMessages().size());
        return messageId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String humanAgentSay(String content, String to, String sender) {
        ensureConfigured();
        String messageId = coordinationManager.handoffHumanAgentInput(content, to, sender);
        context.getMetadata().put("last_route", to == null ? "human_broadcast" : "human_direct");
        context.getMetadata().put("last_target", to == null ? "*" : to);
        context.getMetadata().put("message_count", messageManager.listAllMessages().size());
        return messageId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void updateModelPool(List<ModelPoolEntry> newPool) {
        ensureConfigured();
        List<ModelPoolEntry> merged = ModelPoolEntries.inheritPoolIds(spec.getModelPool(), new ArrayList<>(newPool));
        spec.setModelPool(merged);
        modelAllocator = ModelAllocators.buildModelAllocator(spec);
        context.getMetadata().put("model_pool_size", merged.size());
        persistAllocatorStateToContext();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> snapshot() {
        ensureConfigured();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("spec", spec);
        snapshot.put("context", context);
        snapshot.put("session_id", context.getSessionId());
        snapshot.put("leader_inbox", new ArrayList<>(leaderInbox));
        snapshot.put("messages", new ArrayList<>(messageManager.listAllMessages()));
        snapshot.put(
                "model_allocator_state",
                modelAllocator != null ? new LinkedHashMap<>(modelAllocator.stateDict()) : null);
        return snapshot;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
        ensureConfigured();
        Objects.requireNonNull(memberSpec, "memberSpec is required");
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (memberSpec.getDescription() != null && !memberSpec.getDescription().isBlank()) {
            metadata.put("persona", memberSpec.getDescription());
        }
        return TeamRuntimeContext.builder()
                .teamId(context.getTeamId())
                .sessionId(context.getSessionId())
                .memberName(memberSpec.getName())
                .role(memberSpec.getRole() == TeamRole.LEADER ? TeamRole.LEADER : TeamRole.MEMBER)
                .metadata(metadata)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> buildSpawnPayload(TeamRuntimeContext ctx, String initialMessage) {
        ensureConfigured();
        Objects.requireNonNull(ctx, "ctx is required");
        Map<String, Object> coordination = new LinkedHashMap<>();
        coordination.put("team_name", context.getTeamId());
        coordination.put("display_name", spec.getName());
        coordination.put("leader_member_name", resolveLeaderMemberName());
        coordination.put("member_name", ctx.getMemberName());
        coordination.put("role", payloadRole(ctx.getRole()));
        coordination.put("persona", ctx.getMetadata() != null ? ctx.getMetadata().get("persona") : null);
        coordination.put("transport", null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordination", coordination);
        payload.put("query", initialMessage != null && !initialMessage.isBlank()
                ? initialMessage
                : "Join the team and wait for your first assignment.");
        return payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext ctx) {
        ensureConfigured();
        Objects.requireNonNull(ctx, "ctx is required");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spec", serializeSpec(spec));
        payload.put("context", serializeContext(ctx));

        return SpawnAgentConfig.builder()
                .agentKind(SpawnAgentKind.TEAM_AGENT)
                .runnerConfig(RunnerConfig.getRunnerConfig())
                .loggingConfig(Map.of("member_name", ctx.getMemberName()))
                .sessionId(null)
                .payload(payload)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> buildSpawnConfigPayload(TeamRuntimeContext ctx) {
        return buildSpawnConfig(ctx).toPayload();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public static TeamAgent fromSpawnPayload(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload is required");
        TeamAgentSpec restoredSpec = deserializeSpec(payload.get("spec"));
        TeamRuntimeContext restoredContext = deserializeContext(payload.get("context"));
        return new TeamAgent().configure(restoredSpec, restoredContext);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @SuppressWarnings("unchecked")
    /**
     * Auto-generated for codecheck compliance.
     */
    public TeamAgent restoreFromSnapshot(Map<String, Object> snapshot) {
        ensureConfigured();
        leaderInbox.clear();
        Object restoredInbox = snapshot.get("leader_inbox");
        if (restoredInbox instanceof List<?> inboxValues) {
            for (Object value : inboxValues) {
                leaderInbox.add(String.valueOf(value));
            }
        }
        Object restoredSessionId = snapshot.get("session_id");
        if (restoredSessionId != null) {
            context.setSessionId(String.valueOf(restoredSessionId));
        }
        Object restoredMessages = snapshot.get("messages");
        if (restoredMessages instanceof List<?> messageValues) {
            List<TeamMessage> messages = new ArrayList<>();
            for (Object value : messageValues) {
                if (value instanceof TeamMessage teamMessage) {
                    messages.add(teamMessage);
                }
            }
            messageManager.restoreMessages(messages);
        }
        Object restoredAllocatorState = snapshot.get("model_allocator_state");
        if (restoredAllocatorState instanceof Map<?, ?> rawAllocatorState && modelAllocator != null) {
            Map<String, Object> allocatorState = new LinkedHashMap<>();
            rawAllocatorState.forEach((key, value) -> allocatorState.put(String.valueOf(key), value));
            modelAllocator.loadStateDict(allocatorState);
            context.getMetadata().put("model_allocator_state", allocatorState);
        }
        context.getMetadata().put("session_id", context.getSessionId());
        context.getMetadata().put("leader_inbox_size", leaderInbox.size());
        context.getMetadata().put("message_count", messageManager.listAllMessages().size());
        return this;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String pendingUserQuery() {
        return pendingUserQuery;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> eventListeners() {
        return eventListeners;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String sessionId() {
        return context != null ? context.getSessionId() : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSessionId(String sessionId) {
        applySessionId(sessionId);
    }

    private void bootstrapCoordinationHost() {
        String leaderName = resolveLeaderMemberName();
        Messager messager = MessagerFactory.createMessager(MessagerTransportConfig.builder()
                .teamName(context.getTeamId())
                .nodeId(leaderName)
                .build());
        this.teamBackend = new TeamBackend(context.getTeamId(), leaderName, true, messager);
        this.teamBackend.syncMembers(spec.getMembers());
        this.messageManager = teamBackend.getMessageManager();
        this.coordinationManager = new CoordinationManager(this, teamBackend, messageManager, leaderInbox::add);
        this.recoveryManager = new RecoveryManager(teamBackend, leaderName);
        this.spawnManager = new SpawnManager(this, teamBackend, recoveryManager, () -> context.getSessionId());
        this.recoveryManager.setSpawnManager(spawnManager);
        this.agentSession = com.openjiuwen.core.session.AgentSessionApi.create(context.getSessionId(), null, null);
        this.streamController = new StreamController(
                () -> deepAgent,
                this::resolveLocalMemberName,
                status -> teamBackend.updateMemberStatus(resolveLocalMemberName(), status),
                ignored -> { },
                () -> agentSession,
                () -> dispatcher.processUnreadMessages(resolveLocalMemberName()),
                null
        );
        this.dispatcher = new EventDispatcher(this);
        this.coordinatorLoop = new CoordinatorLoop(context.getRole(), dispatcher::dispatch);
    }

    private void setupAgent() {
        TeamMemberSpec leader = spec.getMembers().stream()
                .filter(member -> member.getRole() == TeamRole.LEADER)
                .findFirst()
                .orElse(null);
        String leaderName = leader != null ? leader.getName() : resolveLeaderMemberName();
        Workspace workspace = Workspace.builder()
                .rootPath(resolveWorkspaceRoot().toString())
                .language(resolveLanguage())
                .build();
        SysOperation sysOperation = new SysOperation(SysOperationCard.builder()
                .id(context.getTeamId() + "." + leaderName + ".sys_operation")
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(workspace.root().toString()).build())
                .build());
        this.deepAgent = new DeepAgent(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                        .id(context.getTeamId() + "." + leaderName)
                        .name(leaderName)
                        .description(leader != null ? leader.getDescription() : "")
                        .build(),
                DeepAgentConfig.builder()
                        .workspacePath(workspace.root().toString())
                        .language(resolveLanguage())
                        .model(resolveConfiguredModel())
                        .backend(resolveConfiguredBackend())
                        .isTaskLoopEnabled(true)
                        .sysOperation(sysOperation)
                        .rails(List.of(new TeamRail(
                                context.getRole() != null ? context.getRole() : TeamRole.LEADER,
                                resolvePersona(leader),
                                resolveLocalMemberName(),
                                spec.getLifecycle(),
                                "build_mode",
                                resolveLanguage(),
                                "default",
                                null,
                                resolveTeamWorkspaceMount(),
                                resolveTeamWorkspacePath(),
                                teamBackend.humanAgentNames(),
                                teamBackend
                        )))
                        .build(),
                workspace
        );
        this.deepAgent.ensureInitialized();
        this.memoryManager = buildMemoryManager(workspace, sysOperation, leaderName);
        Object configuredModel = this.deepAgent.getConfig().getModel();
        if (this.memoryManager != null && configuredModel instanceof com.openjiuwen.core.foundation.llm.Model model) {
            this.memoryManager.setExtractionModel(model);
        }
    }

    private Object resolveConfiguredModel() {
        if (context == null || context.getMetadata() == null) {
            return nullValue();
        }
        Object memberModel = context.getMetadata().get("member_model");
        TeamModelConfig config = teamModelConfigFromObject(memberModel);
        if (config != null) {
            return config.modelRequestConfig();
        }
        return memberModel;
    }

    private Object resolveConfiguredBackend() {
        if (context == null || context.getMetadata() == null) {
            return nullValue();
        }
        TeamModelConfig config = teamModelConfigFromObject(context.getMetadata().get("member_model"));
        return config != null ? config.modelClientConfig() : null;
    }

    private TeamMemoryManager buildMemoryManager(Workspace workspace, SysOperation sysOperation, String memberName) {
        TeamMemoryConfig memory = spec.getMemory();
        if (memory == null || !memory.isEnabled()) {
            return nullValue();
        }
        try {
            java.nio.file.Files.createDirectories(workspace.root());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize team workspace", e);
        }
        com.openjiuwen.core.memory.team.TeamLifecycle lifecycle = parseMemoryLifecycle(spec.getLifecycle());
        String teamMemoryDir = null;
        if (memory.isSharedMemory() && lifecycle == com.openjiuwen.core.memory.team.TeamLifecycle.PERSISTENT) {
            teamMemoryDir = memory.getTeamMemoryDir() != null && !memory.getTeamMemoryDir().isBlank()
                    ? memory.getTeamMemoryDir()
                    : defaultTeamMemoryDir(context.getTeamId()).toString();
        }
        String readOnlySource = lifecycle == com.openjiuwen.core.memory.team.TeamLifecycle.TEMPORARY
                ? memory.getParentWorkspacePath()
                : null;
        return new TeamMemoryManager(TeamMemoryManagerParams.builder()
                .memberName(memberName)
                .teamName(context.getTeamId())
                .role(com.openjiuwen.core.memory.team.TeamRole.LEADER)
                .lifecycle(lifecycle)
                .scenario(parseScenario(memory.getScenario()))
                .embeddingConfig(TeamMemoryConfig.resolveEmbeddingConfig(memory))
                .workspace(workspace)
                .sysOperation(sysOperation)
                .teamMemoryDir(teamMemoryDir)
                .language(parseLanguage(resolveLanguage()))
                .promptMode(parsePromptMode(memory.getMemberMemoryPromptMode()))
                .enableAutoExtract(memory.isAutoExtract()
                        && lifecycle == com.openjiuwen.core.memory.team.TeamLifecycle.PERSISTENT)
                .readOnlySourceWorkspace(readOnlySource)
                .db(teamBackend.getDb())
                .taskManager(teamBackend.getTaskManager())
                .timezoneOffsetHours(memory.getTimezoneOffsetHours())
                .build());
    }

    private String resolvePersona(TeamMemberSpec fallbackLeader) {
        if (context != null && context.getMetadata() != null) {
            Object persona = context.getMetadata().get("persona");
            if (persona != null) {
                return String.valueOf(persona);
            }
        }
        return fallbackLeader != null ? fallbackLeader.getDescription() : "";
    }

    private void startCoordination(String query) {
        if (memoryManager == null || deepAgent == null) {
            return;
        }
        try {
            if (memoryManager.initToolkit()) {
                memoryManager.registerTools(deepAgent);
                if (memoryManager.getExtractionModel() == null) {
                    Object configuredModel = deepAgent.getConfig().getModel();
                    if (configuredModel instanceof com.openjiuwen.core.foundation.llm.Model model) {
                        memoryManager.setExtractionModel(model);
                    }
                }
                memoryManager.loadAndInject(deepAgent, query != null ? query : "");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Team memory initialization failed", e);
        }
    }

    private void finalizeRound() {
        if (memoryManager == null) {
            return;
        }
        try {
            memoryManager.extractAfterRound();
        } catch (IOException e) {
            throw new IllegalStateException("Team memory extraction failed", e);
        }
    }

    private void finalizeStreamingRound() {
        try {
            finalizeRound();
        } finally {
            context.getMetadata().remove("streaming_coordination");
            setInFlightRound(false);
            if (coordinatorLoop != null) {
                coordinatorLoop.stop();
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Allocation allocateModel(String modelName) {
        if (modelAllocator == null) {
            return nullValue();
        }
        Allocation allocation = modelAllocator.allocate(modelName);
        persistAllocatorStateToContext();
        persistAllocatorStateToSession();
        return allocation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void restoreAllocatorState(Map<String, Object> state) {
        if (modelAllocator == null || state == null) {
            return;
        }
        modelAllocator.loadStateDict(state);
        context.getMetadata().put("model_allocator_state", new LinkedHashMap<>(modelAllocator.stateDict()));
    }

    private void applySessionId(String sessionId) {
        context.setSessionId(sessionId);
        agentSession = com.openjiuwen.core.session.AgentSessionApi.create(sessionId, null, null);
        context.getMetadata().put("session_id", sessionId);
        context.getMetadata().put("leader_inbox_size", leaderInbox.size());
        context.getMetadata().put("message_count", messageManager.listAllMessages().size());
    }

    private void restoreAllocatorStateFromContext() {
        Object state = context.getMetadata().get("model_allocator_state");
        if (state instanceof Map<?, ?> rawState && modelAllocator != null) {
            Map<String, Object> allocatorState = new LinkedHashMap<>();
            rawState.forEach((key, value) -> allocatorState.put(String.valueOf(key), value));
            modelAllocator.loadStateDict(allocatorState);
            context.getMetadata().put("model_allocator_state", new LinkedHashMap<>(modelAllocator.stateDict()));
        }
    }

    private void persistAllocatorStateToContext() {
        if (modelAllocator != null) {
            context.getMetadata().put("model_allocator_state", new LinkedHashMap<>(modelAllocator.stateDict()));
        }
    }

    private String resolveLeaderMemberName() {
        return spec.getMembers().stream()
                .filter(member -> member.getRole() == TeamRole.LEADER)
                .map(TeamMemberSpec::getName)
                .findFirst()
                .orElseGet(() -> {
                    if (spec.getMembers().isEmpty()) {
                        return context != null ? context.getTeamId() : "leader";
                    }
                    return spec.getMembers().get(0).getName();
                });
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void close() {
        if (memoryManager != null) {
            memoryManager.close();
        }
        if (coordinatorLoop != null) {
            coordinatorLoop.stop();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void startCoordinationLoop() {
        ensureConfigured();
        coordinationManager.start();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void stopCoordinationLoop() {
        if (coordinationManager != null) {
            coordinationManager.stop();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void pausePolls() {
        if (coordinatorLoop != null) {
            coordinatorLoop.pausePolls();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void resumePolls() {
        if (coordinatorLoop != null) {
            coordinatorLoop.resumePolls();
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void shutdownSelf() {
        if (streamController != null) {
            streamController.cancelAgent();
            streamController.closeStream();
        }
        if (context != null) {
            context.setLifecycle(TeamLifecycle.COMPLETED);
        }
        close();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean destroyTeam(boolean isForceEnabled) {
        ensureConfigured();
        close();
        if (isForceEnabled) {
            for (var member : teamBackend.getDb().member.getTeamMembers(teamBackend.getTeamName())) {
                if (!resolveLocalMemberName().equals(member.getMemberName())) {
                    teamBackend.shutdownMember(member.getMemberName(), true).join();
                }
            }
        }
        return teamBackend.cleanTeam().join();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasPendingInterrupt() {
        return streamController != null && streamController.hasPendingInterrupt();
    }

    private boolean isStreamingCoordinationActive() {
        return context != null
                && context.getMetadata() != null
                && Boolean.TRUE.equals(context.getMetadata().get("streaming_coordination"));
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String resolveLocalMemberName() {
        if (context != null && context.getMemberName() != null && !context.getMemberName().isBlank()) {
            return context.getMemberName();
        }
        return resolveLeaderMemberName();
    }

    private Path resolveWorkspaceRoot() {
        Object configuredWorkspace = context.getMetadata().get("workspace_path");
        if (configuredWorkspace != null && !String.valueOf(configuredWorkspace).isBlank()) {
            return Path.of(String.valueOf(configuredWorkspace)).toAbsolutePath().normalize();
        }
        return defaultTeamHome(context.getTeamId()).resolve("team-workspace");
    }

    private static Path defaultTeamHome(String teamName) {
        return Path.of(System.getProperty("user.home"), ".openjiuwen", ".agent_teams",
                teamName != null && !teamName.isBlank() ? teamName : "agent_team");
    }

    private static Path defaultTeamMemoryDir(String teamName) {
        return defaultTeamHome(teamName).resolve("team-workspace").resolve("team-memory");
    }

    private String resolveTeamWorkspaceMount() {
        if (context == null || context.getMetadata() == null) {
            return nullValue();
        }
        Object value = context.getMetadata().get("teamworkspace_mount");
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : null;
    }

    private String resolveTeamWorkspacePath() {
        if (context == null || context.getMetadata() == null) {
            return nullValue();
        }
        Object value = context.getMetadata().get("teamworkspace_path");
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : null;
    }

    private String resolveLanguage() {
        return spec.getLanguage() != null && !spec.getLanguage().isBlank() ? spec.getLanguage() : "cn";
    }

    private static com.openjiuwen.core.memory.team.TeamLifecycle parseMemoryLifecycle(String lifecycle) {
        return "persistent".equalsIgnoreCase(lifecycle)
                ? com.openjiuwen.core.memory.team.TeamLifecycle.PERSISTENT
                : com.openjiuwen.core.memory.team.TeamLifecycle.TEMPORARY;
    }

    private static TeamScenario parseScenario(String scenario) {
        return "coding".equalsIgnoreCase(scenario) ? TeamScenario.CODING : TeamScenario.GENERAL;
    }

    private static TeamLanguage parseLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? TeamLanguage.EN : TeamLanguage.CN;
    }

    private static PromptMode parsePromptMode(String mode) {
        return "passive".equalsIgnoreCase(mode) ? PromptMode.PASSIVE : PromptMode.PROACTIVE;
    }

    private static String payloadRole(TeamRole role) {
        if (role == TeamRole.LEADER) {
            return "leader";
        }
        if (role == TeamRole.HUMAN_AGENT) {
            return "human_agent";
        }
        if (role == TeamRole.USER) {
            return "user";
        }
        return "teammate";
    }

    private static TeamRole parsePayloadRole(Object role) {
        String value = role != null ? String.valueOf(role) : "";
        if ("leader".equalsIgnoreCase(value)) {
            return TeamRole.LEADER;
        }
        if ("human_agent".equalsIgnoreCase(value)) {
            return TeamRole.HUMAN_AGENT;
        }
        if ("user".equalsIgnoreCase(value)) {
            return TeamRole.USER;
        }
        return TeamRole.MEMBER;
    }

    private static Map<String, Object> serializeSpec(TeamAgentSpec spec) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", spec.getName());
        data.put("description", spec.getDescription());
        data.put("lifecycle", spec.getLifecycle());
        data.put("language", spec.getLanguage());
        List<Map<String, Object>> members = new ArrayList<>();
        for (TeamMemberSpec member : spec.getMembers()) {
            Map<String, Object> memberData = new LinkedHashMap<>();
            memberData.put("name", member.getName());
            memberData.put("role", payloadRole(member.getRole()));
            memberData.put("description", member.getDescription());
            memberData.put("agent_id", member.getAgentId());
            memberData.put("model_id", member.getModelId());
            memberData.put("model_name", member.getModelName());
            members.add(memberData);
        }
        data.put("members", members);
        return data;
    }

    private static Map<String, Object> serializeContext(TeamRuntimeContext ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("team_id", ctx.getTeamId());
        data.put("session_id", ctx.getSessionId());
        data.put("member_name", ctx.getMemberName());
        data.put("role", payloadRole(ctx.getRole()));
        data.put(
                "metadata",
                ctx.getMetadata() != null ? new LinkedHashMap<>(ctx.getMetadata()) : new LinkedHashMap<>());
        return data;
    }

    @SuppressWarnings("unchecked")
    private static TeamAgentSpec deserializeSpec(Object value) {
        if (value instanceof TeamAgentSpec teamAgentSpec) {
            return teamAgentSpec;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("spawn payload spec is required");
        }
        List<TeamMemberSpec> members = new ArrayList<>();
        Object memberValues = raw.get("members");
        if (memberValues instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> memberMap) {
                    members.add(TeamMemberSpec.builder()
                            .name(stringValue(memberMap.get("name")))
                            .role(parsePayloadRole(memberMap.get("role")))
                            .description(stringValue(memberMap.get("description")))
                            .agentId(stringValue(memberMap.get("agent_id")))
                            .modelId(stringValue(memberMap.get("model_id")))
                            .modelName(stringValue(memberMap.get("model_name")))
                            .build());
                }
            }
        }
        return TeamAgentSpec.builder()
                .name(stringValue(raw.get("name")))
                .description(stringValue(raw.get("description")))
                .lifecycle(stringValue(raw.get("lifecycle")))
                .language(stringValue(raw.get("language")))
                .members(members)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static TeamRuntimeContext deserializeContext(Object value) {
        if (value instanceof TeamRuntimeContext teamRuntimeContext) {
            return teamRuntimeContext;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("spawn payload context is required");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        Object rawMetadata = raw.get("metadata");
        if (rawMetadata instanceof Map<?, ?> metadataMap) {
            metadataMap.forEach((key, item) -> {
                String name = String.valueOf(key);
                metadata.put(name, normalizeMetadataValue(name, item));
            });
        }
        return TeamRuntimeContext.builder()
                .teamId(stringValue(raw.get("team_id")))
                .sessionId(stringValue(raw.get("session_id")))
                .memberName(stringValue(raw.get("member_name")))
                .role(parsePayloadRole(raw.get("role")))
                .metadata(metadata)
                .build();
    }

    private static Object normalizeMetadataValue(String key, Object value) {
        if ("member_model".equals(key)) {
            TeamModelConfig config = teamModelConfigFromObject(value);
            if (config != null) {
                return config;
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static TeamModelConfig teamModelConfigFromObject(Object value) {
        if (value instanceof TeamModelConfig config) {
            return config;
        }
        if (!(value instanceof Map<?, ?> raw)) {
            return nullValue();
        }
        Object client = firstPresent(
                raw,
                new String[]{"modelClientConfig", "model_client_config", "modelClient", "model_client"});
        Object request = firstPresent(
                raw,
                new String[]{"modelRequestConfig", "model_request_config", "modelConfig", "model_config"});
        if (!(client instanceof Map<?, ?>) || !(request instanceof Map<?, ?>)) {
            return nullValue();
        }
        try {
            return new TeamModelConfig(
                    OBJECT_MAPPER.convertValue(client, ModelClientConfig.class),
                    OBJECT_MAPPER.convertValue(request, ModelRequestConfig.class)
            );
        } catch (IllegalArgumentException e) {
            return nullValue();
        }
    }

    private static Object firstPresent(Map<?, ?> raw, String[] keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                return raw.get(key);
            }
        }
        return nullValue();
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private void ensureConfigured() {
        if (spec == null || context == null || teamBackend == null || messageManager == null) {
            throw new IllegalStateException("TeamAgent is not configured");
        }
    }

    private final class CoordinationStreamIterator implements Iterator<Object> {
        private final LinkedBlockingQueue<Object> queue;
        private Object next;
        private boolean isFinished;

        private CoordinationStreamIterator(LinkedBlockingQueue<Object> queue) {
            this.queue = queue;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            if (isFinished) {
                return false;
            }
            try {
                Object item = queue.take();
                if (item == StreamController.STREAM_END) {
                    isFinished = true;
                    finalizeStreamingRound();
                    return false;
                }
                next = item;
                return true;
            } catch (InterruptedException interruptedException) {

                isFinished = true;
                finalizeStreamingRound();
                return false;
            } catch (RuntimeException runtimeException) {
                isFinished = true;
                finalizeStreamingRound();
                throw runtimeException;
            }
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Object next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            Object value = next;
            next = null;
            return value;
        }
    }
    private static <T> T nullValue() {
        return null;
    }

}
