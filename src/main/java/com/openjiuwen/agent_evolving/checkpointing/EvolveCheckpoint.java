/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.agent_evolving.checkpointing;

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

    public EvolveCheckpoint() {
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Map<String, Integer> getStep() { return step; }
    public void setStep(Map<String, Integer> step) { this.step = step; }
    public Map<String, Object> getBest() { return best; }
    public void setBest(Map<String, Object> best) { this.best = best; }
    public Integer getSeed() { return seed; }
    public void setSeed(Integer seed) { this.seed = seed; }
    public Map<String, Map<String, Object>> getOperatorsState() { return operatorsState; }
    public void setOperatorsState(Map<String, Map<String, Object>> operatorsState) { this.operatorsState = operatorsState; }
    public Map<String, Object> getUpdaterState() { return updaterState; }
    public void setUpdaterState(Map<String, Object> updaterState) { this.updaterState = updaterState; }
    public Map<String, Object> getSearcherState() { return searcherState; }
    public void setSearcherState(Map<String, Object> searcherState) { this.searcherState = searcherState; }
    public Map<String, Object> getLastMetrics() { return lastMetrics; }
    public void setLastMetrics(Map<String, Object> lastMetrics) { this.lastMetrics = lastMetrics; }

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

        public Builder version(String version) { this.version = version; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder step(Map<String, Integer> step) { this.step = step; return this; }
        public Builder best(Map<String, Object> best) { this.best = best; return this; }
        public Builder seed(Integer seed) { this.seed = seed; return this; }
        public Builder operatorsState(Map<String, Map<String, Object>> operatorsState) { this.operatorsState = operatorsState; return this; }
        public Builder updaterState(Map<String, Object> updaterState) { this.updaterState = updaterState; return this; }
        public Builder searcherState(Map<String, Object> searcherState) { this.searcherState = searcherState; return this; }
        public Builder lastMetrics(Map<String, Object> lastMetrics) { this.lastMetrics = lastMetrics; return this; }

        public EvolveCheckpoint build() {
            return new EvolveCheckpoint(version, runId, step, best, seed, operatorsState, updaterState, searcherState, lastMetrics);
        }
    }
}
