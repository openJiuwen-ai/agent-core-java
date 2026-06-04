/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Handler for tool interruption.
 *
 * <p>Mirrors Python's {@code ToolInterruptHandler} in
 * {@code openjiuwen.core.single_agent.interrupt.handler}.</p>
 */
public class ToolInterruptHandler {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final Object agent;
    private final String key = InterruptConstants.INTERRUPTION_KEY;

    /**
     * Create ToolInterruptHandler.
     *
     * @param agent the ReActAgent instance
     */
    public ToolInterruptHandler(Object agent) {
        this.agent = agent;
    }

    /**
     * Build interrupt state from tool results.
     *
     * @param results        tool execution results
     * @param toolCalls      tool calls that were executed
     * @param aiMessage      the AI message
     * @param iteration      the iteration number
     * @param originalQuery  the original user query
     * @return tuple of interruption state and payloads
     */
    public InterruptStateResult buildInterruptState(
            List<Object> results,
            List<ToolCall> toolCalls,
            AssistantMessage aiMessage,
            int iteration,
            String originalQuery
    ) {
        CollectResult collectResult = collectInterrupts(results, toolCalls);

        if (collectResult.interruptedTools.isEmpty()) {
            return new InterruptStateResult(null, new ArrayList<>());
        }

        ToolInterruptionState state = ToolInterruptionState.create(
                aiMessage,
                iteration,
                originalQuery,
                collectResult.interruptedTools,
                collectResult.autoConfirmMapping
        );

        return new InterruptStateResult(state, collectResult.payloads);
    }

    /**
     * Check if result is a sub-agent interrupt.
     */
    public static boolean isSubAgentInterrupt(Object result) {
        Object toolResult = result;
        if (result instanceof Object[] arr && arr.length >= 1) {
            toolResult = arr[0];
        }

        if (toolResult instanceof Map map) {
            return map.get("result_type").equals("interrupt")
                    && map.containsKey("interrupt_ids");
        }
        return false;
    }

    /**
     * Process sub-agent interrupt result.
     */
    public static void processSubAgentInterrupt(
            Map<String, Object> toolResult,
            ToolCall toolCall,
            Map<String, ToolInterruptEntry> interruptEntries,
            Map<String, String> idMappings,
            List<Object> subAgentOutputs
    ) {
        List<String> subIds = (List<String>) toolResult.getOrDefault("interrupt_ids", new ArrayList<>());
        List<Object> subState = (List<Object>) toolResult.getOrDefault("state", new ArrayList<>());

        Map<String, InterruptRequest> interruptRequests = new HashMap<>();
        for (Object output : subState) {
            if (output instanceof OutputSchema schema) {
                Object payload = schema.getPayload();
                if (payload instanceof InteractionOutput interactionOutput) {
                    String innerId = interactionOutput.getId();
                    Object requestValue = interactionOutput.getValue();
                    if (requestValue instanceof InterruptRequest) {
                        interruptRequests.put(innerId, (InterruptRequest) requestValue);
                    }
                }
            }
        }

        interruptEntries.put(toolCall.getId(), ToolInterruptEntry.builder()
                .toolCall(toolCall)
                .interruptRequests(interruptRequests)
                .build());

        for (String innerId : subIds) {
            idMappings.put(innerId, toolCall.getId());
        }

        subAgentOutputs.addAll(subState);
    }

    /**
     * Save tool interruption state to session.
     */
    public void save(ToolInterruptionState state, Session session) {
        if (session != null) {
            session.updateState(Map.of(key, state));
        }
    }

    /**
     * Load tool interruption state from session.
     */
    public ToolInterruptionState load(Session session) {
        if (session != null) {
            Object state = session.getState(key);
            if (state instanceof ToolInterruptionState) {
                return (ToolInterruptionState) state;
            }
        }
        return null;
    }

    /**
     * Clear tool interruption state from session.
     */
    public void clear(Session session) {
        if (session != null) {
            session.updateState(Map.of(key, null));
        }
    }

    /**
     * Handle tool interrupt exception.
     */
    public static void handleToolInterruptException(
            ToolInterruptException toolResult,
            ToolCall toolCall,
            Map<String, ToolInterruptEntry> interruptedTools,
            List<Object[]> payloads,
            Map<String, String> autoConfirmMapping
    ) {
        ToolCall tc = (ToolCall) toolResult.getToolCall().orElse(toolCall);
        String outerId = tc.getId();
        String innerId = outerId;

        interruptedTools.put(outerId, ToolInterruptEntry.builder()
                .toolCall(tc)
                .interruptRequests(Map.of(innerId, toolResult.getRequest()))
                .build());

        ToolCallInterruptRequest payload = ToolCallInterruptRequest.fromToolCall(
                toolResult.getRequest(), tc
        );
        payloads.add(new Object[]{innerId, payload});

        autoConfirmMapping.put(innerId, toolResult.getRequest().getAutoConfirmKey());
    }

