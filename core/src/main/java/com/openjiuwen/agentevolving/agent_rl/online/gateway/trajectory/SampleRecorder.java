/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample recording helpers for the gateway.
 * <p>
 * Mirrors Python's {@code SampleRecorder} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/sample_recorder.py}.
 */
public class SampleRecorder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path sampleFile;
    private final boolean dumpTokenIds;
    private int totalSamples;

    public SampleRecorder(String sampleFile, boolean dumpTokenIds) {
        this(Path.of(sampleFile), dumpTokenIds);
    }

    public SampleRecorder(Path sampleFile, boolean dumpTokenIds) {
        this.sampleFile = sampleFile;
        this.dumpTokenIds = dumpTokenIds;
    }

    public void recordSample(Map<String, Object> sample) {
        totalSamples += 1;
        appendJsonl(sampleFile, dumpTokenIds ? sample : sampleForLog(sample));
    }

    public Map<String, Integer> snapshotStats() {
        return Map.of("total_samples", totalSamples);
    }

    public static Map<String, Object> sampleForLog(Map<String, Object> sample) {
        if (sample == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(sample);
        Object trajectoryRaw = out.get("trajectory");
        if (!(trajectoryRaw instanceof Map<?, ?> rawMap)) {
            return out;
        }
        Map<String, Object> trajectoryOut = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> trajectoryOut.put(String.valueOf(key), value));
        for (String key : List.of("input_ids", "prompt_ids", "response_ids", "response_logprobs")) {
            Object value = trajectoryOut.remove(key);
            if (value instanceof List<?> list) {
                trajectoryOut.put(key + "_len", list.size());
            }
        }
        out.put("trajectory", trajectoryOut);
        return out;
    }

    private static void appendJsonl(Path path, Map<String, Object> payload) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    path,
                    OBJECT_MAPPER.writeValueAsString(payload) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append sample jsonl", exception);
        }
    }
}
