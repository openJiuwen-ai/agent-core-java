/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayMessageUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delayed-judge dispatch for pending rail-v1 samples.
 * <p>
 * Mirrors Python's {@code JudgeDispatcher} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/judge_dispatcher.py}.
 */
public class JudgeDispatcher {

    private final PendingJudgeStore pendingStore;
    private final SampleRecordingSink recordSample;
    private final JudgeScorer judgeScorer;

    public JudgeDispatcher(PendingJudgeStore pendingStore, SampleRecordingSink recordSample, JudgeScorer judgeScorer) {
        this.pendingStore = pendingStore;
        this.recordSample = recordSample;
        this.judgeScorer = judgeScorer;
    }

    public int onPrevFeedback(String sessionId, Map<String, Object> prevFeedback) {
        String feedback = feedbackText(prevFeedback);
        if (feedback.isBlank()) {
            return 0;
        }
        Map<String, Object> sample = pendingStore.popEarliest(sessionId);
        if (sample == null) {
            return 0;
        }
        recordSample.recordSample(finalizeSample(sample, feedback, "prev_feedback"));
        return 1;
    }

    public int onSessionDone(String sessionId) {
        List<Map<String, Object>> samples = pendingStore.popAll(sessionId);
        int count = 0;
        for (int index = 0; index < samples.size(); index++) {
            String tag = index == samples.size() - 1 ? "session_done" : "session_flush";
            recordSample.recordSample(finalizeSample(samples.get(index), "", tag));
            count += 1;
        }
        return count;
    }

    public Map<String, Object> finalizeSample(Map<String, Object> sample, String feedback, String tag) {
        Map<String, Object> finalized = new LinkedHashMap<>(sample);
        String sessionId = pythonStr(firstTruthy(finalized.get("session_id"), ""));
        int turnNum = intValue(firstTruthy(finalized.get("turn_num"), finalized.get("step_index"), 0), 0);
        Map<String, Object> trajectory = mapValue(finalized.get("trajectory"));
        String responseText = pythonStr(firstTruthy(trajectory.get("response_text"), finalized.get("response_text"), ""));
        List<Map<String, Object>> messages = listOfMaps(mapValue(finalized.get("request")).get("messages"));
        String instructionText = GatewayMessageUtils.extractLastUserInstruction(messages);
        if (instructionText.isEmpty()) {
            instructionText = feedback;
        }

        Map<String, Object> judge;
        if (judgeScorer != null) {
            try {
                judge = judgeScorer.score(responseText, instructionText, feedback, sessionId, turnNum).join();
            } catch (Exception exception) {
                String error = exception.getMessage() != null ? exception.getMessage() : exception.toString();
                judge = Map.of("score", 0.0, "votes", List.of("fail"), "details", Map.of(), "error", error);
            }
        } else {
            judge = Map.of("score", 0.0, "votes", List.of("skip"), "details", Map.of(), "error", tag);
        }

        finalized.put("judge", judge);
        finalized.put("judge_feedback", Map.of("tag", tag, "followup_user_feedback", feedback));
        finalized.putIfAbsent("sample_id", UUID.randomUUID().toString());
        return finalized;
    }

    private static String feedbackText(Map<String, Object> prevFeedback) {
        if (prevFeedback == null) {
            return "";
        }
        Object raw = prevFeedback.get("raw_user_text");
        if (raw == null) {
            raw = prevFeedback.get("text");
        }
        if (raw == null) {
            raw = prevFeedback.get("feedback");
        }
        return pythonStr(firstTruthy(raw, ""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static Object firstTruthy(Object... values) {
        if (values == null || values.length == 0) {
            return "";
        }
        for (Object value : values) {
            if (isPythonTruthy(value)) {
                return value;
            }
        }
        return values[values.length - 1];
    }

    private static boolean isPythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static String pythonStr(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        return value == null ? "None" : String.valueOf(value);
    }
}
