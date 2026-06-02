/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.builtin_teams.hierarchical_tools;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.multiagent.teams.hierarchicaltools.HierarchicalTeam;
import com.openjiuwen.core.multiagent.teams.hierarchicaltools.HierarchicalTeamConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for hierarchical tools teams.
 *
 * <p>Mirrors Python's {@code test_hierarchical_tools.py} in
 * {@code tests.unit_tests.multi_agent.builtin_teams.hierarchical_tools}.</p>
 */
class TestHierarchicalTools {

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

    static class AbilityRecorder {
        private final List<AgentCard> added = new ArrayList<>();

        public void add(AgentCard card) {
            added.add(card);
        }

        List<AgentCard> added() {
            return added;
        }
    }

    static class ParentAgent {
        private final AbilityRecorder abilityManager = new AbilityRecorder();

        public AbilityRecorder getAbilityManager() {
            return abilityManager;
        }
    }

    private static AgentCard agentCard(String agentId) {
        return AgentCard.builder().id(agentId).name(agentId).description("agent " + agentId).build();
    }

    private static TeamCard teamCard(String teamId) {
        return TeamCard.builder().id(teamId).name(teamId).description("hierarchical tools team").build();
    }

    private static HierarchicalTeamConfig config(String rootId) {
        return new HierarchicalTeamConfig(agentCard(rootId));
    }

    private static HierarchicalTeam team() {
        return team("root", "ht_team");
    }

    private static HierarchicalTeam team(String rootId, String teamId) {
        return new HierarchicalTeam(teamCard(teamId), config(rootId));
    }

    private static HierarchicalTeam team(String rootId, String teamId, TeamRuntime runtime) {
        return new HierarchicalTeam(teamCard(teamId), config(rootId), runtime);
    }

    private static AgentCard addAgent(HierarchicalTeam team, String agentId) {
        AgentCard card = agentCard(agentId);
        team.addAgent(card, () -> (Function<Object, Object>) message -> message);
        return card;
    }

    private static ParentAgent addParentAgent(HierarchicalTeam team, String agentId) {
        ParentAgent parent = new ParentAgent();
        team.addAgent(agentCard(agentId), () -> parent);
        return parent;
    }

    private static void addChild(HierarchicalTeam team, String childId, String parentId) {
        team.addAgent(agentCard(childId), () -> (Function<Object, Object>) message -> message, parentId);
    }

    @Nested
    class TestHierarchicalTeamConfig {
        @Test
        void testRequiresRootAgent() {
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig();

            assertThrows(IllegalArgumentException.class, () -> new HierarchicalTeam(teamCard("t"), cfg));
        }

        @Test
        void testStoresRootAgentCard() {
            AgentCard card = agentCard("my_root");
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig(card);

            assertEquals("my_root", cfg.getRootAgent().getId());
        }

        @Test
        void testRootAgentNamePreserved() {
            AgentCard card = agentCard("named_root");
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig(card);

            assertEquals("named_root", cfg.getRootAgent().getName());
        }

        @Test
        void testRootAgentDescriptionPreserved() {
            AgentCard card = AgentCard.builder().id("root").name("root").description("root agent desc").build();
            HierarchicalTeamConfig cfg = new HierarchicalTeamConfig(card);

            assertEquals("root agent desc", cfg.getRootAgent().getDescription());
        }

        @Test
        void testInheritsTeamConfig() {
            assertInstanceOf(TeamConfig.class, config("root"));
        }

        @Test
        void testTeamConfigDefaultsPreserved() {
            HierarchicalTeamConfig cfg = config("root");

            assertEquals(10, cfg.getMaxAgents());
            assertEquals(100, cfg.getMaxConcurrentMessages());
            assertEquals(30.0, cfg.getMessageTimeout());
        }

        @Test
        void testConfigureMaxAgentsChaining() {
            HierarchicalTeamConfig cfg = config("root");

            TeamConfig result = cfg.configureMaxAgents(5);

            assertSame(cfg, result);
            assertEquals(5, cfg.getMaxAgents());
        }

        @Test
        void testConfigureTimeoutChaining() {
            HierarchicalTeamConfig cfg = config("root");

            TeamConfig result = cfg.configureTimeout(60.0);

            assertSame(cfg, result);
            assertEquals(60.0, cfg.getMessageTimeout());
        }

