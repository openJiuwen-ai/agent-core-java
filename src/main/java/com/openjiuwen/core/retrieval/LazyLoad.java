/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lazy loading helper for retrieval attributes that map to heavy dependencies.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval.lazy_load} in
 * {@code openjiuwen/core/retrieval/lazy_load.py}.</p>
 */
public final class LazyLoad {

    public static final String PYTHON_MODULE = "openjiuwen/core/retrieval/lazy_load.py";

    private static final List<String> LAZY_MILVUS = List.of(
            "MilvusAUTO",
            "MilvusFLAT",
            "MilvusHNSW",
            "MilvusIVF",
            "MilvusSCANN",
            "MilvusVectorStore",
            "MilvusIndexer"
    );

    private static final List<String> LAZY_CHROMA = List.of(
            "ChromaIndexer",
            "ChromaVectorStore",
            "ChromaVectorField"
    );

    private static final List<String> LAZY_OPENAI = List.of(
            "OpenAIEmbedding",
            "VLLMEmbedding",
            "DashscopeEmbedding",
            "parse_base64_embedding"
    );

    private static final List<String> LAZY_HTTPX = List.of(
            "StandardReranker",
            "ChatReranker",
            "DashscopeReranker"
    );

    private static final List<String> LAZY_PARSER = List.of(
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
            "WordParser"
    );

    private static final List<String> LAZY_KNOWLEDGE_BASE = List.of(
            "KnowledgeBase",
            "SimpleKnowledgeBase",
            "GraphKnowledgeBase",
            "retrieve_multi_kb",
            "retrieve_multi_kb_with_source"
    );

    private static final List<String> LAZY_QUERY_REWRITER = List.of("QueryRewriter");

    public static final List<String> LAZY_ATTRIBUTES = Collections.unmodifiableList(concat(
            LAZY_MILVUS,
            LAZY_CHROMA,
            LAZY_OPENAI,
            LAZY_HTTPX,
            LAZY_PARSER,
            LAZY_KNOWLEDGE_BASE,
            LAZY_QUERY_REWRITER
    ));

