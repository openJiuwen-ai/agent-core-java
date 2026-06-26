/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundle of callback handlers that emit telemetry spans and events.
 *
 * <p>Mirrors Python's {@code OtelCallbackHandler} in
 * {@code openjiuwen/agent_teams/observability/callback_handler.py}.</p>
 */
public class OtelCallbackHandler {

    public static final String TRACER_NAME = "openjiuwen.agent_teams.observability";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String GEN_AI_SYSTEM_VALUE = "openjiuwen";

    private final ObservabilityConfig config;
    private final TelemetryTracer injectedTracer;

    public OtelCallbackHandler(ObservabilityConfig config) {
        this(config, null);
    }

    public OtelCallbackHandler(ObservabilityConfig config, TelemetryTracer tracer) {
        this.config = config == null ? new ObservabilityConfig() : config;
        this.injectedTracer = tracer;
    }

    public Object onLlmInvokeInput(Map<String, Object> kwargs) {
        return guard("on_llm_invoke_input", () -> openLlmSpan(safeMap(kwargs)));
    }

    public Object onLlmStreamInput(Map<String, Object> kwargs) {
        return guard("on_llm_stream_input", () -> openLlmSpan(safeMap(kwargs)));
    }

    public Object onLlmStreamOutput(Map<String, Object> kwargs) {
        return guard("on_llm_stream_output", () -> {
            LlmSpanState state = SpanContext.popLlmSpanState(true);
            if (state == null) {
                return;
            }
            TelemetrySpan span = state.getSpan();
            Object chunk = safeMap(kwargs).get("result");
            int seq = state.nextChunkSeq();
            if (state.getFirstChunkNs() == null) {
                long firstChunkNs = System.nanoTime();
                state.setFirstChunkNs(firstChunkNs);
                double ttftMs = (firstChunkNs - state.getStartNs()) / 1_000_000.0;
                span.setAttribute(ObservabilitySemconv.GEN_AI_RESPONSE_TTFT_MS, ttftMs);
            }
            String delta = coerceMessageContent(messageContent(chunk));
            span.addEvent("llm.chunk", Map.of("seq", seq, "delta_chars", delta.length()));
            maybeRecordResponseAttrs(state, chunk);
        });
    }

    public Object onLlmInvokeOutput(Map<String, Object> kwargs) {
        return guard("on_llm_invoke_output", () -> {
            LlmSpanState state = SpanContext.popLlmSpanState();
            if (state != null) {
                closeLlmSpan(state, safeMap(kwargs).get("result"));
            }
        });
    }

    public Object onLlmCallError(Map<String, Object> kwargs) {
        return guard("on_llm_call_error", () -> {
            LlmSpanState state = SpanContext.popLlmSpanState();
            if (state == null) {
                return;
            }
            TelemetrySpan span = state.getSpan();
            Object error = safeMap(kwargs).getOrDefault("error", safeMap(kwargs).get("exception"));
            if (error instanceof Throwable throwable) {
                span.recordException(throwable);
                span.setStatus(TelemetrySpan.StatusCode.ERROR, throwable.toString());
            } else {
                span.setStatus(TelemetrySpan.StatusCode.ERROR, "llm call error");
            }
            span.end();
        });
    }

    public Object onToolCallStarted(Map<String, Object> kwargs) {
        return guard("on_tool_call_started", () -> {
            Map<String, Object> values = safeMap(kwargs);
            String toolName = stringValue(values.getOrDefault("tool_name", "unknown"));
            Object toolId = values.get("tool_id");
            Object inputs = values.get("inputs");
            TelemetrySpan span = tracer().startSpan("tool." + toolName, TelemetrySpan.Kind.INTERNAL);
            LlmSpanState parentState = SpanContext.popLlmSpanState(true);
            if (parentState != null) {
                TelemetrySpan parentSpan = parentState.getSpan();
                span.setParent(parentSpan);
            }
            span.setAttribute(ObservabilitySemconv.GEN_AI_TOOL_NAME, toolName);
            if (toolId != null) {
                span.setAttribute("gen_ai.tool.id", String.valueOf(toolId));
            }
            span.setAttribute(
                    ObservabilitySemconv.GEN_AI_TOOL_INPUT,
                    ObservabilityRedaction.redactPrompt(serializeToolInputs(inputs), config)
            );
            SpanContext.pushToolSpan(toolName, span);
        });
    }