        @Test
        void testConfigureConcurrencyChaining() {
            HierarchicalTeamConfig cfg = config("root");

            TeamConfig result = cfg.configureConcurrency(50);

            assertSame(cfg, result);
            assertEquals(50, cfg.getMaxConcurrentMessages());
        }

        @Test
        void testCustomMaxAgents() {
            HierarchicalTeamConfig cfg = config("root");
            cfg.setMaxAgents(3);

            assertEquals(3, cfg.getMaxAgents());
        }
    }

    @Nested
    class TestHierarchicalTeamInit {
        @Test
        void testCardStored() {
            TeamCard card = teamCard("t1");
            HierarchicalTeam team = new HierarchicalTeam(card, config("root"));

            assertSame(card, team.getCard());
        }

        @Test
        void testConfigStored() {
            HierarchicalTeamConfig cfg = config("r1");
            HierarchicalTeam team = new HierarchicalTeam(teamCard("t"), cfg);

            assertSame(cfg, team.getConfig());
        }

        @Test
        void testRuntimeCreatedByDefault() {
            assertInstanceOf(TeamRuntime.class, team().getRuntime());
        }

        @Test
        void testCustomRuntimeAccepted() {
            TeamRuntime runtime = new TeamRuntime();
            HierarchicalTeam team = new HierarchicalTeam(teamCard("t"), config("root"), runtime);

            assertSame(runtime, team.getRuntime());
        }

        @Test
        void testRootAgentIdInConfig() {
            HierarchicalTeamConfig cfg = (HierarchicalTeamConfig) team("entry", "ht_team").getConfig();

            assertEquals("entry", cfg.getRootAgent().getId());
        }

        @Test
        void testTeamIdMatchesCardName() {
            assertEquals("my_ht_team", team("root", "my_ht_team").getTeamId());
        }

        @Test
        void testRuntimeTeamIdMatchesCardId() {
            assertEquals("tid_abc", team("root", "tid_abc").getRuntime().getTeamId());
        }

        @Test
        void testInitialAgentCountIsZero() {
            assertEquals(0, team().getAgentCount());
        }

        @Test
        void testConfigureReplacesConfig() {
            HierarchicalTeam team = team();
            HierarchicalTeamConfig newCfg = config("new_root");

            BaseTeam result = team.configure(newCfg);

            assertSame(team, result);
            assertSame(newCfg, team.getConfig());
        }
    }

    @Nested
    class TestHierarchicalTeamAddAgent {
        @Test
        void testRegistersInRuntime() {
            HierarchicalTeam team = team();

            addAgent(team, "agent_a");

            assertTrue(team.getRuntime().hasAgent("agent_a"));
        }

        @Test
        void testReturnsSelf() {
            HierarchicalTeam team = team();
            AgentCard card = agentCard("agent_b");

            HierarchicalTeam result = team.addAgent(card, () -> (Function<Object, Object>) message -> message);

            assertSame(team, result);
        }

        @Test
        void testAddMultiple() {
            HierarchicalTeam team = team();

            addAgent(team, "a1");
            addAgent(team, "a2");

            assertTrue(team.getRuntime().hasAgent("a1"));
            assertTrue(team.getRuntime().hasAgent("a2"));
        }

        @Test
        void testIncrementsCount() {
            HierarchicalTeam team = team();

            addAgent(team, "c1");
            addAgent(team, "c2");

            assertEquals(2, team.getAgentCount());
        }

        @Test
        void testWithParentRegistersChild() {
            HierarchicalTeam team = team("root", "ht_team");
            addAgent(team, "root");

            addChild(team, "child", "root");

            assertTrue(team.getRuntime().hasAgent("child"));
        }

        @Test
        void testDuplicateReturnsSelfNoRaise() {
            HierarchicalTeam team = team();
            addAgent(team, "dup");

            HierarchicalTeam result = team.addAgent(
                    agentCard("dup"),
                    () -> (Function<Object, Object>) message -> message
            );

            assertSame(team, result);
            assertEquals(1, team.getAgentCount());
        }

        @Test
        void testBeyondMaxRaises() {
            HierarchicalTeamConfig cfg = config("root");
            cfg.setMaxAgents(2);
            HierarchicalTeam team = new HierarchicalTeam(teamCard("ht_team"), cfg);
            addAgent(team, "x1");
            addAgent(team, "x2");

            assertThrows(Exception.class, () -> team.addAgent(
                    agentCard("x3"),
                    () -> (Function<Object, Object>) message -> message
            ));
        }

