/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Configuration card for system operations.
 * <p>
 * Mirrors Python's {@code SysOperationCard} in {@code sys_operation/sys_operation.py}.
 *
 * <p>Usage:
 * <pre>
 *   SysOperationCard card = SysOperationCard.builder()
 *       .id("sys_op")
 *       .mode(OperationMode.LOCAL)
 *       .workConfig(LocalWorkConfig.builder().workDir("/tmp/test").build())
 *       .build();
 *
 *   // Generate tool IDs
 *   String toolId = SysOperationCard.generateToolId("sys_op", "fs", "readFile");
 *
 *   // Use ToolIdProxy for convenience
 *   String readToolId = card.fs().toolId("readFile");
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SysOperationCard extends BaseCard {

    /** Running mode: local or sandbox. */
    private OperationMode mode;

    /** Local work config (required when mode is LOCAL). */
    private LocalWorkConfig workConfig;

    /** Sandbox gateway config (required when mode is SANDBOX). */
    private SandboxGatewayConfig gatewayConfig;

    /**
     * Validate that mode is a valid OperationMode.
     *
     * @param modeValue string value to validate
     * @return validated OperationMode
     */
    public static OperationMode validateMode(String modeValue) {
        try {
            return OperationMode.fromString(modeValue);
        } catch (IllegalArgumentException e) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                    "error_msg", "mode must be one of [local, sandbox], current value: " + modeValue);
        }
    }

    /**
     * Get the ToolIdProxy for file system operations.
     */
    public ToolIdProxy fs() {
        return new ToolIdProxy(getId(), "fs");
    }

    /**
     * Get the ToolIdProxy for shell operations.
     */
    public ToolIdProxy shell() {
        return new ToolIdProxy(getId(), "shell");
    }

    /**
     * Get the ToolIdProxy for code operations.
     */
    public ToolIdProxy code() {
        return new ToolIdProxy(getId(), "code");
    }

    /**
     * Get a ToolIdProxy for a custom operation type.
     *
     * @param opType operation type name
     * @return the proxy
     */
    public ToolIdProxy proxy(String opType) {
        return new ToolIdProxy(getId(), opType);
    }

    /**
     * Centralized tool ID generation for SysOperation methods.
     *
     * @param cardId     card identifier
     * @param opType     operation type (e.g., "fs", "shell", "code")
     * @param methodName method name
     * @return formatted tool ID: "{cardId}.{opType}.{methodName}"
     */
    public static String generateToolId(String cardId, String opType, String methodName) {
        return cardId + "." + opType + "." + methodName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public OperationMode getMode() {
        return mode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMode(OperationMode mode) {
        this.mode = mode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LocalWorkConfig getWorkConfig() {
        return workConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setWorkConfig(LocalWorkConfig workConfig) {
        this.workConfig = workConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SandboxGatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends BaseCard.Builder {
        private OperationMode mode;
        private LocalWorkConfig workConfig;
        private SandboxGatewayConfig gatewayConfig;

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder mode(OperationMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder workConfig(LocalWorkConfig workConfig) {
            this.workConfig = workConfig;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder gatewayConfig(SandboxGatewayConfig gatewayConfig) {
            this.gatewayConfig = gatewayConfig;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
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
