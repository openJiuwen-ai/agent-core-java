/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Showcase Multimodal Embedding Example.
 *
 * Mirrors Python's {@code showcase_multimodal_embedding} in
 * {@code examples.retrieval.showcase_multimodal_embedding}.
 */
public final class ShowcaseMultimodalEmbeddingExample {

    public static final String REFERENCE_TEXT = "A photograph of a person";
    public static final String DIFFERENT_TEXT = "Picture of an octopus in ocean";
    public static final Path REF_IMAGE = Path.of("reference.jpg");
    public static final Path SAME_CONTENT_DIFFERENT_IMAGE = Path.of("reference.ppm");
    public static final Path DIFFERENT_IMAGE = Path.of("different.ppm");
    public static final int EMBEDDING_DIM = 128;

    private static final double IMAGE_WEIGHT = 0.75d;
    private static final double TEXT_WEIGHT = 0.25d;

    private ShowcaseMultimodalEmbeddingExample() {
    }

    public static EmbeddingConfig sampleConfig() {
        return new EmbeddingConfig(
                "multimodal-embedding-model",
                "https://multimodal.example/v1",
                "multimodal-key"
        );
    }

    /**
     * Create the same four multimodal document combinations as the Python script.
     */
    public static List<ExampleDocument> createDocuments() {
        return List.of(
                new ExampleDocument("doc1", REFERENCE_TEXT, REF_IMAGE, "REF_IMAGE + REF_TEXT"),
                new ExampleDocument("doc2", REFERENCE_TEXT, SAME_CONTENT_DIFFERENT_IMAGE,
                        "SAME_CONTENT_DIFFERENT_IMAGE + REF_TEXT"),
                new ExampleDocument("doc3", REFERENCE_TEXT, DIFFERENT_IMAGE, "DIFFERENT_IMAGE + REF_TEXT"),
                new ExampleDocument("doc4", DIFFERENT_TEXT, REF_IMAGE, "REF_IMAGE + DIFFERENT_TEXT")
        );
    }

    public static DemoReport runExample() {
        return runExample(sampleConfig(), new DeterministicVllmEmbeddingClient(EMBEDDING_DIM));
    }

    public static DemoReport runExample(EmbeddingConfig config, MultimodalEmbeddingClient embeddingClient) {
        List<ExampleDocument> docs = createDocuments();
        List<List<Double>> embeddings = embeddingClient.embedDocuments(docs);
        Map<String, SimilarityReport> comparisons = Map.of(
                "doc1_vs_doc2", compare("doc1_vs_doc2", embeddings.get(0), embeddings.get(1)),
                "doc1_vs_doc3", compare("doc1_vs_doc3", embeddings.get(0), embeddings.get(2)),
                "doc2_vs_doc3", compare("doc2_vs_doc3", embeddings.get(1), embeddings.get(2)),
                "doc1_vs_doc4", compare("doc1_vs_doc4", embeddings.get(0), embeddings.get(3)),
                "doc4_vs_doc2", compare("doc4_vs_doc2", embeddings.get(3), embeddings.get(1)),
                "doc4_vs_doc3", compare("doc4_vs_doc3", embeddings.get(3), embeddings.get(2))
        );
        AnalysisReport analysis = analyze(comparisons);
        return new DemoReport(
                config.getModelName(),
                embeddingClient.dimension(),
                docs,
                embeddings,
                comparisons,
                analysis
        );
    }

    public static SimilarityReport compare(String label, List<Double> left, List<Double> right) {
        return new SimilarityReport(label, cosineSimilarity(left, right), euclideanDistance(left, right));
    }

    public static AnalysisReport analyze(Map<String, SimilarityReport> comparisons) {
        SimilarityReport doc1Doc2 = comparisons.get("doc1_vs_doc2");
        SimilarityReport doc1Doc3 = comparisons.get("doc1_vs_doc3");
        SimilarityReport doc2Doc3 = comparisons.get("doc2_vs_doc3");
        SimilarityReport doc1Doc4 = comparisons.get("doc1_vs_doc4");

        double maxDifferentImageSimilarity = Math.max(doc1Doc3.cosineSimilarity(), doc2Doc3.cosineSimilarity());
        boolean sameImageMoreSimilar = doc1Doc2.cosineSimilarity() > doc1Doc3.cosineSimilarity()
                && doc1Doc2.cosineSimilarity() > doc2Doc3.cosineSimilarity();
        boolean differentImagesSignificant = doc1Doc3.cosineSimilarity() < 0.9d
                && doc2Doc3.cosineSimilarity() < 0.9d;
        boolean sameImageDistanceSmaller = doc1Doc2.euclideanDistance() < doc1Doc3.euclideanDistance()
                && doc1Doc2.euclideanDistance() < doc2Doc3.euclideanDistance();
        boolean sameImageDifferentTextMoreSimilar = doc1Doc4.cosineSimilarity() > doc1Doc3.cosineSimilarity()
                && doc1Doc4.cosineSimilarity() > doc2Doc3.cosineSimilarity();
        boolean sameImageSameTextMoreSimilar = doc1Doc2.cosineSimilarity() > doc1Doc4.cosineSimilarity();
        double textInfluenceRatio = doc1Doc4.cosineSimilarity() / doc1Doc2.cosineSimilarity();
        double imageDifferenceRatio = maxDifferentImageSimilarity / doc1Doc2.cosineSimilarity();
        return new AnalysisReport(
                sameImageMoreSimilar,
                differentImagesSignificant,
                sameImageDistanceSmaller,
                sameImageDifferentTextMoreSimilar,
                sameImageSameTextMoreSimilar,
                textInfluenceRatio,
                imageDifferenceRatio
        );
    }

