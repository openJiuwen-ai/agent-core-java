/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamModelConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public record Allocation used by the Java parity implementation.
 * @since 0.1.7
 */
public record Allocation(ModelPoolEntry entry, int groupIndex) {
    /**
     * toTeamModelConfig.
     * @return the result
     * @since 0.1.7
     */
    public TeamModelConfig toTeamModelConfig() {
        return entry.toTeamModelConfig();
    }

    /**
     * toDbRef.
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toDbRef() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model_name", entry.getModelName());
        payload.put("model_index", groupIndex);
        return payload;
    }
}
