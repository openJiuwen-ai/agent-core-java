/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete specification of a workflow structure.
 * <p>
 * Mirrors Python's {@code WorkflowSpec} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowSpec {

    private Map<String, List<String>> edges = new LinkedHashMap<>();

    @JsonProperty("stream_edges")
    private Map<String, List<String>> streamEdges = new LinkedHashMap<>();

    @JsonProperty("comp_configs")
    private Map<String, NodeSpec> compConfigs = new LinkedHashMap<>();

    @JsonProperty("stream_source_groups")
    private Map<String, List<List<String>>> streamSourceGroups = new LinkedHashMap<>();

    @JsonProperty("start_nodes")
    private List<String> startNodes = new ArrayList<>();

    public WorkflowSpec() {
    }

    public WorkflowSpec(
            Map<String, List<String>> streamEdges,
            Map<String, NodeSpec> compConfigs,
            Map<String, List<List<String>>> streamSourceGroups) {
        setStreamEdges(streamEdges);
        setCompConfigs(compConfigs);
        setStreamSourceGroups(streamSourceGroups);
    }

    public Map<String, List<String>> getEdges() {
        return edges;
    }

    public void setEdges(Map<String, List<String>> edges) {
        this.edges = copyStringListMap(edges, "edges");
    }

    public Map<String, List<String>> getStreamEdges() {
        return streamEdges;
    }

    public void setStreamEdges(Map<String, List<String>> streamEdges) {
        this.streamEdges = copyStringListMap(streamEdges, "stream_edges");
    }

    public Map<String, NodeSpec> getCompConfigs() {
        return compConfigs;
    }

    public void setCompConfigs(Map<String, NodeSpec> compConfigs) {
        Objects.requireNonNull(compConfigs, "comp_configs must not be null");
        this.compConfigs = new LinkedHashMap<>(compConfigs);
    }

    public Map<String, List<List<String>>> getStreamSourceGroups() {
        return streamSourceGroups;
    }

    public void setStreamSourceGroups(Map<String, List<List<String>>> streamSourceGroups) {
        Objects.requireNonNull(streamSourceGroups, "stream_source_groups must not be null");
        Map<String, List<List<String>>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<String>>> entry : streamSourceGroups.entrySet()) {
            List<List<String>> groups = Objects.requireNonNull(
                    entry.getValue(), "stream_source_groups entries must not be null");
            List<List<String>> copiedGroups = new ArrayList<>();
            for (List<String> group : groups) {
                copiedGroups.add(new ArrayList<>(Objects.requireNonNull(
                        group, "stream_source_groups nested lists must not be null")));
            }
            copied.put(entry.getKey(), copiedGroups);
        }
        this.streamSourceGroups = copied;
    }

    public List<String> getStartNodes() {
        return startNodes;
    }

    public void setStartNodes(List<String> startNodes) {
        Objects.requireNonNull(startNodes, "start_nodes must not be null");
        this.startNodes = new ArrayList<>(startNodes);
    }

    private static Map<String, List<String>> copyStringListMap(
            Map<String, List<String>> source,
            String fieldName) {
        Objects.requireNonNull(source, fieldName + " must not be null");
        Map<String, List<String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<>(Objects.requireNonNull(
                    entry.getValue(), fieldName + " entries must not be null")));
        }
        return copied;
    }
}