    public static double cosineSimilarity(List<Double> left, List<Double> right) {
        validateSameDimension(left, right);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public static double euclideanDistance(List<Double> left, List<Double> right) {
        validateSameDimension(left, right);
        double sum = 0.0d;
        for (int i = 0; i < left.size(); i++) {
            double diff = left.get(i) - right.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    public static void main(String[] args) {
        DemoReport report = runExample();
        System.out.println("Generating embeddings...");
        System.out.println("Embedding dimensions: " + report.embeddingDimension());
        report.comparisons().forEach((label, comparison) -> {
            System.out.println();
            System.out.println(label + ":");
            System.out.printf("  Cosine similarity: %.4f%n", comparison.cosineSimilarity());
            System.out.printf("  Euclidean distance: %.4f%n", comparison.euclideanDistance());
        });
        System.out.println("Analysis:");
        System.out.println("Same image more similar: " + report.analysis().sameImageMoreSimilar());
        System.out.println("Different images significantly different: "
                + report.analysis().differentImagesSignificant());
        System.out.println("Same image has smaller distance: " + report.analysis().sameImageDistanceSmaller());
        System.out.println("Same image and same text more similar than same image with different text: "
                + report.analysis().sameImageSameTextMoreSimilar());
    }

    private static void validateSameDimension(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size()) {
            throw new IllegalArgumentException("vectors must have the same dimension");
        }
    }

    public interface MultimodalEmbeddingClient {
        int dimension();

        List<List<Double>> embedDocuments(List<ExampleDocument> documents);
    }

    /**
     * Offline VLLM-style client where reference.jpg and reference.ppm share image content.
     */
    public static final class DeterministicVllmEmbeddingClient implements MultimodalEmbeddingClient {
        private final int dimension;

        public DeterministicVllmEmbeddingClient(int dimension) {
            if (dimension < 4) {
                throw new IllegalArgumentException("dimension must be at least 4");
            }
            this.dimension = dimension;
        }

        @Override
        public int dimension() {
            return dimension;
        }

        @Override
        public List<List<Double>> embedDocuments(List<ExampleDocument> documents) {
            List<List<Double>> embeddings = new ArrayList<>();
            for (ExampleDocument document : documents) {
                embeddings.add(embed(document));
            }
            return embeddings;
        }

        private List<Double> embed(ExampleDocument document) {
            List<Double> vector = new ArrayList<>(java.util.Collections.nCopies(dimension, 0.0d));
            vector.set(imageIndex(document.imagePath()), IMAGE_WEIGHT);
            vector.set(textIndex(document.text()), TEXT_WEIGHT);
            return vector;
        }

        private static int imageIndex(Path imagePath) {
            String fileName = imagePath.getFileName() == null ? "" : imagePath.getFileName().toString();
            return fileName.startsWith("reference.") ? 0 : 1;
        }

        private static int textIndex(String text) {
            return REFERENCE_TEXT.equals(text) ? 2 : 3;
        }
    }

    public record ExampleDocument(String id, String text, Path imagePath, String label) {
    }

    public record SimilarityReport(String label, double cosineSimilarity, double euclideanDistance) {
    }

    public record AnalysisReport(
            boolean sameImageMoreSimilar,
            boolean differentImagesSignificant,
            boolean sameImageDistanceSmaller,
            boolean sameImageDifferentTextMoreSimilar,
            boolean sameImageSameTextMoreSimilar,
            double textInfluenceRatio,
            double imageDifferenceRatio
    ) {
    }

    public record DemoReport(
            String modelName,
            int embeddingDimension,
            List<ExampleDocument> documents,
            List<List<Double>> embeddings,
            Map<String, SimilarityReport> comparisons,
            AnalysisReport analysis
    ) {
    }
}