    public Object onToolCallFinished(Map<String, Object> kwargs) {
        return guard("on_tool_call_finished", () -> {
            Map<String, Object> values = safeMap(kwargs);
            String toolName = stringValue(values.getOrDefault("tool_name", "unknown"));
            TelemetrySpan span = SpanContext.popToolSpan(toolName);
            if (span == null) {
                return;
            }
            span.setAttribute(
                    ObservabilitySemconv.GEN_AI_TOOL_OUTPUT,
                    ObservabilityRedaction.redactCompletion(values.get("result"), config)
            );
            span.setStatus(TelemetrySpan.StatusCode.OK);
            span.end();
        });
    }

    public Object onToolCallError(Map<String, Object> kwargs) {
        return guard("on_tool_call_error", () -> {
            Map<String, Object> values = safeMap(kwargs);
            String toolName = stringValue(values.getOrDefault("tool_name", "unknown"));
            TelemetrySpan span = SpanContext.popToolSpan(toolName);
            if (span == null) {
                return;
            }
            Object error = values.getOrDefault("error", values.get("exception"));
            if (error instanceof Throwable throwable) {
                span.recordException(throwable);
                span.setStatus(TelemetrySpan.StatusCode.ERROR, throwable.toString());
            } else {
                span.setStatus(TelemetrySpan.StatusCode.ERROR, "tool call error");
            }
            span.end();
        });
    }

    public Object onAgentInvokeInput(Map<String, Object> kwargs) {
        return guard("on_agent_invoke_input", () -> {
            Map<String, Object> values = safeMap(kwargs);
            Object inputs = extractAgentInputs(values);
            AgentInput agentInput = unpackAgentInputs(inputs);
            TelemetrySpan span = tracer().startSpan("agent." + agentInput.agentId(), TelemetrySpan.Kind.INTERNAL);
            span.setAttribute(ObservabilitySemconv.AT_AGENT_ID, agentInput.agentId());
            if (!agentInput.role().isBlank()) {
                span.setAttribute(ObservabilitySemconv.AT_AGENT_ROLE, agentInput.role());
            }
            if (!agentInput.query().isBlank()) {
                span.setAttribute(
                        ObservabilitySemconv.AT_AGENT_INPUT,
                        ObservabilityRedaction.redactPrompt(agentInput.query(), config)
                );
            }
            SpanContext.pushAgentSpan(agentInput.agentId(), span);
        });
    }

    public Object onAgentInvokeOutput(Map<String, Object> kwargs) {
        return guard("on_agent_invoke_output", () -> {
            Map<String, Object> values = safeMap(kwargs);
            AgentInput agentInput = unpackAgentInputs(extractAgentInputs(values));
            TelemetrySpan span = SpanContext.popAgentSpan(agentInput.agentId());
            if (span == null) {
                return;
            }
            span.setAttribute(
                    ObservabilitySemconv.AT_AGENT_OUTPUT,
                    ObservabilityRedaction.redactCompletion(values.get("result"), config)
            );
            span.setStatus(TelemetrySpan.StatusCode.OK);
            span.end();
        });
    }

