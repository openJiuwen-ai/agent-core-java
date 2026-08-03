/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamStandbyEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceManager;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns coordination lifecycle, transport wiring, and inner event ingress.
 *
 * <p>Mirrors Python's {@code CoordinationKernel} in
 * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
 */
public class CoordinationKernel {

    private static final Logger LOGGER = LoggerFactory.getLogger(CoordinationKernel.class);

    private final KernelHost host;
    private final List<String> subscribedTopics = new ArrayList<>();
    private EventBus eventBus;
    private EventDispatcher dispatcher;
    private String lifecycleState = "idle";

    public CoordinationKernel(KernelHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    public void setup(TeamRole role) {
        TeamAgentBlueprint blueprint = host.getBlueprint();
        TeamInfra infra = host.getInfra();
        if (blueprint == null || infra == null) {
            throw new IllegalStateException("CoordinationKernel.setup() requires configured blueprint and infra");
        }
        EventBus createdBus = new EventBus(role);
        this.eventBus = createdBus;
        this.dispatcher = new EventDispatcher(host, blueprint, infra, createdBus);
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public EventDispatcher getDispatcher() {
        return dispatcher;
    }

    public List<String> getSubscribedTopics() {
        return subscribedTopics;
    }

    public String getLifecycleState() {
        return lifecycleState;
    }

    public CompletionStage<Void> enqueue(CoordinationEvent event) {
        if (eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        return eventBus.enqueue(event);
    }

    public CompletionStage<Void> enqueue(EventMessage event) {
        if (eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        return eventBus.enqueue(event);
    }

    public boolean isRunning() {
        return eventBus != null && eventBus.isRunning();
    }

    public CompletionStage<Void> start(Object session) {
        if (eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        String memberName = host.getMemberName() == null || host.getMemberName().isEmpty()
                ? "?"
                : host.getMemberName();
        LOGGER.info("[{}] coordination starting", memberName);
        TeamInfra infra = host.getInfra();

        CompletionStage<Void> chain = initializeBackend(infra)
                .thenCompose(ignored -> bindOrReleaseSession(session))
                .thenCompose(ignored -> maybeRecoverOrCleanLeaderTeam(infra))
                .thenCompose(ignored -> initializeWorkspace(infra))
                .thenCompose(ignored -> initializeMemoryToolkit())
                .thenCompose(ignored -> host.updateStatus(MemberStatus.READY))
                .thenCompose(ignored -> startEventBusIfNeeded())
                .thenCompose(ignored -> {
                    Messager messager = infra == null ? null : infra.getMessager();
                    String teamName = host.getTeamName();
                    if (messager != null && teamName != null && !teamName.isEmpty() && subscribedTopics.isEmpty()) {
                        return subscribeTransport(teamName);
                    }
                    return CompletableFuture.completedFuture(null);
                })
                .thenRun(() -> {
                    rearmTeamCompletion();
                    lifecycleState = "running";
                });
        return chain;
    }

    public CompletionStage<Void> pause() {
        if (!"running".equals(lifecycleState)) {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("[{}] coordination pausing (persistent)", host.getMemberNameOrUnknown());
        CompletionStage<Void> chain = drainAgentTask()
                .thenRun(host::persistAllocatorState)
                .thenCompose(ignored -> {
                    if (host.getRole() != TeamRole.LEADER) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return host.markLiveTeammates(MemberStatus.PAUSED)
                            .thenCompose(next -> host.getSpawnManager().cancelRecoveryTasks())
                            .thenCompose(next -> host.getSpawnManager().shutdownAllHandles())
                            .thenRun(() -> host.persistTeamLifecycle("paused"));
                })
                .thenCompose(ignored -> publishStandbyIfLeader())
                .thenCompose(ignored -> unsubscribeTransport())
                .thenCompose(ignored -> eventBus == null ? CompletableFuture.completedFuture(null) : eventBus.stop())
                .thenRun(() -> {
                    closeStream();
                    host.getSessionManager().releaseSession();
                    lifecycleState = "paused";
                });
        return chain;
    }

    public CompletionStage<Void> stop() {
        if ("idle".equals(lifecycleState) || "stopped".equals(lifecycleState)) {
            return CompletableFuture.completedFuture(null);
        }
        LOGGER.info("[{}] coordination stopping", host.getMemberNameOrUnknown());
        CompletionStage<Void> chain = drainAgentTask()
                .thenRun(host::persistAllocatorState)
                .thenCompose(ignored -> host.getRole() == TeamRole.LEADER
                        ? host.markLiveTeammates(MemberStatus.STOPPED)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> unsubscribeTransport())
                .thenCompose(ignored -> host.getSpawnManager().cancelRecoveryTasks())
                .thenCompose(ignored -> host.getSpawnManager().shutdownAllHandles())
                .thenCompose(ignored -> closeMemoryManager())
                .thenCompose(ignored -> eventBus == null ? CompletableFuture.completedFuture(null) : eventBus.stop())
                .thenRun(() -> {
                    closeStream();
                    host.getSessionManager().releaseSession();
                    lifecycleState = "stopped";
                });
        return chain;
    }

    public CompletionStage<Void> subscribeTransport(String teamName) {
        Messager messager = host.getInfra() == null ? null : host.getInfra().getMessager();
        if (messager == null || eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        String localMemberName = host.getMemberName() == null ? "" : host.getMemberName();
        return messager.registerDirectMessageHandler(eventBus::enqueue)
                .thenCompose(ignored -> {
                    CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
                    for (TeamTopic topic : TeamTopic.values()) {
                        String topicId = topic.build(AgentTeamsContext.getSessionId(), teamName);
                        chain = chain.thenCompose(next -> messager.subscribe(topicId, event -> {
                            CompletionStage<Void> listenerChain = notifyEventListeners(event);
                            return listenerChain.thenCompose(listenerIgnored -> {
                                if (!localMemberName.isEmpty() && localMemberName.equals(event.getSenderId())) {
                                    LOGGER.debug("ignoring self-published event: {}", event.getEventType());
                                    return CompletableFuture.completedFuture(null);
                                }
                                return eventBus.enqueue(event);
                            });
                        }).thenRun(() -> subscribedTopics.add(topicId)));
                    }
                    return chain;
                });
    }

    public CompletionStage<Void> unsubscribeTransport() {
        Messager messager = host.getInfra() == null ? null : host.getInfra().getMessager();
        if (messager == null) {
            subscribedTopics.clear();
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> chain = messager.unregisterDirectMessageHandler()
                .exceptionally(exception -> {
                    LOGGER.debug("failed to unregister direct message handler during cleanup");
                    return (Void) null;
                });
        for (String topic : List.copyOf(subscribedTopics)) {
            chain = chain.thenCompose(ignored -> messager.unsubscribe(topic)
                    .exceptionally(exception -> {
                        LOGGER.debug("failed to unsubscribe topic {} during cleanup", topic);
                        return (Void) null;
                    }));
        }
        return chain.thenRun(subscribedTopics::clear);
    }

    public CompletionStage<Void> enqueueUserInput(Object inputs) {
        if (eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        Object query = "";
        if (inputs instanceof Map<?, ?> map) {
            query = map.containsKey("query") ? map.get("query") : "";
        } else if (inputs != null) {
            query = inputs;
        }
        return eventBus.enqueue(new InnerEventMessage(InnerEventType.USER_INPUT, Map.of("content", query)));
    }

    public CompletionStage<Void> enqueueMailboxAfterFirstIteration() {
        if (host.getRole() == TeamRole.LEADER || eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        Object gate = host.getResources() == null ? null : host.getResources().getFirstIterGate();
        if (!(gate instanceof FirstIterationGate firstIterationGate)) {
            return CompletableFuture.completedFuture(null);
        }
        return firstIterationGate.waitReady()
                .thenCompose(ignored -> eventBus.enqueue(new InnerEventMessage(InnerEventType.POLL_MAILBOX)));
    }

    public CompletionStage<Void> drainAgentTask() {
        return host.getStreamController().drainAgentTask();
    }

    public void closeStream() {
        host.getStreamController().closeStream();
    }

    public CompletionStage<Void> wakeMailboxIfInterruptCleared() {
        if (host.getRole() != TeamRole.TEAMMATE || host.hasPendingInterrupt() || eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        return eventBus.enqueue(new InnerEventMessage(InnerEventType.POLL_MAILBOX));
    }

    public CompletionStage<Void> finalizeRound() {
        return extractMemoryAfterRound()
                .thenRun(() -> host.getStreamController().clearStreamQueue());
    }

    private CompletionStage<Void> initializeBackend(TeamInfra infra) {
        if (infra != null && infra.getTeamBackend() instanceof KernelTeamBackend backend) {
            return backend.initialize();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> bindOrReleaseSession(Object session) {
        if (session != null) {
            return host.getSessionManager().bindSession(session);
        }
        host.getSessionManager().releaseSession();
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> maybeRecoverOrCleanLeaderTeam(TeamInfra infra) {
        if (host.getRole() != TeamRole.LEADER || infra == null || !(infra.getTeamBackend() instanceof KernelTeamBackend backend)) {
            return CompletableFuture.completedFuture(null);
        }
        return backend.teamExists().thenCompose(exists -> {
            if (!exists) {
                return CompletableFuture.completedFuture(null);
            }
            return backend.allNonLeaderMembersShutdown()
                    .thenCompose(allShutdown -> allShutdown ? backend.cleanTeam() : host.recoverTeam());
        });
    }

    private CompletionStage<Void> initializeWorkspace(TeamInfra infra) {
        if (infra == null || infra.getWorkspaceManager() == null || infra.isWorkspaceInitialized()) {
            return CompletableFuture.completedFuture(null);
        }
        TeamWorkspaceManager workspaceManager = infra.getWorkspaceManager();
        String remoteUrl = null;
        TeamAgentBlueprint blueprint = host.getBlueprint();
        if (blueprint != null && blueprint.getSpec() != null) {
            TeamWorkspaceConfig workspace = blueprint.getSpec().getWorkspace();
            remoteUrl = workspace == null ? null : workspace.getRemoteUrl();
        }
        return workspaceManager.initialize(remoteUrl)
                .thenRun(() -> infra.setWorkspaceInitialized(true));
    }

    private CompletionStage<Void> initializeMemoryToolkit() {
        PrivateAgentResources resources = host.getResources();
        if (resources == null || !(resources.getMemoryManager() instanceof MemoryManagerView memoryManager)
                || resources.getHarness() == null) {
            return CompletableFuture.completedFuture(null);
        }
        MemberRuntime harness = resources.getHarness();
        return memoryManager.initToolkit().thenCompose(success -> {
            if (!success) {
                return CompletableFuture.completedFuture(null);
            }
            harness.registerMemberTools(memoryManager);
            if (memoryManager.getExtractionModel() == null) {
                memoryManager.setExtractionModel(host.getHarnessModel());
            }
            return harness.injectMemberMemory(memoryManager, host.getPendingUserQuery());
        });
    }

    private CompletionStage<Void> closeMemoryManager() {
        PrivateAgentResources resources = host.getResources();
        if (resources != null && resources.getMemoryManager() instanceof MemoryManagerView memoryManager) {
            return memoryManager.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> extractMemoryAfterRound() {
        PrivateAgentResources resources = host.getResources();
        if (resources != null && resources.getMemoryManager() instanceof MemoryManagerView memoryManager) {
            return memoryManager.extractAfterRound();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> startEventBusIfNeeded() {
        if (eventBus == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!eventBus.isRunning()) {
            if (dispatcher == null) {
                throw new IllegalStateException("CoordinationKernel.start() requires setup() before start()");
            }
            return eventBus.start(dispatcher::dispatch);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> publishStandbyIfLeader() {
        TeamInfra infra = host.getInfra();
        Messager messager = infra == null ? null : infra.getMessager();
        String teamName = host.getTeamName();
        if (messager == null || host.getRole() != TeamRole.LEADER || teamName == null || teamName.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        TeamStandbyEvent standby = new TeamStandbyEvent();
        standby.setTeamName(teamName);
        String topicId = TeamTopic.TEAM.build(AgentTeamsContext.getSessionId(), teamName);
        return messager.publish(topicId, EventMessage.fromEvent(standby))
                .exceptionally(exception -> {
                    LOGGER.error("Failed to publish TEAM_STANDBY: {}", exception.toString());
                    return (Void) null;
                });
    }

    private CompletionStage<Void> notifyEventListeners(EventMessage event) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (EventListener listener : host.getEventListeners()) {
            chain = chain.thenCompose(ignored -> listener.onEvent(event)
                    .exceptionally(exception -> {
                        LOGGER.error("Event listener error: {}", exception.toString());
                        return (Void) null;
                    }));
        }
        return chain;
    }

    private void rearmTeamCompletion() {
        if (dispatcher == null) {
            return;
        }
        Object teamCompletion = dispatcher.getTeamCompletion();
        try {
            Method rearm = teamCompletion.getClass().getMethod("rearm");
            rearm.invoke(teamCompletion);
        } catch (ReflectiveOperationException ignored) {
            // Older dispatcher facades do not expose the concrete handler hook.
        }
    }

    /**
     * Host surface consumed by {@link CoordinationKernel}.
     *
     * <p>Mirrors Python's TeamAgent collaborators used by
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface KernelHost extends EventDispatcher.DispatcherHost {
        TeamAgentBlueprint getBlueprint();

        TeamInfra getInfra();

        TeamRole getRole();

        String getMemberName();

        default String getMemberNameOrUnknown() {
            String memberName = getMemberName();
            return memberName == null || memberName.isEmpty() ? "?" : memberName;
        }

        String getTeamName();

        SessionManagerView getSessionManager();

        PrivateAgentResources getResources();

        SpawnManagerView getSpawnManager();

        StreamControllerView getStreamController();

        CompletionStage<Void> updateStatus(MemberStatus status);

        CompletionStage<Void> recoverTeam();

        CompletionStage<Void> markLiveTeammates(MemberStatus status);

        void persistAllocatorState();

        void persistTeamLifecycle(String lifecycle);

        Object getHarnessModel();

        String getPendingUserQuery();

        List<EventListener> getEventListeners();
    }

    /**
     * Session manager surface used by the kernel.
     *
     * <p>Mirrors Python's session-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface SessionManagerView {
        CompletionStage<Void> bindSession(Object session);

        void releaseSession();
    }

    /**
     * Spawn manager surface used by pause/stop.
     *
     * <p>Mirrors Python's spawn-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface SpawnManagerView {
        CompletionStage<Void> cancelRecoveryTasks();

        CompletionStage<Void> shutdownAllHandles();
    }

    /**
     * Stream controller surface used by lifecycle cleanup.
     *
     * <p>Mirrors Python's stream-controller calls in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface StreamControllerView {
        CompletionStage<Void> drainAgentTask();

        void closeStream();

        void clearStreamQueue();
    }

    /**
     * Backend surface used by start-time recovery.
     *
     * <p>Mirrors Python's team-backend calls in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface KernelTeamBackend {
        CompletionStage<Void> initialize();

        CompletionStage<Boolean> teamExists();

        CompletionStage<Boolean> allNonLeaderMembersShutdown();

        CompletionStage<Void> cleanTeam();
    }

    /**
     * Memory manager surface used by start/stop/finalize.
     *
     * <p>Mirrors Python's memory-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface MemoryManagerView {
        CompletionStage<Boolean> initToolkit();

        Object getExtractionModel();

        void setExtractionModel(Object model);

        CompletionStage<Void> extractAfterRound();

        CompletionStage<Void> close();
    }

    /**
     * First-iteration gate surface used by mailbox wakeup.
     *
     * <p>Mirrors Python's first-iteration gate wait in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface FirstIterationGate {
        CompletionStage<Void> waitReady();
    }

    /**
     * Transport event listener.
     *
     * <p>Mirrors Python's event listener callbacks in
     * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
     */
    public interface EventListener {
        CompletionStage<Void> onEvent(EventMessage event);
    }
}
