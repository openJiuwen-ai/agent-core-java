/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.JudgeScorer;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.JudgeDispatcher;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.PendingJudgeStore;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.PendingJudgeStoreBackend;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.SamplePayloads;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.SampleRecordingSink;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code test_processor_components} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/gateway/test_processor_components.py}.
 */
class GatewayProcessorComponentsMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void buildSampleBuildsSharedMasks() {
        Map<String, Object> sample = SamplePayloads.buildSample(
                "user-1",
                "s1",
                1,
                "judge_output",
                "string",
                "m1",
                List.of(Map.of("role", "user", "content", "hi")),
                List.of(Map.of("type", "function")),
                Map.of("role", "assistant", "content", "pong"),
                Map.of("total_tokens", 5),
                "stop",
                "prompt",
                List.of(1, 2, 3),
                "pong",
                List.of(4, 5),
                List.of(-0.1, -0.2),
                List.of(),
                Map.of("temperature", 0.2),
                "sample-1",
                null,
                Map.of("rail_meta", Map.of("protocol_version", "rail-v1"))
        );

        Map<String, Object> trajectory = (Map<String, Object>) sample.get("trajectory");
        assertEquals(List.of(1, 2, 3, 4, 5), trajectory.get("input_ids"));
        assertEquals(List.of(1, 1, 1, 1, 1), trajectory.get("attention_mask"));
        assertEquals(List.of(0, 0, 0, 1, 1), trajectory.get("response_mask"));
        assertEquals(0.2, ((Map<String, Object>) sample.get("request")).get("temperature"));
        assertEquals("rail-v1", ((Map<String, Object>) sample.get("rail_meta")).get("protocol_version"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void processorChatCompletionProxiesWithoutTurnOrSampleWork() throws Exception {
        CapturingForwarder forwarder = new CapturingForwarder(Map.of(
                "choices",
                List.of(Map.of("message", Map.of("role", "assistant", "content", "pong")))
        ));
        FakeTrajectoryGateway trajectoryGateway = new FakeTrajectoryGateway();
        GatewayServer server = new GatewayServer(
                baseConfig(),
                forwarder,
                new NoOpUpstreamClient(),
                trajectoryGateway,
                null,
                null
        );

        byte[] requestBody = OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "messages",
                List.of(Map.of("role", "user", "content", "hello"))
        ));
        GatewayServer.ChatCompletionResult result = server.chatCompletions(
                Map.of("x-request-id", "trace-9", "x-user-id", "user-9"),
                requestBody,
                ""
        );

        Map<String, Object> firstChoice = (Map<String, Object>) ((List<?>) result.jsonBody()
                .get("choices")).get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        assertEquals("pong", message.get("content"));
        assertEquals(1, forwarder.forwardCalls.size());
        assertEquals("trace-9", forwarder.lastHeaders.get("x-request-id"));
        assertEquals("user-9", forwarder.lastHeaders.get("x-user-id"));
        assertEquals(0, trajectoryGateway.ingestCalls);
        assertEquals(0, trajectoryGateway.statsCalls);
    }

    @SuppressWarnings("unchecked")
    @Test
    void judgeDispatcherScoresSessionDoneSampleWithoutFollowupFeedback() {
        PendingJudgeStore store = new PendingJudgeStore(new InMemoryPendingJudgeBackend());
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", "sample-1");
        sample.put("user_id", "user-1");
        sample.put("session_id", "s1");
        sample.put("turn_num", 1);
        sample.put("request", Map.of("messages", List.of(Map.of("role", "user", "content", "hello"))));
        sample.put("trajectory", Map.of("response_text", "pong"));
        store.put(sample);

        RecordingSampleSink recorder = new RecordingSampleSink();
        RecordingJudgeScorer scorer = new RecordingJudgeScorer(Map.of(
                "score",
                0.25,
                "votes",
                List.of(6.25),
                "details",
                Map.of("overall", 6.25)
        ));
        JudgeDispatcher dispatcher = new JudgeDispatcher(store, recorder, scorer);

        int count = dispatcher.onSessionDone("s1");

        assertEquals(1, count);
        assertEquals(1, scorer.calls.size());
        assertEquals("hello", scorer.calls.get(0).get("instruction_text"));
        assertEquals("", scorer.calls.get(0).get("followup_user_feedback"));
        Map<String, Object> recorded = recorder.samples.get(0);
        assertEquals(0.25, ((Map<String, Object>) recorded.get("judge")).get("score"));
        assertEquals("session_done", ((Map<String, Object>) recorded.get("judge_feedback")).get("tag"));
    }

