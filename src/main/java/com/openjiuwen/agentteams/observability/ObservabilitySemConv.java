/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.observability;

/**
 * Semantic convention constants for the agent_teams observability module.
 *
 * <p>Standard LLM attributes follow OpenLLMetry / GenAI semantic conventions
 * ({@code gen_ai.*}). Team collaboration attributes use the project-specific
 * {@code agentteam.*} namespace. DeepAgent task-loop attributes use
 * {@code deepagent.*}. Langfuse attributes use {@code langfuse.*} and the
 * special {@code session.id} key.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.observability.semconv}.</p>
 *
 * @since 0.1.7
 */
public final class ObservabilitySemConv {
    // ===== GenAI standard attributes (gen_ai.*) =====
    /** gen_ai.system attribute key. */
    public static final String GEN_AI_SYSTEM = "gen_ai.system";

    /** gen_ai.system attribute value for this framework. */
    public static final String GEN_AI_SYSTEM_VALUE = "openjiuwen";

    /** gen_ai.operation.name attribute key. */
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";

    /** gen_ai.provider.name attribute key. */
    public static final String GEN_AI_PROVIDER_NAME = "gen_ai.provider.name";

    /** gen_ai.request.model attribute key. */
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";

    /** gen_ai.request.temperature attribute key. */
    public static final String GEN_AI_REQUEST_TEMPERATURE = "gen_ai.request.temperature";

    /** gen_ai.request.top_p attribute key. */
    public static final String GEN_AI_REQUEST_TOP_P = "gen_ai.request.top_p";

    /** gen_ai.request.max_tokens attribute key. */
    public static final String GEN_AI_REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";

    /** gen_ai.request.message_count attribute key. */
    public static final String GEN_AI_REQUEST_MESSAGE_COUNT = "gen_ai.request.message_count";

    /** gen_ai.usage.prompt_tokens attribute key. */
    public static final String GEN_AI_USAGE_PROMPT_TOKENS = "gen_ai.usage.prompt_tokens";

    /** gen_ai.usage.completion_tokens attribute key. */
    public static final String GEN_AI_USAGE_COMPLETION_TOKENS = "gen_ai.usage.completion_tokens";

    /** gen_ai.usage.total_tokens attribute key. */
    public static final String GEN_AI_USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens";

    /** gen_ai.usage.input_tokens attribute key (OpenLLMetry standard alias). */
    public static final String GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";

    /** gen_ai.usage.output_tokens attribute key (OpenLLMetry standard alias). */
    public static final String GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /** gen_ai.conversation.id attribute key. */
    public static final String GEN_AI_CONVERSATION_ID = "gen_ai.conversation.id";

    /** gen_ai.response.finish_reason attribute key. */
    public static final String GEN_AI_RESPONSE_FINISH_REASON = "gen_ai.response.finish_reason";

    /** gen_ai.response.model attribute key. */
    public static final String GEN_AI_RESPONSE_MODEL = "gen_ai.response.model";

    /** gen_ai.response.time_to_first_token_ms attribute key. */
    public static final String GEN_AI_RESPONSE_TTFT_MS = "gen_ai.response.time_to_first_token_ms";

    /** gen_ai.prompt attribute key. */
    public static final String GEN_AI_PROMPT = "gen_ai.prompt";

    /** gen_ai.completion attribute key. */
    public static final String GEN_AI_COMPLETION = "gen_ai.completion";

    /** gen_ai.tool.definitions attribute key. */
    public static final String GEN_AI_TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    /** gen_ai.tool.name attribute key. */
    public static final String GEN_AI_TOOL_NAME = "gen_ai.tool.name";

    /** gen_ai.tool.input attribute key. */
    public static final String GEN_AI_TOOL_INPUT = "gen_ai.tool.input";

    /** gen_ai.tool.output attribute key. */
    public static final String GEN_AI_TOOL_OUTPUT = "gen_ai.tool.output";

    /** gen_ai.tool.id attribute key. */
    public static final String GEN_AI_TOOL_ID = "gen_ai.tool.id";

    /** gen_ai.tool_calls attribute key. */
    public static final String GEN_AI_TOOL_CALLS = "gen_ai.tool_calls";

    // ===== Team collaboration attributes (agentteam.*) =====

    /** agentteam.team.id. */
    public static final String AT_TEAM_ID = "agentteam.team.id";

    /** agentteam.team.name. */
    public static final String AT_TEAM_NAME = "agentteam.team.name";

    /** agentteam.team.display_name. */
    public static final String AT_TEAM_DISPLAY_NAME = "agentteam.team.display_name";

    /** agentteam.team.leader. */
    public static final String AT_TEAM_LEADER = "agentteam.team.leader";

    /** agentteam.event_type. */
    public static final String AT_EVENT_TYPE = "agentteam.event_type";

    /** agentteam.agent.id. */
    public static final String AT_AGENT_ID = "agentteam.agent.id";