        @Test
        void testMethodChainingMultipleCalls() {
            HierarchicalTeam team = team();

            HierarchicalTeam result = team
                    .addAgent(agentCard("chain_a"), () -> (Function<Object, Object>) message -> message)
                    .addAgent(agentCard("chain_b"), () -> (Function<Object, Object>) message -> message);

            assertSame(team, result);
            assertEquals(2, team.getAgentCount());
        }

        @Test
        void testCardAppendedToTeamCardAgentCards() {
            HierarchicalTeam team = team();

            addAgent(team, "card_check");

            assertTrue(team.getCard().getAgentCards().stream().map(AgentCard::getId).toList().contains("card_check"));
        }
    }

    @Nested
    class TestHierarchicalTeamPendingChildren {
        @Test
        void testNoParentDoesNotCreatePendingEntry() {
            HierarchicalTeam team = team("root", "ht_team");
            addAgent(team, "root");

            team.setupHierarchy();

            assertTrue(team.getPendingChildren("root").isEmpty());
        }

        @Test
        void testAddAgentWithParentQueuesChildCard() {
            HierarchicalTeam team = team("root", "ht_team");
            addAgent(team, "root");

            addChild(team, "child_queued", "root");

            assertEquals(List.of("child_queued"),
                    team.getPendingChildren("root").stream().map(AgentCard::getId).toList());
            assertTrue(team.getRuntime().hasAgent("child_queued"));
        }

        @Test
        void testMultipleChildrenUnderSameParent() {
            HierarchicalTeam team = team("parent_a", "ht_team");
            addAgent(team, "parent_a");

            for (int i = 0; i < 3; i++) {
                addChild(team, "child_" + i, "parent_a");
            }

            assertEquals(Set.of("child_0", "child_1", "child_2"),
                    Set.copyOf(team.getPendingChildren("parent_a").stream().map(AgentCard::getId).toList()));
        }

        @Test
        void testChildrenUnderDifferentParents() {
            HierarchicalTeam team = team("p1", "ht_team");
            addAgent(team, "p1");
            addAgent(team, "p2");

            addChild(team, "child_p1", "p1");
            addChild(team, "child_p2", "p2");

            assertEquals("child_p1", team.getPendingChildren("p1").get(0).getId());
            assertEquals("child_p2", team.getPendingChildren("p2").get(0).getId());
        }

        @Test
        void testSetupHierarchyWiresChildToAbilityManager() {
            HierarchicalTeam team = team("root", "ht_team");
            ParentAgent parent = addParentAgent(team, "root");
            AgentCard child = agentCard("wired_child");
            team.addAgent(child, () -> (Function<Object, Object>) message -> message, "root");

            team.setupHierarchy();

            assertEquals(List.of(child), parent.getAbilityManager().added());
        }

        @Test
        void testSetupHierarchyClearsPendingAfterExecution() {
            HierarchicalTeam team = team("root", "ht_team");
            ParentAgent parent = addParentAgent(team, "root");
            team.addAgent(agentCard("clear_child"), () -> (Function<Object, Object>) message -> message, "root");

            team.setupHierarchy();
            parent.getAbilityManager().added().clear();
            team.setupHierarchy();

            assertTrue(parent.getAbilityManager().added().isEmpty());
            assertTrue(team.getPendingChildren("root").isEmpty());
        }

        @Test
        void testSetupHierarchySkippedWhenNoPending() {
            HierarchicalTeam team = team("root", "ht_team");
            addAgent(team, "root");

            assertDoesNotThrow(team::setupHierarchy);
        }
    }

    @Nested
    class TestHierarchicalTeamAssertReady {
        @Test
        void testRaisesWhenRootNotRegistered() {
            assertThrows(Exception.class, () -> team("missing", "ht_team").invoke(Map.of("query", "hello")));
        }

        @Test
        void testPassesWhenRootRegistered() {
            HierarchicalTeam team = team("root_ok", "ht_team");
            addAgent(team, "root_ok");

            assertDoesNotThrow(() -> team.invoke(Map.of("query", "hello")).join());
        }