    /**
     * Handle sub-agent interrupt.
     */
    public static void handleSubAgentInterrupt(
            Object toolResult,
            ToolCall toolCall,
            Map<String, ToolInterruptEntry> interruptedTools,
            List<Object[]> payloads,
            Map<String, String> autoConfirmMapping
    ) {
        String outerId = toolCall.getId();

        Object actualToolResult = toolResult;
        if (toolResult instanceof Object[] arr && arr.length >= 1) {
            actualToolResult = arr[0];
        }

        List<Object> subState = new ArrayList<>();
        if (actualToolResult instanceof Map map) {
            subState = (List<Object>) map.getOrDefault("state", new ArrayList<>());
        }

        Map<String, InterruptRequest> interruptRequests = new HashMap<>();

        for (Object output : subState) {
            if (!(output instanceof OutputSchema schema)) {
                continue;
            }
            Object payload = schema.getPayload();
            if (!(payload instanceof InteractionOutput interactionOutput)) {
                continue;
            }

            String innerId = interactionOutput.getId();
            Object payloadObj = interactionOutput.getValue();

            if (payloadObj instanceof ToolCallInterruptRequest tcir) {
                interruptRequests.put(innerId, tcir);
                payloads.add(new Object[]{innerId, output});
                if (tcir.getAutoConfirmKey() != null && !tcir.getAutoConfirmKey().isEmpty()) {
                    autoConfirmMapping.put(innerId, tcir.getAutoConfirmKey());
                }
            }
        }

        if (!interruptedTools.containsKey(outerId)) {
            interruptedTools.put(outerId, ToolInterruptEntry.builder()
                    .toolCall(toolCall)
                    .interruptRequests(interruptRequests)
                    .isSubAgent(true)
                    .build());
        }
    }

    /**
     * Collect tool interrupts and sub-agent interrupts.
     */
    public CollectResult collectInterrupts(List<Object> results, List<ToolCall> toolCalls) {
        Map<String, ToolInterruptEntry> interruptedTools = new HashMap<>();
        List<Object[]> payloads = new ArrayList<>();
        Map<String, String> autoConfirmMapping = new HashMap<>();

        for (int i = 0; i < results.size(); i++) {
            Object result = results.get(i);
            ToolCall toolCall = toolCalls.get(i);

            if (result instanceof ToolInterruptException tie) {
                handleToolInterruptException(tie, toolCall, interruptedTools, payloads, autoConfirmMapping);
            } else if (isSubAgentInterrupt(result)) {
                handleSubAgentInterrupt(result, toolCall, interruptedTools, payloads, autoConfirmMapping);
            }
        }

        return new CollectResult(interruptedTools, payloads, autoConfirmMapping);
    }

    /**
     * Build interrupt result from payloads.
     */
    public static Map<String, Object> buildInterruptResult(List<Object[]> payloads) {
        List<String> interruptIds = new ArrayList<>();
        List<Object> stateOutputs = new ArrayList<>();

        if (payloads != null) {
            int idx = 0;
            for (Object[] entry : payloads) {
                String innerId = (String) entry[0];
                Object payload = entry[1];

                interruptIds.add(innerId);
                if (payload instanceof OutputSchema schema) {
                    stateOutputs.add(schema);
                } else {
                    stateOutputs.add(new OutputSchema(
                            InterruptConstants.INTERACTION,
                            idx,
                            new InteractionOutput(innerId, payload)));
                }
                idx++;
            }
        }

        return Map.of(
                "result_type", "interrupt",
                "state", stateOutputs,
                "interrupt_ids", interruptIds
        );
    }

