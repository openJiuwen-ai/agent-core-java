/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.harness.DeepAgent;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event handler used by the DeepAgent task-loop controller.
 *
 * <p>Mirrors Python's {@code TaskLoopEventHandler} in
 * {@code openjiuwen/harness/task_loop/task_loop_event_handler.py}.</p>
 */
public class TaskLoopEventHandler extends EventHandler {

    private final DeepAgent deepAgent;
    private LoopQueues interactionQueues = new LoopQueues();
    private Map<String, Object> lastResult;
    private Object sessionToolkit;

    public TaskLoopEventHandler(DeepAgent deepAgent) {
        this.deepAgent = deepAgent;
    }

    public Map<String, Object> getLastResult() {
        return lastResult == null ? null : new LinkedHashMap<>(lastResult);
    }

    public LoopQueues getInteractionQueues() {
        return interactionQueues;
    }

    public void setInteractionQueues(LoopQueues interactionQueues) {
        this.interactionQueues = interactionQueues == null ? new LoopQueues() : interactionQueues;
    }

    public void setSessionToolkit(Object sessionToolkit) {
        this.sessionToolkit = sessionToolkit;
    }

    public Object getSessionToolkit() {
        return sessionToolkit;
    }

    @Override
    public int prepareRound() {
        lastResult = null;
        return super.prepareRound();
    }

    @Override
    public Map<String, Object> waitCompletion(Double timeout) {
        Duration duration = timeout == null ? null : Duration.ofMillis(Math.max(0L, Math.round(timeout * 1000.0d)));
        Object output = interactionQueues.output().poll();
        if (output instanceof Map<?, ?> map) {
            lastResult = normalizeMap(map);
        } else if (duration != null && !duration.isZero()) {
            lastResult = Map.of("status", "waiting", "timeout_seconds", timeout);
        } else {
            lastResult = Map.of("status", "completed");
        }
        return getLastResult();
    }

    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        Map<String, Object> result = eventResult("input", inputs);
        interactionQueues.input().add(result);
        lastResult = result;
        return result;
    }

    @Override
    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        Map<String, Object> result = eventResult("task_interaction", inputs);
        interactionQueues.output().add(result);
        lastResult = result;
        return result;
    }

    @Override
    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        Map<String, Object> result = eventResult("task_completion", inputs);
        interactionQueues.output().add(result);
        lastResult = result;
        return result;
    }

    @Override
    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        Map<String, Object> result = eventResult("task_failed", inputs);
        interactionQueues.output().add(result);
        lastResult = result;
        return result;
    }

    @Override
    public Map<String, Object> handleFollowUp(EventHandlerInput inputs) {
        Map<String, Object> result = eventResult("follow_up", inputs);
        interactionQueues.input().add(result);
        lastResult = result;
        return result;
    }

    public Map<String, Object> completeSessionSpawn(
            String taskId,
            Map<String, Object> inputs,
            boolean error
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "session_spawn");
        result.put("task_id", taskId);
        result.put("error", error);
        result.put("inputs", inputs == null ? Map.of() : new LinkedHashMap<>(inputs));
        interactionQueues.output().add(result);
        lastResult = result;
        return result;
    }

    @Override
    public void onAbort() {
        lastResult = Map.of("status", "aborted");
    }

    private Map<String, Object> eventResult(String type, EventHandlerInput inputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("event", inputs == null ? null : inputs.getEvent());
        result.put("session_id", inputs == null || inputs.getSession() == null ? null : inputs.getSession().getSessionId());
        result.put("deep_agent", deepAgent == null ? null : deepAgent.getCard().getName());
        return result;
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
