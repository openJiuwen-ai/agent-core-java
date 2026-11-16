/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import io.opentelemetry.context.ContextKey;

/**
 * Semantic convention constants for OpenTelemetry attributes.
 *
 * <p>Standard LLM attributes follow OpenLLMetry / GenAI semantic conventions
 * ({@code gen_ai.*}). Workflow attributes use the project-specific
 * {@code openjiuwen.workflow.*} namespace. Agent (non-LLM) attributes use
 * {@code openjiuwen.agent.*}.</p>
 *
 * <p>Keeping all attribute keys here avoids typo drift between handlers.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.semconv}.</p>
 *
 * @since 0.1.7
 */
public final class SemConv {
    // ===== GenAI standard attributes (aligned with observability/semconv.py) =====
    /** gen_ai.system attribute key. */
    public static final String GEN_AI_SYSTEM = "gen_ai.system";

    /** gen_ai.system attribute value for this framework. */
    public static final String GEN_AI_SYSTEM_VALUE = "openjiuwen";

    /** gen_ai.request.model attribute key. */
    public static final String GEN_AI_REQUEST_MODEL = "gen_ai.request.model";

    /** gen_ai.operation.name attribute key. */
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";

    /** gen_ai.prompt attribute key. */
    public static final String GEN_AI_PROMPT = "gen_ai.prompt";

    /** gen_ai.completion attribute key. */
    public static final String GEN_AI_COMPLETION = "gen_ai.completion";

    /** gen_ai.usage.prompt_tokens attribute key. */
    public static final String GEN_AI_USAGE_PROMPT_TOKENS = "gen_ai.usage.prompt_tokens";

    /** gen_ai.usage.completion_tokens attribute key. */
    public static final String GEN_AI_USAGE_COMPLETION_TOKENS = "gen_ai.usage.completion_tokens";

    /** gen_ai.usage.total_tokens attribute key. */
    public static final String GEN_AI_USAGE_TOTAL_TOKENS = "gen_ai.usage.total_tokens";

    /** gen_ai.usage.input_tokens attribute key (current GenAI semantics). */
    public static final String GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";

    /** gen_ai.usage.output_tokens attribute key (current GenAI semantics). */
    public static final String GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";

    /**
     * gen_ai.usage.cache_read.input_tokens attribute key.
     *
     * <p>Backed by {@link com.openjiuwen.core.foundation.llm.schema.UsageMetadata#getCacheTokens()},
     * which Java clients populate from {@code prompt_tokens_details.cached_tokens}.</p>
     */
    public static final String GEN_AI_USAGE_CACHE_READ_INPUT_TOKENS = "gen_ai.usage.cache_read.input_tokens";

    /** gen_ai.response.finish_reason attribute key. */
    public static final String GEN_AI_RESPONSE_FINISH_REASON = "gen_ai.response.finish_reason";

    /** gen_ai.response.finish_reasons attribute key (plural, JSON array). */
    public static final String GEN_AI_RESPONSE_FINISH_REASONS = "gen_ai.response.finish_reasons";

    /** gen_ai.response.model attribute key. */
    public static final String GEN_AI_RESPONSE_MODEL = "gen_ai.response.model";

    /** gen_ai.provider.name attribute key. */
    public static final String GEN_AI_PROVIDER_NAME = "gen_ai.provider.name";

    /** gen_ai.request.temperature attribute key. */
    public static final String GEN_AI_REQUEST_TEMPERATURE = "gen_ai.request.temperature";

    /** gen_ai.request.top_p attribute key. */
    public static final String GEN_AI_REQUEST_TOP_P = "gen_ai.request.top_p";

    /** gen_ai.request.max_tokens attribute key. */
    public static final String GEN_AI_REQUEST_MAX_TOKENS = "gen_ai.request.max_tokens";

    /** gen_ai.request.message_count attribute key. */
    public static final String GEN_AI_REQUEST_MESSAGE_COUNT = "gen_ai.request.message_count";

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

    /** gen_ai.tool.definitions attribute key. */
    public static final String GEN_AI_TOOL_DEFINITIONS = "gen_ai.tool.definitions";

    /**
     * gen_ai.response.time_to_first_token_ms attribute key.
     *
     * <p>Legacy project key, kept for source compatibility only. It borrows the
     * standard {@code gen_ai.*} namespace for a non-standard name, so this
     * handler no longer writes it; {@link #OJ_GEN_AI_RESPONSE_TIME_TO_FIRST_TOKEN_MS}
     * is the project-correct key for the same measurement.</p>
     */
    public static final String GEN_AI_RESPONSE_TTFT_MS = "gen_ai.response.time_to_first_token_ms";

    /**
     * gen_ai.response.time_to_first_chunk attribute key, unit <b>seconds</b>.
     *
     * <p>Per GenAI semantic conventions this measures the first chunk of
     * <i>any</i> kind. This handler deliberately does not write it: Java can
     * only observe chunks after output-bearing detection, so writing TTFT here
     * would silently substitute a different measurement under a standard name.
     * Reserved for a future source that can time the raw first frame.</p>
     */
    public static final String GEN_AI_RESPONSE_TIME_TO_FIRST_CHUNK = "gen_ai.response.time_to_first_chunk";

