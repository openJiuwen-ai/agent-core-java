/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package examples.retrieval;

import com.openjiuwen.core.retrieval.reranker.StandardReranker;

import java.util.List;
import java.util.Map;

/**
 * Java counterpart of the Python standard reranker showcase.
 */
public final class StandardRerankerExample {

    private static final String QUERY = "Hello";
    private static final List<String> DOCUMENTS = List.of("Hi", "Aloha", "bonjour");
    private static final String INSTRUCTION = "greeting in french";

    private StandardRerankerExample() {
    }

    public static void main(String[] args) {
        ExampleOutput.section("Standard Reranker Example");
        ExampleOutput.keyValue("Reranker model", RetrievalExampleSupport.rerankerConfig().getModelName());
        ExampleOutput.keyValue("Reranker endpoint", RetrievalExampleSupport.rerankerConfig().getApiBase());
        ExampleOutput.keyValue("Query", QUERY);
        ExampleOutput.printCollection("Documents", DOCUMENTS);

        StandardReranker reranker = new StandardReranker(RetrievalExampleSupport.rerankerConfig());

        Map<String, Double> defaultScores = reranker.rerankScores(QUERY, DOCUMENTS, Boolean.FALSE, Map.of());
        Map<String, Double> instructedScores = reranker.rerankScores(QUERY, DOCUMENTS, INSTRUCTION, Map.of());

        ExampleOutput.subsection("Without instruction");
        ExampleOutput.printScoredMap(defaultScores);

        ExampleOutput.subsection("With instruction: " + INSTRUCTION);
        ExampleOutput.printScoredMap(instructedScores);

        ExampleOutput.subsection("Top candidates");
        printTopDocument("Default", defaultScores);
        printTopDocument("Instruction", instructedScores);
    }

    private static void printTopDocument(String label, Map<String, Double> scores) {
        Map.Entry<String, Double> best = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        ExampleOutput.line("%s top document: %s (%.4f)", label, best.getKey(), best.getValue());
    }
}