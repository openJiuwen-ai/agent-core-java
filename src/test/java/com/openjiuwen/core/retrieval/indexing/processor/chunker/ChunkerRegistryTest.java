/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.retrieval.common.Document;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ChunkerRegistryTest {
    @Test
    void getBuiltinCharChunkerShouldPassKwargs() {
        Chunker chunker = ChunkerRegistry.getChunker("char", Map.of("chunk_size", 256, "chunk_overlap", 30));

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(30);
    }

    @Test
    void getBuiltinHybridShouldDefaultToCharChunkerAndSupportCustomInner() {
        Chunker defaultHybrid = ChunkerRegistry.getChunker("hybrid", Map.of("chunk_size", 128, "chunk_overlap", 20));
        CharChunker inner = new CharChunker(64, 10);
        Chunker customHybrid = ChunkerRegistry.getChunker("hybrid", Map.of("inner_chunker", inner));

        assertThat(defaultHybrid).isInstanceOf(HybridChunker.class);
        assertThat(defaultHybrid.getChunkSize()).isEqualTo(128);
        assertThat(defaultHybrid.getChunkOverlap()).isEqualTo(20);
        assertThat(customHybrid).isInstanceOf(HybridChunker.class);
        assertThat(customHybrid.getChunkSize()).isEqualTo(64);
    }

    @Test
    void hybridShouldRejectUnknownKwargsWhenInnerProvided() {
        assertThatThrownBy(() -> ChunkerRegistry.getChunker("hybrid",
                Map.of("inner_chunker", new CharChunker(64, 10), "bad_param", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown kwargs");
    }

    @Test
    void hybridShouldRejectInvalidInnerChunker() {
        assertThatThrownBy(() -> ChunkerRegistry.getChunker("hybrid", Map.of("inner_chunker", "not a chunker")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inner_chunker must be a Chunker instance");
    }

    @Test
    void unknownChunkerShouldThrow() {
        assertThatThrownBy(() -> ChunkerRegistry.getChunker("nonexistent")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown chunker");
    }

    @Test
    void registerShouldRejectEmptyAndDuplicateNamesUnlessOverwrite() {
        assertThatThrownBy(() -> ChunkerRegistry.registerChunker("", kwargs -> new CharChunker(10, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-empty string");
        assertThatThrownBy(() -> ChunkerRegistry.registerChunker("char", kwargs -> new CharChunker(10, 0)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already registered");

        String name = "_test_overwrite";
        try {
            ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker(20, 0));
            assertThatThrownBy(() -> ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker(30, 0)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already registered");
            ChunkerRegistry.registerChunker(name, kwargs -> new CharChunker(30, 0), true);
            assertThat(ChunkerRegistry.getChunker(name).getChunkSize()).isEqualTo(30);
        } finally {
            ChunkerRegistry.unregisterChunker(name);
        }
    }

    @Test
    void registryEntryReturningNullShouldThrow() {
        String name = "_test_null";
        try {
            ChunkerRegistry.registerChunker(name, kwargs -> null);
            assertThatThrownBy(() -> ChunkerRegistry.getChunker(name)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must return a Chunker instance");
        } finally {
            ChunkerRegistry.unregisterChunker(name);
        }
    }

    @Test
    void hybridShouldAcceptNoSplitPredicate() {
        Chunker chunker = ChunkerRegistry.getChunker("hybrid", Map.of("no_split_when",
                (java.util.function.Predicate<Document>) doc -> "special".equals(doc.getMetadata().get("type"))));

        assertThat(chunker.chunkDocuments(List.of(new Document("id", "abcdef", Map.of("type", "special"))))).hasSize(1);
    }
}