    /**
     * Commit interrupt to session.
     */
    public CompletableFuture<Map<String, Object>> commitInterrupt(
            ToolInterruptionState state,
            ModelContext context,
            Session session,
            InvokeInputs invokeInputs,
            List<Object[]> payloads
    ) {
        // Persist tool interruption state and return interrupt dict
        // Note: context_engine.save_contexts is called separately
        save(state, session);
        Map<String, Object> result = buildInterruptResult(payloads);
        invokeInputs.setResult(result);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Write interrupt result to session stream.
     */
    public static CompletableFuture<Void> writeInterruptToStream(
            Map<String, Object> result,
            Session session
    ) {
        List<Object> schemas = (List<Object>) result.getOrDefault("state", new ArrayList<>());
        for (Object schema : schemas) {
            if (schema instanceof OutputSchema outputSchema) {
                session.writeStream(outputSchema);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle resume from interruption.
     */
    public CompletableFuture<Map<String, Object>> handleResume(ResumeContext resumeCtx) {
        ToolInterruptionState state = resumeCtx.getState();
        Object userInput = resumeCtx.getUserInput();
        AgentCallbackContext ctx = resumeCtx.getCtx();
        ModelContext context = resumeCtx.getContext();
        Session session = resumeCtx.getSession();
        InvokeInputs invokeInputs = resumeCtx.getInvokeInputs();

        int resumeIteration = state.getIteration();
        Loggers.AGENT.info("Resuming tool interrupt from iteration {}", resumeIteration + 1);

        saveAutoConfirmFromState(state, userInput, session);

        ctx.getExtra().put(InterruptConstants.RESUME_USER_INPUT_KEY, userInput);

        List<ToolCall> toolsToExecute = new ArrayList<>();
        for (Map.Entry<String, ToolInterruptEntry> entry : state.getInterruptedTools().entrySet()) {
            ToolCall tc = copyToolCall(entry.getValue().getToolCall());
            if (entry.getValue().isSubAgent()) {
                tc = buildSubAgentResumeToolCall(tc, userInput);
            }
            toolsToExecute.add(tc);
        }

        List<Object> results = new ArrayList<>();
        if (!toolsToExecute.isEmpty() && resumeCtx.getExecuteToolCall() != null) {
            // Execute tool calls via callback
            Object[] params = new Object[]{ctx, toolsToExecute, session, context};
            Object resultObj = resumeCtx.getExecuteToolCall().apply(params);
            if (resultObj instanceof List) {
                results = (List<Object>) resultObj;
            }
        }

        ctx.getExtra().remove(InterruptConstants.RESUME_USER_INPUT_KEY);

        CollectResult collectResult = collectInterrupts(results, toolsToExecute);

        state.setInterruptedTools(collectResult.interruptedTools);
        state.setAutoConfirmMapping(collectResult.autoConfirmMapping);

        if (!collectResult.interruptedTools.isEmpty()) {
            return commitInterrupt(state, context, session, invokeInputs, collectResult.payloads);
        }

        ctx.getExtra().put(InterruptConstants.RESUME_START_ITERATION_KEY, resumeIteration + 1);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Save auto-confirm config from user input.
     */
    public static void saveAutoConfirmFromState(
            ToolInterruptionState state,
            Object userInput,
            Session session
    ) {
        if (session == null) {
            return;
        }

        if (!(userInput instanceof InteractiveInput interactiveInput)) {
            return;
        }

        Map<String, Object> config = new HashMap<>();
        Object existingConfig = session.getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        if (existingConfig instanceof Map) {
            config = (Map<String, Object>) existingConfig;
        }

        for (Map.Entry<String, Object> entry : interactiveInput.getUserInputs().entrySet()) {
            String innerId = entry.getKey();
            Object userValue = entry.getValue();
            if (userValue instanceof Map valueMap && valueMap.containsKey("auto_confirm")) {
                if (!Boolean.TRUE.equals(valueMap.get("auto_confirm"))) {
                    continue;
                }
                String autoConfirmKey = state.getAutoConfirmMapping().get(innerId);
                if (autoConfirmKey != null) {
                    config.put(autoConfirmKey, true);
                }
            }
        }

        session.updateState(Map.of(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY, config));
    }

    /**
     * Build tool call for sub-agent resume.
     */
    public static ToolCall buildSubAgentResumeToolCall(ToolCall toolCall, Object userInput) {
        Map<String, Object> args = new HashMap<>();
        try {
            if (toolCall.getArguments() instanceof String argStr && !argStr.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JSON_MAPPER.readValue(argStr, Map.class);
                args = new HashMap<>(parsed);
            }
        } catch (JsonProcessingException e) {
            args = new HashMap<>();
        }

        args.put("query", userInput);
        try {
            toolCall.setArguments(JSON_MAPPER.writeValueAsString(args));
        } catch (JsonProcessingException e) {
            toolCall.setArguments(args.toString());
        }
        return toolCall;
    }

    /**
     * Copy tool call.
     */
    private ToolCall copyToolCall(ToolCall tc) {
        return ToolCall.builder()
                .id(tc.getId())
                .name(tc.getName())
                .arguments(tc.getArguments())
                .index(tc.getIndex())
                .build();
    }

    // Helper classes for results

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class InterruptStateResult {
        private ToolInterruptionState state;
        private List<Object[]> payloads;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class CollectResult {
        private Map<String, ToolInterruptEntry> interruptedTools;
        private List<Object[]> payloads;
        private Map<String, String> autoConfirmMapping;
    }
}
