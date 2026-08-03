/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental parity tests for the hybrid chunker test module.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.retrieval.indexing.processor.chunker.test_hybrid_chunker} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/chunker/test_hybrid_chunker.py}.</p>
 */
class HybridChunkerPythonParityTest {

    private static final String SOURCE =
            "tests/unit_tests/core/retrieval/indexing/processor/chunker/test_hybrid_chunker.py";

    @TestFactory
    Collection<DynamicTest> pythonHybridChunkerCases() {
        return List.of(
                caseOf("TestDefaultNoSplit::test_row_source_type",
                        HybridChunkerPythonParityTest::rowSourceType),
                caseOf("TestDefaultNoSplit::test_column_source_type",
                        HybridChunkerPythonParityTest::columnSourceType),
                caseOf("TestDefaultNoSplit::test_other_source_type",
                        HybridChunkerPythonParityTest::otherSourceType),
                caseOf("TestDefaultNoSplit::test_no_source_type",
                        HybridChunkerPythonParityTest::noSourceType),
                caseOf("TestDefaultNoSplit::test_empty_metadata",
                        HybridChunkerPythonParityTest::emptyMetadata),
                caseOf("TestHybridChunker::test_init_inherits_inner_params",
                        HybridChunkerPythonParityTest::initInheritsInnerParams),
                caseOf("TestHybridChunker::test_chunk_text_delegates_to_inner",
                        HybridChunkerPythonParityTest::chunkTextDelegatesToInner),
                caseOf("TestHybridChunker::test_row_doc_single_chunk",
                        HybridChunkerPythonParityTest::rowDocSingleChunk),
                caseOf("TestHybridChunker::test_column_doc_single_chunk",
                        HybridChunkerPythonParityTest::columnDocSingleChunk),
                caseOf("TestHybridChunker::test_normal_doc_delegates_to_inner",
                        HybridChunkerPythonParityTest::normalDocDelegatesToInner),
                caseOf("TestHybridChunker::test_mixed_documents",
                        HybridChunkerPythonParityTest::mixedDocuments),
                caseOf("TestHybridChunker::test_empty_text_row_doc_delegates_to_inner",
                        HybridChunkerPythonParityTest::emptyTextRowDocDelegatesToInner),
                caseOf("TestHybridChunker::test_empty_string_text_row_doc",
                        HybridChunkerPythonParityTest::emptyStringTextRowDoc),
                caseOf("TestHybridChunker::test_custom_no_split_when",
                        HybridChunkerPythonParityTest::customNoSplitWhen),
                caseOf("TestHybridChunker::test_metadata_preserved_in_single_chunk",
                        HybridChunkerPythonParityTest::metadataPreservedInSingleChunk),
                caseOf("TestHybridChunker::test_empty_documents_list",
                        HybridChunkerPythonParityTest::emptyDocumentsList)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void rowSourceType() {
        Document document = new Document("1", "a]", metadata("source_type", "row"));

        List<TextChunk> chunks = new HybridChunker(makeInner()).chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getDocId()).isEqualTo("1");
    }

    private static void columnSourceType() {
        Document document = new Document("1", "a", metadata("source_type", "column"));

        List<TextChunk> chunks = new HybridChunker(makeInner()).chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getDocId()).isEqualTo("1");
    }

    private static void otherSourceType() {
        Document document = new Document("1", "This text should be split", metadata("source_type", "paragraph"));

        List<TextChunk> chunks = new HybridChunker(makeInner(10, 2)).chunkDocuments(List.of(document));

        assertThat(chunks).hasSizeGreaterThan(1);
    }

    private static void noSourceType() {
        Document document = new Document("1", "This text should be split", metadata("title", "hello"));

        List<TextChunk> chunks = new HybridChunker(makeInner(10, 2)).chunkDocuments(List.of(document));

        assertThat(chunks).hasSizeGreaterThan(1);
    }

    private static void emptyMetadata() {
        Document document = new Document("1", "This text should be split", new LinkedHashMap<>());

        List<TextChunk> chunks = new HybridChunker(makeInner(10, 2)).chunkDocuments(List.of(document));

        assertThat(chunks).hasSizeGreaterThan(1);
    }

    private static void initInheritsInnerParams() {
        Chunker inner = makeInner(256, 30);

        HybridChunker chunker = new HybridChunker(inner);

        assertThat(chunker.getChunkSize()).isEqualTo(256);
        assertThat(chunker.getChunkOverlap()).isEqualTo(30);
    }

    private static void chunkTextDelegatesToInner() {
        Chunker inner = makeInner(10, 2);
        HybridChunker chunker = new HybridChunker(inner);

        assertThat(chunker.chunkText("hello world, this is a test"))
                .isEqualTo(inner.chunkText("hello world, this is a test"));
    }

