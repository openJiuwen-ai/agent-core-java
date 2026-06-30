package com.openjiuwen.core.multiagent.runtime;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
        DemoTeam team = new DemoTeam("team-demo");
        RecordingAgent reviewer = new RecordingAgent("reviewer", Map.of("result", "accepted"));
        team.addAgent(reviewer.getCard(), () -> reviewer);

        Object result = team.invoke(Map.of("task", "review"), new AgentGroupSessionApi("team-demo-session"));

        assertThat(team.getAgentCount()).isEqualTo(1);
        assertThat(team.listAgents()).containsExactly("reviewer");
        assertThat(result).isEqualTo(Map.of("result", "accepted"));
    }

    private static final class DemoTeam extends BaseTeam {

        private DemoTeam(String teamId) {
            super(buildCard(teamId), new TeamConfig());
        }

        @Override
        public Object invoke(Object message, AgentGroupSessionApi groupSession) {
            AgentGroupSessionApi resolved = groupSession != null
                    ? groupSession
                    : new AgentGroupSessionApi();
            return send(message, "reviewer", getTeamCard().getId(), resolved.getSessionId(), resolved);
        }

        @Override
        public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
            return List.of(invoke(message, session)).iterator();
        }

        private static TeamCard buildCard(String teamId) {
            TeamCard card = new TeamCard();
            card.setId(teamId);
            card.setName(teamId);
            card.setDescription("demo team");
            return card;
        }
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
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            @SuppressWarnings("unchecked")
            String task = String.valueOf(((Map<String, Object>) inputs).get("task"));
            return send(Map.of("task", task), "responder", session != null ? session.getSessionId() : null, null,
                    session instanceof AgentGroupSessionApi api ? api : null);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
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
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            lastSessionId.set(session != null ? session.getSessionId() : null);
            @SuppressWarnings("unchecked")
            String task = String.valueOf(((Map<String, Object>) inputs).get("task"));
            return "ack:" + task;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
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
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            lastInput.set(inputs);
            lastSessionId.set(session != null ? session.getSessionId() : null);
            return output;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
        }
    }
}
