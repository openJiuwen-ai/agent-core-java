/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryPackage;
import com.openjiuwen.agent_teams.memory.TeamMemoryConfig;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.foundation.store.base_reranker.Document;
import com.openjiuwen.core.foundation.store.base_reranker.RerankerConfig;
import com.openjiuwen.core.foundation.store.kv.KvStorePackage;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.foundation.store.query.QueryPackage;
import com.openjiuwen.core.graph.store.GraphStorePackage;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.InMemoryStore;
import com.openjiuwen.core.graph.store.PendingNode;
import com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;
import com.openjiuwen.core.retrieval.reranker.StandardReranker;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.InMemoryCommitState;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.extensions.context_evolver.core.context.ContextPackage;
import com.openjiuwen.harness.lsp.core.LspServerInstance;
import com.openjiuwen.harness.lsp.core.LspServerState;
import com.openjiuwen.harness.lsp.core.ScopedLspServerConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Batch0050FocusedTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void trajectoryPackageExportsExpectedSymbols() {
        assertThat(TrajectoryPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/agent_evolving/trajectory/__init__.py");
        assertThat(TrajectoryPackage.LLM_CALL_DETAIL).isSameAs(LLMCallDetail.class);
        assertThat(TrajectoryPackage.EXPORTED_SYMBOLS)
                .contains("TrajectoryBuilder", "TrajectoryStore", "aggregate_member_trajectories");
    }

    @Test
    void packageBridgeConstantsMatchPythonModules() {
        assertThat(GraphStorePackage.PYTHON_MODULE).isEqualTo("openjiuwen/core/graph/store/__init__.py");
        assertThat(KvStorePackage.PYTHON_MODULE).isEqualTo("openjiuwen/core/foundation/store/kv/__init__.py");
        assertThat(ContextPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/extensions/context_evolver/core/context/__init__.py");
        assertThat(QueryPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/foundation/store/query/__init__.py");
        assertThat(QueryPackage.EXPORTED_SYMBOLS).contains("register_database_query_language");
        assertThat(QueryExpr.isLanguageRegistered("milvus")).isTrue();
        assertThat(QueryExpr.isLanguageRegistered("chroma")).isTrue();
    }

    @Test
    void teamMemoryConfigResolvePrefersExplicitEmbeddingConfig() {
        EmbeddingConfig embeddingConfig = EmbeddingConfig.builder()
                .modelName("embed-model")
                .baseUrl("https://example.invalid")
                .apiKey("token")
                .build();
        TeamMemoryConfig config = TeamMemoryConfig.builder().embeddingConfig(embeddingConfig).build();

        assertThat(TeamMemoryConfig.resolveEmbeddingConfig(config)).isSameAs(embeddingConfig);
    }

    @Test
    void inMemoryStoreSupportsAsyncRoundTripAndPrefixDelete() {
        InMemoryStore store = new InMemoryStore();
        GraphStoreState state = GraphStoreState.create(
                "ns/demo",
                2,
                Map.of("value", 1),
                List.of(),
                Map.of("node", new PendingNode("node", "running")),
                Map.of("node", 3)
        );

        store.save("session-1", "ns/demo", state).toCompletableFuture().join();
        Optional<GraphStoreState> loaded = store.get("session-1", "ns/demo").toCompletableFuture().join();
        store.delete("session-1", "ns").toCompletableFuture().join();

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getNodeVersion()).containsEntry("node", 3);
        assertThat(store.get("session-1", "ns/demo").toCompletableFuture().join()).isEmpty();
    }

    @Test
    void workflowAndAgentStateRoundTripMatchesPythonStateKeys() {
        WorkflowStateCollection workflowState = new WorkflowStateCollection(
                new InMemoryCommitState(),
                new InMemoryCommitState(),
                new InMemoryCommitState(),
                new InMemoryCommitState(),
                new LinkedHashMap<>(),
                "parent",
                "node"
        );
        Map<String, Object> serialized = Map.of(
                "io_state", Map.of("node", Map.of("input", "v")),
                "global_state", Map.of("node", Map.of("g", 1)),
                "comp_state", Map.of("node", Map.of("c", 2)),
                "workflow_state", Map.of("workflow", Map.of("w", 3))
        );

        workflowState.setState(serialized);

        assertThat(workflowState.getState()).isEqualTo(serialized);

        WorkflowCommitState restored = InMemoryState.fromMap(serialized);
        assertThat(restored.getState()).isEqualTo(serialized);

        AgentStateCollection agentStateCollection = new AgentStateCollection();
        agentStateCollection.setState(Map.of(
                "global_state", Map.of("locale", "zh-CN"),
                "agent_state", Map.of("round", 2)
        ));
        assertThat(agentStateCollection.getState()).isEqualTo(Map.of(
                "global_state", Map.of("locale", "zh-CN"),
                "agent_state", Map.of("round", 2)
        ));
    }

    @Test
    void streamWriterValidatesMapsAndDirectInstances() {
        StreamEmitter emitter = new StreamEmitter();
        StreamWriter<Payload> writer = new StreamWriter<>(
                emitter,
                Payload.class,
                map -> new Payload(String.valueOf(map.get("message")))
        );

        writer.write(Map.of("message", "alpha"));
        writer.write(new Payload("beta"));

        assertThat(emitter.getStreamQueue().receive(100)).isEqualTo(new Payload("alpha"));
        assertThat(emitter.getStreamQueue().receive(100)).isEqualTo(new Payload("beta"));
        assertThatThrownBy(() -> writer.write(null))
                .hasMessageContaining("stream data is null");
    }

    @Test
    void openAiEmbeddingUsesConfiguredDimensionsAndParsesBase64Payload() {
        ExposedOpenAIEmbedding embedding = new ExposedOpenAIEmbedding(4);

        List<Double> values = embedding.embedQuerySync("hello", Map.of());

        assertThat(embedding.lastPayload).containsEntry("dimensions", 4);
        assertThat(values).containsExactly(1.0d, -2.5d, 3.25d);
        assertThat(embedding.getDimension()).isEqualTo(4);
    }

    @Test
    void dashscopeEmbeddingBuildsDimensionedParamsAndSortsResponse() throws Exception {
        ExposedDashscopeEmbedding embedding = new ExposedDashscopeEmbedding(8);

        Map<String, Object> params = embedding.exposeBuildRequestParams(List.of("hello"), Map.of("user", "u1"));
        List<List<Double>> values = embedding.exposeParseResponse(
                OBJECT_MAPPER.readTree("""
                        {"output":{"embeddings":[
                          {"index":1,"embedding":[0.3,0.4]},
                          {"index":0,"embedding":[0.1,0.2]}
                        ]}}
                        """)
        );

        assertThat(params).containsEntry("dimension", 8);
        assertThat(params).containsEntry("user", "u1");
        assertThat(params.get("input")).isEqualTo(List.of("hello"));
        assertThat(values).containsExactly(List.of(0.1d, 0.2d), List.of(0.3d, 0.4d));
    }

    @Test
    void standardRerankerMapsScoresBackToDocumentIds() {
        ExposedStandardReranker reranker = new ExposedStandardReranker();

        Map<String, Double> scores = reranker.exposeParseResponse(
                Map.of("output", Map.of("results", List.of(
                        Map.of("index", 1, "relevance_score", 0.8d),
                        Map.of("index", 0, "relevance_score", 0.2d)
                ))),
                List.of(
                        "raw-doc",
                        Document.builder().id("doc-2").text("doc-text").build()
                )
        );

        assertThat(scores).containsEntry("raw-doc", 0.2d);
        assertThat(scores).containsEntry("doc-2", 0.8d);
    }

    @Test
    void lspServerInstanceReportsEmptyCommandAsError() {
        ScopedLspServerConfig config = new ScopedLspServerConfig();
        config.setServerId("pyright");
        config.setCommand("");
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        LspServerInstance instance = new LspServerInstance(config, errorRef::set);

        assertThatThrownBy(instance::start)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to start LSP server");
        assertThat(instance.getName()).isEqualTo("pyright");
        assertThat(instance.getState()).isEqualTo(LspServerState.ERROR);
        assertThat(instance.getCrashCount()).isEqualTo(1);
        assertThat(errorRef.get()).isNotNull();
    }

    private record Payload(String message) {
    }

    private static final class ExposedOpenAIEmbedding extends OpenAIEmbedding {

        private Map<String, Object> lastPayload = Map.of();

        private ExposedOpenAIEmbedding(int dimension) {
            super(
                    EmbeddingConfig.builder()
                            .modelName("openai-embed")
                            .baseUrl("https://example.invalid/v1/embeddings")
                            .apiKey("token")
                            .build(),
                    60,
                    1,
                    null,
                    8,
                    4,
                    dimension,
                    null
            );
        }

        @Override
        protected ApiResponse doRequest(Map<String, Object> payload) throws IOException {
            lastPayload = new LinkedHashMap<>(payload);
            ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * 3).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putFloat(1.0f);
            buffer.putFloat(-2.5f);
            buffer.putFloat(3.25f);
            String encoded = Base64.getEncoder().encodeToString(buffer.array());
            return buildResponse(200, """
                    {"data":[{"index":0,"embedding":"%s"}]}
                    """.formatted(encoded));
        }

        private ApiResponse buildResponse(int statusCode, String body) {
            try {
                Class<?> type = Class.forName("com.openjiuwen.core.retrieval.embedding.APIEmbedding$ApiResponse");
                java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(int.class, String.class);
                constructor.setAccessible(true);
                return (ApiResponse) constructor.newInstance(statusCode, body);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to construct ApiResponse test stub", exception);
            }
        }
    }

    private static final class ExposedDashscopeEmbedding extends DashscopeEmbedding {

        private ExposedDashscopeEmbedding(int dimension) {
            super(
                    EmbeddingConfig.builder()
                            .modelName("dashscope-embed")
                            .baseUrl("https://dashscope.example.invalid")
                            .apiKey("token")
                            .build(),
                    60,
                    1,
                    null,
                    8,
                    4,
                    dimension,
                    null
            );
        }

        private Map<String, Object> exposeBuildRequestParams(List<?> texts, Map<String, Object> kwargs) {
            return buildRequestParams(texts, kwargs);
        }

        private List<List<Double>> exposeParseResponse(com.fasterxml.jackson.databind.JsonNode root) {
            return parseDashscopeEmbeddings(root);
        }
    }

    private static final class ExposedStandardReranker extends StandardReranker {

        private ExposedStandardReranker() {
            super(
                    RerankerConfig.builder()
                            .apiBase("https://rerank.example.invalid/rerank")
                            .apiKey("token")
                            .modelName("reranker")
                            .build()
            );
        }

        private Map<String, Double> exposeParseResponse(Map<String, Object> responseData, List<Object> doc) {
            return parseResponse(responseData, doc);
        }
    }
}
