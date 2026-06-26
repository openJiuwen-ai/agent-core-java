/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Factory for creating data containers by type name.
 *
 * <p>Mirrors Python's {@code DataContainerFactory} in
 * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
 */
public final class DataContainerFactory {

    public static final String DEFAULT_DATA_CONTAINER_TYPE = "agent";

    private static final Map<String, DataContainerProvider> REGISTRY = new LinkedHashMap<>();

    static {
        register(DEFAULT_DATA_CONTAINER_TYPE, AgentSessionContainer.provider());
    }

    private DataContainerFactory() {
    }

    public static DataContainerProvider register(String dataContainerType, DataContainerProvider provider) {
        if (dataContainerType == null || dataContainerType.isBlank()) {
            throw new IllegalArgumentException("data_container_type is required");
        }
        if (provider == null) {
            throw new IllegalArgumentException("container provider is required");
        }
        synchronized (REGISTRY) {
            REGISTRY.put(dataContainerType, provider);
        }
        return provider;
    }

    public static DataContainer create() {
        return create(DEFAULT_DATA_CONTAINER_TYPE, Map.of());
    }

    public static DataContainer create(String dataContainerType) {
        return create(dataContainerType, Map.of());
    }

    public static DataContainer create(String dataContainerType, Map<String, Object> kwargs) {
        return providerFor(dataContainerType).create(safeKwargs(kwargs));
    }

    public static CompletionStage<DataContainer> load(String dataContainerType,
                                                      String agentId,
                                                      String sessionId,
                                                      Object serialized) {
        return load(dataContainerType, agentId, sessionId, serialized, Map.of());
    }

    public static CompletionStage<DataContainer> load(String dataContainerType,
                                                      String agentId,
                                                      String sessionId,
                                                      Object serialized,
                                                      Map<String, Object> kwargs) {
        return providerFor(dataContainerType).load(agentId, sessionId, serialized, safeKwargs(kwargs));
    }

    public static boolean has(String dataContainerType) {
        synchronized (REGISTRY) {
            return REGISTRY.containsKey(dataContainerType);
        }
    }

    public static List<String> listTypes() {
        synchronized (REGISTRY) {
            return new ArrayList<>(REGISTRY.keySet());
        }
    }

    public static Map<String, DataContainerProvider> registrySnapshot() {
        synchronized (REGISTRY) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(REGISTRY));
        }
    }

    private static DataContainerProvider providerFor(String dataContainerType) {
        synchronized (REGISTRY) {
            DataContainerProvider provider = REGISTRY.get(dataContainerType);
            if (provider != null) {
                return provider;
            }
            throw new IllegalArgumentException(
                    "Unknown data_container_type: '" + dataContainerType + "'. Available types: " + listTypes()
            );
        }
    }

    private static Map<String, Object> safeKwargs(Map<String, Object> kwargs) {
        return kwargs == null ? Map.of() : kwargs;
    }

    /**
     * Provider surface used to mirror Python class construction and classmethod load.
     *
     * <p>Mirrors Python's registered {@code type[DataContainer]} in
     * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
     */
    public interface DataContainerProvider {
        DataContainer create(Map<String, Object> kwargs);

        CompletionStage<DataContainer> load(String agentId, String sessionId, Object serialized,
                                            Map<String, Object> kwargs);
    }
}
