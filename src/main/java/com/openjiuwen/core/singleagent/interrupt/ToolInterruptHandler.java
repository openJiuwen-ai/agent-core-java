/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for tool interruption collection, persistence, and resume.
 *
 * <p>Mirrors Python's {@code ToolInterruptHandler} in
 * {@code openjiuwen/core/single_agent/interrupt/handler.py}.</p>
 */
public class ToolInterruptHandler {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Object agent;
    private final String key = InterruptConstants.INTERRUPTION_KEY;

    public ToolInterruptHandler(Object agent) {
        this.agent = agent;
    }

    public InterruptStateResult buildInterruptState(List<Object> results, List<ToolCall> toolCalls,
                                                    AssistantMessage aiMessage, int iteration,
                                                    String originalQuery) {
        CollectResult collectResult = collectInterrupts(results, toolCalls);
        if (collectResult.getInterruptedTools().isEmpty()) {
            return new InterruptStateResult(null, List.of());
        }
        ToolInterruptionState state = new ToolInterruptionState();
        state.setAiMessage(aiMessage);
        state.setIteration(iteration);
        state.setOriginalQuery(originalQuery);
        state.setInterruptedTools(collectResult.getInterruptedTools());
        state.setAutoConfirmMapping(collectResult.getAutoConfirmMapping());
        return new InterruptStateResult(state, collectResult.getPayloads());
    }

    public static boolean isSubAgentInterrupt(Object result) {
        Object toolResult = tupleFirst(result);
        if (!(toolResult instanceof Map<?, ?> map)) {
            return false;
        }
        return "interrupt".equals(map.get("result_type")) && map.containsKey("interrupt_ids");
    }

    public void save(ToolInterruptionState state, AgentSessionApi session) {
        if (session != null) {
            session.updateState(Map.of(key, state));
        }
    }

    public ToolInterruptionState load(AgentSessionApi session) {
        if (session == null) {
            return null;
        }
        Object state = session.getState(key);
        return state instanceof ToolInterruptionState interruptionState ? interruptionState : null;
    }