    /**
     * openjiuwen.gen_ai.response.time_to_first_token_ms attribute key.
     *
     * <p>Time from request dispatch to the first chunk <b>carrying model
     * output</b>, in milliseconds. This is the real TTFT. It is deliberately
     * <b>not</b> written to the standard {@code gen_ai.response.time_to_first_chunk}:
     * that key measures the first chunk of any kind — on a reasoning model the
     * provider may open with a role-only delta seconds before prefill finishes —
     * so reusing it for TTFT would understate the wait for standard-key
     * consumers. Mirrors Python's {@code OJ_GEN_AI_RESPONSE_TIME_TO_FIRST_TOKEN_MS}.</p>
     */
    public static final String OJ_GEN_AI_RESPONSE_TIME_TO_FIRST_TOKEN_MS =
            "openjiuwen.gen_ai.response.time_to_first_token_ms";

    /**
     * openjiuwen.gen_ai.response.total_latency_ms attribute key.
     *
     * <p>Whole-call elapsed time in milliseconds, from dispatch to the end of
     * the stream. Backfilled from
     * {@link com.openjiuwen.core.foundation.llm.schema.UsageMetadata#getTotalLatency()},
     * which stores the same measurement in seconds. Mirrors Python's
     * {@code OJ_GEN_AI_RESPONSE_TOTAL_LATENCY_MS}.</p>
     */
    public static final String OJ_GEN_AI_RESPONSE_TOTAL_LATENCY_MS =
            "openjiuwen.gen_ai.response.total_latency_ms";

    /**
     * openjiuwen.gen_ai.reasoning.duration_ms attribute key.
     *
     * <p>First-to-last reasoning chunk span in milliseconds. Mirrors Python's
     * {@code OJ_GEN_AI_REASONING_DURATION_MS}.</p>
     */
    public static final String OJ_GEN_AI_REASONING_DURATION_MS =
            "openjiuwen.gen_ai.reasoning.duration_ms";

    /**
     * openjiuwen.gen_ai.usage.{input,output,total}_cost attribute keys.
     *
     * <p>Cost as reported by the provider's pricing table, not recomputed here.
     * Written only when non-zero so an unpriced call is distinguishable from a
     * genuinely free one. Mirror Python's {@code OJ_GEN_AI_USAGE_*_COST}.</p>
     */
    public static final String OJ_GEN_AI_USAGE_INPUT_COST = "openjiuwen.gen_ai.usage.input_cost";

    /** openjiuwen.gen_ai.usage.output_cost attribute key. */
    public static final String OJ_GEN_AI_USAGE_OUTPUT_COST = "openjiuwen.gen_ai.usage.output_cost";

    /** openjiuwen.gen_ai.usage.total_cost attribute key. */
    public static final String OJ_GEN_AI_USAGE_TOTAL_COST = "openjiuwen.gen_ai.usage.total_cost";

    /** openjiuwen.request.retry_count attribute key. */
    public static final String OJ_REQUEST_RETRY_COUNT = "openjiuwen.request.retry_count";

    /**
     * openjiuwen.gen_ai.response.tpot_ms attribute key.
     *
     * <p>Average time per output token in <b>milliseconds</b>, divided by
     * {@code output_tokens - 1}. Mirrors Python's
     * {@code OJ_GEN_AI_RESPONSE_TPOT_MS}.</p>
     */
    public static final String OJ_GEN_AI_RESPONSE_TPOT_MS = "openjiuwen.gen_ai.response.tpot_ms";

    /** langfuse.gen_ai.prompt attribute key. */
    public static final String LANGFUSE_GEN_AI_PROMPT = "langfuse.gen_ai.prompt";

    /** langfuse.gen_ai.completion attribute key. */
    public static final String LANGFUSE_GEN_AI_COMPLETION = "langfuse.gen_ai.completion";

    // ===== openjiuwen.workflow.* — Workflow-level custom attributes =====

    /** openjiuwen.workflow.id. */
    public static final String OJ_WORKFLOW_ID = "openjiuwen.workflow.id";

    /** openjiuwen.workflow.name. */
    public static final String OJ_WORKFLOW_NAME = "openjiuwen.workflow.name";

    /** openjiuwen.workflow.version. */
    public static final String OJ_WORKFLOW_VERSION = "openjiuwen.workflow.version";

    /** openjiuwen.workflow.component.id. */
    public static final String OJ_WORKFLOW_COMPONENT_ID = "openjiuwen.workflow.component.id";

    /** openjiuwen.workflow.component.type. */
    public static final String OJ_WORKFLOW_COMPONENT_TYPE = "openjiuwen.workflow.component.type";

    /** openjiuwen.workflow.component.name. */
    public static final String OJ_WORKFLOW_COMPONENT_NAME = "openjiuwen.workflow.component.name";

    /** openjiuwen.workflow.execution_id. */
    public static final String OJ_WORKFLOW_EXECUTION_ID = "openjiuwen.workflow.execution_id";

