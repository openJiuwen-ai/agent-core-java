/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.PrivateAgentResources;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentCustomizer;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.EventListener;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.FirstIterationGate;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.KernelHost;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.KernelTeamBackend;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.MemoryManagerView;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.SessionManagerView;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.SpawnManagerView;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.StreamControllerView;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamStandbyEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link CoordinationKernel}.
 *
 * <p>Mirrors Python's coordination kernel behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/kernel.py}.</p>
 */
class CoordinationKernelTest {

    @Test
    void setupCreatesEventBusAndDispatcherAndEnqueueForwardsInnerEvents() {
        RecordingHost host = new RecordingHost(TeamRole.TEAMMATE, "dev");
        CoordinationKernel kernel = new CoordinationKernel(host);

        kernel.setup(TeamRole.TEAMMATE);
        kernel.enqueue(new InnerEventMessage(InnerEventType.USER_INPUT, Map.of("content", "hello")))
                .toCompletableFuture()
                .join();

        assertNotNull(kernel.getEventBus());
        assertNotNull(kernel.getDispatcher());
        assertEquals(1, kernel.getEventBus().getPendingEventCount());
        assertFalse(kernel.isRunning());
    }

    @Test
    void startInitializesCollaboratorsStartsBusAndSubscribesTransport() {
        RecordingHost host = new RecordingHost(TeamRole.LEADER, "leader");
        host.backend.teamExists = true;
        host.backend.allShutdown = false;
        host.memoryManager.initSuccess = true;
        host.resources.setMemoryManager(host.memoryManager);
        host.resources.setHarness(host.harness);
        CoordinationKernel kernel = new CoordinationKernel(host);
        kernel.setup(TeamRole.LEADER);
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("sid");
        try {
            kernel.start("session").toCompletableFuture().join();
        } finally {
            kernel.stop().toCompletableFuture().join();
            AgentTeamsContext.resetSessionId(token);
        }

        assertEquals(List.of("session"), host.sessionManager.boundSessions);
        assertEquals(List.of(MemberStatus.READY), host.statusUpdates);
        assertEquals(1, host.backend.initializeCount);
        assertEquals(1, host.recoverCount);
        assertTrue(host.harness.registeredMemory);
        assertTrue(host.harness.injectedMemory);
        assertEquals(3, host.messager.subscribedTopics.size());
        assertEquals(1, host.messager.directRegistered);
        assertEquals(1, host.messager.directUnregistered);
    }

    @Test
    void pauseIsRunningOnlyAndPublishesStandbyForLeader() {
        RecordingHost host = new RecordingHost(TeamRole.LEADER, "leader");
        CoordinationKernel kernel = new CoordinationKernel(host);
        kernel.setup(TeamRole.LEADER);
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("sid");
        try {
            kernel.start(null).toCompletableFuture().join();
            kernel.pause().toCompletableFuture().join();
            kernel.pause().toCompletableFuture().join();
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }

        assertEquals("paused", kernel.getLifecycleState());
        assertEquals(1, host.streamController.drainCount);
        assertEquals(List.of(MemberStatus.PAUSED), host.markedStatuses);
        assertEquals(1, host.spawnManager.cancelCount);
        assertEquals(1, host.spawnManager.shutdownCount);
        assertEquals(List.of("paused"), host.persistedLifecycles);
        assertEquals(1, host.streamController.closeCount);
        assertEquals(2, host.sessionManager.releaseCount);
        assertTrue(host.messager.publishedEvents.stream().anyMatch(message -> TeamEvent.STANDBY.equals(message.getEventType())));
        assertTrue(kernel.getSubscribedTopics().isEmpty());
    }

    @Test
    void stopIsTerminalAndClosesRuntimeResources() {
        RecordingHost host = new RecordingHost(TeamRole.LEADER, "leader");
        host.resources.setMemoryManager(host.memoryManager);
        CoordinationKernel kernel = new CoordinationKernel(host);
        kernel.setup(TeamRole.LEADER);

        kernel.start(null).toCompletableFuture().join();
        kernel.stop().toCompletableFuture().join();
        kernel.stop().toCompletableFuture().join();

        assertEquals("stopped", kernel.getLifecycleState());
        assertEquals(List.of(MemberStatus.STOPPED), host.markedStatuses);
        assertEquals(1, host.memoryManager.closeCount);
        assertEquals(1, host.spawnManager.cancelCount);
        assertEquals(1, host.spawnManager.shutdownCount);
        assertEquals(1, host.streamController.closeCount);
    }

