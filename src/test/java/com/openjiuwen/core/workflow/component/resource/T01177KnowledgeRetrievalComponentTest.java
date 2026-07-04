/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.retrieval.GraphKnowledgeBase;
import com.openjiuwen.core.retrieval.KnowledgeBase;
import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.session.BaseSession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused parity checks for T01177.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/component/test_knowledge_retrieval_comp.py}.</p>
 *
 * <p>Mirrors Python's {@code ComponentKBConfig}, {@code KnowledgeRetrievalInput},
 * {@code KnowledgeRetrievalOutput}, {@code KnowledgeRetrievalExecutable}, and
 * {@code KnowledgeRetrievalComponent} in
 * {@code openjiuwen/core/workflow/components/resource/knowledge_retrieval_comp.py}.</p>
 */
class T01177KnowledgeRetrievalComponentTest {

    @TempDir
    Path tempDir;

    @Test
    void inputFromMapKeepsQueryAndAllowedExtraFields() {
        KnowledgeRetrievalInput input = KnowledgeRetrievalInput.fromMap(Map.of("query", "test query", "extra_field", "extra"));

        assertEquals("test query", input.getQuery());
        assertEquals("extra", input.getExtraFields().get("extra_field"));
    }

    @Test
    void inputFromNullMapUsesDefaults() {
        KnowledgeRetrievalInput input = KnowledgeRetrievalInput.fromMap(null);

        assertNull(input.getQuery());
        assertTrue(input.getExtraFields().isEmpty());
    }

    @Test
    void outputDefaultsMatchPythonModelDefaults() {
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput();

        assertEquals(List.of(), output.getResults());
        assertEquals("", output.getContext());
        assertEquals(Map.of("results", List.of(), "context", ""), output.toMap());
    }

    @Test
    void outputModelUsesOnlyPythonFields() {
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput(List.of("text1", "text2"), "text1\n\ntext2");

        Map<String, Object> dumped = output.toMap();

        assertEquals(List.of("text1", "text2"), dumped.get("results"));
        assertEquals("text1\n\ntext2", dumped.get("context"));
        assertFalse(dumped.containsKey("results_with_metadata"));
    }

    @Test
    void outputFromMapParsesKnownPythonFields() {
        KnowledgeRetrievalOutput output = KnowledgeRetrievalOutput.fromMap(Map.of(
                "results", List.of("text1", 42),
                "context", "text1\n\n42"
        ));

        assertEquals(List.of("text1", "42"), output.getResults());
        assertEquals("text1\n\n42", output.getContext());
    }

    @Test
    void outputFromNullMapUsesDefaults() {
        KnowledgeRetrievalOutput output = KnowledgeRetrievalOutput.fromMap(null);

        assertEquals(List.of(), output.getResults());
        assertEquals("", output.getContext());
    }

    @Test
    void invokeReturnsResultsAndDefaultContextSeparator() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        setField(executable, "knowledgeBases", List.of(
                new FakeKnowledgeBase("kb1", List.of(result("doc A", 0.9d))),
                new FakeKnowledgeBase("kb2", List.of(result("doc B", 0.8d)))
        ));

        Object rawOutput = executable.invoke(Map.of("query", "test query"), new TestSession(), null);
        Map<?, ?> output = assertInstanceOf(Map.class, rawOutput);

