/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.retrieval;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding;

import java.nio.file.Path;
import java.util.List;

/**
 * Java counterpart of the Python DashScope multimodal embedding showcase.
 */
public final class DashscopeMultimodalEmbeddingExample {

    private static final String REFERENCE_TEXT = "A photograph of a person";
    private static final String DIFFERENT_TEXT = "Picture of an octopus in ocean";
    private static final String DIFFERENT_IMAGE = "https://openjiuwen.com/img/jiuwen_logo.png";

    private DashscopeMultimodalEmbeddingExample() {
    }

    public static void main(String[] args) {
        Integer dimension = resolveDimension();
        EmbeddingConfig config = dashscopeEmbeddingConfig();
        Path referenceImage = RetrievalExampleSupport.resolvePathConfig(
                "DASHSCOPE_REFERENCE_IMAGE",
                Path.of("reference.jpg"));

        MultimodalDocument doc1 = new MultimodalDocument()
                .addField("text", REFERENCE_TEXT)
                .addField("image", referenceImage);
        MultimodalDocument doc2 = new MultimodalDocument()
                .addField("text", REFERENCE_TEXT)
                .addField("image", DIFFERENT_IMAGE);
        MultimodalDocument doc3 = new MultimodalDocument()
                .addField("text", DIFFERENT_TEXT)
                .addField("image", DIFFERENT_IMAGE);

        try (DashscopeEmbedding model = new DashscopeEmbedding(config, 30, 3, null, 8, 50, dimension, null)) {
            ExampleOutput.section("DashScope Multimodal Embedding Example");
            ExampleOutput.keyValue("DashScope model", config.getModelName());
            ExampleOutput.keyValue("Reference image", referenceImage.toAbsolutePath().normalize());

            List<List<Float>> embeddings = model.embedDocuments(List.of(doc1, doc2, doc3), null, null);
            List<Float> emb1 = embeddings.get(0);
            List<Float> emb2 = embeddings.get(1);
            List<Float> emb3 = embeddings.get(2);
            ExampleOutput.keyValue("Embedding dimensions", emb1.size());

            double sim12 = VectorSimilarityUtils.cosineSimilarity(emb1, emb2);
            double dist12 = VectorSimilarityUtils.euclideanDistance(emb1, emb2);
            double sim23 = VectorSimilarityUtils.cosineSimilarity(emb2, emb3);
            double dist23 = VectorSimilarityUtils.euclideanDistance(emb2, emb3);
            double sim13 = VectorSimilarityUtils.cosineSimilarity(emb1, emb3);
            double dist13 = VectorSimilarityUtils.euclideanDistance(emb1, emb3);

            ExampleOutput.subsection("doc1 (REF_IMAGE) vs doc2 (DIFFERENT_IMAGE)");
            printPair(sim12, dist12);
            ExampleOutput.subsection("doc2 (DIFFERENT_IMAGE + REF_TEXT) vs doc3 (DIFFERENT_IMAGE + DIFFERENT_TEXT)");
            printPair(sim23, dist23);
            ExampleOutput.subsection("doc1 (REF_IMAGE + REF_TEXT) vs doc3 (DIFFERENT_IMAGE + DIFFERENT_TEXT)");
            printPair(sim13, dist13);

            ExampleOutput.subsection("Analysis");
            ExampleOutput.line("Images significantly affect embeddings: %s", sim23 > sim12);
            ExampleOutput.line("Same image, different text similarity: %.4f", sim23);
            ExampleOutput.line("Different image and text similarity: %.4f", sim13);
        }
    }

    private static EmbeddingConfig dashscopeEmbeddingConfig() {
        EmbeddingConfig fallback = new EmbeddingConfig(
                "qwen3-vl-embedding",
                "https://dashscope.aliyuncs.com/api/v1/",
                RetrievalExampleSupport.resolveStringConfig("DASHSCOPE_API_KEY", ""));
        return new EmbeddingConfig(
                RetrievalExampleSupport.resolveStringConfig("DASHSCOPE_EMBEDDING_MODEL", fallback.getModelName()),
                RetrievalExampleSupport.resolveStringConfig("DASHSCOPE_EMBEDDING_API_BASE", fallback.getBaseUrl()),
                RetrievalExampleSupport.resolveStringConfig("DASHSCOPE_EMBEDDING_API_KEY", fallback.getApiKey()));
    }

    private static Integer resolveDimension() {
        int configured = RetrievalExampleSupport.resolveIntConfig("DASHSCOPE_EMBEDDING_DIM", 256);
        return configured <= 0 ? null : configured;
    }

    private static void printPair(double similarity, double distance) {
        ExampleOutput.line("  Cosine similarity: %.4f", similarity);
        ExampleOutput.line("  Euclidean distance: %.4f", distance);
    }
}
