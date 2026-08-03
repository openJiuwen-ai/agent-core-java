/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.indexing.processor.parser.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the retrieval package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval} package facade in
 * {@code openjiuwen/core/retrieval/__init__.py}.</p>
 */
class RetrievalPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        List<String> expected = new ArrayList<>();
        expected.addAll(List.of(
                "KnowledgeBaseConfig",
                "RetrievalConfig",
                "IndexConfig",
                "VectorStoreConfig",
                "EmbeddingConfig",
                "RerankerConfig",
                "Document",
                "MultimodalDocument",
                "TextChunk",
                "MultiKBRetrievalResult",
                "RetrievalResult",
                "SearchResult",
                "Triple",
                "TripleBeam",
                "TripleMemory",
                "BaseCallback",
                "TqdmCallback",
                "Embedding",
                "APIEmbedding",
                "Reranker",
                "VectorStore",
                "create_vector_store",
                "Indexer",
                "Processor",
                "Chunker",
                "Extractor",
                "Splitter",
                "SentenceSplitter",
                "TextSplitter",
                "CharSplitter",
                "IndexSentenceSplitter",
                "TextPreprocessor",
                "WhitespaceNormalizer",
                "URLEmailRemover",
                "SpecialCharacterNormalizer",
                "PreprocessingPipeline",
                "TextChunker",
                "CharChunker",
                "HybridChunker",
                "get_chunker",
                "register_chunker",
                "TokenizerChunker",
                "TripleExtractor",
                "Retriever",
                "VectorRetriever",
                "SparseRetriever",
                "HybridRetriever",
                "GraphRetriever",
                "AgenticRetriever",
                "ConfigManager",
                "rrf_fusion",
                "deduplicate"
        ));
        expected.addAll(List.of(
                "MilvusAUTO",
                "MilvusFLAT",
                "MilvusHNSW",
                "MilvusIVF",
                "MilvusSCANN",
                "MilvusVectorStore",
                "MilvusIndexer",
                "ChromaIndexer",
                "ChromaVectorStore",
                "ChromaVectorField",
                "OpenAIEmbedding",
                "VLLMEmbedding",
                "DashscopeEmbedding",
                "parse_base64_embedding",
                "StandardReranker",
                "ChatReranker",
                "DashscopeReranker",
                "AutoFileParser",
                "AutoLinkParser",
                "AutoParser",
                "ExcelParser",
                "Parser",
                "JSONParser",
                "PDFParser",
                "ImageParser",
                "TxtMdParser",
                "WebPageParser",
                "WeChatArticleParser",
                "WordParser",
                "KnowledgeBase",
                "SimpleKnowledgeBase",
                "GraphKnowledgeBase",
                "retrieve_multi_kb",
                "retrieve_multi_kb_with_source",
                "QueryRewriter"
        ));

        assertEquals("openjiuwen/core/retrieval/__init__.py", RetrievalPackage.PYTHON_MODULE);
        assertEquals(52, RetrievalPackage.NON_LAZY_SYMBOLS.size());
        assertEquals(35, RetrievalPackage.LAZY_SYMBOLS.size());
        assertEquals(87, RetrievalPackage.EXPORTED_SYMBOLS.size());
        assertEquals(expected, RetrievalPackage.EXPORTED_SYMBOLS);
        assertSame(RetrievalPackage.EXPORTED_SYMBOLS, RetrievalPackage.all());
        assertEquals(expected, new ArrayList<>(RetrievalPackage.EXPORT_SOURCES.keySet()));
        assertEquals(expected, new ArrayList<>(RetrievalPackage.JAVA_SYMBOL_NAMES.keySet()));
    }

    @Test
    void lazyMembershipMatchesPythonLazyLoadLedger() {
        assertFalse(RetrievalPackage.isLazy("KnowledgeBaseConfig"));
        assertFalse(RetrievalPackage.isLazy("deduplicate"));
        assertTrue(RetrievalPackage.isLazy("MilvusAUTO"));
        assertTrue(RetrievalPackage.isLazy("AutoFileParser"));
        assertTrue(RetrievalPackage.isLazy("retrieve_multi_kb"));
    }

    @Test
    void sourceMapPreservesRepresentativePythonOrigins() {
        assertEquals(
                "openjiuwen.core.retrieval.common.config.KnowledgeBaseConfig",
                RetrievalPackage.sourceFor("KnowledgeBaseConfig")
        );
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.chunker.get_chunker",
                RetrievalPackage.sourceFor("get_chunker")
        );
        assertEquals(
                "openjiuwen.core.foundation.store.vector_fields.milvus_fields.MilvusAUTO",
                RetrievalPackage.sourceFor("MilvusAUTO")
        );
        assertEquals(
                "openjiuwen.core.retrieval.indexing.processor.parser.auto_file_parser.AutoFileParser",
                RetrievalPackage.sourceFor("AutoFileParser")
        );
        assertEquals(
                "openjiuwen.core.retrieval.simple_knowledge_base.retrieve_multi_kb_with_source",
                RetrievalPackage.sourceFor("retrieve_multi_kb_with_source")
        );
    }

    @Test
    void javaSymbolNamesRepresentClassesAndFunctions() {
        assertEquals(
                "com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig",
                RetrievalPackage.javaSymbolNameFor("KnowledgeBaseConfig")
        );
        assertEquals(
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerPackage#getChunker",
                RetrievalPackage.javaSymbolNameFor("get_chunker")
        );
        assertEquals(
                "com.openjiuwen.core.retrieval.embedding.EmbeddingUtils#parseBase64Embedding",
                RetrievalPackage.javaSymbolNameFor("parse_base64_embedding")
        );
        assertEquals(
                "com.openjiuwen.core.retrieval.SimpleKnowledgeBase#retrieveMultiKb",
                RetrievalPackage.javaSymbolNameFor("retrieve_multi_kb")
        );
    }

    @Test
    void resolveTypeIsLazyAndSkipsFunctionExports() {
        assertEquals(
                "com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig",
                RetrievalPackage.resolveType("KnowledgeBaseConfig").orElseThrow().getName()
        );
        assertTrue(RetrievalPackage.resolveType("get_chunker").isEmpty());
        assertTrue(RetrievalPackage.resolveType("parse_base64_embedding").isEmpty());
        RetrievalPackage.resolveType("AutoFileParser").ifPresent(type -> assertEquals(
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser",
                type.getName()
        ));
    }

    @Test
    void jsonParserExportResolvesCanonicalJavaType() {
        Class<?> resolvedType = assertDoesNotThrow(
                () -> RetrievalPackage.resolveType("JSONParser").orElseThrow());

        assertSame(JsonParser.class, resolvedType);
    }

    @Test
    void unknownSymbolIsNotExported() {
        assertTrue(RetrievalPackage.exports("RetrievalResult"));
        assertFalse(RetrievalPackage.exports("MissingRetrievalSymbol"));
        assertNull(RetrievalPackage.sourceFor("MissingRetrievalSymbol"));
        assertNull(RetrievalPackage.javaSymbolNameFor("MissingRetrievalSymbol"));
        assertTrue(RetrievalPackage.resolveType("MissingRetrievalSymbol").isEmpty());
    }

    @Test
    void exportedCollectionsAreImmutable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> RetrievalPackage.EXPORTED_SYMBOLS.add("Unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> RetrievalPackage.EXPORT_SOURCES.put("Unexpected", "unexpected")
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> RetrievalPackage.JAVA_SYMBOL_NAMES.put("Unexpected", "Unexpected")
        );
    }
}
