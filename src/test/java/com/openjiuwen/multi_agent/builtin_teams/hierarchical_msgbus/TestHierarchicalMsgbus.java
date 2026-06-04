/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.hierarchical_msgbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus.HierarchicalTeam;
import com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus.HierarchicalTeamConfig;
import com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus.P2PAbilityManager;
import com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus.SupervisorAgent;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hierarchical message-bus teams.
 *
 * <p>Mirrors Python's {@code test_hierarchical_msgbus.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.hierarchical_msgbus}.</p>
 */
class TestHierarchicalMsgbus {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static class RecordingRuntime extends TeamRuntime {
        Object sentMessage;
        String sentRecipient;
        String sentSender;
        String sentSessionId;
        Double sentTimeout;
        Object result = Map.of("ok", true);

        @Override
        public CompletableFuture<Object> send(
                Object message,
                String recipient,
                String sender,
                String sessionId,
                Double timeout
        ) {
            sentMessage = message;
            sentRecipient = recipient;
            sentSender = sender;
            sentSessionId = sessionId;
            sentTimeout = timeout;
            return CompletableFuture.completedFuture(result);
        }
    }

    static class SimpleSession implements Session {
        private final String sessionId;

        SimpleSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> state) {
        }
    }

    static class RecordingSupervisor implements CommunicableAgent {
        final List<String> recipients = java.util.Collections.synchronizedList(new ArrayList<>());
        final List<String> sessionIds = java.util.Collections.synchronizedList(new ArrayList<>());
        final List<Object> messages = java.util.Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger sendCount = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger peak = new AtomicInteger();
        volatile boolean fail;
        volatile long delayMs;
        volatile TeamRuntime runtime = new TeamRuntime();

        @Override
        public CompletableFuture<Object> send(Object message, String recipient, String sessionId, Double timeout) {
            sendCount.incrementAndGet();
            recipients.add(recipient);
            sessionIds.add(sessionId);
            messages.add(message);
            if (fail) {
                return CompletableFuture.failedFuture(new RuntimeException("network error"));
            }
            return CompletableFuture.supplyAsync(() -> {
                int now = active.incrementAndGet();
                peak.updateAndGet(previous -> Math.max(previous, now));
                try {
                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                    return Map.of("from", recipient);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    active.decrementAndGet();
                }
            });
        }

        @Override
        public TeamRuntime getRuntime() {
            return runtime;
        }

        @Override
        public String getAgentId() {
            return "supervisor";
        }
    }

    static class DelegatingManager extends P2PAbilityManager {
        int delegateCalls;
        List<ToolCall> delegated = List.of();
        List<AbilityManager.ToolExecutionEntry> response = new ArrayList<>();

        DelegatingManager(CommunicableAgent supervisor) {
            super(supervisor);
        }

        @Override
        protected List<AbilityManager.ToolExecutionEntry> executeNonAgentCalls(
                AgentCallbackContext ctx,
                List<ToolCall> toolCalls,
                Session session,
                String tag
        ) {
            delegateCalls++;
            delegated = toolCalls;
            return response;
        }
    }

    static class ExposedP2PAbilityManager extends P2PAbilityManager {
        ExposedP2PAbilityManager(CommunicableAgent supervisor, int maxParallelSubAgents) {
            super(supervisor, maxParallelSubAgents);
        }

        int permits() {
            return getSemaphore().availablePermits();
        }

        Object semaphore() {
            return getSemaphore();
        }
    }

    private static AgentCard svCard(String id) {
        return AgentCard.builder().id(id).name(id).description("supervisor agent").build();
    }

    private static AgentCard subCard(String id) {
        return AgentCard.builder().id(id).name(id).description("sub-agent " + id).build();
    }

    private static TeamCard teamCard(String id) {
        return TeamCard.builder().id(id).name(id).description("hierarchical team").build();
    }

    private static HierarchicalTeamConfig config(String supervisorId) {
        return new HierarchicalTeamConfig(svCard(supervisorId));
    }

    private static HierarchicalTeam team(String supervisorId) {
        return new HierarchicalTeam(teamCard("h_team"), config(supervisorId));
    }

    private static HierarchicalTeam team(String supervisorId, RecordingRuntime runtime) {
        return new HierarchicalTeam(teamCard("h_team"), config(supervisorId), runtime);
    }

    private static AgentCard addAgent(HierarchicalTeam team, String agentId) {
        AgentCard card = subCard(agentId);
        team.addAgent(card, () -> (Function<Object, Object>) message -> message);
        return card;
    }

    private static ToolCall tc(String name) {
        return tc(name, Map.of(), "tc1");
    }

    private static ToolCall tc(String name, Map<String, Object> args, String callId) {
        try {
            return ToolCall.builder()
                    .id(callId)
                    .type("function")
                    .name(name)
                    .arguments(MAPPER.writeValueAsString(args))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static AgentCallbackContext ctx() {
        return AgentCallbackContext.builder().build();
    }

    private static ModelClientConfig modelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider("openai")
                .apiKey("test-key")
                .apiBase("https://api.example.com")
                .build();
    }

    private static ModelRequestConfig modelRequestConfig() {
        return ModelRequestConfig.builder().modelName("gpt-4").build();
    }

    @Nested
    class TestHierarchicalTeamConfig {
        @Test
        void testRequiresSupervisorAgent() {
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig();

            assertThrows(IllegalArgumentException.class, () -> new HierarchicalTeam(teamCard("t"), cfg));
        }

        @Test
        void testStoresSupervisorCard() {
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig(svCard("sv1"));

            assertEquals("sv1", cfg.getSupervisorAgent().getId());
            assertEquals("sv1", cfg.getSupervisorAgent().getName());
        }

        @Test
        void testSupervisorCardIdAccessible() {
            assertEquals("my_supervisor", config("my_supervisor").getSupervisorAgent().getId());
        }

        @Test
        void testInheritsTeamConfig() {
            assertInstanceOf(TeamConfig.class, config("sv"));
        }

        @Test
        void testTeamConfigDefaultMaxAgents() {
            assertEquals(10, config("sv").getMaxAgents());
        }

        @Test
        void testTwoConfigsAreIndependent() {
            HierarchicalTeamConfig cfgA = config("sv_a");
            HierarchicalTeamConfig cfgB = config("sv_b");

            assertNotEquals(cfgA.getSupervisorAgent().getId(), cfgB.getSupervisorAgent().getId());
        }
    }

    @Nested
    class TestHierarchicalTeamInit {
        @Test
        void testCardStored() {
            TeamCard card = teamCard("my_team");
            HierarchicalTeam team = new HierarchicalTeam(card, config("sv"));

            assertSame(card, team.getCard());
        }

        @Test
        void testConfigStored() {
            HierarchicalTeamConfig cfg = config("sv_cfg");
            HierarchicalTeam team = new HierarchicalTeam(teamCard("t"), cfg);

            assertSame(cfg, team.getConfig());
        }

        @Test
        void testRuntimeCreated() {
            assertInstanceOf(TeamRuntime.class, team("sv").getRuntime());
        }

        @Test
        void testRuntimeHasNoAgentsInitially() {
            assertEquals(0, team("sv").getAgentCount());
        }

        @Test
        void testIsBaseTeam() {
            assertInstanceOf(BaseTeam.class, team("sv"));
        }
    }

    @Nested
    class TestHierarchicalTeamAddAgent {
        @Test
        void testRegistersAgentInRuntime() {
            HierarchicalTeam team = team("sv");

            addAgent(team, "a1");

            assertTrue(team.getRuntime().hasAgent("a1"));
        }

        @Test
        void testReturnsSelfForChaining() {
            HierarchicalTeam team = team("sv");

            assertSame(team, team.addAgent(subCard("a2"), () -> (Function<Object, Object>) message -> message));
        }

        @Test
        void testAgentCountIncrements() {
            HierarchicalTeam team = team("sv");

            assertEquals(0, team.getAgentCount());
            addAgent(team, "a1");
            assertEquals(1, team.getAgentCount());
            addAgent(team, "a2");
            assertEquals(2, team.getAgentCount());
        }

        @Test
        void testSupervisorCardRegistered() {
            HierarchicalTeam team = team("sv_add");

            team.addAgent(svCard("sv_add"), () -> (Function<Object, Object>) message -> message);

            assertTrue(team.getRuntime().hasAgent("sv_add"));
        }

        @Test
        void testDuplicateAgentDoesNotIncreaseCount() {
            HierarchicalTeam team = team("sv");

            addAgent(team, "dup");
            addAgent(team, "dup");

            assertEquals(1, team.getAgentCount());
        }

        @Test
        void testGetAgentCardAfterRegistration() {
            HierarchicalTeam team = team("sv");
            AgentCard card = subCard("lookup_me");

            team.addAgent(card, () -> (Function<Object, Object>) message -> message);

            assertSame(card, team.getAgentCard("lookup_me"));
        }

        @Test
        void testListAgentsContainsRegisteredId() {
            HierarchicalTeam team = team("sv");

            addAgent(team, "listed");

            assertTrue(team.listAgents().contains("listed"));
        }

        @Test
        void testSupervisorRegistrationAppliesTimeout() {
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig(svCard("sv_logged"), 77.0);
            HierarchicalTeam team = new HierarchicalTeam(teamCard("t"), cfg);

            team.addAgent(svCard("sv_logged"), () -> (Function<Object, Object>) message -> message);

            assertEquals(77.0, team.getRuntime().getP2pTimeout());
        }
    }

    @Nested
    class TestHierarchicalTeamAssertReady {
        @Test
        void testRaisesWhenSupervisorNotRegistered() {
            assertThrows(Exception.class, () -> team("sv_missing").invoke(Map.of("query", "hi")));
        }

        @Test
        void testPassesWhenSupervisorRegistered() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("sv_ok", runtime);
            team.addAgent(svCard("sv_ok"), () -> (Function<Object, Object>) message -> message);

            assertEquals(Map.of("ok", true), team.invoke(Map.of("query", "hi")).join());
        }

        @Test
        void testRaisesWhenOnlyNonSupervisorRegistered() {
            HierarchicalTeam team = team("sv_real");
            addAgent(team, "not_supervisor");

            assertThrows(Exception.class, () -> team.invoke(Map.of("query", "test")));
        }
    }

    @Nested
    class TestHierarchicalTeamInvoke {
        @Test
        void testRaisesWhenSupervisorNotRegistered() {
            assertThrows(Exception.class, () -> team("sv_absent").invoke(Map.of("query", "hi")));
        }

        @Test
        void testReturnsResultFromRuntimeSend() {
            RecordingRuntime runtime = new RecordingRuntime();
            Map<String, Object> expected = Map.of("output", "done");
            runtime.result = expected;
            HierarchicalTeam team = team("sv", runtime);
            team.addAgent(svCard("sv"), () -> (Function<Object, Object>) message -> message);

            assertSame(expected, team.invoke(Map.of("query", "hello")).join());
        }

        @Test
        void testSendCalledWithSupervisorAsRecipient() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("sv_recv", runtime);
            team.addAgent(svCard("sv_recv"), () -> (Function<Object, Object>) message -> message);

            team.invoke(Map.of("q", "test")).join();

            assertEquals("sv_recv", runtime.sentRecipient);
        }

        @Test
        void testSendCalledWithSessionId() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("sv_sid", runtime);
            team.addAgent(svCard("sv_sid"), () -> (Function<Object, Object>) message -> message);

            team.invoke(Map.of("q", "test"), new SimpleSession("my-session-42")).join();

            assertEquals("my-session-42", runtime.sentSessionId);
        }
    }

    @Nested
    class TestHierarchicalTeamStream {
        @Test
        void testRaisesWhenSupervisorNotRegistered() {
            assertThrows(Exception.class, () -> team("sv_absent").stream(Map.of("q", "hi")).toList());
        }

        @Test
        void testYieldsAllChunksFromStreamContext() {
            RecordingRuntime runtime = new RecordingRuntime();
            runtime.result = Map.of("chunk", "a");
            HierarchicalTeam team = team("sv", runtime);
            team.addAgent(svCard("sv"), () -> (Function<Object, Object>) message -> message);

            assertEquals(List.of(Map.of("chunk", "a")), team.stream(Map.of("q", "test")).toList());
        }

        @Test
        void testStreamEmptyWhenContextYieldsNothing() {
            RecordingRuntime runtime = new RecordingRuntime();
            runtime.result = null;
            HierarchicalTeam team = team("sv", runtime);
            team.addAgent(svCard("sv"), () -> (Function<Object, Object>) message -> message);

            assertEquals(List.of(), team.stream(Map.of("q", "test")).toList());
        }
    }

    @Nested
    class TestP2PAbilityManagerInit {
        @Test
        void testInheritsAbilityManager() {
            assertInstanceOf(AbilityManager.class, new P2PAbilityManager(new RecordingSupervisor()));
        }

        @Test
        void testSemaphoreReflectsMaxParallel() {
            assertEquals(7, new ExposedP2PAbilityManager(new RecordingSupervisor(), 7).permits());
        }

        @Test
        void testMaxParallelClampedToOneWhenZero() {
            assertEquals(1, new P2PAbilityManager(new RecordingSupervisor(), 0).getMaxParallelSubAgents());
        }

        @Test
        void testMaxParallelClampedToOneWhenNegative() {
            assertEquals(1, new P2PAbilityManager(new RecordingSupervisor(), -5).getMaxParallelSubAgents());
        }

        @Test
        void testSemaphoreLazilyCreatedAndCached() {
            ExposedP2PAbilityManager mgr = new ExposedP2PAbilityManager(new RecordingSupervisor(), 3);

            assertSame(mgr.semaphore(), mgr.semaphore());
            assertEquals(3, mgr.permits());
        }
    }

    @Nested
    class TestP2PAbilityManagerAdd {
        @Test
        void testAddStoresAgentCard() {
            P2PAbilityManager mgr = new P2PAbilityManager(new RecordingSupervisor());

            mgr.add(subCard("ax"));

            assertTrue(mgr.list().stream().filter(AgentCard.class::isInstance)
                    .map(AgentCard.class::cast).map(AgentCard::getId).toList().contains("ax"));
        }

        @Test
        void testAddMultipleCards() {
            P2PAbilityManager mgr = new P2PAbilityManager(new RecordingSupervisor());

            mgr.add(subCard("a1"));
            mgr.add(subCard("a2"));

            Set<String> registered = Set.copyOf(mgr.list().stream().filter(AgentCard.class::isInstance)
                    .map(AgentCard.class::cast).map(AgentCard::getId).toList());
            assertTrue(registered.containsAll(Set.of("a1", "a2")));
        }

        @Test
        void testAddReturnsAddAbilityResult() {
            P2PAbilityManager mgr = new P2PAbilityManager(new RecordingSupervisor());

            AbilityManager.AddAbilityResult result = mgr.add(subCard("new_agent"));

            assertTrue(result.added());
        }

        @Test
        void testAddDuplicateReturnsNotAdded() {
            P2PAbilityManager mgr = new P2PAbilityManager(new RecordingSupervisor());
            mgr.add(subCard("dup_agent"));

            AbilityManager.AddAbilityResult result = mgr.add(subCard("dup_agent"));

            assertFalse(result.added());
        }
    }

    @Nested
    class TestP2PAbilityManagerExecuteNonAgent {
        @Test
        void testEmptyToolCallsReturnsEmptyList() {
            P2PAbilityManager mgr = new P2PAbilityManager(new RecordingSupervisor());

            assertEquals(List.of(), mgr.execute(ctx(), List.of(), new SimpleSession("s"), null));
        }

        @Test
        void testNonAgentCallDelegatesToSuper() {
            DelegatingManager mgr = new DelegatingManager(new RecordingSupervisor());
            ToolCall call = tc("unknown_tool");
            AbilityManager.ToolExecutionEntry expected = new AbilityManager.ToolExecutionEntry(
                    call, "res", new ToolMessage("ok", "tc1"), AbilityManager.ToolExecutionClassification.SUCCESS, null);
            mgr.response = List.of(expected);

            List<AbilityManager.ToolExecutionEntry> result = mgr.execute(ctx(), call, new SimpleSession("s"), null);

            assertEquals(1, mgr.delegateCalls);
            assertEquals(List.of(expected), result);
        }

        @Test
        void testNonAgentSingleToolCallPassesThrough() {
            DelegatingManager mgr = new DelegatingManager(new RecordingSupervisor());

            mgr.execute(ctx(), tc("plain_tool", Map.of(), "pt1"), new SimpleSession("s"), null);

            assertEquals(1, mgr.delegateCalls);
            assertEquals("plain_tool", mgr.delegated.get(0).getName());
        }
    }

    @Nested
    class TestP2PAbilityManagerExecuteAgentCall {
        @Test
        void testAgentCallInvokesSupervisorSend() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("sub_a"));

            mgr.execute(ctx(), tc("sub_a", Map.of("x", 1), "c1"), new SimpleSession("s1"), null);

            assertEquals(1, supervisor.sendCount.get());
        }

        @Test
        void testAgentCallRecipientMatchesAgentId() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("agent_b"));

            mgr.execute(ctx(), tc("agent_b"), new SimpleSession("s"), null);

            assertEquals("agent_b", supervisor.recipients.get(0));
        }

        @Test
        void testAgentCallPassesSessionId() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("agent_c"));

            mgr.execute(ctx(), tc("agent_c"), new SimpleSession("sess-42"), null);

            assertEquals("sess-42", supervisor.sessionIds.get(0));
        }

        @Test
        void testAgentCallReturnsResultAndToolMessage() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("ag"));

            List<AbilityManager.ToolExecutionEntry> results =
                    mgr.execute(ctx(), tc("ag"), new SimpleSession("s"), null);

            assertEquals(1, results.size());
            assertEquals(Map.of("from", "ag"), results.get(0).result());
            assertInstanceOf(ToolMessage.class, results.get(0).toolMessage());
        }

        @Test
        void testToolMessageHasCorrectToolCallId() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("ag2"));

            ToolMessage msg = mgr.execute(ctx(), tc("ag2", Map.of(), "call-xyz"),
                    new SimpleSession("s"), null).get(0).toolMessage();

            assertEquals("call-xyz", msg.getToolCallId());
        }

        @Test
        void testP2pFailureReturnsErrorToolMessage() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            supervisor.fail = true;
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("fail_ag"));

            AbilityManager.ToolExecutionEntry entry = mgr.execute(ctx(), tc("fail_ag", Map.of(), "tf1"),
                    new SimpleSession("s"), null).get(0);

            assertNull(entry.result());
            assertTrue(String.valueOf(entry.toolMessage().getContent()).contains("P2P parallel dispatch failed"));
        }

        @Test
        void testP2pErrorToolMessageHasOriginalCallId() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            supervisor.fail = true;
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor);
            mgr.add(subCard("fail2"));

            ToolMessage msg = mgr.execute(ctx(), tc("fail2", Map.of(), "err-id"),
                    new SimpleSession("s"), null).get(0).toolMessage();

            assertEquals("err-id", msg.getToolCallId());
        }
    }

    @Nested
    class TestP2PAbilityManagerParallelDispatch {
        @Test
        void testAllParallelAgentCallsDispatched() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor, 5);
            mgr.add(subCard("s1"));
            mgr.add(subCard("s2"));

            mgr.execute(ctx(), List.of(tc("s1", Map.of(), "t1"), tc("s2", Map.of(), "t2")),
                    new SimpleSession("sp"), null);

            assertEquals(Set.of("s1", "s2"), Set.copyOf(supervisor.recipients));
        }

        @Test
        void testSemaphoreLimitsPeakConcurrency() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            supervisor.delayMs = 25;
            int limit = 2;
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor, limit);
            List<ToolCall> calls = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                mgr.add(subCard("ag" + i));
                calls.add(tc("ag" + i, Map.of(), "tc" + i));
            }

            mgr.execute(ctx(), calls, new SimpleSession("ss"), null);

            assertTrue(supervisor.peak.get() <= limit);
        }

        @Test
        void testResultOrderPreservedForParallelCalls() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            P2PAbilityManager mgr = new P2PAbilityManager(supervisor, 2);
            mgr.add(subCard("first"));
            mgr.add(subCard("second"));

            List<AbilityManager.ToolExecutionEntry> results = mgr.execute(ctx(),
                    List.of(tc("first", Map.of(), "c1"), tc("second", Map.of(), "c2")),
                    new SimpleSession("sp"), null);

            assertEquals(Map.of("from", "first"), results.get(0).result());
            assertEquals(Map.of("from", "second"), results.get(1).result());
        }

        @Test
        void testMixedAgentAndToolCallsBothExecuted() {
            RecordingSupervisor supervisor = new RecordingSupervisor();
            DelegatingManager mgr = new DelegatingManager(supervisor);
            mgr.add(subCard("sub_m"));
            ToolCall regularCall = tc("reg_tool", Map.of(), "tr");
            AbilityManager.ToolExecutionEntry regular = new AbilityManager.ToolExecutionEntry(
                    regularCall, "reg_val", new ToolMessage("reg", "tr"),
                    AbilityManager.ToolExecutionClassification.SUCCESS, null);
            mgr.response = List.of(regular);

            List<AbilityManager.ToolExecutionEntry> results = mgr.execute(ctx(),
                    List.of(tc("sub_m", Map.of(), "ta"), regularCall), new SimpleSession("sm"), null);

            assertEquals(2, results.size());
            assertEquals(Map.of("from", "sub_m"), results.get(0).result());
            assertEquals("reg_val", results.get(1).result());
        }
    }

    @Nested
    class TestSupervisorAgentInit {
        @Test
        void testAbilityManagerIsP2p() {
            assertInstanceOf(P2PAbilityManager.class, new SupervisorAgent(svCard("sv_i")).getAbilityManager());
        }

        @Test
        void testIsCommunicableAgent() {
            assertInstanceOf(CommunicableAgent.class, new SupervisorAgent(svCard("sv_comm")));
        }

        @Test
        void testIsReactAgent() {
            assertInstanceOf(ReActAgent.class, new SupervisorAgent(svCard("sv_react")));
        }

        @Test
        void testRegisterSubAgentCardAddsToAbilityManager() {
            SupervisorAgent agent = new SupervisorAgent(svCard("sv_r"));

            agent.registerSubAgentCard(subCard("sub1"));

            assertNotNull(agent.getAbilityManager().get("sub1"));
        }

        @Test
        void testRegisterMultipleSubAgents() {
            SupervisorAgent agent = new SupervisorAgent(svCard("sv_multi"));

            agent.registerSubAgentCard(subCard("s1"));
            agent.registerSubAgentCard(subCard("s2"));

            assertNotNull(agent.getAbilityManager().get("s1"));
            assertNotNull(agent.getAbilityManager().get("s2"));
        }

        @Test
        void testRegisterSubAgentAliasAddsCard() {
            SupervisorAgent agent = new SupervisorAgent(svCard("sv_alias"));

            agent.register_sub_agent_card(subCard("logged_sub"));

            assertNotNull(agent.getAbilityManager().get("logged_sub"));
        }
    }

    @Nested
    class TestSupervisorAgentConfigure {
        @Test
        void testConfigureReactConfigReturnsSelf() {
            SupervisorAgent agent = new SupervisorAgent(svCard("sv_c"));

            assertSame(agent, agent.configure(new ReActAgentConfig()));
        }

        @Test
        void testConfigureNonReactIsNoopReturnsSelf() {
            SupervisorAgent agent = new SupervisorAgent(svCard("sv_n"));

            assertSame(agent, agent.configure(new Object()));
        }

        @Test
        void testConfigureNoneIsNoopReturnsSelf() {
            SupervisorAgent agent = new SupervisorAgent(svCard("sv_none"));

            assertSame(agent, agent.configure(null));
        }
    }

    @Nested
    class TestSupervisorAgentCreate {
        @Test
        void testCreateReturnsCardAndCallableProvider() {
            Object[] created = SupervisorAgent.create(
                    List.of(subCard("a1")), modelClientConfig(), modelRequestConfig(), svCard("sv_create"),
                    "You are a supervisor.");

            assertEquals("sv_create", ((AgentCard) created[0]).getId());
            assertInstanceOf(Supplier.class, created[1]);
        }

        @Test
        void testCreateEmptyAgentsRaises() {
            assertThrows(Exception.class, () -> SupervisorAgent.create(
                    List.of(), modelClientConfig(), modelRequestConfig(), svCard("sv_e"), "sys"));
        }

        @Test
        void testCreateNonAgentCardInListRaises() {
            assertThrows(Exception.class, () -> SupervisorAgent.create(
                    List.of("not_a_card"), modelClientConfig(), modelRequestConfig(), svCard("sv_bad"), "sys"));
        }

        @Test
        void testProviderReturnsSupervisorAgentInstance() {
            Object[] created = SupervisorAgent.create(
                    List.of(subCard("x1")), modelClientConfig(), modelRequestConfig(), svCard("sv_prov"), "sys");

            Object instance = ((Supplier<?>) created[1]).get();

            assertInstanceOf(SupervisorAgent.class, instance);
        }

        @Test
        void testProviderRegistersAllSubAgents() {
            Object[] created = SupervisorAgent.create(
                    List.of(subCard("x1"), subCard("x2")), modelClientConfig(), modelRequestConfig(),
                    svCard("sv_prov2"), "sys");

            SupervisorAgent instance = (SupervisorAgent) ((Supplier<?>) created[1]).get();

            assertNotNull(instance.getAbilityManager().get("x1"));
            assertNotNull(instance.getAbilityManager().get("x2"));
        }

        @Test
        void testCreateAgentCardIdMatchesSuppliedCard() {
            AgentCard card = svCard("exact_id");

            Object[] created = SupervisorAgent.create(
                    List.of(subCard("sub")), modelClientConfig(), modelRequestConfig(), card, "sys");

            assertSame(card, created[0]);
        }

        @Test
        void testCreateWithCustomMaxIterations() {
            Object[] created = SupervisorAgent.create(
                    List.of(subCard("sub")), modelClientConfig(), modelRequestConfig(), svCard("sv_iter"),
                    "sys", 3, 10);

            SupervisorAgent instance = (SupervisorAgent) ((Supplier<?>) created[1]).get();

            assertEquals(3, ((ReActAgentConfig) instance.getConfig()).getMaxIterations());
        }

        @Test
        void testCreateWithCustomMaxParallelSubAgents() {
            Object[] created = SupervisorAgent.create(
                    List.of(subCard("sub")), modelClientConfig(), modelRequestConfig(), svCard("sv_par"),
                    "sys", 5, 4);

            SupervisorAgent instance = (SupervisorAgent) ((Supplier<?>) created[1]).get();

            assertEquals(4, instance.getAbilityManager().getMaxParallelSubAgents());
        }
    }
}
