/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Final result returned by a guardrail.
 */
@Value
@Builder
public class GuardrailResult {
    boolean isSafe;
    RiskLevel riskLevel;
    String riskType;
    Map<String, Object> details;
    Map<String, Object> modifiedData;

    public GuardrailResult(boolean isSafe,
                           RiskLevel riskLevel,
                           String riskType,
                           Map<String, Object> details,
                           Map<String, Object> modifiedData) {
        this.isSafe = isSafe;
        this.riskLevel = riskLevel;
        this.riskType = riskType;
        this.details = details;
        this.modifiedData = modifiedData;
    }

    public boolean isSafe() {
        return isSafe;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getRiskType() {
        return riskType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Map<String, Object> getModifiedData() {
        return modifiedData;
    }

    public static GuardrailResultBuilder builder() {
        return new GuardrailResultBuilder();
    }

    public static final class GuardrailResultBuilder {
        private boolean isSafe;
        private RiskLevel riskLevel;
        private String riskType;
        private Map<String, Object> details;
        private Map<String, Object> modifiedData;

        public GuardrailResultBuilder isSafe(boolean isSafe) {
            this.isSafe = isSafe;
            return this;
        }

        public GuardrailResultBuilder riskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
            return this;
        }

        public GuardrailResultBuilder riskType(String riskType) {
            this.riskType = riskType;
            return this;
        }

        public GuardrailResultBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public GuardrailResultBuilder modifiedData(Map<String, Object> modifiedData) {
            this.modifiedData = modifiedData;
            return this;
        }

        public GuardrailResult build() {
            return new GuardrailResult(isSafe, riskLevel, riskType, details, modifiedData);
        }
    }

    public static GuardrailResult pass(Map<String, Object> details) {
        return GuardrailResult.builder()
                .isSafe(true)
                .riskLevel(RiskLevel.SAFE)
                .details(details)
                .build();
    }

    public static GuardrailResult pass() {
        return pass(null);
    }

    public static GuardrailResult block(
            RiskLevel riskLevel,
            String riskType,
            Map<String, Object> details,
            Map<String, Object> modifiedData
    ) {
        return GuardrailResult.builder()
                .isSafe(false)
                .riskLevel(riskLevel)
                .riskType(riskType)
                .details(details)
                .modifiedData(modifiedData)
                .build();
    }
}
