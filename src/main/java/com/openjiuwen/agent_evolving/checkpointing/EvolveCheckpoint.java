/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.checkpointing.types.EvolveCheckpoint.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvolveCheckpoint {

    private String version;
    @JsonAlias("runId")
    private String runId;
    private Map<String, Integer> step;
    private Map<String, Object> best;
    private Integer seed;
    @JsonAlias("operatorsState")
    private Map<String, Map<String, Object>> operatorsState;
    @JsonAlias("updaterState")
    private Map<String, Object> updaterState;
    @JsonAlias("searcherState")
    private Map<String, Object> searcherState;
    @JsonAlias("lastMetrics")
    private Map<String, Object> lastMetrics;

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvolveCheckpoint() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EvolveCheckpoint(String version,
                            String runId,
                            Map<String, Integer> step,
                            Map<String, Object> best,
                            Integer seed,
                            Map<String, Map<String, Object>> operatorsState,
                            Map<String, Object> updaterState,
                            Map<String, Object> searcherState,
                            Map<String, Object> lastMetrics) {
        this.version = version;
        this.runId = runId;
        this.step = step;
        this.best = best;
        this.seed = seed;
        this.operatorsState = operatorsState;
        this.updaterState = updaterState;
        this.searcherState = searcherState;
        this.lastMetrics = lastMetrics;
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
    public String getVersion() {
        return version;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRunId() {
        return runId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Integer> getStep() {
        return step;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setStep(Map<String, Integer> step) {
        this.step = step;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getBest() {
        return best;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setBest(Map<String, Object> best) {
        this.best = best;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getSeed() {
        return seed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Map<String, Object>> getOperatorsState() {
        return operatorsState;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOperatorsState(Map<String, Map<String, Object>> operatorsState) {
        this.operatorsState = operatorsState;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getUpdaterState() {
        return updaterState;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setUpdaterState(Map<String, Object> updaterState) {
        this.updaterState = updaterState;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getSearcherState() {
        return searcherState;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSearcherState(Map<String, Object> searcherState) {
        this.searcherState = searcherState;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getLastMetrics() {
        return lastMetrics;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLastMetrics(Map<String, Object> lastMetrics) {
        this.lastMetrics = lastMetrics;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private String version;
        private String runId;
        private Map<String, Integer> step;
        private Map<String, Object> best;
        private Integer seed;
        private Map<String, Map<String, Object>> operatorsState;
        private Map<String, Object> updaterState;
        private Map<String, Object> searcherState;
        private Map<String, Object> lastMetrics;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder step(Map<String, Integer> step) {
            this.step = step;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder best(Map<String, Object> best) {
            this.best = best;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder operatorsState(Map<String, Map<String, Object>> operatorsState) {
            this.operatorsState = operatorsState;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder updaterState(Map<String, Object> updaterState) {
            this.updaterState = updaterState;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder searcherState(Map<String, Object> searcherState) {
            this.searcherState = searcherState;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder lastMetrics(Map<String, Object> lastMetrics) {
            this.lastMetrics = lastMetrics;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public EvolveCheckpoint build() {
            return new EvolveCheckpoint(version, runId, step, best, seed, operatorsState, updaterState, searcherState, lastMetrics);
        }
    }
}