    /** agentteam.agent.name. */
    public static final String AT_AGENT_NAME = "agentteam.agent.name";

    /** agentteam.agent.role. */
    public static final String AT_AGENT_ROLE = "agentteam.agent.role";

    /** agentteam.agent.input. */
    public static final String AT_AGENT_INPUT = "agentteam.agent.input";

    /** agentteam.agent.output. */
    public static final String AT_AGENT_OUTPUT = "agentteam.agent.output";

    /** agentteam.session.id. */
    public static final String AT_SESSION_ID = "agentteam.session.id";

    /** agentteam.conversation.id. */
    public static final String AT_CONVERSATION_ID = "agentteam.conversation.id";

    /** agentteam.turn.id. */
    public static final String AT_TURN_ID = "agentteam.turn.id";

    /** agentteam.member.id. */
    public static final String AT_MEMBER_ID = "agentteam.member.id";

    /** agentteam.member.name. */
    public static final String AT_MEMBER_NAME = "agentteam.member.name";

    /** agentteam.member.status.old. */
    public static final String AT_MEMBER_STATUS_OLD = "agentteam.member.status.old";

    /** agentteam.member.status.new. */
    public static final String AT_MEMBER_STATUS_NEW = "agentteam.member.status.new";

    /** agentteam.member.restart_reason. */
    public static final String AT_MEMBER_RESTART_REASON = "agentteam.member.restart_reason";

    /** agentteam.member.restart_count. */
    public static final String AT_MEMBER_RESTART_COUNT = "agentteam.member.restart_count";

    /** agentteam.member.shutdown_force. */
    public static final String AT_MEMBER_SHUTDOWN_FORCE = "agentteam.member.shutdown_force";

    /** agentteam.message.id. */
    public static final String AT_MESSAGE_ID = "agentteam.message.id";

    /** agentteam.message.from. */
    public static final String AT_MESSAGE_FROM = "agentteam.message.from";

    /** agentteam.message.to. */
    public static final String AT_MESSAGE_TO = "agentteam.message.to";

    /** agentteam.message.broadcast. */
    public static final String AT_MESSAGE_BROADCAST = "agentteam.message.broadcast";

    /** agentteam.task.id. */
    public static final String AT_TASK_ID = "agentteam.task.id";

    /** agentteam.task.status. */
    public static final String AT_TASK_STATUS = "agentteam.task.status";

    /** agentteam.task.assignee. */
    public static final String AT_TASK_ASSIGNEE = "agentteam.task.assignee";

    /** agentteam.plan.approved. */
    public static final String AT_PLAN_APPROVED = "agentteam.plan.approved";

    /** agentteam.plan.submitted_by. */
    public static final String AT_PLAN_SUBMITTED_BY = "agentteam.plan.submitted_by";

    // ===== DeepAgent task-loop attributes (deepagent.*) =====

    /** deepagent.task.iteration. */
    public static final String DA_TASK_ITERATION = "deepagent.task.iteration";

    /** deepagent.task.is_follow_up. */
    public static final String DA_TASK_IS_FOLLOW_UP = "deepagent.task.is_follow_up";

    /** deepagent.task.loop_event. */
    public static final String DA_TASK_LOOP_EVENT = "deepagent.task.loop_event";

    // ===== OpenJiuwen namespace attributes (openjiuwen.*) =====

    /** openjiuwen.session.id attribute key (legacy compatibility alias). */
    public static final String OJ_SESSION_ID = "openjiuwen.session.id";

    /** openjiuwen.agent.turn.id attribute key. */
    public static final String OJ_AGENT_TURN_ID = "openjiuwen.agent.turn.id";

    // ===== Langfuse attributes (langfuse.* and special session.id) =====

    /**
     * Langfuse session ID key.
     *
     * <p>Note: LangfuseOtelSpanAttributes defines this as {@code session.id},
     * NOT {@code langfuse.session.id}.</p>
     */
    public static final String LANGFUSE_SESSION_ID = "session.id";

    /** langfuse.trace.name. */
    public static final String LANGFUSE_TRACE_NAME = "langfuse.trace.name";

    /** langfuse.trace.tags. */
    public static final String LANGFUSE_TRACE_TAGS = "langfuse.trace.tags";

    /** langfuse.observation.input. */
    public static final String LANGFUSE_OBSERVATION_INPUT = "langfuse.observation.input";

    /** langfuse.observation.output. */
    public static final String LANGFUSE_OBSERVATION_OUTPUT = "langfuse.observation.output";

    /** langfuse.observation.type. */
    public static final String LANGFUSE_OBSERVATION_TYPE = "langfuse.observation.type";

    /** langfuse.gen_ai.prompt. */
    public static final String LANGFUSE_GEN_AI_PROMPT = "langfuse.gen_ai.prompt";

    /** langfuse.gen_ai.completion. */
    public static final String LANGFUSE_GEN_AI_COMPLETION = "langfuse.gen_ai.completion";

    private ObservabilitySemConv() {
    }
}
