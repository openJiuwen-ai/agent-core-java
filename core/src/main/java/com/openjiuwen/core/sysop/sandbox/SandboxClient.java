/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Legacy Java sandbox client facade backed by gateway-routed sandbox operations.
 *
 * @deprecated Prefer {@link com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient}
 *     or {@code SysOperation} sandbox operations.
 * @since 0.1.7
 */
public class SandboxClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxClient.class);

    private final SandboxGatewayConfig config;
    private final SandboxFsOperation fsOperation;
    private final SandboxShellOperation shellOperation;
    private final SandboxCodeOperation codeOperation;

    /**
     * Creates a client whose operations are routed by the isolation key derived from the config,
     * using the same rule as {@link SysOperation}.
     *
     * @param config config
     * @since 0.1.7
     */
    public SandboxClient(SandboxGatewayConfig config) {
        this.config = config != null ? config : SandboxGatewayConfig.builder().build();
        SandboxRunConfig runConfig = SandboxRunConfig.builder()
                .config(this.config)
                .isolationKeyTemplate(isolationKeyTemplate(this.config).orElse(null))
                .build();
        this.fsOperation = new SandboxFsOperation(runConfig);
        this.shellOperation = new SandboxShellOperation(runConfig);
        this.codeOperation = new SandboxCodeOperation(runConfig);
    }

    private static Optional<String> isolationKeyTemplate(SandboxGatewayConfig config) {
        SandboxIsolationConfig isolation = config.getIsolation() != null
                ? config.getIsolation()
                : SandboxIsolationConfig.builder().build();
        SandboxLauncherConfig launcher = config.getLauncherConfig();
        try {
            return Optional.of(SysOperation.generateIsolationKeyTemplate(
                    isolation.getPrefix(),
                    isolation.getContainerScope(),
                    isolation.getCustomId(),
                    launcher == null ? null : launcher.getLauncherType(),
                    launcher == null ? null : launcher.getSandboxType()));
        } catch (IllegalArgumentException ex) {
            // Keep the legacy shared-key behavior instead of failing construction for existing callers.
            LOGGER.warn("Invalid sandbox isolation config, falling back to shared isolation key: {}",
                    ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SandboxGatewayConfig getConfig() {
        return config;
    }

    /**
     * fs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SandboxFsOperation fs() {
        return fsOperation;
    }

    /**
     * shell.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SandboxShellOperation shell() {
        return shellOperation;
    }

    /**
     * code.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SandboxCodeOperation code() {
        return codeOperation;
    }
}
