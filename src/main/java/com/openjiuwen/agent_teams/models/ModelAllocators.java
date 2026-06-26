/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Factory and resolver functions for model allocation.
 *
 * <p>Mirrors Python's module functions {@code _pool_digest},
 * {@code build_model_allocator}, and {@code resolve_member_model} in
 * {@code openjiuwen/agent_teams/models/allocator.py}.</p>
 */
public final class ModelAllocators {

    private ModelAllocators() {
    }

    public static String poolDigest(List<ModelPoolEntry> pool) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (ModelPoolEntry entry : pool == null ? List.<ModelPoolEntry>of() : pool) {
                digest.update(nullToEmpty(entry.getModelName()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(nullToEmpty(entry.getApiBaseUrl()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1f);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("SHA-1 digest is unavailable", exc);
        }
    }

    public static ModelAllocator buildModelAllocator(
            AgentConfigurator.TeamAgentSpec spec,
            AgentConfigurator.TeamSpec teamSpec
    ) {
        if (teamSpec == null) {
            return null;
        }
        List<ModelPoolEntry> pool = asPool(teamSpec.getModelPool());
        if (pool.isEmpty()) {
            return null;
        }
        String strategy = teamSpec.getModelPoolStrategy();
        if (strategy == null || strategy.isBlank() || "round_robin".equals(strategy)) {
            return new RoundRobinModelAllocator(pool);
        }
        if ("by_model_name".equals(strategy)) {
            return new ByModelNameAllocator(pool);
        }
        if ("router".equals(strategy)) {
            return new RouterAllocator(pool);
        }
        throw new IllegalArgumentException(
                "Unknown model_pool_strategy '" + strategy
                        + "'; expected one of: round_robin, by_model_name, router");
    }

    public static ModelPoolEntry.TeamModelConfig resolveMemberModel(
            AgentConfigurator.TeamSpec teamSpec,
            String modelName,
            Integer modelIndex
    ) {
        return resolveMemberModel(teamSpec == null ? List.of() : asPool(teamSpec.getModelPool()), modelName, modelIndex);
    }

    public static ModelPoolEntry.TeamModelConfig resolveMemberModel(
            List<ModelPoolEntry> pool,
            String modelName,
            Integer modelIndex
    ) {
        if (pool == null || pool.isEmpty() || modelName == null || modelName.isBlank()) {
            return null;
        }
        List<ModelPoolEntry> group = new ArrayList<>();
        for (ModelPoolEntry entry : pool) {
            if (modelName.equals(entry.getModelName())) {
                group.add(entry);
            }
        }
        if (group.isEmpty()) {
            return null;
        }
        int idx = modelIndex != null && modelIndex >= 0 && modelIndex < group.size() ? modelIndex : 0;
        return group.get(idx).toTeamModelConfig();
    }

    static List<ModelPoolEntry> asPool(List<?> rawPool) {
        List<ModelPoolEntry> pool = new ArrayList<>();
        if (rawPool == null) {
            return pool;
        }
        for (Object item : rawPool) {
            if (item instanceof ModelPoolEntry entry) {
                pool.add(entry);
            } else if (item instanceof Map<?, ?> map) {
                pool.add(entryFromMap(map));
            } else {
                throw new IllegalArgumentException(
                        "model_pool entries must be ModelPoolEntry or map values, got "
                                + item.getClass().getName());
            }
        }
        return pool;
    }

    @SuppressWarnings("unchecked")
    private static ModelPoolEntry entryFromMap(Map<?, ?> map) {
        Object metadata = value(map, "metadata");
        Map<String, Object> metadataMap = metadata instanceof Map<?, ?> raw ? stringMap(raw) : Map.of();
        return new ModelPoolEntry(
                stringValue(value(map, "model_name", "modelName")),
                stringValue(value(map, "api_key", "apiKey")),
                stringValue(value(map, "api_base_url", "apiBaseUrl")),
                stringValue(value(map, "api_provider", "apiProvider")),
                stringValue(value(map, "description")),
                stringValue(value(map, "model_id", "modelId")),
                metadataMap
        );
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            result.put(String.valueOf(entry.getKey()), ModelPoolSupport.deepCopyValue(entry.getValue()));
        }
        return result;
    }

    private static Object value(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return builder.toString();
    }
}
