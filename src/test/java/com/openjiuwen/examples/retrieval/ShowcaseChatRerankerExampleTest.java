/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.retrieval.common.RerankerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseChatRerankerExampleTest {

    @Test
    void constantsAndConfigMatchPythonShowcaseInputs() {
        assertEquals("Hello", ShowcaseChatRerankerExample.QUERY);
        assertEquals(List.of("Hi", "Aloha", "bonjour"), ShowcaseChatRerankerExample.DOCUMENTS);
        assertEquals("greeting in french", ShowcaseChatRerankerExample.INSTRUCTION);

        RerankerConfig config = ShowcaseChatRerankerExample.sampleConfig();
        assertEquals("chat-reranker-model", config.getModelName());
        assertEquals("https://chat-reranker.example/v1", config.getApiBase());
        assertEquals("chat-reranker-key", config.getApiKey());
        assertEquals(List.of(1, 0), config.getYesNoIds());
    }

    @Test
    void runExamplePerformsCompatibilityDefaultCustomAndComparisonFlow() {
        ShowcaseChatRerankerExample.DemoReport report = ShowcaseChatRerankerExample.runExample();

        assertTrue(report.compatible());
        assertEquals("chat-reranker-model", report.modelName());
        assertEquals(ShowcaseChatRerankerExample.DOCUMENTS, report.documents());
        assertEquals(3, report.defaultScores().size());
        assertEquals(3, report.customScores().size());
        assertEquals(3, report.comparisons().size());
        assertTrue(report.defaultScores().get("Hi") > report.defaultScores().get("bonjour"));
        assertTrue(report.customScores().get("bonjour") > report.customScores().get("Hi"));
        assertTrue(report.comparisons().stream()
                .filter(row -> "bonjour".equals(row.document()))
                .findFirst()
                .orElseThrow()
                .diff() > 0.0d);
    }

    @Test
    void rerankEachDocumentCallsClientOncePerSingleDocumentLikePythonLoop() {
        CountingClient client = new CountingClient();

        Map<String, Double> scores = ShowcaseChatRerankerExample.rerankEachDocument(
                client,
                ShowcaseChatRerankerExample.QUERY,
                ShowcaseChatRerankerExample.DOCUMENTS,
                Boolean.TRUE
        );

        assertEquals(3, client.calls);
        assertEquals(ShowcaseChatRerankerExample.DOCUMENTS, client.seenDocs);
        assertEquals(Map.of("Hi", 0.1d, "Aloha", 0.2d, "bonjour", 0.3d), scores);
    }

    @Test
    void deterministicClientKeepsChatRerankerSingleDocumentConstraint() {
        ShowcaseChatRerankerExample.DeterministicChatRerankerClient client =
                new ShowcaseChatRerankerExample.DeterministicChatRerankerClient();

        assertThrows(IllegalArgumentException.class,
                () -> client.rerank("Hello", List.of("Hi", "bonjour"), Boolean.TRUE));
        assertThrows(IllegalArgumentException.class,
                () -> client.rerank("Hello", List.of(), Boolean.TRUE));
    }

    private static final class CountingClient implements ShowcaseChatRerankerExample.ChatRerankerClient {
        private int calls;
        private final java.util.ArrayList<String> seenDocs = new java.util.ArrayList<>();

        @Override
        public boolean testCompatibility() {
            return true;
        }

        @Override
        public Map<String, Double> rerank(String query, List<String> doc, Object instruct) {
            calls++;
            seenDocs.add(doc.get(0));
            return Map.of(doc.get(0), calls / 10.0d);
        }
    }
}
