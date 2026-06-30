/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

/**
 * Legacy Java sandbox client facade backed by gateway-routed sandbox operations.
 */
public class SandboxClient {
    private final SandboxGatewayConfig config;
    private final SandboxFsOperation fsOperation;
    private final SandboxShellOperation shellOperation;
    private final SandboxCodeOperation codeOperation;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxClient(SandboxGatewayConfig config) {
        this.config = config != null ? config : SandboxGatewayConfig.builder().build();
        this.fsOperation = new SandboxFsOperation(this.config);
        this.shellOperation = new SandboxShellOperation(this.config);
        this.codeOperation = new SandboxCodeOperation(this.config);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxGatewayConfig getConfig() {
        return config;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxFsOperation fs() {
        return fsOperation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxShellOperation shell() {
        return shellOperation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxCodeOperation code() {
        return codeOperation;
    }
}
