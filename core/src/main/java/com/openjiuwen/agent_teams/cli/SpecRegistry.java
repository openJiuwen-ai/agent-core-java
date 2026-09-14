/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.cli;

import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Name-keyed registry of TeamAgentSpec entries from YAML and in-memory sources.
 *
 * <p>Mirrors Python's {@code SpecRegistry} in
 * {@code openjiuwen/agent_teams/cli/spec_loader.py}.</p>
 */
public class SpecRegistry {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final String IN_MEMORY = "in-memory";

    private final Map<String, SpecEntry> entries = new LinkedHashMap<>();

    public SpecEntry addYaml(String path) {
        return addYaml(Path.of(path));
    }

    public SpecEntry addYaml(Path path) {
        SpecLoader.LoadedSpec loaded = SpecLoader.loadSpecYaml(path);
        Path resolvedPath = resolveUserPath(path);
        SpecEntry entry = new SpecEntry(
                loaded.spec(),
                resolvedPath.toString(),
                loaded.runtimeOverrides()
        );

        String teamName = loaded.spec().getTeamName();
        SpecEntry existing = entries.get(teamName);
        if (existing != null && IN_MEMORY.equals(existing.source())) {
            TEAM_LOGGER.warning(
                    "[cli.spec_registry] in-memory spec for team={} shadows yaml source={}",
                    teamName,
                    entry.source()
            );
            return existing;
        }
        if (existing != null) {
            TEAM_LOGGER.warning(
                    "[cli.spec_registry] yaml reload for team={} replaces source={} with {}",
                    teamName,
                    existing.source(),
                    entry.source()
            );
        }
        entries.put(teamName, entry);
        return entry;
    }

    public SpecEntry addInmemory(TeamAgentSpec spec) {
        SpecEntry entry = SpecEntry.inMemory(spec);
        String teamName = spec.getTeamName();
        SpecEntry existing = entries.get(teamName);
        if (existing != null && !IN_MEMORY.equals(existing.source())) {
            TEAM_LOGGER.warning(
                    "[cli.spec_registry] in-memory spec for team={} replaces yaml source={}",
                    teamName,
                    existing.source()
            );
        }
        entries.put(teamName, entry);
        return entry;
    }

    public SpecEntry get(String teamName) {
        return entries.get(teamName);
    }

    public List<String> names() {
        return new ArrayList<>(entries.keySet());
    }

    public List<SpecEntry> entries() {
        return new ArrayList<>(entries.values());
    }

    public void bulkLoadYaml(Iterable<? extends Path> paths) {
        for (Path path : paths) {
            addYaml(path);
        }
    }

    public void bulkRegister(Map<String, TeamAgentSpec> specs) {
        for (Map.Entry<String, TeamAgentSpec> item : specs.entrySet()) {
            String declaredName = item.getKey();
            TeamAgentSpec spec = item.getValue();
            if (!declaredName.equals(spec.getTeamName())) {
                TEAM_LOGGER.warning(
                        "[cli.spec_registry] dict key={} does not match spec.team_name={}; registering under spec.team_name",
                        declaredName,
                        spec.getTeamName()
                );
            }
            addInmemory(spec);
        }
    }

    private static Path resolveUserPath(Path path) {
        String raw = path.toString();
        Path expanded = raw.equals("~") || raw.startsWith("~\\") || raw.startsWith("~/")
                ? Path.of(System.getProperty("user.home"), raw.length() == 1 ? "" : raw.substring(2))
                : path;
        return expanded.toAbsolutePath().normalize();
    }
}
