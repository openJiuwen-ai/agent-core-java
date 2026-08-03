/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for the chunker registry.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.indexing.processor.chunker.test_chunker_registry} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/chunker/test_chunker_registry.py}.</p>
 */
class ChunkerRegistryPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/core/retrieval/indexing/processor/chunker/test_chunker_registry.py";
    private static final String RUN_ID = Long.toHexString(System.nanoTime());

    @TestFactory
    Collection<DynamicTest> pythonChunkerRegistryCases() {
        return List.of(
                caseOf("TestGetChunkerBuiltin::test_get_char_chunker",
                        ChunkerRegistryPythonParityTest::getCharChunker),
                caseOf("TestGetChunkerBuiltin::test_get_char_chunker_defaults",
                        ChunkerRegistryPythonParityTest::getCharChunkerDefaults),
                caseOf("TestGetChunkerBuiltin::test_get_hybrid_chunker",
                        ChunkerRegistryPythonParityTest::getHybridChunker),
                caseOf("TestGetChunkerBuiltin::test_get_hybrid_chunker_defaults",
                        ChunkerRegistryPythonParityTest::getHybridChunkerDefaults),
                caseOf("TestGetChunkerBuiltin::test_get_hybrid_with_custom_inner",
                        ChunkerRegistryPythonParityTest::getHybridWithCustomInner),
                caseOf("TestGetChunkerBuiltin::test_get_hybrid_with_no_split_when",
                        ChunkerRegistryPythonParityTest::getHybridWithNoSplitWhen),
                caseOf("TestGetChunkerBuiltin::test_get_unknown_chunker",
                        ChunkerRegistryPythonParityTest::getUnknownChunker),
                caseOf("TestGetChunkerValidation::test_hybrid_unknown_kwargs_when_inner_provided",
                        ChunkerRegistryPythonParityTest::hybridUnknownKwargsWhenInnerProvided),
                caseOf("TestGetChunkerValidation::test_hybrid_extra_kwargs_passed_to_inner",
                        ChunkerRegistryPythonParityTest::hybridExtraKwargsPassedToInner),
                caseOf("TestGetChunkerValidation::test_hybrid_inner_chunker_not_chunker",
                        ChunkerRegistryPythonParityTest::hybridInnerChunkerNotChunker),
                caseOf("TestGetChunkerValidation::test_return_type_validation",
                        ChunkerRegistryPythonParityTest::returnTypeValidation),
                caseOf("TestRegisterChunker::test_register_and_get",
                        ChunkerRegistryPythonParityTest::registerAndGet),
                caseOf("TestRegisterChunker::test_register_duplicate_raises",
                        ChunkerRegistryPythonParityTest::registerDuplicateRaises),
                caseOf("TestRegisterChunker::test_register_duplicate_with_overwrite",
                        ChunkerRegistryPythonParityTest::registerDuplicateWithOverwrite),
                caseOf("TestRegisterChunker::test_register_empty_name",
                        ChunkerRegistryPythonParityTest::registerEmptyName),
                caseOf("TestRegisterChunker::test_register_whitespace_name",
                        ChunkerRegistryPythonParityTest::registerWhitespaceName),
                caseOf("TestRegisterChunker::test_builtin_char_registered",
                        ChunkerRegistryPythonParityTest::builtinCharRegistered),
                caseOf("TestRegisterChunker::test_builtin_hybrid_registered",
                        ChunkerRegistryPythonParityTest::builtinHybridRegistered),
                caseOf("TestRegisterChunker::test_register_factory_callable",
                        ChunkerRegistryPythonParityTest::registerFactoryCallable),
                caseOf("TestRegisterChunker::test_overwrite_builtin_blocked",
                        ChunkerRegistryPythonParityTest::overwriteBuiltinBlocked)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void getCharChunker() {
        Chunker chunker = ChunkerPackage.getChunker("char", Map.of("chunk_size", 256, "chunk_overlap", 30));

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(30);
    }

    private static void getCharChunkerDefaults() {
        Chunker chunker = ChunkerPackage.getChunker("char");

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(512);
    }

    private static void getHybridChunker() {
        Chunker chunker = ChunkerPackage.getChunker("hybrid", Map.of("chunk_size", 128, "chunk_overlap", 20));

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(128);
        assertThat(chunker.getChunkOverlap()).isEqualTo(20);
    }

    private static void getHybridChunkerDefaults() {
        Chunker chunker = ChunkerPackage.getChunker("hybrid");

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(512);
    }

    private static void getHybridWithCustomInner() {
        Chunker inner = new CharChunker(64, 10);
        Chunker chunker = ChunkerPackage.getChunker("hybrid", ChunkerOptions.builder().innerChunker(inner).build());

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(64);
    }

    private static void getHybridWithNoSplitWhen() {
        Predicate<Document> predicate = document -> "special".equals(document.getMetadata().get("type"));

        Chunker chunker = ChunkerPackage.getChunker("hybrid", Map.of("no_split_when", predicate));

        assertThat(chunker).isInstanceOf(HybridChunker.class);
    }

    private static void getUnknownChunker() {
        assertThatThrownBy(() -> ChunkerPackage.getChunker("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown chunker");
    }

    private static void hybridUnknownKwargsWhenInnerProvided() {
        Chunker inner = new CharChunker(64, 50);

        assertThatThrownBy(() -> ChunkerPackage.getChunker(
                "hybrid",
                Map.of("inner_chunker", inner, "bad_param", true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown kwargs");
    }

    private static void hybridExtraKwargsPassedToInner() {
        Chunker chunker = ChunkerPackage.getChunker(
                "hybrid",
                Map.of("chunk_size", 256, "chunk_overlap", 10)
        );

        assertThat(chunker).isInstanceOf(HybridChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(10);
    }

    private static void hybridInnerChunkerNotChunker() {
        assertThatThrownBy(() -> ChunkerPackage.getChunker("hybrid", Map.of("inner_chunker", "not a chunker")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inner_chunker must be a Chunker instance");
    }

    private static void returnTypeValidation() {
        String name = testName("bad-return");
        ChunkerPackage.registerChunker(name, options -> null);

        assertThatThrownBy(() -> ChunkerPackage.getChunker(name))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must return a Chunker instance");
    }

    private static void registerAndGet() {
        String name = testName("my-chunker");

        ChunkerPackage.registerChunker(name, options -> new MyChunker());
        Chunker chunker = ChunkerPackage.getChunker(name);

        assertThat(chunker).isInstanceOf(MyChunker.class);
    }

    private static void registerDuplicateRaises() {
        String name = testName("dup");
        ChunkerPackage.registerChunker(name, options -> new CharChunker());

        assertThatThrownBy(() -> ChunkerPackage.registerChunker(name, options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    private static void registerDuplicateWithOverwrite() {
        String name = testName("overwrite");
        ChunkerPackage.registerChunker(name, options -> new CharChunker());
        ChunkerPackage.registerChunker(name, options -> new MyChunker(), true);

        Chunker chunker = ChunkerPackage.getChunker(name);

        assertThat(chunker).isInstanceOf(MyChunker.class);
    }

    private static void registerEmptyName() {
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("", options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty string");
    }

    private static void registerWhitespaceName() {
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("   ", options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty string");
    }

    private static void builtinCharRegistered() {
        assertThat(ChunkerPackage.chunkerRegistry()).containsKey("char");
    }

    private static void builtinHybridRegistered() {
        assertThat(ChunkerPackage.chunkerRegistry()).containsKey("hybrid");
    }

    private static void registerFactoryCallable() {
        String name = testName("factory");
        ChunkerPackage.registerChunker(name, options -> new CharChunker(options.getChunkSize(), 50));

        Chunker chunker = ChunkerPackage.getChunker(name, Map.of("chunk_size", 200));

        assertThat(chunker).isInstanceOf(CharChunker.class);
        assertThat(chunker.getChunkSize()).isEqualTo(200);
    }

    private static void overwriteBuiltinBlocked() {
        assertThatThrownBy(() -> ChunkerPackage.registerChunker("char", options -> new CharChunker()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    private static String testName(String suffix) {
        return "_test_" + suffix + "_" + RUN_ID;
    }

    private static final class MyChunker extends Chunker {
        @Override
        public List<String> chunkText(String text) {
            return List.of(text);
        }
    }
}
