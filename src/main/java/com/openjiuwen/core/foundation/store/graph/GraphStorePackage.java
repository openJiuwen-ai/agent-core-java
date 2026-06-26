/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for graph store exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.store.graph} package facade in
 * {@code openjiuwen/core/foundation/store/graph/__init__.py}.</p>
 */
public final class GraphStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/store/graph/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "GraphStore",
            "GraphStoreFactory",
            "GraphConfig",
            "GraphStoreIndexConfig",
            "GraphStoreStorageConfig",
            "ENTITY_COLLECTION",
            "EPISODE_COLLECTION",
            "RELATION_COLLECTION",
            "Entity",
            "Episode",
            "Relation"
    );

    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private GraphStorePackage() {
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
     * Checks whether a symbol is re-exported by Python {@code __all__}.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the Java type name expected to mirror the Python object.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or {@code null} when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("GraphStore", "openjiuwen.core.foundation.store.graph.base_graph_store.GraphStore");
        sources.put("GraphStoreFactory", "openjiuwen.core.foundation.store.graph.base.GraphStoreFactory");
        sources.put("GraphConfig", "openjiuwen.core.foundation.store.graph.config.GraphConfig");
        sources.put("GraphStoreIndexConfig", "openjiuwen.core.foundation.store.graph.config.GraphStoreIndexConfig");
        sources.put("GraphStoreStorageConfig", "openjiuwen.core.foundation.store.graph.config.GraphStoreStorageConfig");
        sources.put("ENTITY_COLLECTION", "openjiuwen.core.foundation.store.graph.constants.ENTITY_COLLECTION");
        sources.put("EPISODE_COLLECTION", "openjiuwen.core.foundation.store.graph.constants.EPISODE_COLLECTION");
        sources.put("RELATION_COLLECTION", "openjiuwen.core.foundation.store.graph.constants.RELATION_COLLECTION");
        sources.put("Entity", "openjiuwen.core.foundation.store.graph.graph_object.Entity");
        sources.put("Episode", "openjiuwen.core.foundation.store.graph.graph_object.Episode");
        sources.put("Relation", "openjiuwen.core.foundation.store.graph.graph_object.Relation");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("GraphStore", "com.openjiuwen.core.foundation.store.graph.GraphStore");
        javaTypeNames.put("GraphStoreFactory", "com.openjiuwen.core.foundation.store.graph.GraphStoreFactory");
        javaTypeNames.put("GraphConfig", "com.openjiuwen.core.foundation.store.graph.GraphConfig");
        javaTypeNames.put("GraphStoreIndexConfig", "com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig");
        javaTypeNames.put(
                "GraphStoreStorageConfig",
                "com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig"
        );
        javaTypeNames.put("ENTITY_COLLECTION", "com.openjiuwen.core.foundation.store.graph.GraphStoreConstants#ENTITY_COLLECTION");
        javaTypeNames.put("EPISODE_COLLECTION", "com.openjiuwen.core.foundation.store.graph.GraphStoreConstants#EPISODE_COLLECTION");
        javaTypeNames.put("RELATION_COLLECTION", "com.openjiuwen.core.foundation.store.graph.GraphStoreConstants#RELATION_COLLECTION");
        javaTypeNames.put("Entity", "com.openjiuwen.core.foundation.store.graph.Entity");
        javaTypeNames.put("Episode", "com.openjiuwen.core.foundation.store.graph.Episode");
        javaTypeNames.put("Relation", "com.openjiuwen.core.foundation.store.graph.Relation");
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
