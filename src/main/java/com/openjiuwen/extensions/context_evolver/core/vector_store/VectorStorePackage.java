/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.vector_store;

import java.util.List;

/**
 * Package bridge for vector store exports.
 *
 * <p>Mirrors Python's {@code openjiuwen/extensions/context_evolver/core/vector_store/__init__.py}.</p>
 */
public final class VectorStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/extensions/context_evolver/core/vector_store/__init__.py";
    public static final String DESCRIPTION = "Vector store module for memory persistence.";
    public static final Class<MemoryVectorStore> MEMORY_VECTOR_STORE = MemoryVectorStore.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of("MemoryVectorStore");

    private VectorStorePackage() {
    }
}
