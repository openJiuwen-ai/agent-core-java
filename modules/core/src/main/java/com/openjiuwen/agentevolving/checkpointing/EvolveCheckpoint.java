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
 * 
 * @since 0.1.7
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
     * EvolveCheckpoint.
     * 
     * @since 0.1.7
     */
    public EvolveCheckpoint() {
    }

    /**
     * EvolveCheckpoint.
     * 
     * @param version version
     * @param runId runId
     * @param step step
     * @param best best
     * @param seed seed
     * @param operatorsState operatorsState
     * @param updaterState updaterState
     * @param searcherState searcherState
     * @param lastMetrics lastMetrics
     * @since 0.1.7
     */
    public EvolveCheckpoint(String version, String runId, Map<String, Integer> step, Map<String, Object> best,
            Integer seed, Map<String, Map<String, Object>> operatorsState, Map<String, Object> updaterState,
            Map<String, Object> searcherState, Map<String, Object> lastMetrics) {
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
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * getVersion.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getVersion() {
        return version;
    }

    /**
     * setVersion.
     * 
     * @param version version
     * @since 0.1.7
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * getRunId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRunId() {
        return runId;
    }

    /**
     * setRunId.
     * 
     * @param runId runId
     * @since 0.1.7
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * getStep.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Integer> getStep() {
        return step;
    }

    /**
     * setStep.
     * 
     * @param step step
     * @since 0.1.7
     */
    public void setStep(Map<String, Integer> step) {
        this.step = step;
    }

    /**
     * getBest.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getBest() {
        return best;
    }

    /**
     * setBest.
     * 
     * @param best best
     * @since 0.1.7
     */
    public void setBest(Map<String, Object> best) {
        this.best = best;
    }

    /**
     * getSeed.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getSeed() {
        return seed;
    }

    /**
     * setSeed.
     * 
     * @param seed seed
     * @since 0.1.7
     */
    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    /**
     * getOperatorsState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Map<String, Object>> getOperatorsState() {
        return operatorsState;
    }

    /**
     * setOperatorsState.
     * 
     * @param operatorsState operatorsState
     * @since 0.1.7
     */
    public void setOperatorsState(Map<String, Map<String, Object>> operatorsState) {
        this.operatorsState = operatorsState;
    }

    /**
     * getUpdaterState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getUpdaterState() {
        return updaterState;
    }

    /**
     * setUpdaterState.
     * 
     * @param updaterState updaterState
     * @since 0.1.7
     */
    public void setUpdaterState(Map<String, Object> updaterState) {
        this.updaterState = updaterState;
    }

    /**
     * getSearcherState.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getSearcherState() {
        return searcherState;
    }

    /**
     * setSearcherState.
     * 
     * @param searcherState searcherState
     * @since 0.1.7
     */
    public void setSearcherState(Map<String, Object> searcherState) {
        this.searcherState = searcherState;
    }

    /**
     * getLastMetrics.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getLastMetrics() {
        return lastMetrics;
    }

    /**
     * setLastMetrics.
     * 
     * @param lastMetrics lastMetrics
     * @since 0.1.7
     */
    public void setLastMetrics(Map<String, Object> lastMetrics) {
        this.lastMetrics = lastMetrics;
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
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

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * version.
         * 
         * @param version version
         * @return the result
         * @since 0.1.7
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * runId.
         * 
         * @param runId runId
         * @return the result
         * @since 0.1.7
         */
        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        /**
         * step.
         * 
         * @param step step
         * @return the result
         * @since 0.1.7
         */
        public Builder step(Map<String, Integer> step) {
            this.step = step;
            return this;
        }

        /**
         * best.
         * 
         * @param best best
         * @return the result
         * @since 0.1.7
         */
        public Builder best(Map<String, Object> best) {
            this.best = best;
            return this;
        }

        /**
         * seed.
         * 
         * @param seed seed
         * @return the result
         * @since 0.1.7
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * operatorsState.
         * 
         * @param operatorsState operatorsState
         * @return the result
         * @since 0.1.7
         */
        public Builder operatorsState(Map<String, Map<String, Object>> operatorsState) {
            this.operatorsState = operatorsState;
            return this;
        }

        /**
         * updaterState.
         * 
         * @param updaterState updaterState
         * @return the result
         * @since 0.1.7
         */
        public Builder updaterState(Map<String, Object> updaterState) {
            this.updaterState = updaterState;
            return this;
        }

        /**
         * searcherState.
         * 
         * @param searcherState searcherState
         * @return the result
         * @since 0.1.7
         */
        public Builder searcherState(Map<String, Object> searcherState) {
            this.searcherState = searcherState;
            return this;
        }

        /**
         * lastMetrics.
         * 
         * @param lastMetrics lastMetrics
         * @return the result
         * @since 0.1.7
         */
        public Builder lastMetrics(Map<String, Object> lastMetrics) {
            this.lastMetrics = lastMetrics;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public EvolveCheckpoint build() {
            return new EvolveCheckpoint(version, runId, step, best, seed, operatorsState, updaterState, searcherState,
                    lastMetrics);
        }
    }
}
