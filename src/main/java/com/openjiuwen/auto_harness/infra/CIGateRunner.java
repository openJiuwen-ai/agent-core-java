/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CI gate runner for auto-harness.
 * <p>
 * Mirrors Python's {@code CIGateRunner} in {@code openjiuwen.auto_harness.infra.ci_gate_runner}.
 * <p>
 * This minimal Java port focuses on YAML loading and gate matching.
 */
public class CIGateRunner {

    private static final String DEFAULT_YAML = "openjiuwen/auto_harness/resources/ci_gate.yaml";

    private final String workspace;
    private final String pythonExecutable;
    private final String installCommand;
    private boolean prepared;
    private final List<Map<String, Object>> gates;

    public CIGateRunner(String workspace, String configPath, String pythonExecutable, String installCommand) {
        this.workspace = workspace;
        this.pythonExecutable = pythonExecutable;
        this.installCommand = installCommand == null ? "" : installCommand.trim();
        this.gates = loadGates((configPath == null || configPath.isBlank()) ? DEFAULT_YAML : configPath);
    }

    public CIGateRunner(String workspace) {
        this(workspace, "", "", "");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadGates(String path) {
        try {
            if (!Files.exists(Path.of(path))) {
                return Collections.emptyList();
            }
            Object parsed = new Yaml().load(Files.readString(Path.of(path)));
            if (!(parsed instanceof Map<?, ?> root)) {
                return Collections.emptyList();
            }
            Object raw = root.get("ci_gates");
            if (!(raw instanceof List<?> list)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        normalized.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    out.add(normalized);
                }
            }
            return out;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> matchGates(String action) {
        if ("all".equals(action)) {
            return new ArrayList<>(gates);
        }
        String target = "check".equals(action) ? "lint" : action;
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> gate : gates) {
            Object name = gate.get("name");
            if (target.equals(name)) {
                matched.add(gate);
            }
        }
        return matched;
    }

    public List<Map<String, Object>> getGates() {
        return gates;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void setPrepared(boolean prepared) {
        this.prepared = prepared;
    }
}
