/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.interaction.GodViewMessage;
import com.openjiuwen.agent_teams.interaction.HumanAgentMessage;
import com.openjiuwen.agent_teams.interaction.OperatorMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.runtime.test_dispatch_payload} in
 * {@code tests/unit_tests/agent_teams/runtime/test_dispatch_payload.py}.
 */
class TeamRuntimeDispatchPayloadPythonParityTest {

    @Test
    void godViewAlwaysGoesToLeaderWithoutAtParsing() {
        FakeAgent agent = agentWithMembers("dev-1");

        DeliverResult result = TeamRuntimeManager.dispatchPayloads(
                agent,
                List.of(new GodViewMessage("@dev-1 should not be parsed here"))
        ).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("@dev-1 should not be parsed here");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
        assertThat(agent.backend.messageManager.broadcasts).isEmpty();
    }

    @Test
    void operatorMessageDirectRoutesToMember() {
        FakeAgent agent = agentWithMembers("dev-1");

        DeliverResult result = TeamRuntimeManager.dispatchPayloads(
                agent,
                List.of(new OperatorMessage("ping", "dev-1"))
        ).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.autoStartedMembers).containsExactly("dev-1");
        assertThat(agent.backend.messageManager.directMessages)
                .containsExactly(new DirectMessage("ping", "dev-1", "user"));
        assertThat(agent.delivered).isEmpty();
    }

    @Test
    void operatorMessageBroadcastsWhenTargetIsNull() {
        FakeAgent agent = new FakeAgent();

        DeliverResult result = TeamRuntimeManager.dispatchPayloads(
                agent,
                List.of(new OperatorMessage("hello team"))
        ).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.autoStartAllCalls).isEqualTo(1);
        assertThat(agent.backend.messageManager.broadcasts)
                .containsExactly(new BroadcastMessage("hello team", "user"));
    }

    @Test
    void humanAgentMessageDrivesAvatarWhenTargetIsNull() {
        FakeAgent agent = new FakeAgent();
        agent.backend.humanAgents.add("human_alice");

        DeliverResult result = TeamRuntimeManager.dispatchPayloads(
                agent,
                List.of(new HumanAgentMessage("please summarise design.md", "human_alice"))
        ).toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.avatar.delivered).containsExactly("please summarise design.md");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
    }

    @Test
    void interactStrAtMemberRoutesViaOperator() {
        FakeAgent agent = agentWithMembers("dev-1");
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("@dev-1 hi", "alpha", "s1").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.autoStartedMembers).containsExactly("dev-1");
        assertThat(agent.backend.messageManager.directMessages)
                .containsExactly(new DirectMessage("hi", "dev-1", "user"));
        assertThat(agent.delivered).isEmpty();
    }

    @Test
    void interactStrAtAllBroadcastsViaOperator() {
        FakeAgent agent = new FakeAgent();
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("@all status", "alpha", "s1").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.autoStartAllCalls).isEqualTo(1);
        assertThat(agent.backend.messageManager.broadcasts)
                .containsExactly(new BroadcastMessage("status", "user"));
    }

    @Test
    void interactStrHashPrefixRoutesToGodView() {
        FakeAgent agent = new FakeAgent();
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("# raw question", "alpha", "s1").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("raw question");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
        assertThat(agent.backend.messageManager.broadcasts).isEmpty();
    }

    @Test
    void interactStrDollarPrefixDrivesHumanAgent() {
        FakeAgent agent = new FakeAgent();
        agent.backend.humanAgents.add("alice");
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("$alice please summarise", "alpha", "s1")
                .toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.avatar.delivered).containsExactly("please summarise");
        assertThat(agent.delivered).isEmpty();
    }

    @Test
    void interactStrDollarDirectRoutesAsHumanAgent() {
        FakeAgent agent = agentWithMembers("recorder");
        agent.backend.humanAgents.add("human-p1");
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("$human-p1 @recorder hi", "alpha", "s1")
                .toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.autoStartedMembers).containsExactly("recorder");
        assertThat(agent.backend.messageManager.directMessages)
                .containsExactly(new DirectMessage("hi", "recorder", "human-p1"));
        assertThat(agent.delivered).isEmpty();
    }

    @Test
    void interactStrNoPrefixFallsBackToGodView() {
        FakeAgent agent = new FakeAgent();
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("just a plain question", "alpha", "s1")
                .toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("just a plain question");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
    }

    @Test
    void interactStrAtTargetWithoutBodyIsUnparseable() {
        FakeAgent agent = agentWithMembers("dev-1");
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("@dev-1", "alpha", "s1").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("@dev-1");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
    }

    @Test
    void unknownMemberFoldsToGodView() {
        FakeAgent agent = new FakeAgent();
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("@ghost ship it", "alpha", "s1").toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("@ghost ship it");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
    }

    @Test
    void multipleUnknownMembersFoldIntoOneMessage() {
        FakeAgent agent = new FakeAgent();
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("# @g1 @g2 stand-up in 5", "alpha", "s1")
                .toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("@g1 @g2 stand-up in 5");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
    }

    @Test
    void unknownMemberDollarDrivesAvatar() {
        FakeAgent agent = new FakeAgent();
        agent.backend.humanAgents.add("alice");
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("$alice @ghost hi", "alpha", "s1")
                .toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.avatar.delivered).containsExactly("@ghost hi");
        assertThat(agent.backend.messageManager.directMessages).isEmpty();
    }

    @Test
    void partialMatchRoutesKnownAndFoldsUnknown() {
        FakeAgent agent = agentWithMembers("m1");
        TeamRuntimeManager manager = managerWithAgent(agent);

        DeliverResult result = manager.interact("# @m1 @ghost on it", "alpha", "s1")
                .toCompletableFuture().join();

        assertThat(result.ok()).isTrue();
        assertThat(agent.backend.messageManager.directMessages)
                .containsExactly(new DirectMessage("on it", "m1", "user"));
        assertThat(agent.delivered).containsExactly("@ghost on it");
    }

    private static TeamRuntimeManager managerWithAgent(FakeAgent agent) {
        TeamRuntimeManager manager = new TeamRuntimeManager();
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "alpha",
                agent,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));
        return manager;
    }

    private static FakeAgent agentWithMembers(String... members) {
        FakeAgent agent = new FakeAgent();
        agent.backend.knownMembers.addAll(List.of(members));
        return agent;
    }

    private record DirectMessage(String content, String toMemberName, String fromMemberName) {
    }

    private record BroadcastMessage(String content, String fromMemberName) {
    }

    private static final class FakeAgent implements TeamRuntimeManager.TeamAgentRuntime {
        private final FakeBackend backend = new FakeBackend();
        private final FakeAgentRuntime avatar = new FakeAgentRuntime();
        private final List<String> delivered = new ArrayList<>();
        private final List<String> autoStartedMembers = new ArrayList<>();
        private int autoStartAllCalls;

        @Override
        public CompletionStage<Void> deliverInput(String body) {
            delivered.add(body);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public TeamRuntimeManager.TeamBackendRuntime teamBackend() {
            return backend;
        }

        @Override
        public CompletionStage<Void> autoStartAll() {
            autoStartAllCalls += 1;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> autoStartMember(String memberName) {
            autoStartedMembers.add(memberName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<TeamRuntimeManager.TeamAgentRuntime> lookupHumanAgentRuntime(String memberName) {
            return CompletableFuture.completedFuture(avatar);
        }
    }

    private static final class FakeAgentRuntime implements TeamRuntimeManager.TeamAgentRuntime {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public CompletionStage<Void> deliverInput(String body) {
            delivered.add(body);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeBackend implements TeamRuntimeManager.TeamBackendRuntime {
        private final Set<String> knownMembers = new LinkedHashSet<>();
        private final Set<String> humanAgents = new LinkedHashSet<>();
        private final FakeMessageManager messageManager = new FakeMessageManager();

        @Override
        public TeamRuntimeManager.TeamMessageManagerRuntime messageManager() {
            return messageManager;
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            return CompletableFuture.completedFuture(List.copyOf(humanAgents));
        }

        @Override
        public CompletionStage<Object> getMember(String name) {
            return CompletableFuture.completedFuture(knownMembers.contains(name) ? new Object() : null);
        }
    }

    private static final class FakeMessageManager implements TeamRuntimeManager.TeamMessageManagerRuntime {
        private final List<DirectMessage> directMessages = new ArrayList<>();
        private final List<BroadcastMessage> broadcasts = new ArrayList<>();

        @Override
        public CompletionStage<String> broadcastMessage(String content, String fromMemberName) {
            broadcasts.add(new BroadcastMessage(content, fromMemberName));
            return CompletableFuture.completedFuture("bcast-id");
        }

        @Override
        public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
            directMessages.add(new DirectMessage(content, toMemberName, fromMemberName));
            return CompletableFuture.completedFuture("msg-id");
        }
    }
}
