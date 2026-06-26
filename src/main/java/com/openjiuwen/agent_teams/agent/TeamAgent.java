/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredMemberRuntime;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.WorkspaceSpec;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.EventListener;
import com.openjiuwen.agent_teams.agent.coordination.handlers.MessageHandler;
import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.interaction.GodViewMessage;
import com.openjiuwen.agent_teams.interaction.InteractPayload;
import com.openjiuwen.agent_teams.interaction.InteractionRouter;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.schema.TeamOutputSchema;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Unified team-agent facade that wires configuration, streaming, spawning,
 * recovery, session, and coordination managers.
 *
 * <p>Mirrors Python's {@code TeamAgent} in
 * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
 */
public class TeamAgent implements CoordinationKernel.KernelHost {

    private static final String UNKNOWN_MEMBER = "?";

    private final AgentCard card;
    private final AgentConfigurator configurator;
    private final TeamAgentState state;
    private final SpawnManager spawnManager;
    private final RecoveryManager recoveryManager;
    private final SessionManager sessionManager;
    private final StreamController streamController;
    private final CoordinationKernel coordination;

    public TeamAgent(AgentCard card) {
        this.card = Objects.requireNonNull(card, "card");
        this.configurator = new AgentConfigurator(card);
        this.state = new TeamAgentState();
        this.spawnManager = new SpawnManager(state, configurator, () -> this);
        this.recoveryManager = new RecoveryManager(configurator, spawnManager);
        this.sessionManager = new SessionManager(state, configurator, recoveryManager);
        this.streamController = new StreamController(
                configurator::getBlueprint,
                state,
                configurator.getResources(),
                this::updateStatus,
                this::updateExecution,
                this::wakeMailboxIfInterruptCleared,
                this::requestCompletionPoll
        );
        this.coordination = new CoordinationKernel(this);
    }

    public AgentCard getCard() {
        return card;
    }

    @Override
    public TeamAgentBlueprint getBlueprint() {
        return toKernelBlueprint(configurator.getBlueprint());
    }

    public TeamAgentState getState() {
        return state;
    }

    @Override
    public TeamInfra getInfra() {
        return configurator.getInfra();
    }

    @Override
    public PrivateAgentResources getResources() {
        return configurator.getResources();
    }

    public MemberRuntime getHarness() {
        return configurator.getHarness();
    }

    public TeamAgentSpec getSpec() {
        return configurator.getSpec();
    }

    public TeamRuntimeContext getRuntimeContext() {
        return configurator.getCtx();
    }

    public CoordinationKernel getCoordination() {
        return coordination;
    }

    public Object getCoordinationLoop() {
        return coordination.getEventBus();
    }

    @Override
    public TeamRole getRole() {
        return configurator.getRole();
    }

    public String getLifecycle() {
        return configurator.getLifecycle();
    }

    public TeamSpec getTeamSpec() {
        return configurator.getTeamSpec();
    }

    @Override
    public String getMemberName() {
        return configurator.getMemberName();
    }

    public Object getMessageManager() {
        return configurator.getMessageManager();
    }

    public Object getTaskManager() {
        return configurator.getTaskManager();
    }

    public ConfiguredTeamBackend getTeamBackend() {
        return configurator.getTeamBackend();
    }

    public String getSessionId() {
        return AgentTeamsContext.getSessionId();
    }

    @Override
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public RecoveryManager getRecoveryManager() {
        return recoveryManager;
    }

    @Override
    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    @Override
    public StreamController getStreamController() {
        return streamController;
    }

    public TeamMember getTeamMember() {
        return state.getTeamMember();
    }

    public CompletionStage<Boolean> isShutdownRequested() {
        TeamMember member = state.getTeamMember();
        if (member == null) {
            return CompletableFuture.completedFuture(false);
        }
        return member.status().thenApply(status ->
                status == MemberStatus.SHUTDOWN_REQUESTED || status == MemberStatus.SHUTDOWN);
    }

    @Override
    public String getPendingUserQuery() {
        return state.getPendingUserQuery();
    }

    @Override
    public String getTeamName() {
        return configurator.getTeamName();
    }

    @Override
    public CompletionStage<Void> updateStatus(MemberStatus status) {
        TeamMember member = state.getTeamMember();
        return member == null ? CompletableFuture.completedFuture(null)
                : member.updateStatus(status).thenApply(ignored -> null);
    }

    public void persistAllocatorState() {
        recoveryManager.persistAllocatorState(sessionManager.getTeamSession());
    }

    public void addEventListener(EventListener handler) {
        if (handler != null) {
            state.getEventListeners().add(handler);
        }
    }

    public void removeEventListener(EventListener handler) {
        state.getEventListeners().remove(handler);
    }

