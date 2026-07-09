/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

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

    /** gen_ai.tool.name attribute key. */
    public static final String GEN_AI_TOOL_NAME = "gen_ai.tool.name";

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

    // ===== Trace ID bridge — links OTel trace to tracer UUID =====

    /** openjiuwen.trace.id. */
    public static final String OJ_TRACE_ID = "openjiuwen.trace.id";

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
