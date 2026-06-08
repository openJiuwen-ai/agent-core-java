package com.openjiuwen.agent_teams.schema.events;

import com.openjiuwen.agent_teams.schema.TaskPlanRequestEvent;
import com.openjiuwen.agent_teams.schema.TeamCompletedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventMessageTest {

    @Test
    void teamTopicBuildMatchesPythonFormat() {
        assertThat(TeamTopic.TASK.build("session-1", "team-a"))
                .isEqualTo("session:session-1:team:team-a:task");
    }

    @Test
    void eventMessageRoundTripsNewEventTypes() {
        TeamCompletedEvent event = new TeamCompletedEvent();
        event.setTeamName("team-a");
        event.setMemberCount(3);
        event.setTaskCount(7);

        EventMessage message = EventMessage.fromEvent(event);
        assertThat(message.getEventType()).isEqualTo(TeamEvent.TEAM_COMPLETED);
        assertThat(((TeamCompletedEvent) message.getPayload()).getTaskCount()).isEqualTo(7);

        TaskPlanRequestEvent planRequestEvent = new TaskPlanRequestEvent();
        planRequestEvent.setTeamName("team-a");
        planRequestEvent.setMemberName("member-1");
        planRequestEvent.setTaskId("task-1");

        EventMessage deserialized = EventMessage.deserialize(EventMessage.fromEvent(planRequestEvent).serialize());
        assertThat(deserialized.getEventType()).isEqualTo(TeamEvent.TASK_PLAN_REQUEST);
        assertThat(((TaskPlanRequestEvent) deserialized.getPayload()).getStatus()).isEqualTo("claimed");
    }
}