    private void openLlmSpan(Map<String, Object> kwargs) {
        Object messages = kwargs.getOrDefault("messages", List.of());
        String modelName = stringValue(firstNonBlank(kwargs.get("model"), deriveModelName(kwargs), "unknown"));
        TelemetrySpan span = tracer().startSpan("llm.call", TelemetrySpan.Kind.CLIENT);
        span.setAttribute(ObservabilitySemconv.GEN_AI_SYSTEM, GEN_AI_SYSTEM_VALUE);
        span.setAttribute(ObservabilitySemconv.GEN_AI_REQUEST_MODEL, modelName);
        setNumericAttribute(span, kwargs, "temperature", ObservabilitySemconv.GEN_AI_REQUEST_TEMPERATURE, false);
        setNumericAttribute(span, kwargs, "top_p", ObservabilitySemconv.GEN_AI_REQUEST_TOP_P, false);
        setNumericAttribute(span, kwargs, "max_tokens", ObservabilitySemconv.GEN_AI_REQUEST_MAX_TOKENS, true);

        if (messages instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object message : iterable) {
                String role = messageRole(message);
                String content = coerceMessageContent(messageContent(message));
                span.setAttribute(ObservabilitySemconv.GEN_AI_PROMPT + "." + index + ".role", role);
                span.setAttribute(
                        ObservabilitySemconv.GEN_AI_PROMPT + "." + index + ".content",
                        ObservabilityRedaction.redactPrompt(content, config)
                );
                index += 1;
            }
        }
        SpanContext.pushLlmSpanState(new LlmSpanState(span, System.nanoTime(), null, null, 0));
    }

    private void closeLlmSpan(LlmSpanState state, Object response) {
        TelemetrySpan span = state.getSpan();
        String completionText = coerceMessageContent(messageContent(response));
        String reasoningText = stringValue(readValue(response, "reasoning_content", "reasoningContent"));
        maybeRecordResponseAttrs(state, response);
        span.setAttribute(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.role", "assistant");
        span.setAttribute(
                ObservabilitySemconv.GEN_AI_COMPLETION + ".0.content",
                ObservabilityRedaction.redactCompletion(completionText, config)
        );
        if (!reasoningText.isBlank()) {
            TelemetrySpan reasoningSpan = tracer().startSpan("llm.reasoning", TelemetrySpan.Kind.INTERNAL);
            reasoningSpan.setParent(span);
            reasoningSpan.setAttribute(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.role", "reasoning");
            reasoningSpan.setAttribute(ObservabilitySemconv.GEN_AI_COMPLETION + ".0.is_reasoning", true);
            reasoningSpan.setAttribute(
                    ObservabilitySemconv.GEN_AI_COMPLETION + ".0.content",
                    ObservabilityRedaction.redactCompletion(reasoningText, config)
            );
            reasoningSpan.setStatus(TelemetrySpan.StatusCode.OK);
            reasoningSpan.end();
        }
        span.setStatus(TelemetrySpan.StatusCode.OK);
        span.end();
    }

    private void maybeRecordResponseAttrs(LlmSpanState state, Object response) {
        if (response == null) {
            return;
        }
        TelemetrySpan span = state.getSpan();
        Object usage = readValue(response, "usage_metadata", "usageMetadata");
        if (usage != null) {
            setUsageAttribute(span, usage, "input_tokens", "inputTokens", ObservabilitySemconv.GEN_AI_USAGE_PROMPT_TOKENS);
            setUsageAttribute(span, usage, "output_tokens", "outputTokens", ObservabilitySemconv.GEN_AI_USAGE_COMPLETION_TOKENS);
            setUsageAttribute(span, usage, "total_tokens", "totalTokens", ObservabilitySemconv.GEN_AI_USAGE_TOTAL_TOKENS);
            Object modelName = readValue(usage, "model_name", "modelName");
            if (modelName != null && !String.valueOf(modelName).isBlank()) {
                span.setAttribute(ObservabilitySemconv.GEN_AI_RESPONSE_MODEL, String.valueOf(modelName));
            }
        }
        Object finishReason = readValue(response, "finish_reason", "finishReason");
        if (finishReason != null && !"null".equals(finishReason)) {
            span.setAttribute(ObservabilitySemconv.GEN_AI_RESPONSE_FINISH_REASON, String.valueOf(finishReason));
        }
    }

    private static void setNumericAttribute(
            TelemetrySpan span,
            Map<String, Object> kwargs,
            String sourceKey,
            String attributeKey,
            boolean integer
    ) {
        Object value = kwargs.get(sourceKey);
        if (!(value instanceof Number number)) {
            return;
        }
        span.setAttribute(attributeKey, integer ? number.longValue() : number.doubleValue());
    }

    private static void setUsageAttribute(TelemetrySpan span, Object usage, String snake, String camel, String attribute) {
        Object value = readValue(usage, snake, camel);
        if (value instanceof Number number && number.longValue() != 0L) {
            span.setAttribute(attribute, number.longValue());
        }
    }

    private static Object extractAgentInputs(Map<String, Object> kwargs) {
        Object args = kwargs.get("_args");
        if (args instanceof Object[] array && array.length > 0) {
            return array[0];
        }
        if (args instanceof List<?> list && !list.isEmpty()) {
            return list.getFirst();
        }
        return kwargs.get("inputs");
    }

    private static AgentInput unpackAgentInputs(Object inputs) {
        if (inputs instanceof String text) {
            return new AgentInput("unknown", "", text);
        }
        if (inputs instanceof Map<?, ?> map) {
            String agentId = stringValue(firstNonBlank(map.get("agent_id"), map.get("session_id"), "unknown"));
            String role = stringValue(map.get("role"));
            String query = stringValue(firstNonBlank(map.get("user_input"), map.get("query"), ""));
            return new AgentInput(agentId, role, query);
        }
        return new AgentInput("unknown", "", inputs == null ? "" : String.valueOf(inputs));
    }

    private static String serializeToolInputs(Object inputs) {
        if (inputs == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(inputs);
        } catch (JsonProcessingException error) {
            return String.valueOf(inputs);
        }
    }

    private static String deriveModelName(Map<String, Object> kwargs) {
        Object modelConfig = kwargs.get("model_config");
        Object model = readValue(modelConfig, "model");
        return model == null ? "" : String.valueOf(model);
    }

    private static String messageRole(Object message) {
        Object role = readValue(message, "role");
        return role == null ? "" : String.valueOf(role);
    }

    private static Object messageContent(Object message) {
        Object content = readValue(message, "content");
        return content == null ? "" : content;
    }

    private static String coerceMessageContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(content);
        } catch (JsonProcessingException error) {
            return String.valueOf(content);
        }
    }

    private TelemetryTracer tracer() {
        if (injectedTracer != null) {
            return injectedTracer;
        }
        return ObservabilitySetup.getTracer(TRACER_NAME);
    }

    private Object guard(String callbackName, Runnable action) {
        try {
            action.run();
        } catch (Exception error) {
            TEAM_LOGGER.warning("otel: {} failed: {}", callbackName, error);
        }
        return null;
    }

    private static Map<String, Object> safeMap(Map<String, Object> kwargs) {
        return kwargs == null ? Map.of() : kwargs;
    }

    private static Object firstNonBlank(Object first, Object second, Object fallback) {
        if (first != null && !String.valueOf(first).isBlank()) {
            return first;
        }
        if (second != null && !String.valueOf(second).isBlank()) {
            return second;
        }
        return fallback;
    }

    private static Object readValue(Object target, String... names) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
            return null;
        }
        for (String name : names) {
            for (String methodName : accessorNames(name)) {
                try {
                    Method method = target.getClass().getMethod(methodName);
                    return method.invoke(target);
                } catch (ReflectiveOperationException ignored) {
                    // Try the next accessor.
                }
            }
            try {
                Field field = target.getClass().getField(name);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                // Try the next name.
            }
        }
        return null;
    }

    private static List<String> accessorNames(String name) {
        String camel = toCamel(name);
        String suffix = camel.isEmpty() ? "" : Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        return List.of(name, camel, "get" + suffix, "is" + suffix);
    }

    private static String toCamel(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upper = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upper = true;
            } else if (upper) {
                builder.append(Character.toUpperCase(ch));
                upper = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record AgentInput(String agentId, String role, String query) {
    }
}
