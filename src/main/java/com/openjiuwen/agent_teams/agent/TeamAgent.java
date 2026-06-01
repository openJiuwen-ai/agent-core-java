/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.interaction.HumanAgentInbox;
import com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError;
import com.openjiuwen.agent_teams.interaction.MentionParser;
import com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agent_teams.interaction.UserInbox;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentKind;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal Java TeamAgent that composes an internal DeepAgent leader runtime.
 *
 * <p>Mirrors Python's {@code TeamAgent} in
 * {@code openjiuwen.agent_teams.agent.team_agent}.
 */
public class TeamAgent extends BaseAgent {

    private TeamAgentSpec spec;
    private TeamRuntimeContext runtimeContext;
    private DeepAgent deepAgent;
    private TeamBackend teamBackend;
    private FirstIterationGate firstIterationGate;
    private CoordinatorLoop coordinatorLoop;
    private TeamDispatcher dispatcher;
    private UserInbox userInbox;
    private HumanAgentInbox humanAgentInbox;
    private RecoveryManager recoveryManager;
    private SessionManager sessionManager;
    private ModelAllocator modelAllocator;
    private Allocation leaderAllocation;
    private final List<Consumer<Object>> eventListeners = new ArrayList<>();
    private final List<AgentRail> registeredRails = new ArrayList<>();

    public TeamAgent(AgentCard card) {
        super(card);
    }

    public static TeamAgent fromSpec(TeamAgentSpec spec) {
        AgentCard card = new AgentCard();
        assignField(card, "name", spec.getTeamName());
        assignField(card, "description", "Agent team leader");
        TeamAgent agent = new TeamAgent(card);
        agent.configure(spec);
        return agent;
    }

    @Override
    public BaseAgent configure(Object configObj) {
        if (!(configObj instanceof TeamAgentSpec teamAgentSpec)) {
            throw new IllegalArgumentException("Expected TeamAgentSpec, got: "
                    + (configObj != null ? configObj.getClass().getName() : "null"));
        }
        configureInternal(teamAgentSpec, buildRuntimeContext(teamAgentSpec));
        return this;
    }

    public TeamAgent configure(TeamAgentSpec spec, TeamRuntimeContext context) {
        configureInternal(spec, context != null ? context : buildRuntimeContext(spec));
        return this;
    }

    private void configureInternal(TeamAgentSpec teamAgentSpec, TeamRuntimeContext context) {
        this.spec = teamAgentSpec;
        this.runtimeContext = context;
        configureModelAllocation(teamAgentSpec, this.runtimeContext);
        this.deepAgent = buildDeepAgent(teamAgentSpec, this.runtimeContext);
        this.teamBackend = buildTeamBackend(teamAgentSpec, this.runtimeContext);
        this.firstIterationGate = new FirstIterationGate();
        this.dispatcher = new TeamDispatcher(
                runtimeContext.getRole(),
                runtimeContext.getMemberName(),
                teamAgentSpec.getTeamName(),
                this.teamBackend
        );
        this.userInbox = new UserInbox(this.teamBackend, this::deliverInput);
        this.humanAgentInbox = new HumanAgentInbox(this.teamBackend);
        this.coordinatorLoop = new CoordinatorLoop(runtimeContext.getRole(), this::onCoordinationEvent);
        this.recoveryManager = new RecoveryManager(this.spec, this.runtimeContext, this.teamBackend);
        this.recoveryManager.setModelAllocator(this.modelAllocator);
        this.sessionManager = new SessionManager(this::getLifecycle, this::getTeamBackend, this.recoveryManager);
        this.registeredRails.clear();
        this.deepAgent.getDelegate().registerRail(firstIterationGate);
        this.registeredRails.add(firstIterationGate);
        TeamRail teamRail = buildTeamRail(teamAgentSpec, this.runtimeContext);
        this.deepAgent.getDelegate().registerRail(teamRail);
        this.registeredRails.add(teamRail);
        this.teamBackend.registerPredefinedMembers();
        this.deepAgent.getDelegate().getAbilityManager().add(
                AgentTeamsToolRegistry.createTeamTools(
                        this.teamBackend,
                        runtimeContext.getRole(),
                        teamAgentSpec.getTeammateMode()
                ).stream().map(tool -> tool.getCard()).toList()
        );
        registerApprovalRailIfNeeded(teamAgentSpec, this.runtimeContext);
    }

