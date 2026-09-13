
package com.openjiuwen.core.multiagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffOrchestrator;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffRequest;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffRoute;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffSignal;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTeam;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTeamConfig;
import com.openjiuwen.core.multiagent.teams.handoff.HandoffTool;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

class HandoffCompatibilityTest {
    @Test
    void handoffConfigShouldPreserveDefaultsAndRoutes() {
        HandoffRoute route = new HandoffRoute("a", "b");
        HandoffConfig config = HandoffConfig.builder().routes(List.of(route)).build();
        HandoffTeamConfig teamConfig = new HandoffTeamConfig(config);

        assertThat(config.getMaxHandoffs()).isEqualTo(10);
        assertThat(config.getRoutes()).containsExactly(route);
        assertThat(teamConfig.getHandoff()).isSameAs(config);
    }

    @Test
    void handoffRequestShouldExposeSessionId() {
        AgentGroupSessionApi session = new AgentGroupSessionApi("sid-123");
        HandoffRequest request = HandoffRequest.builder().inputMessage("hello").session(session).build();

        assertThat(request.getSessionId()).isEqualTo("sid-123");
        assertThat(request.getHistory()).isEmpty();
    }

    @Test
    void handoffSignalShouldExtractNestedPayload() {
        HandoffSignal signal =
            HandoffSignal.extract(Map.of("output", Map.of(HandoffSignal.HANDOFF_TARGET_KEY, "billing",
                    HandoffSignal.HANDOFF_MESSAGE_KEY, "ctx", HandoffSignal.HANDOFF_REASON_KEY, "needs specialist")));

        assertThat(signal).isNotNull();
        assertThat(signal.target()).isEqualTo("billing");
        assertThat(signal.message()).isEqualTo("ctx");
        assertThat(signal.reason()).isEqualTo("needs specialist");
    }

    @Test
    void handoffToolShouldEmitStructuredDirective() throws Exception {
        HandoffTool tool = new HandoffTool("billing");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
            (Map<String, Object>) tool.invoke(Map.of("reason", "needs billing", "message", "carry context"));

        assertThat(payload).containsEntry(HandoffSignal.HANDOFF_TARGET_KEY, "billing");
        assertThat(payload).containsEntry(HandoffSignal.HANDOFF_REASON_KEY, "needs billing");
        assertThat(payload).containsEntry(HandoffSignal.HANDOFF_MESSAGE_KEY, "carry context");
    }

    @Test
    void handoffOrchestratorShouldRespectRoutesAndSnapshot() {
        HandoffConfig config =
            HandoffConfig.builder().routes(List.of(new HandoffRoute("a", "b"))).maxHandoffs(2).build();
        HandoffOrchestrator orchestrator = new HandoffOrchestrator("a", List.of("a", "b", "c"), config);

        assertThat(orchestrator.requestHandoff("b")).isTrue();
        assertThat(orchestrator.requestHandoff("c")).isFalse();

        AgentGroupSessionApi session = new AgentGroupSessionApi("handoff-session");
        orchestrator.saveToSession(session);
        HandoffOrchestrator restored =
            HandoffOrchestrator.restoreFromSession(session, "a", List.of("a", "b", "c"), config);

        assertThat(restored.getCurrentAgentId()).isEqualTo("b");
        assertThat(restored.getHandoffCount()).isEqualTo(1);
    }

    @Test
    void handoffTeamShouldTransferControlAcrossAgents() {
        TeamCard card = new TeamCard();
        card.setId("handoff-team");
        card.setName("handoff-team");
        HandoffConfig handoff = HandoffConfig.builder().startAgent(agentCard("triage"))
                .routes(List.of(new HandoffRoute("triage", "billing"))).build();
        HandoffTeam team = new HandoffTeam(card, new HandoffTeamConfig(handoff));
        team.addAgent(agentCard("triage"),
                () -> new StaticAgent("triage",
                        Map.of(HandoffSignal.HANDOFF_TARGET_KEY, "billing", HandoffSignal.HANDOFF_REASON_KEY,
                                "needs billing", HandoffSignal.HANDOFF_MESSAGE_KEY, "invoice")));
        team.addAgent(agentCard("billing"), () -> new StaticAgent("billing", Map.of("result", "billing:invoice")));

        Object result = team.invoke(Map.of("query", "pay"), new AgentGroupSessionApi("handoff-team-session"));

        assertThat(result).isEqualTo(Map.of("result", "billing:invoice"));
    }

    @Test
    void handoffTeamShouldRejectDisallowedRoute() {
        TeamCard card = new TeamCard();
        card.setId("handoff-team");
        card.setName("handoff-team");
        HandoffConfig handoff = HandoffConfig.builder().startAgent(agentCard("triage"))
                .routes(List.of(new HandoffRoute("triage", "support"))).build();
        HandoffTeam team = new HandoffTeam(card, new HandoffTeamConfig(handoff));
        team.addAgent(agentCard("triage"),
                () -> new StaticAgent("triage", Map.of(HandoffSignal.HANDOFF_TARGET_KEY, "billing")));
        team.addAgent(agentCard("billing"), () -> new StaticAgent("billing", Map.of("result", "done")));

        assertThatThrownBy(() -> team.invoke("pay", new AgentGroupSessionApi("handoff-team-session")))
                .isInstanceOf(RuntimeException.class);
    }

    private static AgentCard agentCard(String id) {
        return AgentCard.builder().id(id).name(id).description(id).build();
    }

    private static final class StaticAgent extends BaseAgent {
        private final Object output;

        private StaticAgent(String id, Object output) {
            super(agentCard(id));
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
            return output;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(output).iterator();
        }
    }
}
