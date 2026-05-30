/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseMultimodalEmbeddingExampleTest {

    @Test
    void constantsAndConfigMatchPythonVllmShowcaseInputs() {
        assertEquals("A photograph of a person", ShowcaseMultimodalEmbeddingExample.REFERENCE_TEXT);
        assertEquals("Picture of an octopus in ocean", ShowcaseMultimodalEmbeddingExample.DIFFERENT_TEXT);
        assertEquals(Path.of("reference.jpg"), ShowcaseMultimodalEmbeddingExample.REF_IMAGE);
        assertEquals(Path.of("reference.ppm"), ShowcaseMultimodalEmbeddingExample.SAME_CONTENT_DIFFERENT_IMAGE);
        assertEquals(Path.of("different.ppm"), ShowcaseMultimodalEmbeddingExample.DIFFERENT_IMAGE);
        assertEquals(128, ShowcaseMultimodalEmbeddingExample.EMBEDDING_DIM);

        EmbeddingConfig config = ShowcaseMultimodalEmbeddingExample.sampleConfig();
        assertEquals("multimodal-embedding-model", config.getModelName());
        assertEquals("https://multimodal.example/v1", config.getBaseUrl());
        assertEquals("multimodal-key", config.getApiKey());
    }

    @Test
    void createDocumentsMatchesPythonFourDocumentScenario() {
        List<ShowcaseMultimodalEmbeddingExample.ExampleDocument> docs =
                ShowcaseMultimodalEmbeddingExample.createDocuments();

        assertEquals(4, docs.size());
        assertEquals("doc1", docs.get(0).id());
        assertEquals(ShowcaseMultimodalEmbeddingExample.REFERENCE_TEXT, docs.get(0).text());
        assertEquals(Path.of("reference.jpg"), docs.get(0).imagePath());
        assertEquals(Path.of("reference.ppm"), docs.get(1).imagePath());
        assertEquals(Path.of("different.ppm"), docs.get(2).imagePath());
        assertEquals(ShowcaseMultimodalEmbeddingExample.DIFFERENT_TEXT, docs.get(3).text());
        assertEquals(Path.of("reference.jpg"), docs.get(3).imagePath());
    }

    @Test
    void deterministicEmbeddingsPreservePythonAnalysisExpectations() {
        ShowcaseMultimodalEmbeddingExample.DemoReport report = ShowcaseMultimodalEmbeddingExample.runExample();

        assertEquals(4, report.embeddings().size());
        assertEquals(128, report.embeddings().get(0).size());
        assertEquals(6, report.comparisons().size());
        assertTrue(report.comparisons().get("doc1_vs_doc2").cosineSimilarity()
                > report.comparisons().get("doc1_vs_doc3").cosineSimilarity());
        assertTrue(report.comparisons().get("doc1_vs_doc2").cosineSimilarity()
                > report.comparisons().get("doc2_vs_doc3").cosineSimilarity());
        assertTrue(report.comparisons().get("doc1_vs_doc3").cosineSimilarity() < 0.9d);
        assertTrue(report.comparisons().get("doc2_vs_doc3").cosineSimilarity() < 0.9d);
        assertTrue(report.analysis().sameImageMoreSimilar());
        assertTrue(report.analysis().differentImagesSignificant());
        assertTrue(report.analysis().sameImageDistanceSmaller());
        assertTrue(report.analysis().sameImageDifferentTextMoreSimilar());
        assertTrue(report.analysis().sameImageSameTextMoreSimilar());
        assertTrue(report.analysis().imageDifferenceRatio() < 0.9d);
        assertTrue(report.analysis().textInfluenceRatio() < 1.0d);
    }

    @Test
    void vectorMathValidatesDimensionsAndComputesExpectedValues() {
        List<Double> left = List.of(1.0d, 0.0d, 0.0d);
        List<Double> right = List.of(0.0d, 1.0d, 0.0d);
        List<Double> same = List.of(1.0d, 0.0d, 0.0d);

        assertEquals(0.0d, ShowcaseMultimodalEmbeddingExample.cosineSimilarity(left, right));
        assertEquals(1.0d, ShowcaseMultimodalEmbeddingExample.cosineSimilarity(left, same));
        assertEquals(Math.sqrt(2.0d), ShowcaseMultimodalEmbeddingExample.euclideanDistance(left, right));
        assertThrows(IllegalArgumentException.class,
                () -> ShowcaseMultimodalEmbeddingExample.euclideanDistance(left, List.of(1.0d)));
    }
}
