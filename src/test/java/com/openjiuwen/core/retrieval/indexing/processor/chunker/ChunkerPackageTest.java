/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package registry behavior in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py}.
 */
class ChunkerPackageTest {

    @Test
    void exportsMatchPythonAllList() {
        assertThat(ChunkerPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py");
        assertThat(ChunkerPackage.EXPORTED_SYMBOLS).containsExactly(
                "Chunker",
                "CharChunker",
                "HybridChunker",
                "CHUNKER_REGISTRY",
                "register_chunker",
                "get_chunker"
        );
        assertThat(ChunkerPackage.CHUNKER).isEqualTo(Chunker.class);
        assertThat(ChunkerPackage.CHAR_CHUNKER).isEqualTo(CharChunker.class);
        assertThat(ChunkerPackage.HYBRID_CHUNKER).isEqualTo(HybridChunker.class);
    }

    @Test
    void builtInRegistryKeepsCharThenHybridOrder() {
        assertThat(ChunkerPackage.registeredChunkerNames()).contains("char", "hybrid");
        assertThat(ChunkerPackage.registeredChunkerNames().indexOf("char"))
                .isLessThan(ChunkerPackage.registeredChunkerNames().indexOf("hybrid"));
        assertThat(ChunkerPackage.chunkerRegistry()).containsKeys("char", "hybrid");
    }

    @Test
    void getCharChunkerUsesDefaultAndKeywordOptions() {
        Chunker defaultChunker = ChunkerPackage.getChunker("char");
        Chunker configuredChunker = ChunkerPackage.getChunker("char", Map.of("chunk_size", 5, "chunk_overlap", 1));

        assertThat(defaultChunker).isInstanceOf(CharChunker.class);
        assertThat(defaultChunker.getChunkSize()).isEqualTo(512);
        assertThat(defaultChunker.getChunkOverlap()).isEqualTo(50);
        assertThat(configuredChunker).isInstanceOf(CharChunker.class);
        assertThat(configuredChunker.chunkText("abcdefghijk")).containsExactly("abcde", "efghi", "ijk");
    }

    @Test
    void getCharChunkerDefaultsMatchPython() {
        Chunker chunker = ChunkerPackage.getChunker("char");

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
    }

    @Test
    void getCharChunkerAcceptsConfiguredSizeAndOverlap() {
        Chunker chunker = ChunkerPackage.getChunker("char", Map.of("chunk_size", 256, "chunk_overlap", 30));

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(30);
    }

    @Test
    void getHybridChunkerBuildsDefaultCharInnerChunker() {
        Chunker chunker = ChunkerPackage.getChunker("hybrid", Map.of("chunk_size", 5, "chunk_overlap", 1));

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.chunkText("abcdefghijk")).containsExactly("abcde", "efghi", "ijk");
    }

    @Test
    void getHybridChunkerDefaultsMatchPython() {
        Chunker chunker = ChunkerPackage.getChunker("hybrid");

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
    }

    @Test
    void getHybridChunkerAcceptsConfiguredSizeAndOverlap() {
        Chunker chunker = ChunkerPackage.getChunker("hybrid", Map.of("chunk_size", 128, "chunk_overlap", 20));

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(128);
        assertThat(chunker.getChunkOverlap()).isEqualTo(20);
    }

