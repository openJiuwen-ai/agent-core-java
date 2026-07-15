
package com.openjiuwen.core.multiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.HierarchicalMsgBusTeam;
import com.openjiuwen.core.multiagent.teams.hierarchical_msgbus.HierarchicalMsgBusTeamConfig;
import com.openjiuwen.core.multiagent.teams.hierarchical_tools.HierarchicalToolsTeam;
import com.openjiuwen.core.multiagent.teams.hierarchical_tools.HierarchicalToolsTeamConfig;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

class HierarchicalTeamsCompatibilityTest {
    @Test
    void hierarchicalToolsTeamShouldDispatchToRootAgent() {
        TeamCard card = teamCard("hier-tools");
        AgentCard root = agentCard("root");
        HierarchicalToolsTeam team = new HierarchicalToolsTeam(card, new HierarchicalToolsTeamConfig(root));
        team.addAgent(root, () -> new EchoAgent(root, "root-ok"));

        Object result = team.invoke(Map.of("query", "work"), new AgentGroupSessionApi("hier-tools-session"));

        assertThat(result).isEqualTo("root-ok");
    }

    @Test
    void hierarchicalMsgBusTeamShouldDispatchToSupervisor() {
        TeamCard card = teamCard("hier-msgbus");
        AgentCard supervisor = agentCard("supervisor");
        HierarchicalMsgBusTeam team =
            new HierarchicalMsgBusTeam(card, new HierarchicalMsgBusTeamConfig(supervisor, 30.0));
        team.addAgent(supervisor, () -> new EchoAgent(supervisor, Map.of("result", "supervised")));

        Object result = team.invoke("hello", new AgentGroupSessionApi("hier-msgbus-session"));

        assertThat(result).isEqualTo(Map.of("result", "supervised"));
    }

    @Test
    void hierarchicalConfigsShouldKeepNamedEntryAgents() {
        AgentCard root = agentCard("root");
        AgentCard supervisor = agentCard("supervisor");

        assertThat(new HierarchicalToolsTeamConfig(root).getRootAgent()).isEqualTo(root);
        assertThat(new HierarchicalMsgBusTeamConfig(supervisor, 1800.0).getSupervisorAgent()).isEqualTo(supervisor);
    }

    private static TeamCard teamCard(String id) {
        TeamCard card = new TeamCard();
        card.setId(id);
        card.setName(id);
        return card;
    }

    private static AgentCard agentCard(String id) {
        return AgentCard.builder().id(id).name(id).description(id).build();
    }

    private static final class EchoAgent extends BaseAgent {
        private final Object output;

        private EchoAgent(AgentCard card, Object output) {
            super(card);
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
