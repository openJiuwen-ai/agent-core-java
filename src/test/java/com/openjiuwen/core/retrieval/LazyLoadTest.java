/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.indexing.processor.parser.ParserPackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for retrieval lazy loading.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.retrieval.lazy_load} in
 * {@code openjiuwen/core/retrieval/lazy_load.py}.</p>
 */
class LazyLoadTest {

    @BeforeEach
    void resetCache() {
        synchronized (LazyLoad.LAZY_IMPORT_CACHE) {
            LazyLoad.LAZY_IMPORT_CACHE.clear();
            for (String name : LazyLoad.LAZY_ATTRIBUTES) {
                LazyLoad.LAZY_IMPORT_CACHE.put(name, null);
            }
        }
    }

    @Test
    void lazyAttributesMatchPythonOrderAndInitialCache() {
        List<String> expected = expectedLazyAttributes();

        assertEquals("openjiuwen/core/retrieval/lazy_load.py", LazyLoad.PYTHON_MODULE);
        assertEquals(expected, LazyLoad.LAZY_ATTRIBUTES);
        assertEquals(expected, new ArrayList<>(LazyLoad.LAZY_IMPORT_CACHE.keySet()));
        assertTrue(LazyLoad.LAZY_IMPORT_CACHE.values().stream().allMatch(value -> value == null));
    }

    @Test
    void unknownNameReturnsNoneWithoutMutatingCache() {
        List<String> before = new ArrayList<>(LazyLoad.LAZY_IMPORT_CACHE.keySet());

        assertNull(LazyLoad.lazyLoad("MissingRetrievalSymbol"));

        assertEquals(before, new ArrayList<>(LazyLoad.LAZY_IMPORT_CACHE.keySet()));
        assertFalse(LazyLoad.LAZY_IMPORT_CACHE.containsKey("MissingRetrievalSymbol"));
    }

    @Test
    void openaiGroupCachesClassesAndFunctionMethod() {
        Object loaded = LazyLoad.lazyLoad("parse_base64_embedding");

        Method parseBase64Embedding = assertInstanceOf(Method.class, loaded);
        assertEquals("parseBase64Embedding", parseBase64Embedding.getName());
        assertSame(parseBase64Embedding, LazyLoad.LAZY_IMPORT_CACHE.get("parse_base64_embedding"));
        assertCachedClassNameIfPresent(
                "OpenAIEmbedding",
                "com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding"
        );
        assertCachedClassNameIfPresent(
                "VLLMEmbedding",
                "com.openjiuwen.core.retrieval.embedding.VLLMEmbedding"
        );
        assertCachedClassNameIfPresent(
                "DashscopeEmbedding",
                "com.openjiuwen.core.retrieval.embedding.DashscopeEmbedding"
        );
        assertNull(LazyLoad.LAZY_IMPORT_CACHE.get("QueryRewriter"));
    }

    @Test
    void milvusGroupCachesAcceptedVectorFieldClasses() {
        Object loaded = LazyLoad.lazyLoad("MilvusAUTO");

        assertCachedClassName(loaded, "com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO");
        assertCachedClassNameIfPresent(
                "MilvusIndexer",
                "com.openjiuwen.core.retrieval.indexing.indexer.MilvusIndexer"
        );
        assertTrue(LazyLoad.LAZY_IMPORT_CACHE.containsKey("MilvusVectorStore"));
        assertNull(LazyLoad.LAZY_IMPORT_CACHE.get("OpenAIEmbedding"));
    }

    @Test
    void parserLoadCopiesParserPackageAllIntoCache() {
        Object loaded = LazyLoad.lazyLoad("AutoFileParser");

        assertCachedClassName(
                loaded,
                "com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser"
        );
        for (String name : ParserPackage.all()) {
            assertTrue(LazyLoad.LAZY_IMPORT_CACHE.containsKey(name), name);
        }
        assertCachedClassNameIfPresent(
                "HTMLFileParser",
                "com.openjiuwen.core.retrieval.indexing.processor.parser.HTMLFileParser"
        );
        Method parseWebPageUrl = assertInstanceOf(
                Method.class,
                LazyLoad.LAZY_IMPORT_CACHE.get("parse_web_page_url")
        );
        assertEquals("parseWebPageUrl", parseWebPageUrl.getName());
    }

    @Test
    void knowledgeBaseFunctionExportsCacheMethodObjects() {
        Object loaded = LazyLoad.lazyLoad("retrieve_multi_kb");

        Method retrieveMultiKb = assertInstanceOf(Method.class, loaded);
        assertEquals("retrieveMultiKb", retrieveMultiKb.getName());
        assertCachedClassNameIfPresent(
                "KnowledgeBase",
                "com.openjiuwen.core.retrieval.KnowledgeBase"
        );
        assertCachedClassNameIfPresent(
                "SimpleKnowledgeBase",
                "com.openjiuwen.core.retrieval.SimpleKnowledgeBase"
        );
        Method retrieveMultiKbWithSource = assertInstanceOf(
                Method.class,
                LazyLoad.LAZY_IMPORT_CACHE.get("retrieve_multi_kb_with_source")
        );
        assertEquals("retrieveMultiKbWithSource", retrieveMultiKbWithSource.getName());
    }

    private static void assertCachedClassNameIfPresent(String cacheKey, String expectedClassName) {
        Object cached = LazyLoad.LAZY_IMPORT_CACHE.get(cacheKey);
        if (cached != null) {
            assertCachedClassName(cached, expectedClassName);
        }
    }

    private static void assertCachedClassName(Object cached, String expectedClassName) {
        Class<?> cachedClass = assertInstanceOf(Class.class, cached);
        assertEquals(expectedClassName, cachedClass.getName());
    }

    private static List<String> expectedLazyAttributes() {
        return List.of(
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
    }
}
