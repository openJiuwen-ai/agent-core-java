/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

/**
 * Semantic-convention constants for observability attributes.
 * <p>
 * Mirrors Python's module constants in
 * {@code openjiuwen/agent_teams/observability/semconv.py}.
 */
public final class ObservabilitySemconv {

    public static final String GEN_AI_SYSTEM = "gen_ai.system";
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    public static final String GEN_AI_PROVIDER_NAME = "gen_ai.provider.name";
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";
    public static final String GEN_AI_REQUEST_TEMPERATURE = "gen_ai.request.temperature";
    public static final String GEN_AI_REQUEST_TOP_P = "gen_ai.request.top_p";
    public static final String GEN_AI_REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";
    public static final String GEN_AI_REQUEST_MESSAGE_COUNT = "gen_ai.request.message_count";
    public static final String GEN_AI_REQUEST_MESSAGE_COUNT_PREFIX = "gen_ai.request.prev_message_count.";
    public static final String GEN_AI_PROMPT = "gen_ai.prompt";
    public static final String GEN_AI_COMPLETION = "gen_ai.completion";
    public static final String GEN_AI_USAGE_PROMPT_TOKENS = "gen_ai.usage.prompt_tokens";
    public static final String GEN_AI_USAGE_COMPLETION_TOKENS = "gen_ai.usage.completion_tokens";
    public static final String GEN_AI_USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens";
    public static final String GEN_AI_USAGE_CACHE_TOKENS = "gen_ai.usage.cache_tokens";
    public static final String GEN_AI_USAGE_REASONING_TOKENS = "gen_ai.usage.reasoning_tokens";
    public static final String GEN_AI_RESPONSE_FINISH_REASON = "gen_ai.response.finish_reason";
    public static final String GEN_AI_RESPONSE_MODEL = "gen_ai.response.model";
    public static final String GEN_AI_RESPONSE_TTFT_MS = "gen_ai.response.time_to_first_token_ms";
    public static final String GEN_AI_REASONING_DURATION_MS = "gen_ai.reasoning.duration_ms";
    public static final String GEN_AI_TOOL_DEFINITIONS = "gen_ai.tool.definitions";
    public static final String GEN_AI_TOOL_NAME = "gen_ai.tool.name";
    public static final String GEN_AI_TOOL_INPUT = "gen_ai.tool.input";
    public static final String GEN_AI_TOOL_OUTPUT = "gen_ai.tool.output";
    public static final String GEN_AI_TOOL_ID = "gen_ai.tool.id";
    public static final String GEN_AI_TOOL_CALLS = "gen_ai.tool_calls";

    public static final String AT_TEAM_ID = "agentteam.team.id";
    public static final String AT_TEAM_NAME = "agentteam.team.name";
    public static final String AT_TEAM_DISPLAY_NAME = "agentteam.team.display_name";
    public static final String AT_TEAM_LEADER = "agentteam.team.leader";
    public static final String AT_EVENT_TYPE = "agentteam.event_type";
    public static final String AT_AGENT_ID = "agentteam.agent.id";
    public static final String AT_AGENT_NAME = "agentteam.agent.name";
    public static final String AT_AGENT_ROLE = "agentteam.agent.role";
    public static final String AT_AGENT_INPUT = "agentteam.agent.input";
    public static final String AT_AGENT_OUTPUT = "agentteam.agent.output";
    public static final String AT_SESSION_ID = "agentteam.session.id";
    public static final String AT_MEMBER_ID = "agentteam.member.id";
    public static final String AT_MEMBER_NAME = "agentteam.member.name";
    public static final String AT_MEMBER_STATUS_OLD = "agentteam.member.status.old";
    public static final String AT_MEMBER_STATUS_NEW = "agentteam.member.status.new";
    public static final String AT_MEMBER_RESTART_REASON = "agentteam.member.restart_reason";
    public static final String AT_MEMBER_RESTART_COUNT = "agentteam.member.restart_count";
    public static final String AT_MEMBER_SHUTDOWN_FORCE = "agentteam.member.shutdown_force";
    public static final String AT_MESSAGE_ID = "agentteam.message.id";
    public static final String AT_MESSAGE_FROM = "agentteam.message.from";
    public static final String AT_MESSAGE_TO = "agentteam.message.to";
    public static final String AT_MESSAGE_BROADCAST = "agentteam.message.broadcast";
    public static final String AT_TASK_ID = "agentteam.task.id";
    public static final String AT_TASK_STATUS = "agentteam.task.status";
    public static final String AT_TASK_ASSIGNEE = "agentteam.task.assignee";
    public static final String AT_PLAN_APPROVED = "agentteam.plan.approved";
    public static final String AT_PLAN_SUBMITTED_BY = "agentteam.plan.submitted_by";

    public static final String DA_TASK_ITERATION = "deepagent.task.iteration";
    public static final String DA_TASK_IS_FOLLOW_UP = "deepagent.task.is_follow_up";
    public static final String DA_TASK_LOOP_EVENT = "deepagent.task.loop_event";

    public static final String LANGFUSE_TRACE_NAME = "langfuse.trace.name";
    public static final String LANGFUSE_TRACE_TAGS = "langfuse.trace.tags";
    public static final String LANGFUSE_SESSION_ID = "session.id";
    public static final String LANGFUSE_OBSERVATION_INPUT = "langfuse.observation.input";
    public static final String LANGFUSE_OBSERVATION_OUTPUT = "langfuse.observation.output";
    public static final String LANGFUSE_OBSERVATION_TYPE = "langfuse.observation.type";
    public static final String LANGFUSE_GEN_AI_PROMPT = "langfuse.gen_ai.prompt";
    public static final String LANGFUSE_GEN_AI_COMPLETION = "langfuse.gen_ai.completion";

    private ObservabilitySemconv() {
    }
}