    @Override
    public Object getConfig() {
        return spec;
    }

    public TeamAgentSpec getSpec() {
        return spec;
    }

    public TeamRuntimeContext getRuntimeContext() {
        return runtimeContext;
    }

    public DeepAgent getDeepAgent() {
        return deepAgent;
    }

    public TeamBackend getTeamBackend() {
        return teamBackend;
    }

    public FirstIterationGate getFirstIterationGate() {
        return firstIterationGate;
    }

    public CoordinatorLoop getCoordinatorLoop() {
        return coordinatorLoop;
    }

    public RecoveryManager getRecoveryManager() {
        return recoveryManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public ModelAllocator getModelAllocator() {
        return modelAllocator;
    }

    public Allocation getLeaderAllocation() {
        return leaderAllocation;
    }

    public TeamDispatcher getDispatcher() {
        return dispatcher;
    }

    public List<Consumer<Object>> getEventListeners() {
        return new ArrayList<>(eventListeners);
    }

    public List<AgentRail> getRegisteredRails() {
        return new ArrayList<>(registeredRails);
    }

    public String getTeamName() {
        return runtimeContext != null && runtimeContext.getTeamSpec() != null
                ? runtimeContext.getTeamSpec().getTeamName() : null;
    }

    public Object deliverInput(Object content) {
        AgentSessionApi session = AgentSessionApi.create((String) null, Map.of(), getCard());
        return invoke(content, session);
    }

    /**
     * Deliver raw user input into the team runtime.
     *
     * <p>Mirrors Python's router and user inbox flow in
     * {@code openjiuwen.agent_teams.interaction.router} and
     * {@code openjiuwen.agent_teams.interaction.user_inbox}.
     */
    public Object receiveUserInput(String rawContent) {
        MentionParser.Mention mention = MentionParser.parseMention(rawContent);
        if (mention == null) {
            return userInbox.deliverToLeader(rawContent);
        }
        if ("*".equals(mention.target())) {
            return userInbox.broadcast(mention.body());
        }
        if (teamBackend == null || !teamBackend.hasMember(mention.target())) {
            return userInbox.deliverToLeader(rawContent);
        }
        return userInbox.direct(mention.target(), mention.body());
    }

    public Object broadcastFromUser(String body) {
        return userInbox.broadcast(body);
    }

    public Object directFromUser(String target, String body) {
        return userInbox.direct(target, body);
    }

    public Object humanAgentSay(String body) {
        try {
            return humanAgentInbox.send(body);
        } catch (HumanAgentNotEnabledError | UnknownHumanAgentError e) {
            return Map.of("error", e.getMessage());
        }
    }

    public void startCoordination() {
        if (coordinatorLoop != null) {
            coordinatorLoop.start();
        }
    }

    public void pauseCoordination() {
        if (coordinatorLoop != null) {
            coordinatorLoop.pausePolls();
        }
        persistAllocatorState();
    }

    public void stopCoordination() {
        if (coordinatorLoop != null) {
            coordinatorLoop.stop();
        }
    }

    public void interact(String message) {
        if (coordinatorLoop == null) {
            return;
        }
        coordinatorLoop.wake(new CoordinationEvent(
                "user_input",
                Map.of("content", message != null ? message : "")
        ));
    }

    public void notifyEvent(String eventType, Map<String, Object> payload) {
        EventMessage message = new EventMessage(eventType, payload != null ? payload : Map.of());
        if (coordinatorLoop != null) {
            coordinatorLoop.wake(new CoordinationEvent(eventType, payload != null ? payload : Map.of()));
        }
        for (Consumer<Object> listener : new ArrayList<>(eventListeners)) {
            listener.accept(message);
        }
    }

    public void addEventListener(Consumer<Object> handler) {
        if (handler != null) {
            eventListeners.add(handler);
        }
    }

    public void removeEventListener(Consumer<Object> handler) {
        eventListeners.remove(handler);
    }

    public TeamMember spawnMember(TeamMemberSpec spec, AgentCard card) {
        return teamBackend.spawnMember(
                spec.getMemberName(),
                spec.getDisplayName(),
                card,
                spec.getPersona(),
                spec.getPromptHint(),
                spec.getRoleType() == com.openjiuwen.agent_teams.schema.TeamRole.HUMAN_AGENT
                        ? MemberStatus.READY : MemberStatus.UNSTARTED,
                ExecutionStatus.IDLE
        );
    }

    public TeamMember spawnMember(TeamMemberSpec spec) {
        AgentCard card = new AgentCard();
        assignField(card, "id", spec.getMemberName());
        assignField(card, "name", spec.getMemberName());
        assignField(card, "description", spec.getDisplayName());
        return spawnMember(spec, card);
    }

    public TeamRuntimeContext buildMemberContext(TeamMemberSpec memberSpec) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(memberSpec.getRoleType() != null ? memberSpec.getRoleType() : TeamRole.TEAMMATE);
        context.setMemberName(memberSpec.getMemberName());
        context.setPersona(memberSpec.getPersona());
        context.setTeamSpec(runtimeContext != null ? runtimeContext.getTeamSpec() : null);
        context.setDbConfig(runtimeContext != null ? runtimeContext.getDbConfig() : null);
        context.setMetadata(runtimeContext != null ? runtimeContext.getMetadata() : Map.of());

        MessagerTransportConfig transport = cloneMessagerConfig(
                runtimeContext != null ? runtimeContext.getMessagerConfig() : null
        );
        transport.setTeamName(spec != null ? spec.getTeamName() : getTeamName());
        transport.setNodeId(memberSpec.getMemberName());
        context.setMessagerConfig(transport);

        if (modelAllocator != null && memberSpec.getModelName() != null && !memberSpec.getModelName().isBlank()) {
            context.setMemberModel(modelAllocator.allocate(memberSpec.getModelName()).toTeamModelConfig());
        }
        return context;
    }

