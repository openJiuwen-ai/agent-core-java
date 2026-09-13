/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

/**
 * Legacy Java sandbox client facade backed by gateway-routed sandbox operations.
 * 
 * @since 0.1.7
 */
public class SandboxClient {
    private final SandboxGatewayConfig config;
    private final SandboxFsOperation fsOperation;
    private final SandboxShellOperation shellOperation;
    private final SandboxCodeOperation codeOperation;

    /**
     * SandboxClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    public SandboxClient(SandboxGatewayConfig config) {
        this.config = config != null ? config : SandboxGatewayConfig.builder().build();
        this.fsOperation = new SandboxFsOperation(this.config);
        this.shellOperation = new SandboxShellOperation(this.config);
        this.codeOperation = new SandboxCodeOperation(this.config);
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