    public void clear(AgentSessionApi session) {
        if (session != null) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put(key, null);
            session.updateState(update);
        }
    }

    public CollectResult collectInterrupts(List<Object> results, List<ToolCall> toolCalls) {
        Map<String, ToolInterruptEntry> interruptedTools = new LinkedHashMap<>();
        List<PayloadEntry> payloads = new ArrayList<>();
        Map<String, String> autoConfirmMapping = new LinkedHashMap<>();
        int size = Math.min(results == null ? 0 : results.size(), toolCalls == null ? 0 : toolCalls.size());
        for (int i = 0; i < size; i++) {
            Object toolResult = normalizeToolResult(results.get(i));
            ToolCall toolCall = toolCalls.get(i);
            if (toolResult instanceof ToolInterruptException exception) {
                handleToolInterruptException(exception, toolCall, interruptedTools, payloads, autoConfirmMapping);
            } else if (isSubAgentInterrupt(toolResult)) {
                handleSubAgentInterrupt(toolResult, toolCall, interruptedTools, payloads, autoConfirmMapping);
            }
        }
        return new CollectResult(interruptedTools, payloads, autoConfirmMapping);
    }

    public static Map<String, Object> buildInterruptResult(List<PayloadEntry> payloads) {
        List<String> interruptIds = new ArrayList<>();
        List<Object> stateOutputs = new ArrayList<>();
        if (payloads != null) {
            int idx = 0;
            for (PayloadEntry entry : payloads) {
                interruptIds.add(entry.innerId());
                Object payload = entry.payload();
                if (payload instanceof OutputSchema outputSchema) {
                    stateOutputs.add(outputSchema);
                } else {
                    Map<String, Object> interactionPayload = new LinkedHashMap<>();
                    interactionPayload.put("id", entry.innerId());
                    interactionPayload.put("value", payload);
                    stateOutputs.add(new OutputSchema(
                            InterruptConstants.INTERACTION,
                            idx,
                            new InteractionOutput(entry.innerId(), payload)
                    ));
                }
                idx++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", "interrupt");
        result.put("state", stateOutputs);
        result.put("interrupt_ids", interruptIds);
        return result;
    }

    public Map<String, Object> commitInterrupt(ToolInterruptionState state, AgentSessionApi session,
                                               InvokeInputs invokeInputs,
                                               List<PayloadEntry> payloads) {
        saveAgentContexts(session);
        save(state, session);
        Map<String, Object> result = buildInterruptResult(payloads);
        if (invokeInputs != null) {
            invokeInputs.setResult(result);
        }
        return result;
    }

    public static void writeInterruptToStream(Map<String, Object> result, AgentSessionApi session) {
        if (result == null || session == null) {
            return;
        }
        Object schemas = result.get("state");
        if (!(schemas instanceof List<?> list)) {
            return;
        }
        for (Object schema : list) {
            if (schema instanceof OutputSchema outputSchema) {
                session.writeStream(outputSchema);
            }
        }
    }

    public Map<String, Object> handleResume(ResumeContext resumeContext) {
        ToolInterruptionState state = resumeContext.getState();
        Object userInput = resumeContext.getUserInput();
        AgentCallbackContext ctx = resumeContext.getCtx();
        int resumeIteration = state.getIteration();
        saveAutoConfirmFromState(state, userInput, resumeContext.getSession());
        if (ctx != null) {
            ctx.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, userInput);
        }

        List<ToolCall> toolsToExecute = new ArrayList<>();
        for (ToolInterruptEntry entry : state.getInterruptedTools().values()) {
            ToolCall toolCall = copyToolCall(entry.getToolCall());
            if (entry.isSubAgent()) {
                toolCall = buildSubAgentResumeToolCall(toolCall, userInput);
            }
            toolsToExecute.add(toolCall);
        }

        List<Object> results = List.of();
        if (!toolsToExecute.isEmpty() && resumeContext.getExecuteToolCall() != null) {
            results = resumeContext.getExecuteToolCall().execute(
                    ctx, toolsToExecute, resumeContext.getSession(), resumeContext.getContext());
        } else if (!toolsToExecute.isEmpty()) {
            throw new IllegalStateException("executeToolCall is required to resume interrupted tools");
        }
        if (ctx != null) {
            ctx.getExtra().remove(InterruptConstants.RESUME_USER_INPUT_KEY);
        }

        CollectResult collectResult = collectInterrupts(results, toolsToExecute);
        state.setInterruptedTools(collectResult.getInterruptedTools());
        state.setAutoConfirmMapping(collectResult.getAutoConfirmMapping());
        if (!collectResult.getInterruptedTools().isEmpty()) {
            return commitInterrupt(state, resumeContext.getSession(), resumeContext.getInvokeInputs(),
                    collectResult.getPayloads());
        }
        if (ctx != null) {
            ctx.getExtra().put(InterruptConstants.RESUME_START_ITERATION_KEY, resumeIteration + 1);
        }
        return null;
    }

    public Object getAgent() {
        return agent;
    }

    private static void handleToolInterruptException(ToolInterruptException exception, ToolCall fallbackToolCall,
                                                     Map<String, ToolInterruptEntry> interruptedTools,
                                                     List<PayloadEntry> payloads,
                                                     Map<String, String> autoConfirmMapping) {
        ToolCall toolCall = exception.getToolCall().orElse(fallbackToolCall);
        String outerId = toolCall.getId();
        String innerId = outerId;

        ToolInterruptEntry entry = new ToolInterruptEntry();
        entry.setToolCall(toolCall);
        entry.setInterruptRequests(Map.of(innerId, exception.getRequest()));
        interruptedTools.put(outerId, entry);

        ToolCallInterruptRequest payload = ToolCallInterruptRequest.fromToolCall(exception.getRequest(), toolCall);
        payloads.add(new PayloadEntry(innerId, payload));
        autoConfirmMapping.put(innerId, exception.getRequest().getAutoConfirmKey());
    }

    @SuppressWarnings("unchecked")
    private static void handleSubAgentInterrupt(Object toolResult, ToolCall toolCall,
                                                Map<String, ToolInterruptEntry> interruptedTools,
                                                List<PayloadEntry> payloads,
                                                Map<String, String> autoConfirmMapping) {
        Object actualToolResult = tupleFirst(toolResult);
        if (!(actualToolResult instanceof Map<?, ?> map)) {
            return;
        }
        Object state = map.get("state");
        List<?> subState = state instanceof List<?> list ? list : List.of();
        Map<String, InterruptRequest> interruptRequests = new LinkedHashMap<>();
        for (Object output : subState) {
            if (!(output instanceof OutputSchema outputSchema)) {
                continue;
            }
            Object payload = outputSchema.getPayload();
            Object innerIdValue;
            Object payloadValue;
            if (payload instanceof InteractionOutput interactionOutput) {
                innerIdValue = interactionOutput.getId();
                payloadValue = interactionOutput.getValue();
            } else if (payload instanceof Map<?, ?> payloadMap) {
                innerIdValue = payloadMap.get("id");
                payloadValue = payloadMap.get("value");
            } else {
                continue;
            }
            if (innerIdValue == null || !(payloadValue instanceof ToolCallInterruptRequest request)) {
                continue;
            }
            String innerId = String.valueOf(innerIdValue);
            interruptRequests.put(innerId, request);
            payloads.add(new PayloadEntry(innerId, output));
            if (request.getAutoConfirmKey() != null && !request.getAutoConfirmKey().isEmpty()) {
                autoConfirmMapping.put(innerId, request.getAutoConfirmKey());
            }
        }
        if (!interruptedTools.containsKey(toolCall.getId())) {
            ToolInterruptEntry entry = new ToolInterruptEntry();
            entry.setToolCall(toolCall);
            entry.setInterruptRequests(interruptRequests);
            entry.setSubAgent(true);
            interruptedTools.put(toolCall.getId(), entry);
        }
    }

    public static void saveAutoConfirmFromState(ToolInterruptionState state, Object userInput, AgentSessionApi session) {
        if (state == null || session == null || !(userInput instanceof InteractiveInput interactiveInput)) {
            return;
        }
        Map<String, Object> config = new LinkedHashMap<>();
        Object existingConfig = session.getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        if (existingConfig instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> config.put(String.valueOf(key), value));
        }
        for (Map.Entry<String, Object> entry : interactiveInput.getUserInputs().entrySet()) {
            Object userValue = entry.getValue();
            if (!(userValue instanceof Map<?, ?> userMap) || !Boolean.TRUE.equals(userMap.get("auto_confirm"))) {
                continue;
            }
            String autoConfirmKey = state.getAutoConfirmMapping().get(entry.getKey());
            if (autoConfirmKey != null && !autoConfirmKey.isBlank()) {
                config.put(autoConfirmKey, true);
            }
        }
        session.updateState(Map.of(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY, config));
    }

    private void saveAgentContexts(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        Object contextEngine = readAttribute(agent, "contextEngine");
        if (contextEngine instanceof ContextEngine engine) {
            engine.saveContexts(session);
        }
    }

    public static ToolCall buildSubAgentResumeToolCall(ToolCall toolCall, Object userInput) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (toolCall.getArguments() != null && !toolCall.getArguments().isBlank()) {
            try {
                Map<?, ?> parsed = JSON.readValue(toolCall.getArguments(), Map.class);
                parsed.forEach((key, value) -> args.put(String.valueOf(key), value));
            } catch (JsonProcessingException ignored) {
                args.clear();
            }
        }
        args.put("query", userInput);
        try {
            toolCall.setArguments(JSON.writeValueAsString(args));
        } catch (JsonProcessingException e) {
            toolCall.setArguments(String.valueOf(args));
        }
        return toolCall;
    }

    private static Object normalizeToolResult(Object result) {
        if (result instanceof List<?> list && !list.isEmpty()) {
            return list.get(0);
        }
        if (result instanceof Object[] values && values.length > 0) {
            return values[0];
        }
        return result;
    }

    private static Object tupleFirst(Object value) {
        return normalizeToolResult(value);
    }

    private static ToolCall copyToolCall(ToolCall source) {
        if (source == null) {
            return null;
        }
        return ToolCall.builder()
                .id(source.getId())
                .type(source.getType())
                .name(source.getName())
                .arguments(source.getArguments())
                .index(source.getIndex())
                .build();
    }

    private static Object readAttribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                return target.getClass().getField(name).get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    public record PayloadEntry(String innerId, Object payload) {
    }

    public static final class InterruptStateResult {
        private final ToolInterruptionState state;
        private final List<PayloadEntry> payloads;

        public InterruptStateResult(ToolInterruptionState state, List<PayloadEntry> payloads) {
            this.state = state;
            this.payloads = payloads == null ? List.of() : List.copyOf(payloads);
        }

        public ToolInterruptionState getState() {
            return state;
        }

        public List<PayloadEntry> getPayloads() {
            return payloads;
        }
    }

    public static final class CollectResult {
        private final Map<String, ToolInterruptEntry> interruptedTools;
        private final List<PayloadEntry> payloads;
        private final Map<String, String> autoConfirmMapping;

        public CollectResult(Map<String, ToolInterruptEntry> interruptedTools, List<PayloadEntry> payloads,
                             Map<String, String> autoConfirmMapping) {
            this.interruptedTools = interruptedTools == null ? Map.of() : new LinkedHashMap<>(interruptedTools);
            this.payloads = payloads == null ? List.of() : List.copyOf(payloads);
            this.autoConfirmMapping = autoConfirmMapping == null ? Map.of() : new LinkedHashMap<>(autoConfirmMapping);
        }

        public Map<String, ToolInterruptEntry> getInterruptedTools() {
            return interruptedTools;
        }

        public List<PayloadEntry> getPayloads() {
            return payloads;
        }

        public Map<String, String> getAutoConfirmMapping() {
            return autoConfirmMapping;
        }
    }
}