    public Map<String, Object> buildSpawnPayload(TeamRuntimeContext context, String initialMessage) {
        Map<String, Object> coordination = contextToMap(context);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordination", coordination);
        payload.put("spec", spec);
        payload.put("context", context);
        payload.put("query", initialMessage != null ? initialMessage : "");
        return payload;
    }

    public SpawnAgentConfig buildSpawnConfig(TeamRuntimeContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spec", spec);
        payload.put("context", contextToMap(context));
        SpawnAgentConfig config = new SpawnAgentConfig();
        config.setAgentKind(SpawnAgentKind.TEAM_AGENT);
        config.setRunnerConfig(new LinkedHashMap<>());
        config.setPayload(payload);
        return config;
    }

    public static TeamAgent fromSpawnPayload(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        TeamAgentSpec spec = payload.get("spec") instanceof TeamAgentSpec typedSpec
                ? typedSpec : new TeamAgentSpec();
        TeamRuntimeContext context = payload.get("context") instanceof TeamRuntimeContext typedContext
                ? typedContext : contextFromMap(asMap(payload.get("context")));
        AgentCard card = new AgentCard();
        assignField(card, "name", context.getMemberName());
        assignField(card, "description", "Teammate: " + context.getPersona());
        TeamAgent agent = new TeamAgent(card);
        agent.configure(spec, context);
        return agent;
    }

