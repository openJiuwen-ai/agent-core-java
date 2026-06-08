// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.rl_trainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VERL converter for RL training.
 * <p>
 * Mirrors Python's {@code VerlDataProtoConverter} in
 * {@code openjiuwen/agent_evolving/agent_rl/rl_trainer/verl_converter.py}.
 */
public class VerlConverter {

    private final Options options;

    public VerlConverter() {
        this(new Options());
    }

    public VerlConverter(Options options) {
        this.options = options != null ? options : new Options();
    }

    /**
     * Convert samples to a Java DataProto representation.
     *
     * @param samples batch map or sample list
     * @return converted data proto payload
     */
    public static Object convertToVerl(Object samples) {
        VerlConverter converter = new VerlConverter();
        if (samples instanceof Map<?, ?> map) {
            return converter.convertBatch(castMap(map));
        }
        if (samples instanceof List<?> list) {
            return converter.convertSamples(castSampleList(list));
        }
        throw new IllegalArgumentException("samples must be a batch map or a sample list");
    }

    /**
     * Convert DataProto output to a plain map structure.
     *
     * @param verlOutput DataProto or already-converted payload
     * @return plain Java structure
     */
    public static Object convertFromVerl(Object verlOutput) {
        if (verlOutput instanceof DataProto dataProto) {
            return dataProto.toMap();
        }
        return verlOutput;
    }

    /**
     * Convert a Python-style batch payload.
     *
     * @param batch batch payload containing {@code samples}
     * @return converted data proto payload
     */
    public DataProto convertBatch(Map<String, Object> batch) {
        Object samples = batch != null ? batch.get("samples") : null;
        if (!(samples instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("batch.samples must be a non-empty list");
        }
        return convertSamples(castSampleList(list));
    }

    /**
     * Convert normalized trajectory samples to a DataProto-like structure.
     *
     * @param samples trajectory samples
     * @return converted data proto payload
     */
    public DataProto convertSamples(List<Map<String, Object>> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("samples must be non-empty");
        }

        List<Row> rows = new ArrayList<>();
        int droppedSamples = 0;
        int promptTruncated = 0;
        int responseTruncated = 0;
        for (int i = 0; i < samples.size(); i++) {
            Row row = normalizeSample(samples.get(i), i);
            if (row == null) {
                droppedSamples++;
                continue;
            }
            promptTruncated += row.promptTruncated ? 1 : 0;
            responseTruncated += row.responseTruncated ? 1 : 0;
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("all samples were dropped during normalization");
        }

        int promptMax = Math.max(rows.stream().mapToInt(row -> row.promptIds.size()).max().orElse(0), 1);
        int responseMax = Math.max(rows.stream().mapToInt(row -> row.responseIds.size()).max().orElse(0), 1);
        int inputMax = promptMax + responseMax;
        int batchSize = rows.size();

        int[][] inputIds = filledInt(batchSize, inputMax, options.padTokenId);
        int[][] attentionMask = new int[batchSize][inputMax];
        int[][] positionIds = new int[batchSize][inputMax];
        int[][] responseMask = new int[batchSize][responseMax];
        double[][] oldLogProbs = new double[batchSize][responseMax];
        double[][] tokenLevelScores = new double[batchSize][responseMax];
        int[][] prompts = filledInt(batchSize, promptMax, options.padTokenId);
        int[][] responses = filledInt(batchSize, responseMax, options.padTokenId);

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row row = rows.get(rowIdx);
            int promptLen = row.promptIds.size();
            int responseLen = row.responseIds.size();

            for (int i = 0; i < promptLen; i++) {
                int token = row.promptIds.get(i);
                prompts[rowIdx][i] = token;
                inputIds[rowIdx][i] = token;
                attentionMask[rowIdx][i] = 1;
            }
            for (int i = 0; i < responseLen; i++) {
                int token = row.responseIds.get(i);
                responses[rowIdx][i] = token;
                inputIds[rowIdx][promptMax + i] = token;
                attentionMask[rowIdx][promptMax + i] = 1;
                responseMask[rowIdx][i] = 1;
                oldLogProbs[rowIdx][i] = row.responseLogProbs.get(i);
                tokenLevelScores[rowIdx][i] = row.judgeScore;
            }

            int active = -1;
            for (int i = 0; i < inputMax; i++) {
                if (attentionMask[rowIdx][i] == 1) {
                    active++;
                }
                positionIds[rowIdx][i] = Math.max(active, 0);
            }
        }

