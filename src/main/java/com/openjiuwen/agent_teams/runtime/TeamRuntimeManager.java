/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.interaction.GodViewMessage;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox;
import com.openjiuwen.agent_teams.interaction.HumanAgentMessage;
import com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError;
import com.openjiuwen.agent_teams.interaction.InteractPayload;
import com.openjiuwen.agent_teams.interaction.InteractionRouter;
import com.openjiuwen.agent_teams.interaction.OperatorMessage;
import com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agent_teams.interaction.UserInbox;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Owns the active team-runtime pool and dispatches runtime lifecycle effects.
 *
 * <p>Mirrors Python's {@code TeamRuntimeManager} in
 * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
 */
public class TeamRuntimeManager {

    private static final List<RunActionKind> REJECT_KINDS = List.of(
            RunActionKind.REJECT_RUNNING,
            RunActionKind.REJECT_ORPHANED,
            RunActionKind.REJECT_INCONSISTENT
    );

    private final RuntimePool pool;
    private final SessionInspector sessionInspector;
    private final RuntimeCleanup cleanup;
    private final MonitorFactory monitorFactory;

    public TeamRuntimeManager() {
        this(SessionInspector.empty(), RuntimeCleanup.noop(), (agent, hideDm) -> agent);
    }

    public TeamRuntimeManager(
            SessionInspector sessionInspector,
            RuntimeCleanup cleanup,
            MonitorFactory monitorFactory
    ) {
        this.pool = new RuntimePool();
        this.sessionInspector = sessionInspector == null ? SessionInspector.empty() : sessionInspector;
        this.cleanup = cleanup == null ? RuntimeCleanup.noop() : cleanup;
        this.monitorFactory = monitorFactory == null ? (agent, hideDm) -> agent : monitorFactory;
    }

    public RuntimePool pool() {
        return pool;
    }

    public CompletionStage<TeamRuntimeActivation> activate(
            TeamSpecView spec,
            AgentTeamSessionView session,
            Object inputs
    ) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(session, "session");
        String teamName = spec.teamName();
        String targetSessionId = session.getSessionId();
        RuntimeEntry poolEntry = pool.get(teamName);

        CompletionStage<Boolean> staleStop = poolEntry != null
                && !Objects.equals(poolEntry.currentSessionId(), targetSessionId)
                ? stopTeam(teamName, poolEntry.currentSessionId())
                : CompletableFuture.completedFuture(false);

