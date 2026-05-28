/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy loading helper for classes / functions involving heavy dependencies.
 * 
 * <p>Mirrors Python's openjiuwen.core.retrieval.lazy_load.py.</p>
 */
public final class LazyLoad {

    // Lazy attribute groups (matching Python's _LAZY_* lists)
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
            // Knowledge base classes
            "KnowledgeBase",
            "SimpleKnowledgeBase",
            "GraphKnowledgeBase",
            // Knowledge base functions
            "retrieve_multi_kb",
            "retrieve_multi_kb_with_source"
    );

    private static final List<String> LAZY_QUERY_REWRITER = List.of(
            "QueryRewriter"
    );

    /**
     * All lazy attributes combined.
     */
    public static final List<String> LAZY_ATTRIBUTES = Collections.unmodifiableList(
            concat(LAZY_MILVUS, LAZY_CHROMA, LAZY_OPENAI, LAZY_HTTPX, LAZY_PARSER, LAZY_KNOWLEDGE_BASE, LAZY_QUERY_REWRITER)
    );

    /**
     * Lazy import cache - maps attribute name to loaded Class.
     */
    public static final Map<String, Class<?>> LAZY_IMPORT_CACHE = new ConcurrentHashMap<>();

    // Initialize cache with null values for all attributes
    static {
        for (String attr : LAZY_ATTRIBUTES) {
            LAZY_IMPORT_CACHE.put(attr, null);
        }
    }

    private LazyLoad() {
        // Utility class
    }

    /**
     * Load HTTPX-related classes (Rerankers).
     */
    public static void loadHttpx() {
        loadClass("StandardReranker", "com.openjiuwen.core.retrieval.reranker.StandardReranker");
        loadClass("ChatReranker", "com.openjiuwen.core.retrieval.reranker.ChatReranker");
        loadClass("DashscopeReranker", "com.openjiuwen.core.retrieval.reranker.DashscopeReranker");
    }

    /**
     * Load OpenAI-related classes (Embeddings).
     */
    public static void loadOpenai() {
        loadClass("OpenAIEmbedding", "com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding");
        loadClass("VLLMEmbedding", "com.openjiuwen.core.retrieval.embedding.VLLMEmbedding");
        loadClass("DashscopeEmbedding", "com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding");
        loadClass("parse_base64_embedding", "com.openjiuwen.core.retrieval.embedding.EmbeddingUtils");
    }

    /**
     * Load Milvus-related classes.
     */
    public static void loadMilvus() {
        loadClass("MilvusIndexer", "com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer");
        loadClass("MilvusVectorStore", "com.openjiuwen.core.retrieval.vector_store.MilvusVectorStore");
        loadClass("MilvusAUTO", "com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO");
        loadClass("MilvusFLAT", "com.openjiuwen.core.foundation.store.vector_fields.MilvusFLAT");
        loadClass("MilvusHNSW", "com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW");
        loadClass("MilvusIVF", "com.openjiuwen.core.foundation.store.vector_fields.MilvusIVF");
        loadClass("MilvusSCANN", "com.openjiuwen.core.foundation.store.vector_fields.MilvusSCANN");
    }

    /**
     * Load Chroma-related classes.
     */
    public static void loadChroma() {
        loadClass("ChromaIndexer", "com.openjiuwen.core.retrieval.indexing.indexer.ChromaIndexer");
        loadClass("ChromaVectorStore", "com.openjiuwen.core.retrieval.vector_store.ChromaVectorStore");
        loadClass("ChromaVectorField", "com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField");
    }

    /**
     * Load Parser-related classes.
     */
    public static void loadParser() {
        loadClass("AutoFileParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser");
        loadClass("AutoLinkParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser");
        loadClass("AutoParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser");
        loadClass("ExcelParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser");
        loadClass("Parser", "com.openjiuwen.core.retrieval.indexing.processor.parser.Parser");
        loadClass("JSONParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.JSONParser");
        loadClass("PDFParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.PDFParser");
        loadClass("ImageParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.ImageParser");
        loadClass("TxtMdParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser");
        loadClass("WebPageParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser");
        loadClass("WeChatArticleParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser");
        loadClass("WordParser", "com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser");
    }

    /**
     * Load KnowledgeBase-related classes.
     */
    public static void loadKnowledgeBase() {
        loadClass("KnowledgeBase", "com.openjiuwen.core.retrieval.KnowledgeBase");
        loadClass("GraphKnowledgeBase", "com.openjiuwen.core.retrieval.GraphKnowledgeBase");
        loadClass("SimpleKnowledgeBase", "com.openjiuwen.core.retrieval.SimpleKnowledgeBase");
        // Functions are mapped to utility classes in Java
        loadClass("retrieve_multi_kb", "com.openjiuwen.core.retrieval.KnowledgeBaseUtils");
        loadClass("retrieve_multi_kb_with_source", "com.openjiuwen.core.retrieval.KnowledgeBaseUtils");
    }

    /**
     * Load QueryRewriter-related classes.
     */
    public static void loadQueryRewriter() {
        loadClass("QueryRewriter", "com.openjiuwen.core.retrieval.query_rewriter.QueryRewriter");
    }

    /**
     * Lazy loading for heavy modules in retrieval.
     *
     * @param name Attribute name to load
     * @return Loaded Class, or null if not found
     */
    public static Class<?> lazyLoad(String name) {
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

    /**
     * Helper to load a class by name and cache it.
     */
    private static void loadClass(String cacheKey, String className) {
        if (LAZY_IMPORT_CACHE.get(cacheKey) != null) {
            return; // Already loaded
        }
        try {
            Class<?> clazz = Class.forName(className);
            LAZY_IMPORT_CACHE.put(cacheKey, clazz);
        } catch (ClassNotFoundException e) {
            // Class not available, leave null in cache
            LAZY_IMPORT_CACHE.put(cacheKey, null);
        }
    }

    /**
     * Helper to concatenate multiple lists.
     */
    private static List<String> concat(List<String>... lists) {
        List<String> result = new java.util.ArrayList<>();
        for (List<String> list : lists) {
            result.addAll(list);
        }
        return result;
    }
}