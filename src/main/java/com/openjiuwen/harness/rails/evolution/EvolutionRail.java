/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStore;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Base class for evolution rails with automatic trajectory collection.
 *
 * <p>Mirrors Python's {@code EvolutionRail} in
 * {@code openjiuwen.harness.rails.evolution.evolution_rail}.</p>
 */
public class EvolutionRail extends DeepAgentRail {

    public static final int PRIORITY = 60;
    protected static final int MAX_PENDING_EVOLUTION_OUTCOMES = 32;

    private final TrajectoryStore trajectoryStore;
    private final TrajectoryStore teamTrajectoryStore;
    private final boolean accumulateTrajectory;
    private final EvolutionTriggerPoint evolutionTrigger;
    private final boolean asyncEvolution;

    protected volatile TrajectoryBuilder builder;
    protected final Set<CompletableFuture<?>> bgTasks = ConcurrentHashMap.newKeySet();
    protected final List<OutputSchema> pendingApprovalEvents = new ArrayList<>();
    protected final ArrayDeque<Map<String, String>> pendingEvolutionOutcomes =
            new ArrayDeque<>(MAX_PENDING_EVOLUTION_OUTCOMES);

    public EvolutionRail() {
        this(null, null, false, EvolutionTriggerPoint.AFTER_INVOKE, true);
    }

    public EvolutionRail(TrajectoryStore trajectoryStore) {
        this(trajectoryStore, null, false, EvolutionTriggerPoint.AFTER_INVOKE, true);
    }

    public EvolutionRail(TrajectoryStore trajectoryStore, boolean asyncEvolution) {
        this(trajectoryStore, null, false, EvolutionTriggerPoint.AFTER_INVOKE, asyncEvolution);
    }

