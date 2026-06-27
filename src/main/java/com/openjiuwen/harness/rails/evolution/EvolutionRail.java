/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.MemberTrajectorySnapshot;
import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectorySink;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStore;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Captures trajectories and emits evolution trigger snapshots.
 *
 * <p>Mirrors Python's {@code EvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/evolution_rail.py}.</p>
 */
public class EvolutionRail extends DeepAgentRail {

    private final Integer maxTrajectorySteps;
    private final EvolutionTriggerPoint evolutionTrigger;
    private final boolean asyncEvolution;
    private final Set<String> disabledSkills = new LinkedHashSet<>();
    private final List<Map<String, Object>> trajectory = new ArrayList<>();
    private final Queue<Map<String, Object>> hostEvents = new ArrayDeque<>();
    private final TrajectoryStore trajectoryStore;
    private final TrajectoryStore deprecatedTeamTrajectoryStore;
    private TrajectoryBuilder trajectoryBuilder;
    private TrajectorySink trajectorySink;
    private String teamId;
    private String memberRole;

    public EvolutionRail() {
        this(null, null, 100, EvolutionTriggerPoint.AFTER_INVOKE, true, Set.of());
    }

    public EvolutionRail(
            int maxTrajectorySteps,
            EvolutionTriggerPoint evolutionTrigger,
            boolean asyncEvolution,
            Set<String> disabledSkills
    ) {
        this(null, null, Integer.valueOf(maxTrajectorySteps), evolutionTrigger, asyncEvolution, disabledSkills);
    }

    public EvolutionRail(
            Integer maxTrajectorySteps,
            EvolutionTriggerPoint evolutionTrigger,
            boolean asyncEvolution,
            Set<String> disabledSkills
    ) {
        this(null, null, maxTrajectorySteps, evolutionTrigger, asyncEvolution, disabledSkills);
    }

    public EvolutionRail(TrajectoryStore trajectoryStore, boolean asyncEvolution) {
        this(trajectoryStore, null, 200, EvolutionTriggerPoint.AFTER_INVOKE, asyncEvolution, Set.of());
    }

    public EvolutionRail(TrajectoryStore trajectoryStore,
                         TrajectoryStore teamTrajectoryStore,
                         boolean asyncEvolution) {
        this(trajectoryStore, teamTrajectoryStore, 200, EvolutionTriggerPoint.AFTER_INVOKE, asyncEvolution, Set.of());
    }

    public EvolutionRail(TrajectoryStore trajectoryStore,
                         TrajectoryStore teamTrajectoryStore,
                         Integer maxTrajectorySteps,
                         EvolutionTriggerPoint evolutionTrigger,
                         boolean asyncEvolution,
                         Set<String> disabledSkills) {
        setPriority(75);
        this.maxTrajectorySteps = maxTrajectorySteps == null ? null : Math.max(1, maxTrajectorySteps);
        this.evolutionTrigger = evolutionTrigger == null ? EvolutionTriggerPoint.AFTER_INVOKE : evolutionTrigger;
        this.asyncEvolution = asyncEvolution;
        this.trajectoryStore = trajectoryStore == null ? new InMemoryTrajectoryStore() : trajectoryStore;
        this.deprecatedTeamTrajectoryStore = teamTrajectoryStore;
        if (disabledSkills != null) {
            this.disabledSkills.addAll(disabledSkills);
        }
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        trajectory.clear();
        appendStep("before_invoke", ctx);
        Object inputs = ctx.get("inputs");
        if (inputs instanceof InvokeInputs invokeInputs) {
            String sessionId = invokeInputs.getConversationId() == null ? "" : invokeInputs.getConversationId();
            if (trajectoryBuilder == null || !Objects.equals(trajectoryBuilder.getSessionId(), sessionId)) {
                String memberId = agentId(ctx);
                Map<String, Object> meta = memberRole == null
                        ? null
                        : Map.of("member_role", memberRole);
                trajectoryBuilder = TrajectoryBuilder.builder()
                        .sessionId(sessionId)
                        .source("online")
                        .memberId(memberId)
                        .meta(meta)
                        .maxSteps(maxTrajectorySteps)
                        .build();
            }
        }
    }

