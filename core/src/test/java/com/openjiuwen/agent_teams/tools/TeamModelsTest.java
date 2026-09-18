/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TeamModelsTest {

    @Test
    void teamTaskBriefUsesPythonKeys() {
        TeamTask task = new TeamTask("task-1", "team-a", "Plan", "Draft", "claimed", "alice", 123L);

        Map<String, Object> brief = task.brief();

        assertThat(brief).containsEntry("task_id", "task-1");
        assertThat(brief).containsEntry("title", "Plan");
        assertThat(brief).containsEntry("status", "claimed");
    }

    @Test
    void teamMemberKeepsTeammateDefaultAndSupportsExplicitRole() {
        TeamMember defaultMember = new TeamMember(
                "member-a", "team-a", "Alice", "desc", "{}", "ready",
                "idle", "planner", "prompt", "{\"model_id\":\"m1\"}", 11L);
        TeamMember humanMember = new TeamMember(
                "member-b", "team-a", "Bob", null, "{}", "ready",
                null, "planner", "human_agent", null, null, 22L);

        assertThat(defaultMember.getRole()).isEqualTo("teammate");
        assertThat(humanMember.getRole()).isEqualTo("human_agent");
        assertThat(humanMember.getUpdatedAt()).isEqualTo(22L);
    }

    @Test
    void remainingModelsPreserveOptionalFields() {
        Team team = new Team("team-a", "Alpha", "leader", null, "prompt", 1L, 2L);
        TeamTaskDependency dependency = new TeamTaskDependency("task-2", "task-1", "team-a", false);
        TeamMessage message = new TeamMessage("msg-1", "team-a", "leader", null, "hello", 5L, true, false);
        MessageReadStatus readStatus = new MessageReadStatus("alice", "team-a", 8L);

        assertThat(team.getLeaderMemberName()).isEqualTo("leader");
        assertThat(team.getUpdatedAt()).isEqualTo(2L);
        assertThat(dependency.getDependsOnTaskId()).isEqualTo("task-1");
        assertThat(dependency.getResolved()).isFalse();
        assertThat(message.getToMemberName()).isNull();
        assertThat(message.getBroadcast()).isTrue();
        assertThat(message.getIsRead()).isFalse();
        assertThat(readStatus.getReadAt()).isEqualTo(8L);
    }
}