    public EvolutionRail(
            TrajectoryStore trajectoryStore,
            TrajectoryStore teamTrajectoryStore,
            boolean accumulateTrajectory,
            EvolutionTriggerPoint evolutionTrigger,
            boolean asyncEvolution) {
        setPriority(PRIORITY);
        this.trajectoryStore = trajectoryStore != null ? trajectoryStore : new InMemoryTrajectoryStore();
        this.teamTrajectoryStore = teamTrajectoryStore;
        this.accumulateTrajectory = accumulateTrajectory;
        this.evolutionTrigger = evolutionTrigger != null ? evolutionTrigger : EvolutionTriggerPoint.AFTER_INVOKE;
        this.asyncEvolution = asyncEvolution;
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof InvokeInputs inputs)) {
            return;
        }
        if (builder != null && shouldAccumulateTrajectory()) {
            onBeforeInvoke(ctx);
            return;
        }
        String agentId = extractAgentId(ctx.getAgent());
        builder = TrajectoryBuilder.builder()
                .sessionId(inputs.getConversationId())
                .source("online")
                .memberId(agentId)
                .meta(agentId == null ? Map.of() : Map.of("member_id", agentId))
                .build();
        onBeforeInvoke(ctx);
    }

    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (builder == null || !(ctx.getInputs() instanceof ModelCallInputs inputs)) {
            return;
        }
        builder.recordStep(buildModelStep(ctx, inputs));
        onAfterModelCall(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_MODEL_CALL) {
            triggerEvolution(buildTrajectory(), ctx);
        }
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (builder == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        builder.recordStep(buildToolStep(inputs));
        onAfterToolCall(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_TOOL_CALL) {
            triggerEvolution(buildTrajectory(), ctx);
        }
    }

    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        onAfterTaskIteration(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_TASK_ITERATION) {
            triggerEvolution(buildTrajectory(), ctx);
        }
    }

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        if (builder == null) {
            return;
        }
        Trajectory trajectory = buildTrajectory();
        if (trajectory == null) {
            return;
        }
        saveTrajectory(trajectory);
        if (teamTrajectoryStore != null) {
            teamTrajectoryStore.save(trajectory, null);
        }
        onAfterInvoke(ctx);
        if (evolutionTrigger == EvolutionTriggerPoint.AFTER_INVOKE) {
            triggerEvolution(trajectory, ctx);
        }
        if (!shouldAccumulateTrajectory()) {
            builder = null;
        }
    }

    public TrajectoryStore getTrajectoryStore() {
        return trajectoryStore;
    }

    public TrajectoryBuilder getBuilder() {
        return builder;
    }

    public boolean isAsyncEvolution() {
        return asyncEvolution;
    }

    public EvolutionTriggerPoint getEvolutionTrigger() {
        return evolutionTrigger;
    }

    protected boolean shouldAccumulateTrajectory() {
        return accumulateTrajectory;
    }

    protected void onBeforeInvoke(AgentCallbackContext ctx) {
    }

    protected void onAfterModelCall(AgentCallbackContext ctx) {
    }

    protected void onAfterToolCall(AgentCallbackContext ctx) {
    }

    protected void onAfterInvoke(AgentCallbackContext ctx) {
    }

    protected void onAfterTaskIteration(AgentCallbackContext ctx) {
    }

    protected void runEvolution(Trajectory trajectory, AgentCallbackContext ctx, Map<String, Object> snapshot) {
    }

    protected Double getEvolutionTotalTimeoutSecs() {
        return null;
    }

    protected Map<String, Object> snapshotForEvolution(Trajectory trajectory, AgentCallbackContext ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("trajectory", trajectory);
        List<?> parsedMessages = extractParsedMessages(ctx);
        if (parsedMessages != null) {
            snapshot.put("parsed_messages", parsedMessages);
        }
        return snapshot;
    }

    protected List<OutputSchema> collectPendingApprovalEvents() {
        List<OutputSchema> events = new ArrayList<>(pendingApprovalEvents);
        pendingApprovalEvents.clear();
        return events;
    }

    public Trajectory buildTrajectory() {
        if (builder == null) {
            return null;
        }
        Trajectory trajectory = builder.buildTrajectory();
        trajectory.setSteps(new ArrayList<>(trajectory.getSteps()));
        if (trajectory.getExecutionId() == null || trajectory.getExecutionId().isBlank()) {
            trajectory.setExecutionId(UUID.randomUUID().toString());
        }
        return trajectory;
    }

    public void saveTrajectory(Trajectory trajectory) {
        if (trajectory != null) {
            trajectoryStore.save(trajectory, null);
        }
    }

    public void triggerEvolution(Trajectory trajectory, AgentCallbackContext ctx) {
        if (trajectory == null) {
            return;
        }
        if (!asyncEvolution) {
            runEvolution(trajectory, ctx, null);
            return;
        }
        Map<String, Object> snapshot = snapshotForEvolution(trajectory, ctx);
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> safeRunEvolution(snapshot));
        bgTasks.add(task);
        task.whenComplete((ignored, error) -> bgTasks.remove(task));
    }

    public void safeRunEvolution(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return;
        }
        Trajectory trajectory = (Trajectory) snapshot.get("trajectory");
        Double totalTimeout = getEvolutionTotalTimeoutSecs();
        try {
            if (totalTimeout != null && totalTimeout > 0) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> runEvolution(trajectory, null, snapshot));
                future.get(Math.max(1L, Math.round(totalTimeout * 1000)), TimeUnit.MILLISECONDS);
            } else {
                runEvolution(trajectory, null, snapshot);
            }
        } catch (java.util.concurrent.TimeoutException timeoutException) {
            appendEvolutionOutcome("timed_out",
                    "background evolution timed out after " + totalTimeout + "s");
        } catch (Exception exception) {
            Throwable cause = exception instanceof java.util.concurrent.ExecutionException
                    && exception.getCause() != null ? exception.getCause() : exception;
            appendEvolutionOutcome("failed", cause.getMessage() != null ? cause.getMessage() : cause.toString());
        }
    }

    public List<Map<String, String>> drainEvolutionOutcomes() {
        List<Map<String, String>> drained = new ArrayList<>(pendingEvolutionOutcomes);
        pendingEvolutionOutcomes.clear();
        return drained;
    }

    public List<OutputSchema> drainPendingApprovalEvents(boolean wait, Duration timeout) {
        if (wait) {
            waitForBackgroundTasks(timeout);
        }
        return collectPendingApprovalEvents();
    }

    public List<OutputSchema> drainPendingApprovalEvents() {
        return drainPendingApprovalEvents(false, Duration.ofSeconds(1));
    }

    public void waitForBackgroundTasks(Duration timeout) {
        CompletableFuture<?>[] futures = bgTasks.toArray(new CompletableFuture<?>[0]);
        if (futures.length == 0) {
            return;
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        try {
            all.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    public void cleanupBackgroundTasks() {
        bgTasks.removeIf(CompletableFuture::isDone);
    }

    protected List<OutputSchema> getPendingApprovalEvents() {
        return pendingApprovalEvents;
    }

    private void appendEvolutionOutcome(String status, String message) {
        if (pendingEvolutionOutcomes.size() == MAX_PENDING_EVOLUTION_OUTCOMES) {
            pendingEvolutionOutcomes.removeFirst();
        }
        pendingEvolutionOutcomes.addLast(Map.of("status", status, "message", message));
    }

    private TrajectoryStep buildModelStep(AgentCallbackContext ctx, ModelCallInputs inputs) {
        String agentId = extractAgentId(ctx.getAgent());
        Map<String, Object> response = inputs.getResponse() instanceof Map<?, ?> rawResponse
                ? copyMap(rawResponse)
                : null;
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("unknown")
                .messages(copyMessages(inputs.getMessages()))
                .response(response)
                .build();
        TrajectoryStep step = new TrajectoryStep();
        step.setKind("llm");
        step.setDetail(detail);
        step.setMeta(Map.of(
                "operator_id", (agentId != null ? agentId : "unknown") + "/llm_main",
                "agent_id", agentId != null ? agentId : "unknown"));
        return step;
    }

    private TrajectoryStep buildToolStep(ToolCallInputs inputs) {
        ToolCallDetail detail = ToolCallDetail.builder()
                .toolName(inputs.getToolName())
                .callArgs(inputs.getToolArgs())
                .callResult(inputs.getToolResult())
                .build();
        TrajectoryStep step = new TrajectoryStep();
        step.setKind("tool");
        step.setDetail(detail);
        step.setMeta(Map.of("operator_id", inputs.getToolName() != null ? inputs.getToolName() : ""));
        return step;
    }

    private static String extractAgentId(Object agent) {
        if (agent == null) {
            return null;
        }
        try {
            Method getCard = agent.getClass().getMethod("getCard");
            Object card = getCard.invoke(agent);
            if (card == null) {
                return null;
            }
            try {
                Method getId = card.getClass().getMethod("getId");
                Object value = getId.invoke(card);
                return value != null ? String.valueOf(value) : null;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<Map<String, Object>> copyMessages(List<Object> messages) {
        List<Map<String, Object>> copies = new ArrayList<>();
        if (messages == null) {
            return copies;
        }
        for (Object message : messages) {
            if (message instanceof Map<?, ?> rawMap) {
                copies.add(copyMap(rawMap));
            }
        }
        return copies;
    }

    private static Map<String, Object> copyMap(Map<?, ?> rawMap) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static List<?> extractParsedMessages(AgentCallbackContext ctx) {
        Object context = ctx != null ? ctx.getContext() : null;
        if (context == null) {
            return null;
        }
        try {
            Method method = context.getClass().getMethod("getMessages");
            Object value = method.invoke(context);
            if (value instanceof List<?> messages) {
                List<Object> normalized = new ArrayList<>();
                for (Object message : messages) {
                    if (message instanceof Map<?, ?> rawMap) {
                        normalized.add(copyMap(rawMap));
                    } else if (message instanceof com.openjiuwen.core.foundation.llm.schema.BaseMessage baseMessage) {
                        normalized.add(Map.of(
                                "role", baseMessage.getRole(),
                                "content", baseMessage.getContent()));
                    }
                }
                return normalized;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
