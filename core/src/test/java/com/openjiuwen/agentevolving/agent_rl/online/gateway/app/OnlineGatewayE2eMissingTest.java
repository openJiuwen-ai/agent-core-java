/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory.GatewayTrajectoryRuntime;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.agentevolving.agent_rl.online.rail.OnlineTrajectoryConverter;
import com.openjiuwen.agentevolving.agent_rl.online.rail.RailV1Batch;
import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code test_online_gateway_proxy_and_rail_upload_e2e} in
 * {@code tests/system_tests/agent_evolving/agent_rl/online/test_online_gateway_e2e.py}.
 */
public class OnlineGatewayE2eMissingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @TempDir
    Path tempDir;

    @Test
    void onlineGatewayProxyAndRailUploadE2e() throws Exception {
        InMemoryRedis redis = new InMemoryRedis();
        CapturingUpstreamGatewayClient upstreamClient = new CapturingUpstreamGatewayClient();
        GatewayConfig config = gatewayConfig(tempDir.resolve("records"));
        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, redis);
        GatewayServer server = new GatewayServer(
                config,
                new Forwarder(upstreamClient, config.getModelId()),
                upstreamClient,
                runtime,
                null,
                null);

        GatewayHttpException missingUser = assertThrows(
                GatewayHttpException.class,
                () -> server.chatCompletions(
                        Map.of("Authorization", "Bearer gw-token"),
                        jsonBytes(Map.of("messages", List.of(Map.of("role", "user", "content", "ping")))),
                        "Bearer gw-token"));
        assertThat(missingUser.getStatusCode()).isEqualTo(400);
        assertThat(missingUser.getDetail()).contains("x-user-id");
        assertThat(upstreamClient.forwardCalls).isEmpty();

        GatewayServer.ChatCompletionResult chatResponse = server.chatCompletions(
                Map.of("Authorization", "Bearer gw-token", "x-user-id", "st-user"),
                jsonBytes(Map.of(
                        "messages", List.of(Map.of("role", "user", "content", "ping")),
                        "stream", true)),
                "Bearer gw-token");

        assertThat(chatResponse.stream()).isTrue();
        assertThat(chatResponse.eventStream()).anyMatch(event -> event.contains("data: [DONE]"));
        assertThat(upstreamClient.forwardCalls).hasSize(1);
        ForwardCall forwardCall = upstreamClient.forwardCalls.get(0);
        assertThat(forwardCall.jsonBody()).containsEntry("stream", false);
        assertThat(forwardCall.jsonBody()).containsEntry("model", "st-model");
        assertThat(forwardCall.jsonBody()).containsEntry("logprobs", true);
        assertThat(forwardCall.jsonBody()).containsEntry("top_logprobs", 1);
        assertThat(forwardCall.jsonBody())
                .containsEntry("messages", List.of(Map.of("role", "user", "content", "ping")));
        assertThat(forwardCall.headers()).containsEntry("x-user-id", "st-user");

        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-st")
                .sessionId("session-st")
                .source("rl_online")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("llm")
                        .detail(LLMCallDetail.builder()
                                .model("st-model")
                                .messages(List.of(Map.of("role", "user", "content", "ping")))
                                .response(Map.of("role", "assistant", "content", "pong", "finish_reason", "stop"))
                                .build())
                        .promptTokenIds(List.of(101, 102))
                        .completionTokenIds(List.of(201, 202))
                        .logprobs(List.of(-0.1d, -0.2d))
                        .build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("st-user").convert(trajectory, "st-user", true);
        Map<String, Object> ingestResult = runtime.getRailIngestor().ingestRailBatch(batch.toDict());
        assertThat(ingestResult).containsEntry("accepted", 1);
        assertThat(ingestResult).containsEntry("session_flushed", 1);

        Map<String, Object> stats = server.gatewayStats("Bearer gw-token");
        assertThat(stats).containsEntry("trajectory_store_pending", 1);

        Map<String, Object> row = redis.hashes.get("rl:traj:traj-st:0");
        assertThat(row).isNotNull();
        assertThat(row).containsEntry("user_id", "st-user");
        Map<String, Object> storedSample = OBJECT_MAPPER.readValue(String.valueOf(row.get("sample_json")), MAP_TYPE);
        assertThat(storedSample).containsEntry("user_id", "st-user");
        assertThat(mapValue(storedSample.get("trajectory"))).containsEntry("prompt_ids", List.of(101, 102));
        assertThat(mapValue(storedSample.get("trajectory"))).containsEntry("response_ids", List.of(201, 202));
        assertThat(mapValue(storedSample.get("judge_feedback"))).containsEntry("tag", "session_done");
        assertThat(Files.exists(tempDir.resolve("records").resolve("samples.jsonl"))).isTrue();
    }

    private static GatewayConfig gatewayConfig(Path recordDir) {
        GatewayConfig config = new GatewayConfig();
        config.setPort(18080);
        config.setLlmUrl("http://llm.local");
        config.setJudgeUrl("");
        config.setModelId("st-model");
        config.setGatewayApiKey("gw-token");
        config.setLlmApiKey("");
        config.setRecordDir(recordDir.toString());
        config.setDumpTokenIds(true);
        config.setSingleUserDefault(false);
        return config;
    }

    private static byte[] jsonBytes(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize test payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static final class CapturingUpstreamGatewayClient implements UpstreamGatewayClient {
        private final List<ForwardCall> forwardCalls = new ArrayList<>();

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            forwardCalls.add(new ForwardCall(new LinkedHashMap<>(jsonBody), new LinkedHashMap<>(headers)));
            return new GatewayHttpResponse(200, upstreamResponseJson());
        }

        @Override
        public GatewayHttpResponse request(String method,
                                           String url,
                                           Map<String, Object> params,
                                           Map<String, String> headers,
                                           byte[] content) {
            throw new UnsupportedOperationException("proxyOther is outside this Python parity test");
        }

        private static String upstreamResponseJson() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", "chatcmpl-st");
            response.put("object", "chat.completion");
            response.put("created", 123);
            response.put("model", "st-model");
            response.put("prompt_token_ids", List.of(101, 102));
            response.put("choices", List.of(Map.of(
                    "index", 0,
                    "finish_reason", "stop",
                    "message", Map.of("role", "assistant", "content", "pong"),
                    "token_ids", List.of(201, 202),
                    "logprobs", Map.of("content", List.of(Map.of("logprob", -0.1d), Map.of("logprob", -0.2d))))));
            response.put("usage", Map.of("prompt_tokens", 2, "completion_tokens", 2, "total_tokens", 4));
            try {
                return OBJECT_MAPPER.writeValueAsString(response);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to serialize upstream response", exception);
            }
        }
    }

    private record ForwardCall(Map<String, Object> jsonBody, Map<String, String> headers) {
    }

    public static final class InMemoryRedis {
        private final Map<String, String> kv = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        private final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();
        private final Map<String, Set<Object>> sets = new LinkedHashMap<>();

        public FetchAndMarkScript registerFetchAndMarkScript(String luaSource) {
            return new FetchAndMarkScript(this);
        }

        public List<Object> hmget(String key, List<String> fields) {
            Map<String, Object> row = hashes.getOrDefault(key, Map.of());
            List<Object> result = new ArrayList<>(fields.size());
            for (String field : fields) {
                result.add(row.get(field));
            }
            return result;
        }

        public Object hget(String key, String field) {
            return hashes.getOrDefault(key, Map.of()).get(field);
        }

        public long hset(String key, Map<String, Object> mapping) {
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        public long zadd(String key, Map<?, Double> mapping) {
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        public long zcard(String key) {
            return zsets.getOrDefault(key, Map.of()).size();
        }

        public long zrem(String key, Object members) {
            Map<Object, Double> bucket = zsets.get(key);
            if (bucket == null) {
                return 0L;
            }
            long removed = 0L;
            Object[] normalizedMembers = members instanceof Object[] array ? array : new Object[]{members};
            for (Object member : normalizedMembers) {
                removed += bucket.remove(member) == null ? 0L : 1L;
            }
            return removed;
        }

        public long sadd(String key, Object... members) {
            Set<Object> bucket = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            long added = 0L;
            for (Object member : members) {
                added += bucket.add(member) ? 1L : 0L;
            }
            return added;
        }

        public long srem(String key, Object... members) {
            Set<Object> bucket = sets.get(key);
            if (bucket == null) {
                return 0L;
            }
            long removed = 0L;
            for (Object member : members) {
                removed += bucket.remove(member) ? 1L : 0L;
            }
            return removed;
        }

        public Set<Object> smembers(String key) {
            return new LinkedHashSet<>(sets.getOrDefault(key, Set.of()));
        }

        public long set(String key, String value, int ttlSeconds) {
            kv.put(key, value);
            return 1L;
        }

        public long expire(String key, int ttlSeconds) {
            return 1L;
        }

        public List<Object> zrange(String key, int start, int end) {
            List<Object> members = zsets.getOrDefault(key, Map.of()).entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .toList();
            if (members.isEmpty() || start >= members.size()) {
                return List.of();
            }
            int resolvedEnd = end == -1 ? members.size() - 1 : Math.min(end, members.size() - 1);
            if (resolvedEnd < start) {
                return List.of();
            }
            return new ArrayList<>(members.subList(start, resolvedEnd + 1));
        }

        public List<Object> mget(List<String> keys) {
            List<Object> result = new ArrayList<>(keys.size());
            for (String key : keys) {
                result.add(kv.get(key));
            }
            return result;
        }

        public Object get(String key) {
            return kv.get(key);
        }

        public long delete(String key) {
            return kv.remove(key) == null ? 0L : 1L;
        }

        public Pipeline pipeline() {
            return new Pipeline(this);
        }
    }

    public static final class FetchAndMarkScript {
        private final InMemoryRedis redis;

        private FetchAndMarkScript(InMemoryRedis redis) {
            this.redis = redis;
        }

        public List<Object> execute(List<Object> keys, List<Object> args) {
            String pendingKey = String.valueOf(keys.get(0));
            String trainingKey = String.valueOf(keys.get(1));
            int limit = Math.max(1, ((Number) args.get(0)).intValue());
            double nowScore = ((Number) args.get(1)).doubleValue();
            String newStatus = String.valueOf(args.get(2));
            String trajectoryPrefix = String.valueOf(args.get(3));
            List<Object> ids = redis.zrange(pendingKey, 0, limit - 1);
            for (Object sampleId : ids) {
                redis.zrem(pendingKey, sampleId);
                redis.zadd(trainingKey, Map.of(sampleId, nowScore));
                redis.hset(trajectoryPrefix + sampleId, Map.of("status", newStatus));
            }
            return ids;
        }
    }

    public static final class Pipeline {
        private final InMemoryRedis redis;
        private final List<Supplier<Object>> operations = new ArrayList<>();

        private Pipeline(InMemoryRedis redis) {
            this.redis = redis;
        }

        public Pipeline delete(String key) {
            operations.add(() -> redis.delete(key));
            return this;
        }

        public Pipeline zrem(String key, Object members) {
            operations.add(() -> redis.zrem(key, members));
            return this;
        }

        public Pipeline hset(String key, Map<String, Object> mapping) {
            operations.add(() -> redis.hset(key, mapping));
            return this;
        }

        public Pipeline zadd(String key, Map<?, Double> mapping) {
            operations.add(() -> redis.zadd(key, mapping));
            return this;
        }

        public Pipeline sadd(String key, Object... members) {
            operations.add(() -> redis.sadd(key, members));
            return this;
        }

        public Pipeline zcard(String key) {
            operations.add(() -> redis.zcard(key));
            return this;
        }

        public Pipeline hget(String key, String field) {
            operations.add(() -> redis.hget(key, field));
            return this;
        }

        public Pipeline hmget(String key, List<String> fields) {
            operations.add(() -> redis.hmget(key, fields));
            return this;
        }

        public List<Object> execute() {
            List<Object> result = new ArrayList<>(operations.size());
            for (Supplier<Object> operation : operations) {
                result.add(operation.get());
            }
            operations.clear();
            return result;
        }
    }
}
