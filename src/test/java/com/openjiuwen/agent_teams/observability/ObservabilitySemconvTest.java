/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilitySemconvTest {

    @Test
    void genAiKeysMatchPythonConstants() {
        assertThat(ObservabilitySemconv.GEN_AI_SYSTEM).isEqualTo("gen_ai.system");
        assertThat(ObservabilitySemconv.GEN_AI_REQUEST_MODEL).isEqualTo("gen_ai.request.model");
        assertThat(ObservabilitySemconv.GEN_AI_REQUEST_TEMPERATURE).isEqualTo("gen_ai.request.temperature");
        assertThat(ObservabilitySemconv.GEN_AI_REQUEST_TOP_P).isEqualTo("gen_ai.request.top_p");
        assertThat(ObservabilitySemconv.GEN_AI_REQUEST_MAX_TOKENS).isEqualTo("gen_ai.request.max_tokens");
        assertThat(ObservabilitySemconv.GEN_AI_PROMPT).isEqualTo("gen_ai.prompt");
        assertThat(ObservabilitySemconv.GEN_AI_COMPLETION).isEqualTo("gen_ai.completion");
        assertThat(ObservabilitySemconv.GEN_AI_USAGE_PROMPT_TOKENS).isEqualTo("gen_ai.usage.prompt_tokens");
        assertThat(ObservabilitySemconv.GEN_AI_USAGE_COMPLETION_TOKENS).isEqualTo("gen_ai.usage.completion_tokens");
        assertThat(ObservabilitySemconv.GEN_AI_USAGE_TOTAL_TOKENS).isEqualTo("gen_ai.usage.total_tokens");
        assertThat(ObservabilitySemconv.GEN_AI_RESPONSE_FINISH_REASON).isEqualTo("gen_ai.response.finish_reason");
        assertThat(ObservabilitySemconv.GEN_AI_RESPONSE_MODEL).isEqualTo("gen_ai.response.model");
        assertThat(ObservabilitySemconv.GEN_AI_RESPONSE_TTFT_MS).isEqualTo("gen_ai.response.time_to_first_token_ms");
        assertThat(ObservabilitySemconv.GEN_AI_TOOL_NAME).isEqualTo("gen_ai.tool.name");
        assertThat(ObservabilitySemconv.GEN_AI_TOOL_INPUT).isEqualTo("gen_ai.tool.input");
        assertThat(ObservabilitySemconv.GEN_AI_TOOL_OUTPUT).isEqualTo("gen_ai.tool.output");
    }

    @Test
    void teamKeysMatchPythonConstants() {
        assertThat(ObservabilitySemconv.AT_TEAM_NAME).isEqualTo("agentteam.team.name");
        assertThat(ObservabilitySemconv.AT_TEAM_DISPLAY_NAME).isEqualTo("agentteam.team.display_name");
        assertThat(ObservabilitySemconv.AT_EVENT_TYPE).isEqualTo("agentteam.event_type");
        assertThat(ObservabilitySemconv.AT_AGENT_ID).isEqualTo("agentteam.agent.id");
        assertThat(ObservabilitySemconv.AT_AGENT_ROLE).isEqualTo("agentteam.agent.role");
        assertThat(ObservabilitySemconv.AT_AGENT_INPUT).isEqualTo("agentteam.agent.input");
        assertThat(ObservabilitySemconv.AT_AGENT_OUTPUT).isEqualTo("agentteam.agent.output");
        assertThat(ObservabilitySemconv.AT_MEMBER_NAME).isEqualTo("agentteam.member.name");
        assertThat(ObservabilitySemconv.AT_MEMBER_STATUS_OLD).isEqualTo("agentteam.member.status.old");
        assertThat(ObservabilitySemconv.AT_MEMBER_STATUS_NEW).isEqualTo("agentteam.member.status.new");
        assertThat(ObservabilitySemconv.AT_MEMBER_RESTART_REASON).isEqualTo("agentteam.member.restart_reason");
        assertThat(ObservabilitySemconv.AT_MEMBER_RESTART_COUNT).isEqualTo("agentteam.member.restart_count");
        assertThat(ObservabilitySemconv.AT_MEMBER_SHUTDOWN_FORCE).isEqualTo("agentteam.member.shutdown_force");
        assertThat(ObservabilitySemconv.AT_MESSAGE_ID).isEqualTo("agentteam.message.id");
        assertThat(ObservabilitySemconv.AT_MESSAGE_FROM).isEqualTo("agentteam.message.from");
        assertThat(ObservabilitySemconv.AT_MESSAGE_TO).isEqualTo("agentteam.message.to");
        assertThat(ObservabilitySemconv.AT_MESSAGE_BROADCAST).isEqualTo("agentteam.message.broadcast");
        assertThat(ObservabilitySemconv.AT_TASK_ID).isEqualTo("agentteam.task.id");
        assertThat(ObservabilitySemconv.AT_TASK_STATUS).isEqualTo("agentteam.task.status");
        assertThat(ObservabilitySemconv.AT_TASK_ASSIGNEE).isEqualTo("agentteam.task.assignee");
    }

    @Test
    void deepAgentKeysMatchPythonConstants() {
        assertThat(ObservabilitySemconv.DA_TASK_ITERATION).isEqualTo("deepagent.task.iteration");
        assertThat(ObservabilitySemconv.DA_TASK_IS_FOLLOW_UP).isEqualTo("deepagent.task.is_follow_up");
        assertThat(ObservabilitySemconv.DA_TASK_LOOP_EVENT).isEqualTo("deepagent.task.loop_event");
    }

    @Test
    void extendedKeysMatchPythonConstants() {
        assertThat(ObservabilitySemconv.GEN_AI_OPERATION_NAME).isEqualTo("gen_ai.operation.name");
        assertThat(ObservabilitySemconv.GEN_AI_PROVIDER_NAME).isEqualTo("gen_ai.provider.name");
        assertThat(ObservabilitySemconv.AT_TEAM_ID).isEqualTo("agentteam.team.id");
        assertThat(ObservabilitySemconv.AT_SESSION_ID).isEqualTo("agentteam.session.id");
        assertThat(ObservabilitySemconv.LANGFUSE_SESSION_ID).isEqualTo("session.id");
        assertThat(ObservabilitySemconv.LANGFUSE_GEN_AI_PROMPT).isEqualTo("langfuse.gen_ai.prompt");
        assertThat(ObservabilitySemconv.LANGFUSE_GEN_AI_COMPLETION).isEqualTo("langfuse.gen_ai.completion");
    }
}
