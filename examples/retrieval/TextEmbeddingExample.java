/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.retrieval;

import com.openjiuwen.core.retrieval.embedding.VLLMEmbedding;

import java.util.List;

/**
 * Java counterpart of the Python text embedding showcase.
 */
public final class TextEmbeddingExample {

    private static final String DOCUMENT_1 = """
            Modern ray tracing technology has revolutionized computer graphics rendering.
            Physically-based rendering uses bidirectional reflectance distribution functions
            (BRDF) to accurately simulate how light interacts with surfaces, accounting for
            diffuse and specular reflections. Advanced aperture simulation techniques model
            camera depth of field and bokeh effects by tracing multiple rays through virtual
            lens apertures. These techniques produce photorealistic images by simulating
            the physical behavior of light. Bounding Volume Hierarchy (BVH) trees organize
            geometry in axis-aligned bounding boxes, enabling efficient ray-scene intersection
            tests and real-time rendering of complex scenes with accurate shadows, reflections,
            and global illumination.
            """;

    private static final String DOCUMENT_2 = """
            现代光线追踪技术已经彻底改变了计算机图形渲染。基于物理的渲染使用双向
            反射分布函数（BRDF）来精确模拟光线与表面的交互，考虑漫反射和镜面反射。
            先进的光圈模拟技术通过追踪穿过虚拟镜头光圈的多条光线来模拟相机景深和
            散景效果。这些技术通过模拟光的物理行为来生成逼真的图像。包围盒层次结构
            （BVH）树将几何体组织在轴对齐的包围盒中，实现高效的射线-场景相交测试，
            能够实时渲染具有精确阴影、反射和全局光照的复杂场景。
            """;

    private static final String DOCUMENT_3 = """
            Doom's revolutionary graphics engine, released in 1993, used ray casting to
            create pseudo-3D environments from 2D maps. The engine employed binary space
            partitioning (BSP) trees to efficiently determine which walls and surfaces
            were visible from the player's viewpoint. This technique enabled smooth gameplay
            on hardware of the era by avoiding true 3D polygon rendering, instead using
            pre-calculated visibility data stored in the BSP tree structure for efficient
            ray-scene intersection tests, similar to Bounding Volume Hierarchy (BVH).
            """;

    private TextEmbeddingExample() {
    }

    public static void main(String[] args) {
        Integer dimension = resolveDimension();
        ExampleOutput.section("Text Embedding Example");
        ExampleOutput.keyValue("Embedding model", RetrievalExampleSupport.embeddingConfig().getModelName());
        ExampleOutput.keyValue("Embedding endpoint", RetrievalExampleSupport.embeddingConfig().getBaseUrl());
        ExampleOutput.keyValue("Embedding dimension", dimension == null ? "provider default" : dimension);

        try (VLLMEmbedding embedding = new VLLMEmbedding(
                RetrievalExampleSupport.embeddingConfig(),
                10,
                3,
                null,
                8,
                50,
                dimension,
                null)) {
            ExampleOutput.subsection("Generating embeddings");
            List<List<Float>> vectors = embedding.embedDocuments(List.of(DOCUMENT_1, DOCUMENT_2, DOCUMENT_3), null);
            List<Float> emb1 = vectors.get(0);
            List<Float> emb2 = vectors.get(1);
            List<Float> emb3 = vectors.get(2);
            ExampleOutput.keyValue("Returned vector length", emb1.size());

            double sim12 = VectorSimilarityUtils.cosineSimilarity(emb1, emb2);
            double sim13 = VectorSimilarityUtils.cosineSimilarity(emb1, emb3);
            double sim23 = VectorSimilarityUtils.cosineSimilarity(emb2, emb3);
            double dist12 = VectorSimilarityUtils.euclideanDistance(emb1, emb2);
            double dist13 = VectorSimilarityUtils.euclideanDistance(emb1, emb3);
            double dist23 = VectorSimilarityUtils.euclideanDistance(emb2, emb3);

            ExampleOutput.subsection("Similarity comparison");
            printPair("English ray tracing vs Chinese ray tracing", sim12, dist12);
            printPair("English ray tracing vs Doom ray casting", sim13, dist13);
            printPair("Chinese ray tracing vs Doom ray casting", sim23, dist23);

            ExampleOutput.subsection("Analysis");
            if (sim12 > sim13 && sim12 > sim23) {
                ExampleOutput.line("PASS: Cross-language same-topic similarity is the strongest signal.");
            } else {
                ExampleOutput.line("WARNING: Cross-language similarity was not the highest result.");
            }
            if (dist12 < dist13 && dist12 < dist23) {
                ExampleOutput.line("PASS: The same-topic pair also has the smallest Euclidean distance.");
            } else {
                ExampleOutput.line("WARNING: Euclidean distance did not rank the same-topic pair first.");
            }
        }
    }

    private static Integer resolveDimension() {
        int configured = RetrievalExampleSupport.resolveIntConfig(
                "EMBEDDING_DIM",
                RetrievalExampleSupport.DEFAULT_EMBEDDING_DIMENSION);
        return configured <= 0 ? null : configured;
    }

    private static void printPair(String label, double similarity, double distance) {
        ExampleOutput.line(label + ":");
        ExampleOutput.line("  cosine similarity   %.4f", similarity);
        ExampleOutput.line("  euclidean distance  %.4f", distance);
    }
}