/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for online gateway trajectory exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/__init__.py}.
 * </p>
 */
public final class GatewayTrajectoryPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/__init__.py";
    public static final String DESCRIPTION = "Trajectory ingestion and storage helpers for the online-RL gateway.";
    public static final Class<GatewayTrajectoryRuntime> GATEWAY_TRAJECTORY_RUNTIME = GatewayTrajectoryRuntime.class;
    public static final Class<SampleRecorder> SAMPLE_RECORDER = SampleRecorder.class;
    public static final Class<JudgeDispatcher> JUDGE_DISPATCHER = JudgeDispatcher.class;
    public static final Class<PendingJudgeStore> PENDING_JUDGE_STORE = PendingJudgeStore.class;
    public static final Class<RailBatchIngestor> RAIL_BATCH_INGESTOR = RailBatchIngestor.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "GatewayTrajectoryRuntime",
            "SampleRecorder",
            "JudgeDispatcher",
            "PendingJudgeStore",
            "RailBatchIngestor",
            "build_sample",
            "coerce_logprobs"
    );

    private GatewayTrajectoryPackage() {
    }

    public static Map<String, Object> buildSample(String userId,
                                                  String sessionId,
                                                  int turnNum,
                                                  String mode,
                                                  String ioMode,
                                                  Object model,
                                                  List<Map<String, Object>> messages,
                                                  Object tools,
                                                  Map<String, Object> assistantMessage,
                                                  Map<String, Object> usage,
                                                  String finishReason,
                                                  String promptText,
                                                  List<Integer> promptIds,
                                                  String responseText,
                                                  List<Integer> responseIds,
                                                  List<Double> responseLogprobs,
                                                  List<Map<String, Object>> toolCalls,
                                                  Map<String, Object> requestExtras,
                                                  String sampleId,
                                                  String createdAt,
                                                  Map<String, Object> extraFields) {
        return SamplePayloads.buildSample(
                userId,
                sessionId,
                turnNum,
                mode,
                ioMode,
                model,
                messages,
                tools,
                assistantMessage,
                usage,
                finishReason,
                promptText,
                promptIds,
                responseText,
                responseIds,
                responseLogprobs,
                toolCalls,
                requestExtras,
                sampleId,
                createdAt,
                extraFields
        );
    }

    public static List<Double> coerceLogprobs(Object values, int expectedLength) {
        return SamplePayloads.coerceLogprobs(values, expectedLength);
    }
}
