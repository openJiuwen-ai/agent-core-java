/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module facade and dynamic config registries for team blueprints.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/agent_teams/schema/blueprint.py}.</p>
 */
public final class TeamBlueprintPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/schema/blueprint.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "DeepAgentSpec",
            "ExternalCliAgentSpec",
            "LeaderSpec",
            "PredefinedMemberSpec",
            "StorageSpec",
            "TeamAgentSpec",
            "TransportSpec",
            "register_storage",
            "register_transport"
    );

    private static final Map<String, ConfigFactory<?>> TRANSPORT_REGISTRY = new LinkedHashMap<>();
    private static final Map<String, ConfigFactory<?>> STORAGE_REGISTRY = new LinkedHashMap<>();

    private TeamBlueprintPackage() {
    }

    public static void registerTransport(String name, ConfigFactory<?> factory) {
        TRANSPORT_REGISTRY.put(name, factory);
    }

    public static void registerStorage(String name, ConfigFactory<?> factory) {
        STORAGE_REGISTRY.put(name, factory);
    }

    public static Object buildTransport(String type, Map<String, Object> params) {
        ensureBuiltinInfraRegistered();
        ConfigFactory<?> factory = TRANSPORT_REGISTRY.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown transport type '" + type + "'. Registered types: " + TRANSPORT_REGISTRY.keySet());
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("backend", type);
        if (params != null) {
            merged.putAll(params);
        }
        return factory.build(merged);
    }

    public static Object buildStorage(String type, Map<String, Object> params) {
        ensureBuiltinInfraRegistered();
        ConfigFactory<?> factory = STORAGE_REGISTRY.get(type);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown storage type '" + type + "'. Registered types: " + STORAGE_REGISTRY.keySet());
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("db_type", type);
        if (params != null) {
            merged.putAll(params);
        }
        return factory.build(merged);
    }

    public static Map<String, ConfigFactory<?>> transportRegistryView() {
        ensureBuiltinInfraRegistered();
        return Map.copyOf(TRANSPORT_REGISTRY);
    }

    public static Map<String, ConfigFactory<?>> storageRegistryView() {
        ensureBuiltinInfraRegistered();
        return Map.copyOf(STORAGE_REGISTRY);
    }

    public static Class<AgentConfigurator.DeepAgentSpec> deepAgentSpecClass() {
        return AgentConfigurator.DeepAgentSpec.class;
    }

    public static Class<AgentConfigurator.TeamMemberSpec> predefinedMemberSpecClass() {
        return AgentConfigurator.TeamMemberSpec.class;
    }

    static void ensureBuiltinInfraRegistered() {
        if (TRANSPORT_REGISTRY.isEmpty()) {
            TRANSPORT_REGISTRY.put("inprocess", TeamBlueprintPackage::messagerTransportConfig);
            TRANSPORT_REGISTRY.put("pyzmq", TeamBlueprintPackage::messagerTransportConfig);
        }
        if (STORAGE_REGISTRY.isEmpty()) {
            STORAGE_REGISTRY.put("sqlite", TeamBlueprintPackage::databaseConfig);
            STORAGE_REGISTRY.put("postgresql", TeamBlueprintPackage::databaseConfig);
            STORAGE_REGISTRY.put("mysql", TeamBlueprintPackage::databaseConfig);
            STORAGE_REGISTRY.put("memory", TeamBlueprintPackage::dynamicConfigMap);
        }
    }

    private static MessagerTransportConfig messagerTransportConfig(Map<String, Object> values) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        setString(values, "backend", config::setBackend);
        setString(values, "team_name", config::setTeamName);
        setString(values, "teamName", config::setTeamName);
        setString(values, "node_id", config::setNodeId);
        setString(values, "nodeId", config::setNodeId);
        setString(values, "direct_addr", config::setDirectAddr);
        setString(values, "directAddr", config::setDirectAddr);
        setString(values, "pubsub_publish_addr", config::setPubsubPublishAddr);
        setString(values, "pubsubPublishAddr", config::setPubsubPublishAddr);
        setString(values, "pubsub_subscribe_addr", config::setPubsubSubscribeAddr);
        setString(values, "pubsubSubscribeAddr", config::setPubsubSubscribeAddr);
        Object metadata = values.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            config.setMetadata(stringMap(map));
        }
        return config;
    }

    private static DatabaseConfig databaseConfig(Map<String, Object> values) {
        DatabaseConfig config = new DatabaseConfig();
        Object dbType = values.get("db_type");
        config.setDbType(DatabaseType.fromValue(dbType == null ? null : String.valueOf(dbType)));
        setString(values, "connection_string", config::setConnectionString);
        setString(values, "connectionString", config::setConnectionString);
        Object timeout = values.get("db_timeout");
        if (timeout instanceof Number number) {
            config.setDbTimeout(number.intValue());
        }
        Object wal = values.get("db_enable_wal");
        if (wal instanceof Boolean enabled) {
            config.setDbEnableWal(enabled);
        }
        return config;
    }

    private static Map<String, Object> dynamicConfigMap(Map<String, Object> values) {
        return new LinkedHashMap<>(values);
    }

    private static Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static void setString(Map<String, Object> values, String key, java.util.function.Consumer<String> setter) {
        Object value = values.get(key);
        if (value != null) {
            setter.accept(String.valueOf(value));
        }
    }

    /**
     * Dynamic factory for pydantic-like infrastructure config objects.
     *
     * <p>Mirrors Python registry values in
     * {@code openjiuwen/agent_teams/schema/blueprint.py}.</p>
     */
    @FunctionalInterface
    public interface ConfigFactory<T> {
        T build(Map<String, Object> values);
    }
}
