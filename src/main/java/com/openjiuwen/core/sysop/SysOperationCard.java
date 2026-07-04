/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Backward-compatible card for the moved system operation package.
 *
 * <p>Mirrors Python's {@code SysOperationCard} in
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.SysOperationCard}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class SysOperationCard extends com.openjiuwen.core.sys_operation.SysOperationCard {

    public SysOperationCard() {
        super();
    }

    public SysOperationCard(String id,
                            OperationMode mode,
                            com.openjiuwen.core.sys_operation.config.LocalWorkConfig workConfig) {
        super(id, mode != null ? mode.toNewMode() : com.openjiuwen.core.sys_operation.OperationMode.LOCAL,
                workConfig);
    }

    public OperationMode getLegacyMode() {
        return OperationMode.fromNewMode(super.getMode());
    }

    public void setMode(OperationMode mode) {
        super.setMode(mode != null ? mode.toNewMode() : com.openjiuwen.core.sys_operation.OperationMode.LOCAL);
    }

    public ToolIdProxy fs() {
        return new ToolIdProxy(getId(), "fs");
    }

    public ToolIdProxy shell() {
        return new ToolIdProxy(getId(), "shell");
    }

    public ToolIdProxy code() {
        return new ToolIdProxy(getId(), "code");
    }

    public ToolIdProxy proxy(String opType) {
        return new ToolIdProxy(getId(), opType);
    }

    public static OperationMode validateMode(String modeValue) {
        return OperationMode.fromString(modeValue);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Backward-compatible builder accepting the old OperationMode enum while
     * delegating storage to the new card implementation.
     */
    public static class Builder {
        private String id;
        private String name = "";
        private String description = "";
        private OperationMode mode = OperationMode.LOCAL;
        private com.openjiuwen.core.sys_operation.config.LocalWorkConfig workConfig;
        private com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig gatewayConfig;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder mode(OperationMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder mode(com.openjiuwen.core.sys_operation.OperationMode mode) {
            this.mode = OperationMode.fromNewMode(mode);
            return this;
        }

        public Builder workConfig(com.openjiuwen.core.sys_operation.config.LocalWorkConfig workConfig) {
            this.workConfig = workConfig;
            return this;
        }

        public Builder gatewayConfig(com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig gatewayConfig) {
            this.gatewayConfig = gatewayConfig;
            return this;
        }

        public SysOperationCard build() {
            SysOperationCard card = new SysOperationCard();
            card.setId(id);
            card.setName(name);
            card.setDescription(description);
            card.setMode(mode);
            card.setWorkConfig(workConfig);
            card.setGatewayConfig(gatewayConfig);
            return card;
        }
    }
}
