/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.retrieval.indexing.processor.parser.ParserPackage;

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
        for (String name : ParserPackage.all()) {
            putCache(name, resolveJavaSymbol(ParserPackage.javaSymbolNameFor(name)));
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
            putCache(name, resolveJavaSymbol(RetrievalPackage.javaSymbolNameFor(name)));
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

    private static List<String> concat(List<String>... lists) {
        List<String> result = new ArrayList<>();
        for (List<String> list : lists) {
            result.addAll(list);
        }
        return result;
    }
}
