/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed payload for DeepAgent task-iteration callbacks.
 *
 * <p>Mirrors Python's {@code TaskIterationInputs} in
 * {@code openjiuwen.core.single_agent.rail.base}.
 */
public class TaskIterationInputs implements EventInputs {

    private int iteration;
    private Object loopEvent;
    private String conversationId;
    private Map<String, Object> result;
    private String query;
    private boolean followUp;

    public static TaskIterationInputs from(Map<String, Object> data) {
        Map<String, Object> source = data != null ? data : Map.of();
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setIteration(intValue(source.get("iteration")));
        inputs.setLoopEvent(source.getOrDefault("loop_event", source.get("event")));
        inputs.setConversationId(stringValue(source.get("conversation_id")));
        inputs.setQuery(stringValue(source.get("query")));
        inputs.setFollowUp(booleanValue(source.get("is_follow_up")));
        Object rawResult = source.get("result");
        if (rawResult instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            inputs.setResult(copy);
        }
        return inputs;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public Object getLoopEvent() {
        return loopEvent;
    }

    public void setLoopEvent(Object loopEvent) {
        this.loopEvent = loopEvent;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isFollowUp() {
        return followUp;
    }

    public void setFollowUp(boolean followUp) {
        this.followUp = followUp;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
