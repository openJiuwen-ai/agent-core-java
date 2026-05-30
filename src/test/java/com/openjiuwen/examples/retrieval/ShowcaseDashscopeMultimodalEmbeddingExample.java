/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Showcase Dashscope Multimodal Embedding Example.
 *
 * Mirrors Python's {@code showcase_dashscope_multimodal_embedding} in
 * {@code examples.retrieval.showcase_dashscope_multimodal_embedding}.
 */
public final class ShowcaseDashscopeMultimodalEmbeddingExample {

    public static final String REFERENCE_TEXT = "A photograph of a person";
    public static final String DIFFERENT_TEXT = "Picture of an octopus in ocean";
    public static final Path LOCAL_REF_IMAGE = Path.of("reference.jpg");
    public static final String DIFFERENT_IMAGE = "https://openjiuwen.com/img/jiuwen_logo.png";
    public static final int EMBEDDING_DIM = 256;

    private static final double IMAGE_WEIGHT = 0.75d;
    private static final double TEXT_WEIGHT = 0.25d;

    private ShowcaseDashscopeMultimodalEmbeddingExample() {
    }

    /**
     * Build the Dashscope config used by the Python example.
     */
    public static EmbeddingConfig sampleConfig(String dashscopeApiKey) {
        return new EmbeddingConfig(
                "qwen3-vl-embedding",
                "https://dashscope.aliyuncs.com/api/v1/",
                dashscopeApiKey
        );
    }

    /**
     * Create the same three multimodal document combinations as the Python script.
     */
    public static List<ExampleDocument> createDocuments() {
        return List.of(
                new ExampleDocument(
                        "doc1",
                        REFERENCE_TEXT,
                        ImageInput.filePath(LOCAL_REF_IMAGE),
                        "REF_IMAGE + REF_TEXT"
                ),
                new ExampleDocument(
                        "doc2",
                        REFERENCE_TEXT,
                        ImageInput.data(DIFFERENT_IMAGE),
                        "DIFFERENT_IMAGE + REF_TEXT"
                ),
                new ExampleDocument(
                        "doc3",
                        DIFFERENT_TEXT,
                        ImageInput.data(DIFFERENT_IMAGE),
                        "DIFFERENT_IMAGE + DIFFERENT_TEXT"
                )
        );
    }

    /**
     * Run the showcase with a deterministic offline Dashscope embedding client.
     */
    public static DemoReport runExample() {
        return runExample(sampleConfig("dashscope-key"), new DeterministicDashscopeEmbeddingClient(EMBEDDING_DIM));
    }

    public static DemoReport runExample(EmbeddingConfig config, MultimodalEmbeddingClient embeddingClient) {
        List<ExampleDocument> docs = createDocuments();
        List<List<Double>> embeddings = embeddingClient.embedDocuments(docs);
        SimilarityReport doc1Doc2 = compare("doc1_vs_doc2", embeddings.get(0), embeddings.get(1));
        SimilarityReport doc2Doc3 = compare("doc2_vs_doc3", embeddings.get(1), embeddings.get(2));
        SimilarityReport doc1Doc3 = compare("doc1_vs_doc3", embeddings.get(0), embeddings.get(2));
        AnalysisReport analysis = analyze(doc1Doc2, doc2Doc3, doc1Doc3);
        return new DemoReport(
                config.getModelName(),
                config.getBaseUrl(),
                embeddingClient.dimension(),
                docs,
                embeddings,
                doc1Doc2,
                doc2Doc3,
                doc1Doc3,
                analysis
        );
    }

    public static SimilarityReport compare(String label, List<Double> left, List<Double> right) {
        return new SimilarityReport(label, cosineSimilarity(left, right), euclideanDistance(left, right));
    }

