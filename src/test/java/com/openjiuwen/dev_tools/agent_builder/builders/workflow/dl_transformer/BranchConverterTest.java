/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code BranchConverter} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/converters/branch_converter.py}.
 */
class BranchConverterTest {

    @Test
    void convertBuildsBranchConditionsAndEdges() {
        BranchConverter converter = new BranchConverter(nodeData(), Map.of(), new Position(0, 0));

        converter.convert();

        assertThat(converter.getNode().getId()).isEqualTo("node_branch");
        assertThat(converter.getNode().getType()).isEqualTo(NodeType.Branch.getDslType());
        assertThat(converter.getNode().getData().getBranches()).hasSize(3);
        assertThat(converter.getEdges()).hasSize(3);
        assertThat(converter.getEdges().get(0).getSourceNodeId()).isEqualTo("node_branch");
        assertThat(converter.getEdges().get(0).getTargetNodeId()).isEqualTo("node_yes");
        assertThat(converter.getEdges().get(0).getSourcePortId()).isEqualTo("branch_yes");
        assertThat(converter.getEdges().get(2).getTargetNodeId()).isEqualTo("node_default");
        assertThat(converter.getEdges().get(2).getSourcePortId()).isEqualTo("default");
    }

    @Test
    void convertBranchesHandlesSingleMultiAndDefaultConditions() {
        List<Map<String, Object>> branches = BranchConverter.convertBranches(conditions());

        Map<String, Object> first = branches.get(0);
        assertThat(first).containsEntry("branchId", "branch_yes");
        assertThat(castList(first.get("conditions"))).hasSize(1);
        assertThat(castMap(castList(first.get("conditions")).getFirst())).containsEntry("operator", "contains");

        Map<String, Object> second = branches.get(1);
        assertThat(second).containsEntry("branchId", "branch_full");
        assertThat(second).containsEntry("logic", 2);
        assertThat(castList(second.get("conditions"))).hasSize(2);

        Map<String, Object> third = branches.get(2);
        assertThat(third).containsEntry("branchId", "default");
        assertThat(castList(third.get("conditions"))).isEmpty();
    }

    @Test
    void convertExpressionBuildsReferenceAndConstantSides() {
        Map<String, Object> result = BranchConverter.convertExpression("${node_start.query} contain yes");

        assertThat(result).containsEntry("operator", "contains");
        assertThat(castMap(result.get("left"))).containsEntry("type", SourceType.ref.getValue());
        assertThat(castMap(result.get("left"))).containsEntry("content", List.of("node_start", "query"));
        assertThat(castMap(result.get("right"))).containsEntry("type", SourceType.constant.getValue());
        assertThat(castMap(result.get("right"))).containsEntry("content", "yes");
        assertThat(castMap(castMap(result.get("right")).get("schema")))
                .containsEntry("type", "string")
                .containsEntry("extra", Map.of("weak", true));
    }

    @Test
    void convertExpressionReturnsEmptyMapWhenNoOperatorMatches() {
        assertThat(BranchConverter.convertExpression("${node_start.query}")).isEmpty();
    }

    @Test
    void convertExpressionKeepsPythonSubstringOperatorOrder() {
        Map<String, Object> result = BranchConverter.convertExpression("${node_start.query} not_eq yes");

        assertThat(result).containsEntry("operator", "eq");
        assertThat(castMap(result.get("left"))).containsEntry("content", List.of("node_start", "query"));
        assertThat(castMap(result.get("right"))).containsEntry("content", "yes");
    }

    private static Map<String, Object> nodeData() {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "node_branch");
        node.put("type", "Branch");
        node.put("description", "branch node");
        node.put("parameters", Map.of("conditions", conditions()));
        return node;
    }

    private static List<Map<String, Object>> conditions() {
        return List.of(
                Map.of(
                        "branch", "branch_yes",
                        "description", "yes branch",
                        "expression", "${node_start.query} contain yes",
                        "next", "node_yes"
                ),
                Map.of(
                        "branch", "branch_full",
                        "description", "full branch",
                        "expressions", List.of(
                                "${node_start.query} is_not_empty",
                                "answer longer_than 10"
                        ),
                        "operator", "and",
                        "next", "node_full"
                ),
                Map.of(
                        "branch", "default",
                        "description", "default branch",
                        "expression", "default",
                        "next", "node_default"
                )
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return (List<Object>) value;
    }
}