    /**
     * Recover a team agent from persisted session state.
     *
     * <p>Mirrors Python's {@code TeamAgent.recover_from_session}.</p>
     */
    public static TeamAgent recoverFromSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        TeamAgentSpec recoveredSpec = specFromState(session.getState("spec"));
        TeamRuntimeContext recoveredContext = contextFromMap(asMap(session.getState("context")));
        if (recoveredContext.getTeamSpec() == null) {
            recoveredContext.setTeamSpec(teamSpecFromAgentSpec(recoveredSpec, recoveredContext));
        }
        AgentCard card = new AgentCard();
        assignField(card, "name", recoveredContext.getMemberName());
        assignField(card, "description", recoveredContext.getPersona());
        TeamAgent agent = new TeamAgent(card);
        agent.configure(recoveredSpec, recoveredContext);
        agent.registerCurrentSession(session);
        return agent;
    }

    public List<String> startupMembers() {
        return teamBackend.startup();
    }

    public List<TeamMember> listMembers() {
        return teamBackend.listMembers();
    }

    /**
     * Run a teammate through its minimal Java member runtime.
     *
     * <p>Mirrors Python's teammate execution path through
     * {@code openjiuwen.agent_teams.spawn} and the internal DeepAgent runtime.
     */
    public Object runMember(String memberName, Object content) {
        return teamBackend.runMember(memberName, content);
    }

    private void onCoordinationEvent(CoordinationEvent event) {
        String message = dispatcher != null ? dispatcher.dispatch(event) : event.getEventType();
        if (message != null && !message.isBlank()) {
            deliverInput(message);
        }
    }

    /**
     * Register a member session so team approval tools can resume that member's
     * pending harness security approvals.
     *
     * <p>Mirrors Python's member/session routing intent in
     * {@code openjiuwen.agent_teams.spawn.context} and approval tools in
     * {@code openjiuwen.agent_teams.tools.team_tools}.
     */
    public void registerMemberSession(String memberName, Session session) {
        teamBackend.registerMemberSession(memberName, session);
    }

    public void registerCurrentSession(Session session) {
        if (runtimeContext != null) {
            teamBackend.registerMemberSession(runtimeContext.getMemberName(), session);
        }
        if (sessionManager != null) {
            sessionManager.registerCurrentSession(session);
        }
    }

    public void resumeForNewSession(Session session) {
        if (sessionManager != null) {
            sessionManager.resumeForNewSession(session);
        }
    }

    public void recoverForExistingSession(Session session) {
        if (sessionManager != null) {
            sessionManager.recoverForExistingSession(session);
        }
    }

    public void persistAllocatorState() {
        if (recoveryManager != null && sessionManager != null) {
            recoveryManager.persistAllocatorState(sessionManager.getTeamSession());
        }
    }

    public void updateModelPool(List<ModelPoolEntry> newPool) {
        if (runtimeContext == null || runtimeContext.getTeamSpec() == null) {
            return;
        }
        List<ModelPoolEntry> merged = ModelPoolEntry.inheritPoolIds(runtimeContext.getTeamSpec().getModelPool(), newPool);
        runtimeContext.getTeamSpec().setModelPool(merged);
        if (spec != null) {
            spec.setModelPool(merged);
            this.modelAllocator = ModelAllocators.buildModelAllocator(spec, runtimeContext.getTeamSpec());
        }
        this.leaderAllocation = null;
        if (recoveryManager != null) {
            recoveryManager.setModelAllocator(modelAllocator);
        }
        if (sessionManager != null && sessionManager.getTeamSession() != null) {
            recoveryManager.persistLeaderConfig(sessionManager.getTeamSession());
        }
    }

    public void attachModelAllocator(ModelAllocator allocator, Allocation leaderAllocation) {
        this.modelAllocator = allocator;
        this.leaderAllocation = leaderAllocation;
        if (leaderAllocation != null && runtimeContext != null) {
            runtimeContext.setMemberModel(leaderAllocation.toTeamModelConfig());
        }
        if (recoveryManager != null) {
            recoveryManager.setModelAllocator(allocator);
        }
    }

    public void restoreAllocatorState(Map<String, Object> state) {
        if (modelAllocator != null) {
            modelAllocator.loadStateDict(state);
        }
    }

    @Override
    public Object invoke(Object inputs, Session session) {
        registerCurrentSession(session);
        Map<String, Object> normalized = normalizeInputs(inputs);
        normalized.put("team_name", getTeamName());
        normalized.put("team_context", serializeRuntimeContext());
        return deepAgent.invoke(normalized, session);
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        registerCurrentSession(session);
        Map<String, Object> normalized = normalizeInputs(inputs);
        normalized.put("team_name", getTeamName());
        normalized.put("team_context", serializeRuntimeContext());
        return deepAgent.stream(normalized, session, streamModes);
    }

    private TeamRuntimeContext buildRuntimeContext(TeamAgentSpec spec) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(TeamRole.LEADER);
        context.setMemberName(spec.getLeader() != null ? spec.getLeader().getMemberName() : "team_leader");
        context.setPersona(spec.getLeader() != null ? spec.getLeader().getPersona() : "Lead the team.");
        context.setMetadata(spec.getMetadata());
        com.openjiuwen.agent_teams.schema.TeamSpec teamSpec = new com.openjiuwen.agent_teams.schema.TeamSpec();
        teamSpec.setTeamName(spec.getTeamName());
        teamSpec.setDisplayName(spec.getTeamName());
        teamSpec.setLeaderMemberName(context.getMemberName());
        teamSpec.setLanguage(TeamAgentSpec.resolveLanguage(spec.getLanguage()));
        teamSpec.setMetadata(spec.getMetadata());
        teamSpec.setModelPool(spec.getModelPool());
        teamSpec.setModelPoolStrategy(spec.getModelPoolStrategy());
        context.setTeamSpec(teamSpec);
        context.setMessagerConfig(resolveMessagerConfig(spec, context.getMemberName()));
        Object dbConfig = spec.getMetadata() != null ? spec.getMetadata().get("db_config") : null;
        if (dbConfig instanceof DatabaseConfig typedDbConfig) {
            context.setDbConfig(typedDbConfig);
        }
        return context;
    }

    private DeepAgent buildDeepAgent(TeamAgentSpec spec, TeamRuntimeContext context) {
        DeepAgentSpec agentSpec = resolveAgentSpec(spec, context);
        if (agentSpec == null || agentSpec.getConfig() == null) {
            DeepAgentConfig fallback = new DeepAgentConfig();
            fallback.setCard(context.getRole() == TeamRole.LEADER ? createLeaderCard(spec) : getCard());
            fallback.setSystemPrompt(context.getRole() == TeamRole.LEADER
                    ? "You are the leader of the team '" + spec.getTeamName() + "'."
                    : "You are teammate '" + context.getMemberName() + "' in team '" + spec.getTeamName() + "'.");
            applyTeamModelConfig(fallback, runtimeContext != null ? runtimeContext.getMemberModel() : null);
            return com.openjiuwen.harness.HarnessFactory.createDeepAgent(fallback);
        }
        DeepAgentConfig config = agentSpec.getConfig();
        if (config.getCard() == null) {
            config.setCard(context.getRole() == TeamRole.LEADER ? createLeaderCard(spec) : getCard());
        }
        String basePrompt = config.getSystemPrompt() != null ? config.getSystemPrompt() : "";
        String teamPrompt = context.getRole() == TeamRole.LEADER
                ? "\n\nYou are coordinating the agent team '" + spec.getTeamName()
                + "' as leader '" + context.getMemberName() + "'."
                : "\n\nYou are working in agent team '" + spec.getTeamName()
                + "' as teammate '" + context.getMemberName() + "'.";
        config.setSystemPrompt(basePrompt + teamPrompt);
        applyTeamModelConfig(config, runtimeContext != null && runtimeContext.getMemberModel() != null
                ? runtimeContext.getMemberModel() : agentSpec.getModel());
        return com.openjiuwen.harness.HarnessFactory.createDeepAgent(config);
    }

    private TeamBackend buildTeamBackend(TeamAgentSpec spec, TeamRuntimeContext context) {
        return new TeamBackend(
                spec.getTeamName(),
                runtimeContext.getMemberName(),
                context == null || context.getRole() == TeamRole.LEADER,
                "plan_mode".equalsIgnoreCase(spec.getTeammateMode()) ? MemberMode.PLAN_MODE : MemberMode.BUILD_MODE,
                spec.getPredefinedMembers()
        );
    }

    public TeamLifecycle getLifecycle() {
        return spec != null ? spec.getLifecycle() : null;
    }

    private AgentCard createLeaderCard(TeamAgentSpec spec) {
        AgentCard card = new AgentCard();
        assignField(card, "name", spec.getLeader() != null ? spec.getLeader().getMemberName() : "team_leader");
        assignField(card, "description", spec.getLeader() != null ? spec.getLeader().getDisplayName() : "Team Leader");
        return card;
    }

    private void configureModelAllocation(TeamAgentSpec spec, TeamRuntimeContext context) {
        if (context == null || context.getTeamSpec() == null || context.getTeamSpec().getModelPool().isEmpty()) {
            modelAllocator = null;
            leaderAllocation = null;
            return;
        }
        if (modelAllocator == null) {
            modelAllocator = ModelAllocators.buildModelAllocator(spec, context.getTeamSpec());
        }
        if (leaderAllocation == null && modelAllocator != null) {
            String leaderModelName = spec.getLeader() != null ? spec.getLeader().getModelName() : null;
            leaderAllocation = modelAllocator.allocate(leaderModelName);
        }
        TeamModelConfig leaderModel = leaderAllocation != null ? leaderAllocation.toTeamModelConfig() : null;
        context.setMemberModel(leaderModel);
        validateLeaderModelResolved(spec, leaderModel, context.getTeamSpec());
    }

    private void validateLeaderModelResolved(
            TeamAgentSpec spec,
            TeamModelConfig leaderModel,
            com.openjiuwen.agent_teams.schema.TeamSpec teamSpec
    ) {
        DeepAgentSpec leaderAgent = spec.getAgents().get("leader");
        boolean hasExplicitModel = leaderAgent != null && (
                leaderAgent.getModel() != null
                        || (leaderAgent.getConfig() != null && leaderAgent.getConfig().getModelClientConfig() != null)
        );
        if (leaderModel != null || hasExplicitModel || teamSpec == null || teamSpec.getModelPool().isEmpty()) {
            return;
        }
        List<String> names = teamSpec.getModelPool().stream()
                .map(ModelPoolEntry::getModelName)
                .distinct()
                .sorted()
                .toList();
        String leaderName = spec.getLeader() != null ? spec.getLeader().getModelName() : null;
        String cause = leaderName != null && !leaderName.isBlank() && !names.contains(leaderName)
                ? "leader.model_name='" + leaderName + "' is not present in the pool (available names: " + names + ")"
                : "model_pool_strategy='by_model_name' requires leader.model_name to be set to one of the pool names";
        throw new IllegalArgumentException("agent team config invalid: " + cause);
    }

    private static void applyTeamModelConfig(DeepAgentConfig config, TeamModelConfig modelConfig) {
        if (config == null || modelConfig == null) {
            return;
        }
        config.setModelClientConfig(modelConfig.getModelClientConfig());
        config.setModelRequestConfig(modelConfig.getModelRequestConfig());
    }

    private Map<String, Object> serializeRuntimeContext() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (runtimeContext == null) {
            return result;
        }
        result.put("role", runtimeContext.getRole() != null ? runtimeContext.getRole().name().toLowerCase() : null);
        result.put("member_name", runtimeContext.getMemberName());
        result.put("persona", runtimeContext.getPersona());
        result.put("metadata", runtimeContext.getMetadata());
        if (runtimeContext.getTeamSpec() != null) {
            result.put("team_spec", Map.of(
                    "team_name", runtimeContext.getTeamSpec().getTeamName(),
                    "display_name", runtimeContext.getTeamSpec().getDisplayName(),
                    "leader_member_name", runtimeContext.getTeamSpec().getLeaderMemberName(),
                    "language", runtimeContext.getTeamSpec().getLanguage(),
                    "model_pool_strategy", runtimeContext.getTeamSpec().getModelPoolStrategy()
            ));
        }
        return result;
    }

    private TeamRail buildTeamRail(TeamAgentSpec spec, TeamRuntimeContext context) {
        String lifecycle = spec.getLifecycle() != null ? spec.getLifecycle().name().toLowerCase() : "temporary";
        String teamMode = spec.getTeamMode() != null && !spec.getTeamMode().isBlank()
                ? spec.getTeamMode()
                : (!spec.getPredefinedMembers().isEmpty() ? "predefined" : "default");
        String language = context.getTeamSpec() != null && context.getTeamSpec().getLanguage() != null
                ? context.getTeamSpec().getLanguage() : TeamAgentSpec.resolveLanguage(spec.getLanguage());
        return new TeamRail(
                context.getRole(),
                context.getPersona(),
                context.getMemberName(),
                lifecycle,
                spec.getTeammateMode(),
                language,
                teamMode,
                "",
                null,
                null,
                this.teamBackend
        );
    }

    private void registerApprovalRailIfNeeded(TeamAgentSpec spec, TeamRuntimeContext context) {
        if (context == null || context.getRole() != TeamRole.TEAMMATE) {
            return;
        }
        DeepAgentSpec agentSpec = resolveAgentSpec(spec, context);
        if (agentSpec == null || agentSpec.getApprovalRequiredTools().isEmpty()) {
            return;
        }
        TeamToolApprovalRail rail = new TeamToolApprovalRail(agentSpec.getApprovalRequiredTools());
        deepAgent.getDelegate().registerRail(rail);
        registeredRails.add(rail);
    }

    private static DeepAgentSpec resolveAgentSpec(TeamAgentSpec spec, TeamRuntimeContext context) {
        if (spec == null || spec.getAgents() == null || spec.getAgents().isEmpty()) {
            return null;
        }
        String memberName = context != null ? context.getMemberName() : null;
        if (memberName != null && spec.getAgents().containsKey(memberName)) {
            return spec.getAgents().get(memberName);
        }
        String roleKey = context != null && context.getRole() != null ? context.getRole().name().toLowerCase() : "leader";
        DeepAgentSpec byRole = spec.getAgents().get(roleKey);
        if (byRole != null) {
            return byRole;
        }
        DeepAgentSpec teammate = spec.getAgents().get("teammate");
        return teammate != null ? teammate : spec.getAgents().get("leader");
    }

    private static MessagerTransportConfig resolveMessagerConfig(TeamAgentSpec spec, String nodeId) {
        Object configured = spec.getMetadata() != null ? spec.getMetadata().get("messager_config") : null;
        MessagerTransportConfig transport = configured instanceof MessagerTransportConfig typed
                ? cloneMessagerConfig(typed) : new MessagerTransportConfig();
        transport.setTeamName(spec.getTeamName());
        transport.setNodeId(nodeId);
        return transport;
    }

    private static MessagerTransportConfig cloneMessagerConfig(MessagerTransportConfig original) {
        MessagerTransportConfig clone = new MessagerTransportConfig();
        if (original == null) {
            return clone;
        }
        clone.setBackend(original.getBackend());
        clone.setTeamName(original.getTeamName());
        clone.setNodeId(original.getNodeId());
        clone.setDirectAddr(original.getDirectAddr());
        clone.setPubsubPublishAddr(original.getPubsubPublishAddr());
        clone.setPubsubSubscribeAddr(original.getPubsubSubscribeAddr());
        clone.setListenAddrs(original.getListenAddrs());
        clone.setBootstrapPeers(original.getBootstrapPeers());
        clone.setKnownPeers(original.getKnownPeers());
        clone.setRequestTimeout(original.getRequestTimeout());
        clone.setMetadata(original.getMetadata());
        return clone;
    }

    private static Map<String, Object> contextToMap(TeamRuntimeContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (context == null) {
            return result;
        }
        result.put("role", context.getRole() != null ? context.getRole().name().toLowerCase() : null);
        result.put("member_name", context.getMemberName());
        result.put("member_id", context.getMemberName());
        result.put("persona", context.getPersona());
        result.put("team_spec", context.getTeamSpec());
        result.put("messager_config", messagerConfigToMap(context.getMessagerConfig()));
        result.put("db_config", context.getDbConfig());
        result.put("metadata", context.getMetadata());
        return result;
    }

    private static Map<String, Object> messagerConfigToMap(MessagerTransportConfig config) {
        if (config == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("backend", config.getBackend());
        result.put("team_name", config.getTeamName());
        result.put("node_id", config.getNodeId());
        result.put("direct_addr", config.getDirectAddr());
        result.put("pubsub_publish_addr", config.getPubsubPublishAddr());
        result.put("pubsub_subscribe_addr", config.getPubsubSubscribeAddr());
        result.put("metadata", config.getMetadata());
        return result;
    }

    private static TeamRuntimeContext contextFromMap(Map<String, Object> map) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        if (map == null) {
            return context;
        }
        Object role = map.get("role");
        if (role != null) {
            context.setRole(TeamRole.valueOf(String.valueOf(role).toUpperCase()));
        }
        Object memberName = map.get("member_name");
        context.setMemberName(memberName != null ? String.valueOf(memberName) : null);
        Object persona = map.get("persona");
        context.setPersona(persona != null ? String.valueOf(persona) : null);
        if (map.get("team_spec") instanceof com.openjiuwen.agent_teams.schema.TeamSpec teamSpec) {
            context.setTeamSpec(teamSpec);
        } else if (map.get("team_spec") instanceof Map<?, ?> teamSpecMap) {
            context.setTeamSpec(teamSpecFromMap(teamSpecMap));
        }
        if (map.get("messager_config") instanceof MessagerTransportConfig config) {
            context.setMessagerConfig(config);
        } else {
            context.setMessagerConfig(messagerConfigFromMap(asMap(map.get("messager_config"))));
        }
        if (map.get("db_config") instanceof DatabaseConfig dbConfig) {
            context.setDbConfig(dbConfig);
        }
        if (map.get("metadata") instanceof Map<?, ?> metadata) {
            Map<String, Object> typed = new LinkedHashMap<>();
            metadata.forEach((key, value) -> typed.put(String.valueOf(key), value));
            context.setMetadata(typed);
        }
        return context;
    }

    private static TeamAgentSpec specFromState(Object rawSpec) {
        if (rawSpec instanceof TeamAgentSpec typedSpec) {
            return typedSpec;
        }
        TeamAgentSpec spec = new TeamAgentSpec();
        Map<String, Object> map = asMap(rawSpec);
        if (map == null) {
            return spec;
        }
        Object teamName = map.get("team_name");
        if (teamName != null) {
            spec.setTeamName(String.valueOf(teamName));
        }
        Object agents = map.get("agents");
        if (agents instanceof Map<?, ?> agentMap) {
            Map<String, DeepAgentSpec> typedAgents = new LinkedHashMap<>();
            for (Object key : agentMap.keySet()) {
                typedAgents.put(String.valueOf(key), new DeepAgentSpec());
            }
            spec.setAgents(typedAgents);
        }
        return spec;
    }

    private static com.openjiuwen.agent_teams.schema.TeamSpec teamSpecFromMap(Map<?, ?> map) {
        com.openjiuwen.agent_teams.schema.TeamSpec teamSpec = new com.openjiuwen.agent_teams.schema.TeamSpec();
        Object teamName = firstPresent(map, "team_name", "teamName");
        if (teamName != null) {
            teamSpec.setTeamName(String.valueOf(teamName));
        }
        Object displayName = firstPresent(map, "display_name", "displayName");
        if (displayName != null) {
            teamSpec.setDisplayName(String.valueOf(displayName));
        }
        Object leaderMemberName = firstPresent(map, "leader_member_name", "leaderMemberName");
        if (leaderMemberName != null) {
            teamSpec.setLeaderMemberName(String.valueOf(leaderMemberName));
        }
        Object language = map.get("language");
        if (language != null) {
            teamSpec.setLanguage(String.valueOf(language));
        }
        Object strategy = firstPresent(map, "model_pool_strategy", "modelPoolStrategy");
        if (strategy != null) {
            teamSpec.setModelPoolStrategy(String.valueOf(strategy));
        }
        return teamSpec;
    }

    private static com.openjiuwen.agent_teams.schema.TeamSpec teamSpecFromAgentSpec(
            TeamAgentSpec spec,
            TeamRuntimeContext context
    ) {
        com.openjiuwen.agent_teams.schema.TeamSpec teamSpec = new com.openjiuwen.agent_teams.schema.TeamSpec();
        teamSpec.setTeamName(spec.getTeamName());
        teamSpec.setDisplayName(spec.getTeamName());
        teamSpec.setLeaderMemberName(context.getMemberName());
        teamSpec.setLanguage(TeamAgentSpec.resolveLanguage(spec.getLanguage()));
        return teamSpec;
    }

    private static Object firstPresent(Map<?, ?> map, String first, String second) {
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }

    private static MessagerTransportConfig messagerConfigFromMap(Map<String, Object> map) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        if (map == null) {
            return config;
        }
        if (map.get("backend") != null) {
            config.setBackend(String.valueOf(map.get("backend")));
        }
        if (map.get("team_name") != null) {
            config.setTeamName(String.valueOf(map.get("team_name")));
        }
        if (map.get("node_id") != null) {
            config.setNodeId(String.valueOf(map.get("node_id")));
        }
        if (map.get("direct_addr") != null) {
            config.setDirectAddr(String.valueOf(map.get("direct_addr")));
        }
        if (map.get("pubsub_publish_addr") != null) {
            config.setPubsubPublishAddr(String.valueOf(map.get("pubsub_publish_addr")));
        }
        if (map.get("pubsub_subscribe_addr") != null) {
            config.setPubsubSubscribeAddr(String.valueOf(map.get("pubsub_subscribe_addr")));
        }
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> typed = new LinkedHashMap<>();
            source.forEach((key, mapValue) -> typed.put(String.valueOf(key), mapValue));
            return typed;
        }
        return null;
    }

    private static Map<String, Object> normalizeInputs(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            return normalized;
        }
        if (inputs instanceof String text) {
            return new LinkedHashMap<>(Map.of("query", text));
        }
        return new LinkedHashMap<>(Map.of("query", String.valueOf(inputs)));
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