    /** openjiuwen.workflow.loop.node_id. */
    public static final String OJ_WORKFLOW_LOOP_NODE_ID = "openjiuwen.workflow.loop.node_id";

    /** openjiuwen.workflow.loop.index. */
    public static final String OJ_WORKFLOW_LOOP_INDEX = "openjiuwen.workflow.loop.index";

    // ===== openjiuwen.agent.* — Agent-level custom attributes (non-LLM types) =====

    /** openjiuwen.agent.invoke_type. */
    public static final String OJ_AGENT_INVOKE_TYPE = "openjiuwen.agent.invoke_type";

    /** openjiuwen.agent.name. */
    public static final String OJ_AGENT_NAME = "openjiuwen.agent.name";

    /** openjiuwen.agent.inputs. */
    public static final String OJ_AGENT_INPUTS = "openjiuwen.agent.inputs";

    /** openjiuwen.agent.outputs. */
    public static final String OJ_AGENT_OUTPUTS = "openjiuwen.agent.outputs";

    /** openjiuwen.agent.error_message. */
    public static final String OJ_AGENT_ERROR_MESSAGE = "openjiuwen.agent.error_message";

    // ===== openjiuwen.sandbox.* — Sandbox-level attributes =====

    /** openjiuwen.sandbox.id — Sandbox instance identifier. */
    public static final String OJ_SANDBOX_ID = "openjiuwen.sandbox.id";

    /** openjiuwen.sandbox.error_message — Sandbox operation error message. */
    public static final String OJ_SANDBOX_ERROR_MESSAGE = "openjiuwen.sandbox.error_message";

    /**
     * Context key for sandbox ID propagation.
     * Singleton instance ensures same key is used across all Context operations.
     */
    public static final ContextKey<String> CTX_SANDBOX_ID = ContextKey.named(OJ_SANDBOX_ID);

    /**
     * Context key for sandbox error message propagation.
     * Singleton instance ensures same key is used across all Context operations.
     */
    public static final ContextKey<String> CTX_SANDBOX_ERROR_MESSAGE =
            ContextKey.named(OJ_SANDBOX_ERROR_MESSAGE);

    // ===== Trace ID bridge — links OTel trace to tracer UUID =====

    /** openjiuwen.trace.id. */
    public static final String OJ_TRACE_ID = "openjiuwen.trace.id";

    /** openjiuwen.session_id. */
    public static final String OJ_SESSION_ID = "openjiuwen.session_id";

    // ===== openjiuwen.* — Base Span attributes (shared by both handlers) =====

    /** openjiuwen.invoke_id. */
    public static final String OJ_INVOKE_ID = "openjiuwen.invoke_id";

    /** openjiuwen.parent_invoke_id. */
    public static final String OJ_PARENT_INVOKE_ID = "openjiuwen.parent_invoke_id";

    /** openjiuwen.start_time. */
    public static final String OJ_START_TIME = "openjiuwen.start_time";

    /** openjiuwen.end_time. */
    public static final String OJ_END_TIME = "openjiuwen.end_time";

    /** openjiuwen.elapsed_time. */
    public static final String OJ_ELAPSED_TIME = "openjiuwen.elapsed_time";

    /** openjiuwen.status. */
    public static final String OJ_STATUS = "openjiuwen.status";

    /** openjiuwen.error. */
    public static final String OJ_ERROR = "openjiuwen.error";

    /** openjiuwen.child_invoke_ids. */
    public static final String OJ_CHILD_INVOKE_IDS = "openjiuwen.child_invoke_ids";

    /** openjiuwen.meta_data. */
    public static final String OJ_META_DATA = "openjiuwen.meta_data";

    // ===== openjiuwen.* — Workflow-specific base attributes =====

    /** openjiuwen.parent_node_id. */
    public static final String OJ_PARENT_NODE_ID = "openjiuwen.parent_node_id";

    /** openjiuwen.source_ids. */
    public static final String OJ_SOURCE_IDS = "openjiuwen.source_ids";

    /** openjiuwen.inner_error. */
    public static final String OJ_INNER_ERROR = "openjiuwen.inner_error";

    /** openjiuwen.stream_inputs. */
    public static final String OJ_STREAM_INPUTS = "openjiuwen.stream_inputs";

    /** openjiuwen.stream_outputs. */
    public static final String OJ_STREAM_OUTPUTS = "openjiuwen.stream_outputs";

    /** openjiuwen.interactive_inputs. */
    public static final String OJ_INTERACTIVE_INPUTS = "openjiuwen.interactive_inputs";

    /** openjiuwen.workflow.inputs. */
    public static final String OJ_WORKFLOW_INPUTS = "openjiuwen.workflow.inputs";

    /** openjiuwen.workflow.outputs. */
    public static final String OJ_WORKFLOW_OUTPUTS = "openjiuwen.workflow.outputs";

    /** openjiuwen.workflow.error_message. */
    public static final String OJ_WORKFLOW_ERROR_MESSAGE = "openjiuwen.workflow.error_message";

    /** openjiuwen.workflow.invoke_data. */
    public static final String OJ_WORKFLOW_INVOKE_DATA = "openjiuwen.workflow.invoke_data";

    private SemConv() {
    }
}
