/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.extractor;

import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.Triple;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code Extractor} in
 * {@code openjiuwen/core/retrieval/indexing/processor/extractor/base.py}.
 *
 * <p>Mirrors Python's supplemental coverage in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/extractor/test_base.py}.</p>
 */
class ExtractorTest {

    @Test
    void extractBuildsTriplesFromMatchingChunks() {
        ConcreteExtractor extractor = new ConcreteExtractor();
        List<Triple> triples = extractor.extract(List.of(
                new TextChunk("1", "Alice knows Bob", "doc_1"),
                new TextChunk("2", "Some other text", "doc_1")
        )).join();

        assertThat(triples).hasSize(1);
        assertThat(triples.get(0).getSubject()).isEqualTo("Alice");
        assertThat(triples.get(0).getPredicate()).isEqualTo("knows");
        assertThat(triples.get(0).getObject()).isEqualTo("Bob");
    }

    @Test
    void processDelegatesToExtract() {
        ConcreteExtractor extractor = new ConcreteExtractor();

        assertThat(extractor.process(List.of(new TextChunk("1", "Alice knows Bob", "doc_1"))).join()).hasSize(1);
    }

    @Test
    void extractorRemainsAbstractBaseType() {
        assertThat(Modifier.isAbstract(Extractor.class.getModifiers())).isTrue();
    }

    private static final class ConcreteExtractor extends Extractor {

        @Override
        public CompletableFuture<List<Triple>> extract(List<TextChunk> chunks) {
            List<Triple> triples = new ArrayList<>();
            for (TextChunk chunk : chunks) {
                if (chunk.getText().contains("knows")) {
                    triples.add(new Triple("Alice", "knows", "Bob", Map.of("doc_id", chunk.getDocId())));
                }
            }
            return CompletableFuture.completedFuture(triples);
        }
    }
}
