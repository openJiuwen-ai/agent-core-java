/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.openjiuwen.core.foundation.store.db.DefaultDbStore;
import com.openjiuwen.core.foundation.store.kv.DbBasedKVStore;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Foundation store package facade and vector-store factory registry.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.store} module in
 * {@code openjiuwen/core/foundation/store/__init__.py}.</p>
 */
public final class FoundationStorePackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/store/__init__.py";

    public static final String VECTOR_STORE_ENTRY_POINT_GROUP = "openjiuwen.vector_stores";

    public static final Set<String> BUILTIN_VECTOR_STORE_NAMES = Set.of("chroma", "milvus", "gaussvector");

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BaseKVStore",
            "BaseMessageStore",
            "MessageMetadata",
            "BaseVectorStore",
            "InMemoryKVStore",
            "VectorSearchResult",
            "CollectionSchema",
            "FieldSchema",
            "VectorDataType",
            "create_vector_store",
            "register_vector_store",
            "VECTOR_STORE_ENTRY_POINT_GROUP",
            "vector_fields",
            "vector",
            "query",
            "object",
            "kv",
            "graph",
            "db",
            "BaseDbStore",
            "DbBasedKVStore",
            "DefaultDbStore"
    );

    public static final List<String> LAZY_ATTRIBUTES = List.of(
            "BaseDbStore",
            "DbBasedKVStore",
            "DefaultDbStore"
    );

    private static final Logger LOGGER = Logger.getLogger(FoundationStorePackage.class.getName());
    private static final Map<String, VectorStoreFactory> CUSTOM_VECTOR_STORES = new LinkedHashMap<>();
    private static List<VectorStoreProvider> vectorStoreProvidersForTest;
    private static final Map<String, String> BUILTIN_CLASS_NAMES = Map.of(
            "chroma", "com.openjiuwen.core.foundation.store.vector.ChromaVectorStore",
            "milvus", "com.openjiuwen.core.foundation.store.vector.MilvusVectorStore",
            "gaussvector", "com.openjiuwen.core.foundation.store.vector.GaussVectorStore"
    );

    private FoundationStorePackage() {
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
     * Mirrors Python's {@code __dir__}, including the duplicated lazy attributes.
     *
     * @return directory symbols in Python order
     */
    public static List<String> dirSymbols() {
        List<String> symbols = new ArrayList<>(EXPORTED_SYMBOLS);
        symbols.addAll(LAZY_ATTRIBUTES);
        return symbols;
    }

    /**
     * Register a vector-store backend at runtime.
     *
     * @param name backend identifier
     * @param factory factory invoked with keyword-style arguments
     */
    public static void registerVectorStore(String name, VectorStoreFactory factory) {
        CUSTOM_VECTOR_STORES.put(name, factory);
    }

    /**
     * Python-compatible alias for {@link #registerVectorStore(String, VectorStoreFactory)}.
     *
     * @param name backend identifier
     * @param factory factory invoked with keyword-style arguments
     */
    public static void register_vector_store(String name, VectorStoreFactory factory) {
        registerVectorStore(name, factory);
    }

    /**
     * Factory for vector-store backends.
     *
     * @param storeType backend identifier
     * @param kwargs keyword-style constructor arguments
     * @return matching vector store, or {@code null} when none matches
     */
    public static BaseVectorStore createVectorStore(String storeType, Map<String, Object> kwargs) {
        Map<String, Object> safeKwargs = kwargs == null ? Collections.emptyMap() : kwargs;
        if (BUILTIN_VECTOR_STORE_NAMES.contains(storeType)) {
            return resolveBuiltin(storeType, safeKwargs);
        }
        VectorStoreFactory customFactory = CUSTOM_VECTOR_STORES.get(storeType);
        if (customFactory != null) {
            return customFactory.create(safeKwargs);
        }
        return resolveServiceProvider(storeType, safeKwargs);
    }

    /**
     * Python-compatible alias for {@link #createVectorStore(String, Map)}.
     *
     * @param storeType backend identifier
     * @param kwargs keyword-style constructor arguments
     * @return matching vector store, or {@code null} when none matches
     */
    public static BaseVectorStore create_vector_store(String storeType, Map<String, Object> kwargs) {
        return createVectorStore(storeType, kwargs);
    }

    /**
     * Resolve SQL-related lazy attributes without importing them at package load time in Python.
     *
     * @param name lazy attribute name
     * @return Java class corresponding to the lazy Python attribute
     */
    public static Class<?> resolveLazyAttribute(String name) {
        return switch (name) {
            case "BaseDbStore" -> BaseDbStore.class;
            case "DbBasedKVStore" -> DbBasedKVStore.class;
            case "DefaultDbStore" -> DefaultDbStore.class;
            default -> throw new IllegalArgumentException("Unsupported foundation.store attribute: " + name);
        };
    }

    public static boolean isBuiltinVectorStore(String storeType) {
        return BUILTIN_VECTOR_STORE_NAMES.contains(storeType);
    }

    static void clearCustomVectorStoresForTest() {
        CUSTOM_VECTOR_STORES.clear();
        vectorStoreProvidersForTest = null;
    }

    static void setVectorStoreProvidersForTest(List<VectorStoreProvider> providers) {
        vectorStoreProvidersForTest = providers == null ? null : List.copyOf(providers);
    }

    private static BaseVectorStore resolveBuiltin(String storeType, Map<String, Object> kwargs) {
        String className = BUILTIN_CLASS_NAMES.get(storeType);
        return instantiateVectorStore(className, kwargs);
    }

    private static BaseVectorStore resolveServiceProvider(String storeType, Map<String, Object> kwargs) {
        try {
            Iterable<VectorStoreProvider> providers = vectorStoreProvidersForTest == null
                    ? ServiceLoader.load(VectorStoreProvider.class)
                    : vectorStoreProvidersForTest;
            for (VectorStoreProvider provider : providers) {
                if (!provider.name().equals(storeType)) {
                    continue;
                }
                try {
                    return provider.create(kwargs);
                } catch (RuntimeException exception) {
                    LOGGER.log(Level.WARNING,
                            "Vector-store plugin '" + storeType + "' loaded but failed to instantiate", exception);
                    return null;
                }
            }
        } catch (ServiceConfigurationError error) {
            LOGGER.log(Level.WARNING,
                    "Failed to enumerate entry_points for " + VECTOR_STORE_ENTRY_POINT_GROUP, error);
        }
        return null;
    }

    private static BaseVectorStore instantiateVectorStore(String className, Map<String, Object> kwargs) {
        try {
            Class<?> type = Class.forName(className);
            if (!BaseVectorStore.class.isAssignableFrom(type)) {
                throw new IllegalStateException(className + " is not a BaseVectorStore");
            }

            @SuppressWarnings("unchecked")
            Class<? extends BaseVectorStore> vectorStoreType = (Class<? extends BaseVectorStore>) type;
            Constructor<? extends BaseVectorStore> mapConstructor = findMapConstructor(vectorStoreType);
            if (mapConstructor != null) {
                return mapConstructor.newInstance(kwargs);
            }
            return vectorStoreType.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to resolve built-in vector store '" + className + "'", exception);
        }
    }

    private static Constructor<? extends BaseVectorStore> findMapConstructor(Class<? extends BaseVectorStore> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 1 && Map.class.isAssignableFrom(parameterTypes[0])) {
                @SuppressWarnings("unchecked")
                Constructor<? extends BaseVectorStore> typedConstructor =
                        (Constructor<? extends BaseVectorStore>) constructor;
                typedConstructor.setAccessible(true);
                return typedConstructor;
            }
        }
        return null;
    }

    /**
     * Runtime vector-store factory, mirroring Python's callable registry value.
     */
    @FunctionalInterface
    public interface VectorStoreFactory {
        BaseVectorStore create(Map<String, Object> kwargs);
    }

    /**
     * ServiceLoader-backed equivalent of Python entry-points in group {@code openjiuwen.vector_stores}.
     */
    public interface VectorStoreProvider {
        String name();

        BaseVectorStore create(Map<String, Object> kwargs);
    }
}
