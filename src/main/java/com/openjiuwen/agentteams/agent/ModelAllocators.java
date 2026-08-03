/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamModelConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ModelAllocators.
 * 
 * @since 0.1.7
 */
public final class ModelAllocators {
    /**
     * ModelAllocators.
     * 
     * @since 0.1.7
     */
    private ModelAllocators() {
    }

    /**
     * buildModelAllocator.
     * 
     * @param spec spec
     * @return the result
     * @since 0.1.7
     */
    public static ModelAllocator buildModelAllocator(TeamAgentSpec spec) {
        if (spec == null || spec.getModelPool() == null || spec.getModelPool().isEmpty()) {
            return nullValue();
        }
        String strategy = spec.getModelPoolStrategy();
        if (strategy == null || strategy.isBlank() || "round_robin".equals(strategy)) {
            return new RoundRobinModelAllocator(spec.getModelPool());
        }
        if ("by_model_name".equals(strategy)) {
            return new ByModelNameAllocator(spec.getModelPool());
        }
        throw new IllegalArgumentException(
                "Unknown model_pool_strategy '" + strategy + "'; expected one of: round_robin, by_model_name");
    }

    /**
     * resolveMemberModel.
     * 
     * @param spec spec
     * @param modelName modelName
     * @param modelIndex modelIndex
     * @return the result
     * @since 0.1.7
     */
    public static TeamModelConfig resolveMemberModel(TeamAgentSpec spec, String modelName, Integer modelIndex) {
        if (spec == null || spec.getModelPool() == null || spec.getModelPool().isEmpty() || modelName == null
                || modelName.isBlank()) {
            return nullValue();
        }
        List<ModelPoolEntry> group =
            spec.getModelPool().stream().filter(entry -> modelName.equals(entry.getModelName())).toList();
        if (group.isEmpty()) {
            return nullValue();
        }
        int index = modelIndex != null && modelIndex >= 0 && modelIndex < group.size() ? modelIndex : 0;
        return group.get(index).toTeamModelConfig();
    }

    static String poolDigest(List<ModelPoolEntry> pool) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<ModelPoolEntry> entries = pool == null ? List.of() : pool;
            for (ModelPoolEntry entry : entries) {
                digest.update(nullToEmpty(entry.getModelName()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(nullToEmpty(entry.getApiBaseUrl()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    static int groupIndexOf(ModelPoolEntry entry, List<ModelPoolEntry> group) {
        for (int i = 0; i < group.size(); i++) {
            if (group.get(i) == entry) {
                return i;
            }
        }
        return 0;
    }

    /**
     * nullToEmpty.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * RoundRobinModelAllocator.
     * 
     * @since 0.1.7
     */
    public static final class RoundRobinModelAllocator implements ModelAllocator {
        private final List<ModelPoolEntry> pool;
        private final String poolDigest;

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private final Map<String, List<ModelPoolEntry>> groups = new LinkedHashMap<>();
        private int index;

        /**
         * RoundRobinModelAllocator.
         * 
         * @param pool pool
         * @since 0.1.7
         */
        public RoundRobinModelAllocator(List<ModelPoolEntry> pool) {
            this.pool = pool == null ? List.of() : new ArrayList<>(pool);
            this.poolDigest = ModelAllocators.poolDigest(this.pool);
            for (ModelPoolEntry entry : this.pool) {
                groups.computeIfAbsent(entry.getModelName(), key -> new ArrayList<>()).add(entry);
            }
        }

        /**
         * allocate.
         * 
         * @param modelName modelName
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Allocation allocate(String modelName) {
            if (pool.isEmpty()) {
                return nullValue();
            }
            ModelPoolEntry entry = pool.get(index % pool.size());
            index += 1;
            List<ModelPoolEntry> group = groups.getOrDefault(entry.getModelName(), List.of(entry));
            return new Allocation(entry, groupIndexOf(entry, group));
        }

        /**
         * stateDict.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> stateDict() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("index", index);
            state.put("pool_digest", poolDigest);
            return state;
        }

        /**
         * loadStateDict.
         * 
         * @param state state
         * @since 0.1.7
         */
        @Override
        public void loadStateDict(Map<String, Object> state) {
            if (state == null || !poolDigest.equals(String.valueOf(state.get("pool_digest")))) {
                index = 0;
                return;
            }
            Integer restored = asInteger(state.get("index"));
            index = restored != null ? restored : 0;
        }
    }

    /**
     * ByModelNameAllocator.
     * 
     * @since 0.1.7
     */
    public static final class ByModelNameAllocator implements ModelAllocator {
        private final Map<String, List<ModelPoolEntry>> groups = new LinkedHashMap<>();
        private final String poolDigest;

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private Map<String, Integer> innerIndexes = new LinkedHashMap<>();

        /**
         * ByModelNameAllocator.
         * 
         * @param pool pool
         * @since 0.1.7
         */
        public ByModelNameAllocator(List<ModelPoolEntry> pool) {
            List<ModelPoolEntry> entries = pool == null ? List.of() : pool;
            for (ModelPoolEntry entry : entries) {
                groups.computeIfAbsent(entry.getModelName(), key -> new ArrayList<>()).add(entry);
            }
            poolDigest = ModelAllocators.poolDigest(entries);
            resetIndexes();
        }

        /**
         * allocate.
         * 
         * @param modelName modelName
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Allocation allocate(String modelName) {
            if (modelName == null || modelName.isBlank() || !groups.containsKey(modelName)) {
                return nullValue();
            }
            List<ModelPoolEntry> group = groups.get(modelName);
            int idx = innerIndexes.getOrDefault(modelName, 0) % group.size();
            innerIndexes.put(modelName, innerIndexes.getOrDefault(modelName, 0) + 1);
            return new Allocation(group.get(idx), idx);
        }

        /**
         * stateDict.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Map<String, Object> stateDict() {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("inner_indexes", new LinkedHashMap<>(innerIndexes));
            state.put("pool_digest", poolDigest);
            return state;
        }

        /**
         * loadStateDict.
         * 
         * @param state state
         * @since 0.1.7
         */
        @Override
        public void loadStateDict(Map<String, Object> state) {
            if (state == null || !poolDigest.equals(String.valueOf(state.get("pool_digest")))) {
                resetIndexes();
                return;
            }
            Object rawInnerIndexes = state.get("inner_indexes");
            if (!(rawInnerIndexes instanceof Map<?, ?> rawMap)) {
                return;
            }
            for (String name : groups.keySet()) {
                Integer restored = asInteger(rawMap.get(name));
                if (restored != null) {
                    innerIndexes.put(name, restored);
                } else {
                    innerIndexes.put(name, 0);
                }
            }
        }

        /**
         * resetIndexes.
         * 
         * @since 0.1.7
         */
        private void resetIndexes() {
            innerIndexes = new LinkedHashMap<>();
            for (String name : groups.keySet()) {
                innerIndexes.put(name, 0);
            }
        }
    }

    /**
     * asInteger.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return nullValue();
            }
        }
        return nullValue();
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
