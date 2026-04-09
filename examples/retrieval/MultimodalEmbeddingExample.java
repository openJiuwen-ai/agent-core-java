  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package examples.retrieval;

import com.openjiuwen.core.retrieval.common.MultimodalDocument;
import com.openjiuwen.core.retrieval.embedding.VLLMEmbedding;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Java counterpart of the Python multimodal embedding showcase.
 */
public final class MultimodalEmbeddingExample {

    private static final String REFERENCE_TEXT = "A photograph of a geometric icon with orange blocks";
    private static final String DIFFERENT_TEXT = "An abstract blue poster describing underwater creatures";

    private MultimodalEmbeddingExample() {
    }

    public static void main(String[] args) throws Exception {
        Integer dimension = resolveDimension();
        Path assetDirectory = RetrievalExampleSupport.resolvePathConfig(
                "MULTIMODAL_ASSET_DIR",
                Path.of("examples", "retrieval", "output", "multimodal_assets"));
        Files.createDirectories(assetDirectory);

        ImageVariants variants = createImageVariants(RetrievalExampleSupport.sampleImagePath(), assetDirectory);

        ExampleOutput.section("Multimodal Embedding Example");
        ExampleOutput.keyValue("Multimodal model", RetrievalExampleSupport.multimodalEmbeddingConfig().getModelName());
        ExampleOutput.keyValue("Reference image", variants.referenceImage());
        ExampleOutput.keyValue("Alternate format", variants.sameContentDifferentFormat());
        ExampleOutput.keyValue("Different image", variants.differentImage());

        MultimodalDocument doc1 = new MultimodalDocument()
                .addField("text", REFERENCE_TEXT)
                .addField("image", variants.referenceImage());
        MultimodalDocument doc2 = new MultimodalDocument()
                .addField("text", REFERENCE_TEXT)
                .addField("image", variants.sameContentDifferentFormat());
        MultimodalDocument doc3 = new MultimodalDocument()
                .addField("text", REFERENCE_TEXT)
                .addField("image", variants.differentImage());
        MultimodalDocument doc4 = new MultimodalDocument()
                .addField("text", DIFFERENT_TEXT)
                .addField("image", variants.referenceImage());

        try (VLLMEmbedding embedding = new VLLMEmbedding(
                RetrievalExampleSupport.multimodalEmbeddingConfig(),
                10,
                3,
                null,
                8,
                20,
                dimension,
                null)) {
            ExampleOutput.subsection("Generating multimodal embeddings");
            List<Float> emb1 = embedding.embedMultimodal(doc1);
            List<Float> emb2 = embedding.embedMultimodal(doc2);
            List<Float> emb3 = embedding.embedMultimodal(doc3);
            List<Float> emb4 = embedding.embedMultimodal(doc4);
            ExampleOutput.keyValue("Returned vector length", emb1.size());

            double sim12 = VectorSimilarityUtils.cosineSimilarity(emb1, emb2);
            double sim13 = VectorSimilarityUtils.cosineSimilarity(emb1, emb3);
            double sim14 = VectorSimilarityUtils.cosineSimilarity(emb1, emb4);
            double sim23 = VectorSimilarityUtils.cosineSimilarity(emb2, emb3);
            double dist12 = VectorSimilarityUtils.euclideanDistance(emb1, emb2);
            double dist13 = VectorSimilarityUtils.euclideanDistance(emb1, emb3);
            double dist14 = VectorSimilarityUtils.euclideanDistance(emb1, emb4);

            ExampleOutput.subsection("Similarity comparison");
            printPair("Same image, alternate format", sim12, dist12);
            printPair("Different image, same text", sim13, dist13);
            printPair("Same image, different text", sim14, dist14);
            ExampleOutput.line("Alternate format vs different image cosine similarity: %.4f", sim23);

            ExampleOutput.subsection("Analysis");
            if (sim12 > sim13 && sim12 > sim23) {
                ExampleOutput.line("PASS: Same content with a different file format stays closest.");
            } else {
                ExampleOutput.line("WARNING: The same-content alternate-format pair was not the closest result.");
            }
            if (sim14 > sim13) {
                ExampleOutput.line("PASS: Reusing the same image with different text keeps more signal than swapping images.");
            } else {
                ExampleOutput.line("WARNING: Text influence dominated more strongly than expected in this run.");
            }
        }
    }

    private static Integer resolveDimension() {
        int configured = RetrievalExampleSupport.resolveIntConfig(
                "MULTIMODAL_EMBEDDING_DIM",
                RetrievalExampleSupport.DEFAULT_EMBEDDING_DIMENSION);
        return configured <= 0 ? null : configured;
    }

    private static ImageVariants createImageVariants(Path referenceImage, Path outputDirectory) throws IOException {
        BufferedImage source = ImageIO.read(referenceImage.toFile());
        if (source == null) {
            throw new IOException("Unable to read image file: " + referenceImage);
        }

        Path sameContentDifferentFormat = outputDirectory.resolve("sample_reference.jpg");
        if (!Files.isRegularFile(sameContentDifferentFormat)) {
            BufferedImage rgbImage = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgbImage.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
            graphics.drawImage(source, 0, 0, null);
            graphics.dispose();
            ImageIO.write(rgbImage, "jpg", sameContentDifferentFormat.toFile());
        }

        Path differentImage = outputDirectory.resolve("sample_variant.png");
        if (!Files.isRegularFile(differentImage)) {
            BufferedImage variant = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < source.getWidth(); x++) {
                for (int y = 0; y < source.getHeight(); y++) {
                    int mirroredX = source.getWidth() - 1 - x;
                    Color original = new Color(source.getRGB(mirroredX, y), true);
                    Color shifted = new Color(
                            Math.min(255, original.getBlue() + 20),
                            Math.max(0, original.getGreen() - 30),
                            Math.max(0, original.getRed() - 15),
                            original.getAlpha());
                    variant.setRGB(x, y, shifted.getRGB());
                }
            }
            ImageIO.write(variant, "png", differentImage.toFile());
        }

        return new ImageVariants(referenceImage, sameContentDifferentFormat, differentImage);
    }

    private static void printPair(String label, double similarity, double distance) {
        ExampleOutput.line(label + ":");
        ExampleOutput.line("  cosine similarity   %.4f", similarity);
        ExampleOutput.line("  euclidean distance  %.4f", distance);
    }

    private record ImageVariants(Path referenceImage, Path sameContentDifferentFormat, Path differentImage) {
    }
}