    @Test
    void transportSubscriptionRunsListenersAndFiltersSelfPublishedEvents() {
        RecordingHost host = new RecordingHost(TeamRole.TEAMMATE, "dev");
        List<String> listenerEvents = new ArrayList<>();
        host.eventListeners.add(event -> {
            listenerEvents.add(event.getEventType());
            return CompletableFuture.completedFuture(null);
        });
        CoordinationKernel kernel = new CoordinationKernel(host);
        kernel.setup(TeamRole.TEAMMATE);
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("sid");
        try {
            kernel.subscribeTransport("team").toCompletableFuture().join();
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }

        EventMessage self = EventMessage.fromEvent(new TeamStandbyEvent());
        self.setSenderId("dev");
        EventMessage other = EventMessage.fromEvent(new TeamStandbyEvent());
        other.setSenderId("other");
        host.messager.handlers.get(0).handle(self).toCompletableFuture().join();
        host.messager.handlers.get(0).handle(other).toCompletableFuture().join();

        assertEquals(List.of(TeamEvent.STANDBY, TeamEvent.STANDBY), listenerEvents);
        assertEquals(1, kernel.getEventBus().getPendingEventCount());
    }

    @Test
    void userInputMailboxWakeAndFinalizeDelegateToBusAndResources() {
        RecordingHost host = new RecordingHost(TeamRole.TEAMMATE, "dev");
        host.resources.setFirstIterGate(host.firstGate);
        host.resources.setMemoryManager(host.memoryManager);
        CoordinationKernel kernel = new CoordinationKernel(host);
        kernel.setup(TeamRole.TEAMMATE);

        kernel.enqueueUserInput(Map.of("query", "hi")).toCompletableFuture().join();
        kernel.enqueueMailboxAfterFirstIteration().toCompletableFuture().join();
        kernel.wakeMailboxIfInterruptCleared().toCompletableFuture().join();
        kernel.finalizeRound().toCompletableFuture().join();

        assertEquals(3, kernel.getEventBus().getPendingEventCount());
        assertEquals(1, host.firstGate.waitCount);
        assertEquals(1, host.memoryManager.extractCount);
        assertEquals(1, host.streamController.clearCount);
    }

