/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import java.util.List;

/**
 * Package facade for CLI agent configuration and factory exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.agent} module in
 * {@code openjiuwen/harness/cli/agent/__init__.py}.</p>
 */
public final class HarnessCliAgentPackage {
    public static final String PYTHON_MODULE = "openjiuwen/harness/cli/agent/__init__.py";
    public static final List<String> ALL = List.of(
            "CLIConfig",
            "load_config",
            "AgentBackend",
            "LocalBackend",
            "create_agent",
            "create_backend"
    );

    private HarnessCliAgentPackage() {
    }

    public static List<String> all() {
        return ALL;
    }

    public static ExportedSymbol getAttr(String name) {
        for (ExportedSymbol symbol : ExportedSymbol.values()) {
            if (symbol.pythonName().equals(name)) {
                return symbol;
            }
        }
        throw new IllegalArgumentException("module 'openjiuwen.harness.cli.agent' has no attribute '" + name + "'");
    }

    /**
     * Exported symbols declared by Python {@code __all__}.
     *
     * <p>Mirrors Python's lazy {@code __getattr__} export surface in
     * {@code openjiuwen/harness/cli/agent/__init__.py}.</p>
     */
    public enum ExportedSymbol {
        CLI_CONFIG("CLIConfig", CliAgentConfig.class),
        LOAD_CONFIG("load_config", CliAgentConfig.class),
        AGENT_BACKEND("AgentBackend", AgentBackend.class),
        LOCAL_BACKEND("LocalBackend", LocalBackend.class),
        CREATE_AGENT("create_agent", CliAgentFactory.class),
        CREATE_BACKEND("create_backend", CliAgentFactory.class);

        private final String pythonName;
        private final Class<?> ownerType;

        ExportedSymbol(String pythonName, Class<?> ownerType) {
            this.pythonName = pythonName;
            this.ownerType = ownerType;
        }

        public String pythonName() {
            return pythonName;
        }

        public Class<?> ownerType() {
            return ownerType;
        }
    }
}
