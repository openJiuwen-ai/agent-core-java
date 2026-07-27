package com.openjiuwen.core.multiagent.runtime;

import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.AgentSessionApi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TeamRuntimeCompatibilityTest {

    @Test
    void teamRuntimeShouldSendPointToPointMessages() {
        TeamRuntime runtime = new TeamRuntime("runtime-team");
        RecordingAgent reviewer = new RecordingAgent("reviewer", Map.of("result", "approved"));
        runtime.registerAgent(reviewer.getCard(), () -> reviewer);

        AgentGroupSessionApi session = new AgentGroupSessionApi("team-session");
        Object result = runtime.send(Map.of("task", "review"), "reviewer", "lead", "team-session", session);

        assertThat(result).isEqualTo(Map.of("result", "approved"));
        assertThat(reviewer.lastInput.get()).isEqualTo(Map.of("task", "review"));
        assertThat(reviewer.lastSessionId.get()).isEqualTo("team-session");
    }

    @Test
    void teamRuntimeShouldFanOutPublishedMessages() {
        TeamRuntime runtime = new TeamRuntime("runtime-team");
        RecordingAgent reviewer = new RecordingAgent("reviewer", "ok");
        RecordingAgent auditor = new RecordingAgent("auditor", "ok");
        runtime.registerAgent(reviewer.getCard(), () -> reviewer);
        runtime.registerAgent(auditor.getCard(), () -> auditor);
        runtime.subscribe("reviewer", "code_*");
        runtime.subscribe("auditor", "code_review");

        runtime.publish(Map.of("event", "done"), "code_review", "lead", "team-pubsub", new AgentGroupSessionApi("team-pubsub"));

        assertThat(reviewer.lastInput.get()).isEqualTo(Map.of("event", "done"));
        assertThat(auditor.lastInput.get()).isEqualTo(Map.of("event", "done"));
        assertThat(reviewer.lastSessionId.get()).isEqualTo("team-pubsub");
        assertThat(auditor.lastSessionId.get()).isEqualTo("team-pubsub");
    }

    @Test
    void communicableAgentShouldReuseBoundRuntimeHelpers() {
        TeamRuntime runtime = new TeamRuntime("runtime-team");
        ResponderAgent responder = new ResponderAgent("responder");
        RequesterAgent requester = new RequesterAgent("requester");
        runtime.registerAgent(responder.getCard(), () -> responder);
        runtime.registerAgent(requester.getCard(), () -> requester);

        Object result = runtime.send(Map.of("task", "ping"), "requester", "lead", "runtime-bridge", new AgentGroupSessionApi("runtime-bridge"));

        assertThat(result).isEqualTo("ack:ping");
        assertThat(responder.lastSessionId.get()).isEqualTo("runtime-bridge");
    }

    @Test
    void baseTeamShouldDelegateAgentRegistrationAndMessagingToRuntime() {
        TeamRuntime runtime = new TeamRuntime("team-demo");
        RecordingAgent reviewer = new RecordingAgent("reviewer", Map.of("result", "accepted"));
        runtime.registerAgent(reviewer.getCard(), () -> reviewer);

        AgentGroupSessionApi session = new AgentGroupSessionApi("team-demo-session");
        Object result = runtime.send(Map.of("task", "review"), "reviewer", "reviewer", "team-demo-session", session);

        assertThat(runtime.getAgentCount()).isEqualTo(1);
        assertThat(runtime.listAgents()).containsExactly("reviewer");
        assertThat(result).isEqualTo(Map.of("result", "accepted"));
    }

    private static final class RequesterAgent extends CommunicableAgent {

        private RequesterAgent(String agentId) {
            super(AgentCard.builder().id(agentId).name(agentId).description(agentId).build());
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            @SuppressWarnings("unchecked")
            String task = String.valueOf(((Map<String, Object>) inputs).get("task"));
            AgentGroupSessionApi groupSession = session instanceof AgentGroupSessionApi api ? api : null;
            return CompletableFuture.completedFuture(
                    send(Map.of("task", task), "responder",
                            session != null ? session.getSessionId() : null,
                            null,
                            groupSession));
        }
    }

    private static final class ResponderAgent extends CommunicableAgent {

        private final AtomicReference<String> lastSessionId = new AtomicReference<>();

        private ResponderAgent(String agentId) {
            super(AgentCard.builder().id(agentId).name(agentId).description(agentId).build());
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            lastSessionId.set(session != null ? session.getSessionId() : null);
            @SuppressWarnings("unchecked")
            String task = String.valueOf(((Map<String, Object>) inputs).get("task"));
            return CompletableFuture.completedFuture("ack:" + task);
        }
    }

    private static final class RecordingAgent extends BaseAgent {

        private final Object output;
        private final AtomicReference<Object> lastInput = new AtomicReference<>();
        private final AtomicReference<String> lastSessionId = new AtomicReference<>();

        private RecordingAgent(String agentId, Object output) {
            super(AgentCard.builder().id(agentId).name(agentId).description(agentId).build());
            this.output = output;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            lastInput.set(inputs);
            lastSessionId.set(session != null ? session.getSessionId() : null);
            return CompletableFuture.completedFuture(output);
        }
    }
}