    private static GatewayConfig baseConfig() {
        GatewayConfig config = new GatewayConfig();
        config.setGatewayApiKey("");
        config.setLlmApiKey("");
        config.setModelId("demo-model");
        config.setLlmUrl("http://mock.llm/");
        config.setSingleUserDefault(true);
        return config;
    }

    private static final class CapturingForwarder extends Forwarder {
        private final Map<String, Object> response;
        private final List<Map<String, Object>> forwardCalls = new ArrayList<>();
        private Map<String, String> lastHeaders = Map.of();

        private CapturingForwarder(Map<String, Object> response) {
            super(new NoOpUpstreamClient(), "demo-model");
            this.response = response;
        }

        @Override
        public Map<String, Object> forward(Map<String, Object> body, Map<String, String> headers) {
            this.forwardCalls.add(Map.of("body", new LinkedHashMap<>(body), "headers", new LinkedHashMap<>(headers)));
            this.lastHeaders = new LinkedHashMap<>(headers);
            return response;
        }
    }

    private static final class FakeTrajectoryGateway implements GatewayServer.TrajectoryGateway {
        private int ingestCalls;
        private int statsCalls;

        @Override
        public Map<String, Object> snapshotStats() {
            statsCalls += 1;
            return Map.of("total_samples", 0, "trajectory_store_total", 0, "trajectory_store_pending", 0);
        }

        @Override
        public Map<String, Object> ingestRailBatch(Map<String, Object> payload) {
            ingestCalls += 1;
            return Map.of("accepted", 0, "rejected", 0);
        }
    }

    private static class NoOpUpstreamClient implements UpstreamGatewayClient {
        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            return new GatewayHttpResponse(200, "{}");
        }

        @Override
        public GatewayHttpResponse request(
                String method,
                String url,
                Map<String, Object> params,
                Map<String, String> headers,
                byte[] content
        ) {
            return new GatewayHttpResponse(200, "{}");
        }
    }

    private static final class RecordingSampleSink implements SampleRecordingSink {
        private final List<Map<String, Object>> samples = new ArrayList<>();

        @Override
        public void recordSample(Map<String, Object> sample) {
            samples.add(new LinkedHashMap<>(sample));
        }
    }

    private static final class RecordingJudgeScorer implements JudgeScorer {
        private final Map<String, Object> result;
        private final List<Map<String, Object>> calls = new ArrayList<>();

        private RecordingJudgeScorer(Map<String, Object> result) {
            this.result = result;
        }

        @Override
        public Object score(
                String responseText,
                String instructionText,
                String followupUserFeedback,
                String sessionId,
                int turnNum
        ) {
            calls.add(Map.of(
                    "response_text",
                    responseText,
                    "instruction_text",
                    instructionText,
                    "followup_user_feedback",
                    followupUserFeedback,
                    "session_id",
                    sessionId,
                    "turn_num",
                    turnNum
            ));
            return result;
        }
    }

    private static final class InMemoryPendingJudgeBackend implements PendingJudgeStoreBackend {
        private final Map<String, byte[]> kv = new LinkedHashMap<>();
        private final Map<String, LinkedHashMap<String, Double>> zsets = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSeconds) {
            kv.put(key, value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            LinkedHashMap<String, Double> set = zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            set.putAll(mapping);
            return mapping.size();
        }

        @Override
        public long expire(String key, int ttlSeconds) {
            return 1;
        }

        @Override
        public List<Object> zrange(String key, int start, int end) {
            return zsets.getOrDefault(key, new LinkedHashMap<>()).entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .map(value -> (Object) value)
                    .toList();
        }

        @Override
        public List<Object> mget(List<String> keys) {
            return keys.stream().map(kv::get).map(value -> (Object) value).toList();
        }

        @Override
        public Object get(String key) {
            return kv.get(key);
        }

        @Override
        public PendingJudgeStorePipeline pipeline() {
            return new PendingJudgeStorePipeline() {
                @Override
                public PendingJudgeStorePipeline delete(String key) {
                    kv.remove(key);
                    return this;
                }

                @Override
                public PendingJudgeStorePipeline zrem(String key, Object member) {
                    zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).remove(String.valueOf(member));
                    return this;
                }

                @Override
                public List<Object> execute() {
                    return List.of();
                }
            };
        }
    }
}
