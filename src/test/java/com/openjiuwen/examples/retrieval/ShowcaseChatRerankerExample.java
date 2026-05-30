/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.retrieval.common.RerankerConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase Chat Reranker Example.
 *
 * Mirrors Python's {@code showcase_chat_reranker} in
 * {@code examples.retrieval.showcase_chat_reranker}.
 */
public final class ShowcaseChatRerankerExample {

    public static final String QUERY = "Hello";
    public static final List<String> DOCUMENTS = List.of("Hi", "Aloha", "bonjour");
    public static final String INSTRUCTION = "greeting in french";

    private ShowcaseChatRerankerExample() {
    }

    /**
     * Build a chat reranker config equivalent to the Python example's CHAT_RERANKER_CONFIG.
     */
    public static RerankerConfig sampleConfig() {
        RerankerConfig config = new RerankerConfig();
        config.setModelName("chat-reranker-model");
        config.setApiBase("https://chat-reranker.example/v1");
        config.setApiKey("chat-reranker-key");
        config.setYesNoIds(List.of(1, 0));
        return config;
    }

    /**
     * Run the showcase with the deterministic offline client.
     */
    public static DemoReport runExample() {
        return runExample(sampleConfig(), new DeterministicChatRerankerClient());
    }

    public static DemoReport runExample(RerankerConfig config, ChatRerankerClient chatReranker) {
        boolean compatible = chatReranker.testCompatibility();
        Map<String, Double> defaultScores = rerankEachDocument(chatReranker, QUERY, DOCUMENTS, Boolean.TRUE);
        Map<String, Double> customScores = rerankEachDocument(chatReranker, QUERY, DOCUMENTS, INSTRUCTION);
        List<ComparisonRow> comparisons = compareResults(DOCUMENTS, defaultScores, customScores);
        return new DemoReport(
                QUERY,
                DOCUMENTS,
                INSTRUCTION,
                config.getModelName(),
                compatible,
                defaultScores,
                customScores,
                comparisons
        );
    }

    public static Map<String, Double> rerankEachDocument(
            ChatRerankerClient chatReranker,
            String query,
            List<String> documents,
            Object instruct
    ) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String doc : documents) {
            Map<String, Double> result = chatReranker.rerank(query, List.of(doc), instruct);
            scores.put(doc, result.getOrDefault(doc, 0.0d));
        }
        return scores;
    }

    public static List<ComparisonRow> compareResults(
            List<String> documents,
            Map<String, Double> defaultScores,
            Map<String, Double> customScores
    ) {
        return documents.stream()
                .map(doc -> {
                    double defaultScore = defaultScores.getOrDefault(doc, 0.0d);
                    double customScore = customScores.getOrDefault(doc, 0.0d);
                    return new ComparisonRow(doc, defaultScore, customScore, customScore - defaultScore);
                })
                .toList();
    }

    public static void main(String[] args) {
        DemoReport report = runExample();
        System.out.printf("Query: %s%n", report.query());
        System.out.printf("Documents: %s%n", report.documents());
        System.out.printf("Model: %s%n%n", report.modelName());
        System.out.println("Testing compatibility...");
        System.out.printf("Compatible: %s%n%n", report.compatible());
        System.out.println("Reranking with default instruction:");
        report.defaultScores().forEach((doc, score) -> System.out.printf("  %7s: %.4f%n", doc, score));
        System.out.println();
        System.out.printf("Reranking with custom instruction='%s':%n", report.customInstruction());
        report.customScores().forEach((doc, score) -> System.out.printf("  %7s: %.4f%n", doc, score));
        System.out.println();
        System.out.println("Comparison:");
        for (ComparisonRow row : report.comparisons()) {
            System.out.printf(
                    "%7s: default=%.4f, custom=%.4f, diff=%.4f%n",
                    row.document(),
                    row.defaultScore(),
                    row.customScore(),
                    row.diff()
            );
        }
    }

    /**
     * Minimal client seam for the Python example's ChatReranker calls.
     */
    public interface ChatRerankerClient {
        boolean testCompatibility();

        Map<String, Double> rerank(String query, List<String> doc, Object instruct);
    }

    /**
     * Offline client for tests and examples that do not have chat-completion credentials.
     */
    public static final class DeterministicChatRerankerClient implements ChatRerankerClient {

        @Override
        public boolean testCompatibility() {
            try {
                rerank("test", List.of("test"), Boolean.FALSE);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        }

        @Override
        public Map<String, Double> rerank(String query, List<String> doc, Object instruct) {
            if (doc == null || doc.size() != 1) {
                throw new IllegalArgumentException("input to chat reranker must be a list of size 1");
            }
            String text = doc.get(0);
            return Map.of(text, score(query, text, instruct));
        }

        private static double score(String query, String document, Object instruct) {
            String normalizedDoc = document == null ? "" : document.toLowerCase();
            String normalizedInstruction = instruct instanceof String text ? text.toLowerCase() : "";
            if (normalizedInstruction.contains("french")) {
                if (normalizedDoc.contains("bonjour")) {
                    return 0.94d;
                }
                if (normalizedDoc.contains("hi")) {
                    return 0.38d;
                }
                if (normalizedDoc.contains("aloha")) {
                    return 0.30d;
                }
            }
            if (normalizedDoc.contains("hi")) {
                return 0.90d;
            }
            if (normalizedDoc.contains("aloha")) {
                return 0.72d;
            }
            if (normalizedDoc.contains("bonjour")) {
                return "hello".equalsIgnoreCase(query) ? 0.24d : 0.18d;
            }
            return 0.05d;
        }
    }

    public record ComparisonRow(String document, double defaultScore, double customScore, double diff) {
    }

    public record DemoReport(
            String query,
            List<String> documents,
            String customInstruction,
            String modelName,
            boolean compatible,
            Map<String, Double> defaultScores,
            Map<String, Double> customScores,
            List<ComparisonRow> comparisons
    ) {
    }
}
