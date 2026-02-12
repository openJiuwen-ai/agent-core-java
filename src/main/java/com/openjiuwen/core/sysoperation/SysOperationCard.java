// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation;

import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.config.LocalWorkConfig;
import com.openjiuwen.core.sysoperation.config.SandboxGatewayConfig;

import java.util.Map;

/**
 * Configuration card for SysOperation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.sys_operation.SysOperationCard
 * 
 * <p>This card holds the configuration for a SysOperation instance:
 * <ul>
 *   <li>{@code mode} - Running mode (LOCAL or SANDBOX)</li>
 *   <li>{@code workConfig} - Local work config (required when mode is LOCAL)</li>
 *   <li>{@code gatewayConfig} - Sandbox gateway config (required when mode is SANDBOX)</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public class SysOperationCard extends BaseCard {

    /**
     * Running mode (LOCAL or SANDBOX).
     */
    private OperationMode mode;

    /**
     * Local work config (required when mode is LOCAL).
     */
    private LocalWorkConfig workConfig;

    /**
     * Sandbox gateway config (required when mode is SANDBOX).
     */
    private SandboxGatewayConfig gatewayConfig;

    /**
     * Default constructor with LOCAL mode.
     */
    public SysOperationCard() {
        super();
        this.mode = OperationMode.LOCAL;
        this.workConfig = null;
        this.gatewayConfig = null;
    }

    /**
     * Constructor with all parameters.
     * 
     * @param id unique identifier
     * @param mode running mode
     * @param workConfig local work configuration
     * @param gatewayConfig sandbox gateway configuration
     */
    public SysOperationCard(String id, OperationMode mode, LocalWorkConfig workConfig,
                            SandboxGatewayConfig gatewayConfig) {
        super(id, "", "", null);
        this.mode = validateMode(mode);
        this.workConfig = workConfig;
        this.gatewayConfig = gatewayConfig;
    }

    /**
     * Validates and returns the operation mode.
     * 
     * @param mode the mode to validate
     * @return the validated mode
     * @throws com.openjiuwen.core.common.exception.BaseError if mode is invalid
     */
    private OperationMode validateMode(OperationMode mode) {
        if (mode == null) {
            return OperationMode.LOCAL;
        }
        return mode;
    }

    /**
     * Validates mode from string value.
     * 
     * @param modeValue the string mode value
     * @return the validated OperationMode
     * @throws com.openjiuwen.core.common.exception.BaseError if mode is invalid
     */
    public static OperationMode validateModeFromString(String modeValue) {
        if (modeValue == null || modeValue.isEmpty()) {
            return OperationMode.LOCAL;
        }
        try {
            return OperationMode.fromValue(modeValue);
        } catch (IllegalArgumentException e) {
            throw ErrorBuilder.build(StatusCode.SYS_OPERATION_CARD_PARAM_ERROR,
                "mode must be one of [local, sandbox], current value: " + modeValue,
                null, e, Map.of("error_msg", 
                    "mode must be one of [local, sandbox], current value: " + modeValue));
        }
    }

    /**
     * Gets the operation mode.
     * 
     * @return the operation mode
     */
    public OperationMode getMode() {
        return mode;
    }

    /**
     * Sets the operation mode.
     * 
     * @param mode the operation mode
     */
    public void setMode(OperationMode mode) {
        this.mode = validateMode(mode);
    }

    /**
     * Gets the local work configuration.
     * 
     * @return the local work config, or null if not set
     */
    public LocalWorkConfig getWorkConfig() {
        return workConfig;
    }

    /**
     * Sets the local work configuration.
     * 
     * @param workConfig the local work config
     */
    public void setWorkConfig(LocalWorkConfig workConfig) {
        this.workConfig = workConfig;
    }

    /**
     * Gets the sandbox gateway configuration.
     * 
     * @return the sandbox gateway config, or null if not set
     */
    public SandboxGatewayConfig getGatewayConfig() {
        return gatewayConfig;
    }

    /**
     * Sets the sandbox gateway configuration.
     * 
     * @param gatewayConfig the sandbox gateway config
     */
    public void setGatewayConfig(SandboxGatewayConfig gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }

    @Override
    public Object toolInfo() {
        return Map.of(
            "id", getId(),
            "mode", mode.getValue(),
            "type", "sys_operation"
        );
    }

    /**
     * Creates a new Builder instance.
     * 
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for SysOperationCard.
     */
    public static class Builder {
        private String id;
        private OperationMode mode = OperationMode.LOCAL;
        private LocalWorkConfig workConfig;
        private SandboxGatewayConfig gatewayConfig;

        /**
         * Sets the unique identifier.
         * 
         * @param id the unique identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the operation mode.
         * 
         * @param mode the operation mode
         * @return this builder
         */
        public Builder mode(OperationMode mode) {
            this.mode = mode != null ? mode : OperationMode.LOCAL;
            return this;
        }

        /**
         * Sets the operation mode from string.
         * 
         * @param modeValue the mode string value
         * @return this builder
         */
        public Builder mode(String modeValue) {
            this.mode = SysOperationCard.validateModeFromString(modeValue);
            return this;
        }

        /**
         * Sets the local work configuration.
         * 
         * @param workConfig the local work config
         * @return this builder
         */
        public Builder workConfig(LocalWorkConfig workConfig) {
            this.workConfig = workConfig;
            return this;
        }

        /**
         * Sets the sandbox gateway configuration.
         * 
         * @param gatewayConfig the sandbox gateway config
         * @return this builder
         */
        public Builder gatewayConfig(SandboxGatewayConfig gatewayConfig) {
            this.gatewayConfig = gatewayConfig;
            return this;
        }

        /**
         * Builds the SysOperationCard instance.
         * 
         * @return the built SysOperationCard
         */
        public SysOperationCard build() {
            return new SysOperationCard(id, mode, workConfig, gatewayConfig);
        }
    }

    @Override
    public String toString() {
        return "SysOperationCard{" +
            "id='" + getId() + '\'' +
            ", mode=" + mode +
            ", workConfig=" + workConfig +
            ", gatewayConfig=" + gatewayConfig +
            '}';
    }
}