        Map<String, Object> tensors = new LinkedHashMap<>();
        tensors.put("input_ids", inputIds);
        tensors.put("attention_mask", attentionMask);
        tensors.put("position_ids", positionIds);
        tensors.put("prompts", prompts);
        tensors.put("responses", responses);
        tensors.put("response_mask", responseMask);
        tensors.put("old_log_probs", oldLogProbs);
        tensors.put("token_level_scores", tokenLevelScores);

        Map<String, Object> nonTensors = new LinkedHashMap<>();
        nonTensors.put("sample_id", rows.stream().map(row -> row.sampleId).toList());
        nonTensors.put("data_id_list", rows.stream().map(row -> row.sampleId).toList());
        nonTensors.put("session_id", rows.stream().map(row -> row.sessionId).toList());
        nonTensors.put("turn_num", rows.stream().map(row -> row.turnNum).toList());
        nonTensors.put("created_at", rows.stream().map(row -> row.createdAt).toList());
        nonTensors.put("mode", rows.stream().map(row -> row.mode).toList());
        nonTensors.put("io_mode", rows.stream().map(row -> row.ioMode).toList());
        nonTensors.put("model", rows.stream().map(row -> row.model).toList());
        nonTensors.put("prompt_text", rows.stream().map(row -> row.promptText).toList());
        nonTensors.put("response_text", rows.stream().map(row -> row.responseText).toList());
        nonTensors.put("judge_score", rows.stream().map(row -> row.judgeScore).toList());

        Map<String, Object> metaInfo = new LinkedHashMap<>();
        metaInfo.put("source", "agent-online-rl");
        metaInfo.put("converter", "verl_dataproto");
        metaInfo.put("num_samples", batchSize);
        metaInfo.put("pad_token_id", options.padTokenId);
        metaInfo.put("dropped_samples", droppedSamples);
        metaInfo.put("prompt_truncated_samples", promptTruncated);
        metaInfo.put("response_truncated_samples", responseTruncated);
        metaInfo.put("max_prompt_length", options.maxPromptLength);
        metaInfo.put("max_response_length", options.maxResponseLength);