    private static TeamAgentBlueprint blueprint(TeamRole role, String memberName) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setLifecycle("persistent");
        return new TeamAgentBlueprint(
                new AgentCard("agent", "Agent", "description"),
                spec,
                ctx,
                "",
                "cn"
        );
    }

    private static final class RecordingHost implements KernelHost {
        private final TeamRole role;
        private final String memberName;
        private final TeamAgentBlueprint blueprint;
        private final TeamInfra infra = new TeamInfra();
        private final RecordingBackend backend = new RecordingBackend();
        private final RecordingMessager messager = new RecordingMessager();
        private final RecordingSessionManager sessionManager = new RecordingSessionManager();
        private final PrivateAgentResources resources = new PrivateAgentResources();
        private final RecordingSpawnManager spawnManager = new RecordingSpawnManager();
        private final RecordingStreamController streamController = new RecordingStreamController();
        private final RecordingMemoryManager memoryManager = new RecordingMemoryManager();
        private final RecordingHarness harness = new RecordingHarness();
        private final RecordingFirstGate firstGate = new RecordingFirstGate();
        private final List<MemberStatus> statusUpdates = new ArrayList<>();
        private final List<MemberStatus> markedStatuses = new ArrayList<>();
        private final List<String> persistedLifecycles = new ArrayList<>();
        private final List<String> lifecycleSeen = new ArrayList<>();
        private final List<EventListener> eventListeners = new ArrayList<>();
        private int recoverCount;
        private boolean pendingInterrupt;

        private RecordingHost(TeamRole role, String memberName) {
            this.role = role;
            this.memberName = memberName;
            this.blueprint = blueprint(role, memberName);
            infra.setTeamBackend(backend);
            infra.setMessager(messager);
        }

        @Override
        public TeamAgentBlueprint getBlueprint() {
            return blueprint;
        }

        @Override
        public TeamInfra getInfra() {
            return infra;
        }

        @Override
        public TeamRole getRole() {
            return role;
        }

        @Override
        public String getMemberName() {
            return memberName;
        }

        @Override
        public String getTeamName() {
            return "team";
        }

        @Override
        public SessionManagerView getSessionManager() {
            return sessionManager;
        }

        @Override
        public PrivateAgentResources getResources() {
            return resources;
        }

        @Override
        public SpawnManagerView getSpawnManager() {
            return spawnManager;
        }

        @Override
        public StreamControllerView getStreamController() {
            return streamController;
        }

        @Override
        public CompletionStage<Void> updateStatus(MemberStatus status) {
            statusUpdates.add(status);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> recoverTeam() {
            recoverCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> markLiveTeammates(MemberStatus status) {
            markedStatuses.add(status);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void persistAllocatorState() {
        }

        @Override
        public void persistTeamLifecycle(String lifecycle) {
            persistedLifecycles.add(lifecycle);
        }

        @Override
        public Object getHarnessModel() {
            return "model";
        }

        @Override
        public String getPendingUserQuery() {
            return "pending";
        }

        @Override
        public List<EventListener> getEventListeners() {
            return eventListeners;
        }

        @Override
        public boolean isAgentReady() {
            return true;
        }

        @Override
        public boolean isAgentRunning() {
            return false;
        }

        @Override
        public boolean hasInFlightRound() {
            return false;
        }

        @Override
        public boolean hasPendingInterrupt() {
            return pendingInterrupt;
        }

        @Override
        public CompletionStage<Void> cancelAgent() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deliverInput(Object content, boolean useSteer) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumeInterrupt(Object userInput) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdownSelf() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> concludeCompletedRound(int memberCount, int taskCount) {
            lifecycleSeen.add("running");
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingBackend extends ConfiguredTeamBackend implements KernelTeamBackend {
        private int initializeCount;
        private boolean teamExists;
        private boolean allShutdown;

        private RecordingBackend() {
            super("team", "leader", true, Map.of(), null, "", List.of(), null, null, true, false, List.of(), null, null, "leader");
        }

        @Override
        public CompletionStage<Void> initialize() {
            initializeCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> teamExists() {
            return CompletableFuture.completedFuture(teamExists);
        }

        @Override
        public CompletionStage<Boolean> allNonLeaderMembersShutdown() {
            return CompletableFuture.completedFuture(allShutdown);
        }

        @Override
        public CompletionStage<Void> cleanTeam() {
            allShutdown = false;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingSessionManager implements SessionManagerView {
        private final List<Object> boundSessions = new ArrayList<>();
        private int releaseCount;

        @Override
        public CompletionStage<Void> bindSession(Object session) {
            boundSessions.add(session);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void releaseSession() {
            releaseCount++;
        }
    }

    private static final class RecordingSpawnManager implements SpawnManagerView {
        private int cancelCount;
        private int shutdownCount;

        @Override
        public CompletionStage<Void> cancelRecoveryTasks() {
            cancelCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdownAllHandles() {
            shutdownCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingStreamController implements StreamControllerView {
        private int drainCount;
        private int closeCount;
        private int clearCount;

        @Override
        public CompletionStage<Void> drainAgentTask() {
            drainCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void closeStream() {
            closeCount++;
        }

        @Override
        public void clearStreamQueue() {
            clearCount++;
        }
    }

    private static final class RecordingFirstGate implements FirstIterationGate {
        private int waitCount;

        @Override
        public CompletionStage<Void> waitReady() {
            waitCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingMemoryManager implements MemoryManagerView {
        private boolean initSuccess;
        private Object extractionModel;
        private int extractCount;
        private int closeCount;

        @Override
        public CompletionStage<Boolean> initToolkit() {
            return CompletableFuture.completedFuture(initSuccess);
        }

        @Override
        public Object getExtractionModel() {
            return extractionModel;
        }

        @Override
        public void setExtractionModel(Object model) {
            extractionModel = model;
        }

        @Override
        public CompletionStage<Void> extractAfterRound() {
            extractCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> close() {
            closeCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingHarness implements MemberRuntime {
        private boolean registeredMemory;
        private boolean injectedMemory;

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.of().iterator();
        }

        @Override
        public CompletionStage<Void> steer(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void initCwdForRound() {
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public boolean isPendingInterruptResumeValid(Object userInput) {
            return true;
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void registerMemberTools(Object memoryManager) {
            registeredMemory = true;
        }

        @Override
        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            injectedMemory = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runAgentCustomizer(AgentCustomizer customizer) {
        }

        @Override
        public Object workspace() {
            return (Object) null;
        }

        @Override
        public Object sysOperation() {
            return (Object) null;
        }
    }

    private static final class RecordingMessager implements Messager {
        private final List<String> subscribedTopics = new ArrayList<>();
        private final List<String> unsubscribedTopics = new ArrayList<>();
        private final List<MessagerHandler> handlers = new ArrayList<>();
        private final List<EventMessage> publishedEvents = new ArrayList<>();
        private int directRegistered;
        private int directUnregistered;

        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            publishedEvents.add(message);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            subscribedTopics.add(topicId);
            handlers.add(handler);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            unsubscribedTopics.add(topicId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            directRegistered++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            directUnregistered++;
            return CompletableFuture.completedFuture(null);
        }
    }
}
