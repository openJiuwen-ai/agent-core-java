/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code OnlineTrajectoryConverter} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/converter.py}.
 */
public class OnlineTrajectoryConverter {

    private final String tenantId;
    private final String modelId;
    private final boolean sessionDone;

    public OnlineTrajectoryConverter(String tenantId) {
        this(tenantId, null, false);
    }

    public OnlineTrajectoryConverter(String tenantId, String modelId, boolean sessionDone) {
        this.tenantId = tenantId;
        this.modelId = modelId;
        this.sessionDone = sessionDone;
    }

    public RailV1Batch convert(Trajectory trajectory) {
        return convert(trajectory, null, null);
    }

    public RailV1Batch convert(Trajectory trajectory, String tenantId, Boolean sessionDone) {
        String trajectoryId = trajectory != null && trajectory.getExecutionId() != null ? trajectory.getExecutionId() : "";
        String sessionId = trajectory != null && trajectory.getSessionId() != null ? trajectory.getSessionId() : "";
        List<PerTurnSample> samples = new ArrayList<>();
        String resolvedModelId = modelId != null ? modelId : "";

        List<TrajectoryStep> steps = trajectory != null && trajectory.getSteps() != null ? trajectory.getSteps() : List.of();
        for (int stepIndex = 0; stepIndex < steps.size(); stepIndex++) {
            TrajectoryStep step = steps.get(stepIndex);
            if (!"llm".equals(step.getKind()) || !(step.getDetail() instanceof LLMCallDetail detail)) {
                continue;
            }
            if (resolvedModelId.isEmpty() && detail.getModel() != null) {
                resolvedModelId = detail.getModel();
            }

            List<Map<String, Object>> messages = new ArrayList<>();
            for (Object message : detailMessages(detail)) {
                messages.add(TrajectoryConverterHelper.messageToDict(message));
            }
            Map<String, Object> response = TrajectoryConverterHelper.responseToDict(detail.getResponse());
            String responseText = TrajectoryConverterHelper.extractText(response.get("content"));
            if (responseText.strip().isEmpty() && response.isEmpty()) {
                continue;
            }

            Map<String, Object> detailMeta = new LinkedHashMap<>(detail.getMeta() != null ? detail.getMeta() : Map.of());
            Object providerResponseJson = detailMeta.get("provider_response_json");
            Object tokenSource = TrajectoryConverterHelper.truthy(providerResponseJson)
                    ? providerResponseJson
                    : detail.getResponse();
            Map<String, Object> stepMeta = step.getMeta() != null ? step.getMeta() : Map.of();
            List<Integer> responseTokens = firstIntList(
                    step.getCompletionTokenIds(),
                    LlmResponseUtils.extractTokenIds(tokenSource)
            );
            List<Integer> promptIds = firstIntList(
                    step.getPromptTokenIds(),
                    stepMeta.get("prompt_ids"),
                    LlmResponseUtils.extractPromptIds(tokenSource)
            );
            List<Double> logprobs = firstDoubleList(
                    TrajectoryConverterHelper.coerceLogprobs(step.getLogprobs()),
                    LlmResponseUtils.extractLogprobs(tokenSource)
            );

            Map<String, Object> mergedMeta = new LinkedHashMap<>(detailMeta);
            mergedMeta.putAll(stepMeta);
            Object renderFingerprint = TrajectoryConverterHelper.firstTruthy(
                    stepMeta.get("render_fingerprint"),
                    TrajectoryConverterHelper.fingerprintPayload(messages, detail.getTools())
            );

            samples.add(PerTurnSample.builder()
                    .trajectoryId(trajectoryId)
                    .stepIndex(stepIndex)
                    .sessionId(sessionId)
                    .modelId(detail.getModel() != null && !detail.getModel().isEmpty()
                            ? detail.getModel()
                            : resolvedModelId)
                    .messages(messages)
                    .response(response)
                    .responseText(responseText)
                    .responseTokens(responseTokens)
                    .logprobs(logprobs)
                    .promptIds(promptIds)
                    .renderFingerprint(asMap(renderFingerprint))
                    .tools(TrajectoryConverterHelper.jsonValue(detail.getTools()))
                    .meta(mergedMeta)
                    .build());
        }

        Map<String, Object> trajectoryMetaMap = trajectory != null && trajectory.getMeta() != null
                ? trajectory.getMeta()
                : Map.of();
        Object statusValue = TrajectoryConverterHelper.firstTruthy(trajectoryMetaMap.get("status"), "ok");
        Map<String, Object> extra = new LinkedHashMap<>(trajectoryMetaMap);
        extra.put("source", trajectory != null ? trajectory.getSource() : null);
        extra.put("case_id", trajectory != null ? trajectory.getCaseId() : null);
        extra.put("cost", trajectory != null ? trajectory.getCost() : null);

        TrajectoryMeta meta = TrajectoryMeta.builder()
                .trajectoryId(trajectoryId)
                .sessionId(sessionId)
                .status(String.valueOf(statusValue))
                .totalTurns(samples.size())
                .extra(extra)
                .build();

        return RailV1Batch.builder()
                .protocolVersion("rail-v1")
                .sessionId(sessionId)
                .tenantId(tenantId != null ? tenantId : this.tenantId)
                .trajectoryId(trajectoryId)
                .modelId(resolvedModelId)
                .samples(samples)
                .trajectoryMeta(meta)
                .prevFeedback(extractPrevFeedback(trajectory))
                .sessionDone(sessionDone == null ? this.sessionDone : sessionDone)
                .build();
    }

