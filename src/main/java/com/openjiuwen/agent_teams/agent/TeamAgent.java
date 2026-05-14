/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.interaction.HumanAgentInbox;
import com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError;
import com.openjiuwen.agent_teams.interaction.MentionParser;
import com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agent_teams.interaction.UserInbox;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        this.spec = teamAgentSpec;
        this.runtimeContext = buildRuntimeContext(teamAgentSpec);
        this.deepAgent = buildLeaderDeepAgent(teamAgentSpec);
        this.teamBackend = buildTeamBackend(teamAgentSpec);
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
        this.sessionManager = new SessionManager(this::getLifecycle, this::getTeamBackend, this.recoveryManager);
        this.deepAgent.getDelegate().registerRail(firstIterationGate);
        this.teamBackend.registerPredefinedMembers();
        this.deepAgent.getDelegate().getAbilityManager().add(
                AgentTeamsToolRegistry.createTeamTools(
                        this.teamBackend,
                        runtimeContext.getRole(),
                        teamAgentSpec.getTeammateMode()
                ).stream().map(tool -> tool.getCard()).toList()
        );
        return this;
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

    public TeamDispatcher getDispatcher() {
        return dispatcher;
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

    public void stopCoordination() {
        if (coordinatorLoop != null) {
            coordinatorLoop.stop();
        }
    }

    public void notifyEvent(String eventType, Map<String, Object> payload) {
        if (coordinatorLoop != null) {
            coordinatorLoop.wake(new CoordinationEvent(eventType, payload));
        }
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
        teamSpec.setLanguage(spec.getLanguage());
        teamSpec.setMetadata(spec.getMetadata());
        context.setTeamSpec(teamSpec);
        return context;
    }

    private DeepAgent buildLeaderDeepAgent(TeamAgentSpec spec) {
        DeepAgentSpec leaderSpec = spec.getAgents().get("leader");
        if (leaderSpec == null || leaderSpec.getConfig() == null) {
            DeepAgentConfig fallback = new DeepAgentConfig();
            fallback.setCard(createLeaderCard(spec));
            fallback.setSystemPrompt("You are the leader of the team '" + spec.getTeamName() + "'.");
            return com.openjiuwen.harness.HarnessFactory.createDeepAgent(fallback);
        }
        DeepAgentConfig config = leaderSpec.getConfig();
        if (config.getCard() == null) {
            config.setCard(createLeaderCard(spec));
        }
        String basePrompt = config.getSystemPrompt() != null ? config.getSystemPrompt() : "";
        String teamPrompt = "\n\nYou are coordinating the agent team '" + spec.getTeamName()
                + "' as leader '" + runtimeContext.getMemberName() + "'.";
        config.setSystemPrompt(basePrompt + teamPrompt);
        return com.openjiuwen.harness.HarnessFactory.createDeepAgent(config);
    }

    private TeamBackend buildTeamBackend(TeamAgentSpec spec) {
        return new TeamBackend(
                spec.getTeamName(),
                runtimeContext.getMemberName(),
                true,
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
                    "language", runtimeContext.getTeamSpec().getLanguage()
            ));
        }
        return result;
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
