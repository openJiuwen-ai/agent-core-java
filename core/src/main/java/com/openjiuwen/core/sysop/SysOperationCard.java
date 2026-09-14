/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

/**
 * Configuration card for system operations.
 *
 * <p>Mirrors Python's {@code SysOperationCard} in
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.</p>
 */
public class SysOperationCard extends BaseCard {

    private OperationMode mode = OperationMode.LOCAL;
    private LocalWorkConfig workConfig;
    private SandboxGatewayConfig gatewayConfig;

    public SysOperationCard() {
        super();
    }

    public SysOperationCard(String id, OperationMode mode, LocalWorkConfig workConfig) {
        super(id, "", "");
        setMode(mode);
        this.workConfig = workConfig;
    }

    public OperationMode getMode() {
        return mode;
    }

    public void setMode(OperationMode mode) {
        this.mode = mode != null ? mode : OperationMode.LOCAL;
    }

    public void setMode(String mode) {
        if (mode != null && mode.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg",
                    "mode must be one of [local, sandbox], current value: " + mode
            );
        }
        OperationMode parsed = OperationMode.fromValue(mode);
        if (mode != null && !parsed.value().equalsIgnoreCase(mode.trim())) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg",
                    "mode must be one of [local, sandbox], current value: " + mode
            );
        }
        this.mode = parsed;
    }

    public LocalWorkConfig getWorkConfig() {
        return workConfig;
    }

    public void setWorkConfig(LocalWorkConfig workConfig) {
        this.workConfig = workConfig;
    }

    public SandboxGatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    public void setGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }

    public ToolIdProxy getFs() {
        return new ToolIdProxy(getId(), "fs");
    }

    public ToolIdProxy getShell() {
        return new ToolIdProxy(getId(), "shell");
    }

    public ToolIdProxy getCode() {
        return new ToolIdProxy(getId(), "code");
    }

    public ToolIdProxy operation(String name) {
        return new ToolIdProxy(getId(), name);
    }

    public static String generateToolId(String cardId, String opType, String methodName) {
        return cardId + "." + opType + "." + methodName;
    }

    public ToolIdProxy fs() {
        return getFs();
    }

    public ToolIdProxy shell() {
        return getShell();
    }

    public ToolIdProxy code() {
        return getCode();
    }

    public ToolIdProxy proxy(String opType) {
        return operation(opType);
    }

    public static OperationMode validateMode(String modeValue) {
        return OperationMode.fromString(modeValue);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name = "";
        private String description = "";
        private OperationMode mode = OperationMode.LOCAL;
        private LocalWorkConfig workConfig;
        private SandboxGatewayConfig gatewayConfig;

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

        public Builder workConfig(LocalWorkConfig workConfig) {
            this.workConfig = workConfig;
            return this;
        }

        public Builder gatewayConfig(SandboxGatewayConfig gatewayConfig) {
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