    public static Map<String, Object> extractPrevFeedback(Trajectory trajectory) {
        List<TrajectoryStep> steps = trajectory != null && trajectory.getSteps() != null ? trajectory.getSteps() : List.of();
        for (TrajectoryStep step : steps) {
            if (!"llm".equals(step.getKind()) || !(step.getDetail() instanceof LLMCallDetail detail)) {
                continue;
            }
            for (Object message : detailMessages(detail)) {
                Map<String, Object> msg = TrajectoryConverterHelper.messageToDict(message);
                if (!"user".equals(msg.get("role"))) {
                    continue;
                }
                String rawUserText = TrajectoryConverterHelper.extractText(msg.get("content")).strip();
                if (rawUserText.isEmpty()) {
                    return null;
                }
                Map<String, Object> feedback = new LinkedHashMap<>();
                feedback.put("raw_user_text", rawUserText);
                feedback.put("source", "first_user_msg_of_next_batch");
                return feedback;
            }
        }
        return null;
    }

    private static List<?> detailMessages(LLMCallDetail detail) {
        Object messages = detail.getMessages();
        return messages instanceof List<?> list ? list : List.of();
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, val) -> out.put(String.valueOf(key), val));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static List<Integer> firstIntList(Object... candidates) {
        for (Object candidate : candidates) {
            List<Integer> converted = toIntList(candidate);
            if (converted != null && !converted.isEmpty()) {
                return converted;
            }
        }
        return null;
    }

    private static List<Double> firstDoubleList(Object... candidates) {
        for (Object candidate : candidates) {
            List<Double> converted = toDoubleList(candidate);
            if (converted != null && !converted.isEmpty()) {
                return converted;
            }
        }
        return null;
    }

    private static List<Integer> toIntList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                out.add(number.intValue());
            } else if (item != null) {
                try {
                    out.add(Integer.parseInt(String.valueOf(item)));
                } catch (NumberFormatException ignored) {
                    // Mirrors Python's best-effort numeric coercion.
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static List<Double> toDoubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Double> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                out.add(number.doubleValue());
            } else if (item != null) {
                try {
                    out.add(Double.parseDouble(String.valueOf(item)));
                } catch (NumberFormatException ignored) {
                    // Mirrors Python's best-effort numeric coercion.
                }
            }
        }
        return out.isEmpty() ? null : out;
    }
}
