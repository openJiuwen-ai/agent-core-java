package com.openjiuwen.auto_harness.schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code SessionResultsArtifact} in {@code openjiuwen.auto_harness.schema}.
 */
public class SessionResultsArtifact {

    private List<CycleResult> results = new ArrayList<>();

    public List<CycleResult> getResults() {
        return results;
    }

    public void setResults(List<CycleResult> results) {
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
    }
}