    @Test
    void getHybridChunkerAcceptsInnerChunkerAndNoSplitPredicate() {
        Chunker innerChunker = new CharChunker(5, 1);
        Chunker chunker = ChunkerPackage.getChunker(
                "hybrid",
                ChunkerOptions.builder()
                        .innerChunker(innerChunker)
                        .noSplitWhen(document -> "note".equals(document.getMetadata().get("kind")))
                        .build()
        );
        Document document = new Document("doc-1", "  keep as one  ", Map.of("kind", "note"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).isEqualTo("keep as one");
        assertThat(chunks.get(0).getDocId()).isEqualTo("doc-1");
        assertThat(chunks.get(0).getMetadata()).containsEntry("kind", "note");
    }

    @Test
    void getHybridChunkerWithCustomInnerInheritsInnerSettings() {
        Chunker innerChunker = new CharChunker(64, 10);

        Chunker chunker = ChunkerPackage.getChunker(
                "hybrid",
                ChunkerOptions.builder().innerChunker(innerChunker).build()
        );

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(64);
        assertThat(chunker.getChunkOverlap()).isEqualTo(10);
    }

    @Test
    void getHybridChunkerAcceptsNoSplitPredicateFromKeywordArgs() {
        Chunker chunker = ChunkerPackage.getChunker(
                "hybrid",
                Map.of("no_split_when", (java.util.function.Predicate<Document>) document ->
                        "special".equals(document.getMetadata().get("type")))
        );
        Document document = new Document("doc-special", "  special text  ", Map.of("type", "special"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).isEqualTo("special text");
    }

    @Test
    void hybridFactoryIgnoresExtraKeywordsWhenDefaultInnerChunkerIsBuilt() {
        Chunker chunker = ChunkerPackage.getChunker(
                "hybrid",
                Map.of("chunk_size", 256, "chunk_overlap", 10, "future_option", true)
        );

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(10);
    }

    @Test
    void hybridFactoryRejectsUnknownKeywordsWhenInnerChunkerIsProvided() {
        Chunker innerChunker = new CharChunker(5, 1);

        assertThatThrownBy(() -> ChunkerPackage.getChunker(
                "hybrid",
                Map.of("inner_chunker", innerChunker, "beta", 1, "alpha", 2)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown kwargs for 'hybrid' chunker: alpha, beta");
    }

    @Test
    void keywordInnerChunkerMustBeChunker() {
        assertThatThrownBy(() -> ChunkerPackage.getChunker("hybrid", Map.of("inner_chunker", "not a chunker")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inner_chunker must be a Chunker instance");
    }

    @Test
    void keywordChunkSizeMustBeNumeric() {
        assertThatThrownBy(() -> ChunkerPackage.getChunker("char", Map.of("chunk_size", "large")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunk_size must be a number");
    }

    @Test
    void registerChunkerValidatesNameAndOverwrite() {
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("", options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunker name must be a non-empty string");
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("char", options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        ChunkerPackage.registerChunker("package-test-custom", options -> new CharChunker(6, 2), true);

        Chunker chunker = ChunkerPackage.getChunker("package-test-custom");

        assertThat(chunker.getChunkSize()).isEqualTo(6);
        assertThat(chunker.getChunkOverlap()).isEqualTo(2);
    }

    @Test
    void registerChunkerRejectsWhitespaceName() {
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("   ", options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chunker name must be a non-empty string");
    }

    @Test
    void registerDuplicateNameRaises() {
        String name = "package-test-duplicate";
        ChunkerPackage.registerChunker(name, options -> new CharChunker(4, 1), true);

        assertThatThrownBy(() -> ChunkerPackage.registerChunker(name, options -> new CharChunker(6, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void registerDuplicateNameWithOverwriteReplacesFactory() {
        String name = "package-test-overwrite";
        ChunkerPackage.registerChunker(name, options -> new CharChunker(4, 1), true);
        ChunkerPackage.registerChunker(name, options -> new CharChunker(9, 3), true);

        Chunker chunker = ChunkerPackage.getChunker(name);

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(9);
        assertThat(chunker.getChunkOverlap()).isEqualTo(3);
    }

    @Test
    void registerFactoryCallableUsesChunkerOptions() {
        String name = "package-test-factory";
        ChunkerPackage.registerChunker(name, options -> new CharChunker(options.getChunkSize(), 1), true);

        Chunker chunker = ChunkerPackage.getChunker(name, Map.of("chunk_size", 200));

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(200);
        assertThat(chunker.getChunkOverlap()).isEqualTo(1);
    }

    @Test
    void builtInCharIsRegistered() {
        assertThat(ChunkerPackage.chunkerRegistry()).containsKey("char");
    }

    @Test
    void builtInHybridIsRegistered() {
        assertThat(ChunkerPackage.chunkerRegistry()).containsKey("hybrid");
    }

    @Test
    void overwriteBuiltinWithoutFlagIsBlocked() {
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("char", options -> new CharChunker(4, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void getChunkerRejectsUnknownNameAndNullFactoryResult() {
        assertThatThrownBy(() -> ChunkerPackage.getChunker("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown chunker: missing");

        ChunkerPackage.registerChunker("package-test-null", options -> null, true);

        assertThatThrownBy(() -> ChunkerPackage.getChunker("package-test-null"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must return a Chunker instance");
    }
}