    @Override
    public List<EventListener> getEventListeners() {
        List<EventListener> listeners = new ArrayList<>();
        for (Object listener : state.getEventListeners()) {
            if (listener instanceof EventListener eventListener) {
                listeners.add(eventListener);
            }
        }
        return listeners;
    }

    public CompletionStage<Object> lookupHumanAgentRuntime(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof MessageHandler.TeamBackendView humanBackend)) {
            return CompletableFuture.completedFuture(null);
        }
        return humanBackend.isHumanAgent(memberName)
                .thenApply(isHuman -> Boolean.TRUE.equals(isHuman)
                        ? spawnManager.lookupInprocessAgent(memberName)
                        : null);
    }

    public Object lookupBridgeAgentRuntime(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof BridgeAgentBackend bridgeBackend)
                || !bridgeBackend.isBridgeAgent(memberName)) {
            return null;
        }
        return spawnManager.lookupInprocessAgent(memberName);
    }

    @Override
    public boolean isAgentReady() {
        return configurator.getHarness() != null;
    }

    @Override
    public boolean isAgentRunning() {
        return streamController.isAgentRunning();
    }

    @Override
    public boolean hasInFlightRound() {
        return streamController.hasInFlightRound();
    }

    @Override
    public boolean hasPendingInterrupt() {
        return streamController.hasPendingInterrupt();
    }

    @Override
    public CompletionStage<Void> deliverInput(Object content, boolean useSteer) {
        if (isAgentRunning()) {
            return useSteer ? steer(String.valueOf(content)) : followUp(String.valueOf(content));
        }
        if (hasInFlightRound()) {
            streamController.getPendingInputs().add(content);
            return CompletableFuture.completedFuture(null);
        }
        return startAgent(content);
    }

    public CompletionStage<Void> deliverInput(Object content) {
        return deliverInput(content, true);
    }

    public CompletionStage<Void> startAgent(Object content) {
        return streamController.startRound(content);
    }

    public CompletionStage<Void> followUp(String content) {
        return streamController.followUp(content);
    }

    @Override
    public CompletionStage<Void> cancelAgent() {
        return streamController.cancelAgent();
    }

    public CompletionStage<Boolean> destroyTeam(boolean force) {
        String sessionIdSnapshot = getSessionId();
        return cancelAgent()
                .exceptionally(error -> null)
                .thenCompose(ignored -> coordination.stop().exceptionally(error -> null))
                .thenCompose(ignored -> removeSelfFromPool(sessionIdSnapshot))
                .thenCompose(ignored -> {
                    ConfiguredTeamBackend backend = configurator.getTeamBackend();
                    if (!(backend instanceof TeamCleaner cleaner)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return cleaner.forceCleanTeam(force);
                });
    }

    public CompletionStage<Boolean> destroyTeam() {
        return destroyTeam(true);
    }

    public CompletionStage<Void> steer(String content) {
        return streamController.steer(content);
    }

    @Override
    public CompletionStage<Void> resumeInterrupt(Object userInput) {
        if (!streamController.isValidInterruptResume(userInput)) {
            return CompletableFuture.completedFuture(null);
        }
        if (hasInFlightRound()) {
            streamController.getPendingInterruptResumes().add(userInput);
            return CompletableFuture.completedFuture(null);
        }
        return startAgent(userInput);
    }

    public TeamAgent configure(
            TeamAgentSpec spec,
            TeamRuntimeContext context,
            MemberRuntime memberRuntime
    ) {
        setupInfra(spec, context);
        setupAgent(spec, context, memberRuntime);
        return this;
    }

    public TeamAgent configure(TeamAgentSpec spec, TeamRuntimeContext context) {
        return configure(spec, context, null);
    }

    public void setupInfra(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        configurator.setupInfra(
                spec,
                ctx,
                (TeammateCreatedCallback) this::onTeammateCreated,
                (LifecycleCallback) this::markTeamCleaned,
                (LifecycleCallback) this::markTeamBuilt
        );
    }

    public void setupAgent(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            MemberRuntime memberRuntime
    ) {
        configurator.setupAgent(spec, ctx, memberRuntime);
        if (ctx.getMemberName() != null) {
            state.setTeamMember(createMemberHandle(ctx.getMemberName()));
        }
        coordination.setup(ctx.getRole());
        registerTeamCompletionCallbacks();
    }

    public DeepAgentSpec resolveAgentSpec(TeamAgentSpec spec, TeamRole role, String memberName) {
        return AgentConfigurator.resolveAgentSpec(spec, role, memberName);
    }

    public void updateModelPool(List<?> newPool) {
        configurator.updateModelPool(newPool);
    }

    public void attachModelAllocator(AgentConfigurator.ModelAllocator allocator) {
        configurator.attachModelAllocator(allocator, null);
    }

    public void attachModelAllocator(
            AgentConfigurator.ModelAllocator allocator,
            AgentConfigurator.Allocation leaderAllocation
    ) {
        configurator.attachModelAllocator(allocator, leaderAllocation);
    }

    public void restoreAllocatorState(Map<String, Object> allocatorState) {
        configurator.restoreAllocatorState(allocatorState);
    }

    public CompletionStage<DeliverResult> broadcast(String content) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof InteractiveGateway gateway)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "TeamAgent.broadcast requires a configured team backend"));
        }
        return gateway.broadcast(content);
    }

    public CompletionStage<DeliverResult> humanAgentSay(String content, String to, String sender) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof InteractiveGateway gateway)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "TeamAgent.humanAgentSay requires a configured team backend"));
        }
        return gateway.humanAgentSay(content, to, sender);
    }

    public CompletionStage<List<Object>> stream(
            Object inputs,
            Object session,
            Object streamModes
    ) {
        return runInputRound(inputs, session);
    }

    public CompletionStage<List<Object>> stream(Object inputs) {
        return stream(inputs, null, null);
    }

    public CompletionStage<Object> invoke(Object inputs, Object session) {
        return runInputRound(inputs, session)
                .thenApply(chunks -> chunks.isEmpty() ? null : chunks.get(chunks.size() - 1));
    }

    public CompletionStage<Object> invoke(Object inputs) {
        return invoke(inputs, null);
    }

    private CompletionStage<List<Object>> runInputRound(Object inputs, Object session) {
        streamController.clearStreamQueue();
        String rawQuery = rawQuery(inputs);
        state.setPendingUserQuery(rawQuery);
        List<InteractPayload> routedPayloads = initialLeaderRoutePayloads(rawQuery);
        CompletionStage<Void> roundStage = coordination.start(session)
                .thenCompose(ignored -> {
                    if (routedPayloads != null) {
                        return dispatchInitialLeaderRoute(routedPayloads);
                    }
                    return coordination.enqueueUserInput(inputs)
                            .thenCompose(next -> coordination.enqueueMailboxAfterFirstIteration());
                });
        return roundStage.handle((ignored, throwable) -> throwable)
                .thenCompose(throwable -> coordination.finalizeRound()
                        .handle((ignored, finalizeThrowable) -> {
                            if (throwable != null) {
                                throw new CompletionException(unwrapCompletion(throwable));
                            }
                            if (finalizeThrowable != null) {
                                throw new CompletionException(unwrapCompletion(finalizeThrowable));
                            }
                            return drainStreamQueueSnapshot();
                        }));
    }

    protected CompletionStage<Void> dispatchInitialLeaderRoute(List<InteractPayload> payloads) {
        return TeamRuntimeManager.dispatchPayloads(new InitialRouteRuntimeAdapter(this), payloads)
                .thenCompose(result -> {
                    if (result.ok()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return emitInteractFailed(result.reason()).thenRun(streamController::closeStream);
                });
    }

    protected CompletionStage<Void> emitInteractFailed(String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", "team.interact.failed");
        payload.put("reason", reason);
        streamController.getRawStreamQueue().offer(new TeamOutputSchema(
                "message",
                0,
                payload,
                getMemberName(),
                getRole()
        ));
        return CompletableFuture.completedFuture(null);
    }

    protected List<InteractPayload> initialLeaderRoutePayloads(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty() || getRole() != TeamRole.LEADER || getTeamBackend() == null) {
            return null;
        }
        List<InteractPayload> parsed = InteractionRouter.parseInteractStr(rawQuery);
        if (parsed.isEmpty()) {
            return null;
        }
        boolean routed = parsed.stream().anyMatch(payload -> !(payload instanceof GodViewMessage));
        return routed ? parsed : null;
    }

    public CompletionStage<Void> interact(String message) {
        return coordination.enqueueUserInput(message);
    }

    public CompletionStage<Void> startCoordination(Object session) {
        return coordination.start(session);
    }

    public CompletionStage<Void> pauseCoordination() {
        return coordination.pause();
    }

    public CompletionStage<Void> stopCoordination() {
        return coordination.stop();
    }

    public void closeStream() {
        coordination.closeStream();
    }

    public List<String> subscribedTopics() {
        return coordination.getSubscribedTopics();
    }

    @Override
    public CompletionStage<Void> shutdownSelf() {
        String memberName = memberNameOrUnknown();
        return streamController.cooperativeCancel()
                .thenCompose(ignored -> {
                    TeamMember member = state.getTeamMember();
                    return member == null ? CompletableFuture.completedFuture(null)
                            : member.updateStatus(MemberStatus.SHUTDOWN)
                            .exceptionally(error -> false)
                            .thenApply(statusUpdated -> null);
                })
                .thenRun(() -> streamController.closeStream());
    }

    @Override
    public CompletionStage<Void> concludeCompletedRound(int memberCount, int taskCount) {
        streamController.emitCompletionAndClose(memberCount, taskCount);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> runOneRound(Object initialMessage) {
        return streamController.startRound(initialMessage);
    }

    public Map<String, Object> buildSpawnPayload(
            TeamRuntimeContext ctx,
            String initialMessage
    ) {
        return configurator.buildSpawnPayload(ctx, initialMessage);
    }

    public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
        return configurator.buildMemberContext(memberSpec);
    }

    public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext ctx) {
        return configurator.buildSpawnConfig(ctx);
    }

    public static CompletionStage<TeamAgent> fromSpawnPayload(Map<String, Object> payload) {
        TeamAgentSpec spec = parseSpec(payload.get("spec"));
        TeamRuntimeContext context = parseContext(payload.get("context"));
        DeepAgentSpec agentSpec = spec.getAgents().getOrDefault(
                context.getRole().value(),
                spec.getAgents().get("leader")
        );
        String teamName = context.getTeamSpec() == null || context.getTeamSpec().getTeamName() == null
                ? spec.getTeamName()
                : context.getTeamSpec().getTeamName();
        String cardId = context.getMemberName() == null ? "unknown" : teamName + "_" + context.getMemberName();
        AgentCard card = new AgentCard(
                cardId,
                context.getMemberName() == null ? "unknown" : context.getMemberName(),
                context.getPersona() == null || context.getPersona().isEmpty()
                        ? "Teammate"
                        : "Teammate: " + context.getPersona()
        );
        if (agentSpec != null && agentSpec.getSystemPrompt() != null) {
            card.setDescription(agentSpec.getSystemPrompt());
        }
        TeamAgent agent = new TeamAgent(card);
        agent.configure(spec, context);
        return CompletableFuture.completedFuture(agent);
    }

    public CompletionStage<Void> onTeammateCreated(String teammateId) {
        return spawnManager.buildContextFromDb(teammateId)
                .thenCompose(ctx -> {
                    if (ctx == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    ConfiguredTeamBackend backend = configurator.getTeamBackend();
                    if (!(backend instanceof SpawnManager.TeamBackendView teamBackend)) {
                        return spawnManager.spawnTeammate(ctx, null, getSessionId(), new SpawnManager.SpawnOptions(30, 50))
                                .thenApply(ignored -> null);
                    }
                    return teamBackend.getMember(teammateId)
                            .thenCompose(rawMember -> {
                                SpawnManager.MemberRow member = rawMember instanceof SpawnManager.MemberRow row
                                        ? row
                                        : null;
                                return spawnManager.spawnTeammate(
                                        ctx,
                                        member == null ? null : member.prompt(),
                                        getSessionId(),
                                        new SpawnManager.SpawnOptions(30, 50)
                                );
                            })
                            .thenApply(ignored -> null);
                });
    }

    public CompletionStage<Boolean> autoStartMember(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof AutoStartBackend autoStartBackend)
                || !backend.isLeader()) {
            return CompletableFuture.completedFuture(false);
        }
        return autoStartBackend.startupMember(memberName, this::onTeammateCreated)
                .exceptionally(error -> false);
    }

    public CompletionStage<List<String>> autoStartAll() {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof AutoStartBackend autoStartBackend)
                || !backend.isLeader()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return autoStartBackend.startup(this::onTeammateCreated)
                .exceptionally(error -> List.of());
    }

    public CompletionStage<Void> resumeForNewSession(SessionManager.AgentTeamSessionView session) {
        return sessionManager.resumeForNewSession(session);
    }

    public CompletionStage<Void> recoverForExistingSession(SessionManager.AgentTeamSessionView session) {
        return coordination.stop().thenCompose(ignored -> sessionManager.recoverForExistingSession(session));
    }

    @Override
    public CompletionStage<Void> recoverTeam() {
        return recoveryManager.recoverTeam().thenApply(ignored -> null);
    }

    public void persistSessionManifest(SessionManager.AgentTeamSessionView session) {
        recoveryManager.persistLeaderConfig(session);
    }

    public static TeamAgent recoverFromSession(
            SessionManager.AgentTeamSessionView session,
            String teamName,
            TeamAgentSpec runtimeSpec
    ) {
        Map<String, Object> bucket = TeamRuntimeMetadata.readTeamNamespace(session, teamName);
        if (bucket == null) {
            throw new IllegalArgumentException("No persisted state for team '" + teamName + "' in session");
        }
        Object specData = bucket.get("spec");
        if (specData == null) {
            throw new IllegalArgumentException("No leader spec found for team '" + teamName + "'");
        }
        TeamAgentSpec spec = parseSpec(specData);
        if (runtimeSpec != null && runtimeSpec.getAgentCustomizer() != null) {
            spec.setAgentCustomizer(runtimeSpec.getAgentCustomizer());
        }
        TeamRuntimeContext context = parseContext(bucket.get("context"));
        DeepAgentSpec agentSpec = spec.getAgents().getOrDefault(
                context.getRole().value(),
                spec.getAgents().get("leader")
        );
        AgentCard card = new AgentCard(
                context.getMemberName() == null ? "leader" : teamName + "_" + context.getMemberName(),
                context.getMemberName() == null ? "leader" : context.getMemberName(),
                agentSpec == null || agentSpec.getSystemPrompt() == null ? "" : agentSpec.getSystemPrompt()
        );
        TeamAgent agent = new TeamAgent(card);
        agent.configure(spec, context);
        Object allocatorState = bucket.get("model_allocator_state");
        if (allocatorState instanceof Map<?, ?> stateMap) {
            agent.restoreAllocatorState(stringObjectMap(stateMap));
        }
        AgentTeamsContext.setSessionId(session.getSessionId());
        return agent;
    }

    public static TeamAgent recoverFromSession(
            SessionManager.AgentTeamSessionView session,
            String teamName
    ) {
        return recoverFromSession(session, teamName, null);
    }

    @Override
    public CompletionStage<Void> markLiveTeammates(MemberStatus status) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (backend == null || !(backend.getMemberStore() instanceof RecoveryManager.MemberRegistry registry)) {
            return CompletableFuture.completedFuture(null);
        }
        String teamName = configurator.getTeamName();
        String self = configurator.getMemberName();
        EnumSet<MemberStatus> terminal = EnumSet.of(MemberStatus.SHUTDOWN, MemberStatus.STOPPED);
        return registry.listMembers()
                .thenCompose(members -> updateLiveTeammates(registry, members, 0, self, teamName, status, terminal));
    }

    @Override
    public void persistTeamLifecycle(String lifecycle) {
        SessionManager.AgentTeamSessionView teamSession = sessionManager.getTeamSession();
        String teamName = configurator.getTeamName();
        if (teamSession == null || teamName == null) {
            return;
        }
        TeamRuntimeMetadata.mergeTeamNamespace(teamSession, teamName, Map.of("lifecycle", lifecycle));
    }

    @Override
    public Object getHarnessModel() {
        MemberRuntime harness = configurator.getHarness();
        if (harness instanceof ConfiguredMemberRuntime runtime) {
            return runtime.getAgentSpec().getModel();
        }
        return null;
    }

    CompletionStage<Void> updateExecution(ExecutionStatus status) {
        TeamMember member = state.getTeamMember();
        return member == null ? CompletableFuture.completedFuture(null)
                : member.updateExecutionStatus(status).thenApply(ignored -> null);
    }

    private void registerTeamCompletionCallbacks() {
        MemberRuntime harness = configurator.getHarness();
        if (harness == null || coordination.getDispatcher() == null) {
            return;
        }
        Object teamCompletion = coordination.getDispatcher().getTeamCompletion();
        try {
            java.lang.reflect.Method register = teamCompletion.getClass()
                    .getMethod("registerCompletionCallback", TeamCompletionCallback.class);
            for (Object rail : harness.findRails(Object.class)) {
                if (rail instanceof TeamCompletionCallback callback) {
                    register.invoke(teamCompletion, callback);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // The current dispatcher facade does not expose the concrete handler hook.
        }
    }

    private TeamMember createMemberHandle(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (backend == null) {
            return null;
        }
        TeamMember.MemberStore memberStore = backend.getMemberStore() == null
                ? new MissingMemberStore()
                : backend.getMemberStore();
        return new TeamMember(
                memberName,
                backend.getTeamName(),
                card,
                memberStore,
                configurator.getInfra().getMessager(),
                null,
                null,
                configurator.getCtx() == null ? null : configurator.getCtx().getPersona()
        );
    }

    private CompletionStage<Void> markTeamCleaned() {
        return persistTeamDbState(TeamRuntimeMetadata.TEAM_DB_STATE_CLEANED)
                .thenRun(() -> state.setTeamCleaned(true));
    }

    private CompletionStage<Void> markTeamBuilt() {
        return persistTeamDbState(TeamRuntimeMetadata.TEAM_DB_STATE_CREATED);
    }

    private CompletionStage<Void> persistTeamDbState(String dbState) {
        SessionManager.AgentTeamSessionView teamSession = sessionManager.getTeamSession();
        String teamName = configurator.getTeamName();
        if (teamSession != null && teamName != null) {
            TeamRuntimeMetadata.mergeTeamDbState(teamSession, teamName, dbState);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> wakeMailboxIfInterruptCleared() {
        return coordination.wakeMailboxIfInterruptCleared();
    }

    private CompletionStage<Void> requestCompletionPoll() {
        if (getRole() != TeamRole.LEADER || !"persistent".equals(getLifecycle())) {
            return CompletableFuture.completedFuture(null);
        }
        return coordination.enqueue(new com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage(
                com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType.POLL_TASK
        ));
    }

    private CompletionStage<Void> removeSelfFromPool(String sessionId) {
        return CompletableFuture.completedFuture(null);
    }

    private String memberNameOrUnknown() {
        String memberName = configurator.getMemberName();
        return memberName == null || memberName.isEmpty() ? UNKNOWN_MEMBER : memberName;
    }

    private List<Object> drainStreamQueueSnapshot() {
        List<Object> chunks = new ArrayList<>();
        Queue<Object> queue = streamController.getRawStreamQueue();
        Object chunk;
        while ((chunk = queue.poll()) != null) {
            chunks.add(chunk);
        }
        return chunks;
    }

    private static Throwable unwrapCompletion(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class InitialRouteRuntimeAdapter implements TeamRuntimeManager.TeamAgentRuntime {
        private final TeamAgent agent;

        private InitialRouteRuntimeAdapter(TeamAgent agent) {
            this.agent = agent;
        }

        @Override
        public CompletionStage<Void> deliverInput(String body) {
            return agent.deliverInput(body);
        }

        @Override
        public CompletionStage<Void> pauseCoordination() {
            return agent.pauseCoordination();
        }

        @Override
        public CompletionStage<Void> stopCoordination() {
            return agent.stopCoordination();
        }

        @Override
        public CompletionStage<Boolean> isShutdownRequested() {
            return agent.isShutdownRequested();
        }

        @Override
        public String lifecycle() {
            return agent.getLifecycle();
        }

        @Override
        public TeamRuntimeManager.TeamBackendRuntime teamBackend() {
            return agent.getTeamBackend();
        }

        @Override
        public boolean hasPendingInterrupt() {
            return agent.hasPendingInterrupt();
        }

        @Override
        public CompletionStage<Void> autoStartAll() {
            return agent.autoStartAll().thenApply(ignored -> (Void) null);
        }

        @Override
        public CompletionStage<Void> autoStartMember(String memberName) {
            return agent.autoStartMember(memberName).thenApply(ignored -> (Void) null);
        }

        @Override
        public CompletionStage<TeamRuntimeManager.TeamAgentRuntime> lookupHumanAgentRuntime(String memberName) {
            return agent.lookupHumanAgentRuntime(memberName).thenApply(runtime -> {
                if (runtime instanceof TeamRuntimeManager.TeamAgentRuntime teamRuntime) {
                    return teamRuntime;
                }
                if (runtime instanceof TeamAgent teamAgent) {
                    return new InitialRouteRuntimeAdapter(teamAgent);
                }
                return null;
            });
        }

        @Override
        public CompletionStage<Void> recoverTeam() {
            return agent.recoverTeam();
        }
    }

    private static CompletionStage<Void> updateLiveTeammates(
            RecoveryManager.MemberRegistry registry,
            List<RecoveryManager.MemberRecord> members,
            int index,
            String selfMemberName,
            String teamName,
            MemberStatus targetStatus,
            EnumSet<MemberStatus> terminalStatuses
    ) {
        if (index >= members.size() || teamName == null) {
            return CompletableFuture.completedFuture(null);
        }
        RecoveryManager.MemberRecord member = members.get(index);
        if (Objects.equals(member.memberName(), selfMemberName)) {
            return updateLiveTeammates(registry, members, index + 1, selfMemberName, teamName, targetStatus, terminalStatuses);
        }
        MemberStatus current = MemberStatus.fromValue(member.status());
        CompletionStage<Boolean> update = terminalStatuses.contains(current)
                ? CompletableFuture.completedFuture(false)
                : registry.updateMemberStatus(member.memberName(), teamName, targetStatus.value());
        return update.thenCompose(ignored ->
                updateLiveTeammates(registry, members, index + 1, selfMemberName, teamName, targetStatus, terminalStatuses));
    }

    private static TeamAgentBlueprint toKernelBlueprint(AgentConfigurator.TeamAgentBlueprint source) {
        if (source == null) {
            return null;
        }
        return new TeamAgentBlueprint(
                source.getCard(),
                source.getSpec(),
                source.getCtx(),
                source.getRolePolicy(),
                source.getLanguage()
        );
    }

    private static String rawQuery(Object inputs) {
        if (inputs instanceof Map<?, ?> map && map.containsKey("query")) {
            Object query = map.get("query");
            return query == null ? "" : String.valueOf(query);
        }
        return inputs == null ? "" : String.valueOf(inputs);
    }

    private static TeamAgentSpec parseSpec(Object raw) {
        if (raw instanceof TeamAgentSpec spec) {
            return spec;
        }
        Map<String, Object> map = asStringMap(raw);
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(stringValue(map.get("team_name"), spec.getTeamName()));
        spec.setLifecycle(stringValue(map.get("lifecycle"), spec.getLifecycle()));
        spec.setTeammateMode(stringValue(map.get("teammate_mode"), spec.getTeammateMode()));
        spec.setSpawnMode(stringValue(map.get("spawn_mode"), spec.getSpawnMode()));
        spec.setTeamMode(stringValue(map.get("team_mode"), null));
        spec.setAgents(parseAgents(map.get("agents")));
        spec.setPredefinedMembers(parseMembers(map.get("predefined_members")));
        spec.setExternalCliAgents(listValue(map.get("external_cli_agents")));
        spec.setMetadata(asStringMap(map.get("metadata")));
        spec.setEnableHitt(booleanValue(map.get("enable_hitt")));
        spec.setEnableBridge(booleanValue(map.get("enable_bridge")));
        spec.setExposeHumanAgentsToTeammates(booleanValue(map.get("expose_human_agents_to_teammates")));
        return spec;
    }

    private static TeamRuntimeContext parseContext(Object raw) {
        if (raw instanceof TeamRuntimeContext context) {
            return context;
        }
        Map<String, Object> map = asStringMap(raw);
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(TeamRole.fromValue(stringValue(map.get("role"), TeamRole.LEADER.value())));
        context.setMemberName(stringValue(map.get("member_name"), null));
        context.setPersona(stringValue(map.get("persona"), ""));
        context.setTeamSpec(parseTeamSpec(map.get("team_spec")));
        context.setMessagerConfig(parseMessagerConfig(map.get("messager_config")));
        context.setDbConfig(asStringMap(map.get("db_config")));
        context.setMemberModel(map.get("member_model"));
        context.setCliAgent(stringValue(map.get("cli_agent"), null));
        return context;
    }

    private static Map<String, DeepAgentSpec> parseAgents(Object raw) {
        Map<String, DeepAgentSpec> agents = new LinkedHashMap<>();
        Map<String, Object> map = asStringMap(raw);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Map<String, Object> value = asStringMap(entry.getValue());
            DeepAgentSpec agent = new DeepAgentSpec();
            agent.setLanguage(stringValue(value.get("language"), null));
            agent.setModel(value.get("model"));
            agent.setSystemPrompt(stringValue(value.get("system_prompt"), null));
            agent.setWorkspace(parseWorkspace(value.get("workspace")));
            agent.setTools(listValue(value.get("tools")));
            agent.setApprovalRequiredTools(stringList(value.get("approval_required_tools")));
            agents.put(entry.getKey(), agent);
        }
        if (agents.isEmpty()) {
            agents.put("leader", new DeepAgentSpec());
        }
        return agents;
    }

    private static WorkspaceSpec parseWorkspace(Object raw) {
        if (raw instanceof WorkspaceSpec workspaceSpec) {
            return workspaceSpec;
        }
        Map<String, Object> map = asStringMap(raw);
        if (map.isEmpty()) {
            return null;
        }
        WorkspaceSpec workspace = new WorkspaceSpec();
        workspace.setRootPath(stringValue(map.get("root_path"), workspace.getRootPath()));
        workspace.setLanguage(stringValue(map.get("language"), workspace.getLanguage()));
        workspace.setStableBase(booleanValue(map.get("stable_base")));
        return workspace;
    }

    private static List<TeamMemberSpec> parseMembers(Object raw) {
        List<TeamMemberSpec> members = new ArrayList<>();
        for (Object item : listValue(raw)) {
            Map<String, Object> value = asStringMap(item);
            TeamMemberSpec member = new TeamMemberSpec();
            member.setMemberName(stringValue(value.get("member_name"), null));
            member.setDisplayName(stringValue(value.get("display_name"), member.getMemberName()));
            member.setRoleType(TeamRole.fromValue(stringValue(value.get("role_type"), TeamRole.TEAMMATE.value())));
            member.setPersona(stringValue(value.get("persona"), ""));
            member.setPromptHint(stringValue(value.get("prompt_hint"), null));
            member.setModelName(stringValue(value.get("model_name"), null));
            members.add(member);
        }
        return members;
    }

    private static TeamSpec parseTeamSpec(Object raw) {
        if (raw instanceof TeamSpec teamSpec) {
            return teamSpec;
        }
        Map<String, Object> map = asStringMap(raw);
        if (map.isEmpty()) {
            return null;
        }
        TeamSpec teamSpec = new TeamSpec();
        teamSpec.setTeamName(stringValue(map.get("team_name"), null));
        teamSpec.setDisplayName(stringValue(map.get("display_name"), null));
        teamSpec.setLeaderMemberName(stringValue(map.get("leader_member_name"), null));
        teamSpec.setLanguage(stringValue(map.get("language"), null));
        teamSpec.setMetadata(asStringMap(map.get("metadata")));
        teamSpec.setModelPool(listValue(map.get("model_pool")));
        teamSpec.setModelPoolStrategy(stringValue(map.get("model_pool_strategy"), "round_robin"));
        return teamSpec;
    }

    private static MessagerTransportConfig parseMessagerConfig(Object raw) {
        if (raw instanceof MessagerTransportConfig config) {
            return config;
        }
        Map<String, Object> map = asStringMap(raw);
        if (map.isEmpty()) {
            return null;
        }
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setBackend(stringValue(map.get("backend"), config.getBackend()));
        config.setTeamName(stringValue(map.get("team_name"), config.getTeamName()));
        config.setNodeId(stringValue(map.get("node_id"), null));
        config.setDirectAddr(stringValue(map.get("direct_addr"), null));
        config.setPubsubPublishAddr(stringValue(map.get("pubsub_publish_addr"), null));
        config.setPubsubSubscribeAddr(stringValue(map.get("pubsub_subscribe_addr"), null));
        config.setListenAddrs(stringList(map.get("listen_addrs")));
        if (map.get("request_timeout") instanceof Number number) {
            config.setRequestTimeout(number.doubleValue());
        }
        config.setMetadata(asStringMap(map.get("metadata")));
        return config;
    }

    private static Map<String, Object> asStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        return stringObjectMap(source);
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            map.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return map;
    }

    private static List<Object> listValue(Object raw) {
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        for (Object item : listValue(raw)) {
            values.add(String.valueOf(item));
        }
        return values;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Optional bridge-agent classifier supplied by a concrete backend.
     *
     * <p>Mirrors Python's {@code team_backend.is_bridge_agent(...)} use in
     * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    public interface BridgeAgentBackend {
        boolean isBridgeAgent(String memberName);
    }

    /**
     * Optional backend cleanup hook used by leader-level destroy.
     *
     * <p>Mirrors Python's {@code team_backend.force_clean_team(...)} use in
     * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    public interface TeamCleaner {
        CompletionStage<Boolean> forceCleanTeam(boolean shutdownMembers);
    }

    /**
     * Optional interaction gateway for user and human-agent messages.
     *
     * <p>Mirrors Python's {@code UserInbox} and {@code HumanAgentInbox} calls in
     * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    public interface InteractiveGateway {
        CompletionStage<DeliverResult> broadcast(String content);

        CompletionStage<DeliverResult> humanAgentSay(String content, String to, String sender);
    }

    /**
     * Optional startup hook for predefined members.
     *
     * <p>Mirrors Python's {@code team_backend.startup(...)} and
     * {@code startup_member(...)} calls in
     * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    public interface AutoStartBackend {
        CompletionStage<Boolean> startupMember(
                String memberName,
                java.util.function.Function<String, CompletionStage<Void>> onCreated
        );

        CompletionStage<List<String>> startup(
                java.util.function.Function<String, CompletionStage<Void>> onCreated
        );
    }

    /**
     * Callback shape passed into configurator setup.
     *
     * <p>Mirrors Python's {@code on_teammate_created} callback in
     * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    @FunctionalInterface
    public interface TeammateCreatedCallback {
        CompletionStage<Void> onCreated(String teammateId);
    }

    /**
     * Lifecycle callback shape passed into backend setup.
     *
     * <p>Mirrors Python's {@code _mark_team_cleaned} and {@code _mark_team_built}
     * callbacks in {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    @FunctionalInterface
    public interface LifecycleCallback {
        CompletionStage<Void> call();
    }

    /**
     * Optional rail callback notified when the team is complete.
     *
     * <p>Mirrors Python's team-completion rail callback registration in
     * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    public interface TeamCompletionCallback {
        CompletionStage<Void> notifyTeamCompleted();
    }

    /**
     * No-op member store used before a concrete database row exists.
     *
     * <p>Mirrors Python's {@code TeamMember} tolerance for missing persisted
     * rows in {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
     */
    private static final class MissingMemberStore implements TeamMember.MemberStore {
        @Override
        public CompletionStage<TeamMember.MemberSnapshot> getMember(String memberName, String teamName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
