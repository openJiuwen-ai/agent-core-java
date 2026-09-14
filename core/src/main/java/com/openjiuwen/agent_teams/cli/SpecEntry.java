/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry record for a single {@link TeamAgentSpec}.
 *
 * <p>Mirrors Python's {@code SpecEntry} in
 * {@code openjiuwen/agent_teams/cli/spec_loader.py}.</p>
 *
 * @param spec             validated team agent spec
 * @param source           absolute YAML path or {@code in-memory}
 * @param runtimeOverrides optional runtime block extracted from YAML
 */
public record SpecEntry(TeamAgentSpec spec, String source, Map<String, Object> runtimeOverrides) {

    public SpecEntry {
        spec = Objects.requireNonNull(spec, "spec");
        source = source == null ? "" : source;
        runtimeOverrides = runtimeOverrides == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(runtimeOverrides));
    }

    public static SpecEntry inMemory(TeamAgentSpec spec) {
        return new SpecEntry(spec, "in-memory", Map.of());
    }
}
