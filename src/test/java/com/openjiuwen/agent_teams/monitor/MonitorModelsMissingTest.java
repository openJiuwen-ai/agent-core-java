/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.TaskPlanRequestEvent;
import com.openjiuwen.agent_teams.schema.TaskPlanResponseEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/monitor/test_models.py}.
 */
class MonitorModelsMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void monitorEventExposesLightweightPlanRequestFields() {
        TaskPlanRequestEvent request = new TaskPlanRequestEvent();
        request.setTeamName("team");
        request.setMemberName("member");
        request.setTaskId("task-1");
        request.setPlanId("plan-1");
        request.setMemberPlanMd("/tmp/member_plan.md");
        request.setToolCallId("tool-1");

        MonitorEvent monitorEvent = MonitorEvent.fromEventMessage(EventMessage.fromEvent(request));

        assertThat(monitorEvent).isNotNull();
        assertThat(monitorEvent.eventType()).isEqualTo(MonitorEventType.TASK_PLAN_REQUEST);
        assertThat(monitorEvent.taskId()).isEqualTo("task-1");
        assertThat(monitorEvent.planId()).isEqualTo("plan-1");
        assertThat(monitorEvent.memberPlanMd()).isEqualTo("/tmp/member_plan.md");
        assertThat(asMap(monitorEvent)).doesNotContainKey("tool_call_id");
    }

    @Test
    void monitorEventDoesNotExposePlanResponseFeedback() {
        TaskPlanResponseEvent response = new TaskPlanResponseEvent();
        response.setTeamName("team");
        response.setMemberName("member");
        response.setTaskId("task-1");
        response.setPlanId("plan-1");
        response.setApproved(false);
        response.setStatus("claimed");
        response.setFeedback("revise implementation details");
        response.setToolCallId("tool-1");

        MonitorEvent monitorEvent = MonitorEvent.fromEventMessage(EventMessage.fromEvent(response));

        assertThat(monitorEvent).isNotNull();
        assertThat(monitorEvent.eventType()).isEqualTo(MonitorEventType.TASK_PLAN_RESPONSE);
        assertThat(monitorEvent.taskId()).isEqualTo("task-1");
        assertThat(monitorEvent.planId()).isEqualTo("plan-1");
        assertThat(monitorEvent.approved()).isFalse();
        assertThat(asMap(monitorEvent)).doesNotContainKeys("feedback", "tool_call_id");
    }

    private static Map<String, Object> asMap(MonitorEvent monitorEvent) {
        return OBJECT_MAPPER.convertValue(monitorEvent, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }
}