    public static AnalysisReport analyze(
            SimilarityReport doc1Doc2,
            SimilarityReport doc2Doc3,
            SimilarityReport doc1Doc3
    ) {
        double maxDifferentImageSimilarity = Math.max(doc1Doc3.cosineSimilarity(), doc1Doc2.cosineSimilarity());
        boolean differentImagesSignificant = maxDifferentImageSimilarity < 0.9d;
        boolean sameImageDistanceSmaller = doc2Doc3.euclideanDistance() < doc1Doc3.euclideanDistance();
        boolean sameImageDifferentTextMoreSimilar = doc2Doc3.cosineSimilarity() > doc1Doc2.cosineSimilarity();
        return new AnalysisReport(
                differentImagesSignificant,
                sameImageDistanceSmaller,
                sameImageDifferentTextMoreSimilar,
                maxDifferentImageSimilarity
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
        printComparison("doc1 (REF_IMAGE) vs doc2 (DIFFERENT_IMAGE)", report.doc1Doc2());
        printComparison("doc2 (DIFFERENT_IMAGE + REF_TEXT) vs doc3 (DIFFERENT_IMAGE + DIFFERENT_TEXT)",
                report.doc2Doc3());
        printComparison("doc1 (REF_IMAGE + REF_TEXT) vs doc3 (DIFFERENT_IMAGE + DIFFERENT_TEXT)",
                report.doc1Doc3());
        System.out.println("Analysis:");
        System.out.println("Images significantly affect embeddings: "
                + report.analysis().differentImagesSignificant());
        System.out.println("Same image, different text has smaller distance: "
                + report.analysis().sameImageDistanceSmaller());
        System.out.println("Same image, different text is more similar than different image and same text: "
                + report.analysis().sameImageDifferentTextMoreSimilar());
    }

    private static void printComparison(String title, SimilarityReport report) {
        System.out.println();
        System.out.println(title + ":");
        System.out.printf("  Cosine similarity: %.4f%n", report.cosineSimilarity());
        System.out.printf("  Euclidean distance: %.4f%n", report.euclideanDistance());
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
     * Offline embedding client that makes image differences dominate text differences.
     */
    public static final class DeterministicDashscopeEmbeddingClient implements MultimodalEmbeddingClient {
        private final int dimension;

        public DeterministicDashscopeEmbeddingClient(int dimension) {
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
            for (ExampleDocument doc : documents) {
                embeddings.add(embed(doc));
            }
            return embeddings;
        }

        private List<Double> embed(ExampleDocument doc) {
            List<Double> vector = new ArrayList<>(java.util.Collections.nCopies(dimension, 0.0d));
            vector.set(imageIndex(doc.image()), IMAGE_WEIGHT);
            vector.set(textIndex(doc.text()), TEXT_WEIGHT);
            return vector;
        }

        private static int imageIndex(ImageInput image) {
            return image.isLocalFile() ? 0 : 1;
        }

        private static int textIndex(String text) {
            return REFERENCE_TEXT.equals(text) ? 2 : 3;
        }
    }

    public record ImageInput(String value, boolean localFile) {
        public static ImageInput filePath(Path path) {
            return new ImageInput(path.toString(), true);
        }

        public static ImageInput data(String data) {
            return new ImageInput(data, false);
        }

        public boolean isLocalFile() {
            return localFile;
        }
    }

    public record ExampleDocument(String id, String text, ImageInput image, String label) {
    }

    public record SimilarityReport(String label, double cosineSimilarity, double euclideanDistance) {
    }

    public record AnalysisReport(
            boolean differentImagesSignificant,
            boolean sameImageDistanceSmaller,
            boolean sameImageDifferentTextMoreSimilar,
            double maxDifferentImageSimilarity
    ) {
    }

    public record DemoReport(
            String modelName,
            String baseUrl,
            int embeddingDimension,
            List<ExampleDocument> documents,
            List<List<Double>> embeddings,
            SimilarityReport doc1Doc2,
            SimilarityReport doc2Doc3,
            SimilarityReport doc1Doc3,
            AnalysisReport analysis
    ) {
    }
}
