/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Focused validation for {@link BaseTeam}.
 */
public final class BaseTeamTest {

    private BaseTeamTest() {
    }

    public static void main(String[] args) {
        constructorCreatesConfigAndRuntimeFromCard();
        addRemoveAndListAgentsDelegateToRuntimeAndMaintainCardMetadata();
        addAgentSkipsDuplicateAndEnforcesMaxAgents();
        messagingValidatesSenderAndRecipientBeforeDelegating();
        configureAndAbstractEntrypointsPreservePythonShape();
    }

    private static void constructorCreatesConfigAndRuntimeFromCard() {
        TeamCard card = new TeamCard("team-id", "review-team", "");
        TestTeam team = new TestTeam(card);

        assertSame(card, team.getCard(), "constructor should retain card reference");
        assertEquals("review-team", team.getTeamId(), "team_id mirrors card.name");
        assertEquals("team-id", team.getRuntime().getConfig().getTeamId(), "runtime team_id mirrors card.id");
        assertEquals(100, team.getRuntime().getConfig().getMessageBus().getMaxQueueSize(),
                "default runtime uses TeamConfig.max_concurrent_messages");
        assertEquals(30.0, team.getRuntime().getConfig().getMessageBus().getProcessTimeout(),
                "default runtime uses TeamConfig.message_timeout");
    }

    private static void addRemoveAndListAgentsDelegateToRuntimeAndMaintainCardMetadata() {
        TestTeam team = new TestTeam(new TeamCard("team-id", "team-name", ""));
        AgentCard coder = new AgentCard("coder", "Coder", "");
        AgentCard reviewer = new AgentCard("reviewer", "Reviewer", "");

        assertSame(team, team.addAgent(coder, ignored -> "coder-agent"), "add_agent returns self");
        team.addAgent(reviewer, ignored -> "reviewer-agent");

        assertEquals(2, team.getAgentCount(), "runtime count should include both agents");
        assertEquals(List.of("coder", "reviewer"), team.listAgents(), "runtime preserves registration order");
        assertSame(coder, team.getAgentCard("coder"), "get_agent_card delegates to runtime");
        assertEquals(2, team.getCard().getAgentCards().size(), "TeamCard metadata mirrors additions");

        assertSame(team, team.removeAgent(coder), "remove_agent by AgentCard returns self");
        assertEquals(List.of("reviewer"), team.listAgents(), "remove_agent unregisters runtime card");
        assertEquals(List.of(reviewer), team.getCard().getAgentCards(), "remove_agent removes matching TeamCard metadata");
    }

    private static void addAgentSkipsDuplicateAndEnforcesMaxAgents() {
        TestTeam duplicateTeam = new TestTeam(new TeamCard("team-id", "team-name", ""));
        AgentCard coder = new AgentCard("coder", "Coder", "");
        duplicateTeam.addAgent(coder, ignored -> "first");
        duplicateTeam.addAgent(coder, ignored -> "second");
        assertEquals(1, duplicateTeam.getAgentCount(), "duplicate agent should be skipped");
        assertEquals(1, duplicateTeam.getCard().getAgentCards().size(), "duplicate should not duplicate TeamCard metadata");

        TeamConfig config = new TeamConfig();
        config.setMaxAgents(1);
        TestTeam cappedTeam = new TestTeam(new TeamCard("team-id", "team-name", ""), config, null);
        cappedTeam.addAgent(new AgentCard("one", "One", ""), ignored -> "one");

        BaseError error = expectThrows(
                BaseError.class,
                () -> cappedTeam.addAgent(new AgentCard("two", "Two", ""), ignored -> "two"),
                "max_agents overflow should raise framework error"
        );
        assertContains(error.getMessage(), "Agent count exceeds max_agents (1)", "error message should preserve Python text");
    }

    private static void messagingValidatesSenderAndRecipientBeforeDelegating() {
        TestTeam team = new TestTeam(new TeamCard("team-id", "team-name", ""));
        AtomicReference<Object> seenPayload = new AtomicReference<>();
        team.addAgent(new AgentCard("sender", "Sender", ""), ignored -> "sender-agent");
        team.addAgent(new AgentCard("recipient", "Recipient", ""),
                ignored -> (Function<Object, Object>) payload -> {
                    seenPayload.set(payload);
                    return "response:" + payload;
                });

        Object response = team.send("payload", "recipient", "sender", "session-1", 2.5)
                .toCompletableFuture()
                .join();
        assertEquals("response:payload", response, "send delegates to runtime");
        assertEquals("payload", seenPayload.get(), "send preserves message payload");

        team.publish("event", "topic", "sender", "session-2").toCompletableFuture().join();

        expectThrows(
                BaseError.class,
                () -> team.send("payload", "recipient", "missing").toCompletableFuture().join(),
                "missing sender should fail before runtime send"
        );
        expectThrows(
                BaseError.class,
                () -> team.publish("event", "topic", "missing").toCompletableFuture().join(),
                "missing publisher should fail before runtime publish"
        );
    }

    private static void configureAndAbstractEntrypointsPreservePythonShape() {
        TestTeam team = new TestTeam(new TeamCard("team-id", "team-name", ""));
        TeamConfig config = new TeamConfig();
        config.setMessageTimeout(42.0);

        assertSame(team, team.configure(config), "configure returns self");
        assertSame(config, team.getConfig(), "configure replaces config reference");

        assertEquals("invoke:hello:none", team.invoke("hello").toCompletableFuture().join(),
                "invoke(message) delegates with null session");
        assertEquals("invoke:hello:s1", team.invoke("hello", new TestSession("s1")).toCompletableFuture().join(),
                "invoke(message, session) passes session");
        assertEquals(List.of("stream:hello:none"), team.stream("hello").toList(),
                "stream(message) delegates with null session");
        assertEquals(List.of("stream:hello:s2"), team.stream("hello", new TestSession("s2")).toList(),
                "stream(message, session) passes session");
    }

    private static <T extends Throwable> T expectThrows(Class<T> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return expected.cast(throwable);
            }
            throw new AssertionError(message + ": expected " + expected.getSimpleName()
                    + " but got " + throwable.getClass().getSimpleName(), throwable);
        }
        throw new AssertionError(message + ": expected exception");
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected same reference");
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertContains(String actual, String expectedSubstring, String message) {
        if (actual == null || !actual.contains(expectedSubstring)) {
            throw new AssertionError(message + ": expected substring=" + expectedSubstring + ", actual=" + actual);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static final class TestTeam extends BaseTeam {

        private TestTeam(TeamCard card) {
            super(card);
        }

        private TestTeam(TeamCard card, TeamConfig config, TeamRuntime runtime) {
            super(card, config, runtime);
        }

        @Override
        public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
            String sessionId = session == null ? "none" : session.getSessionId();
            return CompletableFuture.completedFuture("invoke:" + message + ":" + sessionId);
        }

        @Override
        public Stream<Object> stream(Object message, AgentSessionApi session) {
            String sessionId = session == null ? "none" : session.getSessionId();
            return Stream.of("stream:" + message + ":" + sessionId);
        }
    }

    private record TestSession(String sessionId) implements AgentSessionApi {
        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