        return new DataProto(tensors, nonTensors, metaInfo);
    }

    private Row normalizeSample(Map<String, Object> sample, int idx) {
        Map<String, Object> trajectory = asMap(sample != null ? sample.get("trajectory") : null);

        List<Integer> promptIds = coerceIntList(trajectory.get("prompt_ids"));
        List<Integer> responseIds = coerceIntList(trajectory.get("response_ids"));
        List<Integer> inputIds = coerceIntList(trajectory.get("input_ids"));

        if (inputIds.isEmpty()) {
            inputIds = concat(promptIds, responseIds);
        }

        boolean hasResponse = !responseIds.isEmpty();
        boolean hasPrompt = !promptIds.isEmpty();
        boolean canSplitInputByPrompt = !hasResponse && !inputIds.isEmpty() && hasPrompt
                && inputIds.size() >= promptIds.size();
        boolean canSplitInputByResponse = !hasPrompt && !inputIds.isEmpty() && hasResponse
                && inputIds.size() >= responseIds.size();

        if (canSplitInputByPrompt) {
            responseIds = new ArrayList<>(inputIds.subList(promptIds.size(), inputIds.size()));
        }
        if (canSplitInputByResponse) {
            promptIds = new ArrayList<>(inputIds.subList(0, inputIds.size() - responseIds.size()));
        }
        if (inputIds.isEmpty()) {
            throw new IllegalArgumentException("sample[" + idx + "] has no valid token ids");
        }

        boolean promptTruncated = false;
        boolean responseTruncated = false;
        if (options.maxPromptLength != null && promptIds.size() > options.maxPromptLength) {
            if (options.filterOverlongPrompts && !"truncate".equals(options.truncation)) {
                return null;
            }
            promptIds = new ArrayList<>(
                    promptIds.subList(promptIds.size() - options.maxPromptLength, promptIds.size())
            );
            promptTruncated = true;
        }
        if (options.maxResponseLength != null && responseIds.size() > options.maxResponseLength) {
            responseIds = new ArrayList<>(responseIds.subList(0, options.maxResponseLength));
            responseTruncated = true;
        }
        inputIds = concat(promptIds, responseIds);

        int responseLen = responseIds.size();
        List<Double> responseLogProbs = coerceDoubleList(trajectory.get("response_logprobs"));
        if (responseLogProbs.size() > responseLen) {
            responseLogProbs = new ArrayList<>(responseLogProbs.subList(0, responseLen));
        }
        while (responseLogProbs.size() < responseLen) {
            responseLogProbs.add(0.0);
        }

        double judgeScore = options.defaultScore;
        Map<String, Object> judge = asMap(sample != null ? sample.get("judge") : null);
        Object score = judge.get("score");
        if (score instanceof Number number) {
            judgeScore = number.doubleValue();
        }

        return new Row(
                stringValue(sample, "sample_id", ""),
                stringValue(sample, "session_id", "default"),
                intValue(sample != null ? sample.get("turn_num") : null),
                stringValue(sample, "created_at", ""),
                stringValue(sample, "mode", ""),
                stringValue(sample, "io_mode", ""),
                stringValue(sample, "model", ""),
                String.valueOf(trajectory.getOrDefault("prompt_text", "")),
                String.valueOf(trajectory.getOrDefault("response_text", "")),
                inputIds,
                promptIds,
                responseIds,
                responseLogProbs,
                judgeScore,
                promptTruncated,
                responseTruncated
        );
    }

    private static int[][] filledInt(int rows, int cols, int value) {
        int[][] result = new int[rows][cols];
        for (int[] row : result) {
            Arrays.fill(row, value);
        }
        return result;
    }

    private static List<Integer> concat(List<Integer> left, List<Integer> right) {
        List<Integer> out = new ArrayList<>(left);
        out.addAll(right);
        return out;
    }

    private static List<Integer> coerceIntList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Integer> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                out.add(number.intValue());
            }
        }
        return out;
    }

    private static List<Double> coerceDoubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Double> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                out.add(number.doubleValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castSampleList(List<?> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("sample entries must be maps");
            }
            out.add((Map<String, Object>) map);
        }
        return out;
    }

    private static String stringValue(Map<String, Object> map, String key, String fallback) {
        Object value = map != null ? map.get(key) : null;
        return String.valueOf(value != null ? value : fallback);
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * Converter options matching Python's constructor arguments.
     */
    public static final class Options {
        private int padTokenId = 0;
        private double defaultScore = 0.0;
        private Integer maxPromptLength;
        private Integer maxResponseLength;
        private String truncation = "truncate";
        private boolean filterOverlongPrompts;

        public int getPadTokenId() {
            return padTokenId;
        }

        public Options setPadTokenId(int padTokenId) {
            this.padTokenId = padTokenId;
            return this;
        }

        public double getDefaultScore() {
            return defaultScore;
        }

        public Options setDefaultScore(double defaultScore) {
            this.defaultScore = defaultScore;
            return this;
        }

        public Integer getMaxPromptLength() {
            return maxPromptLength;
        }

        public Options setMaxPromptLength(Integer maxPromptLength) {
            this.maxPromptLength = normalizeOptionalLimit(maxPromptLength);
            return this;
        }

        public Integer getMaxResponseLength() {
            return maxResponseLength;
        }

        public Options setMaxResponseLength(Integer maxResponseLength) {
            this.maxResponseLength = normalizeOptionalLimit(maxResponseLength);
            return this;
        }

        public String getTruncation() {
            return truncation;
        }

        public Options setTruncation(String truncation) {
            this.truncation = truncation == null || truncation.isEmpty() ? "truncate" : truncation;
            return this;
        }

        public boolean isFilterOverlongPrompts() {
            return filterOverlongPrompts;
        }

        public Options setFilterOverlongPrompts(boolean filterOverlongPrompts) {
            this.filterOverlongPrompts = filterOverlongPrompts;
            return this;
        }

        private static Integer normalizeOptionalLimit(Integer value) {
            if (value == null || value <= 0) {
                return null;
            }
            return value;
        }
    }

    /**
     * Java representation of the DataProto surface produced by the converter.
     */
    public record DataProto(
            Map<String, Object> tensors,
            Map<String, Object> nonTensors,
            Map<String, Object> metaInfo
    ) {
        public int length() {
            Object sampleIds = nonTensors.get("sample_id");
            return sampleIds instanceof List<?> list ? list.size() : 0;
        }

        public Map<String, Object> batch() {
            return tensors;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tensors", tensors);
            out.put("non_tensors", nonTensors);
            out.put("meta_info", metaInfo);
            return out;
        }
    }

    private record Row(
            String sampleId,
            String sessionId,
            int turnNum,
            String createdAt,
            String mode,
            String ioMode,
            String model,
            String promptText,
            String responseText,
            List<Integer> inputIds,
            List<Integer> promptIds,
            List<Integer> responseIds,
            List<Double> responseLogProbs,
            double judgeScore,
            boolean promptTruncated,
            boolean responseTruncated
    ) {
    }
}
