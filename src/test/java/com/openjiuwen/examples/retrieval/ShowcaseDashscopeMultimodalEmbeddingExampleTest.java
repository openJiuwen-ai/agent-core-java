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

class ShowcaseDashscopeMultimodalEmbeddingExampleTest {

    @Test
    void constantsAndConfigMatchPythonDashscopeShowcaseInputs() {
        assertEquals("A photograph of a person", ShowcaseDashscopeMultimodalEmbeddingExample.REFERENCE_TEXT);
        assertEquals("Picture of an octopus in ocean", ShowcaseDashscopeMultimodalEmbeddingExample.DIFFERENT_TEXT);
        assertEquals(Path.of("reference.jpg"), ShowcaseDashscopeMultimodalEmbeddingExample.LOCAL_REF_IMAGE);
        assertEquals("https://openjiuwen.com/img/jiuwen_logo.png",
                ShowcaseDashscopeMultimodalEmbeddingExample.DIFFERENT_IMAGE);
        assertEquals(256, ShowcaseDashscopeMultimodalEmbeddingExample.EMBEDDING_DIM);

        EmbeddingConfig config = ShowcaseDashscopeMultimodalEmbeddingExample.sampleConfig("dashscope-key");
        assertEquals("qwen3-vl-embedding", config.getModelName());
        assertEquals("https://dashscope.aliyuncs.com/api/v1/", config.getBaseUrl());
        assertEquals("dashscope-key", config.getApiKey());
    }

    @Test
    void createDocumentsMatchesPythonTextAndImageCombinations() {
        List<ShowcaseDashscopeMultimodalEmbeddingExample.ExampleDocument> docs =
                ShowcaseDashscopeMultimodalEmbeddingExample.createDocuments();

        assertEquals(3, docs.size());
        assertEquals("doc1", docs.get(0).id());
        assertEquals(ShowcaseDashscopeMultimodalEmbeddingExample.REFERENCE_TEXT, docs.get(0).text());
        assertTrue(docs.get(0).image().isLocalFile());
        assertEquals("reference.jpg", docs.get(0).image().value());
        assertEquals(ShowcaseDashscopeMultimodalEmbeddingExample.REFERENCE_TEXT, docs.get(1).text());
        assertEquals(ShowcaseDashscopeMultimodalEmbeddingExample.DIFFERENT_IMAGE, docs.get(1).image().value());
        assertEquals(ShowcaseDashscopeMultimodalEmbeddingExample.DIFFERENT_TEXT, docs.get(2).text());
        assertEquals(ShowcaseDashscopeMultimodalEmbeddingExample.DIFFERENT_IMAGE, docs.get(2).image().value());
    }

    @Test
    void deterministicEmbeddingsPreservePythonAnalysisExpectations() {
        ShowcaseDashscopeMultimodalEmbeddingExample.DemoReport report =
                ShowcaseDashscopeMultimodalEmbeddingExample.runExample();

        assertEquals(3, report.embeddings().size());
        assertEquals(256, report.embeddings().get(0).size());
        assertTrue(report.doc1Doc2().cosineSimilarity() < 0.9d);
        assertTrue(report.doc1Doc3().cosineSimilarity() < 0.9d);
        assertTrue(report.doc2Doc3().euclideanDistance() < report.doc1Doc3().euclideanDistance());
        assertTrue(report.doc2Doc3().cosineSimilarity() > report.doc1Doc2().cosineSimilarity());
        assertTrue(report.analysis().differentImagesSignificant());
        assertTrue(report.analysis().sameImageDistanceSmaller());
        assertTrue(report.analysis().sameImageDifferentTextMoreSimilar());
    }

    @Test
    void vectorMathValidatesDimensionsAndComputesExpectedValues() {
        List<Double> left = List.of(1.0d, 0.0d, 0.0d);
        List<Double> right = List.of(0.0d, 1.0d, 0.0d);
        List<Double> same = List.of(1.0d, 0.0d, 0.0d);

        assertEquals(0.0d, ShowcaseDashscopeMultimodalEmbeddingExample.cosineSimilarity(left, right));
        assertEquals(1.0d, ShowcaseDashscopeMultimodalEmbeddingExample.cosineSimilarity(left, same));
        assertEquals(Math.sqrt(2.0d), ShowcaseDashscopeMultimodalEmbeddingExample.euclideanDistance(left, right));
        assertThrows(IllegalArgumentException.class,
                () -> ShowcaseDashscopeMultimodalEmbeddingExample.cosineSimilarity(left, List.of(1.0d)));
    }
}