    private static void rowDocSingleChunk() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        Document document = new Document(
                "row1",
                "姓名: 张三, 部门: 研发, 工号: 1001",
                metadata("source_type", "row", "sheet_name", "Sheet1", "row_index", 2)
        );

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        assertThat(chunk.getDocId()).isEqualTo("row1");
        assertThat(chunk.getText()).isEqualTo(document.getText().strip());
        assertThat(chunk.getMetadata())
                .containsEntry("source_type", "row")
                .containsEntry("chunk_index", 0)
                .containsEntry("total_chunks", 1);
    }

    private static void columnDocSingleChunk() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        Document document = new Document(
                "col1",
                "列名: 姓名。取值: 张三, 李四, 王五",
                metadata("source_type", "column", "sheet_name", "Sheet1", "column_name", "姓名")
        );

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getDocId()).isEqualTo("col1");
        assertThat(chunks.get(0).getMetadata()).containsEntry("source_type", "column");
    }

    private static void normalDocDelegatesToInner() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        Document document = new Document(
                "doc1",
                "This is a long document that should be split into multiple chunks by the inner chunker",
                metadata("title", "test")
        );

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(chunk -> "doc1".equals(chunk.getDocId()));
    }

    private static void mixedDocuments() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        List<Document> documents = List.of(
                new Document("row1", "姓名: 张三", metadata("source_type", "row")),
                new Document("doc1", "This is a long document that should be split into multiple chunks",
                        new LinkedHashMap<>()),
                new Document("col1", "列名: 部门", metadata("source_type", "column"))
        );

        List<TextChunk> chunks = chunker.chunkDocuments(documents);
        List<TextChunk> rowChunks = byDocId(chunks, "row1");
        List<TextChunk> colChunks = byDocId(chunks, "col1");
        List<TextChunk> docChunks = byDocId(chunks, "doc1");

        assertThat(rowChunks).hasSize(1);
        assertThat(colChunks).hasSize(1);
        assertThat(docChunks).hasSizeGreaterThan(1);
    }

    private static void emptyTextRowDocDelegatesToInner() {
        Chunker inner = makeInner();
        HybridChunker chunker = new HybridChunker(inner);
        Document document = new Document("row1", "   ", metadata("source_type", "row"));

        List<TextChunk> hybridChunks = chunker.chunkDocuments(List.of(document));
        List<TextChunk> innerChunks = inner.chunkDocuments(List.of(document));

        assertThat(hybridChunks).hasSameSizeAs(innerChunks);
    }

    private static void emptyStringTextRowDoc() {
        Chunker inner = makeInner();
        HybridChunker chunker = new HybridChunker(inner);
        Document document = new Document("row1", "", metadata("source_type", "row"));

        List<TextChunk> hybridChunks = chunker.chunkDocuments(List.of(document));
        List<TextChunk> innerChunks = inner.chunkDocuments(List.of(document));

        assertThat(hybridChunks).hasSameSizeAs(innerChunks);
    }

    private static void customNoSplitWhen() {
        HybridChunker chunker = new HybridChunker(
                makeInner(10, 2),
                document -> Boolean.TRUE.equals(document.getMetadata().get("keep_whole"))
        );
        Document keep = new Document("a", "short text", metadata("keep_whole", true));
        Document split = new Document(
                "b",
                "This is a long document that should be split by the inner chunker",
                metadata("source_type", "row")
        );

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(keep, split));

        assertThat(byDocId(chunks, "a")).hasSize(1);
        assertThat(byDocId(chunks, "b")).hasSizeGreaterThan(1);
    }

    private static void metadataPreservedInSingleChunk() {
        HybridChunker chunker = new HybridChunker(makeInner());
        Document document = new Document(
                "row1",
                "content",
                metadata("source_type", "row", "sheet_name", "S1", "custom_key", "custom_val")
        );

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("source_type", "row")
                .containsEntry("sheet_name", "S1")
                .containsEntry("custom_key", "custom_val")
                .containsEntry("chunk_index", 0)
                .containsEntry("total_chunks", 1)
                .containsKey("chunk_id");
    }

    private static void emptyDocumentsList() {
        assertThat(new HybridChunker(makeInner()).chunkDocuments(List.of())).isEmpty();
    }

    private static Chunker makeInner() {
        return makeInner(512, 50);
    }

    private static Chunker makeInner(int chunkSize, int chunkOverlap) {
        return new CharChunker(chunkSize, chunkOverlap);
    }

    private static List<TextChunk> byDocId(List<TextChunk> chunks, String docId) {
        return chunks.stream()
                .filter(chunk -> docId.equals(chunk.getDocId()))
                .toList();
    }

    private static Map<String, Object> metadata(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            metadata.put((String) keyValues[i], keyValues[i + 1]);
        }
        return metadata;
    }
}
