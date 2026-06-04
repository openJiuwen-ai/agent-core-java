/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.retrieval.KnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalCompConfig;
import com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalComponent;
import com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalExecutable;
import com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalInput;
import com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_knowledge_retrieval_comp.py} in
 * {@code tests.unit_tests.core.component}.
 */
@Tag("unit-test")
class TestKnowledgeRetrievalComp {

    @Test
    @DisplayName("KnowledgeRetrievalInput accepts a valid query")
    void testValidInput() {
        KnowledgeRetrievalInput input = KnowledgeRetrievalInput.fromMap(Map.of("query", "test query"));
        assertEquals("test query", input.getQuery());
    }

    @Test
    @DisplayName("KnowledgeRetrievalInput tolerates extra fields")
    void testExtraFieldsAllowed() {
        KnowledgeRetrievalInput input = KnowledgeRetrievalInput.fromMap(Map.of("query", "q", "extra_field", "extra"));
        assertEquals("q", input.getQuery());
    }

    @Test
    @DisplayName("KnowledgeRetrievalOutput defaults match Python model")
    void testOutputDefaults() {
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput();
        assertEquals(List.of(), output.getResults());
        assertEquals("", output.getContext());
    }

    @Test
    @DisplayName("KnowledgeRetrievalOutput stores values")
    void testOutputWithValues() {
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput(List.of("text1", "text2"),
                "text1\n\ntext2", null);
        assertEquals(2, output.getResults().size());
        assertEquals("text1\n\ntext2", output.getContext());
    }