        assertEquals(List.of("doc A", "doc B"), output.get("results"));
        assertEquals("doc A\n\ndoc B", output.get("context"));
        assertFalse(output.containsKey("results_with_metadata"));
    }

    @Test
    void invokeReturnsEmptyResultsAndEmptyContext() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        setField(executable, "knowledgeBases", List.of(new FakeKnowledgeBase("kb1", List.of())));

        Object rawOutput = executable.invoke(Map.of("query", "no result query"), new TestSession(), null);
        Map<?, ?> output = assertInstanceOf(Map.class, rawOutput);

        assertEquals(List.of(), output.get("results"));
        assertEquals("", output.get("context"));
    }

    @Test
    void invokeJoinsContextWithDefaultSeparatorForAllResults() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        setField(executable, "knowledgeBases", List.of(
                new FakeKnowledgeBase("kb1", List.of(result("A", 0.9d))),
                new FakeKnowledgeBase("kb2", List.of(result("B", 0.8d), result("C", 0.7d)))
        ));

        Object rawOutput = executable.invoke(Map.of("query", "sep query"), new TestSession(), null);
        Map<?, ?> output = assertInstanceOf(Map.class, rawOutput);

        assertEquals("A\n\nB\n\nC", output.get("context"));
    }

    @Test
    void emptyQueryRaisesInputParamErrorAfterLazyInitIsSatisfied() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        setField(executable, "knowledgeBases", List.of());

        BaseError error = assertThrows(
                BaseError.class,
                () -> executable.invoke(Map.of("query", "   "), new TestSession(), null)
        );

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void missingQueryRaisesInputParamErrorAfterLazyInitIsSatisfied() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        setField(executable, "knowledgeBases", List.of());

        BaseError error = assertThrows(
                BaseError.class,
                () -> executable.invoke(Map.of("not_query", "value"), new TestSession(), null)
        );

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void retrievalFailureFromOneKnowledgeBaseReturnsEmptyOutput() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        setField(executable, "knowledgeBases", List.of(new FailingKnowledgeBase("kb1")));

        Object rawOutput = executable.invoke(Map.of("query", "fail query"), new TestSession(), null);
        Map<?, ?> output = assertInstanceOf(Map.class, rawOutput);

        assertEquals(List.of(), output.get("results"));
        assertEquals("", output.get("context"));
    }

    @Test
    void topLevelRetrievalHelperFailureRaisesInvokeCallFailed() throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(baseConfig());
        setField(executable, "initialized", true);
        List<KnowledgeBase> knowledgeBases = new ArrayList<>();
        knowledgeBases.add(null);
        setField(executable, "knowledgeBases", knowledgeBases);

        BaseError error = assertThrows(
                BaseError.class,
                () -> executable.invoke(Map.of("query", "fail query"), new TestSession(), null)
        );

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
    }

    @Test
    void multipleKbConfigsCreateMultipleSimpleKnowledgeBases() throws Exception {
        KnowledgeRetrievalCompConfig config = configWithKbCount("bm25", 2, false, false);
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config);

        @SuppressWarnings("unchecked")
        List<KnowledgeBase> knowledgeBases = (List<KnowledgeBase>) invokePrivate(executable, "createKnowledgeBases");

        assertEquals(2, knowledgeBases.size());
        assertTrue(knowledgeBases.stream().allMatch(SimpleKnowledgeBase.class::isInstance));
        assertEquals("kb1", knowledgeBases.get(0).getConfig().getKbId());
        assertEquals("kb2", knowledgeBases.get(1).getConfig().getKbId());
    }

    @Test
    void bm25KnowledgeBaseDoesNotRequireEmbedConfig() throws Exception {
        KnowledgeRetrievalCompConfig config = configWithKbCount("bm25", 1, false, false);
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config);

        @SuppressWarnings("unchecked")
        List<KnowledgeBase> knowledgeBases = (List<KnowledgeBase>) invokePrivate(executable, "createKnowledgeBases");

        assertEquals(1, knowledgeBases.size());
        assertInstanceOf(SimpleKnowledgeBase.class, knowledgeBases.get(0));
    }

    @Test
    void useGraphCreatesGraphKnowledgeBase() throws Exception {
        KnowledgeRetrievalCompConfig config = configWithKbCount("bm25", 1, true, false);
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config);

        @SuppressWarnings("unchecked")
        List<KnowledgeBase> knowledgeBases = (List<KnowledgeBase>) invokePrivate(executable, "createKnowledgeBases");

        assertEquals(1, knowledgeBases.size());
        assertInstanceOf(GraphKnowledgeBase.class, knowledgeBases.get(0));
    }

    @Test
    void missingEmbedConfigForVectorIndexIsWrappedAsInitialisationFailure() {
        KnowledgeRetrievalCompConfig config = configWithKbCount("vector", 1, false, false);
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config);

        BaseError error = assertThrows(
                BaseError.class,
                () -> executable.invoke(Map.of("query", "needs embedding"), new TestSession(), null)
        );

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
    }

    @Test
    void invalidEmbedAdditionalConfigIsWrappedAsInitialisationFailure() {
        KnowledgeRetrievalCompConfig config = configWithKbCount("vector", 1, false, true);
        config.getComponentKbConfigs().get(0).setEmbedAdditionalConfig(Map.of("timeout", "not-an-int"));
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config);

        BaseError error = assertThrows(
                BaseError.class,
                () -> executable.invoke(Map.of("query", "embed failure"), new TestSession(), null)
        );

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
    }

    @Test
    void lazyInitialisationRunsOnlyOnce() throws Exception {
        KnowledgeRetrievalCompConfig config = configWithKbCount("bm25", 1, false, false);
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config);

        invokePrivate(executable, "initializeIfNeeded");
        Object firstKnowledgeBases = getField(executable, "knowledgeBases");
        invokePrivate(executable, "initializeIfNeeded");

        assertSame(firstKnowledgeBases, getField(executable, "knowledgeBases"));
        assertEquals(Boolean.TRUE, getField(executable, "initialized"));
    }

    @Test
    void componentToExecutableReturnsKnowledgeRetrievalExecutable() {
        KnowledgeRetrievalComponent component = new KnowledgeRetrievalComponent(baseConfig());

        assertInstanceOf(KnowledgeRetrievalExecutable.class, component.toExecutable());
    }

    @Test
    void addComponentAddsExecutableNodeToGraph() {
        KnowledgeRetrievalComponent component = new KnowledgeRetrievalComponent(baseConfig());
        RecordingGraph graph = new RecordingGraph();

        component.addComponent(graph, "kr_node", true);

        assertEquals("kr_node", graph.nodeId);
        assertTrue(graph.waitForAll);
        assertInstanceOf(KnowledgeRetrievalExecutable.class, graph.node);
    }

    @Test
    void configSettersDefensivelyCopyMutableCollections() {
        ComponentKBConfig componentKBConfig = new ComponentKBConfig();
        List<ComponentKBConfig> componentConfigs = new ArrayList<>(List.of(componentKBConfig));
        Map<String, Object> connectionConfig = new java.util.LinkedHashMap<>(Map.of("chroma_path", "path1"));

        KnowledgeRetrievalCompConfig config = new KnowledgeRetrievalCompConfig();
        config.setComponentKbConfigs(componentConfigs);
        config.setVectorStoreConnectionConfig(connectionConfig);
        componentConfigs.clear();
        connectionConfig.put("chroma_path", "path2");

        assertEquals(1, config.getComponentKbConfigs().size());
        assertEquals("path1", config.getVectorStoreConnectionConfig().get("chroma_path"));
    }

    @Test
    void componentKbConfigCopiesEmbedAdditionalConfig() {
        ComponentKBConfig config = new ComponentKBConfig();
        Map<String, Object> additional = new java.util.LinkedHashMap<>(Map.of("timeout", 1));

        config.setEmbedAdditionalConfig(additional);
        additional.put("timeout", 2);

        assertEquals(1, config.getEmbedAdditionalConfig().get("timeout"));
    }

    private KnowledgeRetrievalCompConfig baseConfig() {
        KnowledgeRetrievalCompConfig config = new KnowledgeRetrievalCompConfig();
        config.setRetrievalConfig(RetrievalConfig.builder().topK(3).build());
        return config;
    }

    private KnowledgeRetrievalCompConfig configWithKbCount(
            String indexType,
            int kbCount,
            boolean useGraph,
            boolean includeEmbedConfig
    ) {
        KnowledgeRetrievalCompConfig config = baseConfig();
        config.getRetrievalConfig().setUseGraph(useGraph);
        config.setVectorStoreConnectionConfig(Map.of("chroma_path", tempDir.toString()));
        List<ComponentKBConfig> kbConfigs = new ArrayList<>();
        for (int index = 1; index <= kbCount; index++) {
            ComponentKBConfig componentKBConfig = new ComponentKBConfig();
            componentKBConfig.setKbConfig(KnowledgeBaseConfig.builder()
                    .kbId("kb" + index)
                    .indexType(indexType)
                    .build());
            componentKBConfig.setVectorStoreConfig(VectorStoreConfig.builder()
                    .storeProvider(StoreType.CHROMA)
                    .collectionName("collection" + index)
                    .distanceMetric("cosine")
                    .build());
            if (includeEmbedConfig) {
                componentKBConfig.setEmbedConfig(EmbeddingConfig.builder()
                        .modelName("text-embedding-test")
                        .baseUrl("http://fake-embed.invalid")
                        .apiKey("fake-key")
                        .build());
            }
            kbConfigs.add(componentKBConfig);
        }
        config.setComponentKbConfigs(kbConfigs);
        return config;
    }

    private static RetrievalResult result(String text, double score) {
        return new RetrievalResult(text, score, Map.of("source", "test"), "doc", "chunk");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invokePrivate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static final class FakeKnowledgeBase extends KnowledgeBase {

        private final List<RetrievalResult> retrievalResults;

        private FakeKnowledgeBase(String kbId, List<RetrievalResult> retrievalResults) {
            super(KnowledgeBaseConfig.builder().kbId(kbId).indexType("bm25").build());
            this.retrievalResults = retrievalResults;
        }

        @Override
        public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> retrieve(
                String query,
                RetrievalConfig config,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(retrievalResults);
        }

        @Override
        public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class FailingKnowledgeBase extends KnowledgeBase {

        private FailingKnowledgeBase(String kbId) {
            super(KnowledgeBaseConfig.builder().kbId(kbId).indexType("bm25").build());
        }

        @Override
        public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> retrieve(
                String query,
                RetrievalConfig config,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.failedFuture(new IllegalStateException("connection timeout"));
        }

        @Override
        public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
            return CompletableFuture.completedFuture(Map.of());
        }
    }

    private static final class RecordingGraph extends Graph {
        private String nodeId;
        private Executable<?, ?> node;
        private boolean waitForAll;

        @Override
        public Graph addNode(String nodeId, Executable<?, ?> node, boolean waitForAll) {
            this.nodeId = nodeId;
            this.node = node;
            this.waitForAll = waitForAll;
            return this;
        }
    }

    private static final class TestSession extends BaseSession {
        @Override
        public String sessionId() {
            return "test_session";
        }

        public String getComponentId() {
            return "kr_test";
        }

        public String getExecutableId() {
            return "kr_test";
        }
    }
}
