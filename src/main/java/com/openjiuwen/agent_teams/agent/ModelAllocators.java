/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.agent_teams.schema.TeamSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Factory and resolver helpers for team model allocation.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen.agent_teams.agent.model_allocator}.</p>
 */
public final class ModelAllocators {

    private ModelAllocators() {
    }

    public static ModelAllocator buildModelAllocator(TeamAgentSpec spec, TeamSpec teamSpec) {
        if (teamSpec == null || teamSpec.getModelPool().isEmpty()) {
            return null;
        }
        String strategy = teamSpec.getModelPoolStrategy();
        if ("round_robin".equals(strategy)) {
            return new RoundRobinModelAllocator(teamSpec.getModelPool());
        }
        if ("by_model_name".equals(strategy)) {
            return new ByModelNameAllocator(teamSpec.getModelPool());
        }
        throw new IllegalArgumentException(
                "Unknown model_pool_strategy '" + strategy + "'; expected one of: round_robin, by_model_name"
        );
    }

    public static TeamModelConfig resolveMemberModel(TeamSpec teamSpec, String modelName, Integer modelIndex) {
        if (teamSpec == null || teamSpec.getModelPool().isEmpty() || modelName == null || modelName.isBlank()) {
            return null;
        }
        List<ModelPoolEntry> group = teamSpec.getModelPool().stream()
                .filter(entry -> modelName.equals(entry.getModelName()))
                .toList();
        if (group.isEmpty()) {
            return null;
        }
        int idx = modelIndex != null && modelIndex >= 0 && modelIndex < group.size() ? modelIndex : 0;
        return group.get(idx).toTeamModelConfig();
    }

    static String poolDigest(List<ModelPoolEntry> pool) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (ModelPoolEntry entry : pool != null ? pool : List.<ModelPoolEntry>of()) {
                updateDigest(digest, entry.getModelName());
                digest.update((byte) 0);
                updateDigest(digest, entry.getApiBaseUrl());
                digest.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 digest is not available", e);
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

    static int toInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static void updateDigest(MessageDigest digest, String text) {
        digest.update((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
    }
}