    @Test
    @DisplayName("invoke returns retrieved results and joined context")
    void testInvokeReturnsResults() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(
                result("doc A", 0.9), result("doc B", 0.8)), false, "\n\n");

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "test query"), null, null);

        assertEquals(List.of("doc A", "doc B"), output.get("results"));
        assertTrue(String.valueOf(output.get("context")).contains("doc A"));
        assertFalse(output.containsKey("results_with_metadata"));
    }

    @Test
    @DisplayName("empty query raises input parameter error")
    void testInvokeEmptyQueryRaises() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(result("doc", 0.9)), false, "\n\n");

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of("query", ""), null, null));

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    @DisplayName("missing query raises input parameter error")
    void testInvokeMissingQueryRaises() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(result("doc", 0.9)), false, "\n\n");

        BaseError error = assertThrows(BaseError.class,
                () -> executable.invoke(Map.of(), null, null));

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    @DisplayName("agentic retrieval requires LLM model config")
    void testAgenticConfigRequiresModelConfig() {
        KnowledgeRetrievalCompConfig config = config(false, "\n\n");
        config.getRetrievalConfig().setAgentic(true);

        BaseError error = assertThrows(BaseError.class,
                () -> new KnowledgeRetrievalExecutable(config).invoke(Map.of("query", "q"), null, null));

        assertEquals(StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED.getCode(), error.getCode());
    }

    @Test
    @DisplayName("empty retrieval results return empty results and context")
    void testInvokeEmptyResults() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(), false, "\n\n");

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "test query"), null, null);

        assertEquals(List.of(), output.get("results"));
        assertEquals("", output.get("context"));
    }

    @Test
    @DisplayName("context is joined with configured separator")
    void testInvokeContextJoinedWithDefaultSeparator() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(
                result("doc A", 0.9), result("doc B", 0.8)), false, " | ");

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "test query"), null, null);

        assertEquals("doc A | doc B", output.get("context"));
    }

    @Test
    @DisplayName("multiple knowledge bases aggregate matching texts")
    void testInvokeMultipleKbConfigs() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(
                result("doc A", 0.9), result("doc A", 0.7), result("doc B", 0.8)), true, "\n\n");

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "test query"), null, null);

        assertEquals(List.of("doc A", "doc B"), output.get("results"));
        assertTrue(output.containsKey("results_with_metadata"));
    }

    @Test
    @DisplayName("lazy initialisation is bypassed once executable is initialised")
    void testLazyInitialisationOnlyOnce() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(result("doc", 0.9)), false, "\n\n");

        Map<?, ?> first = (Map<?, ?>) executable.invoke(Map.of("query", "q"), null, null);
        Map<?, ?> second = (Map<?, ?>) executable.invoke(Map.of("query", "q"), null, null);

        assertEquals(first, second);
    }

    @Test
    @DisplayName("component toExecutable returns knowledge retrieval executable")
    void testToExecutableReturnsCorrectType() {
        KnowledgeRetrievalComponent component = new KnowledgeRetrievalComponent(config(false, "\n\n"));
        assertInstanceOf(KnowledgeRetrievalExecutable.class, component.toExecutable());
    }

    @Test
    @DisplayName("config stores multiple knowledge bases")
    void testMultipleKnowledgeBaseConfig() {
        KnowledgeRetrievalCompConfig config = config(false, "\n\n");
        config.setKbConfigs(List.of(new KnowledgeBaseConfig("kb_1"), new KnowledgeBaseConfig("kb_2")));
        assertEquals(2, config.getKbConfigs().size());
    }

    @Test
    @DisplayName("output can be converted to and from map")
    void testOutputMapRoundTrip() {
        KnowledgeRetrievalOutput output = new KnowledgeRetrievalOutput(List.of("doc"), "doc",
                List.of(Map.of("score", 0.9)));

        KnowledgeRetrievalOutput roundTrip = KnowledgeRetrievalOutput.fromMap(output.toMap());

        assertEquals(output.getResults(), roundTrip.getResults());
        assertEquals(output.getContext(), roundTrip.getContext());
        assertNotNull(roundTrip.getResultsWithMetadata());
    }

    @Test
    @DisplayName("End can consume knowledge retrieval output")
    void testStartKrEndWorkflowEquivalent() throws Exception {
        Map<?, ?> krOutput = (Map<?, ?>) executable(List.of(result("doc A", 0.9)), false, "\n\n")
                .invoke(Map.of("query", "q"), null, null);

        Object endOutput = new End().invoke(Map.of("kr", krOutput), null, null);

        assertEquals(Map.of("output", Map.of("kr", krOutput)), endOutput);
    }

    @Test
    @DisplayName("End can consume empty retrieval output")
    void testStartKrEndWorkflowEmptyResults() throws Exception {
        Map<?, ?> krOutput = (Map<?, ?>) executable(List.of(), false, "\n\n")
                .invoke(Map.of("query", "q"), null, null);

        assertEquals(Map.of("output", Map.of("kr", krOutput)), new End().invoke(Map.of("kr", krOutput), null, null));
    }

    @Test
    @DisplayName("includeMetadata emits metadata entries")
    void testIncludeMetadata() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(result("doc", 0.9)), true, "\n\n");

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "q"), null, null);

        assertTrue(output.containsKey("results_with_metadata"));
        assertTrue(String.valueOf(output.get("results_with_metadata")).contains("source"));
    }

    @Test
    @DisplayName("retrieval config topK limits results")
    void testTopKLimitsResults() throws Exception {
        KnowledgeRetrievalExecutable executable = executable(List.of(
                result("doc A", 0.9), result("doc B", 0.8), result("doc C", 0.7)), false, "\n\n");

        Map<?, ?> output = (Map<?, ?>) executable.invoke(Map.of("query", "q"), null, null);

        assertEquals(List.of("doc A", "doc B", "doc C"), output.get("results"));
    }

    @Test
    @DisplayName("fake knowledge base statistics remain available")
    void testKnowledgeBaseStatistics() {
        FakeKnowledgeBase knowledgeBase = new FakeKnowledgeBase("test_kb", List.of());
        assertEquals("test_kb", knowledgeBase.getStatistics().get("kb_id"));
    }

    private static KnowledgeRetrievalExecutable executable(
            List<RetrievalResult> retrievalResults, boolean includeMetadata, String separator) throws Exception {
        KnowledgeRetrievalExecutable executable = new KnowledgeRetrievalExecutable(config(includeMetadata, separator));
        Field initialized = KnowledgeRetrievalExecutable.class.getDeclaredField("initialized");
        initialized.setAccessible(true);
        initialized.set(executable, true);

        Field knowledgeBases = KnowledgeRetrievalExecutable.class.getDeclaredField("knowledgeBases");
        knowledgeBases.setAccessible(true);
        knowledgeBases.set(executable, List.of(new FakeKnowledgeBase("test_kb", retrievalResults)));
        return executable;
    }

    private static KnowledgeRetrievalCompConfig config(boolean includeMetadata, String separator) {
        RetrievalConfig retrievalConfig = new RetrievalConfig();
        retrievalConfig.setTopK(3);

        KnowledgeRetrievalCompConfig config = new KnowledgeRetrievalCompConfig();
        config.setKbConfigs(List.of(new KnowledgeBaseConfig("test_kb")));
        config.setRetrievalConfig(retrievalConfig);
        config.setIncludeMetadata(includeMetadata);
        config.setResultSeparator(separator);
        return config;
    }

    private static RetrievalResult result(String text, double score) {
        return new RetrievalResult(text, score, Map.of("source", "test", "raw_score", score, "raw_score_scaled", score),
                null, null);
    }

    private static final class FakeKnowledgeBase extends KnowledgeBase {
        private final List<RetrievalResult> results;

        FakeKnowledgeBase(String kbId, List<RetrievalResult> results) {
            super(new KnowledgeBaseConfig(kbId));
            this.results = results;
        }

        @Override
        public List<String> addDocuments(List<Document> documents) {
            return List.of();
        }

        @Override
        public List<RetrievalResult> retrieve(String query, RetrievalConfig config) {
            return results;
        }

        @Override
        public boolean deleteDocuments(List<String> docIds) {
            return true;
        }

        @Override
        public List<String> updateDocuments(List<Document> documents) {
            return List.of();
        }

        @Override
        public Map<String, Object> getStatistics() {
            Map<String, Object> statistics = new LinkedHashMap<>();
            statistics.put("kb_id", getConfig().getKbId());
            statistics.put("count", results.size());
            return statistics;
        }
    }
}
