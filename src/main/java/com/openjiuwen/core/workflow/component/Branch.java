  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.condition.Condition;
import com.openjiuwen.core.workflow.condition.ExpressionCondition;
import com.openjiuwen.core.workflow.condition.FuncCondition;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A single branch with condition and target nodes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.branch_router.Branch}.
 */
public class Branch {

    private final String branchId;
    private final Condition condition;
    private final List<String> target;

    public Branch(Object conditionObj, List<String> target, String branchId) {
        this.branchId = branchId;
        this.target = target;

        if (conditionObj instanceof Condition) {
            this.condition = (Condition) conditionObj;
        } else if (conditionObj instanceof String) {
            this.condition = new ExpressionCondition((String) conditionObj);
        } else if (conditionObj instanceof BooleanSupplier) {
            this.condition = new FuncCondition((BooleanSupplier) conditionObj);
        } else {
            throw new IllegalArgumentException("branch condition type does not meet the requirements");
        }
    }

    public boolean evaluate(BaseSession session) {
        return condition.evaluate(session);
    }

    public Object traceInfo(BaseSession session) {
        return condition.traceInfo(session);
    }

    public String getBranchId() {
        return branchId;
    }

    public List<String> getTarget() {
        return target;
    }
}