    @Override
    public void afterModelCall(CallbackContext ctx) {
        appendStep("after_model_call", ctx);
        Object inputs = ctx.get("inputs");
        if (trajectoryBuilder != null && inputs instanceof ModelCallInputs modelInputs) {
            String agentId = agentId(ctx);
            LLMCallDetail detail = LLMCallDetail.builder()
                    .model("unknown")
                    .messages(modelInputs.getMessages())
                    .response(modelInputs.getResponse())
                    .tools(asToolList(modelInputs.getTools()))
                    .build();
            trajectoryBuilder.recordStep(TrajectoryStep.builder()
                    .kind(StepKind.LLM)
                    .detail(detail)
                    .meta(Map.of(
                            "operator_id", agentId + "/llm_main",
                            "agent_id", agentId))
                    .build());
        }
        maybeTrigger(EvolutionTriggerPoint.AFTER_MODEL_CALL, ctx);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        appendStep("after_tool_call", ctx);
        Object inputs = ctx.get("inputs");
        if (trajectoryBuilder != null && inputs instanceof ToolCallInputs toolInputs) {
            ToolCallDetail detail = ToolCallDetail.builder()
                    .toolName(toolInputs.getToolName())
                    .callArgs(toolInputs.getToolArgs())
                    .callResult(toolInputs.getToolResult())
                    .build();
            trajectoryBuilder.recordStep(TrajectoryStep.builder()
                    .kind(StepKind.TOOL)
                    .detail(detail)
                    .meta(Map.of("operator_id", toolInputs.getToolName()))
                    .build());
        }
        maybeTrigger(EvolutionTriggerPoint.AFTER_TOOL_CALL, ctx);
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        appendStep("after_task_iteration", ctx);
        maybeTrigger(EvolutionTriggerPoint.AFTER_TASK_ITERATION, ctx);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        appendStep("after_invoke", ctx);
        Trajectory built = buildStoredTrajectory();
        if (built != null) {
            trajectoryStore.save(built, null);
            publishTrajectorySnapshot(built);
        }
        maybeTrigger(EvolutionTriggerPoint.AFTER_INVOKE, ctx);
    }

