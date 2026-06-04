/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.harness.prompts.sections.TaskCompletionSection;
import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.MaxRoundsEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.TimeoutEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Task-completion strategy Rail.
 * <p>
 * Carries loop-strategy parameters and implements the lifecycle hooks
 * that drive completion detection and prompt injection.
 * <p>
 * Mirrors Python's {@code TaskCompletionRail} in
 * {@code openjiuwen.harness.rails.task_completion_rail}.
 */
public class TaskCompletionRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TaskCompletionRail.class);

    /** Rail priority (low = runs first). */
    public static final int PRIORITY = 10;

    /** Promise tag pattern for completion detection. */
    private static final Pattern PROMISE_TAG_PATTERN =
            Pattern.compile("<promise>\\s*(.*?)\\s*</promise>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final String taskInstruction;
    private final String completionPromise;
    private final int requiredConfirmations;
    private final boolean allowPromiseDetails;
    private final Integer maxRounds;
    private final Double timeoutSeconds;
    private final List<StopConditionEvaluator> extraEvaluators;

    private int confirmCount = 0;
    private int iterationCount = 0;
    private long startTimeMs;

    /**
     * Create a TaskCompletionRail.
     *
     * @param taskInstruction      format string with {query} placeholder
     * @param completionPromise    token the model outputs to signal completion
     * @param requiredConfirmations number of confirmations needed
     * @param allowPromiseDetails  whether to allow details inside promise tags
     * @param maxRounds            max outer-loop rounds
     * @param timeoutSeconds       wall-clock timeout
     */
    public TaskCompletionRail() {
        this(null, null, 1, false, null, null, null);
    }

    public TaskCompletionRail(String taskInstruction, String completionPromise,
                              int requiredConfirmations, boolean allowPromiseDetails,
                              Integer maxRounds, Double timeoutSeconds) {
        this(taskInstruction, completionPromise, requiredConfirmations, allowPromiseDetails,
                maxRounds, timeoutSeconds, null);
    }

    public TaskCompletionRail(String taskInstruction, String completionPromise,
                              int requiredConfirmations, boolean allowPromiseDetails,
                              Integer maxRounds, Double timeoutSeconds,
                              List<StopConditionEvaluator> evaluators) {
        super();
        this.taskInstruction = taskInstruction;
        this.completionPromise = completionPromise;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
        this.allowPromiseDetails = allowPromiseDetails;
        this.maxRounds = maxRounds;
        this.timeoutSeconds = timeoutSeconds;
        this.extraEvaluators = evaluators != null ? new ArrayList<>(evaluators) : new ArrayList<>();
    }

    /** Detect completion promise in model output. */
    public Optional<String> detectPromise(String output) {
        if (completionPromise == null || output == null) {
            return Optional.empty();
        }
        String content = extractPromiseBlock(output);
        if (content == null) {
            return Optional.empty();
        }
        String normalized = normalize(content);
        String expected = normalize(completionPromise);
        if (normalized.equalsIgnoreCase(expected)
                || (allowPromiseDetails && promiseMatches(content, completionPromise))) {
            confirmCount++;
            LOG.info("[TaskCompletionRail] Completion promise detected ({}/{})",
                    confirmCount, requiredConfirmations);
            if (confirmCount >= requiredConfirmations) {
                return Optional.of(expected);
            }
        }
        return Optional.empty();
    }

    /** Check if max rounds exceeded. */
    public boolean isMaxRoundsExceeded() {
        return maxRounds != null && iterationCount >= maxRounds;
    }

    /** Check if timeout exceeded. */
    public boolean isTimeoutExceeded() {
        if (timeoutSeconds == null) {
            return false;
        }
        return (System.currentTimeMillis() - startTimeMs) / 1000.0 > timeoutSeconds;
    }

    /** Increment iteration counter. */
    public void incrementIteration() {
        iterationCount++;
    }

    @Override
    public void init(Object agent) {
        this.startTimeMs = System.currentTimeMillis();
        LOG.info("[TaskCompletionRail] Initialized (maxRounds={}, timeout={}s)",
                maxRounds, timeoutSeconds);
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TaskCompletionRail] Uninitialized after {} iterations", iterationCount);
    }

    public List<StopConditionEvaluator> buildEvaluators() {
        List<StopConditionEvaluator> result = new ArrayList<>();
        if (maxRounds != null) {
            result.add(new MaxRoundsEvaluator(maxRounds));
        }
        if (timeoutSeconds != null) {
            result.add(new TimeoutEvaluator(timeoutSeconds));
        }
        if (completionPromise != null) {
            result.add(new CompletionPromiseEvaluator(completionPromise, requiredConfirmations));
        }
        result.addAll(extraEvaluators);
        return result;
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (completionPromise == null || completionPromise.isBlank() || ctx == null) {
            return;
        }
        Object builderObj = resolveBuilder(ctx.getAgent());
        if (builderObj instanceof SystemPromptBuilder builder) {
            builder.addSection(TaskCompletionSection.build(builder.getLanguage(), completionPromise));
        }
    }

    @Override
    public void beforeTaskIteration(AgentCallbackContext ctx) {
        if (taskInstruction == null || taskInstruction.isBlank() || ctx == null) {
            return;
        }
        if (ctx.getInputs() instanceof TaskIterationInputs inputs
                && inputs.getQuery() != null
                && !inputs.getQuery().isBlank()
                && !inputs.isFollowUp()) {
            inputs.setQuery(taskInstruction.replace("{query}", inputs.getQuery()));
        }
    }

    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        if (completionPromise == null || completionPromise.isBlank() || ctx == null) {
            return;
        }
        String output = extractOutput(ctx);
        Optional<String> matched = detectPromise(output);
        if (matched.isEmpty()) {
            notifyAbsent(ctx);
            return;
        }
        notifyFulfilled(ctx, matched.get());
    }

    public static String extractPromiseBlock(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = PROMISE_TAG_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    public static boolean promiseMatches(String block, String expected) {
        if (block == null || block.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        String expectedNorm = normalize(expected);
        String firstLine = block.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(block.trim());
        String firstNorm = normalize(firstLine);
        return firstNorm.equals(expectedNorm) || firstNorm.startsWith(expectedNorm + " ");
    }

    private static String normalize(String text) {
        return text == null ? "" : String.join(" ", text.trim().split("\\s+"));
    }

    private static String extractOutput(AgentCallbackContext ctx) {
        if (ctx.getInputs() instanceof TaskIterationInputs taskInputs) {
            Map<String, Object> result = taskInputs.getResult();
            Object output = result != null ? result.get("output") : null;
            return output != null ? String.valueOf(output) : null;
        }
        return null;
    }

    private static Object resolveBuilder(Object agent) {
        Object value = invokeNoArg(agent, "getSystemPromptBuilder");
        if (value != null) {
            return value;
        }
        return readField(agent, "systemPromptBuilder");
    }

    private static void notifyFulfilled(AgentCallbackContext ctx, String text) {
        Object evaluator = resolveCompletionEvaluator(ctx);
        if (evaluator instanceof CompletionPromiseEvaluator promiseEvaluator) {
            promiseEvaluator.notifyFulfilled(text);
        } else {
            invokeOneArg(evaluator, "notifyFulfilled", text);
        }
    }

    private static void notifyAbsent(AgentCallbackContext ctx) {
        Object evaluator = resolveCompletionEvaluator(ctx);
        if (evaluator instanceof CompletionPromiseEvaluator promiseEvaluator) {
            promiseEvaluator.notifyAbsent();
        } else {
            invokeNoArg(evaluator, "notifyAbsent");
        }
    }

    private static Object resolveCompletionEvaluator(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        Object coordinator = readField(agent, "loopCoordinator");
        if (coordinator == null) {
            coordinator = readField(agent, "loop_coordinator");
        }
        if (coordinator == null) {
            coordinator = invokeNoArg(agent, "getLoopCoordinator");
        }
        return invokeNoArg(coordinator, "getCompletionPromiseEvaluator");
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void invokeOneArg(Object target, String methodName, Object arg) {
        if (target == null) {
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                try {
                    method.setAccessible(true);
                    method.invoke(target, arg);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to invoke " + methodName, e);
                }
            }
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }
}
