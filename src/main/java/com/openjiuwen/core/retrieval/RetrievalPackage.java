/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Package bridge for retrieval module exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval} package facade in
 * {@code openjiuwen/core/retrieval/__init__.py}.</p>
 */
public final class RetrievalPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/retrieval/__init__.py";

    public static final String DESCRIPTION =
            "Retrieval module, supporting knowledge base management, document indexing, embedding generation, "
                    + "vector search, and multi-strategy retrieval.";

    public static final List<String> NON_LAZY_SYMBOLS = List.of(
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
    );

    public static final List<String> LAZY_SYMBOLS = List.of(
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
    );

    public static final List<String> EXPORTED_SYMBOLS = buildExportedSymbols();
    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_SYMBOL_NAMES = buildJavaSymbolNames();

    private RetrievalPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is re-exported by the Python package facade.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Checks whether a symbol comes from the Python lazy-load ledger.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is in Python's lazy attributes
     */
    public static boolean isLazy(String symbolName) {
        return LAZY_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported or lazily loaded by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the expected Java class or static member name for an exported symbol.
     *
     * @param symbolName symbol name
     * @return fully qualified Java symbol name, or {@code null} when absent
     */
    public static String javaSymbolNameFor(String symbolName) {
        return JAVA_SYMBOL_NAMES.get(symbolName);
    }

    /**
     * Resolves the Java class for class-like exported symbols when available.
     *
     * @param symbolName symbol name
     * @return resolved class, or empty for unknown symbols, static function exports, or unavailable heavy classes
     */
    public static Optional<Class<?>> resolveType(String symbolName) {
        String javaSymbolName = JAVA_SYMBOL_NAMES.get(symbolName);
        if (javaSymbolName == null || javaSymbolName.contains("#")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Class.forName(javaSymbolName));
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }
    }

    private static List<String> buildExportedSymbols() {
        List<String> symbols = new ArrayList<>(NON_LAZY_SYMBOLS.size() + LAZY_SYMBOLS.size());
        symbols.addAll(NON_LAZY_SYMBOLS);
        symbols.addAll(LAZY_SYMBOLS);
        return Collections.unmodifiableList(symbols);
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("KnowledgeBaseConfig", "openjiuwen.core.retrieval.common.config.KnowledgeBaseConfig");
        sources.put("RetrievalConfig", "openjiuwen.core.retrieval.common.config.RetrievalConfig");
        sources.put("IndexConfig", "openjiuwen.core.retrieval.common.config.IndexConfig");
        sources.put("VectorStoreConfig", "openjiuwen.core.retrieval.common.config.VectorStoreConfig");
        sources.put("EmbeddingConfig", "openjiuwen.core.retrieval.common.config.EmbeddingConfig");
        sources.put("RerankerConfig", "openjiuwen.core.retrieval.common.config.RerankerConfig");
        sources.put("Document", "openjiuwen.core.retrieval.common.document.Document");
        sources.put("MultimodalDocument", "openjiuwen.core.retrieval.common.document.MultimodalDocument");
        sources.put("TextChunk", "openjiuwen.core.retrieval.common.document.TextChunk");
        sources.put("MultiKBRetrievalResult",
                "openjiuwen.core.retrieval.common.retrieval_result.MultiKBRetrievalResult");
        sources.put("RetrievalResult", "openjiuwen.core.retrieval.common.retrieval_result.RetrievalResult");
        sources.put("SearchResult", "openjiuwen.core.retrieval.common.retrieval_result.SearchResult");
        sources.put("Triple", "openjiuwen.core.retrieval.common.triple.Triple");
        sources.put("TripleBeam", "openjiuwen.core.retrieval.common.triple_beam.TripleBeam");
        sources.put("TripleMemory", "openjiuwen.core.retrieval.common.triple_memory.TripleMemory");
        sources.put("BaseCallback", "openjiuwen.core.retrieval.common.callbacks.BaseCallback");
        sources.put("TqdmCallback", "openjiuwen.core.retrieval.common.callbacks.TqdmCallback");
        sources.put("Embedding", "openjiuwen.core.retrieval.embedding.base.Embedding");
        sources.put("APIEmbedding", "openjiuwen.core.retrieval.embedding.api_embedding.APIEmbedding");
        sources.put("Reranker", "openjiuwen.core.retrieval.reranker.base.Reranker");
        sources.put("VectorStore", "openjiuwen.core.retrieval.vector_store.base.VectorStore");
        sources.put("create_vector_store", "openjiuwen.core.retrieval.vector_store.store.create_vector_store");
        sources.put("Indexer", "openjiuwen.core.retrieval.indexing.indexer.base.Indexer");
        sources.put("Processor", "openjiuwen.core.retrieval.indexing.processor.base.Processor");
        sources.put("Chunker", "openjiuwen.core.retrieval.indexing.processor.chunker.base.Chunker");
        sources.put("Extractor", "openjiuwen.core.retrieval.indexing.processor.extractor.base.Extractor");
        sources.put("Splitter", "openjiuwen.core.retrieval.indexing.processor.splitter.base.Splitter");
        sources.put("SentenceSplitter",
                "openjiuwen.core.retrieval.indexing.processor.splitter.splitter.SentenceSplitter");
        sources.put("TextSplitter",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_splitter.TextSplitter");
        sources.put("CharSplitter",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_splitter.CharSplitter");
        sources.put("IndexSentenceSplitter",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_splitter.IndexSentenceSplitter");
        sources.put("TextPreprocessor",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_preprocessor.TextPreprocessor");
        sources.put("WhitespaceNormalizer",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_preprocessor.WhitespaceNormalizer");
        sources.put("URLEmailRemover",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_preprocessor.URLEmailRemover");
        sources.put("SpecialCharacterNormalizer",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_preprocessor.SpecialCharacterNormalizer");
        sources.put("PreprocessingPipeline",
                "openjiuwen.core.retrieval.indexing.processor.chunker.text_preprocessor.PreprocessingPipeline");
        sources.put("TextChunker", "openjiuwen.core.retrieval.indexing.processor.chunker.chunking.TextChunker");
        sources.put("CharChunker",
                "openjiuwen.core.retrieval.indexing.processor.chunker.char_chunker.CharChunker");
        sources.put("HybridChunker", "openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker");
        sources.put("get_chunker", "openjiuwen.core.retrieval.indexing.processor.chunker.get_chunker");
        sources.put("register_chunker", "openjiuwen.core.retrieval.indexing.processor.chunker.register_chunker");
        sources.put("TokenizerChunker",
                "openjiuwen.core.retrieval.indexing.processor.chunker.tokenizer_chunker.TokenizerChunker");
        sources.put("TripleExtractor",
                "openjiuwen.core.retrieval.indexing.processor.extractor.triple_extractor.TripleExtractor");
        sources.put("Retriever", "openjiuwen.core.retrieval.retriever.base.Retriever");
        sources.put("VectorRetriever", "openjiuwen.core.retrieval.retriever.vector_retriever.VectorRetriever");
        sources.put("SparseRetriever", "openjiuwen.core.retrieval.retriever.sparse_retriever.SparseRetriever");
        sources.put("HybridRetriever", "openjiuwen.core.retrieval.retriever.hybrid_retriever.HybridRetriever");
        sources.put("GraphRetriever", "openjiuwen.core.retrieval.retriever.graph_retriever.GraphRetriever");
        sources.put("AgenticRetriever", "openjiuwen.core.retrieval.retriever.agentic_retriever.AgenticRetriever");
        sources.put("ConfigManager", "openjiuwen.core.retrieval.utils.config_manager.ConfigManager");
        sources.put("rrf_fusion", "openjiuwen.core.retrieval.utils.fusion.rrf_fusion");
        sources.put("deduplicate", "openjiuwen.core.retrieval.utils.common.deduplicate");
        sources.put("MilvusAUTO", "openjiuwen.core.foundation.store.vector_fields.milvus_fields.MilvusAUTO");
        sources.put("MilvusFLAT", "openjiuwen.core.foundation.store.vector_fields.milvus_fields.MilvusFLAT");
        sources.put("MilvusHNSW", "openjiuwen.core.foundation.store.vector_fields.milvus_fields.MilvusHNSW");
        sources.put("MilvusIVF", "openjiuwen.core.foundation.store.vector_fields.milvus_fields.MilvusIVF");
        sources.put("MilvusSCANN", "openjiuwen.core.foundation.store.vector_fields.milvus_fields.MilvusSCANN");
        sources.put("MilvusVectorStore", "openjiuwen.core.retrieval.vector_store.milvus_store.MilvusVectorStore");
        sources.put("MilvusIndexer", "openjiuwen.core.retrieval.indexing.indexer.milvus_indexer.MilvusIndexer");
        sources.put("ChromaIndexer", "openjiuwen.core.retrieval.indexing.indexer.chroma_indexer.ChromaIndexer");
        sources.put("ChromaVectorStore", "openjiuwen.core.retrieval.vector_store.chroma_store.ChromaVectorStore");
        sources.put("ChromaVectorField", "openjiuwen.core.foundation.store.vector_fields.chroma_fields.ChromaVectorField");
        sources.put("OpenAIEmbedding", "openjiuwen.core.retrieval.embedding.openai_embedding.OpenAIEmbedding");
        sources.put("VLLMEmbedding", "openjiuwen.core.retrieval.embedding.vllm_embedding.VLLMEmbedding");
        sources.put("DashscopeEmbedding", "openjiuwen.core.retrieval.embedding.dashscope_embedding.DashscopeEmbedding");
        sources.put("parse_base64_embedding", "openjiuwen.core.retrieval.embedding.utils.parse_base64_embedding");
        sources.put("StandardReranker", "openjiuwen.core.retrieval.reranker.standard_reranker.StandardReranker");
        sources.put("ChatReranker", "openjiuwen.core.retrieval.reranker.chat_reranker.ChatReranker");
        sources.put("DashscopeReranker", "openjiuwen.core.retrieval.reranker.dashscope_reranker.DashscopeReranker");
        sources.put("AutoFileParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.auto_file_parser.AutoFileParser");
        sources.put("AutoLinkParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.auto_link_parser.AutoLinkParser");
        sources.put("AutoParser", "openjiuwen.core.retrieval.indexing.processor.parser.auto_parser.AutoParser");
        sources.put("ExcelParser", "openjiuwen.core.retrieval.indexing.processor.parser.excel_parser.ExcelParser");
        sources.put("Parser", "openjiuwen.core.retrieval.indexing.processor.parser.base.Parser");
        sources.put("JSONParser", "openjiuwen.core.retrieval.indexing.processor.parser.json_parser.JSONParser");
        sources.put("PDFParser", "openjiuwen.core.retrieval.indexing.processor.parser.pdf_parser.PDFParser");
        sources.put("ImageParser", "openjiuwen.core.retrieval.indexing.processor.parser.image_parser.ImageParser");
        sources.put("TxtMdParser", "openjiuwen.core.retrieval.indexing.processor.parser.txt_md_parser.TxtMdParser");
        sources.put("WebPageParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.web_page_parser.WebPageParser");
        sources.put("WeChatArticleParser",
                "openjiuwen.core.retrieval.indexing.processor.parser.wechat_article_parser.WeChatArticleParser");
        sources.put("WordParser", "openjiuwen.core.retrieval.indexing.processor.parser.word_parser.WordParser");
        sources.put("KnowledgeBase", "openjiuwen.core.retrieval.knowledge_base.KnowledgeBase");
        sources.put("SimpleKnowledgeBase", "openjiuwen.core.retrieval.simple_knowledge_base.SimpleKnowledgeBase");
        sources.put("GraphKnowledgeBase", "openjiuwen.core.retrieval.graph_knowledge_base.GraphKnowledgeBase");
        sources.put("retrieve_multi_kb", "openjiuwen.core.retrieval.simple_knowledge_base.retrieve_multi_kb");
        sources.put("retrieve_multi_kb_with_source",
                "openjiuwen.core.retrieval.simple_knowledge_base.retrieve_multi_kb_with_source");
        sources.put("QueryRewriter", "openjiuwen.core.retrieval.query_rewriter.query_rewriter.QueryRewriter");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaSymbolNames() {
        Map<String, String> symbols = new LinkedHashMap<>();
        symbols.put("KnowledgeBaseConfig", "com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig");
        symbols.put("RetrievalConfig", "com.openjiuwen.core.retrieval.common.RetrievalConfig");
        symbols.put("IndexConfig", "com.openjiuwen.core.retrieval.common.IndexConfig");
        symbols.put("VectorStoreConfig", "com.openjiuwen.core.retrieval.common.VectorStoreConfig");
        symbols.put("EmbeddingConfig", "com.openjiuwen.core.retrieval.common.EmbeddingConfig");
        symbols.put("RerankerConfig", "com.openjiuwen.core.retrieval.common.RerankerConfig");
        symbols.put("Document", "com.openjiuwen.core.retrieval.common.Document");
        symbols.put("MultimodalDocument", "com.openjiuwen.core.retrieval.common.MultimodalDocument");
        symbols.put("TextChunk", "com.openjiuwen.core.retrieval.common.TextChunk");
        symbols.put("MultiKBRetrievalResult", "com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult");
        symbols.put("RetrievalResult", "com.openjiuwen.core.retrieval.common.RetrievalResult");
        symbols.put("SearchResult", "com.openjiuwen.core.retrieval.common.SearchResult");
        symbols.put("Triple", "com.openjiuwen.core.retrieval.common.Triple");
        symbols.put("TripleBeam", "com.openjiuwen.core.retrieval.common.TripleBeam");
        symbols.put("TripleMemory", "com.openjiuwen.core.retrieval.common.TripleMemory");
        symbols.put("BaseCallback", "com.openjiuwen.core.retrieval.common.BaseCallback");
        symbols.put("TqdmCallback", "com.openjiuwen.core.retrieval.common.TqdmCallback");
        symbols.put("Embedding", "com.openjiuwen.core.retrieval.embedding.Embedding");
        symbols.put("APIEmbedding", "com.openjiuwen.core.retrieval.embedding.APIEmbedding");
        symbols.put("Reranker", "com.openjiuwen.core.retrieval.reranker.Reranker");
        symbols.put("VectorStore", "com.openjiuwen.core.retrieval.vector_store.VectorStore");
        symbols.put("create_vector_store",
                "com.openjiuwen.core.retrieval.vector_store.RetrievalVectorStorePackage#createVectorStore");
        symbols.put("Indexer", "com.openjiuwen.core.retrieval.indexing.indexer.Indexer");
        symbols.put("Processor", "com.openjiuwen.core.retrieval.indexing.processor.Processor");
        symbols.put("Chunker", "com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker");
        symbols.put("Extractor", "com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor");
        symbols.put("Splitter", "com.openjiuwen.core.retrieval.indexing.processor.splitter.Splitter");
        symbols.put("SentenceSplitter", "com.openjiuwen.core.retrieval.indexing.processor.splitter.SentenceSplitter");
        symbols.put("TextSplitter", "com.openjiuwen.core.retrieval.indexing.processor.chunker.TextSplitter");
        symbols.put("CharSplitter", "com.openjiuwen.core.retrieval.indexing.processor.chunker.CharSplitter");
        symbols.put("IndexSentenceSplitter",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.IndexSentenceSplitter");
        symbols.put("TextPreprocessor", "com.openjiuwen.core.retrieval.indexing.processor.chunker.TextPreprocessor");
        symbols.put("WhitespaceNormalizer",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.WhitespaceNormalizer");
        symbols.put("URLEmailRemover", "com.openjiuwen.core.retrieval.indexing.processor.chunker.URLEmailRemover");
        symbols.put("SpecialCharacterNormalizer",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.SpecialCharacterNormalizer");
        symbols.put("PreprocessingPipeline",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.PreprocessingPipeline");
        symbols.put("TextChunker", "com.openjiuwen.core.retrieval.indexing.processor.chunker.TextChunker");
        symbols.put("CharChunker", "com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker");
        symbols.put("HybridChunker", "com.openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker");
        symbols.put("get_chunker", "com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerPackage#getChunker");
        symbols.put("register_chunker",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerPackage#registerChunker");
        symbols.put("TokenizerChunker", "com.openjiuwen.core.retrieval.indexing.processor.chunker.TokenizerChunker");
        symbols.put("TripleExtractor", "com.openjiuwen.core.retrieval.indexing.processor.extractor.TripleExtractor");
        symbols.put("Retriever", "com.openjiuwen.core.retrieval.retriever.Retriever");
        symbols.put("VectorRetriever", "com.openjiuwen.core.retrieval.retriever.VectorRetriever");
        symbols.put("SparseRetriever", "com.openjiuwen.core.retrieval.retriever.SparseRetriever");
        symbols.put("HybridRetriever", "com.openjiuwen.core.retrieval.retriever.HybridRetriever");
        symbols.put("GraphRetriever", "com.openjiuwen.core.retrieval.retriever.GraphRetriever");
        symbols.put("AgenticRetriever", "com.openjiuwen.core.retrieval.retriever.AgenticRetriever");
        symbols.put("ConfigManager", "com.openjiuwen.core.retrieval.utils.ConfigManager");
        symbols.put("rrf_fusion", "com.openjiuwen.core.retrieval.utils.FusionUtils#rrfFusionRetrieval");
        symbols.put("deduplicate", "com.openjiuwen.core.retrieval.utils.CommonUtils#deduplicate");
        symbols.put("MilvusAUTO", "com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO");
        symbols.put("MilvusFLAT", "com.openjiuwen.core.foundation.store.vector_fields.MilvusFLAT");
        symbols.put("MilvusHNSW", "com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW");
        symbols.put("MilvusIVF", "com.openjiuwen.core.foundation.store.vector_fields.MilvusIVF");
        symbols.put("MilvusSCANN", "com.openjiuwen.core.foundation.store.vector_fields.MilvusSCANN");
        symbols.put("MilvusVectorStore", "com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore");
        symbols.put("MilvusIndexer", "com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer");
        symbols.put("ChromaIndexer", "com.openjiuwen.core.retrieval.indexing.indexer.ChromaIndexer");
        symbols.put("ChromaVectorStore", "com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore");
        symbols.put("ChromaVectorField", "com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField");
        symbols.put("OpenAIEmbedding", "com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding");
        symbols.put("VLLMEmbedding", "com.openjiuwen.core.retrieval.embedding.VLLMEmbedding");
        symbols.put("DashscopeEmbedding", "com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding");
        symbols.put("parse_base64_embedding",
                "com.openjiuwen.core.retrieval.embedding.EmbeddingUtils#parseBase64Embedding");
        symbols.put("StandardReranker", "com.openjiuwen.core.retrieval.reranker.StandardReranker");
        symbols.put("ChatReranker", "com.openjiuwen.core.retrieval.reranker.ChatReranker");
        symbols.put("DashscopeReranker", "com.openjiuwen.core.retrieval.reranker.DashscopeReranker");
        symbols.put("AutoFileParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser");
        symbols.put("AutoLinkParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser");
        symbols.put("AutoParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser");
        symbols.put("ExcelParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser");
        symbols.put("Parser", "com.openjiuwen.core.retrieval.indexing.processor.parser.Parser");
        symbols.put("JSONParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.JSONParser");
        symbols.put("PDFParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser");
        symbols.put("ImageParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser");
        symbols.put("TxtMdParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser");
        symbols.put("WebPageParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser");
        symbols.put("WeChatArticleParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser");
        symbols.put("WordParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser");
        symbols.put("KnowledgeBase", "com.openjiuwen.core.retrieval.KnowledgeBase");
        symbols.put("SimpleKnowledgeBase", "com.openjiuwen.core.retrieval.SimpleKnowledgeBase");
        symbols.put("GraphKnowledgeBase", "com.openjiuwen.core.retrieval.GraphKnowledgeBase");
        symbols.put("retrieve_multi_kb", "com.openjiuwen.core.retrieval.SimpleKnowledgeBase#retrieveMultiKb");
        symbols.put("retrieve_multi_kb_with_source",
                "com.openjiuwen.core.retrieval.SimpleKnowledgeBase#retrieveMultiKbWithSource");
        symbols.put("QueryRewriter", "com.openjiuwen.core.retrieval.query_rewriter.QueryRewriter");
        return Collections.unmodifiableMap(symbols);
    }
}
