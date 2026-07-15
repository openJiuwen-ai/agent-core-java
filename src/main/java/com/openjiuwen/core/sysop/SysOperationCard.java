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
 * <p>
 * Usage:
 * 
 * <pre>
 * SysOperationCard card = SysOperationCard.builder().id("sys_op").mode(OperationMode.LOCAL)
 *         .workConfig(LocalWorkConfig.builder().workDir("/tmp/test").build()).build();
 * // Generate tool IDs
 * String toolId = SysOperationCard.generateToolId("sys_op", "fs", "readFile");
 * // Use ToolIdProxy for convenience
 * String readToolId = card.fs().toolId("readFile");
 * </pre>
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SysOperationCard extends BaseCard {
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
     * @since 0.1.7
     */
    public static OperationMode validateMode(String modeValue) {
        try {
            return OperationMode.fromString(modeValue);
        } catch (IllegalArgumentException e) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR, "error_msg",
                    "mode must be one of [local, sandbox], current value: " + modeValue);
        }
    }

    /**
     * Get the ToolIdProxy for file system operations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolIdProxy fs() {
        return new ToolIdProxy(getId(), "fs");
    }

    /**
     * Get the ToolIdProxy for shell operations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolIdProxy shell() {
        return new ToolIdProxy(getId(), "shell");
    }

    /**
     * Get the ToolIdProxy for code operations.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ToolIdProxy code() {
        return new ToolIdProxy(getId(), "code");
    }

    /**
     * Get a ToolIdProxy for a custom operation type.
     * 
     * @param opType operation type name
     * @return the proxy
     * @since 0.1.7
     */
    public ToolIdProxy proxy(String opType) {
        return new ToolIdProxy(getId(), opType);
    }

    /**
     * Centralized tool ID generation for SysOperation methods.
     * 
     * @param cardId card identifier
     * @param opType operation type (e.g., "fs", "shell", "code")
     * @param methodName method name
     * @return formatted tool ID: "{cardId}.{opType}.{methodName}"
     * @since 0.1.7
     */
    public static String generateToolId(String cardId, String opType, String methodName) {
        return cardId + "." + opType + "." + methodName;
    }

    /**
     * getMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public OperationMode getMode() {
        return mode;
    }

    /**
     * setMode.
     * 
     * @param mode mode
     * @since 0.1.7
     */
    public void setMode(OperationMode mode) {
        this.mode = mode;
    }

    /**
     * getWorkConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LocalWorkConfig getWorkConfig() {
        return workConfig;
    }

    /**
     * setWorkConfig.
     * 
     * @param workConfig workConfig
     * @since 0.1.7
     */
    public void setWorkConfig(LocalWorkConfig workConfig) {
        this.workConfig = workConfig;
    }

    /**
     * getGatewayConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SandboxGatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    /**
     * setGatewayConfig.
     * 
     * @param gatewayConfig gatewayConfig
     * @since 0.1.7
     */
    public void setGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends BaseCard.Builder {
        private OperationMode mode;
        private LocalWorkConfig workConfig;
        private SandboxGatewayConfig gatewayConfig;

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * mode.
         * 
         * @param mode mode
         * @return the result
         * @since 0.1.7
         */
        public Builder mode(OperationMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * workConfig.
         * 
         * @param workConfig workConfig
         * @return the result
         * @since 0.1.7
         */
        public Builder workConfig(LocalWorkConfig workConfig) {
            this.workConfig = workConfig;
            return this;
        }

        /**
         * gatewayConfig.
         * 
         * @param gatewayConfig gatewayConfig
         * @return the result
         * @since 0.1.7
         */
        public Builder gatewayConfig(SandboxGatewayConfig gatewayConfig) {
            this.gatewayConfig = gatewayConfig;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
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
