/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BranchConverter for DL transformer.
 * <p>
 * Mirrors Python's {@code BranchConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}.
 */
public class BranchConverter extends BaseConverter {

    public BranchConverter(Map<String, Object> nodeData, Map<String, Object> context) {
        super(nodeData, context);
    }

    public BranchConverter(Map<String, Object> nodeData, Map<String, Object> context, Position position) {
        super(nodeData, context, position);
    }

    @Override
    protected void convertSpecificConfig() {
        Object parameters = nodeData.get("parameters");
        if (!(parameters instanceof Map<?, ?> parameterMap)) {
            return;
        }
        Object conditions = parameterMap.get("conditions");
        if (!(conditions instanceof List<?> rawConditions)) {
            return;
        }

        List<Map<String, Object>> branches = new ArrayList<>();
        for (Object rawCondition : rawConditions) {
            if (!(rawCondition instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> branch = new LinkedHashMap<>();
            Object branchId = rawMap.get("branch");
            if (branchId != null) {
                branch.put("branchId", branchId.toString());
            }
            branches.add(branch);
        }
        node.getData().setBranches(branches);
    }

    @Override
    public void convertEdges() {
        Object parameters = nodeData.get("parameters");
        if (!(parameters instanceof Map<?, ?> parameterMap)) {
            return;
        }
        Object conditions = parameterMap.get("conditions");
        if (!(conditions instanceof List<?> rawConditions)) {
            return;
        }

        for (Object rawCondition : rawConditions) {
            if (!(rawCondition instanceof Map<?, ?> condition)) {
                continue;
            }
            Object next = condition.get("next");
            if (next == null || next.toString().isEmpty()) {
                continue;
            }
            Object branch = condition.get("branch");
            edges.add(new BaseConverter.Edge(
                    String.valueOf(nodeData.get("id")),
                    next.toString(),
                    branch != null ? branch.toString() : null,
                    null
            ));
        }
    }
}
