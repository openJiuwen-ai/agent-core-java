/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.app.GatewayHttpHelpers;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.agent_evolving.agent_rl.online.inference.InferenceNotifier;
import com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeScorer;
import com.openjiuwen.agent_evolving.agent_rl.online.rail.OnlineTrajectoryConverter;
import com.openjiuwen.agent_evolving.agent_rl.online.rail.RailV1Batch;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's gateway support tests in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/test_gateway_support.py}.
 */
class GatewaySupportPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void testJudgeScorerParseScoresHandlesMultipleCodeBlocksAndAliases() {
        String content = """
                前置说明
                ```text
                ignored
                ```
                ```json
                {"task_completion_score": 8, "response_quality": 7, "tool_usage_score": 9, "coherence": 6}
                ```
                """;

        Map<String, Object> scores = JudgeScorer.parseScores(content);

        assertThat(scores.get("task_completion_score")).isEqualTo(8);
        assertThat(((Number) scores.get("overall")).doubleValue()).isEqualTo(7.5d);
    }

    @Test
    void testInferenceNotifierUsesAsyncClient() throws Exception {
        RecordingHttpClient client = new RecordingHttpClient(200, "ok");
        InferenceNotifier notifier = new InferenceNotifier("http://vllm.local", 120.0, client);

        notifier.notifyUpdate("user1", "/tmp/lora").toCompletableFuture().join();

        assertThat(client.calls).hasSize(1);
        HttpCall call = client.calls.get(0);
        assertThat(call.uri()).isEqualTo(URI.create("http://vllm.local/v1/load_lora_adapter"));
        assertThat(call.body()).contains("\"lora_name\":\"user1\"");
        assertThat(call.body()).contains("\"lora_path\":\"/tmp/lora\"");
        assertThat(call.body()).contains("\"load_inplace\":true");

        notifier.close().toCompletableFuture().join();
        assertThat(notifier.isOwnedClient()).isFalse();
    }

    @Test
    void testJudgeScorerRetriesLengthAndSanitizesPrompt() {
        FakeJudgeClient client = new FakeJudgeClient(List.of(
                new GatewayHttpResponse(200, "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"<tag>bad</tag>\"}}]}"),
                new GatewayHttpResponse(200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"overall\\\": 8, \\\"reason\\\": \\\"ok\\\"}\"}}]}")
        ));
        JudgeScorer scorer = new JudgeScorer(
                "http://judge.local",
                "judge-model",
                "EMPTY",
                60.0,
                1,
                2,
                0.0,
                client
        );

        Map<String, Object> result = scorer.score(
                "<tag>resp</tag>",
                "<tool_call>plan</tool_call>",
                "next",
                "",
                0
        ).join();

        assertThat(((Number) result.get("overall_raw")).doubleValue()).isEqualTo(8.0d);
        assertThat(client.calls).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) ((List<?>) client.calls.get(0).payload().get("messages")).get(0);
        String prompt = String.valueOf(message.get("content"));
        assertThat(prompt).contains("[tool_call block]");
        assertThat(prompt).contains("[tag]resp[/tag]");
    }

    @Test
    void testGatewayTrajectoryRuntimeFillsSingleUserDefaultOnRecord() throws Exception {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());
        FakeRedis redis = new FakeRedis();
        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, redis);

        runtime.recordSample(Map.of("sample_id", "s1"));

        assertThat(redis.hashes.get("rl:traj:s1").get("user_id")).isEqualTo("jiuwenclaw-web");
        String line = Files.readString(tempDir.resolve("samples.jsonl"), StandardCharsets.UTF_8).trim();
        assertThat(line).contains("\"user_id\":\"jiuwenclaw-web\"");
    }

    @Test
    void testOnlineTrajectoryConverterReadsPromptAndResponseTokenIdsFromResponse() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .meta(Map.of("provider_response_json", Map.of(
                        "prompt_token_ids", List.of(1, 2, 3),
                        "choices", List.of(Map.of(
                                "token_ids", List.of(4, 5),
                                "logprobs", List.of(-0.1d, -0.2d)
                        ))
                )))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(detail).build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertThat(batch.getSamples()).hasSize(1);
        assertThat(batch.getSamples().get(0).getPromptIds()).isEqualTo(List.of(1, 2, 3));
        assertThat(batch.getSamples().get(0).getResponseTokens()).isEqualTo(List.of(4, 5));
    }

    @Test
    void testOnlineTrajectoryConverterNormalizesStreamingLogprobsForGateway() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .promptTokenIds(List.of(1, 2, 3))
                .completionTokenIds(List.of(4, 5))
                .logprobs(Map.of("content", List.of(Map.of("logprob", -0.1d), Map.of("logprob", -0.2d))))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-stream")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(step))
                .build();

        Map<String, Object> batch = new OnlineTrajectoryConverter("user-1").convert(trajectory).toDict();
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(
                batch,
                ((List<Map<String, Object>>) batch.get("samples")).get(0),
                ""
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedTrajectory = (Map<String, Object>) normalized.get("trajectory");
        assertEquals(List.of(1, 2, 3), normalizedTrajectory.get("prompt_ids"));
        assertEquals(List.of(4, 5), normalizedTrajectory.get("response_ids"));
        assertEquals(List.of(-0.1d, -0.2d), normalizedTrajectory.get("response_logprobs"));
    }

    @Test
    void testOnlineTrajectoryConverterToleratesMessageModelDumpFailure() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(
                        Map.of("role", "user", "content", "hello"),
                        new BrokenMessage()
                ))
                .response(Map.of("role", "assistant", "content", "pong"))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-broken-message")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(detail).build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertThat(batch.getSamples()).hasSize(1);
        assertThat(batch.getSamples().get(0).getMessages().get(1))
                .isEqualTo(Map.of("role", "assistant", "content", "previous turn"));
    }

    @Test
    void testStreamChatResponsePreservesRuntimeTokenFields() {
        Map<String, Object> logprobs = new LinkedHashMap<>();
        logprobs.put("content", List.of(Map.of("logprob", -0.1d), Map.of("logprob", -0.2d)));
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        choice.put("token_ids", List.of(4, 5));
        choice.put("logprobs", logprobs);
        choice.put("message", Map.of("role", "assistant", "content", "pong"));
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 3);
        usage.put("completion_tokens", 2);
        usage.put("total_tokens", 5);

        Map<String, Object> responseJson = new LinkedHashMap<>();
        responseJson.put("id", "chatcmpl-test");
        responseJson.put("object", "chat.completion");
        responseJson.put("created", 123);
        responseJson.put("model", "m1");
        responseJson.put("prompt_token_ids", List.of(1, 2, 3));
        responseJson.put("usage", usage);
        responseJson.put("choices", List.of(choice));

        List<String> chunks = GatewayHttpHelpers.streamChatResponse(responseJson, "m1");

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).contains("\"prompt_token_ids\": [1, 2, 3]");
        assertThat(chunks.get(0)).contains("\"token_ids\": [4, 5]");
        assertThat(chunks.get(0)).contains("\"logprobs\": {\"content\": [{\"logprob\": -0.1}, {\"logprob\": -0.2}]}");
        assertThat(chunks.get(1)).contains("\"usage\": {\"prompt_tokens\": 3, \"completion_tokens\": 2, \"total_tokens\": 5}");
    }

    private record HttpCall(URI uri, String body) {
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final int statusCode;
        private final String responseBody;
        private final List<HttpCall> calls = new ArrayList<>();

        private RecordingHttpClient(int statusCode, String responseBody) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.of(Executors.newSingleThreadExecutor());
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            calls.add(new HttpCall(request.uri(), readBody(request)));
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new FakeHttpResponse(request, statusCode, responseBody);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private static String readBody(HttpRequest request) {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
            CountDownLatch done = new CountDownLatch(1);
            StringBuilder out = new StringBuilder();
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    out.append(StandardCharsets.UTF_8.decode(item));
                }

                @Override
                public void onError(Throwable throwable) {
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });
            try {
                done.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return out.toString();
        }
    }

    private record FakeHttpResponse(
            HttpRequest request,
            int statusCode,
            String body
    ) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }

    private record JudgeCall(String method, String url, Map<String, Object> payload, Map<String, String> headers) {
    }

    private static final class FakeJudgeClient implements UpstreamGatewayClient {
        private final List<GatewayHttpResponse> responses;
        private final List<JudgeCall> calls = new ArrayList<>();
        private int index;

        private FakeJudgeClient(List<GatewayHttpResponse> responses) {
            this.responses = responses;
        }

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            calls.add(new JudgeCall("POST", "chat.completions", jsonBody, headers));
            return responses.get(index++);
        }

        @Override
        public GatewayHttpResponse request(String method,
                                           String url,
                                           Map<String, Object> params,
                                           Map<String, String> headers,
                                           byte[] content) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = OBJECT_MAPPER.readValue(content, Map.class);
                calls.add(new JudgeCall(method, url, payload, headers));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to capture judge request", exception);
            }
            return responses.get(index++);
        }
    }

    private static final class BrokenMessage {
        private final String role = "assistant";
        private final String content = "previous turn";

        @SuppressWarnings("unused")
        public Map<String, Object> model_dump() {
            throw new IllegalStateException("MockValSer");
        }
    }

    private static final class FakeRedis {
        private final Map<String, String> kv = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        private final Map<String, Set<Object>> sets = new LinkedHashMap<>();
        private final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();

        public RedisTrajectoryStoreBackend.RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
            return (keys, args) -> {
                String pendingKey = keys.get(0);
                String trainingKey = keys.get(1);
                int limit = ((Number) args.get(0)).intValue();
                double nowScore = ((Number) args.get(1)).doubleValue();
                String newStatus = String.valueOf(args.get(2));
                String trajPrefix = String.valueOf(args.get(3));
                List<Map.Entry<Object, Double>> ordered = new ArrayList<>(
                        zsets.getOrDefault(pendingKey, Map.of()).entrySet());
                ordered.sort(Map.Entry.comparingByValue());
                List<Object> ids = ordered.stream().limit(limit).map(Map.Entry::getKey).toList();
                for (Object sampleId : ids) {
                    zsets.getOrDefault(pendingKey, new LinkedHashMap<>()).remove(sampleId);
                    zsets.computeIfAbsent(trainingKey, ignored -> new LinkedHashMap<>()).put(sampleId, nowScore);
                    hashes.computeIfAbsent(trajPrefix + sampleId, ignored -> new LinkedHashMap<>())
                            .put("status", newStatus);
                }
                return new ArrayList<>(ids);
            };
        }

        public List<Object> hmget(String key, List<String> fields) {
            Map<String, Object> hash = hashes.getOrDefault(key, Map.of());
            List<Object> values = new ArrayList<>();
            for (String field : fields) {
                values.add(hash.get(field));
            }
            return values;
        }

        public long hset(String key, Map<String, Object> mapping) {
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return 1;
        }

        public long zadd(String key, Map<String, Double> mapping) {
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        public long zcard(String key) {
            return zsets.getOrDefault(key, Map.of()).size();
        }

        public long zrem(String key, Object... members) {
            long removed = 0;
            Map<Object, Double> zset = zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            for (Object member : members) {
                if (zset.remove(member) != null) {
                    removed++;
                }
            }
            return removed;
        }

        public long sadd(String key, Object... members) {
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (Object member : members) {
                set.add(member);
            }
            return members.length;
        }

        public long srem(String key, Object... members) {
            long removed = 0;
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (Object member : members) {
                if (set.remove(member)) {
                    removed++;
                }
            }
            return removed;
        }

        public Set<Object> smembers(String key) {
            return new LinkedHashSet<>(sets.getOrDefault(key, Set.of()));
        }

        public void set(String key, String value, int ttlSeconds) {
            kv.put(key, value);
        }

        public long expire(String key, int ttlSeconds) {
            return 1;
        }

        public List<Object> zrange(String key, int start, int end) {
            List<Map.Entry<Object, Double>> ordered = new ArrayList<>(
                    zsets.getOrDefault(key, Map.of()).entrySet());
            ordered.sort(Map.Entry.comparingByValue());
            int effectiveEnd = end == -1 ? ordered.size() - 1 : Math.min(end, ordered.size() - 1);
            if (ordered.isEmpty() || start > effectiveEnd) {
                return List.of();
            }
            List<Object> keys = new ArrayList<>();
            for (int index = start; index <= effectiveEnd; index++) {
                keys.add(ordered.get(index).getKey());
            }
            return keys;
        }

        public List<Object> mget(List<String> keys) {
            return keys.stream().map(kv::get).map(value -> (Object) value).toList();
        }

        public Object get(String key) {
            return kv.get(key);
        }

        public FakePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    private static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline {
        private final FakeRedis redis;
        private final List<Operation> operations = new ArrayList<>();

        private FakePipeline(FakeRedis redis) {
            this.redis = redis;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zrem(String key, Object... members) {
            operations.add(() -> redis.zrem(key, members));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hset(String key, Map<String, Object> mapping) {
            operations.add(() -> redis.hset(key, mapping));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zadd(String key, Map<String, Double> mapping) {
            operations.add(() -> redis.zadd(key, mapping));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline sadd(String key, Object... members) {
            operations.add(() -> redis.sadd(key, members));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline zcard(String key) {
            operations.add(() -> redis.zcard(key));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hget(String key, String field) {
            operations.add(() -> redis.hashes.getOrDefault(key, Map.of()).get(field));
            return this;
        }

        @Override
        public RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline hmget(String key, List<String> fields) {
            operations.add(() -> redis.hmget(key, fields));
            return this;
        }

        @Override
        public List<Object> execute() {
            List<Object> results = new ArrayList<>();
            for (Operation operation : operations) {
                results.add(operation.run());
            }
            operations.clear();
            return results;
        }
    }

    private interface Operation {
        Object run();
    }
}