    public List<Map<String, Object>> buildTrajectory() {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> step : trajectory) {
            copy.add(new LinkedHashMap<>(step));
        }
        return copy;
    }

    protected List<Map<String, Object>> mutableTrajectory() {
        return trajectory;
    }

    protected void resetTrajectoryBuilder() {
        trajectory.clear();
    }

    protected Map<String, Object> lastTrajectoryStep() {
        if (trajectory.isEmpty()) {
            return null;
        }
        return trajectory.getLast();
    }

    public Set<String> getDisabledSkills() {
        return new LinkedHashSet<>(disabledSkills);
    }

    public boolean isAsyncEvolution() {
        return asyncEvolution;
    }

    public TrajectoryStore getTrajectoryStore() {
        return trajectoryStore;
    }

    public TrajectoryStore getDeprecatedTeamTrajectoryStore() {
        return deprecatedTeamTrajectoryStore;
    }

    public TrajectoryBuilder getTrajectoryBuilder() {
        return trajectoryBuilder;
    }

    public void setTrajectorySink(TrajectorySink sink, String teamId) {
        setTrajectorySink(sink, teamId, null);
    }

    public void setTrajectorySink(TrajectorySink sink, String teamId, Object memberRole) {
        if (sink != null && (teamId == null || teamId.isBlank())) {
            throw new IllegalArgumentException("team_id is required when binding a trajectory sink");
        }
        this.trajectorySink = sink;
        this.teamId = teamId;
        this.memberRole = normalizeMemberRole(memberRole);
    }

    public void emitHostEvent(Map<String, Object> event) {
        if (event != null) {
            hostEvents.add(new LinkedHashMap<>(event));
        }
    }

    public List<Map<String, Object>> drainPendingHostEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        while (!hostEvents.isEmpty()) {
            events.add(hostEvents.remove());
        }
        return events;
    }

    protected boolean allowEvolutionTrigger(EvolutionTriggerPoint triggerPoint, CallbackContext ctx) {
        return evolutionTrigger == triggerPoint && evolutionTrigger != EvolutionTriggerPoint.NONE;
    }

    protected Map<String, Object> snapshotForEvolution(CallbackContext ctx) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("trajectory", buildTrajectory());
        snapshot.put("context", new LinkedHashMap<>(ctx.getValues()));
        snapshot.put("disabled_skills", new ArrayList<>(disabledSkills));
        return snapshot;
    }

    protected void runEvolution(Map<String, Object> snapshot) {
        emitHostEvent(Map.of("type", "evolution_snapshot", "snapshot", snapshot));
    }

    protected void appendStep(String event, CallbackContext ctx) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("event", event);
        String kind = kindForEvent(event);
        if (kind != null) {
            step.put("kind", kind);
        }
        step.put("meta", new LinkedHashMap<String, Object>());
        step.put("values", new LinkedHashMap<>(ctx.getValues()));
        trajectory.add(step);
        if (maxTrajectorySteps != null && trajectory.size() > maxTrajectorySteps) {
            trajectory.removeFirst();
        }
    }

    private String kindForEvent(String event) {
        return switch (event) {
            case "after_model_call" -> "llm";
            case "after_tool_call" -> "tool";
            case "after_task_iteration" -> "task_iteration";
            case "before_invoke", "after_invoke" -> "invoke";
            default -> null;
        };
    }

    private void maybeTrigger(EvolutionTriggerPoint triggerPoint, CallbackContext ctx) {
        if (allowEvolutionTrigger(triggerPoint, ctx)) {
            runEvolution(snapshotForEvolution(ctx));
        }
    }

    private Trajectory buildStoredTrajectory() {
        if (trajectoryBuilder == null) {
            return null;
        }
        Trajectory built = trajectoryBuilder.build();
        built.setSteps(new ArrayList<>(built.getSteps()));
        return built;
    }

    private void publishTrajectorySnapshot(Trajectory built) {
        if (trajectorySink == null || teamId == null || teamId.isBlank()) {
            return;
        }
        Object memberId = built.getMeta().get("member_id");
        if (memberId == null || String.valueOf(memberId).isBlank()) {
            return;
        }
        String snapshotRole = normalizeMemberRole(built.getMeta().get("member_role"));
        if (snapshotRole == null) {
            snapshotRole = memberRole;
        }
        trajectorySink.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                teamId,
                String.valueOf(memberId),
                built,
                snapshotRole,
                built.getSessionId(),
                null));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asToolList(List<Object> rawTools) {
        if (rawTools == null) {
            return null;
        }
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Object rawTool : rawTools) {
            if (rawTool instanceof Map<?, ?> rawMap) {
                Map<String, Object> converted = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> converted.put(String.valueOf(key), value));
                tools.add(converted);
            } else {
                tools.add(new LinkedHashMap<>(Map.of("value", rawTool)));
            }
        }
        return tools;
    }

    private static String agentId(CallbackContext ctx) {
        Object agent = ctx.get("agent") != null ? ctx.get("agent") : ctx.getAgent();
        Object card = memberValue(agent, "card");
        Object id = memberValue(card, "id");
        return id == null ? "unknown" : String.valueOf(id);
    }

    private static String normalizeMemberRole(Object rawRole) {
        if (rawRole == null) {
            return null;
        }
        if (rawRole instanceof CharSequence textRole) {
            String text = textRole.toString();
            return text.isBlank() ? null : text;
        }
        Object value = memberValue(rawRole, "value");
        String text = value == null ? String.valueOf(rawRole) : String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static Object memberValue(Object source, String fieldName) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String methodName : List.of(fieldName, "get" + suffix, "is" + suffix)) {
            Object result = invokeNoArg(source, methodName);
            if (result != null) {
                return result;
            }
        }
        Field field = findField(source.getClass(), fieldName);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(source);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object source, String methodName) {
        Method method = findMethod(source.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(source);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
