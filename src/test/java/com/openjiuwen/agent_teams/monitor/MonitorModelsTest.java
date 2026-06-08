/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonitorModelsTest {

    @Test
    void fromInternalUsesCurrentPublicFieldNames() {
        TeamInfo team = TeamInfo.fromInternal(Map.of(
                "team_name", "team-alpha",
                "display_name", "Alpha",
                "leader_member_name", "leader",
                "created", 1234L
        ));
        MemberInfo member = MemberInfo.fromInternal(Map.of(
                "member_name", "worker-1",
                "team_name", "team-alpha",
                "display_name", "Worker 1",
                "status", "running",
                "execution_status", "busy",
                "mode", "active"
        ));

        assertThat(team.teamName()).isEqualTo("team-alpha");
        assertThat(team.displayName()).isEqualTo("Alpha");
        assertThat(team.leaderMemberName()).isEqualTo("leader");
        assertThat(team.created()).isEqualTo(1234L);
        assertThat(member.memberName()).isEqualTo("worker-1");
        assertThat(member.teamName()).isEqualTo("team-alpha");
        assertThat(member.executionStatus()).isEqualTo("busy");
    }

    @Test
    void taskAndMessageAdaptersPreserveOptionalFields() {
        TaskInfo task = TaskInfo.fromInternal(Map.of(
                "task_id", "task-1",
                "team_name", "team-alpha",
                "title", "Plan",
                "content", "Do work",
                "status", "claimed",
                "assignee", "worker-1",
                "updated_at", 9876L
        ));
        MessageInfo message = MessageInfo.fromInternal(Map.of(
                "message_id", "msg-1",
                "team_name", "team-alpha",
                "from_member_name", "leader",
                "content", "hello",
                "timestamp", 555L,
                "broadcast", true,
                "is_read", false
        ));

        assertThat(task.taskId()).isEqualTo("task-1");
        assertThat(task.updatedAt()).isEqualTo(9876L);
        assertThat(message.messageId()).isEqualTo("msg-1");
        assertThat(message.toMemberName()).isNull();
        assertThat(message.broadcast()).isTrue();
        assertThat(message.isRead()).isFalse();
    }

    @Test
    void monitorEventFiltersUnknownTypesAndFlattensKnownPayloads() {
        EventMessage known = new EventMessage(
                "task_plan_request",
                Map.of(
                        "team_name", "team-alpha",
                        "task_id", "task-2",
                        "status", "planning",
                        "plan_id", "plan-7",
                        "member_plan_md", "draft"
                ),
                ""
        );
        EventMessage unknown = new EventMessage("workspace_conflict", Map.of("team_name", "team-alpha"), "");

        MonitorEvent event = MonitorEvent.fromEventMessage(known);

        assertThat(event).isNotNull();
        assertThat(event.eventType()).isEqualTo(MonitorEventType.TASK_PLAN_REQUEST);
        assertThat(event.teamName()).isEqualTo("team-alpha");
        assertThat(event.taskId()).isEqualTo("task-2");
        assertThat(event.planId()).isEqualTo("plan-7");
        assertThat(event.memberPlanMd()).isEqualTo("draft");
        assertThat(MonitorEvent.fromEventMessage(unknown)).isNull();
    }
}