        @Test
        void testErrorMessageContainsRootId() {
            Exception error = assertThrows(
                    Exception.class,
                    () -> team("missing_root", "ht_team").invoke(Map.of("query", "hello"))
            );

            assertTrue(error.toString().contains("missing_root"));
        }
    }

    @Nested
    class TestHierarchicalTeamInvoke {
        @Test
        void testRaisesWhenRootNotRegistered() {
            assertThrows(Exception.class, () -> team("no_root", "ht_team").invoke(Map.of("query", "hello")));
        }

        @Test
        void testReturnsResultFromRootAgent() {
            RecordingRuntime runtime = new RecordingRuntime();
            Map<String, Object> expected = Map.of("answer", "42");
            runtime.result = expected;
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            assertSame(expected, team.invoke(Map.of("query", "hello")).join());
        }

        @Test
        void testSendCalledWithRootAsRecipient() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            team.invoke(Map.of("q", "test")).join();

            assertEquals("root", runtime.sentRecipient);
        }

        @Test
        void testSendCalledWithTeamCardAsSender() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "team_abc", runtime);
            addAgent(team, "root");

            team.invoke(Map.of("q", "test")).join();

            assertEquals("team_abc", runtime.sentSender);
        }

        @Test
        void testSendIncludesSessionId() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            team.invoke(Map.of("q", "t")).join();

            assertNotNull(runtime.sentSessionId);
            assertFalse(runtime.sentSessionId.isBlank());
        }

        @Test
        void testReusesConversationIdFromMessage() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            team.invoke(Map.of("conversation_id", "cid-001", "q", "t")).join();

            assertEquals("cid-001", runtime.sentSessionId);
        }

        @Test
        void testInvokeWithStringInput() {
            RecordingRuntime runtime = new RecordingRuntime();
            runtime.result = "string result";
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            Object result = team.invoke("plain string input").join();

            assertEquals("string result", result);
            assertEquals("plain string input", runtime.sentMessage);
        }

        @Test
        void testInvokeCallsSetupHierarchy() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            ParentAgent parent = addParentAgent(team, "root");
            AgentCard child = agentCard("child");
            team.addAgent(child, () -> (Function<Object, Object>) message -> message, "root");

            team.invoke(Map.of("q", "x")).join();

            assertEquals(List.of(child), parent.getAbilityManager().added());
            assertTrue(team.getPendingChildren("root").isEmpty());
        }

        @Test
        void testInvokePassesMessageToSend() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");
            Map<String, Object> message = Map.of("question", "what is 2+2");

            team.invoke(message).join();

            assertSame(message, runtime.sentMessage);
        }
    }

    @Nested
    class TestHierarchicalTeamStream {
        @Test
        void testRaisesWhenRootNotRegistered() {
            assertThrows(Exception.class, () -> team("no_root", "ht_team").stream(Map.of("q", "hi")).toList());
        }

        @Test
        void testStreamCompletesWithoutError() {
            RecordingRuntime runtime = new RecordingRuntime();
            runtime.result = Map.of("out", "c");
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            List<Object> chunks = team.stream(Map.of("q", "hi")).toList();

            assertEquals(List.of(Map.of("out", "c")), chunks);
        }

        @Test
        void testStreamSendsToRootAgent() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            team.stream(Map.of("q", "hi")).toList();

            assertEquals("root", runtime.sentRecipient);
        }

        @Test
        void testStreamSenderIsTeamCardId() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "stream_team", runtime);
            addAgent(team, "root");

            team.stream(Map.of("q", "hi")).toList();

            assertEquals("stream_team", runtime.sentSender);
        }

        @Test
        void testStreamWithStringInput() {
            RecordingRuntime runtime = new RecordingRuntime();
            runtime.result = "done";
            HierarchicalTeam team = team("root", "ht_team", runtime);
            addAgent(team, "root");

            List<Object> chunks = team.stream("plain string").toList();

            assertEquals(List.of("done"), chunks);
            assertEquals("plain string", runtime.sentMessage);
        }

        @Test
        void testStreamCallsSetupHierarchy() {
            RecordingRuntime runtime = new RecordingRuntime();
            HierarchicalTeam team = team("root", "ht_team", runtime);
            ParentAgent parent = addParentAgent(team, "root");
            AgentCard child = agentCard("child");
            team.addAgent(child, () -> (Function<Object, Object>) message -> message, "root");

            team.stream(Map.of("q", "x")).toList();

            assertEquals(List.of(child), parent.getAbilityManager().added());
            assertTrue(team.getPendingChildren("root").isEmpty());
        }
    }

    @Nested
    class TestHierarchicalTeamAgentManagement {
        @Test
        void testCountStartsAtZero() {
            assertEquals(0, team().getAgentCount());
        }

        @Test
        void testCountReflectsAdditions() {
            HierarchicalTeam team = team();

            addAgent(team, "a1");
            assertEquals(1, team.getAgentCount());
            addAgent(team, "a2");
            assertEquals(2, team.getAgentCount());
        }

        @Test
        void testListAgentsReturnsRegisteredIds() {
            HierarchicalTeam team = team();
            addAgent(team, "p1");
            addAgent(team, "p2");

            assertEquals(Set.of("p1", "p2"), Set.copyOf(team.listAgents()));
        }

        @Test
        void testListAgentsDoesNotIncludeRemoved() {
            HierarchicalTeam team = team();
            addAgent(team, "keep");
            addAgent(team, "gone");

            team.removeAgent("gone");

            assertFalse(team.listAgents().contains("gone"));
            assertTrue(team.listAgents().contains("keep"));
        }

        @Test
        void testGetAgentCardReturnsCorrectCard() {
            HierarchicalTeam team = team();
            addAgent(team, "agent_x");

            AgentCard card = team.getAgentCard("agent_x");

            assertNotNull(card);
            assertEquals("agent_x", card.getId());
        }

        @Test
        void testGetAgentCardReturnsNoneForUnknown() {
            assertNull(team().getAgentCard("ghost"));
        }

        @Test
        void testGetAgentCardReturnsNoneAfterRemove() {
            HierarchicalTeam team = team();
            addAgent(team, "rm_lookup");

            team.removeAgent("rm_lookup");

            assertNull(team.getAgentCard("rm_lookup"));
        }

        @Test
        void testRemoveAgentById() {
            HierarchicalTeam team = team();
            addAgent(team, "rm_a");

            team.removeAgent("rm_a");

            assertFalse(team.getRuntime().hasAgent("rm_a"));
        }

        @Test
        void testRemoveAgentReturnsSelf() {
            HierarchicalTeam team = team();
            addAgent(team, "rm_b");

            assertSame(team, team.removeAgent("rm_b"));
        }

        @Test
        void testRemoveAgentByCardObject() {
            HierarchicalTeam team = team();
            AgentCard card = addAgent(team, "rm_c");

            team.removeAgent(card);

            assertFalse(team.getRuntime().hasAgent("rm_c"));
        }

        @Test
        void testRemoveNonexistentAgentIsSafe() {
            assertNotNull(team().removeAgent("ghost"));
        }

        @Test
        void testRemoveAgentDecrementsCount() {
            HierarchicalTeam team = team();
            addAgent(team, "dec_a");
            addAgent(team, "dec_b");

            team.removeAgent("dec_a");

            assertEquals(1, team.getAgentCount());
        }

        @Test
        void testRemoveByIdRemovesFromTeamCardAgentCards() {
            HierarchicalTeam team = team();
            addAgent(team, "rm_meta");

            team.removeAgent("rm_meta");

            assertFalse(team.getCard().getAgentCards().stream().map(AgentCard::getId).toList().contains("rm_meta"));
        }

        @Test
        void testRemoveByCardRemovesFromTeamCardAgentCards() {
            HierarchicalTeam team = team();
            AgentCard card = addAgent(team, "rm_meta_card");

            team.removeAgent(card);

            assertFalse(team.getCard().getAgentCards().stream().map(AgentCard::getId).toList().contains("rm_meta_card"));
        }

        @Test
        void testListAgentsEmptyInitially() {
            assertEquals(List.of(), team().listAgents());
        }

        @Test
        void testHasAgentFalseForUnregistered() {
            assertFalse(team().getRuntime().hasAgent("nobody"));
        }

        @Test
        void testHasAgentTrueAfterAdd() {
            HierarchicalTeam team = team();

            addAgent(team, "present");

            assertTrue(team.getRuntime().hasAgent("present"));
        }

        @Test
        void testHasAgentFalseAfterRemove() {
            HierarchicalTeam team = team();
            addAgent(team, "temp");

            team.removeAgent("temp");

            assertFalse(team.getRuntime().hasAgent("temp"));
        }
    }
}