        return staleStop.thenCompose(ignored -> {
            RuntimeEntry currentEntry = pool.get(teamName);
            return sessionInspector.inspect(spec, session, teamName).thenCompose(inspection -> {
                RunAction action = TeamRunDispatcher.decideRunAction(
                        inspection.teamInDb(),
                        inspection.teamInSession(),
                        currentEntry,
                        targetSessionId,
                        teamName,
                        inspection.teamDbState()
                );
                return applyAction(action, spec, session, currentEntry, inputs);
            });
        });
    }

    public CompletionStage<TeamRuntimeActivation> activate(TeamSpecView spec, AgentTeamSessionView session) {
        return activate(spec, session, null);
    }

    public CompletionStage<Void> finalizeTeam(String teamName, String sessionId) {
        RuntimeEntry entry = resolveEntry(teamName, sessionId);
        if (entry == null) {
            return CompletableFuture.completedFuture(null);
        }
        TeamAgentRuntime agent = entry.agent();
        return agent.isShutdownRequested()
                .handle((shutdownRequested, throwable) -> throwable == null && Boolean.TRUE.equals(shutdownRequested))
                .thenCompose(shutdownRequested -> {
                    if (shutdownRequested || !"persistent".equals(agent.lifecycle())) {
                        return agent.stopCoordination()
                                .handle((ignored, throwable) -> null)
                                .thenRun(() -> pool.remove(teamName));
                    }
                    return agent.pauseCoordination().thenRun(() -> entry.setState(RuntimeState.PAUSED));
                });
    }

    public static CompletionStage<Void> finalizeMember(TeamAgentRuntime agent) {
        if (agent == null) {
            return CompletableFuture.completedFuture(null);
        }
        TeamMemberRuntime member = agent.teamMember();
        CompletionStage<MemberStatus> statusStage = member == null
                ? CompletableFuture.completedFuture(null)
                : member.status().exceptionally(ignored -> null);
        return statusStage.thenCompose(status -> {
            if (status == MemberStatus.STOPPED || status == MemberStatus.PAUSED || status == MemberStatus.SHUTDOWN) {
                return agent.stopCoordination().handle((ignored, throwable) -> null);
            }
            if (status == MemberStatus.SHUTDOWN_REQUESTED) {
                return agent.stopCoordination()
                        .thenCompose(ignored -> member == null
                                ? CompletableFuture.completedFuture(null)
                                : member.updateStatus(MemberStatus.SHUTDOWN));
            }
            return agent.pauseCoordination().thenCompose(ignored -> member == null
                    ? CompletableFuture.completedFuture(null)
                    : member.updateStatus(MemberStatus.READY));
        }).handle((ignored, throwable) -> null);
    }

    public CompletionStage<Boolean> pause(String teamName, String sessionId) {
        RuntimeEntry entry = resolveEntry(teamName, sessionId);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }
        return entry.agent().pauseCoordination().thenApply(ignored -> {
            entry.setState(RuntimeState.PAUSED);
            return true;
        });
    }

    public CompletionStage<DeliverResult> interact(Object payload, String teamName, String sessionId) {
        RuntimeEntry entry = resolveEntry(teamName, sessionId);
        if (entry == null) {
            return CompletableFuture.completedFuture(DeliverResult.failure("not_active"));
        }

        if (payload instanceof InteractiveInput interactiveInput) {
            if (entry.agent().hasPendingInterrupt()) {
                return entry.agent().resumeInterrupt(interactiveInput).thenApply(ignored -> DeliverResult.success());
            }
            return CompletableFuture.completedFuture(DeliverResult.failure("unsupported_interactive_input"));
        }

        List<InteractPayload> payloads;
        if (payload instanceof String text) {
            payloads = InteractionRouter.parseInteractStr(text);
            if (payloads.isEmpty()) {
                payloads = List.of(new GodViewMessage(text));
            }
        } else if (payload instanceof InteractPayload typedPayload) {
            payloads = List.of(typedPayload);
        } else {
            String typeName = payload == null ? "null" : payload.getClass().getSimpleName();
            return CompletableFuture.completedFuture(DeliverResult.failure("unknown_payload:" + typeName));
        }

        AdmissionTicket ticket = entry.interactGate().admit();
        if (ticket == null) {
            return CompletableFuture.completedFuture(DeliverResult.failure("gate_closed"));
        }
        return dispatchPayloads(entry.agent(), payloads)
                .whenComplete((ignored, throwable) -> entry.interactGate().consumeDone(ticket));
    }

    public static CompletionStage<DeliverResult> dispatchPayloads(
            TeamAgentRuntime agent,
            List<InteractPayload> payloads
    ) {
        return resolveRecipients(agent, payloads).thenCompose(TeamRuntimeManager::dispatchPayloadsSequentially);
    }

    private static CompletionStage<DeliverResult> dispatchPayloadsSequentially(DispatchPlan plan) {
        CompletionStage<DeliverResult> chain = CompletableFuture.completedFuture(DeliverResult.success());
        for (InteractPayload payload : plan.payloads()) {
            chain = chain.thenCompose(previous -> {
                if (!previous.ok()) {
                    return CompletableFuture.completedFuture(previous);
                }
                return dispatchPayload(plan.agent(), payload);
            });
        }
        return chain;
    }

    public CompletionStage<Boolean> registerHumanAgentInbound(
            String teamName,
            String sessionId,
            String memberName,
            Object callback
    ) {
        RuntimeEntry entry = resolveEntry(teamName, sessionId);
        if (entry == null || entry.agent().teamBackend() == null) {
            return CompletableFuture.completedFuture(false);
        }
        return entry.agent().teamBackend().registerHumanAgentInbound(memberName, callback).thenApply(ignored -> true);
    }

    public CompletionStage<Boolean> stopTeam(String teamName, String sessionId) {
        RuntimeEntry entry = resolveEntry(teamName, sessionId);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }
        return entry.agent().stopCoordination()
                .handle((ignored, throwable) -> null)
                .thenApply(ignored -> {
                    pool.remove(teamName);
                    return true;
                });
    }

    public CompletionStage<Object> getMonitor(String teamName, String sessionId, boolean hideDm) {
        RuntimeEntry entry = resolveEntry(teamName, sessionId);
        if (entry == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(monitorFactory.create(entry.agent(), hideDm));
    }

    public List<RuntimeEntryInfo> listActiveTeams() {
        return pool.listAllInfo();
    }

    public CompletionStage<Boolean> deleteTeam(String teamName, List<String> sessionIds, boolean force) {
        RuntimeEntry entry = pool.get(teamName);
        CompletionStage<Boolean> stopStage;
        if (entry != null && !force) {
            return failed(new IllegalStateException(
                    "team has an active runtime; stop_team before delete_team or pass force=True"
            ));
        }
        if (entry != null) {
            stopStage = stopTeam(teamName, entry.currentSessionId());
        } else {
            stopStage = CompletableFuture.completedFuture(false);
        }
        return stopStage.thenCompose(ignored -> cleanup.deleteTeam(teamName, safeList(sessionIds)));
    }

    public CompletionStage<Void> releaseSession(String sessionId, boolean force) {
        if (sessionId == null || sessionId.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<RuntimeEntry> activeTeams = pool.teamsForSession(sessionId);
        if (!activeTeams.isEmpty() && !force) {
            String names = String.join(", ", activeTeams.stream().map(RuntimeEntry::teamName).toList());
            return failed(new IllegalStateException(
                    "team(s) active on this session; stop_team or pause_team first, or pass force=True: " + names
            ));
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (RuntimeEntry entry : activeTeams) {
            chain = chain.thenCompose(ignored -> stopTeam(entry.teamName(), sessionId).thenApply(stopped -> null));
        }
        return chain.thenCompose(ignored -> cleanup.releaseSession(sessionId));
    }

    public CompletionStage<Void> releaseSession(String sessionId) {
        return releaseSession(sessionId, false);
    }

    public RuntimeEntry resolveEntry(String teamName, String sessionId) {
        RuntimeEntry entry = pool.get(teamName);
        if (entry == null || !Objects.equals(entry.currentSessionId(), sessionId)) {
            return null;
        }
        return entry;
    }

    private CompletionStage<TeamRuntimeActivation> applyAction(
            RunAction action,
            TeamSpecView spec,
            AgentTeamSessionView session,
            RuntimeEntry poolEntry,
            Object inputs
    ) {
        String teamName = spec.teamName();
        String sessionId = session.getSessionId();
        if (REJECT_KINDS.contains(action.kind())) {
            return CompletableFuture.completedFuture(new TeamRuntimeActivation(
                    poolEntry == null ? null : poolEntry.agent(),
                    session,
                    action
            ));
        }

        if (action.kind() == RunActionKind.RESUME_FROM_PAUSE) {
            if (poolEntry == null) {
                return failed(new IllegalStateException("resume_from_pause requires an active pool entry"));
            }
            return preRunWithInputs(session, inputs).thenApply(ignored -> {
                poolEntry.setState(RuntimeState.RUNNING);
                poolEntry.interactGate().reset();
                return new TeamRuntimeActivation(poolEntry.agent(), session, action);
            });
        }

        CompletionStage<TeamAgentRuntime> agentStage;
        if (action.kind() == RunActionKind.COLD_RECOVER) {
            TeamAgentRuntime agent = spec.recoverFromSession(session, teamName);
            agentStage = agent.recoverTeam().thenApply(ignored -> agent);
        } else if (action.kind() == RunActionKind.NEW_TEAM_IN_SESSION) {
            agentStage = preRunWithInputs(session, inputs).thenCompose(ignored -> {
                TeamAgentRuntime agent = spec.build();
                return agent.resumeForNewSession(session)
                        .thenCompose(resumed -> agent.recoverTeam())
                        .thenCompose(recovered -> flushTeamManifest(agent, session))
                        .thenApply(flushed -> agent);
            });
        } else if (action.kind() == RunActionKind.CREATE) {
            agentStage = preRunWithInputs(session, inputs).thenCompose(ignored -> {
                TeamAgentRuntime agent = spec.build();
                return flushTeamManifest(agent, session).thenApply(flushed -> agent);
            });
        } else {
            return failed(new IllegalStateException("Unhandled RunActionKind: " + action.kind()));
        }

        return agentStage.thenApply(agent -> {
            pool.add(new RuntimeEntry(teamName, agent, sessionId, RuntimeState.RUNNING));
            return new TeamRuntimeActivation(agent, session, action);
        });
    }

    private static CompletionStage<DispatchPlan> resolveRecipients(
            TeamAgentRuntime agent,
            List<InteractPayload> payloads
    ) {
        TeamBackendRuntime backend = agent.teamBackend();
        if (backend == null) {
            return CompletableFuture.completedFuture(new DispatchPlan(agent, payloads));
        }
        return InteractionRouter.resolveTargets(payloads, name -> backend.getMember(name).thenApply(Objects::nonNull))
                .thenApply(resolved -> new DispatchPlan(agent, resolved));
    }

    private static CompletionStage<DeliverResult> dispatchPayload(TeamAgentRuntime agent, InteractPayload payload) {
        TeamBackendRuntime backend = agent.teamBackend();
        if (backend == null && !(payload instanceof GodViewMessage)) {
            return CompletableFuture.completedFuture(DeliverResult.failure("no_team_backend"));
        }

        if (payload instanceof GodViewMessage message) {
            return UserInbox.deliverToLeader(agent::deliverInput, message.body());
        }
        if (payload instanceof OperatorMessage message) {
            UserInbox inbox = new UserInbox(backend.messageManager());
            if (message.target() == null) {
                return agent.autoStartAll().thenCompose(ignored -> inbox.broadcast(message.body()));
            }
            return agent.autoStartMember(message.target()).thenCompose(ignored -> inbox.direct(message.target(), message.body()));
        }
        if (payload instanceof HumanAgentMessage message) {
            CompletionStage<Void> startup;
            if (message.target() == null) {
                startup = CompletableFuture.completedFuture(null);
            } else if ("all".equals(message.target()) || "*".equals(message.target())) {
                startup = agent.autoStartAll();
            } else {
                startup = agent.autoStartMember(message.target());
            }
            HumanAgentInbox inbox = new HumanAgentInbox(
                    backend,
                    backend.messageManager(),
                    memberName -> agent.lookupHumanAgentRuntime(memberName)
                            .thenApply(runtime -> (HumanAgentInbox.AgentRuntime) runtime)
            );
            return startup.thenCompose(ignored -> inbox.send(message.body(), message.target(), message.sender()))
                    .handle((result, throwable) -> {
                        if (throwable == null) {
                            return result;
                        }
                        Throwable cause = unwrap(throwable);
                        if (cause instanceof HumanAgentNotEnabledError) {
                            return DeliverResult.failure("human_agent_not_enabled");
                        }
                        if (cause instanceof UnknownHumanAgentError) {
                            return DeliverResult.failure("unknown_human_agent");
                        }
                        throw new CompletionException(cause);
                    });
        }
        return CompletableFuture.completedFuture(DeliverResult.failure("unknown_payload:" + payload.getClass().getSimpleName()));
    }

    private static CompletionStage<Void> preRunWithInputs(AgentTeamSessionView session, Object inputs) {
        return session.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null);
    }

    private static CompletionStage<Void> flushTeamManifest(TeamAgentRuntime agent, AgentTeamSessionView session) {
        agent.persistSessionManifest(session);
        return session.flushCheckpoint();
    }

    private static Map<String, Object> copyStringMap(Map<?, ?> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private static List<String> safeList(Collection<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletionStage<T> failed(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * Top-level runtime state held by the manager's pool.
     *
     * <p>Mirrors Python manager usage of {@code RuntimeState} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public enum RuntimeState {
        RUNNING("running"),
        PAUSED("paused");

        private final String value;

        RuntimeState(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Active runtime entry owned by the manager-local pool.
     *
     * <p>Mirrors Python manager access to {@code ActiveTeam} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public static final class RuntimeEntry implements TeamRunDispatcher.PoolEntryView {
        private final String teamName;
        private final TeamAgentRuntime agent;
        private final String currentSessionId;
        private RuntimeState state;
        private final InteractGate interactGate;

        public RuntimeEntry(String teamName, TeamAgentRuntime agent, String currentSessionId, RuntimeState state) {
            this(teamName, agent, currentSessionId, state, new InteractGate());
        }

        public RuntimeEntry(
                String teamName,
                TeamAgentRuntime agent,
                String currentSessionId,
                RuntimeState state,
                InteractGate interactGate
        ) {
            this.teamName = teamName;
            this.agent = Objects.requireNonNull(agent, "agent");
            this.currentSessionId = currentSessionId;
            this.state = state == null ? RuntimeState.RUNNING : state;
            this.interactGate = interactGate == null ? new InteractGate() : interactGate;
        }

        public String teamName() {
            return teamName;
        }

        public TeamAgentRuntime agent() {
            return agent;
        }

        @Override
        public String currentSessionId() {
            return currentSessionId;
        }

        public RuntimeState runtimeState() {
            return state;
        }

        @Override
        public String state() {
            return state.getValue();
        }

        public void setState(RuntimeState state) {
            this.state = state == null ? RuntimeState.RUNNING : state;
        }

        public InteractGate interactGate() {
            return interactGate;
        }
    }

    /**
     * Read-only active-runtime snapshot.
     *
     * <p>Mirrors Python manager return of {@code ActiveTeamInfo} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public record RuntimeEntryInfo(String teamName, String currentSessionId, RuntimeState state, boolean gateClosed) {
    }

    /**
     * Process-local pool tracking active runtime entries.
     *
     * <p>Mirrors Python manager ownership of {@code TeamRuntimePool} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public static final class RuntimePool {
        private final Map<String, RuntimeEntry> teams = new LinkedHashMap<>();

        public synchronized RuntimeEntry get(String teamName) {
            return teams.get(teamName);
        }

        public synchronized boolean hasActive(String teamName) {
            return teams.containsKey(teamName);
        }

        public synchronized void add(RuntimeEntry entry) {
            teams.put(entry.teamName(), entry);
        }

        public synchronized RuntimeEntry remove(String teamName) {
            return teams.remove(teamName);
        }

        public synchronized List<String> listTeamNames() {
            return new ArrayList<>(teams.keySet());
        }

        public synchronized List<RuntimeEntry> teamsForSession(String sessionId) {
            return teams.values().stream()
                    .filter(entry -> Objects.equals(entry.currentSessionId(), sessionId))
                    .toList();
        }

        public synchronized List<RuntimeEntryInfo> listAllInfo() {
            return teams.values().stream()
                    .map(entry -> new RuntimeEntryInfo(
                            entry.teamName(),
                            entry.currentSessionId(),
                            entry.runtimeState(),
                            entry.interactGate().isClosed()
                    ))
                    .toList();
        }
    }

    /**
     * Minimal TeamAgent runtime surface consumed by manager.py.
     *
     * <p>Mirrors Python manager calls on {@code TeamAgent} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface TeamAgentRuntime extends HumanAgentInbox.AgentRuntime {
        @Override
        CompletionStage<Void> deliverInput(String body);

        default CompletionStage<Void> pauseCoordination() {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<Void> stopCoordination() {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<Boolean> isShutdownRequested() {
            return CompletableFuture.completedFuture(false);
        }

        default String lifecycle() {
            return "persistent";
        }

        default TeamBackendRuntime teamBackend() {
            return null;
        }

        default boolean hasPendingInterrupt() {
            return false;
        }

        default CompletionStage<Void> resumeInterrupt(InteractiveInput input) {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<Void> autoStartAll() {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<Void> autoStartMember(String memberName) {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<TeamAgentRuntime> lookupHumanAgentRuntime(String memberName) {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<Void> resumeForNewSession(AgentTeamSessionView session) {
            return CompletableFuture.completedFuture(null);
        }

        default CompletionStage<Void> recoverTeam() {
            return CompletableFuture.completedFuture(null);
        }

        default void persistSessionManifest(AgentTeamSessionView session) {
        }

        default TeamMemberRuntime teamMember() {
            return null;
        }
    }

    /**
     * Minimal team backend surface consumed by manager.py.
     *
     * <p>Mirrors Python manager calls on {@code TeamBackend} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface TeamBackendRuntime extends HumanAgentInbox.TeamBackendView {
        TeamMessageManagerRuntime messageManager();

        default CompletionStage<Void> registerHumanAgentInbound(String memberName, Object callback) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Shared message manager view for user and human-agent inboxes.
     *
     * <p>Mirrors Python manager use of {@code TeamMessageManager} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface TeamMessageManagerRuntime
            extends UserInbox.MessageManagerView, HumanAgentInbox.MessageManagerView {
    }

    /**
     * Minimal member status surface used by {@link #finalizeMember(TeamAgentRuntime)}.
     *
     * <p>Mirrors Python manager calls on {@code TeamMember} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface TeamMemberRuntime {
        CompletionStage<MemberStatus> status();

        CompletionStage<Void> updateStatus(MemberStatus status);
    }

    /**
     * Team-member statuses relevant to manager finalize decisions.
     *
     * <p>Mirrors Python manager use of {@code MemberStatus} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public enum MemberStatus {
        READY,
        STOPPED,
        PAUSED,
        SHUTDOWN,
        SHUTDOWN_REQUESTED
    }

    /**
     * Minimal session surface used by activation and pre-run hooks.
     *
     * <p>Mirrors Python manager use of {@code AgentTeamSession} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface AgentTeamSessionView {
        String getSessionId();

        CompletionStage<Void> preRun(Map<String, Object> inputs);

        CompletionStage<Void> flushCheckpoint();
    }

    /**
     * Minimal team spec surface used to construct or recover a runtime.
     *
     * <p>Mirrors Python manager use of {@code TeamAgentSpec} in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface TeamSpecView {
        String teamName();

        TeamAgentRuntime build();

        default TeamAgentRuntime recoverFromSession(AgentTeamSessionView session, String teamName) {
            return build();
        }
    }

    /**
     * Session and DB inspection result used by activation dispatch.
     *
     * <p>Mirrors Python's {@code _inspect_session} tuple in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public record SessionInspection(boolean teamInSession, boolean teamInDb, String teamDbState) {
    }

    /**
     * Inspect session checkpoint and DB state for the dispatch truth table.
     *
     * <p>Mirrors Python's {@code _inspect_session} hook in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    @FunctionalInterface
    public interface SessionInspector {
        CompletionStage<SessionInspection> inspect(
                TeamSpecView spec,
                AgentTeamSessionView session,
                String teamName
        );

        static SessionInspector empty() {
            return (spec, session, teamName) -> CompletableFuture.completedFuture(
                    new SessionInspection(false, false, null)
            );
        }
    }

    /**
     * External cleanup boundary for release/delete effects.
     *
     * <p>Mirrors Python manager cleanup calls in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    public interface RuntimeCleanup {
        CompletionStage<Void> releaseSession(String sessionId);

        CompletionStage<Boolean> deleteTeam(String teamName, List<String> sessionIds);

        static RuntimeCleanup noop() {
            return new RuntimeCleanup() {
                @Override
                public CompletionStage<Void> releaseSession(String sessionId) {
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<Boolean> deleteTeam(String teamName, List<String> sessionIds) {
                    return CompletableFuture.completedFuture(true);
                }
            };
        }
    }

    /**
     * Monitor factory boundary used by {@link #getMonitor(String, String, boolean)}.
     *
     * <p>Mirrors Python's {@code create_monitor} call in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    @FunctionalInterface
    public interface MonitorFactory {
        Object create(TeamAgentRuntime agent, boolean hideDm);
    }

    /**
     * Resolved dispatch inputs used to preserve payload order.
     *
     * <p>Mirrors Python's {@code interact} dispatch plan in
     * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
     */
    private record DispatchPlan(TeamAgentRuntime agent, List<InteractPayload> payloads) {
    }
}
