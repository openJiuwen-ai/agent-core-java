/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

/**
 * Semantic-convention attribute keys for the trajectory observability profile.
 *
 * <p>Keys mirror the Python Agent Core
 * {@code openjiuwen/extensions/observability/semconv.py} and
 * {@code gen_ai_semconv.py} registries. The swarm WebUI trajectory projector
 * reads these exact strings, so renaming any constant is a public-contract
 * change.</p>
 *
 * @since 0.1.18
 */
public final class TrajectorySemConv {
    // ===== GenAI standard attributes =====
    /** Conversation (session) id binding a span to a chat session. */
    public static final String GEN_AI_CONVERSATION_ID = "gen_ai.conversation.id";

    /** GenAI operation name: {@code chat}, {@code execute_tool}, {@code invoke_agent}. */
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";

    /** Requested model name. */
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";

    /** Requested sampling temperature. */
    public static final String GEN_AI_REQUEST_TEMPERATURE = "gen_ai.request.temperature";

    /** Requested nucleus sampling probability. */
    public static final String GEN_AI_REQUEST_TOP_P = "gen_ai.request.top_p";

    /** Requested maximum output tokens. */
    public static final String GEN_AI_REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";

    /** JSON array of GenAI-structured input messages. */
    public static final String GEN_AI_INPUT_MESSAGES = "gen_ai.input.messages";

    /** JSON array of GenAI-structured output messages. */
    public static final String GEN_AI_OUTPUT_MESSAGES = "gen_ai.output.messages";

    /** JSON array of system instruction blocks. */
    public static final String GEN_AI_SYSTEM_INSTRUCTIONS = "gen_ai.system_instructions";

    /** JSON array of tool definitions offered to the model. */
    public static final String GEN_AI_TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    /** Executed tool name. */
    public static final String GEN_AI_TOOL_NAME = "gen_ai.tool.name";

    /** Tool call id the execution belongs to. */
    public static final String GEN_AI_TOOL_CALL_ID = "gen_ai.tool.call.id";

    /** JSON string of tool call arguments. */
    public static final String GEN_AI_TOOL_CALL_ARGUMENTS = "gen_ai.tool.call.arguments";

    /** JSON string of the tool call result. */
    public static final String GEN_AI_TOOL_CALL_RESULT = "gen_ai.tool.call.result";

    /** Input (prompt) token count. */
    public static final String GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";

    /** Output (completion) token count. */
    public static final String GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /** Reasoning token count. */
    public static final String GEN_AI_USAGE_REASONING_OUTPUT_TOKENS =
            "gen_ai.usage.reasoning.output_tokens";

    /** Cache-read input token count. */
    public static final String GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS =
            "gen_ai.usage.cache_read.input_tokens";

    /** Cache-write input token count. */
    public static final String GEN_AI_USAGE_CACHE_WRITE_INPUT_TOKENS =
            "gen_ai.usage.cache_write.input_tokens";

    /** Total token count. */
    public static final String GEN_AI_USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens";

    /** Finish reasons reported by the provider. */
    public static final String GEN_AI_RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons";

    /** Operation invoked on the agent runtime. */
    public static final String GEN_AI_OPERATION_INVOKE_AGENT = "invoke_agent";

    /** Operation value for chat model calls. */
    public static final String GEN_AI_OPERATION_CHAT = "chat";

    /** Operation value for tool executions. */
    public static final String GEN_AI_OPERATION_EXECUTE_TOOL = "execute_tool";

    // ===== OpenJiuwen trajectory extensions =====

    /** Marks the run-root span of one request. */
    public static final String OJ_TRACE_ROOT = "openjiuwen.trace.root";

    /** Marks the run-root span as completed normally. */
    public static final String OJ_TRACE_COMPLETE = "openjiuwen.trace.complete";

    /** Stable request id shared by every span of one request. */
    public static final String OJ_REQUEST_ID = "openjiuwen.request.id";

    /** Stable run id (same value as the request id for one swarm turn). */
    public static final String OJ_RUN_ID = "openjiuwen.run.id";

    /** ReAct turn id stitching multiple request traces into one displayed turn. */
    public static final String OJ_TURN_ID = "openjiuwen.turn.id";

    /** Human-visible turn number. */
    public static final String OJ_TURN_NUMBER = "openjiuwen.turn.number";

    /** Step id inside the turn. */
    public static final String OJ_STEP_ID = "openjiuwen.step.id";

    /** Step number inside the turn. */
    public static final String OJ_STEP_NUMBER = "openjiuwen.step.number";

    /** Inference call id; one value per chat span. */
    public static final String OJ_INFERENCE_ID = "openjiuwen.inference.id";

    /** Monotonic request number within the session. */
    public static final String OJ_REQUEST_NUMBER = "openjiuwen.request.number";

    /** Request purpose: {@code assistant} or {@code compaction}. */
    public static final String OJ_REQUEST_PURPOSE = "openjiuwen.request.purpose";

    /** Trajectory record kind: {@code turn|step|inference|reasoning|tool|agent|event}. */
    public static final String OJ_TRAJECTORY_RECORD_KIND = "openjiuwen.trajectory.record.kind";

    /** Trajectory payload schema version; {@code 2} for the current projector. */
    public static final String OJ_TRAJECTORY_SCHEMA_VERSION = "openjiuwen.trajectory.schema_version";

    /** Swarm agent mode: {@code work}, {@code code}, {@code team}. */
    public static final String OJ_AGENT_MODE = "openjiuwen.agent.mode";

    /** Execution subject id; {@code main} for the single-agent surface. */
    public static final String OJ_EXECUTION_SUBJECT_ID = "openjiuwen.execution.subject.id";

    /** Human-readable execution subject name. */
    public static final String OJ_EXECUTION_SUBJECT_DISPLAY_NAME =
            "openjiuwen.execution.subject.display_name";

    /** Execution subject kind: {@code main_agent|team_leader|team_member|subagent}. */
    public static final String OJ_EXECUTION_SUBJECT_KIND = "openjiuwen.execution.subject.kind";

    /** Parent execution subject id. */
    public static final String OJ_EXECUTION_SUBJECT_PARENT_ID =
            "openjiuwen.execution.subject.parent_id";

    /** Session id owning the execution subject. */
    public static final String OJ_EXECUTION_SUBJECT_SESSION_ID =
            "openjiuwen.execution.subject.session_id";

    /** Monotonic request number per execution subject. */
    public static final String OJ_EXECUTION_SUBJECT_REQUEST_NUMBER =
            "openjiuwen.execution.subject.request.number";

    // ===== Record kind values =====

    /** Record kind of the run-root span. */
    public static final String RECORD_KIND_TURN = "turn";

    /** Record kind of chat model-call spans. */
    public static final String RECORD_KIND_INFERENCE = "inference";

    /** Record kind of tool-execution spans. */
    public static final String RECORD_KIND_TOOL = "tool";

    /** Record kind of reasoning spans. */
    public static final String RECORD_KIND_REASONING = "reasoning";

    /** Schema version value stamped on every trajectory span. */
    public static final String SCHEMA_VERSION_VALUE = "2";

    /** Default execution subject id for the single-agent surface. */
    public static final String SUBJECT_ID_MAIN = "main";

    /** Execution subject kind for the single-agent surface. */
    public static final String SUBJECT_KIND_MAIN_AGENT = "main_agent";

    /** Request purpose value for assistant turns. */
    public static final String REQUEST_PURPOSE_ASSISTANT = "assistant";

    private TrajectorySemConv() {
    }
}
