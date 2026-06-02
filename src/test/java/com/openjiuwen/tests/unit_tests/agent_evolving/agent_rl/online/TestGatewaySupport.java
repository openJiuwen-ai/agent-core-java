/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl.online;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.app.HttpHelpers;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.GatewayTrajectoryRuntime;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.PendingJudgeStore;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.RailBatchIngestor;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GatewaySupport.
 * <p>
 * Mirrors Python's {@code test_gateway_support.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/online/}.
 */
@DisplayName("GatewaySupport Tests")
class TestGatewaySupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("judge scorer parses multiple code blocks and aliases")
    void testJudgeScorerParseScoresHandlesMultipleCodeBlocksAndAliases() {
        String content = """
                preface
                ```text
                ignored
                ```
                ```json
                {"task_completion_score": 8, "response_quality": 7, "tool_usage_score": 9, "coherence": 6}
                ```
                """;

        Map<String, Object> scores = JudgeScorer.parseScores(content);

        assertThat(((Number) scores.get("task_completion_score")).intValue()).isEqualTo(8);
        assertThat(((Number) scores.get("overall")).doubleValue()).isEqualTo(7.5);
    }

    @Test
    @DisplayName("inference notifier uses supplied HTTP client")
    void testInferenceNotifierUsesAsyncClientEquivalent() throws Exception {
        CapturingHttpClient client = new CapturingHttpClient(200, "ok");
        InferenceNotifier notifier = new InferenceNotifier("http://vllm.local", 120.0, client);

        notifier.notifyUpdate("user1", "/tmp/lora");
        notifier.close();

        assertThat(client.requests).hasSize(1);
        assertThat(client.requests.getFirst().uri()).isEqualTo(URI.create("http://vllm.local/v1/load_lora_adapter"));
        assertThat(client.bodies.getFirst()).contains("\"lora_name\":\"user1\"");
        assertThat(client.bodies.getFirst()).contains("\"lora_path\":\"/tmp/lora\"");
        assertThat(client.bodies.getFirst()).contains("\"load_inplace\":true");
    }

    @Test
    @DisplayName("judge scorer retries length output and sanitizes prompt")
    void testJudgeScorerRetriesLengthAndSanitizesPrompt() {
        FakeJudgeClient client = new FakeJudgeClient(List.of(
                new GatewayHttpResponse(200, "{\"choices\":[{\"finish_reason\":\"length\","
                        + "\"message\":{\"content\":\"<tag>bad</tag>\"}}]}"),
                new GatewayHttpResponse(200, "{\"choices\":[{\"finish_reason\":\"stop\","
                        + "\"message\":{\"content\":\"{\\\"overall\\\": 8, \\\"reason\\\": \\\"ok\\\"}\"}}]}")
        ));
        JudgeScorer scorer = new JudgeScorer(
                "http://judge.local/",
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

        assertThat(((Number) result.get("overall_raw")).doubleValue()).isEqualTo(8.0);
        assertThat(result).doesNotContainKeys("model", "session_id", "turn_num");
        assertThat(client.calls).hasSize(2);
        assertThat(client.calls.getFirst().url()).isEqualTo("http://judge.local/v1/chat/completions");
        String prompt = String.valueOf(
                ((Map<?, ?>) ((List<?>) client.calls.getFirst().payload().get("messages")).getFirst()).get("content")
        );
        assertThat(prompt).contains("[tool_call block]");
        assertThat(prompt).contains("[tag]resp[/tag]");
    }

    @Test
    @DisplayName("gateway trajectory runtime fills single-user default on record")
    void testGatewayTrajectoryRuntimeFillsSingleUserDefaultOnRecord() throws Exception {
        GatewayConfig config = new GatewayConfig(18080);
        config.setModelId("dummy-model");
        config.setRecordDir(tempDir.toString());
        FakeRedis redis = new FakeRedis();
        GatewayTrajectoryRuntime runtime = new GatewayTrajectoryRuntime(config, redis);

        runtime.recordSample(new LinkedHashMap<>(Map.of("sample_id", "s1")));

        assertThat(redis.hashes.get("rl:traj:s1").get("user_id")).isEqualTo("jiuwenclaw-web");
        Path sampleFile = tempDir.resolve("samples.jsonl");
        assertThat(Files.readString(sampleFile)).contains("\"user_id\":\"jiuwenclaw-web\"");
    }

    @Test
    @DisplayName("online trajectory converter reads prompt and response token IDs")
    void testOnlineTrajectoryConverterReadsPromptAndResponseTokenIdsFromResponse() {
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("prompt_token_ids", List.of(1, 2, 3));
        provider.put("choices", List.of(Map.of("token_ids", List.of(4, 5), "logprobs", List.of(-0.1, -0.2))));
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .meta(Map.of("provider_response_json", provider))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-1")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(detail).build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertThat(batch.getSamples()).hasSize(1);
        assertThat(batch.getSamples().getFirst().getPromptIds()).containsExactly(1, 2, 3);
        assertThat(batch.getSamples().getFirst().getResponseTokens()).containsExactly(4, 5);
    }

    @Test
    @DisplayName("online trajectory converter normalizes streaming logprobs for gateway")
    void testOnlineTrajectoryConverterNormalizesStreamingLogprobsForGateway() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m1")
                .messages(List.of(Map.of("role", "user", "content", "hello")))
                .response(Map.of("role", "assistant", "content", "pong"))
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .meta(Map.of(
                        "prompt_token_ids", List.of(1, 2, 3),
                        "completion_token_ids", List.of(4, 5),
                        "logprobs", Map.of("content", List.of(Map.of("logprob", -0.1), Map.of("logprob", -0.2)))
                ))
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-stream")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(step))
                .build();

        Map<String, Object> payload = new OnlineTrajectoryConverter("user-1").convert(trajectory).toDict();
        @SuppressWarnings("unchecked")
        Map<String, Object> sample = (Map<String, Object>) ((List<?>) payload.get("samples")).getFirst();
        Map<String, Object> normalized = RailBatchIngestor.normalizeRailSample(payload, sample, "");
        @SuppressWarnings("unchecked")
        Map<String, Object> normalizedTrajectory = (Map<String, Object>) normalized.get("trajectory");

        assertThat(normalizedTrajectory.get("prompt_ids")).isEqualTo(List.of(1, 2, 3));
        assertThat(normalizedTrajectory.get("response_ids")).isEqualTo(List.of(4, 5));
        assertThat(normalizedTrajectory.get("response_logprobs")).isEqualTo(List.of(-0.1, -0.2));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    @DisplayName("online trajectory converter tolerates message model dump failure")
    void testOnlineTrajectoryConverterToleratesMessageModelDumpFailure() {
        class BrokenMessage {
            public String role = "assistant";
            public String content = "previous turn";

            public Map<String, Object> model_dump() {
                throw new RuntimeException("MockValSer");
            }
        }

        List rawMessages = new ArrayList();
        rawMessages.add(Map.of("role", "user", "content", "hello"));
        rawMessages.add(new BrokenMessage());
        LLMCallDetail detail = new LLMCallDetail(
                "m1",
                rawMessages,
                Map.of("role", "assistant", "content", "pong"),
                null,
                null,
                null
        );
        Trajectory trajectory = Trajectory.builder()
                .executionId("traj-broken-message")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(TrajectoryStep.builder().kind("llm").detail(detail).build()))
                .build();

        RailV1Batch batch = new OnlineTrajectoryConverter("user-1").convert(trajectory);

        assertThat(batch.getSamples()).hasSize(1);
        assertThat(batch.getSamples().getFirst().getMessages().get(1))
                .isEqualTo(Map.of("role", "assistant", "content", "previous turn"));
    }

    @Test
    @DisplayName("stream chat response preserves runtime token fields")
    void testStreamChatResponsePreservesRuntimeTokenFields() {
        Map<String, Object> responseJson = new LinkedHashMap<>();
        responseJson.put("id", "chatcmpl-test");
        responseJson.put("object", "chat.completion");
        responseJson.put("created", 123);
        responseJson.put("model", "m1");
        responseJson.put("prompt_token_ids", List.of(1, 2, 3));
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", 3);
        usage.put("completion_tokens", 2);
        usage.put("total_tokens", 5);
        responseJson.put("usage", usage);
        responseJson.put("choices", List.of(Map.of(
                "index", 0,
                "finish_reason", "stop",
                "token_ids", List.of(4, 5),
                "logprobs", Map.of("content", List.of(Map.of("logprob", -0.1), Map.of("logprob", -0.2))),
                "message", Map.of("role", "assistant", "content", "pong")
        )));

        List<String> chunks = HttpHelpers.streamChatResponse(responseJson, "m1");

        assertThat(chunks).hasSize(3);
        assertThat(chunks.getFirst()).contains("\"prompt_token_ids\":[1,2,3]");
        assertThat(chunks.getFirst()).contains("\"token_ids\":[4,5]");
        assertThat(chunks.getFirst()).contains("\"logprobs\":{\"content\":[{\"logprob\":-0.1},{\"logprob\":-0.2}]}");
        assertThat(chunks.get(1)).contains("\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}");
    }

    record Call(String method, String url, Map<String, Object> payload, Map<String, String> headers) {
    }

    static final class FakeJudgeClient implements UpstreamGatewayClient {
        private final List<GatewayHttpResponse> responses;
        private int index;
        final List<Call> calls = new ArrayList<>();

        FakeJudgeClient(List<GatewayHttpResponse> responses) {
            this.responses = responses;
        }

        @Override
        public GatewayHttpResponse postChatCompletions(Map<String, Object> jsonBody, Map<String, String> headers) {
            calls.add(new Call("POST", "chat.completions", jsonBody, headers));
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
                calls.add(new Call(method, url, payload, headers));
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to capture judge request", exception);
            }
            return responses.get(index++);
        }
    }

    static final class CapturingHttpClient extends HttpClient {
        private final int status;
        private final String body;
        final List<HttpRequest> requests = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        CapturingHttpClient(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
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
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            bodies.add(request.bodyPublisher().map(TestGatewaySupport::bodyString).orElse(""));
            @SuppressWarnings("unchecked")
            T typedBody = (T) body;
            return new SimpleHttpResponse<>(request, status, typedBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }
    }

    record SimpleHttpResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
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

    static final class FakeRedis implements RedisTrajectoryStoreBackend, PendingJudgeStore.TestablePendingJudgeBackend {
        final Map<String, String> kv = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> hashes = new LinkedHashMap<>();
        final Map<String, Set<Object>> sets = new LinkedHashMap<>();
        final Map<String, Map<Object, Double>> zsets = new LinkedHashMap<>();
        final Map<String, Integer> ttl = new LinkedHashMap<>();

        @Override
        public void set(String key, String value, int ttlSec) {
            kv.put(key, value);
        }

        @Override
        public void expire(String key, int ttlSec) {
            ttl.put(key, ttlSec);
        }

        @Override
        public List<String> zrange(String key, int start, int end) {
            List<Map.Entry<Object, Double>> entries = new ArrayList<>(zsets.getOrDefault(key, Map.of()).entrySet());
            entries.sort(Map.Entry.comparingByValue());
            List<String> members = entries.stream().map(entry -> String.valueOf(entry.getKey())).toList();
            int normalizedEnd = end == -1 ? members.size() - 1 : end;
            if (members.isEmpty() || start > normalizedEnd) {
                return List.of();
            }
            return new ArrayList<>(members.subList(start, Math.min(normalizedEnd + 1, members.size())));
        }

        @Override
        public Object get(String key) {
            return kv.get(key);
        }

        @Override
        public RedisTrajectoryStoreFetchScript registerFetchAndMarkScript(String luaSource) {
            return (keys, args) -> {
                String pendingKey = keys.get(0);
                String trainingKey = keys.get(1);
                int limit = ((Number) args.get(0)).intValue();
                double nowScore = ((Number) args.get(1)).doubleValue();
                String newStatus = String.valueOf(args.get(2));
                String trajPrefix = String.valueOf(args.get(3));
                List<Map.Entry<Object, Double>> ordered =
                        new ArrayList<>(zsets.getOrDefault(pendingKey, Map.of()).entrySet());
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

        @Override
        public List<Object> hmget(String key, List<String> fields) {
            Map<String, Object> hash = hashes.getOrDefault(key, Map.of());
            List<Object> values = new ArrayList<>();
            for (String field : fields) {
                values.add(hash.get(field));
            }
            return values;
        }

        @Override
        public Object hget(String key, String field) {
            return hashes.getOrDefault(key, Map.of()).get(field);
        }

        @Override
        public long hset(String key, Map<String, Object> mapping) {
            hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return 1;
        }

        @Override
        public long zadd(String key, Map<String, Double> mapping) {
            zsets.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).putAll(mapping);
            return mapping.size();
        }

        @Override
        public long zcard(String key) {
            return zsets.getOrDefault(key, Map.of()).size();
        }

        @Override
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

        @Override
        public long sadd(String key, Object... members) {
            Set<Object> set = sets.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            for (Object member : members) {
                set.add(member);
            }
            return members.length;
        }

        @Override
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

        @Override
        public Set<Object> smembers(String key) {
            return new LinkedHashSet<>(sets.getOrDefault(key, Set.of()));
        }

        @Override
        public RedisTrajectoryStorePipeline pipeline() {
            return new FakePipeline(this);
        }
    }

    static final class FakePipeline implements RedisTrajectoryStoreBackend.RedisTrajectoryStorePipeline,
            PendingJudgeStore.TestablePendingJudgePipeline {
        private final FakeRedis redis;
        private final List<Operation> operations = new ArrayList<>();

        FakePipeline(FakeRedis redis) {
            this.redis = redis;
        }

        @Override
        public void delete(String key) {
            operations.add(() -> redis.kv.remove(key));
        }

        @Override
        public void zremSingle(String key, String member) {
            operations.add(() -> redis.zrem(key, member));
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
            operations.add(() -> redis.hget(key, field));
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

    interface Operation {
        Object run();
    }

    private static String bodyString(HttpRequest.BodyPublisher publisher) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                try {
                    out.write(bytes);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        return out.toString(StandardCharsets.UTF_8);
    }
}