    /**
     * Mirrors Python's mutable {@code _LAZY_IMPORT_CACHE}; values may be {@code null},
     * {@link Class}, or {@link Method} because Python stores imported classes and functions.
     */
    public static final Map<String, Object> LAZY_IMPORT_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>());

    static {
        initializeCache();
    }


    private static final Map<String, String> PARSER_JAVA_SYMBOLS = parserJavaSymbols();
    private static final Map<String, String> RETRIEVAL_JAVA_SYMBOLS = retrievalJavaSymbols();

    private LazyLoad() {
    }

    public static void loadHttpx() {
        loadRetrievalSymbols(LAZY_HTTPX);
    }

    public static void loadOpenai() {
        loadRetrievalSymbols(LAZY_OPENAI);
    }

    public static void loadMilvus() {
        loadRetrievalSymbols(LAZY_MILVUS);
    }

    public static void loadChroma() {
        loadRetrievalSymbols(LAZY_CHROMA);
    }

    public static void loadParser() {
        for (Map.Entry<String, String> entry : PARSER_JAVA_SYMBOLS.entrySet()) {
            putCache(entry.getKey(), resolveJavaSymbol(entry.getValue()));
        }
    }

    public static void loadKnowledgeBase() {
        loadRetrievalSymbols(LAZY_KNOWLEDGE_BASE);
    }

    public static void loadQueryRewriter() {
        loadRetrievalSymbols(LAZY_QUERY_REWRITER);
    }

    /**
     * Lazy loading for heavy modules in retrieval.
     *
     * @param name attribute name to load
     * @return loaded class or method object, or {@code null} when the attribute is absent or not translated yet
     */
    public static Object lazyLoad(String name) {
        if (LAZY_OPENAI.contains(name)) {
            loadOpenai();
        } else if (LAZY_MILVUS.contains(name)) {
            loadMilvus();
        } else if (LAZY_CHROMA.contains(name)) {
            loadChroma();
        } else if (LAZY_HTTPX.contains(name)) {
            loadHttpx();
        } else if (LAZY_PARSER.contains(name)) {
            loadParser();
        } else if (LAZY_KNOWLEDGE_BASE.contains(name)) {
            loadKnowledgeBase();
        } else if (LAZY_QUERY_REWRITER.contains(name)) {
            loadQueryRewriter();
        }
        return LAZY_IMPORT_CACHE.get(name);
    }

    private static void initializeCache() {
        synchronized (LAZY_IMPORT_CACHE) {
            LAZY_IMPORT_CACHE.clear();
            for (String attr : LAZY_ATTRIBUTES) {
                LAZY_IMPORT_CACHE.put(attr, null);
            }
        }
    }

    private static void loadRetrievalSymbols(List<String> names) {
        for (String name : names) {
            putCache(name, resolveJavaSymbol(RETRIEVAL_JAVA_SYMBOLS.get(name)));
        }
    }

    private static void putCache(String name, Object value) {
        LAZY_IMPORT_CACHE.put(name, value);
    }

    private static Object resolveJavaSymbol(String javaSymbolName) {
        if (javaSymbolName == null || javaSymbolName.isBlank()) {
            return null;
        }
        int memberSeparator = javaSymbolName.indexOf('#');
        if (memberSeparator < 0) {
            return resolveClass(javaSymbolName);
        }
        String className = javaSymbolName.substring(0, memberSeparator);
        String methodName = javaSymbolName.substring(memberSeparator + 1);
        return resolveStaticMethod(className, methodName);
    }

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className, false, LazyLoad.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    private static Method resolveStaticMethod(String className, String methodName) {
        Class<?> type = resolveClass(className);
        if (type == null) {
            return null;
        }
        Method selected = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (selected == null || compareMethods(method, selected) < 0) {
                selected = method;
            }
        }
        return selected;
    }

    private static int compareMethods(Method left, Method right) {
        int parameterCompare = Integer.compare(left.getParameterCount(), right.getParameterCount());
        if (parameterCompare != 0) {
            return parameterCompare;
        }
        return left.toGenericString().compareTo(right.toGenericString());
    }


    private static Map<String, String> parserJavaSymbols() {
        Map<String, String> javaSymbols = new LinkedHashMap<>();
        javaSymbols.put("AutoFileParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser");
        javaSymbols.put("AutoLinkParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser");
        javaSymbols.put("AutoParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser");
        javaSymbols.put("ExcelParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser");
        javaSymbols.put("HTMLFileParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.HTMLFileParser");
        javaSymbols.put("Parser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.Parser");
        javaSymbols.put("JSONParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.JsonParser");
        javaSymbols.put("PDFParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser");
        javaSymbols.put("TxtMdParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser");
        javaSymbols.put("WebPageParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser");
        javaSymbols.put("WordParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser");
        javaSymbols.put("WeChatArticleParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser");
        javaSymbols.put("ImageParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser");
        javaSymbols.put("parse_wechat_article_url",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser#parseWechatArticleUrl");
        javaSymbols.put("parse_web_page_url",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser#parseWebPageUrl");
        return Collections.unmodifiableMap(javaSymbols);
    }

    private static Map<String, String> retrievalJavaSymbols() {
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
                "com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory#createVectorStore");
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
        symbols.put("get_chunker",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry#getChunker");
        symbols.put("register_chunker",
                "com.openjiuwen.core.retrieval.indexing.processor.chunker.ChunkerRegistry#registerChunker");
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
        symbols.put("JSONParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.JsonParser");
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

    private static List<String> concat(List<String>... lists) {
        List<String> result = new ArrayList<>();
        for (List<String> list : lists) {
            result.addAll(list);
        }
        return result;
    }
}
